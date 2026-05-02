# WriterPad Phase 4: FloatingBallService Integration Design

> 悬浮球服务集成 - 节拍上下文感知的语音润色

---

## 一、概述

### 1.1 目标

将 WriterPad 的节拍上下文集成到现有的 FloatingBallService 中，实现：
- 按住悬浮球录音时，自动获取当前节拍的上下文
- AI 润色时，将角色、世界观、大纲信息注入提示词
- 在 WriterPad 创作界面内，提供上下文感知的润色体验

### 1.2 设计原则

- **最小侵入**：尽量不修改现有 FloatingBallService 的核心逻辑
- **可选上下文**：上下文为空时，降级到原有润色逻辑
- **单向数据流**：WriterPad → FloatingBallService，服务不反向修改上下文

---

## 二、架构设计

### 2.1 整体架构

```
┌─────────────────────────────────────────────────────────────┐
│                    WriterPadActivity                         │
│  - 用户选择节拍 → ViewModel 更新 currentBeat                 │
│  - ViewModel 调用 FloatingBallService.updateBeatContext()   │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│                   FloatingBallService                         │
│  - 持有 BeatContext (可为 null)                              │
│  - 按住录音 → StreamingPipeline.start()                      │
│  - 松开结束 → StreamingPipeline.stop(style, context)         │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│                    StreamingPipeline                          │
│  - stop() 接收可选的 BeatContext                             │
│  - 调用 PromptEngine.buildWithContext(text, context, style) │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│                     PromptEngine                              │
│  - buildWithContext(): 构建带上下文的润色提示词               │
│  - 上下文为空时降级到 build()                                 │
└─────────────────────────────────────────────────────────────┘
```

### 2.2 文件结构

```
app/src/main/java/com/aivoice/input/
├── model/
│   └── BeatContext.kt              # 新增：节拍上下文数据类
├── pipeline/
│   ├── StreamingPipeline.kt        # 修改：stop() 接收上下文
│   └── PromptEngine.kt             # 修改：添加 buildWithContext()
└── service/
    └── FloatingBallService.kt      # 修改：添加上下文管理方法
```

---

## 三、核心组件

### 3.1 BeatContext（数据类）

轻量级数据类，用于跨进程传递上下文：

```kotlin
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
    val content: String  // Truncated if too long
)

/**
 * World rule summary for prompt context.
 */
data class WorldRuleSummary(
    val title: String,
    val content: String  // Truncated if too long
)
```

### 3.2 PromptEngine 扩展

添加带上下文的提示词构建方法：

```kotlin
class PromptEngine {
    // Existing method
    fun build(style: PolishStyle, text: String): String

    // New method with context
    fun buildWithContext(
        text: String,
        context: BeatContext?,
        style: PolishStyle
    ): String {
        if (context == null) {
            return build(style, text)
        }

        return buildPromptWithBeatContext(text, context, style)
    }

    private fun buildPromptWithBeatContext(
        text: String,
        context: BeatContext,
        style: PolishStyle
    ): String {
        // Build prompt with beat info, characters, world rules
    }
}
```

### 3.3 StreamingPipeline 扩展

修改 stop() 方法签名：

```kotlin
class StreamingPipeline(
    private val asrClient: AsrClient,
    private val aiClient: AiClient,
    private val promptEngine: PromptEngine
) {
    // Existing method (backward compatible)
    suspend fun stop(style: PolishStyle): String

    // New overload with context
    suspend fun stop(
        style: PolishStyle,
        context: BeatContext?
    ): String {
        val transcript = asrClient.stop()
        val prompt = promptEngine.buildWithContext(transcript, context, style)
        return aiClient.polish(prompt)
    }
}
```

### 3.4 FloatingBallService 扩展

添加上下文管理方法：

```kotlin
class FloatingBallService : Service() {
    // Current beat context (null when not in WriterPad)
    private var beatContext: BeatContext? = null

    /**
     * Update beat context from WriterPad.
     * Called when user selects a beat.
     */
    fun updateBeatContext(context: BeatContext?) {
        this.beatContext = context
    }

    /**
     * Clear beat context when leaving WriterPad.
     */
    fun clearBeatContext() {
        this.beatContext = null
    }

    // Modified touch listener
    private val touchListener = object : View.OnTouchListener {
        override fun onTouch(v: View, event: MotionEvent): Boolean {
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    pipeline.start()
                }
                MotionEvent.ACTION_UP -> {
                    val style = getCurrentStyle()
                    val result = pipeline.stop(style, beatContext)
                    injectText(result)
                }
            }
            return true
        }
    }
}
```

---

## 四、WriterPad 集成

### 4.1 ViewModel 扩展

在节拍切换时同步上下文到服务：

```kotlin
class WriterPadViewModel(...) : ViewModel() {
    private var floatingBallService: FloatingBallService? = null

    fun setFloatingBallService(service: FloatingBallService?) {
        this.floatingBallService = service
    }

    private fun selectBeat(beatId: String) {
        viewModelScope.launch {
            val beat = _uiState.value.beatList.find { it.beatId == beatId } ?: return@launch
            val context = beatContextService.loadBeatContext(beatId)

            // Update UI state
            val result = WriterPadResult.BeatChanged(...)
            updateState { reducer.reduce(it, result) }

            // Sync context to FloatingBallService
            syncBeatContextToService(beat, context)
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

    private fun truncate(text: String, maxLength: Int): String {
        return if (text.length <= maxLength) text
        else text.take(maxLength) + "..."
    }
}
```

### 4.2 Activity 绑定

在 WriterPadActivity 中绑定服务：

```kotlin
class WriterPadActivity : AppCompatActivity() {
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
}
```

---

## 五、提示词设计

### 5.1 带上下文的润色提示词

```
你是一位专业的小说写作助手。请根据以下上下文信息，对用户的语音输入进行润色。

## 当前节拍
标题：{beatTitle}
摘要：{beatSummary}

## 相关角色
{characters}

## 世界观规则
{worldRules}

## 大纲
{outlineSummary}

## 用户输入
{text}

## 润色要求
风格：{style}
- 保持与上下文的一致性
- 角色行为符合设定
- 遵守世界观规则
- 输出润色后的文本，不要添加解释

请输出润色后的文本：
```

### 5.2 Token 限制

为避免提示词过长，对上下文进行截断：
- 每个角色摘要：最多 500 字符
- 每个世界观规则：最多 300 字符
- 大纲摘要：最多 500 字符
- 总上下文：最多 2000 字符

---

## 六、边界情况

### 6.1 服务未启动

- FloatingBallService 为 null 时，ViewModel 不调用 updateBeatContext()
- 用户仍可使用悬浮球，但无上下文感知

### 6.2 上下文为空

- PromptEngine.buildWithContext() 接收 null 时，降级到 build()
- 保持原有润色行为

### 6.3 离开 WriterPad

- Activity.onStop() 时调用 clearBeatContext()
- 悬浮球恢复默认润色行为

### 6.4 切换节拍

- ViewModel.selectBeat() 时自动同步新上下文
- 无需手动清除旧上下文

---

## 七、文件清单

| 类型 | 文件路径 | 说明 |
|------|----------|------|
| 数据类 | model/BeatContext.kt | 新增：节拍上下文数据类 |
| 修改 | pipeline/StreamingPipeline.kt | 修改：stop() 接收上下文 |
| 修改 | pipeline/PromptEngine.kt | 修改：添加 buildWithContext() |
| 修改 | service/FloatingBallService.kt | 修改：添加上下文管理方法 |
| 修改 | ui/writer/WriterPadViewModel.kt | 修改：同步上下文到服务 |
| 修改 | ui/writer/WriterPadActivity.kt | 修改：绑定服务 |

**总计：1 个新文件，5 个修改文件**

---

## 八、测试要点

1. **单元测试**
   - PromptEngine.buildWithContext() 正确构建提示词
   - 上下文为 null 时降级到 build()
   - 长文本正确截断

2. **集成测试**
   - WriterPadActivity 绑定服务成功
   - 节拍切换时上下文同步
   - 离开 Activity 时上下文清除

3. **手动测试**
   - 在 WriterPad 中选择节拍，使用悬浮球录音
   - 验证润色结果包含上下文信息
   - 切换节拍后验证上下文更新
   - 离开 WriterPad 后验证默认行为

---

## 九、后续迭代

Phase 4 完成后：
- **Phase 5**: 完整端到端测试，性能优化
- **Phase 6**: 用户设置界面，风格偏好配置
