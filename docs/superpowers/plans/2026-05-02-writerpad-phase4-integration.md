# WriterPad Phase 4: FloatingBallService Integration Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Integrate WriterPad beat context into FloatingBallService for context-aware voice polishing.

**Architecture:** Add BeatContext data class, extend PromptEngine with buildWithContext(), modify StreamingPipeline.stop() to accept optional context, and add context management methods to FloatingBallService. WriterPadActivity binds to the service and syncs beat context on selection.

**Tech Stack:** Kotlin, Coroutines, Flow, Android Service binding

---

## File Structure

| File | Action | Purpose |
|------|--------|---------|
| `model/BeatContext.kt` | Create | Data class for beat context passed to service |
| `pipeline/PromptEngine.kt` | Modify | Add buildWithContext() method |
| `pipeline/StreamingPipeline.kt` | Modify | Add stop(style, context) overload |
| `service/FloatingBallService.kt` | Modify | Add beatContext field and management methods |
| `ui/writer/WriterPadViewModel.kt` | Modify | Add service binding and context sync |
| `ui/writer/WriterPadActivity.kt` | Modify | Bind/unbind FloatingBallService |

---

### Task 1: Create BeatContext Data Class

**Files:**
- Create: `app/src/main/java/com/aivoice/input/model/BeatContext.kt`

- [ ] **Step 1: Create BeatContext.kt**

```kotlin
package com.aivoice.input.model

/**
 * Beat context for context-aware polishing.
 * Passed from WriterPad to FloatingBallService.
 */
data class BeatContext(
    val beatId: String,
    val beatTitle: String,
    val beatSummary: String,
    val characters: List<CharacterSummary>,
    val worldRules: List<WorldRuleSummary>,
    val outlineSummary: String?
)

/**
 * Character summary for prompt context.
 */
data class CharacterSummary(
    val name: String,
    val content: String
)

/**
 * World rule summary for prompt context.
 */
data class WorldRuleSummary(
    val title: String,
    val content: String
)
```

- [ ] **Step 2: Commit**

```bash
git add app/src/main/java/com/aivoice/input/model/BeatContext.kt
git commit -m "feat: add BeatContext data class for service integration"
```

---

### Task 2: Extend PromptEngine with Context Support

**Files:**
- Modify: `app/src/main/java/com/aivoice/input/pipeline/PromptEngine.kt`

- [ ] **Step 1: Add buildWithContext method to PromptEngine**

Replace the entire `PromptEngine.kt` file:

```kotlin
package com.aivoice.input.pipeline

import com.aivoice.input.model.BeatContext
import com.aivoice.input.model.PolishStyle

class PromptEngine {

    fun build(style: PolishStyle, text: String): String {
        return when (style) {
            PolishStyle.NATIVE -> nativePrompt(text)
            PolishStyle.FORMAL -> formalPrompt(text)
            PolishStyle.CONCISE -> concisePrompt(text)
        }
    }

    /**
     * Build prompt with optional beat context.
     * Falls back to build() if context is null.
     */
    fun buildWithContext(
        text: String,
        context: BeatContext?,
        style: PolishStyle
    ): String {
        if (context == null) {
            return build(style, text)
        }
        return contextAwarePrompt(text, context, style)
    }

    private fun nativePrompt(text: String): String {
        return """
你是一个语音转文字整理助手。请处理以下文本：

任务：
1. 补全标点符号（根据语义添加逗号、句号、问号等）
2. 修正明显的语音转写错误（如同音字错误）
3. 保持原意和口语风格，不做书面化改写

原文：$text

只输出处理后的文字。
        """.trimIndent()
    }

    private fun formalPrompt(text: String): String {
        return """
你是一个文字润色助手。请将以下口语内容改写为正式书面语：

任务：
1. 补全标点符号
2. 修正语法错误
3. 调整语序，使表达更清晰
4. 使用书面化词汇替换口语表达
5. 保持原意不变

原文：$text

只输出改写后的文字。
        """.trimIndent()
    }

    private fun concisePrompt(text: String): String {
        return """
你是一个精简助手。请提取以下内容的核心信息：

任务：
1. 删除冗余表达
2. 只保留关键信息
3. 用最简洁的方式表达
4. 补全必要标点

原文：$text

只输出精简后的文字。
        """.trimIndent()
    }

    private fun contextAwarePrompt(
        text: String,
        context: BeatContext,
        style: PolishStyle
    ): String {
        val styleInstruction = when (style) {
            PolishStyle.NATIVE -> "保持口语风格，自然流畅"
            PolishStyle.FORMAL -> "使用书面语，正式规范"
            PolishStyle.CONCISE -> "精简表达，突出重点"
        }

        val characterSection = if (context.characters.isNotEmpty()) {
            context.characters.joinToString("\n\n") { char ->
                "【${char.name}】\n${char.content}"
            }
        } else {
            "无"
        }

        val worldRuleSection = if (context.worldRules.isNotEmpty()) {
            context.worldRules.joinToString("\n\n") { rule ->
                "【${rule.title}】\n${rule.content}"
            }
        } else {
            "无"
        }

        val outlineSection = context.outlineSummary ?: "无"

        return """
你是一位专业的小说写作助手。请根据以下上下文信息，对用户的语音输入进行润色。

## 当前节拍
标题：${context.beatTitle}
摘要：${context.beatSummary}

## 相关角色
$characterSection

## 世界观规则
$worldRuleSection

## 大纲
$outlineSection

## 用户输入
$text

## 润色要求
风格：$styleInstruction
- 保持与上下文的一致性
- 角色行为符合设定
- 遵守世界观规则
- 只输出润色后的文字，不要添加解释

请输出润色后的文字：
        """.trimIndent()
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add app/src/main/java/com/aivoice/input/pipeline/PromptEngine.kt
git commit -m "feat: add buildWithContext() to PromptEngine for beat context"
```

---

### Task 3: Extend StreamingPipeline with Context Support

**Files:**
- Modify: `app/src/main/java/com/aivoice/input/pipeline/StreamingPipeline.kt`

- [ ] **Step 1: Add import and new stop() overload**

Add import at the top (after existing imports):

```kotlin
import com.aivoice.input.model.BeatContext
```

Add new stop() overload after the existing stop(style) method (around line 110, after the existing stop(style) method):

```kotlin
    /**
     * Stop recording and polish with optional beat context.
     */
    fun stop(style: PolishStyle, context: BeatContext?): Flow<String> = flow {
        audioRecorder.stopRecording()
        asrClient.end()

        val rawText = speechBuffer.merge()
        if (rawText.isEmpty()) {
            return@flow
        }

        val processedText = postProcessor.process(rawText)
        val replacedText = dictionaryReplacer.replace(processedText)
        val prompt = promptEngine.buildWithContext(replacedText, context, style)

        val result = StringBuilder()
        miniMaxClient.chatStream(prompt).collect { chunk ->
            result.append(chunk)
            emit(chunk)
        }

        Log.d(TAG, """
            |=== AI 润色调试 (带上下文) ===
            |[节拍]: ${context?.beatTitle ?: "无"}
            |[ASR 原文]: $rawText
            |[后处理]: $processedText
            |[词库替换]: $replacedText
            |[AI 润色]: $result
            |==================
        """.trimMargin())
    }.flowOn(Dispatchers.IO)
```

- [ ] **Step 2: Commit**

```bash
git add app/src/main/java/com/aivoice/input/pipeline/StreamingPipeline.kt
git commit -m "feat: add stop(style, context) overload to StreamingPipeline"
```

---

### Task 4: Add Context Management to FloatingBallService

**Files:**
- Modify: `app/src/main/java/com/aivoice/input/service/FloatingBallService.kt`

- [ ] **Step 1: Add import for BeatContext**

Add after existing imports (around line 30):

```kotlin
import com.aivoice.input.model.BeatContext
```

- [ ] **Step 2: Add beatContext field and management methods**

Add after the `currentPolishedText` field (around line 64):

```kotlin
    // Beat context for WriterPad integration
    private var beatContext: BeatContext? = null

    /**
     * Update beat context from WriterPad.
     * Called when user selects a beat.
     */
    fun updateBeatContext(context: BeatContext?) {
        this.beatContext = context
        Log.d(TAG, "Beat context updated: ${context?.beatTitle}")
    }

    /**
     * Clear beat context when leaving WriterPad.
     */
    fun clearBeatContext() {
        this.beatContext = null
        Log.d(TAG, "Beat context cleared")
    }
```

- [ ] **Step 3: Modify onLongPressEnd to use context**

Replace the `onLongPressEnd()` method (lines 316-364) with:

```kotlin
    private fun onLongPressEnd() {
        Log.d(TAG, "onLongPressEnd called, isLongPress=$isLongPress")
        if (isLongPress) {
            floatingBallView.state = FloatingBallState.PROCESSING
            VibrationHelper.vibrate(this, 50)

            recordingJob?.cancel()
            recordingJob = null

            // 获取当前 ASR 文本
            val asrText = pipeline.getCurrentText()
            Log.d(TAG, "ASR text: $asrText")

            serviceScope.launch {
                try {
                    val style = getDefaultPolishStyle()
                    Log.d(TAG, "Stopping pipeline with style: $style, context: ${beatContext?.beatTitle}")

                    // 重置注入器，准备接收 AI 润色结果
                    TextInjectService.getInstance()?.resetInjection()

                    var firstChunk = true
                    pipeline.stop(style, beatContext).collectLatest { chunk ->
                        Log.d(TAG, "Received chunk: $chunk")
                        if (firstChunk) {
                            // 第一个 chunk 替换掉 ASR 文字
                            TextInjectService.getInstance()?.replaceText(chunk)
                            firstChunk = false
                        } else {
                            // 后续 chunk 追加
                            TextInjectService.getInstance()?.injectTextStreaming(chunk)
                        }
                        currentPolishedText.append(chunk)
                    }

                    // Save to history
                    if (currentPolishedText.isNotEmpty()) {
                        Log.d(TAG, "Saving to history: ${currentPolishedText}")
                        saveToHistory(asrText, currentPolishedText.toString())
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Processing error: ${e.message}", e)
                } finally {
                    floatingBallView.state = FloatingBallState.NORMAL
                    TextInjectService.getInstance()?.resetInjection()
                }
            }
        }
    }
```

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/aivoice/input/service/FloatingBallService.kt
git commit -m "feat: add beat context management to FloatingBallService"
```

---

### Task 5: Add Service Binding to WriterPadViewModel

**Files:**
- Modify: `app/src/main/java/com/aivoice/input/ui/writer/WriterPadViewModel.kt`

- [ ] **Step 1: Add import for BeatContext and FloatingBallService**

Add after existing imports (around line 24):

```kotlin
import com.aivoice.input.model.BeatContext
import com.aivoice.input.model.CharacterSummary
import com.aivoice.input.model.WorldRuleSummary
import com.aivoice.input.service.FloatingBallService
```

- [ ] **Step 2: Add service field and setter**

Add after the `_uiState` field (around line 39):

```kotlin
    // Reference to FloatingBallService for context sync
    private var floatingBallService: FloatingBallService? = null

    fun setFloatingBallService(service: FloatingBallService?) {
        this.floatingBallService = service
    }
```

- [ ] **Step 3: Add truncate helper function**

Add at the end of the class, before the closing brace (around line 282):

```kotlin
    private fun truncate(text: String, maxLength: Int): String {
        return if (text.length <= maxLength) text
        else text.take(maxLength) + "..."
    }
```

- [ ] **Step 4: Modify selectBeat to sync context**

Replace the existing `selectBeat` method (lines 186-202) with:

```kotlin
    private fun selectBeat(beatId: String) {
        viewModelScope.launch {
            val beat = _uiState.value.beatList.find { it.beatId == beatId } ?: return@launch
            try {
                val context = beatContextService.loadBeatContext(beatId)
                val result = WriterPadResult.BeatChanged(
                    beat = beat,
                    characters = context.characters,
                    worldRules = context.worldRules,
                    outline = context.outline
                )
                updateState { reducer.reduce(it, result) }

                // Sync context to FloatingBallService
                syncBeatContextToService(beat, context)
            } catch (e: Exception) {
                updateState { reducer.reduce(it, WriterPadResult.Error(e.message ?: "Load beat failed")) }
            }
        }
    }

    private fun syncBeatContextToService(
        beat: Beat,
        context: BeatContextData
    ) {
        val beatContext = BeatContext(
            beatId = beat.beatId,
            beatTitle = beat.title,
            beatSummary = beat.summary,
            characters = context.characters.map {
                CharacterSummary(it.name, truncate(it.content, 500))
            },
            worldRules = context.worldRules.map {
                WorldRuleSummary(it.title, truncate(it.content, 300))
            },
            outlineSummary = context.outline?.content?.let { truncate(it, 500) }
        )
        floatingBallService?.updateBeatContext(beatContext)
    }
```

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/aivoice/input/ui/writer/WriterPadViewModel.kt
git commit -m "feat: add FloatingBallService binding and context sync to ViewModel"
```

---

### Task 6: Add Service Binding to WriterPadActivity

**Files:**
- Modify: `app/src/main/java/com/aivoice/input/ui/writer/WriterPadActivity.kt`

- [ ] **Step 1: Add imports for service binding**

Add after existing imports (around line 22):

```kotlin
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import com.aivoice.input.service.FloatingBallService
```

- [ ] **Step 2: Add service connection fields**

Add after the adapter field (around line 49):

```kotlin
    // FloatingBallService binding
    private var floatingBallService: FloatingBallService? = null
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            val binder = service as FloatingBallService.LocalBinder
            floatingBallService = binder.getService()
            viewModel.setFloatingBallService(floatingBallService)
        }

        override fun onServiceDisconnected(name: ComponentName) {
            floatingBallService = null
            viewModel.setFloatingBallService(null)
        }
    }
```

- [ ] **Step 3: Add onBind method to FloatingBallService**

First, we need to add the LocalBinder to FloatingBallService. Add this inner class to FloatingBallService.kt after the companion object (around line 88):

```kotlin
    // Binder for service binding
    inner class LocalBinder : android.os.Binder() {
        fun getService(): FloatingBallService = this@FloatingBallService
    }

    private val binder = LocalBinder()

    override fun onBind(intent: Intent?): android.os.IBinder {
        super.onBind(intent)
        return binder
    }
```

- [ ] **Step 4: Add onStart and onStop for service binding**

Add after the `onCreate` method (around line 59):

```kotlin
    override fun onStart() {
        super.onStart()
        // Bind to FloatingBallService
        val intent = Intent(this, FloatingBallService::class.java)
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    override fun onStop() {
        super.onStop()
        // Clear context and unbind
        floatingBallService?.clearBeatContext()
        unbindService(serviceConnection)
    }
```

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/aivoice/input/service/FloatingBallService.kt
git add app/src/main/java/com/aivoice/input/ui/writer/WriterPadActivity.kt
git commit -m "feat: add FloatingBallService binding to WriterPadActivity"
```

---

### Task 7: Update AndroidManifest for Service Binding

**Files:**
- Modify: `app/src/main/AndroidManifest.xml`

- [ ] **Step 1: Add BIND_SERVICE permission if not present**

Check if `BIND_SERVICE` permission is needed. For binding to a foreground service, no additional permission is required. Skip this step if the manifest already has the service declared correctly.

- [ ] **Step 2: Verify service declaration**

Ensure FloatingBallService is declared with the correct intent filter for binding. The existing declaration should work. No changes needed if the service is already declared.

- [ ] **Step 3: Commit (if changes were made)**

```bash
git add app/src/main/AndroidManifest.xml
git commit -m "chore: verify service binding configuration"
```

---

## Self-Review Checklist

**Spec Coverage:**
- [x] BeatContext data class created (Task 1)
- [x] PromptEngine.buildWithContext() added (Task 2)
- [x] StreamingPipeline.stop(style, context) added (Task 3)
- [x] FloatingBallService.updateBeatContext() and clearBeatContext() added (Task 4)
- [x] WriterPadViewModel syncs context to service (Task 5)
- [x] WriterPadActivity binds/unbinds service (Task 6)

**Placeholder Scan:**
- No TBD, TODO, or placeholder patterns found
- All code steps have complete implementations

**Type Consistency:**
- BeatContext used consistently across all files
- CharacterSummary and WorldRuleSummary defined in BeatContext.kt
- PipelineState, PolishStyle types match existing definitions
