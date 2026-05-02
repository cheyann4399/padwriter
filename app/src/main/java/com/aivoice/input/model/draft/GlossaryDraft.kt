package com.aivoice.input.model.draft

import com.aivoice.input.model.enums.GlossaryPriority
import com.aivoice.input.model.enums.GlossaryType

data class GlossaryDraft(
    val word: String,
    val type: GlossaryType,
    val sourceId: String,
    val priority: GlossaryPriority
)
