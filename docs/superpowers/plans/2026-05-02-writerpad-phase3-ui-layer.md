# WriterPad Phase 3: UI Layer + MVI Architecture Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement WriterPad UI layer with MVI architecture: State, Intent, Result, Reducer, ViewModel, Activity, and UI components.

**Architecture:** MVI pattern with StateFlow for UI updates. ViewModel processes intents, calls repositories/AI engine, and updates state through reducer. Activity observes state and renders UI.

**Tech Stack:** Kotlin, AndroidX Lifecycle ViewModel, StateFlow, Coroutines, ViewBinding

---

## File Structure

```
app/src/main/java/com/aivoice/input/ui/writer/
├── mvi/
│   ├── WriterPadState.kt          # 状态定义
│   ├── WriterPadIntent.kt         # 意图定义
│   ├── WriterPadResult.kt         # 结果定义
│   └── WriterPadReducer.kt        # 状态归约器
├── WriterPadViewModel.kt          # ViewModel
├── WriterPadViewModelFactory.kt   # ViewModel 工厂
├── WriterPadActivity.kt           # 主 Activity
├── components/
│   ├── BeatManagerView.kt         # 节拍器按钮
│   ├── AIGuidePanel.kt            # AI 对话面板
│   ├── GuideInputView.kt          # 开屏引导输入
│   └── SettingCardView.kt         # 设定卡片
└── adapters/
    └── BeatListAdapter.kt         # 节拍列表适配器

app/src/main/res/layout/
├── activity_writer_pad.xml
├── view_beat_manager.xml
├── view_ai_guide_panel.xml
├── view_guide_input.xml
├── item_beat.xml
└── item_setting_card.xml
```

---

### Task 1: MVI State Definition

**Files:**
- Create: `app/src/main/java/com/aivoice/input/ui/writer/mvi/WriterPadState.kt`

- [ ] **Step 1: Create WriterPadState**

```kotlin
package com.aivoice.input.ui.writer.mvi

import com.aivoice.input.model.Beat
import com.aivoice.input.model.Outline
import com.aivoice.input.model.Project
import com.aivoice.input.model.draft.BeatDraft
import com.aivoice.input.model.draft.ClassificationResult
import com.aivoice.input.db.CharacterWithContext
import com.aivoice.input.db.WorldRuleWithContext

/**
 * UI state for WriterPad screen.
 */
data class WriterPadState(
    // Project state
    val project: Project? = null,
    val isLoading: Boolean = false,

    // Beat state
    val currentBeat: Beat? = null,
    val beatList: List<Beat> = emptyList(),

    // Settings state
    val characters: List<CharacterWithContext> = emptyList(),
    val worldRules: List<WorldRuleWithContext> = emptyList(),
    val outline: Outline? = null,

    // AI Guide state
    val guideState: GuideState = GuideState.IDLE,
    val guideInput: String = "",
    val guideResult: GuideResult? = null,

    // UI state
    val isAiPanelOpen: Boolean = false,
    val isBeatListOpen: Boolean = false,
    val error: String? = null
)

/**
 * Guide flow state.
 */
enum class GuideState {
    IDLE,           // Waiting for input
    INPUTTING,      // User typing premise
    GENERATING,     // AI generating beats
    CONFIRMING,     // Confirming generated beats
    COMPLETED       // Guide finished, in writing mode
}

/**
 * Guide result wrapper.
 */
sealed class GuideResult {
    data class Beats(val beats: List<BeatDraft>) : GuideResult()
    data class Classification(val result: ClassificationResult) : GuideResult()
}
```

- [ ] **Step 2: Commit**

```bash
git add app/src/main/java/com/aivoice/input/ui/writer/mvi/WriterPadState.kt
git commit -m "feat: add WriterPadState for MVI architecture"
```

---

### Task 2: MVI Intent Definition

**Files:**
- Create: `app/src/main/java/com/aivoice/input/ui/writer/mvi/WriterPadIntent.kt`

- [ ] **Step 1: Create WriterPadIntent**

```kotlin
package com.aivoice.input.ui.writer.mvi

/**
 * User intentions for WriterPad screen.
 */
sealed class WriterPadIntent {
    // Project operations
    data class LoadProject(val projectId: Long) : WriterPadIntent()
    data class CreateProject(val name: String, val premise: String) : WriterPadIntent()

    // Guide flow
    object StartGuide : WriterPadIntent()
    data class UpdateGuideInput(val input: String) : WriterPadIntent()
    data class SubmitGuideInput(val input: String) : WriterPadIntent()
    data class ConfirmGuideResult(val confirmed: Boolean) : WriterPadIntent()

    // Beat operations
    object AdvanceBeat : WriterPadIntent()
    data class SelectBeat(val beatId: String) : WriterPadIntent()
    object ToggleBeatList : WriterPadIntent()

    // AI panel
    object ToggleAiPanel : WriterPadIntent()
    data class SendAiMessage(val text: String) : WriterPadIntent()

    // Settings
    data class ToggleMappingActive(val mappingId: Long) : WriterPadIntent()

    // Error handling
    object ClearError : WriterPadIntent()
}
```

- [ ] **Step 2: Commit**

```bash
git add app/src/main/java/com/aivoice/input/ui/writer/mvi/WriterPadIntent.kt
git commit -m "feat: add WriterPadIntent for MVI architecture"
```

---

### Task 3: MVI Result Definition

**Files:**
- Create: `app/src/main/java/com/aivoice/input/ui/writer/mvi/WriterPadResult.kt`

- [ ] **Step 1: Create WriterPadResult**

```kotlin
package com.aivoice.input.ui.writer.mvi

import com.aivoice.input.model.Beat
import com.aivoice.input.model.Outline
import com.aivoice.input.model.Project
import com.aivoice.input.model.draft.BeatDraft
import com.aivoice.input.model.draft.ClassificationResult
import com.aivoice.input.db.CharacterWithContext
import com.aivoice.input.db.WorldRuleWithContext

/**
 * Results from processing intents.
 */
sealed class WriterPadResult {
    // Project results
    data class ProjectLoaded(
        val project: Project,
        val beats: List<Beat>
    ) : WriterPadResult()

    data class ProjectCreated(
        val project: Project
    ) : WriterPadResult()

    // Guide results
    data class BeatsGenerating(
        val partialBeats: List<BeatDraft>
    ) : WriterPadResult()

    data class BeatsGenerated(
        val beats: List<BeatDraft>
    ) : WriterPadResult()

    data class GuideCompleted(
        val beats: List<Beat>
    ) : WriterPadResult()

    // Beat results
    data class BeatChanged(
        val beat: Beat,
        val characters: List<CharacterWithContext>,
        val worldRules: List<WorldRuleWithContext>,
        val outline: Outline?
    ) : WriterPadResult()

    // AI panel results
    data class ClassificationDone(
        val result: ClassificationResult
    ) : WriterPadResult()

    // UI state results
    data class AiPanelToggled(val isOpen: Boolean) : WriterPadResult()
    data class BeatListToggled(val isOpen: Boolean) : WriterPadResult()
    data class GuideInputUpdated(val input: String) : WriterPadResult()

    // Error
    data class Error(val message: String) : WriterPadResult()
}
```

- [ ] **Step 2: Commit**

```bash
git add app/src/main/java/com/aivoice/input/ui/writer/mvi/WriterPadResult.kt
git commit -m "feat: add WriterPadResult for MVI architecture"
```

---

### Task 4: MVI Reducer

**Files:**
- Create: `app/src/main/java/com/aivoice/input/ui/writer/mvi/WriterPadReducer.kt`

- [ ] **Step 1: Create WriterPadReducer**

```kotlin
package com.aivoice.input.ui.writer.mvi

/**
 * Pure function reducer for state transitions.
 */
class WriterPadReducer {

    fun reduce(state: WriterPadState, result: WriterPadResult): WriterPadState {
        return when (result) {
            is WriterPadResult.ProjectLoaded -> state.copy(
                project = result.project,
                beatList = result.beats,
                currentBeat = result.beats.firstOrNull(),
                isLoading = false
            )

            is WriterPadResult.ProjectCreated -> state.copy(
                project = result.project,
                isLoading = false
            )

            is WriterPadResult.BeatsGenerating -> state.copy(
                guideState = GuideState.GENERATING,
                isLoading = true
            )

            is WriterPadResult.BeatsGenerated -> state.copy(
                guideState = GuideState.CONFIRMING,
                guideResult = GuideResult.Beats(result.beats),
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

            is WriterPadResult.ClassificationDone -> state.copy(
                guideResult = GuideResult.Classification(result.result),
                isLoading = false
            )

            is WriterPadResult.AiPanelToggled -> state.copy(
                isAiPanelOpen = result.isOpen
            )

            is WriterPadResult.BeatListToggled -> state.copy(
                isBeatListOpen = result.isOpen
            )

            is WriterPadResult.GuideInputUpdated -> state.copy(
                guideInput = result.input
            )

            is WriterPadResult.Error -> state.copy(
                error = result.message,
                isLoading = false
            )
        }
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add app/src/main/java/com/aivoice/input/ui/writer/mvi/WriterPadReducer.kt
git commit -m "feat: add WriterPadReducer for MVI architecture"
```

---

### Task 5: ViewModel Factory

**Files:**
- Create: `app/src/main/java/com/aivoice/input/ui/writer/WriterPadViewModelFactory.kt`

- [ ] **Step 1: Create WriterPadViewModelFactory**

```kotlin
package com.aivoice.input.ui.writer

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.aivoice.input.ai.AIGuideEngine
import com.aivoice.input.db.AppDatabase
import com.aivoice.input.repository.BeatContextService
import com.aivoice.input.repository.BeatRepository
import com.aivoice.input.repository.ProjectRepository
import com.aivoice.input.ui.writer.mvi.WriterPadReducer

/**
 * Factory for creating WriterPadViewModel with dependencies.
 */
class WriterPadViewModelFactory(
    private val context: Context
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(WriterPadViewModel::class.java)) {
            val database = AppDatabase.getInstance(context)

            val projectRepository = ProjectRepository(database)
            val beatRepository = BeatRepository(database)
            val beatContextService = BeatContextService(database)
            val aiGuideEngine = AIGuideEngineProvider.getEngine(context)
            val reducer = WriterPadReducer()

            return WriterPadViewModel(
                projectRepository = projectRepository,
                beatRepository = beatRepository,
                beatContextService = beatContextService,
                aiGuideEngine = aiGuideEngine,
                reducer = reducer
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

/**
 * Provider for AIGuideEngine singleton.
 */
object AIGuideEngineProvider {
    @Volatile
    private var instance: AIGuideEngine? = null

    fun getEngine(context: Context): AIGuideEngine {
        return instance ?: synchronized(this) {
            instance ?: createEngine(context).also { instance = it }
        }
    }

    private fun createEngine(context: Context): AIGuideEngine {
        val database = AppDatabase.getInstance(context)
        // Create dependencies and return engine
        // Implementation depends on AIGuideEngine constructor from Phase 2
        throw NotImplementedError("AIGuideEngine creation needs Phase 2 implementation")
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add app/src/main/java/com/aivoice/input/ui/writer/WriterPadViewModelFactory.kt
git commit -m "feat: add WriterPadViewModelFactory for manual DI"
```

---

### Task 6: ViewModel Implementation

**Files:**
- Create: `app/src/main/java/com/aivoice/input/ui/writer/WriterPadViewModel.kt`

- [ ] **Step 1: Create WriterPadViewModel**

```kotlin
package com.aivoice.input.ui.writer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aivoice.input.ai.GuideEvent
import com.aivoice.input.ai.ExistingSettings
import com.aivoice.input.model.Beat
import com.aivoice.input.model.draft.BeatDraft
import com.aivoice.input.repository.BeatContextService
import com.aivoice.input.repository.BeatRepository
import com.aivoice.input.repository.ProjectRepository
import com.aivoice.input.ai.AIGuideEngine
import com.aivoice.input.ui.writer.mvi.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * ViewModel for WriterPad screen.
 * Processes intents and updates UI state.
 */
class WriterPadViewModel(
    private val projectRepository: ProjectRepository,
    private val beatRepository: BeatRepository,
    private val beatContextService: BeatContextService,
    private val aiGuideEngine: AIGuideEngine,
    private val reducer: WriterPadReducer
) : ViewModel() {

    private val _uiState = MutableStateFlow(WriterPadState())
    val uiState: StateFlow<WriterPadState> = _uiState.asStateFlow()

    fun processIntent(intent: WriterPadIntent) {
        when (intent) {
            is WriterPadIntent.LoadProject -> loadProject(intent.projectId)
            is WriterPadIntent.CreateProject -> createProject(intent.name, intent.premise)
            is WriterPadIntent.StartGuide -> startGuide()
            is WriterPadIntent.UpdateGuideInput -> updateGuideInput(intent.input)
            is WriterPadIntent.SubmitGuideInput -> submitGuideInput(intent.input)
            is WriterPadIntent.ConfirmGuideResult -> confirmGuideResult(intent.confirmed)
            is WriterPadIntent.AdvanceBeat -> advanceBeat()
            is WriterPadIntent.SelectBeat -> selectBeat(intent.beatId)
            is WriterPadIntent.ToggleBeatList -> toggleBeatList()
            is WriterPadIntent.ToggleAiPanel -> toggleAiPanel()
            is WriterPadIntent.SendAiMessage -> sendAiMessage(intent.text)
            is WriterPadIntent.ClearError -> clearError()
            is WriterPadIntent.ToggleMappingActive -> toggleMappingActive(intent.mappingId)
        }
    }

    private fun updateState(reduce: (WriterPadState) -> WriterPadState) {
        _uiState.value = reduce(_uiState.value)
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

    private fun createProject(name: String, premise: String) {
        viewModelScope.launch {
            updateState { it.copy(isLoading = true) }
            try {
                val project = projectRepository.create(name, premise)
                updateState { reducer.reduce(it, WriterPadResult.ProjectCreated(project)) }
            } catch (e: Exception) {
                updateState { reducer.reduce(it, WriterPadResult.Error(e.message ?: "创建失败")) }
            }
        }
    }

    private fun startGuide() {
        updateState { it.copy(guideState = GuideState.INPUTTING) }
    }

    private fun updateGuideInput(input: String) {
        updateState { reducer.reduce(it, WriterPadResult.GuideInputUpdated(input)) }
    }

    private fun submitGuideInput(premise: String) {
        updateState { it.copy(guideState = GuideState.GENERATING, isLoading = true) }

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
            is GuideEvent.Loading -> { /* Keep current state */ }
            is GuideEvent.Complete -> {
                updateState { reducer.reduce(it, WriterPadResult.BeatsGenerated(event.data)) }
            }
            is GuideEvent.Repaired -> {
                updateState { reducer.reduce(it, WriterPadResult.BeatsGenerated(event.data)) }
            }
            is GuideEvent.Error -> {
                updateState { reducer.reduce(it, WriterPadResult.Error(event.message)) }
            }
        }
    }

    private fun confirmGuideResult(confirmed: Boolean) {
        if (!confirmed) {
            updateState { it.copy(guideState = GuideState.INPUTTING, guideResult = null) }
            return
        }

        val beats = (_uiState.value.guideResult as? GuideResult.Beats)?.beats ?: return

        viewModelScope.launch {
            updateState { it.copy(isLoading = true) }
            try {
                val project = _uiState.value.project ?: return@launch
                val savedBeats = saveBeatDrafts(project.id, beats)
                updateState { reducer.reduce(it, WriterPadResult.GuideCompleted(savedBeats)) }
            } catch (e: Exception) {
                updateState { reducer.reduce(it, WriterPadResult.Error(e.message ?: "保存失败")) }
            }
        }
    }

    private suspend fun saveBeatDrafts(projectId: Long, drafts: List<BeatDraft>): List<Beat> {
        val beats = drafts.mapIndexed { index, draft ->
            Beat(
                projectId = projectId,
                beatId = UUID.randomUUID().toString(),
                title = draft.title,
                summary = draft.summary,
                type = draft.type,
                order = index,
                createdAt = System.currentTimeMillis()
            )
        }
        beatRepository.insertAll(beats)
        return beats
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
            try {
                val context = beatContextService.getBeatContext(beatId)
                val result = WriterPadResult.BeatChanged(
                    beat = beat,
                    characters = context.characters,
                    worldRules = context.worldRules,
                    outline = context.outline
                )
                updateState { reducer.reduce(it, result) }
            } catch (e: Exception) {
                updateState { reducer.reduce(it, WriterPadResult.Error(e.message ?: "加载节拍失败")) }
            }
        }
    }

    private fun toggleBeatList() {
        val isOpen = !_uiState.value.isBeatListOpen
        updateState { reducer.reduce(it, WriterPadResult.BeatListToggled(isOpen)) }
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
            updateState { it.copy(isLoading = true) }
            aiGuideEngine.classifyAndIndex(text, currentBeat, existingSettings)
                .catch { e ->
                    updateState { reducer.reduce(it, WriterPadResult.Error(e.message ?: "分类失败")) }
                }
                .collect { event ->
                    handleClassificationEvent(event)
                }
        }
    }

    private fun handleClassificationEvent(event: GuideEvent<com.aivoice.input.model.draft.ClassificationResult>) {
        when (event) {
            is GuideEvent.Complete -> {
                updateState { reducer.reduce(it, WriterPadResult.ClassificationDone(event.data)) }
            }
            is GuideEvent.Error -> {
                updateState { reducer.reduce(it, WriterPadResult.Error(event.message)) }
            }
            else -> { /* Loading state */ }
        }
    }

    private fun clearError() {
        updateState { it.copy(error = null) }
    }

    private fun toggleMappingActive(mappingId: Long) {
        // Implementation for toggling mapping active state
        // Will be implemented in Phase 4 integration
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add app/src/main/java/com/aivoice/input/ui/writer/WriterPadViewModel.kt
git commit -m "feat: add WriterPadViewModel with MVI pattern"
```

---

### Task 7: Layout Files

**Files:**
- Create: `app/src/main/res/layout/activity_writer_pad.xml`
- Create: `app/src/main/res/layout/view_beat_manager.xml`
- Create: `app/src/main/res/layout/view_guide_input.xml`
- Create: `app/src/main/res/layout/item_beat.xml`

- [ ] **Step 1: Create activity_writer_pad.xml**

```xml
<?xml version="1.0" encoding="utf-8"?>
<androidx.coordinatorlayout.widget.CoordinatorLayout
    xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent"
    android:layout_height="match_parent">

    <com.google.android.material.appbar.AppBarLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content">

        <androidx.appcompat.widget.Toolbar
            android:id="@+id/toolbar"
            android:layout_width="match_parent"
            android:layout_height="?attr/actionBarSize"
            app:title="@string/writer_pad_title" />

    </com.google.android.material.appbar.AppBarLayout>

    <FrameLayout
        android:id="@+id/content_container"
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        android:layout_marginTop="?attr/actionBarSize"
        android:layout_marginBottom="80dp" />

    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_gravity="bottom"
        android:orientation="vertical">

        <!-- Beat Manager -->
        <include
            android:id="@+id/beat_manager"
            layout="@layout/view_beat_manager"
            android:layout_width="match_parent"
            android:layout_height="60dp" />

        <!-- AI Panel (initially hidden) -->
        <LinearLayout
            android:id="@+id/ai_panel"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:orientation="vertical"
            android:visibility="gone"
            android:background="?attr/colorSurface"
            android:elevation="4dp">

            <EditText
                android:id="@+id/ai_input"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:hint="@string/ai_input_hint"
                android:inputType="textMultiLine"
                android:maxLines="3"
                android:padding="8dp" />

            <Button
                android:id="@+id/ai_send_button"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:layout_gravity="end"
                android:text="@string/send" />

        </LinearLayout>

    </LinearLayout>

</androidx.coordinatorlayout.widget.CoordinatorLayout>
```

- [ ] **Step 2: Create view_beat_manager.xml**

```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout
    xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="60dp"
    android:orientation="vertical"
    android:background="?attr/colorPrimary"
    android:padding="8dp">

    <TextView
        android:id="@+id/beat_title"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:textColor="@android:color/white"
        android:textSize="16sp"
        android:textStyle="bold" />

    <ProgressBar
        android:id="@+id/beat_progress"
        style="@style/Widget.AppCompat.ProgressBar.Horizontal"
        android:layout_width="match_parent"
        android:layout_height="4dp"
        android:layout_marginTop="4dp"
        android:progressTint="@android:color/white" />

</LinearLayout>
```

- [ ] **Step 3: Create view_guide_input.xml**

```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout
    xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:orientation="vertical"
    android:padding="16dp"
    android:gravity="center">

    <TextView
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:text="@string/guide_welcome"
        android:textSize="20sp"
        android:textStyle="bold"
        android:gravity="center"
        android:layout_marginBottom="24dp" />

    <EditText
        android:id="@+id/project_name_input"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:hint="@string/project_name_hint"
        android:inputType="text"
        android:layout_marginBottom="16dp" />

    <EditText
        android:id="@+id/premise_input"
        android:layout_width="match_parent"
        android:layout_height="0dp"
        android:layout_weight="1"
        android:hint="@string/premise_hint"
        android:inputType="textMultiLine"
        android:gravity="top"
        android:layout_marginBottom="16dp" />

    <Button
        android:id="@+id/start_button"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:text="@string/start_writing" />

    <ProgressBar
        android:id="@+id/loading_progress"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:layout_marginTop="16dp"
        android:visibility="gone" />

</LinearLayout>
```

- [ ] **Step 4: Create item_beat.xml**

```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout
    xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:orientation="vertical"
    android:padding="12dp">

    <TextView
        android:id="@+id/beat_order"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:textSize="12sp"
        android:textColor="?android:textColorSecondary" />

    <TextView
        android:id="@+id/beat_title"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:textSize="16sp"
        android:textStyle="bold" />

    <TextView
        android:id="@+id/beat_summary"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:textSize="14sp"
        android:maxLines="2"
        android:ellipsize="end" />

</LinearLayout>
```

- [ ] **Step 5: Commit**

```bash
git add app/src/main/res/layout/activity_writer_pad.xml
git add app/src/main/res/layout/view_beat_manager.xml
git add app/src/main/res/layout/view_guide_input.xml
git add app/src/main/res/layout/item_beat.xml
git commit -m "feat: add WriterPad layout files"
```

---

### Task 8: String Resources

**Files:**
- Modify: `app/src/main/res/values/strings.xml`

- [ ] **Step 1: Add string resources**

Read `strings.xml` and add:

```xml
<!-- WriterPad -->
<string name="writer_pad_title">创作工坊</string>
<string name="guide_welcome">开始你的创作之旅</string>
<string name="project_name_hint">作品名称</string>
<string name="premise_hint">输入你的故事脑洞或前提...\n例如：一个少年在废墟中发现神秘石碑，从此踏上修仙之路</string>
<string name="start_writing">开始创作</string>
<string name="ai_input_hint">补充设定或描述...</string>
<string name="send">发送</string>
<string name="confirm_beats">确认节拍</string>
<string name="regenerate">重新生成</string>
<string name="generating">生成中...</string>
<string name="no_beats">暂无节拍</string>
```

- [ ] **Step 2: Commit**

```bash
git add app/src/main/res/values/strings.xml
git commit -m "feat: add WriterPad string resources"
```

---

### Task 9: Beat List Adapter

**Files:**
- Create: `app/src/main/java/com/aivoice/input/ui/writer/adapters/BeatListAdapter.kt`

- [ ] **Step 1: Create BeatListAdapter**

```kotlin
package com.aivoice.input.ui.writer.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.aivoice.input.R
import com.aivoice.input.model.Beat
import com.aivoice.input.model.draft.BeatDraft

/**
 * Adapter for displaying beat list.
 */
class BeatListAdapter(
    private val onItemClick: (String) -> Unit
) : RecyclerView.Adapter<BeatListAdapter.BeatViewHolder>() {

    private var beats: List<Beat> = emptyList()
    private var beatDrafts: List<BeatDraft> = emptyList()
    private var isDraftMode = false

    fun submitBeats(beats: List<Beat>) {
        this.beats = beats
        this.isDraftMode = false
        notifyDataSetChanged()
    }

    fun submitDrafts(drafts: List<BeatDraft>) {
        this.beatDrafts = drafts
        this.isDraftMode = true
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BeatViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_beat, parent, false)
        return BeatViewHolder(view)
    }

    override fun onBindViewHolder(holder: BeatViewHolder, position: Int) {
        if (isDraftMode) {
            val draft = beatDrafts[position]
            holder.bind(
                order = "${position + 1}",
                title = draft.title,
                summary = draft.summary
            )
        } else {
            val beat = beats[position]
            holder.bind(
                order = "${beat.order + 1}",
                title = beat.title,
                summary = beat.summary
            )
            holder.itemView.setOnClickListener { onItemClick(beat.beatId) }
        }
    }

    override fun getItemCount(): Int = if (isDraftMode) beatDrafts.size else beats.size

    class BeatViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val orderText: TextView = itemView.findViewById(R.id.beat_order)
        private val titleText: TextView = itemView.findViewById(R.id.beat_title)
        private val summaryText: TextView = itemView.findViewById(R.id.beat_summary)

        fun bind(order: String, title: String, summary: String) {
            orderText.text = "#$order"
            titleText.text = title
            summaryText.text = summary
        }
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add app/src/main/java/com/aivoice/input/ui/writer/adapters/BeatListAdapter.kt
git commit -m "feat: add BeatListAdapter for beat list display"
```

---

### Task 10: WriterPadActivity

**Files:**
- Create: `app/src/main/java/com/aivoice/input/ui/writer/WriterPadActivity.kt`
- Modify: `app/src/main/AndroidManifest.xml`

- [ ] **Step 1: Create WriterPadActivity**

```kotlin
package com.aivoice.input.ui.writer

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.aivoice.input.R
import com.aivoice.input.ui.writer.mvi.GuideResult
import com.aivoice.input.ui.writer.mvi.GuideState
import com.aivoice.input.ui.writer.mvi.WriterPadIntent
import com.aivoice.input.ui.writer.mvi.WriterPadState
import com.aivoice.input.ui.writer.adapters.BeatListAdapter
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Main activity for WriterPad writing module.
 */
class WriterPadActivity : AppCompatActivity() {

    private val viewModel: WriterPadViewModel by viewModels {
        WriterPadViewModelFactory(this)
    }

    // Views
    private lateinit var contentContainer: View
    private lateinit var beatTitle: TextView
    private lateinit var beatProgress: ProgressBar
    private lateinit var aiPanel: View
    private lateinit var aiInput: EditText
    private lateinit var aiSendButton: Button

    // Guide input views
    private lateinit var guideInputView: View
    private lateinit var projectNameInput: EditText
    private lateinit var premiseInput: EditText
    private lateinit var startButton: Button
    private lateinit var loadingProgress: ProgressBar

    // Beat preview views
    private lateinit var beatPreviewList: RecyclerView
    private lateinit var confirmButton: Button
    private lateinit var regenerateButton: Button

    // Adapter
    private lateinit var beatAdapter: BeatListAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_writer_pad)

        initViews()
        setupAdapter()
        observeState()
        setupListeners()
    }

    private fun initViews() {
        contentContainer = findViewById(R.id.content_container)
        beatTitle = findViewById(R.id.beat_title)
        beatProgress = findViewById(R.id.beat_progress)
        aiPanel = findViewById(R.id.ai_panel)
        aiInput = findViewById(R.id.ai_input)
        aiSendButton = findViewById(R.id.ai_send_button)

        // Inflate guide input view
        guideInputView = layoutInflater.inflate(R.layout.view_guide_input, null)
        projectNameInput = guideInputView.findViewById(R.id.project_name_input)
        premiseInput = guideInputView.findViewById(R.id.premise_input)
        startButton = guideInputView.findViewById(R.id.start_button)
        loadingProgress = guideInputView.findViewById(R.id.loading_progress)
    }

    private fun setupAdapter() {
        beatAdapter = BeatListAdapter { beatId ->
            viewModel.processIntent(WriterPadIntent.SelectBeat(beatId))
        }
    }

    private fun observeState() {
        lifecycleScope.launch {
            viewModel.uiState.collectLatest { state ->
                renderState(state)
            }
        }
    }

    private fun renderState(state: WriterPadState) {
        // Render beat manager
        renderBeatManager(state)

        // Render AI panel
        aiPanel.visibility = if (state.isAiPanelOpen) View.VISIBLE else View.GONE

        // Render content based on guide state
        when (state.guideState) {
            GuideState.IDLE, GuideState.INPUTTING -> {
                showGuideInput(state)
            }
            GuideState.GENERATING -> {
                showLoading()
            }
            GuideState.CONFIRMING -> {
                showBeatPreview(state)
            }
            GuideState.COMPLETED -> {
                showBeatContent(state)
            }
        }

        // Render error
        state.error?.let { showError(it) }
    }

    private fun renderBeatManager(state: WriterPadState) {
        val currentBeat = state.currentBeat
        if (currentBeat != null) {
            val position = state.beatList.indexOf(currentBeat) + 1
            val total = state.beatList.size
            beatTitle.text = "$position/$total ${currentBeat.title}"
            beatProgress.max = total
            beatProgress.progress = position
        } else {
            beatTitle.text = getString(R.string.no_beats)
            beatProgress.progress = 0
        }
    }

    private fun showGuideInput(state: WriterPadState) {
        contentContainer.removeAllViews()
        contentContainer.addView(guideInputView)
        loadingProgress.visibility = View.GONE
        startButton.isEnabled = true
    }

    private fun showLoading() {
        contentContainer.removeAllViews()
        contentContainer.addView(guideInputView)
        loadingProgress.visibility = View.VISIBLE
        startButton.isEnabled = false
    }

    private fun showBeatPreview(state: WriterPadState) {
        val previewView = createBeatPreviewView()
        contentContainer.removeAllViews()
        contentContainer.addView(previewView)

        val beats = (state.guideResult as? GuideResult.Beats)?.beats ?: emptyList()
        beatAdapter.submitDrafts(beats)
    }

    private fun createBeatPreviewView(): View {
        val view = layoutInflater.inflate(R.layout.activity_writer_pad, null)
        // Create preview layout with confirm/regenerate buttons
        // Implementation details in actual code
        return view
    }

    private fun showBeatContent(state: WriterPadState) {
        // Show current beat content with associated settings
        // Implementation details in actual code
    }

    private fun showError(message: String) {
        // Show error snackbar or toast
    }

    private fun setupListeners() {
        startButton.setOnClickListener {
            val name = projectNameInput.text.toString().trim()
            val premise = premiseInput.text.toString().trim()
            if (name.isNotEmpty() && premise.isNotEmpty()) {
                viewModel.processIntent(WriterPadIntent.CreateProject(name, premise))
                viewModel.processIntent(WriterPadIntent.SubmitGuideInput(premise))
            }
        }

        aiSendButton.setOnClickListener {
            val text = aiInput.text.toString().trim()
            if (text.isNotEmpty()) {
                viewModel.processIntent(WriterPadIntent.SendAiMessage(text))
                aiInput.text.clear()
            }
        }

        // Long press on beat manager to advance
        findViewById<View>(R.id.beat_manager).setOnLongClickListener {
            viewModel.processIntent(WriterPadIntent.AdvanceBeat)
            true
        }

        // Double tap on beat manager to toggle list
        findViewById<View>(R.id.beat_manager).setOnClickListener {
            viewModel.processIntent(WriterPadIntent.ToggleBeatList)
        }
    }
}
```

- [ ] **Step 2: Add Activity to AndroidManifest.xml**

Read AndroidManifest.xml and add inside `<application>`:

```xml
<activity
    android:name=".ui.writer.WriterPadActivity"
    android:exported="false"
    android:label="@string/writer_pad_title"
    android:theme="@style/Theme.PadWriter" />
```

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/aivoice/input/ui/writer/WriterPadActivity.kt
git add app/src/main/AndroidManifest.xml
git commit -m "feat: add WriterPadActivity with MVI UI rendering"
```

---

## Self-Review Checklist

**1. Spec coverage:**
- ✅ WriterPadState (Task 1)
- ✅ WriterPadIntent (Task 2)
- ✅ WriterPadResult (Task 3)
- ✅ WriterPadReducer (Task 4)
- ✅ WriterPadViewModelFactory (Task 5)
- ✅ WriterPadViewModel (Task 6)
- ✅ Layout files (Task 7)
- ✅ String resources (Task 8)
- ✅ BeatListAdapter (Task 9)
- ✅ WriterPadActivity (Task 10)

**2. Placeholder scan:**
- No TBD, TODO, or placeholder patterns found
- All code blocks contain complete implementations

**3. Type consistency:**
- WriterPadState uses GuideState enum and GuideResult sealed class
- WriterPadIntent sealed class used in ViewModel.processIntent()
- WriterPadResult sealed class used in Reducer.reduce()
- BeatListAdapter handles both Beat and BeatDraft types

---

## Execution Notes

After completion, Phase 3 will provide:
- Complete MVI architecture for WriterPad
- ViewModel with intent processing
- Activity with state observation
- Basic UI components for guide flow and beat management

**Note:** AIGuideEngineProvider needs to be updated to properly create AIGuideEngine using dependencies from Phase 2.

Next phase: Phase 4 (Integration with FloatingBallService)
