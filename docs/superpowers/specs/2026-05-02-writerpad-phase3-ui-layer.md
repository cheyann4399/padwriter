# WriterPad Phase 3: UI Layer + MVI Architecture Design

> UI 层架构 - MVI 模式实现

---

## 一、概述

### 1.1 目标

实现 WriterPad 的 UI 层，采用 MVI (Model-View-Intent) 架构：
- 单向数据流，状态可预测
- StateFlow 驱动 UI 更新
- 清晰的意图-状态分离

### 1.2 设计原则

- **单向数据流**：Intent → ViewModel → Result → Reducer → State → UI
- **状态不可变**：所有状态通过 copy() 创建新实例
- **副作用隔离**：ViewModel 处理业务逻辑，Reducer 纯函数归约状态

---

## 二、架构设计

### 2.1 整体架构

```
┌─────────────────────────────────────────────────────────────┐
│                    WriterPadActivity                         │
│  - 观察 StateFlow<WriterPadState>                            │
│  - 发送 Intent 到 ViewModel                                  │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│                   WriterPadViewModel                          │
│  - processIntent(intent: WriterPadIntent)                    │
│  - StateFlow<WriterPadState> uiState                         │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│              Repository Layer (Phase 1)                      │
│  ProjectRepository, BeatRepository, BeatContextService       │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│                AI Engine (Phase 2)                           │
│  AIGuideEngine - generateBeats, classifyAndIndex             │
└─────────────────────────────────────────────────────────────┘
```

### 2.2 文件结构

```
app/src/main/java/com/aivoice/input/ui/writer/
├── WriterPadActivity.kt           # 主 Activity
├── WriterPadViewModel.kt          # ViewModel
├── mvi/
│   ├── WriterPadState.kt          # 状态定义
│   ├── WriterPadIntent.kt         # 意图定义
│   ├── WriterPadResult.kt         # 结果定义
│   └── WriterPadReducer.kt        # 状态归约器
├── components/
│   ├── BeatManagerView.kt         # 节拍器按钮组件
│   ├── AIGuidePanel.kt            # AI 对话面板
│   ├── GuideInputView.kt          # 开屏引导输入
│   └── SettingCardView.kt         # 设定卡片
└── adapters/
    └── BeatListAdapter.kt         # 节拍列表适配器

app/src/main/res/layout/
├── activity_writer_pad.xml        # Activity 主布局
├── view_beat_manager.xml          # 节拍器布局
├── view_ai_guide_panel.xml        # AI 面板布局
├── view_guide_input.xml           # 引导输入布局
└── item_beat.xml                  # 节拍列表项布局
```

---

## 三、MVI 核心组件

### 3.1 WriterPadState（状态）

```kotlin
data class WriterPadState(
    // 项目状态
    val project: Project? = null,
    val isLoading: Boolean = false,

    // 节拍状态
    val currentBeat: Beat? = null,
    val beatList: List<Beat> = emptyList(),

    // 设定状态
    val characters: List<CharacterWithContext> = emptyList(),
    val worldRules: List<WorldRuleWithContext> = emptyList(),
    val outline: Outline? = null,

    // AI 引导状态
    val guideState: GuideState = GuideState.IDLE,
    val guideInput: String = "",
    val guideResult: Any? = null,  // BeatDraft 列表或 ClassificationResult

    // UI 状态
    val isAiPanelOpen: Boolean = false,
    val isBeatListOpen: Boolean = false,
    val error: String? = null
)

enum class GuideState {
    IDLE,           // 空闲，等待输入
    INPUTTING,      // 输入脑洞中
    GENERATING,     // AI 生成中
    CONFIRMING,     // 确认结果中
    COMPLETED       // 引导完成，进入创作
}
```

### 3.2 WriterPadIntent（意图）

```kotlin
sealed class WriterPadIntent {
    // 项目操作
    data class LoadProject(val projectId: Long) : WriterPadIntent()
    data class CreateProject(val name: String, val premise: String) : WriterPadIntent()

    // 引导流程
    object StartGuide : WriterPadIntent()
    data class UpdateGuideInput(val input: String) : WriterPadIntent()
    data class SubmitGuideInput(val input: String) : WriterPadIntent()
    data class ConfirmGuideResult(val confirmed: Boolean) : WriterPadIntent()

    // 节拍操作
    object AdvanceBeat : WriterPadIntent()
    data class SelectBeat(val beatId: String) : WriterPadIntent()
    object ToggleBeatList : WriterPadIntent()

    // AI 对话
    object ToggleAiPanel : WriterPadIntent()
    data class SendAiMessage(val text: String) : WriterPadIntent()

    // 设定管理
    data class ToggleMappingActive(val mappingId: Long) : WriterPadIntent()

    // 错误处理
    object ClearError : WriterPadIntent()
}
```

### 3.3 WriterPadResult（结果）

```kotlin
sealed class WriterPadResult {
    // 项目结果
    data class ProjectLoaded(
        val project: Project,
        val beats: List<Beat>
    ) : WriterPadResult()

    data class ProjectCreated(
        val project: Project
    ) : WriterPadResult()

    // 引导结果
    data class BeatsGenerating(
        val partialBeats: List<BeatDraft>
    ) : WriterPadResult()

    data class BeatsGenerated(
        val beats: List<BeatDraft>
    ) : WriterPadResult()

    data class GuideCompleted(
        val beats: List<Beat>
    ) : WriterPadResult()

    // 节拍结果
    data class BeatChanged(
        val beat: Beat,
        val characters: List<CharacterWithContext>,
        val worldRules: List<WorldRuleWithContext>,
        val outline: Outline?
    ) : WriterPadResult()

    // AI 对话结果
    data class ClassificationDone(
        val result: ClassificationResult
    ) : WriterPadResult()

    // UI 状态
    data class AiPanelToggled(val isOpen: Boolean) : WriterPadResult()
    data class BeatListToggled(val isOpen: Boolean) : WriterPadResult()

    // 错误
    data class Error(val message: String) : WriterPadResult()
}
```

### 3.4 WriterPadReducer（归约器）

```kotlin
class WriterPadReducer {
    fun reduce(state: WriterPadState, result: WriterPadResult): WriterPadState {
        return when (result) {
            is WriterPadResult.ProjectLoaded -> state.copy(
                project = result.project,
                beatList = result.beats,
                currentBeat = result.beats.firstOrNull(),
                isLoading = false
            )

            is WriterPadResult.BeatsGenerated -> state.copy(
                guideState = GuideState.CONFIRMING,
                guideResult = result.beats,
                isLoading = false
            )

            is WriterPadResult.GuideCompleted -> state.copy(
                guideState = GuideState.COMPLETED,
                beatList = result.beats,
                currentBeat = result.beats.firstOrNull(),
                guideResult = null,
                isLoading = false
            )

            is WriterPadResult.BeatChanged -> state.copy(
                currentBeat = result.beat,
                characters = result.characters,
                worldRules = result.worldRules,
                outline = result.outline
            )

            is WriterPadResult.Error -> state.copy(
                error = result.message,
                isLoading = false
            )

            is WriterPadResult.AiPanelToggled -> state.copy(
                isAiPanelOpen = result.isOpen
            )

            is WriterPadResult.BeatListToggled -> state.copy(
                isBeatListOpen = result.isOpen
            )

            // ... 其他结果处理
        }
    }
}
```

---

## 四、ViewModel 设计

### 4.1 WriterPadViewModel

```kotlin
@HiltViewModel
class WriterPadViewModel @Inject constructor(
    private val projectRepository: ProjectRepository,
    private val beatRepository: BeatRepository,
    private val beatContextService: BeatContextService,
    private val aiGuideEngine: AIGuideEngine,
    private val reducer: WriterPadReducer,
    private val gson: Gson
) : ViewModel() {

    private val _uiState = MutableStateFlow(WriterPadState())
    val uiState: StateFlow<WriterPadState> = _uiState.asStateFlow()

    // 累积的 AI 响应
    private var accumulatedJson = ""

    fun processIntent(intent: WriterPadIntent) {
        when (intent) {
            is WriterPadIntent.LoadProject -> loadProject(intent.projectId)
            is WriterPadIntent.CreateProject -> createProject(intent.name, intent.premise)
            is WriterPadIntent.SubmitGuideInput -> submitGuideInput(intent.input)
            is WriterPadIntent.ConfirmGuideResult -> confirmGuideResult(intent.confirmed)
            is WriterPadIntent.AdvanceBeat -> advanceBeat()
            is WriterPadIntent.SelectBeat -> selectBeat(intent.beatId)
            is WriterPadIntent.ToggleAiPanel -> toggleAiPanel()
            is WriterPadIntent.SendAiMessage -> sendAiMessage(intent.text)
            is WriterPadIntent.ClearError -> updateState { it.copy(error = null) }
            // ... 其他意图处理
        }
    }

    private fun updateState(reduce: (WriterPadState) -> WriterPadState) {
        _uiState.update(reduce)
    }

    private fun loadProject(projectId: Long) {
        viewModelScope.launch {
            updateState { it.copy(isLoading = true) }
            try {
                val project = projectRepository.getById(projectId)
                val beats = beatRepository.getByProject(projectId)
                val result = WriterPadResult.ProjectLoaded(project, beats)
                updateState { reducer.reduce(it, result) }
            } catch (e: Exception) {
                updateState { reducer.reduce(it, WriterPadResult.Error(e.message ?: "加载失败")) }
            }
        }
    }

    private fun submitGuideInput(premise: String) {
        updateState { it.copy(guideState = GuideState.GENERATING, isLoading = true) }
        accumulatedJson = ""

        viewModelScope.launch {
            aiGuideEngine.generateBeats(premise)
                .catch { e ->
                    updateState { reducer.reduce(it, WriterPadResult.Error(e.message ?: "生成失败")) }
                }
                .collect { event ->
                    handleBeatEvent(event)
                }
        }
    }

    private fun handleBeatEvent(event: GuideEvent<List<BeatDraft>>) {
        when (event) {
            is GuideEvent.Loading -> { /* 保持当前状态 */ }
            is GuideEvent.Complete -> {
                val result = WriterPadResult.BeatsGenerated(event.data)
                updateState { reducer.reduce(it, result) }
            }
            is GuideEvent.Repaired -> {
                val result = WriterPadResult.BeatsGenerated(event.data)
                updateState { reducer.reduce(it, result) }
            }
            is GuideEvent.Error -> {
                updateState { reducer.reduce(it, WriterPadResult.Error(event.message)) }
            }
        }
    }

    private fun confirmGuideResult(confirmed: Boolean) {
        if (!confirmed) {
            // 用户拒绝，重新输入
            updateState { it.copy(guideState = GuideState.INPUTTING, guideResult = null) }
            return
        }

        val beats = (_uiState.value.guideResult as? List<*>)?.filterIsInstance<BeatDraft>()
            ?: return

        viewModelScope.launch {
            updateState { it.copy(isLoading = true) }
            try {
                val project = _uiState.value.project ?: return@launch
                val savedBeats = beatRepository.saveBeatDrafts(project.id, beats)
                val result = WriterPadResult.GuideCompleted(savedBeats)
                updateState { reducer.reduce(it, result) }
            } catch (e: Exception) {
                updateState { reducer.reduce(it, WriterPadResult.Error(e.message ?: "保存失败")) }
            }
        }
    }

    private fun advanceBeat() {
        val currentIndex = _uiState.value.beatList.indexOf(_uiState.value.currentBeat)
        val nextIndex = currentIndex + 1
        if (nextIndex < _uiState.value.beatList.size) {
            selectBeat(_uiState.value.beatList[nextIndex].beatId)
        }
    }

    private fun selectBeat(beatId: String) {
        viewModelScope.launch {
            val beat = _uiState.value.beatList.find { it.beatId == beatId } ?: return@launch
            val context = beatContextService.getBeatContext(beatId)
            val result = WriterPadResult.BeatChanged(
                beat = beat,
                characters = context.characters,
                worldRules = context.worldRules,
                outline = context.outline
            )
            updateState { reducer.reduce(it, result) }
        }
    }

    private fun toggleAiPanel() {
        val isOpen = !_uiState.value.isAiPanelOpen
        updateState { reducer.reduce(it, WriterPadResult.AiPanelToggled(isOpen)) }
    }

    private fun sendAiMessage(text: String) {
        val currentBeat = _uiState.value.currentBeat ?: return
        val existingSettings = ExistingSettings(
            characters = _uiState.value.characters.map { it.character },
            worldRules = _uiState.value.worldRules.map { it.worldRule },
            currentBeatId = currentBeat.beatId
        )

        viewModelScope.launch {
            aiGuideEngine.classifyAndIndex(text, currentBeat, existingSettings)
                .catch { e ->
                    updateState { reducer.reduce(it, WriterPadResult.Error(e.message ?: "分类失败")) }
                }
                .collect { event ->
                    handleClassificationEvent(event)
                }
        }
    }

    private fun handleClassificationEvent(event: GuideEvent<ClassificationResult>) {
        when (event) {
            is GuideEvent.Complete -> {
                // 保存分类结果到数据库
                saveClassificationResult(event.data)
            }
            is GuideEvent.Error -> {
                updateState { reducer.reduce(it, WriterPadResult.Error(event.message)) }
            }
            else -> { /* Loading 状态 */ }
        }
    }

    private suspend fun saveClassificationResult(result: ClassificationResult) {
        // 保存角色、世界观、映射到数据库
        // 然后刷新当前节拍的上下文
        // 实现细节在实现阶段完成
    }
}
```

---

## 五、UI 组件设计

### 5.1 BeatManagerView（节拍器按钮）

**功能：**
- 显示当前节拍标题和序号（如 "1/8 废墟觉醒"）
- 进度条显示节拍位置
- 长按 → 推进下一节拍
- 双击 → 打开节拍列表对话框

**状态：**
```kotlin
data class BeatManagerState(
    val currentOrder: Int,
    val totalBeats: Int,
    val title: String,
    val isLastBeat: Boolean
)
```

### 5.2 AIGuidePanel（AI 对话面板）

**功能：**
- 悬浮在底部，可展开/收起
- 输入框 + 发送按钮
- 显示 AI 响应（支持流式显示）
- 引导模式下显示节拍预览列表

**状态：**
```kotlin
data class AiPanelState(
    val isOpen: Boolean,
    val inputText: String,
    val isProcessing: Boolean,
    val messages: List<ChatMessage>
)
```

### 5.3 GuideInputView（开屏引导）

**功能：**
- 项目名称输入
- 脑洞/前提输入框（多行）
- "开始创作"按钮
- 生成过程中显示加载动画

**状态：**
```kotlin
data class GuideInputState(
    val projectName: String,
    val premise: String,
    val isGenerating: Boolean,
    val error: String?
)
```

### 5.4 SettingCardView（设定卡片）

**功能：**
- 显示角色/世界观摘要
- 激活状态切换开关
- 点击展开详情

**状态：**
```kotlin
data class SettingCardState(
    val id: String,
    val title: String,
    val summary: String,
    val isActive: Boolean,
    val type: SettingType
)
```

---

## 六、页面布局

### 6.1 Activity 主布局

```xml
<!-- activity_writer_pad.xml -->
<androidx.coordinatorlayout.widget.CoordinatorLayout>
    <com.google.android.material.appbar.AppBarLayout>
        <androidx.appcompat.widget.Toolbar
            android:id="@+id/toolbar" />
    </com.google.android.material.appbar.AppBarLayout>

    <androidx.core.widget.NestedContentView>
        <!-- 根据 guideState 切换内容 -->
        <FrameLayout android:id="@+id/content_container" />
    </androidx.core.widget.NestedContentView>

    <!-- 底部节拍器 -->
    <com.aivoice.input.ui.writer.components.BeatManagerView
        android:id="@+id/beat_manager" />

    <!-- AI 对话面板 -->
    <com.aivoice.input.ui.writer.components.AIGuidePanel
        android:id="@+id/ai_panel" />
</androidx.coordinatorlayout.widget.CoordinatorLayout>
```

### 6.2 内容区域切换逻辑

```kotlin
// WriterPadActivity 中
private fun renderState(state: WriterPadState) {
    when (state.guideState) {
        GuideState.IDLE, GuideState.INPUTTING -> {
            showGuideInput(state)
        }
        GuideState.GENERATING -> {
            showLoading()
        }
        GuideState.CONFIRMING -> {
            showBeatPreview(state.guideResult as List<BeatDraft>)
        }
        GuideState.COMPLETED -> {
            showBeatContent(state)
        }
    }
}
```

---

## 七、交互流程

### 7.1 开屏引导流程

```
用户进入 → guideState = IDLE
    ↓
显示 GuideInputView（项目名 + 脑洞输入）
    ↓
用户点击"开始创作" → SubmitGuideInput
    ↓
guideState = GENERATING，显示加载动画
    ↓
AIGuideEngine.generateBeats(premise) 返回 Flow
    ↓
收到 Complete<List<BeatDraft>> → guideState = CONFIRMING
    ↓
显示 BeatPreviewList，用户确认或重新生成
    ↓
用户确认 → ConfirmGuideResult(true)
    ↓
保存到数据库，guideState = COMPLETED
```

### 7.2 节拍推进流程

```
用户长按 BeatManagerView → AdvanceBeat
    ↓
计算下一节拍 index
    ↓
调用 selectBeat(beatId)
    ↓
BeatContextService.getBeatContext(beatId) 返回上下文
    ↓
更新 currentBeat, characters, worldRules, outline
    ↓
UI 刷新显示新节拍内容
```

### 7.3 AI 对话补充设定

```
用户打开 AIGuidePanel，输入内容 → SendAiMessage
    ↓
AIGuideEngine.classifyAndIndex(content, currentBeat, existingSettings)
    ↓
收到 ClassificationResult
    ↓
保存角色/世界观/映射到数据库
    ↓
如果有 conflictCheck.hasConflict → 显示冲突警告
    ↓
刷新当前节拍的关联设定列表
```

---

## 八、依赖注入

### 8.1 ViewModelModule

```kotlin
@Module
@InstallIn(ViewModelComponent::class)
object ViewModelModule {
    @ViewModelScoped
    @Provides
    fun provideWriterPadReducer(): WriterPadReducer {
        return WriterPadReducer()
    }
}
```

### 8.2 Activity 注册

在 AndroidManifest.xml 中添加：
```xml
<activity
    android:name=".ui.writer.WriterPadActivity"
    android:exported="false"
    android:label="@string/writer_pad_title"
    android:theme="@style/Theme.PadWriter" />
```

---

## 九、文件清单

| 类型 | 文件路径 | 说明 |
|------|----------|------|
| MVI | ui/writer/mvi/WriterPadState.kt | 状态定义 |
| MVI | ui/writer/mvi/WriterPadIntent.kt | 意图定义 |
| MVI | ui/writer/mvi/WriterPadResult.kt | 结果定义 |
| MVI | ui/writer/mvi/WriterPadReducer.kt | 状态归约器 |
| ViewModel | ui/writer/WriterPadViewModel.kt | ViewModel |
| Activity | ui/writer/WriterPadActivity.kt | 主 Activity |
| 组件 | ui/writer/components/BeatManagerView.kt | 节拍器按钮 |
| 组件 | ui/writer/components/AIGuidePanel.kt | AI 对话面板 |
| 组件 | ui/writer/components/GuideInputView.kt | 开屏引导输入 |
| 组件 | ui/writer/components/SettingCardView.kt | 设定卡片 |
| 适配器 | ui/writer/adapters/BeatListAdapter.kt | 节拍列表适配器 |
| 布局 | res/layout/activity_writer_pad.xml | Activity 主布局 |
| 布局 | res/layout/view_beat_manager.xml | 节拍器布局 |
| 布局 | res/layout/view_ai_guide_panel.xml | AI 面板布局 |
| 布局 | res/layout/view_guide_input.xml | 引导输入布局 |
| 布局 | res/layout/item_beat.xml | 节拍列表项布局 |

**总计：16 个文件**

---

## 十、后续迭代

Phase 3 完成后：
- **Phase 4**: 与悬浮球集成（FloatingBallService 扩展，BeatContext 同步）
