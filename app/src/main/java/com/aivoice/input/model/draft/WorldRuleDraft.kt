package com.aivoice.input.model.draft

import com.aivoice.input.model.enums.ContextType
import com.aivoice.input.model.enums.DraftAction

data class WorldRuleDraft(
    val title: String,
    val content: String,
    val action: DraftAction,
    val contextType: ContextType,
    val contextNote: String,
    val targetId: String = ""
)
