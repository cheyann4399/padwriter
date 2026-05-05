# 开发问题排查指南

本文档记录开发过程中遇到的问题及解决方案，供后续开发参考。

---

## 目录

1. [真机调试](#真机调试)
2. [AI 节拍生成问题](#ai-节拍生成问题)
3. [UI 状态问题](#ui-状态问题)
4. [数据持久化问题](#数据持久化问题)

---

## 真机调试

### 问题：设备显示 unauthorized

**现象：** `adb devices` 显示设备状态为 `unauthorized`

**解决方案：**
1. 在手机上查看是否弹出"允许 USB 调试吗？"对话框
2. 勾选"一律允许使用这台计算机进行调试"（可选）
3. 点击"允许"
4. 如果没看到对话框，尝试拔掉 USB 线重新连接，或在手机设置中撤销 USB 调试授权后重新连接

### 问题：安装被拒绝

**现象：** `INSTALL_FAILED_ABORTED: User rejected permissions`

**解决方案：**
1. 在手机上点击"允许"安装
2. 如果没看到对话框，检查手机屏幕是否锁定
3. 某些手机需要在设置中允许"通过 USB 安装应用"

---

## AI 节拍生成问题

### 问题：AI 生成一直转圈，无结果

**现象：** 点击"开始创作"后一直显示加载状态，无任何输出

**排查步骤：**
1. 使用 `adb logcat` 查看日志
2. 检查是否有网络请求发出
3. 检查 API 返回的数据格式

**根本原因：** MiniMax API 返回的数据包含 `thinking_delta`（思考过程）和 `text_delta`（实际文本）两种类型，AI 在思考过程中消耗了所有 token（max_tokens=2048），导致没有空间输出最终的 JSON 结果。

**解决方案：**
1. 增加 `max_tokens` 到 4096
2. 修改 prompt，明确要求 AI 不要输出思考过程

```kotlin
// MiniMaxClient.kt
addProperty("max_tokens", 4096)  // 从 2048 增加到 4096
```

```kotlin
// GuidePromptBuilder.kt
// 在 prompt 中添加：
重要：直接输出JSON结果，不要输出任何思考过程或解释。
```

### 问题：JSON 解析失败

**现象：** AI 返回了数据，但解析失败

**排查步骤：**
1. 检查 `AIGuideEngine.accumulateAndParseBeats` 的日志
2. 检查 `accumulatedJson` 的内容
3. 检查 `isJsonComplete` 的判断结果

**根本原因：** MiniMax 返回的 JSON 是分多个 chunk 发送的，需要累积后才能解析。同时需要检查 JSON 是否完整（括号匹配）。

**调试日志添加：**
```kotlin
Log.d(TAG, "accumulateAndParseBeats: chunk='${chunk.take(100)}'")
Log.d(TAG, "accumulatedJson length: ${accumulatedJson.length}")
Log.d(TAG, "tryParseAccumulated: isJsonComplete=$isComplete")
```

---

## UI 状态问题

### 问题：节拍生成成功但界面不更新

**现象：** 日志显示 `handleBeatEvent: Complete with X beats`，但界面仍显示加载中

**排查步骤：**
1. 检查 `renderState` 是否被调用
2. 检查 `guideState` 是否正确更新
3. 检查 Reducer 是否正确处理 `BeatsGenerated` 结果

**根本原因：** 状态更新了但 UI 没有正确响应状态变化。

**解决方案：**
1. 确保 `observeState()` 使用 `collectLatest` 收集状态
2. 在 `renderState` 中添加日志确认调用
3. 检查 `when (state.guideState)` 的分支是否正确

```kotlin
private fun renderState(state: WriterPadState) {
    Log.d(TAG, "renderState: guideState=${state.guideState}")
    when (state.guideState) {
        GuideState.CONFIRMING -> showBeatPreview(state)
        // ...
    }
}
```

### 问题：确认按钮不显示

**现象：** 节拍列表显示了，但没有确认按钮

**根本原因：** `showBeatPreview` 方法中没有显示确认按钮

**解决方案：**
```kotlin
private fun showBeatPreview(state: WriterPadState) {
    // ... 显示 RecyclerView
    confirmBeatsButton.visibility = View.VISIBLE
}
```

### 问题：按钮点击无反应

**现象：** 点击按钮后没有任何日志输出

**排查步骤：**
1. 检查 `setupListeners()` 是否在 `onCreate` 中调用
2. 检查按钮 ID 是否正确
3. 检查 `viewModel.processIntent` 是否被调用

**调试方法：**
```kotlin
startButton.setOnClickListener {
    Log.d(TAG, "startButton clicked")
    // ...
}
```

---

## 数据持久化问题

### 问题：退出后项目消失

**现象：** 创建了项目和节拍，退出后再次进入看不到

**根本原因：** WriterPadActivity 每次启动都创建新项目，而不是加载已有项目

**解决方案：**
1. 添加 `LoadLatestProject` Intent
2. 在 `onCreate` 中调用加载最新项目
3. 如果项目有节拍，直接进入 `COMPLETED` 状态

```kotlin
// WriterPadIntent.kt
object LoadLatestProject : WriterPadIntent()

// WriterPadViewModel.kt
private fun loadLatestProject() {
    viewModelScope.launch {
        val projects = projectRepository.getAllProjects().first()
        if (projects.isNotEmpty()) {
            val latestProject = projects.first()
            val beats = beatRepository.getBeatsForProject(latestProject.id).first()
            // 加载项目和节拍
            if (beats.isNotEmpty()) {
                updateState { it.copy(guideState = GuideState.COMPLETED) }
            }
        }
    }
}

// WriterPadActivity.kt
override fun onCreate(savedInstanceState: Bundle?) {
    // ...
    viewModel.processIntent(WriterPadIntent.LoadLatestProject)
}
```

---

## 常用调试命令

```bash
# 查看设备连接状态
adb devices

# 安装应用
adb install -r app/build/outputs/apk/debug/app-debug.apk

# 清除日志
adb logcat -c

# 查看应用日志
adb logcat -d | grep -iE "WriterPad|AIGuide|MiniMax"

# 启动应用
adb shell am start -n com.aivoice.input/.MainActivity

# 构建应用
./gradlew.bat assembleDebug
```

---

## 日志标签对照表

| 标签 | 类 | 用途 |
|------|-----|------|
| WriterPadActivity | WriterPadActivity.kt | Activity 生命周期和 UI 渲染 |
| WriterPadViewModel | WriterPadViewModel.kt | Intent 处理和状态管理 |
| AIGuideEngine | AIGuideEngine.kt | AI 请求和响应处理 |
| MiniMaxClient | MiniMaxClient.kt | 网络请求和 SSE 流处理 |
| GuideResponseParser | GuideResponseParser.kt | JSON 解析 |
| FloatingBallService | FloatingBallService.kt | 悬浮球服务 |

---

## 更新记录

- 2026-05-04: 初始版本，记录真机调试、AI 生成、UI 状态、数据持久化问题
