package com.aivoice.input.model.draft

import com.aivoice.input.model.enums.ContextType
import com.aivoice.input.model.enums.DraftAction

data class CharacterDraft(
    val name: String,
    val content: String,
    val action: DraftAction? = null,  // 可选，AI 可能不返回
    val contextType: ContextType,
    val contextNote: String,
    val targetId: String = ""
)
