package com.aivoice.input.ui.writer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aivoice.input.ai.GuideEvent
import com.aivoice.input.ai.ExistingSettings
import com.aivoice.input.model.Beat
import com.aivoice.input.model.Character
import com.aivoice.input.model.WorldRule
import com.aivoice.input.model.draft.BeatDraft
import com.aivoice.input.model.draft.ClassificationResult
import com.aivoice.input.repository.BeatContextService
import com.aivoice.input.repository.BeatRepository
import com.aivoice.input.repository.ProjectRepository
import com.aivoice.input.ai.AIGuideEngine
import com.aivoice.input.ui.writer.mvi.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
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
                val project = projectRepository.getProjectById(projectId)
                if (project != null) {
                    // Use take(1) to get the first emission and avoid indefinite collection
                    val beats = beatRepository.getBeatsForProject(projectId).take(1).first()
                    val result = WriterPadResult.ProjectLoaded(project, beats)
                    updateState { reducer.reduce(it, result) }
                } else {
                    updateState { reducer.reduce(it, WriterPadResult.Error("Project not found")) }
                }
            } catch (e: Exception) {
                updateState { reducer.reduce(it, WriterPadResult.Error(e.message ?: "Load failed")) }
            }
        }
    }

    private fun createProject(name: String, premise: String) {
        viewModelScope.launch {
            updateState { it.copy(isLoading = true) }
            try {
                val project = projectRepository.createProject(name, premise)
                updateState { reducer.reduce(it, WriterPadResult.ProjectCreated(project)) }
            } catch (e: Exception) {
                updateState { reducer.reduce(it, WriterPadResult.Error(e.message ?: "Create failed")) }
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
                    updateState { reducer.reduce(it, WriterPadResult.Error(e.message ?: "Generation failed")) }
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
            is GuideEvent.Partial -> {
                // Could update UI with partial results for streaming display
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
                updateState { reducer.reduce(it, WriterPadResult.Error(e.message ?: "Save failed")) }
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
            } catch (e: Exception) {
                updateState { reducer.reduce(it, WriterPadResult.Error(e.message ?: "Load beat failed")) }
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

        val existingSettings = ExistingSettings(
            characters = characters,
            worldRules = worldRules,
            currentBeatId = currentBeat.beatId
        )

        viewModelScope.launch {
            updateState { it.copy(isLoading = true) }
            aiGuideEngine.classifyAndIndex(text, currentBeat, existingSettings)
                .catch { e ->
                    updateState { reducer.reduce(it, WriterPadResult.Error(e.message ?: "Classification failed")) }
                }
                .collect { event ->
                    handleClassificationEvent(event)
                }
        }
    }

    private fun handleClassificationEvent(event: GuideEvent<ClassificationResult>) {
        when (event) {
            is GuideEvent.Complete -> {
                updateState { reducer.reduce(it, WriterPadResult.ClassificationDone(event.data)) }
            }
            is GuideEvent.Error -> {
                updateState { reducer.reduce(it, WriterPadResult.Error(event.message)) }
            }
            is GuideEvent.Loading -> { /* Keep loading state */ }
            is GuideEvent.Partial -> { /* Could show partial results */ }
            is GuideEvent.Repaired -> {
                updateState { reducer.reduce(it, WriterPadResult.ClassificationDone(event.data)) }
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