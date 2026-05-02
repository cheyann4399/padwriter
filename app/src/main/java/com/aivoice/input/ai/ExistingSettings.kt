package com.aivoice.input.ai

import com.aivoice.input.model.Character
import com.aivoice.input.model.WorldRule

/**
 * Helper data class for Skill 2 input.
 * Contains existing project settings for context.
 */
data class ExistingSettings(
    val characters: List<Character>,
    val worldRules: List<WorldRule>,
    val currentBeatId: String
) {
    /**
     * Truncate content for token optimization.
     * Characters: 200 chars, WorldRules: 150 chars.
     */
    fun toTruncatedJson(): String {
        val truncatedChars = characters.map { char ->
            char.copy(content = char.content.take(200))
        }
        val truncatedRules = worldRules.map { rule ->
            rule.copy(content = rule.content.take(150))
        }
        return """{"characters":${truncatedChars.size},"worldRules":${truncatedRules.size},"currentBeatId":"$currentBeatId"}"""
    }
}
