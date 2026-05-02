package com.aivoice.input.model.draft

import com.aivoice.input.model.enums.ContextType
import com.aivoice.input.model.enums.SettingType

data class MappingDraft(
    val beatId: String,
    val settingType: SettingType,
    val settingId: String,
    val contextType: ContextType,
    val contextNote: String
)
