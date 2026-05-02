package com.aivoice.input.model

/**
 * Beat context for context-aware polishing.
 * Passed from WriterPad to FloatingBallService.
 */
data class BeatContext(
    val beatId: String,
    val beatTitle: String,
    val beatSummary: String,
    val characters: List<CharacterSummary>,
    val worldRules: List<WorldRuleSummary>,
    val outlineSummary: String?
)

/**
 * Character summary for prompt context.
 */
data class CharacterSummary(
    val name: String,
    val content: String
)

/**
 * World rule summary for prompt context.
 */
data class WorldRuleSummary(
    val title: String,
    val content: String
)
