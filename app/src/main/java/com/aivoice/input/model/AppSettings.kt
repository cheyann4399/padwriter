package com.aivoice.input.model

data class AppSettings(
    val polishStyle: PolishStyle = PolishStyle.NATIVE,
    val floatingBallHidden: Boolean = false,
    val autoStart: Boolean = true
)
