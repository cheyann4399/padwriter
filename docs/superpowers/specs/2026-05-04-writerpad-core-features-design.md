# WriterPad 核心功能完善设计文档

## 概述

本文档设计三个核心功能的实现方案：
1. 悬浮球节拍联动修复
2. AI 建议气泡卡片
3. 词库自动生成与使用

---

## 功能 1：悬浮球节拍联动修复

### 问题分析

**根因**：`WriterPadActivity.onStop()` 调用 `clearBeatContext()` 清空了节拍上下文。当用户离开 WriterPad 界面去其他 App 写作时，悬浮球的节拍上下文被清除，导致 AI 润色无法使用节拍的人设/世界观信息。

### 解决方案

#### 1.1 移除自动清除逻辑

**文件**：`WriterPadActivity.kt`

**改动**：
```kotlin
// 修改前
override fun onStop() {
    super.onStop()
    floatingBallService?.clearBeatContext()  // 删除此行
    if (isBound) {
        unbindService(serviceConnection)
        isBound = false
    }
}

// 修改后
override fun onStop() {
    super.onStop()
    // 不再清除节拍上下文，保留给悬浮球使用
    if (isBound) {
        unbindService(serviceConnection)
        isBound = false
    }
}
```

#### 1.2 新增节拍上下文持久化

**文件**：`FloatingBallService.kt`

**改动**：
- 新增 `SharedPreferences` 存储当前节拍 ID
- 服务启动时恢复上下文

```kotlin
companion object {
    private const val PREFS_NAME = "floating_ball_prefs"
    private const val KEY_CURRENT_BEAT_ID = "current_beat_id"
}

private fun saveCurrentBeatId(beatId: String) {
    getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        .edit()
        .putString(KEY_CURRENT_BEAT_ID, beatId)
        .apply()
}

private fun restoreBeatContext() {
    val beatId = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        .getString(KEY_CURRENT_BEAT_ID, null) ?: return
    // 通过 ViewModel 加载节拍上下文
}
```

#### 1.3 清除时机调整

节拍上下文仅在以下情况清除：
- 用户切换项目时
- 用户主动清除（新增功能）

---

## 功能 2：节拍切换悬浮按钮

### 设计目标

用户在其他 App 写作时，能通过悬浮界面快速切换节拍，不打断当前写作流程。

### 界面布局

```
正常状态：
┌──────────────────────┐
│    [▶ 1/5]    ●     │
│   节拍按钮    悬浮球  │
└──────────────────────┘
```

### 组件设计

#### 2.1 节拍按钮

**文件**：新增 `BeatIndicatorView.kt`

**属性**：
- 尺寸：48dp x 36dp
- 样式：圆角矩形，半透明背景
- 内容：当前节拍编号（如 "1/5"）+ 右箭头图标
- 位置：悬浮球左侧，间距 8dp

**交互**：
| 操作 | 功能 |
|------|------|
| 单击 | 切换到下一节拍 + Toast 提示 |
| 长按 | 弹出节拍列表气泡 |

#### 2.2 节拍列表气泡

**文件**：新增 `BeatListBubbleView.kt`

**属性**：
- 尺寸：最大宽度 200dp，最大高度 300dp
- 样式：圆角卡片，阴影效果
- 位置：节拍按钮上方或下方（根据屏幕位置自动调整）
- 内容：节拍列表，当前节拍高亮

**交互**：
- 点击节拍项：切换到该节拍
- 点击空白处：关闭气泡

### 状态管理

| 状态 | 节拍按钮 | 悬浮球 | 建议气泡 |
|------|---------|--------|---------|
| 正常 | 显示 | 显示 | 隐藏 |
| 录音中 | 隐藏 | 显示（动画） | 隐藏 |
| 建议展示 | 隐藏 | 显示 | 显示 |

### 数据同步

**文件**：`WriterPadViewModel.kt`

**改动**：`syncBeatContextToService()` 方法增加节拍列表传递

```kotlin
private fun syncBeatContextToService(
    beat: Beat,
    context: BeatContext,
    beatList: List<Beat>  // 新增参数
) {
    val beatContext = BeatContext(...)
    floatingBallServiceRef?.get()?.updateBeatContext(beatContext, beatList)
}
```

---

## 功能 3：AI 建议气泡卡片

### 交互流程

```
用户长按悬浮球
       ↓
    语音录音
       ↓
   松开 → 语音转写
       ↓
  AI 润色 + 生成建议
       ↓
 文本填入输入框 + 弹出建议气泡
       ↓
用户点击建议 → 插入输入框
或
用户关闭气泡 → 返回正常状态
```

### 组件设计

#### 3.1 建议气泡视图

**文件**：新增 `SuggestionBubbleView.kt`

**属性**：
- 尺寸：最大宽度 280dp，自适应高度
- 样式：圆角卡片，阴影效果
- 位置：悬浮球上方或下方
- 内容：AI 建议文本列表（最多 3 条）

**布局**：
```xml
<FrameLayout>
    <LinearLayout orientation="vertical">
        <TextView text="写作建议" />
        <TextView suggestion1 clickable />
        <TextView suggestion2 clickable />
        <TextView suggestion3 clickable />
    </LinearLayout>
    <ImageButton close_button />
</FrameLayout>
```

**交互**：
- 点击建议文本：插入到当前输入框
- 点击关闭按钮：关闭气泡

#### 3.2 建议生成管理器

**文件**：新增 `SuggestionManager.kt`

**职责**：
- 接收语音转写文本 + 节拍上下文
- 调用 AI 生成建议
- 返回建议列表

**AI Prompt**：
```
你是一个小说写作助手。根据以下信息生成写作建议：

当前节拍：{beatTitle} - {beatSummary}
相关人设：{characters}
世界观规则：{worldRules}
用户刚输入的内容：{userInput}

请生成 2-3 条写作建议，每条建议不超过 50 字，直接可插入正文。

输出 JSON 格式：
{
  "suggestions": [
    "建议1：具体内容...",
    "建议2：具体内容...",
    "建议3：具体内容..."
  ]
}
```

#### 3.3 悬浮球服务集成

**文件**：`FloatingBallService.kt`

**改动**：
1. 在 `onLongPressEnd()` 中，AI 润色完成后触发建议生成
2. 显示 `SuggestionBubbleView`
3. 隐藏节拍按钮（进入建议状态）
4. 用户操作后恢复节拍按钮

```kotlin
private fun onLongPressEnd() {
    // ... 现有润色逻辑 ...

    // 生成建议
    suggestionManager.generateSuggestions(text, beatContext)
        .collect { suggestions ->
            hideBeatIndicator()
            showSuggestionBubble(suggestions)
        }
}

private fun onSuggestionDismissed() {
    hideSuggestionBubble()
    showBeatIndicator()
}
```

---

## 功能 4：词库自动生成与使用

### 问题分析

1. 创建人设/世界观时，AI 没有自动提取词库
2. 词库没有在语音输入时用于纠错
3. 词库没有同步到输入法

### 解决方案

#### 4.1 增强词库提取 Prompt

**文件**：`AIGuideEngine.kt`

**改动**：在 `buildUnifiedPrompt()` 中增强词库提取规则

```kotlin
// 增强词库提取规则
"""
词库提取规则（必须执行）：
1. 提取所有人名（主角、配角、龙套）
2. 提取所有地名（城市、区域、建筑）
3. 提取专有名词（功法、道具、组织、职位、特殊术语）
4. 每个词库条目必须关联 sourceId（人设ID或世界观ID）
5. priority 规则：
   - HIGH：主角、核心设定
   - MEDIUM：重要配角、常用地名
   - LOW：次要角色、偶尔出现的名词
6. aliases 包含：外号、简称、尊称、蔑称等

示例输出：
{
  "glossary": [
    {"word": "林墨", "type": "CHARACTER", "sourceId": "NEW_CHAR_林墨", "priority": "HIGH", "aliases": ["林少", "墨儿"]},
    {"word": "青云宗", "type": "WORLD", "sourceId": "NEW_RULE_青云宗", "priority": "MEDIUM", "aliases": ["宗门"]}
  ]
}
"""
```

#### 4.2 词库加载与使用

**文件**：`FloatingBallService.kt`

**改动**：
1. 初始化时加载当前项目的词库
2. 切换节拍时更新词库

```kotlin
private var currentGlossary: List<Glossary> = emptyList()

fun updateBeatContext(context: BeatContext?, beatList: List<Beat>) {
    this.beatContext = context
    // 加载项目词库
    serviceScope.launch {
        currentGlossary = glossaryRepository.getGlossaryForProject(projectId).first()
        dictionaryReplacer.updateGlossary(currentGlossary)
    }
}
```

#### 4.3 词库替换流程

**文件**：`StreamingPipeline.kt`

**现有流程**：ASR → 后处理 → 词库替换 → AI 润色

**验证点**：
- 确保 `DictionaryReplacer.replace()` 在正确位置调用
- 确保词库数据已正确加载

**文件**：`DictionaryReplacer.kt`

**改动**：增强替换逻辑

```kotlin
class DictionaryReplacer {
    private var glossary: List<Glossary> = emptyList()

    fun updateGlossary(newGlossary: List<Glossary>) {
        this.glossary = newGlossary
    }

    fun replace(text: String): String {
        var result = text
        glossary.forEach { entry ->
            // 替换别名为主词
            entry.aliases.forEach { alias ->
                result = result.replace(alias, entry.word)
            }
        }
        return result
    }
}
```

---

## 实现优先级

| 优先级 | 功能 | 理由 |
|--------|------|------|
| P0 | 节拍联动修复 | 核心功能，影响所有后续功能 |
| P1 | 节拍切换按钮 | 用户在其他 App 切换节拍的基础 |
| P2 | AI 建议气泡 | 增强写作体验 |
| P3 | 词库完善 | 锦上添花，可后续迭代 |

---

## 文件改动清单

### 新增文件

| 文件 | 说明 |
|------|------|
| `BeatIndicatorView.kt` | 节拍按钮视图 |
| `BeatListBubbleView.kt` | 节拍列表气泡 |
| `SuggestionBubbleView.kt` | AI 建议气泡 |
| `SuggestionManager.kt` | 建议生成管理器 |
| `view_beat_indicator.xml` | 节拍按钮布局 |
| `view_beat_list_bubble.xml` | 节拍列表气泡布局 |
| `view_suggestion_bubble.xml` | 建议气泡布局 |

### 修改文件

| 文件 | 改动说明 |
|------|---------|
| `WriterPadActivity.kt` | 移除 onStop 中的 clearBeatContext |
| `WriterPadViewModel.kt` | syncBeatContextToService 增加节拍列表参数 |
| `FloatingBallService.kt` | 新增节拍按钮、建议气泡、词库加载 |
| `FloatingBallView.kt` | 集成节拍按钮视图 |
| `AIGuideEngine.kt` | 增强词库提取 prompt |
| `StreamingPipeline.kt` | 验证词库替换流程 |
| `DictionaryReplacer.kt` | 增强替换逻辑 |
| `BeatContext.kt` | 新增 beatList 字段 |

---

## 测试验收标准

### 功能 1：节拍联动

- [ ] 在 WriterPad 选中节拍后，切换到其他 App
- [ ] 使用悬浮球语音输入，AI 润色结果符合当前节拍的人设/世界观
- [ ] 重启悬浮球服务后，节拍上下文自动恢复

### 功能 2：节拍切换

- [ ] 单击节拍按钮，切换到下一节拍并显示 Toast
- [ ] 长按节拍按钮，弹出节拍列表气泡
- [ ] 点击节拍列表项，切换到对应节拍
- [ ] 拖动悬浮球时，节拍按钮跟随移动

### 功能 3：AI 建议

- [ ] 语音输入完成后，弹出建议气泡
- [ ] 建议内容与当前节拍上下文相关
- [ ] 点击建议文本，插入到当前输入框
- [ ] 关闭建议气泡后，节拍按钮恢复显示

### 功能 4：词库

- [ ] 创建人设后，自动提取人名到词库
- [ ] 语音输入时，词库中的别名自动替换为主词
- [ ] 切换项目后，词库自动更新
