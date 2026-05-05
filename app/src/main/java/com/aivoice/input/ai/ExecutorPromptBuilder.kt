package com.aivoice.input.ai

import com.aivoice.input.model.Beat
import com.aivoice.input.model.Character
import com.aivoice.input.model.WorldRule
import com.aivoice.input.model.Outline
import com.aivoice.input.model.router.RouterDecision
import com.aivoice.input.model.router.SkillType
import com.aivoice.input.model.router.SkillAction

/**
 * 执行 Agent 的 Prompt 构建器
 */
class ExecutorPromptBuilder {

    /**
     * 构建统一的执行 Prompt
     */
    fun buildExecutionPrompt(
        input: String,
        decision: RouterDecision,
        context: ExecutorContext
    ): String {
        val contextInfo = buildContextInfo(context)
        val actionInfo = buildActionInfo(decision.actions)

        return """
你是一个网文设定管理助手。根据路由决策执行具体操作。

当前上下文:
$contextInfo

路由决策:
$actionInfo

用户输入: $input

请执行上述操作，输出JSON格式:

{
  "beats": {
    "action": "CREATE|UPDATE|INSERT|DELETE",
    "beats": [{"title": "标题", "summary": "摘要", "type": "OPENING|DEVELOPMENT|TWIST|CLOSING|FORESHADOW|CLIMAX"}],
    "targetBeatId": "目标节拍ID（UPDATE/DELETE时）",
    "position": 位置（INSERT时）
  },
  "characters": {
    "created": [{"name": "角色名", "content": "完整描述", "contextType": "STATE|RELATION|EVENT|CONDITION", "contextNote": "备注"}],
    "updated": [{"targetId": "charId", "content": "更新内容"}],
    "deleted": ["charId列表"]
  },
  "worldRules": {
    "created": [{"title": "规则标题", "content": "规则内容", "contextType": "STATE|RELATION|EVENT|CONDITION", "contextNote": "备注"}],
    "updated": [{"targetId": "ruleId", "content": "更新内容"}],
    "deleted": ["ruleId列表"]
  },
  "outline": {
    "beatId": "节拍ID",
    "content": "大纲内容",
    "action": "CREATE|UPDATE|APPEND",
    "appendPosition": "START|END"
  },
  "mappings": [
    {"beatId": "节拍ID", "settingType": "CHARACTER|OUTLINE|WORLD_RULE", "settingId": "设定ID", "contextType": "STATE|RELATION|EVENT|CONDITION", "contextNote": "备注"}
  ],
  "conflicts": [
    {"type": "CHARACTER|WORLD_RULE|PLOT", "settingId": "ID", "description": "冲突描述", "severity": "WARNING|ERROR", "suggestion": "解决建议"}
  ],
  "feedback": "给用户的简短反馈"
}

执行规则:
1. 人设/世界观创建时，settingId 使用 "NEW_CHAR_角色名" 或 "NEW_RULE_规则标题" 格式
2. 人设/世界观默认关联所有节拍，mappings 中 beatId 使用 "ALL" 表示全局关联
3. 大纲按节拍分段存储，每个节拍对应一段大纲
4. 用户未提及的内容不要生成，保持留白
5. 检测逻辑冲突，如角色状态矛盾、世界观规则违反等
6. feedback 用一句话告诉用户做了什么

只输出JSON，不要其他文字。
""".trimIndent()
    }

    private fun buildContextInfo(context: ExecutorContext): String {
        val sb = StringBuilder()

        // 节拍列表
        if (context.beats.isNotEmpty()) {
            sb.append("节拍列表:\n")
            context.beats.forEachIndexed { i, b ->
                sb.append("${i + 1}. [${b.beatId}] ${b.title}: ${b.summary}\n")
            }
        } else {
            sb.append("节拍列表: 无\n")
        }

        // 人设列表
        if (context.characters.isNotEmpty()) {
            sb.append("人设列表:\n")
            context.characters.forEach { c ->
                sb.append("- [${c.charId}] ${c.name}: ${c.content.take(150)}...\n")
            }
        } else {
            sb.append("人设列表: 无\n")
        }

        // 世界观列表
        if (context.worldRules.isNotEmpty()) {
            sb.append("世界观列表:\n")
            context.worldRules.forEach { r ->
                sb.append("- [${r.ruleId}] ${r.title}: ${r.content.take(150)}...\n")
            }
        } else {
            sb.append("世界观列表: 无\n")
        }

        // 当前大纲
        if (context.outlines.isNotEmpty()) {
            sb.append("大纲:\n")
            context.outlines.forEach { o ->
                sb.append("- 节拍[${o.beatId}]: ${o.content.take(100)}...\n")
            }
        } else {
            sb.append("大纲: 无\n")
        }

        // 当前节拍
        context.currentBeat?.let { b ->
            sb.append("当前节拍: [${b.beatId}] ${b.title}\n")
        }

        return sb.toString()
    }

    private fun buildActionInfo(actions: List<SkillAction>): String {
        return actions.map { action ->
            val targetInfo = if (action.targetBeatIds.isEmpty()) {
                "全局（所有节拍）"
            } else {
                action.targetBeatIds.joinToString(", ")
            }
            "- ${action.type}: ${action.reason} [目标: $targetInfo]"
        }.joinToString("\n")
    }
}

/**
 * 执行上下文
 */
data class ExecutorContext(
    val beats: List<Beat> = emptyList(),
    val characters: List<Character> = emptyList(),
    val worldRules: List<WorldRule> = emptyList(),
    val outlines: List<Outline> = emptyList(),
    val currentBeat: Beat? = null
)