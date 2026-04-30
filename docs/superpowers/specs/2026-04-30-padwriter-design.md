# PadWriter 设计文档

> AI 速录助手（安卓版）- 复刻 Typeless 核心体验

## 1. 项目概述

**产品名称**：PadWriter

**产品定位**：安卓全局悬浮球语音输入工具，复刻 Typeless 核心体验

**核心流程**：按住悬浮球 → 语音录制 → 实时转写 → AI 润色 → 流式注入到当前 App

**技术栈**：Kotlin + MVVM + Room + Coroutines + AccessibilityService

**目标版本**：Android 8.0+ (API 26)

**包名**：com.aivoice.input

---

## 2. 技术架构

```
┌─────────────────────────────────────────────────────────┐
│                     Input Layer                          │
│         FloatingBallService | AudioRecorder              │
└─────────────────────────────────────────────────────────┘
                          ↓ 音频流
┌─────────────────────────────────────────────────────────┐
│              Streaming Pipeline（核心）                   │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐      │
│  │  WebSocket  │→ │   Speech    │→ │   Post      │      │
│  │  Client     │  │   Buffer    │  │   Processor │      │
│  │  (讯飞RTASR)│  │  (Chunk Mgr)│  │  (轻量处理) │      │
│  └─────────────┘  └─────────────┘  └─────────────┘      │
└─────────────────────────────────────────────────────────┘
                          ↓ 处理后文本
┌─────────────────────────────────────────────────────────┐
│                AI Processing Layer                       │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐      │
│  │   Prompt    │→ │   MiniMax   │→ │  Dictionary │      │
│  │   Engine    │  │   (Streaming)│  │  Replacer   │      │
│  └─────────────┘  └─────────────┘  └─────────────┘      │
└─────────────────────────────────────────────────────────┘
                          ↓ 流式输出
┌─────────────────────────────────────────────────────────┐
│                   Output Controller                       │
│         TextInjector (三级 Fallback)                     │
│  ├─ ACTION_SET_TEXT                                      │
│  ├─ Clipboard + ACTION_PASTE                             │
│  └─ simulateKeyEvent                                     │
└─────────────────────────────────────────────────────────┘
```

---

## 3. 核心模块设计

### 3.1 SpeechBuffer（Chunk Manager）

管理语音识别的实时切片，处理用户修正。

```kotlin
class SpeechBuffer {
    private val chunks = mutableListOf<SpeechChunk>()

    data class SpeechChunk(
        val text: String,
        val isFinal: Boolean,      // 是否确定结果
        val timestamp: Long
    )

    // 添加实时识别片段
    fun append(chunk: SpeechChunk)

    // 处理修正：用户说"上海...不北京" → 删除上海，保留北京
    fun handleCorrection()

    // 获取当前文本（用于预热判断）
    fun getCurrentText(): String

    // 合并最终文本
    fun merge(): String

    // 清空
    fun clear()
}
```

### 3.2 PostProcessor（轻量处理）

本地只做简单处理，智能处理交给 AI Prompt。

```kotlin
class PostProcessor {
    private val fillerWords = setOf(
        "嗯", "啊", "呃", "那个", "就是", "然后",
        "所以", "其实", "就是说", "怎么说呢", "这个"
    )

    // 本地轻量处理
    fun process(text: String): String {
        return text
            .removeFillerWords()      // 删除口语词
            .removeDuplicates()       // 去重复 "就是就是" → "就是"
        // 智能加标点 + 语法修正 → 交给 Prompt
    }
}
```

### 3.3 PromptEngine（Prompt 工程）

承担智能处理：加标点、语法修正、风格转换。

```kotlin
class PromptEngine {

    fun build(style: PolishStyle, text: String): String {
        return when (style) {
            NATIVE -> nativePrompt()
            FORMAL -> formalPrompt()
            CONCISE -> concisePrompt()
        }.format(text)
    }

    // 原生风格：删除无意义词，保留原意，加标点
    private fun nativePrompt(): String = """
        你是一个语音转文字整理助手。请处理以下文本：

        任务：
        1. 补全标点符号（根据语义添加逗号、句号、问号等）
        2. 修正明显的语音转写错误（如同音字错误）
        3. 保持原意和口语风格，不做书面化改写

        原文：{text}

        只输出处理后的文字。
    """

    // 正式风格：书面化改写
    private fun formalPrompt(): String = """
        你是一个文字润色助手。请将以下口语内容改写为正式书面语：

        任务：
        1. 补全标点符号
        2. 修正语法错误
        3. 调整语序，使表达更清晰
        4. 使用书面化词汇替换口语表达
        5. 保持原意不变

        原文：{text}

        只输出改写后的文字。
    """

    // 精简风格：提取核心
    private fun concisePrompt(): String = """
        你是一个精简助手。请提取以下内容的核心信息：

        任务：
        1. 删除冗余表达
        2. 只保留关键信息
        3. 用最简洁的方式表达
        4. 补全必要标点

        原文：{text}

        只输出精简后的文字。
    """
}
```

### 3.4 XunfeiRTASRClient（实时语音识别）

讯飞实时语音转写，WebSocket 连接。

```kotlin
class XunfeiRTASRClient(
    private val appId: String,
    private val apiKey: String,
    private val apiSecret: String
) {
    // WebSocket 接口
    // wss://office-api-ast-dx.iflyaisol.com/ast/communicate/v1

    // 连接
    fun connect(onResult: (RTASRResult) -> Unit)

    // 发送音频数据（每40ms发送1280字节）
    fun sendAudio(audioData: ByteArray)

    // 结束
    fun end()

    // 断开
    fun disconnect()
}

data class RTASRResult(
    val text: String,
    val isFinal: Boolean,      // 是否最终结果
    val isMiddle: Boolean      // 是否中间结果（实时显示用）
)
```

**音频格式要求**：
- 采样率：16000 Hz
- 位长：16 bit
- 声道：单声道
- 格式：PCM

### 3.5 MiniMaxClient（流式 AI）

MiniMax API 流式调用，实现"字随声出"。

```kotlin
class MiniMaxClient(
    private val apiKey: String,
    private val baseUrl: String = "https://api.minimaxi.com/anthropic"
) {
    // 流式调用
    suspend fun chatStream(
        prompt: String,
        onChunk: (String) -> Unit  // 流式回调
    )

    // 解析 SSE 流式返回
    private fun parseStreamChunk(json: String): String?
}
```

### 3.6 TextInjector（三级 Fallback）

文字注入，兼容所有 App。

```kotlin
class TextInjector(private val accessibilityService: AccessibilityService) {

    // 流式注入
    fun injectStreaming(textChunk: String)

    // 方式1: ACTION_SET_TEXT（最快，部分 App 不支持）
    private fun trySetTextView(text: String): Boolean

    // 方式2: Clipboard + ACTION_PASTE（兼容性好）
    private fun tryClipboardPaste(text: String): Boolean

    // 方式3: 模拟按键输入（兼容所有，速度慢）
    private fun simulateTyping(text: String)

    // 重置状态
    fun reset()
}
```

**Fallback 策略**：
1. 优先尝试 `ACTION_SET_TEXT`
2. 失败则用剪贴板 + `ACTION_PASTE`
3. 再失败则模拟按键逐字输入

### 3.7 StreamingPipeline（流式管道）

整合所有模块，实现核心流程。

```kotlin
class StreamingPipeline(
    private val rtasrClient: XunfeiRTASRClient,
    private val miniMaxClient: MiniMaxClient,
    private val promptEngine: PromptEngine,
    private val postProcessor: PostProcessor,
    private val textInjector: TextInjector
) {
    private val speechBuffer = SpeechBuffer()
    private var prewarmJob: Job? = null
    private var lastTextLength = 0

    // ASR 结果回调
    fun onASRResult(result: RTASRResult) {
        speechBuffer.append(result)

        // 实时显示中间结果（可选）
        if (result.isMiddle) {
            showIntermediateText(result.text)
        }

        // 预热机制：文本超过30字且仍在说话
        val currentLength = speechBuffer.getCurrentText().length
        if (currentLength > 30 && currentLength - lastTextLength > 10) {
            lastTextLength = currentLength
            triggerPrewarm()
        }
    }

    // 预热：提前准备 prompt
    private fun triggerPrewarm()

    // 用户松开：开始流式处理
    fun onUserRelease(style: PolishStyle): Flow<String>
}
```

---

## 4. 悬浮球交互设计

| 手势 | 动作 |
|------|------|
| **按住** | 开始录音 |
| **松开** | 结束录音，开始处理 |
| **单击** | 打开设置 |
| **双击** | 隐藏悬浮球 |
| **拖动** | 移动悬浮球位置 |

**录音视觉反馈**：
- 悬浮球变色（空闲 → 录音中 → 处理中）
- 震动提示开始/结束

---

## 5. 数据结构

```kotlin
// 历史记录
@Entity
data class HistoryItem(
    @PrimaryKey val id: Long,
    val originalText: String,    // 原始识别文本
    val polishedText: String,    // 润色后文本
    val style: PolishStyle,      // 润色风格
    val timestamp: Long          // 时间戳
)

// 词库条目
@Entity
data class DictionaryEntry(
    @PrimaryKey val id: Long,
    val original: String,        // 原词
    val replacement: String,     // 替换词
    val enabled: Boolean
)

// 配置
data class AppSettings(
    val polishStyle: PolishStyle,    // 默认润色风格
    val floatingBallHidden: Boolean, // 悬浮球隐藏
    val autoStart: Boolean           // 开机自启
)

// 润色风格
enum class PolishStyle {
    NATIVE,   // 原生
    FORMAL,   // 正式
    CONCISE   // 精简
}
```

---

## 6. API 配置

敏感信息通过 `local.properties` 配置：

```properties
# 讯飞语音
XUNFEI_APP_ID=your_app_id
XUNFEI_API_KEY=your_api_key
XUNFEI_API_SECRET=your_api_secret

# MiniMax AI
MINIMAX_API_KEY=your_api_key
```

---

## 7. 必需权限

| 权限 | 用途 | 申请时机 |
|------|------|----------|
| SYSTEM_ALERT_WINDOW | 悬浮窗 | 首次启动引导 |
| RECORD_AUDIO | 麦克风 | 首次按住悬浮球 |
| BIND_ACCESSIBILITY_SERVICE | 辅助功能 | 设置页引导 |
| FOREGROUND_SERVICE | 前台服务 | 安装后自动 |
| POST_NOTIFICATIONS | 通知 | Android 13+ |

---

## 8. 开发阶段

| 阶段 | 内容 | 核心验证点 |
|------|------|-----------|
| **1. 悬浮球基础** | 显示、拖动、单击/双击/长按、权限引导 | 交互正确 |
| **2. 录音 + 实时转写** | AudioRecorder、WebSocket、SpeechBuffer | 边说边出字 |
| **3. PostProcessor** | 轻量处理：去口语词、去重复 | 本地预处理 |
| **4. AI 流式润色** | PromptEngine、MiniMax 流式、预热机制 | 字随声出 |
| **5. 词库替换** | 本地词库、文本替换 | 专业词正确 |
| **6. 文字注入** | 三级 Fallback、流式注入 | 全 App 兼容 |
| **7. 辅助功能** | 历史记录、设置页面 | 完整可用 |

---

## 9. 验收标准

核心功能必须完整实现：

1. 悬浮球在任意界面显示和拖动
2. 按住录音、松开结束，震动反馈
3. 实时语音转写，边说边出字
4. AI 润色输出符合选择风格
5. 流式注入文字到当前 App
6. 三级 Fallback 确保全 App 兼容
7. 历史记录查看、复制、删除
8. 本地词库替换

---

## 10. 技术依赖

```gradle
// 核心依赖
implementation "org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3"
implementation "androidx.room:room-runtime:2.6.1"
implementation "androidx.room:room-ktx:2.6.1"
kapt "androidx.room:room-compiler:2.6.1"

// 网络
implementation "com.squareup.okhttp3:okhttp:4.12.0"
implementation "com.squareup.okhttp3:okhttp-sse:4.12.0"

// JSON
implementation "com.google.code.gson:gson:2.10.1"

// Lifecycle
implementation "androidx.lifecycle:lifecycle-viewmodel-ktx:2.7.0"
implementation "androidx.lifecycle:lifecycle-livedata-ktx:2.7.0"
```
