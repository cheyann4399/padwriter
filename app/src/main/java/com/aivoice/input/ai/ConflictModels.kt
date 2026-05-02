package com.aivoice.input.ai

/**
 * Conflict detection result for Skill 2.
 */
data class ConflictCheck(
    val hasConflict: Boolean,
    val conflicts: List<ConflictItem> = emptyList()
)

/**
 * Individual conflict item.
 */
data class ConflictItem(
    val settingId: String,
    val beatRange: Pair<Int, Int>,
    val description: String,
    val severity: ConflictSeverity
)

/**
 * Conflict severity level.
 */
enum class ConflictSeverity {
    WARNING,  // Minor inconsistency, possibly intentional
    ERROR     // Clear contradiction, needs resolution
}