package com.aivoice.input.model.draft

import com.aivoice.input.model.enums.BeatType

data class BeatDraft(
    val title: String,
    val summary: String,
    val type: BeatType
)
