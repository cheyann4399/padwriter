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
