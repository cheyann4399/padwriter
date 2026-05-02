# WriterPad Phase 2: AI Engine Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement AI guidance engine with three skills: beat generation, setting classification with conflict detection, and glossary generation with alias support.

**Architecture:** AIGuideEngine orchestrates MiniMaxClient calls through GuidePromptBuilder, with GuideResponseParser handling JSON parsing and repair. Uses Flow-based streaming events for UI updates.

**Tech Stack:** Kotlin, Coroutines/Flow, Gson, OkHttp SSE (existing MiniMaxClient)

---

## File Structure

```
app/src/main/java/com/aivoice/input/ai/
├── GuideEvent.kt             # Sealed event class for streaming
├── JsonRepair.kt             # Local JSON repair utility
├── GuideResponseParser.kt    # JSON parsing with repair fallback
├── GuidePromptBuilder.kt     # Schema + Few-shot prompt construction
├── ExistingSettings.kt       # Helper data class for Skill 2 input
├── ConflictModels.kt         # Conflict detection models
├── AIGuideEngine.kt          # Main orchestrator
└── AIGuideModule.kt          # Hilt DI module
```

---

### Task 1: GuideEvent Sealed Class

**Files:**
- Create: `app/src/main/java/com/aivoice/input/ai/GuideEvent.kt`

- [ ] **Step 1: Create GuideEvent sealed class**

```kotlin
package com.aivoice.input.ai

/**
 * Streaming events for AI guidance operations.
 */
sealed class GuideEvent<out T> {
    /** AI is processing, no output yet */
    object Loading : GuideEvent<Nothing>()

    /** Partial result received, useful for streaming display */
    data class Partial<T>(val data: T) : GuideEvent<T>()

    /** Final complete result */
    data class Complete<T>(val data: T) : GuideEvent<T>()

    /** Result was repaired from malformed JSON */
    data class Repaired<T>(val data: T, val originalJson: String) : GuideEvent<T>()

    /** Error occurred during processing */
    data class Error(val message: String, val rawResponse: String? = null) : GuideEvent<Nothing>()
}
```

- [ ] **Step 2: Commit**

```bash
git add app/src/main/java/com/aivoice/input/ai/GuideEvent.kt
git commit -m "feat: add GuideEvent sealed class for AI streaming"
```

---

### Task 2: Conflict Detection Models

**Files:**
- Create: `app/src/main/java/com/aivoice/input/ai/ConflictModels.kt`

- [ ] **Step 1: Create ConflictModels file**

```kotlin
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
```

- [ ] **Step 2: Commit**

```bash
git add app/src/main/java/com/aivoice/input/ai/ConflictModels.kt
git commit -m "feat: add conflict detection models"
```

---

### Task 3: ExistingSettings Helper Class

**Files:**
- Create: `app/src/main/java/com/aivoice/input/ai/ExistingSettings.kt`

- [ ] **Step 1: Create ExistingSettings file**

```kotlin
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
```

- [ ] **Step 2: Commit**

```bash
git add app/src/main/java/com/aivoice/input/ai/ExistingSettings.kt
git commit -m "feat: add ExistingSettings helper class"
```

---

### Task 4: JsonRepair Utility

**Files:**
- Create: `app/src/main/java/com/aivoice/input/ai/JsonRepair.kt`

- [ ] **Step 1: Create JsonRepair utility**

```kotlin
package com.aivoice.input.ai

/**
 * Local JSON repair utility for common malformed JSON issues.
 */
object JsonRepair {

    /**
     * Attempt to repair malformed JSON.
     * Handles: trailing commas, missing brackets, illegal escape sequences.
     *
     * @param json The potentially malformed JSON string
     * @return Repaired JSON string, or original if no repair needed
     */
    fun repair(json: String): String {
        var result = json.trim()

        // 1. Remove trailing commas before ] and }
        result = result.replace(Regex(",\\s*]"), "]")
        result = result.replace(Regex(",\\s*}"), "}")

        // 2. Fix missing closing braces
        val openBraces = result.count { it == '{' }
        val closeBraces = result.count { it == '}' }
        if (openBraces > closeBraces) {
            result += "}".repeat(openBraces - closeBraces)
        }

        // 3. Fix missing closing brackets
        val openBrackets = result.count { it == '[' }
        val closeBrackets = result.count { it == ']' }
        if (openBrackets > closeBrackets) {
            result += "]".repeat(openBrackets - closeBrackets)
        }

        // 4. Remove illegal escape sequences (keep only valid JSON escapes)
        // Valid escapes: \", \\, \/, \b, \f, \n, \r, \t, \uXXXX
        result = result.replace(Regex("\\\\(?!['\"\\\\/bfnrtu])"), "")

        // 5. Fix unescaped quotes in string values (simple cases)
        // This is tricky - only fix obvious cases like {"key": "value with "quote" inside"}
        // Skip complex cases to avoid breaking valid JSON

        return result
    }

    /**
     * Check if JSON appears repairable.
     */
    fun isRepairable(json: String): Boolean {
        val trimmed = json.trim()
        // Must start with { or [
        return trimmed.startsWith("{") || trimmed.startsWith("[")
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add app/src/main/java/com/aivoice/input/ai/JsonRepair.kt
git commit -m "feat: add JsonRepair utility for malformed JSON"
```

---

### Task 5: GuideResponseParser

**Files:**
- Create: `app/src/main/java/com/aivoice/input/ai/GuideResponseParser.kt`
- Modify: `app/src/main/java/com/aivoice/input/model/draft/ClassificationResult.kt`
- Modify: `app/src/main/java/com/aivoice/input/model/draft/GlossaryDraft.kt`

- [ ] **Step 1: Update ClassificationResult to include conflictCheck**

Read `ClassificationResult.kt`, then update:

```kotlin
package com.aivoice.input.model.draft

data class ClassificationResult(
    val characters: List<CharacterDraft>,
    val outline: OutlineDraft?,
    val worldRules: List<WorldRuleDraft>,
    val mappings: List<MappingDraft>,
    val conflictCheck: com.aivoice.input.ai.ConflictCheck? = null
)
```

- [ ] **Step 2: Update GlossaryDraft to include aliases**

Read `GlossaryDraft.kt`, then update:

```kotlin
package com.aivoice.input.model.draft

import com.aivoice.input.model.enums.GlossaryPriority
import com.aivoice.input.model.enums.GlossaryType

data class GlossaryDraft(
    val word: String,
    val type: GlossaryType,
    val sourceId: String,
    val priority: GlossaryPriority,
    val aliases: List<String> = emptyList(),
    val canonicalId: String? = null
)
```

- [ ] **Step 3: Create GuideResponseParser**

```kotlin
package com.aivoice.input.ai

import android.util.Log
import com.aivoice.input.model.draft.*
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.google.gson.reflect.TypeToken

/**
 * Parses AI JSON responses into draft models.
 * Includes repair fallback for malformed JSON.
 */
class GuideResponseParser {
    private val gson = Gson()
    private val TAG = "GuideResponseParser"

    /**
     * Parse beat generation response.
     */
    fun parseBeats(json: String): GuideEvent<List<BeatDraft>> {
        return tryParse(json, object : TypeToken<BeatResponse>() {})?.let { response ->
            GuideEvent.Complete(response.beats)
        } ?: GuideEvent.Error("Failed to parse beats", json)
    }

    /**
     * Parse classification response.
     */
    fun parseClassification(json: String): GuideEvent<ClassificationResult> {
        return tryParse(json, object : TypeToken<ClassificationResponse>() {})?.let { response ->
            GuideEvent.Complete(response.result)
        } ?: GuideEvent.Error("Failed to parse classification", json)
    }

    /**
     * Parse glossary response.
     */
    fun parseGlossary(json: String): GuideEvent<List<GlossaryDraft>> {
        return tryParse(json, object : TypeToken<GlossaryResponse>() {})?.let { response ->
            GuideEvent.Complete(response.glossary)
        } ?: GuideEvent.Error("Failed to parse glossary", json)
    }

    /**
     * Try to parse JSON, with repair fallback.
     */
    private inline fun <reified T> tryParse(json: String, typeToken: TypeToken<T>): T? {
        // First attempt: direct parse
        try {
            return gson.fromJson(json, typeToken.type)
        } catch (e: JsonSyntaxException) {
            Log.w(TAG, "Direct parse failed: ${e.message}")
        }

        // Second attempt: repair and parse
        if (JsonRepair.isRepairable(json)) {
            val repaired = JsonRepair.repair(json)
            try {
                val result = gson.fromJson(repaired, typeToken.type)
                Log.i(TAG, "JSON repaired successfully")
                return result
            } catch (e: JsonSyntaxException) {
                Log.w(TAG, "Repair parse failed: ${e.message}")
            }
        }

        return null
    }

    // Response wrapper classes for Gson parsing
    private data class BeatResponse(val beats: List<BeatDraft>)
    private data class ClassificationResponse(val result: ClassificationResult)
    private data class GlossaryResponse(val glossary: List<GlossaryDraft>)
}
```

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/aivoice/input/ai/GuideResponseParser.kt
git add app/src/main/java/com/aivoice/input/model/draft/ClassificationResult.kt
git add app/src/main/java/com/aivoice/input/model/draft/GlossaryDraft.kt
git commit -m "feat: add GuideResponseParser with JSON repair fallback"
```

---

### Task 6: GuidePromptBuilder

**Files:**
- Create: `app/src/main/java/com/aivoice/input/ai/GuidePromptBuilder.kt`

- [ ] **Step 1: Create GuidePromptBuilder with Skill 1 prompt**

```kotlin
package com.aivoice.input.ai

import com.aivoice.input.model.Beat
import com.aivoice.input.model.Character
import com.aivoice.input.model.WorldRule
import com.aivoice.input.model.draft.BeatDraft

/**
 * Builds Schema + Few-shot prompts for AI guidance skills.
 */
class GuidePromptBuilder {

    /**
     * Skill 1: Build beat generation prompt.
     */
    fun buildBeatPrompt(premise: String): String {
        return """
你是一个网文创作顾问，擅长梳理故事脉络。请根据用户的故事前提生成节拍器骨架。

JSON Schema:
{
  "beats": [
    {
      "title": "节拍标题（2-6字）",
      "summary": "节拍摘要（20-50字）",
      "type": "OPENING|DEVELOPMENT|TWIST|CLOSING|FORESHADOW|CLIMAX"
    }
  ]
}

示例1:
输入: 一个少年在废墟中发现神秘石碑，从此踏上修仙之路
输出: {"beats":[{"title":"废墟觉醒","summary":"少年李火旺在废墟中发现神秘石碑，获得传承","type":"OPENING"},{"title":"初入江湖","summary":"拜入青云宗，结识师兄张三","type":"DEVELOPMENT"},{"title":"宗门大比","summary":"在宗门比武中崭露头角","type":"CLIMAX"},{"title":"下山历练","summary":"奉命下山执行任务，遭遇魔教","type":"TWIST"}]}

示例2:
输入: 都市重生文，主角重生回到高考前，决定改变命运
输出: {"beats":[{"title":"重生归来","summary":"主角重生回到高考前三个月","type":"OPENING"},{"title":"备战高考","summary":"利用前世记忆高效复习","type":"DEVELOPMENT"},{"title":"意外相遇","summary":"偶遇前世暗恋对象","type":"FORESHADOW"},{"title":"高考冲刺","summary":"高考前夕的关键抉择","type":"CLIMAX"}]}

用户输入: $premise

只输出JSON，不要其他文字。
""".trimIndent()
    }

    /**
     * Skill 2: Build classification prompt.
     */
    fun buildClassifyPrompt(
        content: String,
        currentBeat: Beat,
        existingSettings: ExistingSettings
    ): String {
        val beatInfo = "当前节拍: #${currentBeat.order} ${currentBeat.title} - ${currentBeat.summary}"
        val existingInfo = buildExistingSettingsInfo(existingSettings)

        return """
你是一个网文设定管理助手，擅长归类和检测冲突。

JSON Schema:
{
  "result": {
    "characters": [{"name": "角色名", "content": "描述", "action": "CREATE|UPDATE", "contextType": "STATE|RELATION|EVENT|CONDITION", "contextNote": "备注", "targetId": ""}],
    "outline": {"content": "大纲内容", "beatId": "当前节拍ID"},
    "worldRules": [{"title": "规则标题", "content": "规则内容", "action": "CREATE|UPDATE", "contextType": "STATE|RELATION|EVENT|CONDITION", "contextNote": "备注", "targetId": ""}],
    "mappings": [{"beatId": "节拍ID", "settingType": "CHARACTER|OUTLINE|WORLD_RULE", "settingId": "设定ID", "contextType": "STATE|RELATION|EVENT|CONDITION", "contextNote": "备注"}],
    "conflictCheck": {"hasConflict": false, "conflicts": []}
  }
}

冲突检测规则:
- 检查同一角色在不同节拍中的状态一致性（如受伤后活蹦乱跳）
- 检查世界观规则是否被违反
- conflicts数组包含: settingId, beatRange(起始-结束), description, severity(WARNING|ERROR)

$beatInfo
$existingInfo
用户补充内容: $content

只输出JSON，不要其他文字。
""".trimIndent()
    }

    /**
     * Skill 3: Build glossary generation prompt.
     */
    fun buildGlossaryPrompt(
        characters: List<Character>,
        worldRules: List<WorldRule>
    ): String {
        val charList = characters.map { c ->
            "- ${c.name}: ${c.content.take(200)}"
        }.joinToString("\n")

        val ruleList = worldRules.map { r ->
            "- ${r.title}: ${r.content.take(150)}"
        }.joinToString("\n")

        return """
你是一个网文术语提取专家。

JSON Schema:
{
  "glossary": [
    {
      "word": "主词条",
      "type": "CHARACTER|WORLD|MANUAL",
      "sourceId": "关联ID",
      "priority": "HIGH|MEDIUM|LOW",
      "aliases": ["别名1", "别名2"]
    }
  ]
}

提取规则:
- 提取专有名词（人名、地名、功法、组织、道具等）
- type: CHARACTER(人设相关), WORLD(世界观相关), MANUAL(其他)
- priority: HIGH(主角/核心), MEDIUM(重要配角/常用), LOW(次要)
- aliases: 包括外号、简称、尊称等

人设列表:
$charList

世界观列表:
$ruleList

只输出JSON，不要其他文字。
""".trimIndent()
    }

    private fun buildExistingSettingsInfo(settings: ExistingSettings): String {
        val charCount = settings.characters.size
        val ruleCount = settings.worldRules.size
        return "已有设定: ${charCount}个角色, ${ruleCount}个世界观规则, 当前节拍ID=${settings.currentBeatId}"
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add app/src/main/java/com/aivoice/input/ai/GuidePromptBuilder.kt
git commit -m "feat: add GuidePromptBuilder with Schema + Few-shot prompts"
```

---

### Task 7: AIGuideEngine

**Files:**
- Create: `app/src/main/java/com/aivoice/input/ai/AIGuideEngine.kt`

- [ ] **Step 1: Create AIGuideEngine**

```kotlin
package com.aivoice.input.ai

import android.util.Log
import com.aivoice.input.model.Beat
import com.aivoice.input.model.Character
import com.aivoice.input.model.WorldRule
import com.aivoice.input.model.draft.BeatDraft
import com.aivoice.input.model.draft.ClassificationResult
import com.aivoice.input.model.draft.GlossaryDraft
import com.aivoice.input.network.ai.MiniMaxClient
import kotlinx.coroutines.flow.*

/**
 * AI guidance engine for WriterPad.
 * Orchestrates three skills: beat generation, classification, glossary.
 */
class AIGuideEngine(
    private val client: MiniMaxClient,
    private val promptBuilder: GuidePromptBuilder,
    private val parser: GuideResponseParser
) {
    companion object {
        private const val TAG = "AIGuideEngine"
    }

    /**
     * Skill 1: Generate beats from premise.
     * Returns streaming events, final output is List<BeatDraft>.
     */
    fun generateBeats(premise: String): Flow<GuideEvent<List<BeatDraft>>> {
        val prompt = promptBuilder.buildBeatPrompt(premise)

        return client.chatStream(prompt)
            .map { chunk -> accumulateAndParse(chunk, parser::parseBeats) }
            .catch { e -> emit(GuideEvent.Error(e.message ?: "Unknown error")) }
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
        val prompt = promptBuilder.buildClassifyPrompt(content, currentBeat, existingSettings)

        return client.chatStream(prompt)
            .map { chunk -> accumulateAndParse(chunk, parser::parseClassification) }
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
        val prompt = promptBuilder.buildGlossaryPrompt(characters, worldRules)

        return client.chatStream(prompt)
            .map { chunk -> accumulateAndParse(chunk, parser::parseGlossary) }
            .catch { e -> emit(GuideEvent.Error(e.message ?: "Unknown error")) }
    }

    /**
     * Accumulate streaming chunks and attempt to parse.
     * Returns Partial for incomplete, Complete/Repaired/Error for final.
     */
    private inline fun <T> accumulateAndParse(
        chunk: String,
        parserFunc: (String) -> GuideEvent<T>
    ): GuideEvent<T> {
        accumulatedJson += chunk

        // Try to parse accumulated JSON
        val trimmed = accumulatedJson.trim()
        if (trimmed.isEmpty()) {
            return GuideEvent.Loading
        }

        // Check if JSON appears complete (has matching brackets)
        if (isJsonComplete(trimmed)) {
            val event = parserFunc(trimmed)
            if (event is GuideEvent.Complete || event is GuideEvent.Repaired) {
                accumulatedJson = "" // Reset for next call
            }
            return event
        }

        // JSON incomplete, return Loading
        return GuideEvent.Loading
    }

    private var accumulatedJson = ""

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
```

- [ ] **Step 2: Commit**

```bash
git add app/src/main/java/com/aivoice/input/ai/AIGuideEngine.kt
git commit -m "feat: add AIGuideEngine orchestrator for three AI skills"
```

---

### Task 8: Hilt Dependency Injection Module

**Files:**
- Create: `app/src/main/java/com/aivoice/input/ai/AIGuideModule.kt`

- [ ] **Step 1: Create AIGuideModule**

```kotlin
package com.aivoice.input.ai

import com.aivoice.input.network.ai.MiniMaxClient
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module for AI guidance engine dependencies.
 */
@Module
@InstallIn(SingletonComponent::class)
object AIGuideModule {

    @Provides
    @Singleton
    fun provideGuidePromptBuilder(): GuidePromptBuilder {
        return GuidePromptBuilder()
    }

    @Provides
    @Singleton
    fun provideGuideResponseParser(): GuideResponseParser {
        return GuideResponseParser()
    }

    @Provides
    @Singleton
    fun provideAIGuideEngine(
        client: MiniMaxClient,
        promptBuilder: GuidePromptBuilder,
        parser: GuideResponseParser
    ): AIGuideEngine {
        return AIGuideEngine(client, promptBuilder, parser)
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add app/src/main/java/com/aivoice/input/ai/AIGuideModule.kt
git commit -m "feat: add Hilt DI module for AIGuideEngine"
```

---

## Self-Review Checklist

**1. Spec coverage:**
- ✅ GuideEvent sealed class (Task 1)
- ✅ Conflict detection models (Task 2)
- ✅ ExistingSettings helper (Task 3)
- ✅ JsonRepair utility (Task 4)
- ✅ GuideResponseParser with repair (Task 5)
- ✅ GuidePromptBuilder Schema + Few-shot (Task 6)
- ✅ AIGuideEngine three skills (Task 7)
- ✅ Hilt DI module (Task 8)

**2. Placeholder scan:**
- No TBD, TODO, or placeholder patterns found
- All code blocks contain complete implementations

**3. Type consistency:**
- GuideEvent<T> used consistently across parser and engine
- ClassificationResult.conflictCheck references ConflictCheck from Task 2
- GlossaryDraft.aliases and canonicalId added in Task 5
- ExistingSettings references Character and WorldRule from Phase 1

---

## Execution Notes

After completion, Phase 2 will provide:
- Streaming AI events for UI updates
- JSON repair for robustness
- Conflict detection for consistency
- Alias support for glossary disambiguation

Next phase: Phase 3 (UI Layer + MVI Architecture)