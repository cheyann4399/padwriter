package com.aivoice.input.network.rtasr

data class RTASRResult(
    val text: String,
    val isFinal: Boolean,
    val isMiddle: Boolean
)
