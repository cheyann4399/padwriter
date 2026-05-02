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
