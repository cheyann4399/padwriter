package com.aivoice.input.ui.writer.mvi

/**
 * User intentions for WriterPad screen.
 */
sealed class WriterPadIntent {
    // Project operations
    object LoadLatestProject : WriterPadIntent()
    data class LoadProject(val projectId: Long) : WriterPadIntent()
    data class CreateProject(val name: String, val premise: String) : WriterPadIntent()
    data class DeleteProject(val projectId: Long) : WriterPadIntent()

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