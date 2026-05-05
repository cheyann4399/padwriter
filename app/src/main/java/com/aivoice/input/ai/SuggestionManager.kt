package com.aivoice.input.ai

import android.content.Context
import com.aivoice.input.model.BeatContext
import com.aivoice.input.network.ai.MiniMaxClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import org.json.JSONObject

/**
 * AI 建议生成管理器
 */
class SuggestionManager(
    private val miniMaxClient: MiniMaxClient,
    private val context: Context? = null
) {
    /**
     * 生成写作建议
     */
    fun generateSuggestions(
        userInput: String,
        context: BeatContext?
    ): Flow<Result<List<String>>> = flow {
        val prompt = buildSuggestionPrompt(userInput, context)

        val result = StringBuilder()
        miniMaxClient.chatStream(prompt).collect { chunk ->
            result.append(chunk)
        }

        val suggestions = parseSuggestions(result.toString())
        emit(Result.success(suggestions))
    }.flowOn(Dispatchers.IO)

    private fun buildSuggestionPrompt(userInput: String, context: BeatContext?): String {
        val contextInfo = if (context != null) {
            """
当前节拍：${context.beatTitle} - ${context.beatSummary}
相关人设：${context.characters.take(3).joinToString("、") { it.name }}
世界观规则：${context.worldRules.take(2).joinToString("、") { it.title }}
"""
        } else {
            "当前无节拍上下文"
        }

        // 读取用户自定义文风设置
        val writingStyleRequirement = getWritingStyleRequirement()

        return """
你是一个小说写作助手。用户刚刚输入了一段文字，现在需要你提供 2-3 条优化建议。

$contextInfo
用户输入的内容：$userInput

$writingStyleRequirement

请提供以下类型的建议（每条建议是完整的文本，可直接替换或追加）：
1. 优化版本：在原文基础上增加细节描写、情感渲染，使文字更生动
2. 美化版本：使用更优美的词汇和修辞手法，提升文学性
3. 续写建议：根据节拍内容，提供接下来可能的情节发展（1-2句话）

每条建议应该：
- 保持原意和人物性格
- 符合节拍设定和世界观
- 长度适中（20-80字）
- 可以直接使用

输出 JSON 格式：
{
  "suggestions": [
    "优化后的完整文本...",
    "美化后的完整文本...",
    "续写的内容..."
  ]
}

只输出 JSON，不要其他文字。
""".trimIndent()
    }

    private fun getWritingStyleRequirement(): String {
        if (this.context == null) return ""

        val prefs = this.context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        val preset = prefs.getString("writing_style_preset", "none") ?: "none"
        val custom = prefs.getString("writing_style_custom", "") ?: ""

        val presetRequirement = when (preset) {
            "novel" -> "输出格式要求：小说格式 - 使用叙述性语言，注重场景描写和心理刻画。"
            "script" -> "输出格式要求：剧本格式 - 角色名在前，冒号后接对话，舞台指示用括号标注。例如：张三：（激动地）我终于找到了！"
            "essay" -> "输出格式要求：论文格式 - 使用学术性语言，逻辑严谨，论点明确。"
            else -> ""
        }

        val customRequirement = if (custom.isNotBlank()) {
            "用户自定义格式要求：$custom"
        } else {
            ""
        }

        return listOf(presetRequirement, customRequirement)
            .filter { it.isNotBlank() }
            .joinToString("\n")
    }

    private fun parseSuggestions(json: String): List<String> {
        return try {
            // 去除 markdown 代码块标记
            var trimmed = json.trim()
            if (trimmed.startsWith("```json")) {
                trimmed = trimmed.removePrefix("```json").trim()
            }
            if (trimmed.startsWith("```")) {
                trimmed = trimmed.removePrefix("```").trim()
            }
            if (trimmed.endsWith("```")) {
                trimmed = trimmed.removeSuffix("```").trim()
            }

            val obj = JSONObject(trimmed)
            val array = obj.getJSONArray("suggestions")
            (0 until array.length()).map { array.getString(it) }
        } catch (e: Exception) {
            android.util.Log.e("SuggestionManager", "Parse error: ${e.message}")
            android.util.Log.e("SuggestionManager", "Raw JSON: $json")
            emptyList()
        }
    }
}
