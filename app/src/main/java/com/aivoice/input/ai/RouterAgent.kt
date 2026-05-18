package com.aivoice.input.ai

import com.aivoice.input.model.Beat
import com.aivoice.input.model.Character
import com.aivoice.input.model.WorldRule
import com.aivoice.input.model.router.RouterDecision
import com.aivoice.input.model.router.SkillType
import com.aivoice.input.model.router.SkillAction
import com.aivoice.input.network.ai.MiniMaxClient
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.google.gson.annotations.SerializedName
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.catch

import android.util.Log

/**
 * 路由 Agent - 分析用户输入，决定调用哪些 Skill
 */
class RouterAgent(
    private val client: MiniMaxClient
) {
    companion object {
        private const val TAG = "RouterAgent"
        private val gson = Gson()
    }

    private var accumulatedJson = ""

    /**
     * 分析用户输入，返回路由决策
     *
     * @param input 用户输入内容
     * @param context 当前上下文（节拍列表、人设列表、世界观列表等）
     */
    fun analyze(
        input: String,
        context: RouterContext
    ): Flow<GuideEvent<RouterDecision>> {
        accumulatedJson = ""
        val prompt = buildRouterPrompt(input, context)
        Log.d(TAG, "Router prompt built, length: ${prompt.length}")

        return client.chatStream(prompt)
            .map { chunk -> accumulateAndParse(chunk, ::parseRouterDecision) }
            .catch { e ->
                Log.e(TAG, "Router error: ${e.message}")
                emit(GuideEvent.Error(e.message ?: "路由分析失败"))
            }
    }

    private fun buildRouterPrompt(input: String, context: RouterContext): String {
        val beatListInfo = if (context.beats.isNotEmpty()) {
            "当前节拍列表:\n" + context.beats.mapIndexed { i, b ->
                "${i + 1}. [${b.beatId}] ${b.title}: ${b.summary}"
            }.joinToString("\n")
        } else {
            "当前无节拍"
        }

        val charListInfo = if (context.characters.isNotEmpty()) {
            "当前人设:\n" + context.characters.map { c ->
                "- [${c.charId}] ${c.name}: ${c.content.take(100)}"
            }.joinToString("\n")
        } else {
            "当前无人设"
        }

        val worldRuleInfo = if (context.worldRules.isNotEmpty()) {
            "当前世界观:\n" + context.worldRules.map { r ->
                "- [${r.ruleId}] ${r.title}: ${r.content.take(100)}"
            }.joinToString("\n")
        } else {
            "当前无世界观"
        }

        val currentBeatInfo = context.currentBeat?.let {
            "当前选中节拍: [${it.beatId}] ${it.title}"
        } ?: "当前无选中节拍"

        return """
你是一个网文创作助手的路由决策模块。你的任务是分析用户输入，判断需要执行哪些操作。

当前上下文:
$beatListInfo

$charListInfo

$worldRuleInfo

$currentBeatInfo

用户输入: $input

请分析用户输入，判断需要执行哪些操作。输出JSON格式:

{
  "analysis": "对用户输入的分析（一句话）",
  "actions": [
    {
      "type": "操作类型",
      "reason": "判断理由",
      "targetBeatIds": ["目标节拍ID列表"],
      "priority": 优先级数字
    }
  ],
  "needsMoreInfo": false,
  "question": ""
}

操作类型说明:
- BEAT_GENERATE: 生成新节拍或修改节拍结构（用户提到新的剧情发展、新的故事阶段）
- CHARACTER_CREATE: 创建新人设（用户提到新角色，且该角色不在当前人设列表中）
- CHARACTER_UPDATE: 更新人设（用户补充已有角色的信息）
- WORLD_RULE_CREATE: 创建新世界观规则（用户提到新的设定规则）
- WORLD_RULE_UPDATE: 更新世界观规则（用户补充已有规则）
- OUTLINE_UPDATE: 更新大纲（用户描述具体情节、场景、对话）
- GLOSSARY_GENERATE: 生成词库（通常在设定变更后自动触发）

判断规则:
1. 如果当前无节拍，用户输入故事前提，优先 BEAT_GENERATE
2. 如果用户提到新角色名且不在当前人设列表，添加 CHARACTER_CREATE
3. 如果用户提到已有角色名并补充信息，添加 CHARACTER_UPDATE
4. 如果用户描述具体情节、场景，添加 OUTLINE_UPDATE
5. 人设/世界观默认关联所有节拍，targetBeatIds 为空数组 []
6. 如果信息不足无法判断，设置 needsMoreInfo=true 并提供 question

只输出JSON，不要其他文字。
""".trimIndent()
    }

    private fun <T> accumulateAndParse(
        chunk: String,
        parserFunc: (String) -> GuideEvent<T>
    ): GuideEvent<T> {
        accumulatedJson += chunk
        val trimmed = accumulatedJson.trim()

        if (trimmed.isEmpty()) return GuideEvent.Loading

        // 检查 JSON 是否完整
        val openBraces = trimmed.count { it == '{' }
        val closeBraces = trimmed.count { it == '}' }
        val openBrackets = trimmed.count { it == '[' }
        val closeBrackets = trimmed.count { it == ']' }

        if (openBraces == closeBraces && openBrackets == closeBrackets) {
            val event = parserFunc(trimmed)
            if (event is GuideEvent.Complete || event is GuideEvent.Repaired) {
                accumulatedJson = ""
            }
            return event
        }

        return GuideEvent.Loading
    }

    private fun parseRouterDecision(json: String): GuideEvent<RouterDecision> {
        return try {
            val repaired = JsonRepair.repair(json)
            val decision = parseDecisionFromJson(repaired)
            Log.d(TAG, "Router decision parsed: $decision")
            GuideEvent.Complete(decision)
        } catch (e: Exception) {
            Log.e(TAG, "Parse error: ${e.message}")
            GuideEvent.Error("解析路由决策失败: ${e.message}")
        }
    }

    private fun parseDecisionFromJson(json: String): RouterDecision {
        return try {
            val dto = gson.fromJson(json, RouterDecisionDto::class.java)
            dto.toRouterDecision()
        } catch (e: JsonSyntaxException) {
            Log.e(TAG, "Gson parse failed, attempting repair: ${e.message}")
            // Fallback: try with repaired JSON
            val repaired = JsonRepair.repair(json)
            val dto = gson.fromJson(repaired, RouterDecisionDto::class.java)
            dto.toRouterDecision()
        }
    }

    // Gson DTO classes for deserialization
    private data class RouterDecisionDto(
        val analysis: String? = null,
        val actions: List<ActionDto>? = null,
        val needsMoreInfo: Boolean = false,
        val question: String? = null
    ) {
        fun toRouterDecision(): RouterDecision {
            val actions = if (!actions.isNullOrEmpty()) {
                actions.map { it.toSkillAction() }.sortedBy { it.priority }
            } else {
                listOf(SkillAction(SkillType.OUTLINE_UPDATE, "默认操作"))
            }
            return RouterDecision(
                actions = actions,
                analysis = analysis ?: "分析用户输入",
                needsMoreInfo = needsMoreInfo,
                question = question ?: ""
            )
        }
    }

    private data class ActionDto(
        val type: String? = null,
        val reason: String? = null,
        val targetBeatIds: List<String>? = null,
        val priority: Int = 0
    ) {
        fun toSkillAction(): SkillAction {
            val skillType = try {
                SkillType.valueOf(type ?: "OUTLINE_UPDATE")
            } catch (e: Exception) {
                SkillType.OUTLINE_UPDATE
            }
            return SkillAction(
                type = skillType,
                reason = reason ?: "",
                targetBeatIds = targetBeatIds ?: emptyList(),
                priority = priority
            )
        }
    }
}

/**
 * 路由上下文 - 传递给 RouterAgent 的当前状态
 */
data class RouterContext(
    val beats: List<Beat> = emptyList(),
    val characters: List<Character> = emptyList(),
    val worldRules: List<WorldRule> = emptyList(),
    val currentBeat: Beat? = null
)
