package com.aivoice.input.model.router

import com.aivoice.input.model.draft.BeatDraft
import com.aivoice.input.model.draft.CharacterDraft
import com.aivoice.input.model.draft.WorldRuleDraft
import com.aivoice.input.model.draft.MappingDraft
import com.aivoice.input.model.draft.OutlineDraft
import com.aivoice.input.model.draft.GlossaryDraft

/**
 * 执行结果 - 统一的 AI 输出格式
 */
data class ExecutionResult(
    // 节拍相关
    val beats: BeatChanges? = null,

    // 人设相关
    val characters: CharacterChanges? = null,

    // 世界观相关
    val worldRules: WorldRuleChanges? = null,

    // 大纲相关
    val outline: OutlineChanges? = null,

    // 映射关系
    val mappings: List<MappingDraft> = emptyList(),

    // 词库相关
    val glossary: List<GlossaryDraft> = emptyList(),

    // 冲突检测结果
    val conflicts: List<ConflictInfo> = emptyList(),

    // AI 给用户的反馈
    val feedback: String = ""
)

/**
 * 节拍变更
 */
data class BeatChanges(
    val action: String,  // "CREATE" | "UPDATE" | "INSERT" | "DELETE"
    val beats: List<BeatDraft> = emptyList(),
    val targetBeatId: String = "",  // UPDATE/INSERT/DELETE 时的目标
    val position: Int = -1          // INSERT 时的位置
)

/**
 * 人设变更
 */
data class CharacterChanges(
    val created: List<CharacterDraft> = emptyList(),
    val updated: List<CharacterDraft> = emptyList(),
    val deleted: List<String> = emptyList()  // charId 列表
)

/**
 * 世界观变更
 */
data class WorldRuleChanges(
    val created: List<WorldRuleDraft> = emptyList(),
    val updated: List<WorldRuleDraft> = emptyList(),
    val deleted: List<String> = emptyList()  // ruleId 列表
)

/**
 * 大纲变更
 */
data class OutlineChanges(
    val beatId: String,           // 关联的节拍ID
    val content: String,          // 大纲内容
    val action: String,           // "CREATE" | "UPDATE" | "APPEND"
    val appendPosition: String = "END"  // APPEND 时: "START" | "END"
)

/**
 * 冲突信息
 */
data class ConflictInfo(
    val type: String,             // "CHARACTER" | "WORLD_RULE" | "PLOT"
    val settingId: String,
    val description: String,
    val severity: String,         // "WARNING" | "ERROR"
    val suggestion: String = ""   // 解决建议
)
