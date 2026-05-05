package com.aivoice.input.model.router

/**
 * AI 判断需要执行的操作类型
 */
enum class SkillType {
    BEAT_GENERATE,      // 生成/修改节拍
    CHARACTER_CREATE,   // 创建人设
    CHARACTER_UPDATE,   // 更新人设
    WORLD_RULE_CREATE,  // 创建世界观
    WORLD_RULE_UPDATE,  // 更新世界观
    OUTLINE_UPDATE,     // 更新大纲
    GLOSSARY_GENERATE   // 生成词库
}

/**
 * 单个 Skill 操作
 */
data class SkillAction(
    val type: SkillType,
    val reason: String,           // AI 判断理由
    val targetBeatIds: List<String> = emptyList(),  // 目标节拍ID（空=所有节拍）
    val priority: Int = 0         // 执行优先级（数字越小越先执行）
)

/**
 * 路由决策结果
 */
data class RouterDecision(
    val actions: List<SkillAction>,
    val analysis: String,         // AI 对用户输入的分析
    val needsMoreInfo: Boolean = false,  // 是否需要更多信息
    val question: String = ""     // 如果需要更多信息，向用户提问
)
