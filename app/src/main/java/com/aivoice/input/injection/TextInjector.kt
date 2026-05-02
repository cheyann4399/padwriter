package com.aivoice.input.injection

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo

class TextInjector(private val context: Context) {

    private val clipboard: ClipboardManager by lazy {
        context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    }

    private val handler = Handler(Looper.getMainLooper())
    private var currentText = StringBuilder()
    private var lastSuccessfulMethod: InjectionMethod? = null

    companion object {
        private const val TAG = "TextInjector"
    }

    enum class InjectionMethod {
        SET_TEXT,
        CLIPBOARD_PASTE,
        SIMULATE_TYPING
    }

    fun injectStreaming(textChunk: String, rootNode: AccessibilityNodeInfo?) {
        currentText.append(textChunk)
        val fullText = currentText.toString()

        val inputNode = findInputNode(rootNode)
        if (inputNode == null) {
            Log.w(TAG, "No input node found")
            return
        }

        if (lastSuccessfulMethod == InjectionMethod.SET_TEXT) {
            if (trySetTextView(inputNode, fullText)) {
                return
            }
        }

        when {
            trySetTextView(inputNode, fullText) -> {
                lastSuccessfulMethod = InjectionMethod.SET_TEXT
            }
            tryClipboardPaste(inputNode, textChunk) -> {
                lastSuccessfulMethod = InjectionMethod.CLIPBOARD_PASTE
            }
            else -> {
                simulateTyping(inputNode, textChunk)
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

        trySetTextView(inputNode, text)
    }

    // 清空并设置新文字（用于润色后替换）
    fun replaceText(text: String, rootNode: AccessibilityNodeInfo?) {
        currentText.clear()
        currentText.append(text)

        val inputNode = findInputNode(rootNode)
        if (inputNode == null) {
            Log.w(TAG, "No input node found")
            return
        }

        // 先清空，再设置新文字
        trySetTextView(inputNode, text)
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
        val focusedNode = rootNode.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        if (focusedNode != null && isEditable(focusedNode)) {
            return focusedNode
        }
        return findEditableNode(rootNode)
    }

    private fun findEditableNode(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (isEditable(node)) return node
        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (child != null) {
                val result = findEditableNode(child)
                if (result != null) return result
            }
        }
        return null
    }

    private fun isEditable(node: AccessibilityNodeInfo): Boolean {
        return node.isEditable ||
                node.className?.contains("EditText") == true ||
                (node.className?.contains("TextView") == true && node.isEditable)
    }

    private fun trySetTextView(node: AccessibilityNodeInfo, text: String): Boolean {
        return try {
            val args = Bundle().apply {
                putCharSequence(
                    AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                    text
                )
            }
            node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
        } catch (e: Exception) {
            Log.e(TAG, "SET_TEXT failed: ${e.message}")
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
    }
}
