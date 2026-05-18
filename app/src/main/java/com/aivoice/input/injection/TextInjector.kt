package com.aivoice.input.injection

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.delay

class TextInjector(private val context: Context) {

    private val clipboard: ClipboardManager by lazy {
        context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    }

    private val handler = Handler(Looper.getMainLooper())
    private var currentText = StringBuilder()
    private var lastSuccessfulMethod: InjectionMethod? = null
    private var baselineContent: String = "" // 记录开始录音时输入框的内容
    private var targetNode: AccessibilityNodeInfo? = null // 记录目标输入框节点
    private var targetNodeId: String? = null // 记录目标节点的标识（viewId）
    private var targetNodeY: Int = 0 // 记录目标节点的 Y 坐标

    companion object {
        private const val TAG = "TextInjector"
    }

    enum class InjectionMethod {
        SET_TEXT,
        CLIPBOARD_PASTE,
        SIMULATE_TYPING
    }

    fun injectStreaming(textChunk: String, rootNode: AccessibilityNodeInfo?) {
        // 去除换行符（只在第一个 chunk 时去除开头换行）
        val cleanChunk = if (currentText.isEmpty()) {
            textChunk.trimStart('\n', '\r')
        } else {
            textChunk
        }

        if (cleanChunk.isEmpty()) return

        currentText.append(cleanChunk)
        val fullText = currentText.toString()

        val inputNode = findInputNode(rootNode)
        if (inputNode == null) {
            Log.w(TAG, "No input node found")
            return
        }

        // 流式输入时，使用 baselineContent + fullText 保留原有内容
        val textToInject = baselineContent + fullText

        if (lastSuccessfulMethod == InjectionMethod.SET_TEXT) {
            if (trySetTextView(inputNode, textToInject)) {
                return
            }
        }

        when {
            trySetTextView(inputNode, textToInject) -> {
                lastSuccessfulMethod = InjectionMethod.SET_TEXT
            }
            tryClipboardPaste(inputNode, cleanChunk) -> {
                lastSuccessfulMethod = InjectionMethod.CLIPBOARD_PASTE
            }
            else -> {
                simulateTyping(inputNode, cleanChunk)
                lastSuccessfulMethod = InjectionMethod.SIMULATE_TYPING
            }
        }
    }

    // 实时更新文字（替换当前内容）
    fun updateText(text: String, rootNode: AccessibilityNodeInfo?) {
        currentText.clear()
        currentText.append(text)

        val inputNode = findInputNode(rootNode)
        if (inputNode == null) {
            Log.w(TAG, "No input node found")
            return
        }

        // 更新时保持基线内容 + 新文字
        val fullText = baselineContent + text
        trySetTextView(inputNode, fullText)
    }

    // 清空并设置新文字（用于润色后替换 ASR 文字）
    suspend fun replaceText(text: String, rootNode: AccessibilityNodeInfo?) {
        // 去除开头的换行符
        val trimmedText = text.trimStart('\n', '\r', ' ')

        if (trimmedText.isEmpty()) {
            Log.w(TAG, "Trimmed text is empty, skipping")
            return
        }

        currentText.clear()
        currentText.append(trimmedText)

        val inputNode = findInputNode(rootNode)
        if (inputNode == null) {
            Log.w(TAG, "No input node found")
            return
        }

        // 获取当前输入框内容
        val currentContent = inputNode.text?.toString() ?: ""

        // 智能替换策略：只替换本次录音添加的内容
        // 1. 如果当前内容以基线内容开头，保留基线 + 添加润色文字
        // 2. 如果基线内容已被修改（用户手动编辑了），则追加润色文字而不是替换全部

        val newText = if (currentContent.startsWith(baselineContent)) {
            // 保留基线内容 + 润色文字
            baselineContent + trimmedText
        } else if (baselineContent.isEmpty()) {
            // 没有基线内容（空白输入框），直接使用润色文字
            trimmedText
        } else {
            // 基线内容已被修改，追加润色文字到当前内容末尾（不覆盖）
            Log.w(TAG, "Baseline content changed, appending instead of replacing")
            currentContent + trimmedText
        }

        Log.d(TAG, "replaceText called: baseline='$baselineContent' (${baselineContent.length} chars)")
        Log.d(TAG, "replaceText: current='$currentContent' (${currentContent.length} chars)")
        Log.d(TAG, "replaceText: trimmed='$trimmedText' (${trimmedText.length} chars)")
        Log.d(TAG, "replaceText: final='$newText' (${newText.length} chars)")

        // 尝试多种方法替换内容
        var success = trySetTextView(inputNode, newText)
        Log.d(TAG, "replaceText: trySetTextView returned $success")

        // 如果 SET_TEXT 失败，尝试先选中全部再粘贴（适用于某些不支持 SET_TEXT 的应用）
        if (!success) {
            Log.d(TAG, "replaceText: SET_TEXT failed, trying SELECT_ALL + PASTE")
            success = trySelectAllAndPaste(inputNode, newText)
            Log.d(TAG, "replaceText: SELECT_ALL + PASTE returned $success")
        }

        // 如果还是失败，尝试直接粘贴（追加模式）
        if (!success) {
            Log.d(TAG, "replaceText: SELECT_ALL + PASTE failed, trying direct PASTE")
            success = tryClipboardPaste(inputNode, newText)
            Log.d(TAG, "replaceText: direct PASTE returned $success")
        }
    }

    // 选中全部内容后粘贴（用于替换）
    private suspend fun trySelectAllAndPaste(node: AccessibilityNodeInfo, text: String): Boolean {
        return try {
            // 先聚焦
            node.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
            delay(50)

            // 选中全部（使用 SET_SELECTION 选中所有文本）
            val currentText = node.text?.toString() ?: ""
            val args = Bundle().apply {
                putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, 0)
                putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, currentText.length)
            }
            val selectAllSuccess = node.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION, args)
            Log.d(TAG, "trySelectAllAndPaste: SET_SELECTION result=$selectAllSuccess")

            if (!selectAllSuccess) {
                return false
            }

            Thread.sleep(50)

            // 复制到剪贴板
            val originalClip = clipboard.primaryClip
            val clip = ClipData.newPlainText("text", text)
            clipboard.setPrimaryClip(clip)

            // 粘贴
            val pasteSuccess = node.performAction(AccessibilityNodeInfo.ACTION_PASTE)
            Log.d(TAG, "trySelectAllAndPaste: PASTE result=$pasteSuccess")

            // 恢复原剪贴板内容
            handler.postDelayed({
                if (originalClip != null) {
                    clipboard.setPrimaryClip(originalClip)
                }
            }, 500)

            pasteSuccess
        } catch (e: Exception) {
            Log.e(TAG, "SET_SELECTION + PASTE failed: ${e.message}", e)
            false
        }
    }

    fun injectFull(text: String, rootNode: AccessibilityNodeInfo?) {
        currentText.clear()
        currentText.append(text)

        val inputNode = findInputNode(rootNode)
        if (inputNode == null) {
            Log.w(TAG, "No input node found")
            return
        }

        when {
            trySetTextView(inputNode, text) -> {}
            tryClipboardPaste(inputNode, text) -> {}
            simulateTyping(inputNode, text) -> {}
        }
    }

    private fun findInputNode(rootNode: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (rootNode == null) return null

        // 如果已经记录了目标节点，强制优先使用它（除非完全失效）
        if (targetNode != null) {
            // 尝试刷新节点
            val isValid = try {
                targetNode!!.refresh() && isEditable(targetNode!!)
            } catch (e: Exception) {
                Log.w(TAG, "Target node refresh failed: ${e.message}")
                false
            }

            if (isValid) {
                // 验证节点标识是否匹配（防止节点被复用）
                val currentId = targetNode!!.viewIdResourceName

                // 如果有 viewId，必须匹配；如果没有 viewId（null），只要刷新成功就继续使用
                val idMatches = if (targetNodeId != null) {
                    currentId == targetNodeId
                } else {
                    currentId == null  // 两者都是 null 才匹配
                }

                if (idMatches) {
                    Log.d(TAG, "Using cached target node: ${targetNode!!.className}, viewId=$currentId")
                    return targetNode
                } else {
                    Log.w(TAG, "Target node viewId changed: $targetNodeId -> $currentId, discarding cache")
                }
            }
        }

        // 目标节点失效，重新查找
        // 优先查找有输入焦点的节点
        val focusedNode = rootNode.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        if (focusedNode != null && isEditable(focusedNode)) {
            Log.d(TAG, "Found focused input node: ${focusedNode.className}, viewId=${focusedNode.viewIdResourceName}")
            return focusedNode
        }

        // 如果没有焦点节点，查找可访问性焦点的节点
        val accessibilityFocusedNode = rootNode.findFocus(AccessibilityNodeInfo.FOCUS_ACCESSIBILITY)
        if (accessibilityFocusedNode != null && isEditable(accessibilityFocusedNode)) {
            Log.d(TAG, "Found accessibility focused node: ${accessibilityFocusedNode.className}, viewId=${accessibilityFocusedNode.viewIdResourceName}")
            return accessibilityFocusedNode
        }

        // 最后才遍历查找（这可能会找错节点）
        Log.w(TAG, "No focused node found, searching all editable nodes")
        return findEditableNode(rootNode)
    }

    private fun findEditableNode(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        // 收集所有可编辑节点
        val editableNodes = mutableListOf<AccessibilityNodeInfo>()
        collectEditableNodes(node, editableNodes)

        if (editableNodes.isEmpty()) return null

        // 如果只有一个，直接返回
        if (editableNodes.size == 1) return editableNodes[0]

        // 多个节点时的选择策略：
        // 1. 如果有缓存的目标节点，优先匹配相同 viewId 的节点
        if (targetNode != null) {
            val cachedViewId = targetNode!!.viewIdResourceName
            if (cachedViewId != null) {
                val matchingNode = editableNodes.find { it.viewIdResourceName == cachedViewId }
                if (matchingNode != null) {
                    Log.d(TAG, "Found ${editableNodes.size} editable nodes, matched cached viewId: $cachedViewId")
                    return matchingNode
                }
            }
        }

        // 2. 收集节点信息（长度和位置）
        val nodeInfos = editableNodes.mapNotNull { n ->
            val rect = android.graphics.Rect()
            n.getBoundsInScreen(rect)
            NodeInfo(
                node = n,
                textLength = n.text?.length ?: 0,
                yPosition = rect.top
            )
        }

        // 打印所有节点信息用于调试
        nodeInfos.forEachIndexed { index, info ->
            Log.d(TAG, "Node $index: Y=${info.yPosition}, length=${info.textLength}, viewId=${info.node.viewIdResourceName}")
        }

        // 3. 选择策略：
        // - 如果有缓存节点，优先选择 Y 坐标相近的节点（同一个输入框）
        // - 否则，选择文本长度较长的节点（正文通常比标题长）或 Y 坐标较大的节点
        val bestNode = if (targetNode != null) {
            val cachedRect = android.graphics.Rect()
            targetNode!!.getBoundsInScreen(cachedRect)
            val cachedY = cachedRect.top

            // 选择 Y 坐标最接近缓存节点的
            nodeInfos.minByOrNull { kotlin.math.abs(it.yPosition - cachedY) }?.node
        } else {
            // 没有缓存时，优先选择文本长度较长的节点（正文通常比标题长）
            // 如果长度相同，选择 Y 坐标较大的（正文在下方）
            nodeInfos.maxByOrNull { it.textLength * 1000 + it.yPosition }?.node
        }

        Log.d(TAG, "Found ${editableNodes.size} editable nodes, selected one at Y=${nodeInfos.find { it.node == bestNode }?.yPosition}, length=${bestNode?.text?.length ?: 0}, viewId=${bestNode?.viewIdResourceName}")
        return bestNode
    }

    private fun collectEditableNodes(node: AccessibilityNodeInfo, result: MutableList<AccessibilityNodeInfo>) {
        if (isEditable(node)) {
            result.add(node)
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (child != null) {
                collectEditableNodes(child, result)
            }
        }
    }

    private data class NodeInfo(
        val node: AccessibilityNodeInfo,
        val textLength: Int,
        val yPosition: Int
    )

    private fun isEditable(node: AccessibilityNodeInfo): Boolean {
        return node.isEditable ||
                node.className?.contains("EditText") == true ||
                (node.className?.contains("TextView") == true && node.isEditable)
    }

    private fun trySetTextView(node: AccessibilityNodeInfo, text: String): Boolean {
        return try {
            Log.d(TAG, "trySetTextView: node=${node.className}, viewId=${node.viewIdResourceName}, editable=${node.isEditable}, focused=${node.isFocused}, text.length=${text.length}")
            Log.d(TAG, "trySetTextView: current content='${node.text}', new content='$text'")

            val args = Bundle().apply {
                putCharSequence(
                    AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                    text
                )
            }
            val result = node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)

            Log.d(TAG, "trySetTextView: ACTION_SET_TEXT result=$result")

            // 验证是否真的设置成功
            if (result) {
                handler.postDelayed({
                    node.refresh()
                    val actualText = node.text?.toString() ?: ""
                    val success = actualText == text
                    Log.d(TAG, "trySetTextView: verification - expected.length=${text.length}, actual.length=${actualText.length}, match=$success")
                    if (!success) {
                        Log.w(TAG, "trySetTextView: text mismatch! expected='$text', actual='$actualText'")
                    }
                }, 100)
            }

            result
        } catch (e: Exception) {
            Log.e(TAG, "SET_TEXT failed: ${e.message}", e)
            false
        }
    }

    private fun tryClipboardPaste(node: AccessibilityNodeInfo, text: String): Boolean {
        return try {
            val originalClip = clipboard.primaryClip
            val clip = ClipData.newPlainText("text", text)
            clipboard.setPrimaryClip(clip)
            val success = node.performAction(AccessibilityNodeInfo.ACTION_PASTE)
            handler.postDelayed({
                if (originalClip != null) {
                    clipboard.setPrimaryClip(originalClip)
                }
            }, 500)
            success
        } catch (e: Exception) {
            Log.e(TAG, "CLIPBOARD_PASTE failed: ${e.message}")
            false
        }
    }

    private fun simulateTyping(node: AccessibilityNodeInfo, text: String): Boolean {
        return try {
            node.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
            val clip = ClipData.newPlainText("text", text)
            clipboard.setPrimaryClip(clip)
            node.performAction(AccessibilityNodeInfo.ACTION_PASTE)
            true
        } catch (e: Exception) {
            Log.e(TAG, "SIMULATE_TYPING failed: ${e.message}")
            false
        }
    }

    fun reset() {
        currentText.clear()
        lastSuccessfulMethod = null
        // 重置时记录当前输入框内容作为基线
        // 注意：这里无法获取 rootNode，所以在 Service 层调用时需要先获取
    }

    // 重置以准备接收 AI 润色文字（只清空 currentText，保留 baseline 和 targetNode）
    fun resetForPolish() {
        currentText.clear()
        lastSuccessfulMethod = null
        Log.d(TAG, "Reset for polish, baseline preserved: '$baselineContent'")
    }

    // 清除目标节点缓存（在润色完成后调用，让用户可以自由编辑）
    fun clearTargetNode() {
        targetNode = null
        targetNodeId = null
        targetNodeY = 0
        Log.d(TAG, "Target node cache cleared")
    }

    // 设置基线内容（在开始录音时调用）
    fun setBaseline(rootNode: AccessibilityNodeInfo?) {
        val inputNode = findInputNode(rootNode)
        if (inputNode != null) {
            // 记录目标节点及其标识，后续操作都使用这个节点
            targetNode = inputNode
            targetNodeId = inputNode.viewIdResourceName
            val rect = android.graphics.Rect()
            inputNode.getBoundsInScreen(rect)
            targetNodeY = rect.top

            // 获取真实内容，排除 placeholder
            val rawText = inputNode.text?.toString() ?: ""
            val hintText = inputNode.hintText?.toString() ?: ""

            // 如果 text 和 hint 相同，说明是 placeholder，实际内容为空
            baselineContent = if (rawText == hintText || rawText.isEmpty()) {
                ""
            } else {
                rawText
            }

            Log.d(TAG, "Baseline set: rawText='$rawText', hintText='$hintText', baseline='$baselineContent', node: ${inputNode.className}, viewId=$targetNodeId, Y=$targetNodeY")
        } else {
            targetNode = null
            targetNodeId = null
            targetNodeY = 0
            baselineContent = ""
            Log.w(TAG, "No input node found when setting baseline")
        }
    }
}
