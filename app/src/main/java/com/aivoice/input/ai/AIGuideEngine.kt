package com.aivoice.input.ai

import com.aivoice.input.model.Beat
import com.aivoice.input.model.Character
import com.aivoice.input.model.WorldRule
import com.aivoice.input.model.Outline
import com.aivoice.input.model.draft.BeatDraft
import com.aivoice.input.model.draft.ClassificationResult
import com.aivoice.input.model.draft.GlossaryDraft
import com.aivoice.input.model.router.RouterDecision
import com.aivoice.input.ai.RouterContext
import com.aivoice.input.ai.ExecutorContext
import com.aivoice.input.model.router.ExecutionResult
import com.aivoice.input.network.ai.MiniMaxClient
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

import android.util.Log

/**
 * AI guidance engine for WriterPad.
 * Provides unified entry point with intelligent routing.
 */
class AIGuideEngine(
    private val client: MiniMaxClient,
    private val promptBuilder: GuidePromptBuilder,
    private val parser: GuideResponseParser,
    private val routerAgent: RouterAgent,
    private val executorPromptBuilder: ExecutorPromptBuilder
) {
    companion object {
        private const val TAG = "AIGuideEngine"
    }

    // Accumulator for streaming JSON chunks
    private var accumulatedJson = ""

    /**
     * 统一入口：处理用户输入
     * 自动路由到对应的 Skill 执行
     */
    fun processInput(
        input: String,
        beats: List<Beat>,
        characters: List<Character>,
        worldRules: List<WorldRule>,
        outlines: List<Outline>,
        currentBeat: Beat?
    ): Flow<GuideEvent<ExecutionResult>> {
        Log.d(TAG, "processInput: input='${input.take(100)}'")

        // Step 1: 路由决策
        val routerContext = RouterContext(beats, characters, worldRules, currentBeat)

        // 简化流程：直接构建执行 prompt，不再分两步调用
        val executorContext = ExecutorContext(beats, characters, worldRules, outlines, currentBeat)
        val prompt = buildUnifiedPrompt(input, executorContext)

        accumulatedJson = ""

        return client.chatStream(prompt)
            .map { chunk -> accumulateAndParseExecution(chunk) }
            .catch { e ->
                Log.e(TAG, "processInput error: ${e.message}")
                emit(GuideEvent.Error(e.message ?: "处理失败"))
            }
    }

    /**
     * 构建统一的处理 Prompt（包含路由+执行）
     */
    private fun buildUnifiedPrompt(input: String, context: ExecutorContext): String {
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

        val outlineInfo = if (context.outlines.isNotEmpty()) {
            "当前大纲:\n" + context.outlines.map { o ->
                "- 节拍[${o.beatId}]: ${o.content.take(100)}"
            }.joinToString("\n")
        } else {
            "当前无大纲"
        }

        val currentBeatInfo = context.currentBeat?.let {
            "当前选中节拍: [${it.beatId}] ${it.title}"
        } ?: "当前无选中节拍"

        return """
你是一个网文创作助手。分析用户输入，执行相应操作。

当前上下文:
$beatListInfo

$charListInfo

$worldRuleInfo

$outlineInfo

$currentBeatInfo

用户输入: $input

请分析并执行操作，输出JSON格式:

{
  "beats": {
    "action": "CREATE|UPDATE|INSERT|DELETE|null",
    "beats": [{"title": "标题", "summary": "摘要", "type": "OPENING|DEVELOPMENT|TWIST|CLOSING|FORESHADOW|CLIMAX"}],
    "targetBeatId": "目标节拍ID",
    "position": 0
  },
  "characters": {
    "created": [{"name": "角色名", "content": "完整描述", "contextType": "STATE|RELATION|EVENT|CONDITION", "contextNote": "备注"}],
    "updated": [{"targetId": "charId", "content": "更新内容"}],
    "deleted": []
  },
  "worldRules": {
    "created": [{"title": "规则标题", "content": "规则内容", "contextType": "STATE|RELATION|EVENT|CONDITION", "contextNote": "备注"}],
    "updated": [],
    "deleted": []
  },
  "outline": {
    "beatId": "节拍ID",
    "content": "大纲内容",
    "action": "CREATE|UPDATE|APPEND",
    "appendPosition": "START|END"
  },
  "mappings": [
    {"beatId": "beat_1", "settingType": "CHARACTER", "settingId": "NEW_CHAR_角色名", "contextType": "STATE", "contextNote": "该角色在此节拍出场"},
    {"beatId": "beat_2", "settingType": "WORLD_RULE", "settingId": "NEW_RULE_规则标题", "contextType": "STATE", "contextNote": "该设定在此节拍被提及"}
  ],
  "glossary": [
    {"word": "专有名词", "type": "CHARACTER|WORLD|MANUAL", "sourceId": "关联的charId或ruleId", "priority": "HIGH|MEDIUM|LOW", "aliases": ["别名1", "别名2"]}
  ],
  "conflicts": [],
  "feedback": "给用户的简短反馈"
}

判断规则:
1. 【重要】如果当前无节拍，用户输入故事前提，必须同时执行：
   - 生成节拍列表 (beats.action = "CREATE")，每个节拍要有清晰的标题和摘要
   - 提取所有角色并创建人设 (characters.created)，包括主角、配角等
   - 提取世界观设定 (worldRules.created)，包括时代背景、特殊规则等
   - 为第一个节拍创建大纲 (outline)
   - 提取词库 (glossary)，包括所有人名、地名、专有名词
   - 【重要】mappings 必须精确关联：根据每个节拍的情节内容，只关联在该节拍中出场或被提及的人设/世界观
2. 如果用户提到新角色名且不在当前人设列表，创建人设 (characters.created)
3. 如果用户提到已有角色名并补充信息，更新人设 (characters.updated)
4. 如果用户描述具体情节、场景，更新大纲 (outline)
5. 【重要】mappings 关联规则：
   - 不要使用 beatId = "ALL"
   - 每个节拍只关联在该节拍情节中出场或被提及的人设/世界观
   - 使用节拍序号作为 beatId，如 "beat_1", "beat_2" 等
   - 例如：第一节拍只有主角出场，就只关联主角；第二节拍主角和反派都出场，就关联这两个
6. 用户未提及的内容不要生成，对应字段设为 null 或空
7. feedback 用一句话告诉用户做了什么

词库提取规则（必须执行）：
1. 提取所有人名（主角、配角、龙套），type 设为 CHARACTER
2. 提取所有地名（城市、区域、建筑），type 设为 WORLD
3. 提取专有名词（功法、道具、组织、职位、特殊术语），type 设为 MANUAL
4. 每个词库条目必须关联 sourceId（人设ID或世界观ID）
5. priority 规则：
   - HIGH：主角、核心设定
   - MEDIUM：重要配角、常用地名
   - LOW：次要角色、偶尔出现的名词
6. aliases 包含：外号、简称、尊称、蔑称等别名

示例输出：
{
  "glossary": [
    {"word": "林墨", "type": "CHARACTER", "sourceId": "NEW_CHAR_林墨", "priority": "HIGH", "aliases": ["林少", "墨儿"]},
    {"word": "青云宗", "type": "WORLD", "sourceId": "NEW_RULE_青云宗", "priority": "MEDIUM", "aliases": ["宗门"]}
  ]
}

只输出JSON，不要其他文字。
""".trimIndent()
    }

    private fun accumulateAndParseExecution(chunk: String): GuideEvent<ExecutionResult> {
        accumulatedJson += chunk
        val trimmed = accumulatedJson.trim()

        if (trimmed.isEmpty()) return GuideEvent.Loading

        val openBraces = trimmed.count { it == '{' }
        val closeBraces = trimmed.count { it == '}' }
        val openBrackets = trimmed.count { it == '[' }
        val closeBrackets = trimmed.count { it == ']' }

        if (openBraces == closeBraces && openBrackets == closeBrackets) {
            val event = parser.parseExecutionResult(trimmed)
            if (event is GuideEvent.Complete || event is GuideEvent.Repaired) {
                accumulatedJson = ""
            }
            return event
        }

        return GuideEvent.Loading
    }

    // ========== 保留原有方法供兼容 ==========

    /**
     * Skill 1: Generate beats from premise.
     * Returns streaming events, final output is List<BeatDraft>.
     */
    fun generateBeats(premise: String): Flow<GuideEvent<List<BeatDraft>>> {
        Log.d(TAG, "generateBeats called with premise: ${premise.take(100)}")
        accumulatedJson = "" // Reset accumulator
        val prompt = promptBuilder.buildBeatPrompt(premise)
        Log.d(TAG, "Built prompt, length: ${prompt.length}")

        return client.chatStream(prompt)
            .map { chunk ->
                Log.d(TAG, "Received chunk: ${chunk.take(50)}")
                accumulateAndParseBeats(chunk)
            }
            .catch { e ->
                Log.e(TAG, "Error in generateBeats: ${e.message}")
                emit(GuideEvent.Error(e.message ?: "Unknown error"))
            }
    }

    /**
     * Skill 2: Classify content and detect conflicts.
     * Returns streaming events, final output is ClassificationResult.
     */
    fun classifyAndIndex(
        content: String,
        currentBeat: Beat,
        existingSettings: ExistingSettings
    ): Flow<GuideEvent<ClassificationResult>> {
        accumulatedJson = "" // Reset accumulator
        val prompt = promptBuilder.buildClassifyPrompt(content, currentBeat, existingSettings)

        return client.chatStream(prompt)
            .map { chunk -> accumulateAndParseClassification(chunk) }
            .catch { e -> emit(GuideEvent.Error(e.message ?: "Unknown error")) }
    }

    /**
     * Skill 3: Generate glossary with aliases.
     * Returns streaming events, final output is List<GlossaryDraft>.
     */
    fun generateGlossary(
        characters: List<Character>,
        worldRules: List<WorldRule>
    ): Flow<GuideEvent<List<GlossaryDraft>>> {
        accumulatedJson = "" // Reset accumulator
        val prompt = promptBuilder.buildGlossaryPrompt(characters, worldRules)

        return client.chatStream(prompt)
            .map { chunk -> accumulateAndParseGlossary(chunk) }
            .catch { e -> emit(GuideEvent.Error(e.message ?: "Unknown error")) }
    }

    /**
     * Accumulate streaming chunks and attempt to parse beats.
     * Returns Loading for incomplete, Complete/Error for final.
     */
    private fun accumulateAndParseBeats(chunk: String): GuideEvent<List<BeatDraft>> {
        Log.d(TAG, "accumulateAndParseBeats: chunk='${chunk.take(100)}'")
        accumulatedJson += chunk
        Log.d(TAG, "accumulatedJson length: ${accumulatedJson.length}, content: '${accumulatedJson.take(200)}'")
        val result = tryParseAccumulated(parser::parseBeats)
        Log.d(TAG, "parse result: $result")
        return result
    }

    /**
     * Accumulate streaming chunks and attempt to parse classification.
     * Returns Loading for incomplete, Complete/Error for final.
     */
    private fun accumulateAndParseClassification(chunk: String): GuideEvent<ClassificationResult> {
        accumulatedJson += chunk
        return tryParseAccumulated(parser::parseClassification)
    }

    /**
     * Accumulate streaming chunks and attempt to parse glossary.
     * Returns Loading for incomplete, Complete/Error for final.
     */
    private fun accumulateAndParseGlossary(chunk: String): GuideEvent<List<GlossaryDraft>> {
        accumulatedJson += chunk
        return tryParseAccumulated(parser::parseGlossary)
    }

    /**
     * Try to parse accumulated JSON if it appears complete.
     */
    private fun <T> tryParseAccumulated(parserFunc: (String) -> GuideEvent<T>): GuideEvent<T> {
        val trimmed = accumulatedJson.trim()
        Log.d(TAG, "tryParseAccumulated: trimmed length=${trimmed.length}")
        if (trimmed.isEmpty()) {
            Log.d(TAG, "tryParseAccumulated: empty, returning Loading")
            return GuideEvent.Loading
        }

        // Check if JSON appears complete (has matching brackets)
        val isComplete = isJsonComplete(trimmed)
        Log.d(TAG, "tryParseAccumulated: isJsonComplete=$isComplete, openBraces=${trimmed.count { it == '{' }}, closeBraces=${trimmed.count { it == '}' }}")

        if (isComplete) {
            Log.d(TAG, "tryParseAccumulated: JSON appears complete, attempting parse")
            val event = parserFunc(trimmed)
            Log.d(TAG, "tryParseAccumulated: parse result=$event")
            if (event is GuideEvent.Complete || event is GuideEvent.Repaired) {
                accumulatedJson = "" // Reset for next call
            }
            return event
        }

        // JSON incomplete, return Loading
        Log.d(TAG, "tryParseAccumulated: JSON incomplete, returning Loading")
        return GuideEvent.Loading
    }

    /**
     * Check if JSON appears structurally complete.
     * Simple heuristic: matching brackets and braces.
     */
    private fun isJsonComplete(json: String): Boolean {
        val openBraces = json.count { it == '{' }
        val closeBraces = json.count { it == '}' }
        val openBrackets = json.count { it == '[' }
        val closeBrackets = json.count { it == ']' }

        return openBraces == closeBraces && openBrackets == closeBrackets
    }
}
