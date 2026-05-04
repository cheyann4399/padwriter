package com.aivoice.input.ai

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
    private val miniMaxClient: MiniMaxClient
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

        return """
你是一个小说写作助手。根据以下信息生成写作建议：

$contextInfo
用户刚输入的内容：$userInput

请生成 2-3 条写作建议，每条建议不超过 50 字，直接可插入正文。

输出 JSON 格式：
{
  "suggestions": [
    "建议1：具体内容...",
    "建议2：具体内容...",
    "建议3：具体内容..."
  ]
}

只输出 JSON，不要其他文字。
""".trimIndent()
    }

    private fun parseSuggestions(json: String): List<String> {
        return try {
            val trimmed = json.trim()
            val obj = JSONObject(trimmed)
            val array = obj.getJSONArray("suggestions")
            (0 until array.length()).map { array.getString(it) }
        } catch (e: Exception) {
            android.util.Log.e("SuggestionManager", "Parse error: ${e.message}")
            emptyList()
        }
    }
}
