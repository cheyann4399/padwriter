package com.aivoice.input.ui.writer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aivoice.input.ai.GuideEvent
import com.aivoice.input.ai.ExistingSettings
import com.aivoice.input.model.Beat
import com.aivoice.input.model.BeatInfo
import com.aivoice.input.model.Character
import com.aivoice.input.model.WorldRule
import com.aivoice.input.model.BeatContext
import com.aivoice.input.model.CharacterSummary
import com.aivoice.input.model.WorldRuleSummary
import com.aivoice.input.model.draft.BeatDraft
import com.aivoice.input.model.draft.ClassificationResult
import com.aivoice.input.repository.BeatContextService
import com.aivoice.input.repository.BeatRepository
import com.aivoice.input.repository.ProjectRepository
import com.aivoice.input.repository.GlossaryRepository
import com.aivoice.input.ai.AIGuideEngine
import com.aivoice.input.service.FloatingBallService
import com.aivoice.input.ui.writer.mvi.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import java.util.UUID
import java.lang.ref.WeakReference

import android.util.Log

/**
 * ViewModel for WriterPad screen.
 * Processes intents and updates UI state.
 */
class WriterPadViewModel(
    private val projectRepository: ProjectRepository,
    private val beatRepository: BeatRepository,
    private val beatContextService: BeatContextService,
    private val aiGuideEngine: AIGuideEngine,
    private val reducer: WriterPadReducer,
    private val glossaryRepository: GlossaryRepository
) : ViewModel() {

    companion object {
        private const val TAG = "WriterPadViewModel"
    }

    private val _uiState = MutableStateFlow(WriterPadState())
    val uiState: StateFlow<WriterPadState> = _uiState.asStateFlow()

    // All projects for project list dialog
    val allProjects: kotlinx.coroutines.flow.Flow<List<com.aivoice.input.model.Project>> = projectRepository.getAllProjects()

    // Current project ID for glossary
    private val _currentProjectId = MutableStateFlow(0L)

    // Glossary flow - switches based on current project
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val glossary: kotlinx.coroutines.flow.Flow<List<com.aivoice.input.model.Glossary>> = _currentProjectId.flatMapLatest { projectId ->
        if (projectId > 0) {
            glossaryRepository.getGlossaryForProject(projectId)
        } else {
            kotlinx.coroutines.flow.flowOf(emptyList())
        }
    }

    // Reference to FloatingBallService for context sync (WeakReference to avoid memory leak)
    private var floatingBallServiceRef: WeakReference<FloatingBallService>? = null

    fun setFloatingBallService(service: FloatingBallService?) {
        this.floatingBallServiceRef = if (service != null) WeakReference(service) else null
    }

    fun processIntent(intent: WriterPadIntent) {
        Log.d(TAG, "processIntent: $intent")
        when (intent) {
            is WriterPadIntent.LoadLatestProject -> loadLatestProject()
            is WriterPadIntent.LoadProject -> loadProject(intent.projectId)
            is WriterPadIntent.CreateProject -> createProject(intent.name, intent.premise)
            is WriterPadIntent.DeleteProject -> deleteProject(intent.projectId)
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

    private fun loadLatestProject() {
        viewModelScope.launch {
            updateState { it.copy(isLoading = true) }
            try {
                val projects = projectRepository.getAllProjects().first()
                Log.d(TAG, "loadLatestProject: found ${projects.size} projects")
                if (projects.isNotEmpty()) {
                    val latestProject = projects.first()
                    _currentProjectId.value = latestProject.id  // Update for glossary
                    val beats = beatRepository.getBeatsForProject(latestProject.id).first()
                    Log.d(TAG, "loadLatestProject: loading project ${latestProject.id} with ${beats.size} beats")
                    val result = WriterPadResult.ProjectLoaded(latestProject, beats)
                    updateState { reducer.reduce(it, result) }
                    // If project has beats, go to COMPLETED state
                    if (beats.isNotEmpty()) {
                        updateState { it.copy(guideState = GuideState.COMPLETED) }
                    }
                } else {
                    Log.d(TAG, "loadLatestProject: no projects, staying in IDLE")
                    updateState { it.copy(isLoading = false, guideState = GuideState.IDLE) }
                }
            } catch (e: Exception) {
                Log.e(TAG, "loadLatestProject: error: ${e.message}", e)
                updateState { reducer.reduce(it, WriterPadResult.Error(e.message ?: "Load failed")) }
            }
        }
    }

    private fun loadProject(projectId: Long) {
        viewModelScope.launch {
            updateState { it.copy(isLoading = true) }
            try {
                val project = projectRepository.getProjectById(projectId)
                if (project != null) {
                    _currentProjectId.value = projectId  // Update for glossary

                    val beats = beatRepository.getBeatsForProject(projectId).first()
                    val result = WriterPadResult.ProjectLoaded(project, beats)
                    updateState { reducer.reduce(it, result) }

                    // If project has beats, load first beat context and go to COMPLETED state
                    if (beats.isNotEmpty()) {
                        val firstBeat = beats.first()
                        val context = beatContextService.loadBeatContext(firstBeat.beatId)
                        updateState {
                            it.copy(
                                currentBeat = firstBeat,
                                characters = context.characters,
                                worldRules = context.worldRules,
                                outline = context.outline,
                                guideState = GuideState.COMPLETED,
                                isLoading = false
                            )
                        }
                    } else {
                        updateState { it.copy(guideState = GuideState.INPUTTING, isLoading = false) }
                    }

                    Log.d(TAG, "loadProject: loaded project $projectId with ${beats.size} beats")
                } else {
                    updateState { reducer.reduce(it, WriterPadResult.Error("Project not found")) }
                }
            } catch (e: Exception) {
                Log.e(TAG, "loadProject: error: ${e.message}", e)
                updateState { reducer.reduce(it, WriterPadResult.Error(e.message ?: "Load failed")) }
            }
        }
    }

    private fun createProject(name: String, premise: String) {
        Log.d(TAG, "createProject: name='$name', premise='${premise.take(50)}'")
        viewModelScope.launch {
            updateState { it.copy(isLoading = true, guideState = GuideState.GENERATING) }
            try {
                val project = projectRepository.createProject(name, premise)
                Log.d(TAG, "createProject: success, project id=${project.id}")
                _currentProjectId.value = project.id  // Update for glossary
                updateState { reducer.reduce(it, WriterPadResult.ProjectCreated(project)) }

                // 创建项目后自动开始 AI 生成
                doSubmitGuideInput(premise, project)
            } catch (e: Exception) {
                Log.e(TAG, "createProject: error: ${e.message}", e)
                updateState { reducer.reduce(it, WriterPadResult.Error(e.message ?: "Create failed")) }
            }
        }
    }

    private fun deleteProject(projectId: Long) {
        viewModelScope.launch {
            try {
                projectRepository.deleteProject(projectId)
                Log.d(TAG, "deleteProject: deleted project $projectId")

                // 如果删除的是当前项目，加载最新的项目
                if (_uiState.value.project?.id == projectId) {
                    loadLatestProject()
                }
            } catch (e: Exception) {
                Log.e(TAG, "deleteProject: error: ${e.message}", e)
                updateState { reducer.reduce(it, WriterPadResult.Error(e.message ?: "Delete failed")) }
            }
        }
    }

    private fun submitGuideInput(premise: String) {
        Log.d(TAG, "submitGuideInput: premise='${premise.take(100)}'")

        val project = _uiState.value.project
        if (project == null) {
            Log.e(TAG, "submitGuideInput: project is null")
            updateState { reducer.reduce(it, WriterPadResult.Error("请先创建项目")) }
            return
        }

        // 防止重复调用
        if (_uiState.value.guideState == GuideState.GENERATING) {
            Log.w(TAG, "submitGuideInput: already generating, skipping")
            return
        }

        updateState { it.copy(guideState = GuideState.GENERATING, isLoading = true) }
        doSubmitGuideInput(premise, project)
    }

    private fun startGuide() {
        updateState { it.copy(guideState = GuideState.INPUTTING) }
    }

    private fun updateGuideInput(input: String) {
        updateState { reducer.reduce(it, WriterPadResult.GuideInputUpdated(input)) }
    }

    private fun doSubmitGuideInput(premise: String, project: com.aivoice.input.model.Project) {
        // 使用统一入口处理，AI 会自动判断生成节拍、人设、世界观、词库
        viewModelScope.launch {
            Log.d(TAG, "doSubmitGuideInput: starting aiGuideEngine.processInput")
            aiGuideEngine.processInput(
                input = premise,
                beats = emptyList(),  // 当前无节拍
                characters = emptyList(),
                worldRules = emptyList(),
                outlines = emptyList(),
                currentBeat = null
            )
                .catch { e ->
                    Log.e(TAG, "doSubmitGuideInput: error: ${e.message}", e)
                    updateState { reducer.reduce(it, WriterPadResult.Error(e.message ?: "Generation failed")) }
                }
                .collect { event ->
                    Log.d(TAG, "doSubmitGuideInput: received event: $event")
                    handleInitialGenerationEvent(event, project.id)
                }
        }
    }

    private fun handleInitialGenerationEvent(event: GuideEvent<com.aivoice.input.model.router.ExecutionResult>, projectId: Long) {
        Log.d(TAG, "handleInitialGenerationEvent: $event")
        when (event) {
            is GuideEvent.Loading -> {
                Log.d(TAG, "handleInitialGenerationEvent: Loading...")
            }
            is GuideEvent.Complete, is GuideEvent.Repaired -> {
                val result = if (event is GuideEvent.Complete) event.data else (event as GuideEvent.Repaired).data
                Log.d(TAG, "handleInitialGenerationEvent: Complete with beats=${result.beats?.beats?.size}, chars=${result.characters?.created?.size}, rules=${result.worldRules?.created?.size}")

                // 保存结果到数据库
                saveInitialGenerationResult(projectId, result)
            }
            is GuideEvent.Error -> {
                Log.e(TAG, "handleInitialGenerationEvent: Error: ${event.message}")
                updateState { reducer.reduce(it, WriterPadResult.Error(event.message)) }
            }
            is GuideEvent.Partial -> {
                Log.d(TAG, "handleInitialGenerationEvent: Partial data")
            }
        }
    }

    private fun saveInitialGenerationResult(projectId: Long, result: com.aivoice.input.model.router.ExecutionResult) {
        viewModelScope.launch {
            try {
                // 统一使用 beatContextService 保存所有内容（包括节拍）
                // 先获取空的 beatIds，因为节拍还没创建
                beatContextService.saveExecutionResult(projectId, result, emptyList())

                // 保存完成后，重新加载节拍列表
                val savedBeats = beatRepository.getBeatsForProject(projectId).first()
                val allBeatIds = savedBeats.map { it.beatId }

                // 现在需要更新映射关系，把人设/世界观关联到所有节拍
                if (allBeatIds.isNotEmpty() && result.mappings.isNotEmpty()) {
                    // 映射关系已经在 saveExecutionResult 中处理了
                }

                // 更新 UI 状态
                if (savedBeats.isNotEmpty()) {
                    // 加载第一个节拍的上下文（人设、世界观、大纲）
                    val firstBeat = savedBeats.first()
                    val context = beatContextService.loadBeatContext(firstBeat.beatId)
                    Log.d(TAG, "saveInitialGenerationResult: loaded context for beat ${firstBeat.beatId}, characters=${context.characters.size}, worldRules=${context.worldRules.size}")

                    updateState {
                        it.copy(
                            beatList = savedBeats,
                            currentBeat = firstBeat,
                            characters = context.characters,
                            worldRules = context.worldRules,
                            outline = context.outline,
                            guideState = GuideState.CONFIRMING,
                            guideResult = GuideResult.Beats(result.beats?.beats ?: emptyList()),
                            isLoading = false
                        )
                    }
                } else {
                    updateState { it.copy(isLoading = false, guideState = GuideState.COMPLETED) }
                }

                // 更新当前项目ID（用于词库）
                _currentProjectId.value = projectId

                Log.d(TAG, "saveInitialGenerationResult: completed, beats=${savedBeats.size}")
            } catch (e: Exception) {
                Log.e(TAG, "saveInitialGenerationResult: error=${e.message}", e)
                updateState { reducer.reduce(it, WriterPadResult.Error(e.message ?: "保存失败")) }
            }
        }
    }

    private fun confirmGuideResult(confirmed: Boolean) {
        if (!confirmed) {
            updateState { it.copy(guideState = GuideState.INPUTTING, guideResult = null) }
            return
        }

        // 节拍已经在 saveInitialGenerationResult 中保存了
        // 直接切换到完成状态
        viewModelScope.launch {
            val project = _uiState.value.project ?: return@launch
            val savedBeats = beatRepository.getBeatsForProject(project.id).first()

            updateState {
                it.copy(
                    beatList = savedBeats,
                    currentBeat = savedBeats.firstOrNull(),
                    guideState = GuideState.COMPLETED,
                    guideResult = null,
                    isLoading = false
                )
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
        // Insert beats one by one since there's no insertAll
        beats.forEach { beat ->
            beatRepository.createBeat(
                projectId = beat.projectId,
                title = beat.title,
                summary = beat.summary,
                type = beat.type
            )
        }
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
        context: com.aivoice.input.repository.BeatContext
    ) {
        val beatList = _uiState.value.beatList
        val currentIndex = beatList.indexOf(beat)

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
            outlineSummary = context.outline?.content?.let { truncate(it, 500) },
            beatList = beatList.map { BeatInfo(it.beatId, it.title) },
            currentBeatIndex = currentIndex
        )
        floatingBallServiceRef?.get()?.updateBeatContext(beatContext)
    }

    private fun truncate(text: String, maxLength: Int): String {
        return if (text.length <= maxLength) text
        else text.take(maxLength) + "..."
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
        Log.d(TAG, "sendAiMessage: text='$text'")
        val project = _uiState.value.project
        if (project == null) {
            Log.e(TAG, "sendAiMessage: project is null, returning")
            return
        }

        val currentBeat = _uiState.value.currentBeat
        val beats = _uiState.value.beatList

        // Extract Character and WorldRule from CharacterWithContext and WorldRuleWithContext
        val characters = _uiState.value.characters.map { context ->
            Character(
                id = context.id,
                projectId = context.projectId,
                charId = context.charId,
                name = context.name,
                content = context.content,
                createdAt = context.createdAt,
                updatedAt = context.updatedAt
            )
        }
        val worldRules = _uiState.value.worldRules.map { context ->
            WorldRule(
                id = context.id,
                projectId = context.projectId,
                ruleId = context.ruleId,
                title = context.title,
                content = context.content,
                createdAt = context.createdAt,
                updatedAt = context.updatedAt
            )
        }

        // 获取大纲列表
        val outlines = _uiState.value.outline?.let { listOf(it) } ?: emptyList()

        Log.d(TAG, "sendAiMessage: calling processInput with unified agent")
        viewModelScope.launch {
            updateState { it.copy(isLoading = true) }
            aiGuideEngine.processInput(
                input = text,
                beats = beats,
                characters = characters,
                worldRules = worldRules,
                outlines = outlines,
                currentBeat = currentBeat
            )
                .catch { e ->
                    Log.e(TAG, "sendAiMessage: error: ${e.message}", e)
                    updateState { reducer.reduce(it, WriterPadResult.Error(e.message ?: "处理失败")) }
                }
                .collect { event ->
                    Log.d(TAG, "sendAiMessage: received event: $event")
                    handleExecutionEvent(event, project.id, beats.map { it.beatId })
                }
        }
    }

    private fun handleExecutionEvent(event: GuideEvent<com.aivoice.input.model.router.ExecutionResult>, projectId: Long, allBeatIds: List<String>) {
        Log.d(TAG, "handleExecutionEvent: event type=${event::class.simpleName}")
        when (event) {
            is GuideEvent.Complete, is GuideEvent.Repaired -> {
                val result = if (event is GuideEvent.Complete) event.data else (event as GuideEvent.Repaired).data
                Log.d(TAG, "handleExecutionEvent: calling saveExecutionResult")
                saveExecutionResult(projectId, result, allBeatIds)
            }
            is GuideEvent.Error -> {
                Log.e(TAG, "handleExecutionEvent: error=${event.message}")
                updateState { reducer.reduce(it, WriterPadResult.Error(event.message)) }
            }
            is GuideEvent.Loading -> { /* Keep loading state */ }
            is GuideEvent.Partial -> { /* Could show partial results */ }
        }
    }

    private fun saveExecutionResult(projectId: Long, result: com.aivoice.input.model.router.ExecutionResult, allBeatIds: List<String>) {
        Log.d(TAG, "saveExecutionResult: called")
        val currentBeat = _uiState.value.currentBeat

        viewModelScope.launch {
            try {
                Log.d(TAG, "saveExecutionResult: calling beatContextService.saveExecutionResult")
                beatContextService.saveExecutionResult(
                    projectId = projectId,
                    result = result,
                    existingBeatIds = allBeatIds
                )
                Log.d(TAG, "saveExecutionResult: saved successfully")

                // 如果有节拍变更，重新加载节拍列表
                if (result.beats != null && result.beats.action == "CREATE") {
                    val newBeats = beatRepository.getBeatsForProject(projectId).first()
                    updateState { it.copy(beatList = newBeats, currentBeat = newBeats.firstOrNull()) }
                }

                // Reload beat context to refresh characters, worldRules, outline
                val beatToLoad = currentBeat?.beatId ?: allBeatIds.firstOrNull()
                if (beatToLoad != null) {
                    val context = beatContextService.loadBeatContext(beatToLoad)
                    Log.d(TAG, "saveExecutionResult: loaded context, characters=${context.characters.size}, worldRules=${context.worldRules.size}")
                    val beat = _uiState.value.beatList.find { it.beatId == beatToLoad }
                    if (beat != null) {
                        updateState { reducer.reduce(it, WriterPadResult.BeatChanged(
                            beat = beat,
                            characters = context.characters,
                            worldRules = context.worldRules,
                            outline = context.outline
                        )) }
                        syncBeatContextToService(beat, context)
                    }
                }

                // 显示 AI 反馈
                if (result.feedback.isNotEmpty()) {
                    // 可以考虑显示一个 Toast 或者更新 UI 状态
                    Log.d(TAG, "saveExecutionResult: AI feedback: ${result.feedback}")
                }

                Log.d(TAG, "saveExecutionResult: completed")
            } catch (e: Exception) {
                Log.e(TAG, "saveExecutionResult: error=${e.message}", e)
                updateState { reducer.reduce(it, WriterPadResult.Error(e.message ?: "保存失败")) }
            }
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