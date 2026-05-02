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
