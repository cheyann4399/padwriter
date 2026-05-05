package com.aivoice.input.model

/**
 * Beat context for context-aware polishing.
 * Passed from WriterPad to FloatingBallService.
 */
data class BeatContext(
    val beatId: String,
    val beatTitle: String,
    val beatSummary: String,
    val characters: List<CharacterSummary> = emptyList(),
    val worldRules: List<WorldRuleSummary> = emptyList(),
    val outlineSummary: String? = null,
    val beatList: List<BeatInfo> = emptyList(),  // 新增：节拍列表
    val currentBeatIndex: Int = 0  // 新增：当前节拍索引
)

/**
 * 节拍简要信息，用于悬浮球显示
 */
data class BeatInfo(
    val beatId: String,
    val title: String,
    val summary: String = ""
)

data class CharacterSummary(
    val name: String,
    val content: String
)

data class WorldRuleSummary(
    val title: String,
    val content: String
)
