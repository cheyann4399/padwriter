package com.aivoice.input.ai

import com.aivoice.input.model.Beat
import com.aivoice.input.model.Character
import com.aivoice.input.model.WorldRule
import com.aivoice.input.model.Outline
import com.aivoice.input.model.draft.BeatDraft
import com.aivoice.input.model.draft.ClassificationResult
import com.aivoice.input.model.draft.GlossaryDraft
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
    private val parser: GuideResponseParser
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
你是一个网文创作助手。分析用户输入，执行相应操作，输出JSON。

当前上下文:
$beatListInfo

$charListInfo

$worldRuleInfo

$outlineInfo

$currentBeatInfo

用户输入: $input

## 输出 Schema

严格输出以下 JSON 结构，未涉及的字段设为 null 或空数组：

{
  "beats": {
    "action": "CREATE|UPDATE|INSERT|DELETE|null",
    "beats": [{"title": "2-6字标题", "summary": "20-50字摘要", "type": "OPENING|DEVELOPMENT|TWIST|CLOSING|FORESHADOW|CLIMAX"}],
    "targetBeatId": "",
    "position": -1
  },
  "characters": {
    "created": [{"name": "角色名", "content": "完整描述", "contextType": "STATE|RELATION|EVENT|CONDITION", "contextNote": "备注"}],
    "updated": [{"targetId": "已有charId", "content": "补充内容"}],
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
  "mappings": [{"beatId": "beat_1", "settingType": "CHARACTER|WORLD_RULE", "settingId": "设定ID", "contextType": "STATE", "contextNote": "出场/被提及"}],
  "glossary": [{"word": "专有名词", "type": "CHARACTER|WORLD|MANUAL", "sourceId": "关联ID", "priority": "HIGH|MEDIUM|LOW", "aliases": []}],
  "conflicts": [],
  "feedback": "一句话反馈"
}

## 判断规则

1. 当前无节拍 + 用户输入故事前提 → 必须同时：生成节拍、提取人设、提取世界观、为第一节拍创建大纲、提取词库、生成 mappings
2. 新角色名不在当前人设列表 → characters.created
3. 已有角色名 + 补充信息 → characters.updated
4. 具体情节/场景 → outline 更新
5. 用户未提及的内容 → 对应字段设 null 或空数组

## mappings 规则（严格遵守）

- 禁止使用 beatId = "ALL"，必须枚举具体 beatId
- 每个节拍只关联在该节拍情节中出场或被提及的人设/世界观
- 新创建的人设 settingId 格式: "NEW_CHAR_角色名"
- 新创建的世界观 settingId 格式: "NEW_RULE_规则标题"

## 词库提取规则

- 人名 → type: CHARACTER，priority: HIGH(主角)/MEDIUM(重要配角)/LOW(龙套)
- 地名/组织 → type: WORLD
- 功法/道具/术语 → type: MANUAL
- 每条必须关联 sourceId
- aliases 包含外号、简称、尊称等

## 完整示例

输入: 少年林墨在废墟中发现一块刻满符文的黑色石碑，触碰后获得远古传承，踏上修仙之路
上下文: 当前无节拍

输出:
```json
{
  "beats": {
    "action": "CREATE",
    "beats": [
      {"title": "废墟觉醒", "summary": "少年林墨在废墟中发现黑色石碑，触碰后获得远古传承", "type": "OPENING"},
      {"title": "初入宗门", "summary": "林墨拜入修仙宗门，开始系统修炼", "type": "DEVELOPMENT"},
      {"title": "宗门大比", "summary": "林墨在宗门比武中崭露头角，引起关注", "type": "CLIMAX"},
      {"title": "下山历练", "summary": "林墨奉命下山执行任务，遭遇危机", "type": "TWIST"}
    ],
    "targetBeatId": "",
    "position": -1
  },
  "characters": {
    "created": [
      {"name": "林墨", "content": "主角，少年，性格坚韧，在废墟中获得远古传承踏上修仙路", "contextType": "STATE", "contextNote": "主角初始状态"}
    ],
    "updated": [],
    "deleted": []
  },
  "worldRules": {
    "created": [
      {"title": "远古传承", "content": "黑色石碑中的远古传承，赋予修炼者特殊能力", "contextType": "STATE", "contextNote": "核心设定"}
    ],
    "updated": [],
    "deleted": []
  },
  "outline": {
    "beatId": "beat_1",
    "content": "少年林墨在废墟中探索，发现一块刻满符文的黑色石碑。触碰石碑后，远古传承涌入体内，林墨获得修炼功法，身体发生异变，踏上修仙之路。",
    "action": "CREATE",
    "appendPosition": "END"
  },
  "mappings": [
    {"beatId": "beat_1", "settingType": "CHARACTER", "settingId": "NEW_CHAR_林墨", "contextType": "STATE", "contextNote": "主角出场"},
    {"beatId": "beat_1", "settingType": "WORLD_RULE", "settingId": "NEW_RULE_远古传承", "contextType": "STATE", "contextNote": "传承被激活"},
    {"beatId": "beat_2", "settingType": "CHARACTER", "settingId": "NEW_CHAR_林墨", "contextType": "STATE", "contextNote": "主角拜入宗门"},
    {"beatId": "beat_3", "settingType": "CHARACTER", "settingId": "NEW_CHAR_林墨", "contextType": "EVENT", "contextNote": "主角参加大比"},
    {"beatId": "beat_4", "settingType": "CHARACTER", "settingId": "NEW_CHAR_林墨", "contextType": "EVENT", "contextNote": "主角下山历练"}
  ],
  "glossary": [
    {"word": "林墨", "type": "CHARACTER", "sourceId": "NEW_CHAR_林墨", "priority": "HIGH", "aliases": ["林少", "墨儿"]},
    {"word": "远古传承", "type": "MANUAL", "sourceId": "NEW_RULE_远古传承", "priority": "HIGH", "aliases": ["传承"]},
    {"word": "黑色石碑", "type": "MANUAL", "sourceId": "NEW_RULE_远古传承", "priority": "MEDIUM", "aliases": ["石碑"]}
  ],
  "conflicts": [],
  "feedback": "已生成4个节拍、1个人设、1个世界观设定、词库3条"
}
```

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

    private data class ExecutorContext(
        val beats: List<Beat> = emptyList(),
        val characters: List<Character> = emptyList(),
        val worldRules: List<WorldRule> = emptyList(),
        val outlines: List<Outline> = emptyList(),
        val currentBeat: Beat? = null
    )
}
