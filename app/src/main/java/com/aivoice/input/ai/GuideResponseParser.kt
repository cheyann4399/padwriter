package com.aivoice.input.ai

import android.util.Log
import com.aivoice.input.model.draft.*
import com.aivoice.input.model.router.ExecutionResult
import com.aivoice.input.model.router.RouterDecision
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
        Log.d(TAG, "parseBeats: input json length=${json.length}")
        Log.d(TAG, "parseBeats: input json='${json.take(300)}'")
        val result = tryParse(json, object : TypeToken<BeatResponse>() {})
        val event = when (result) {
            is ParseResult.Success -> {
                Log.d(TAG, "parseBeats: Success with ${result.data.beats.size} beats")
                GuideEvent.Complete(result.data.beats)
            }
            is ParseResult.Repaired -> {
                Log.d(TAG, "parseBeats: Repaired with ${result.data.beats.size} beats")
                GuideEvent.Repaired(result.data.beats, result.originalJson)
            }
            null -> {
                Log.e(TAG, "parseBeats: Failed to parse")
                GuideEvent.Error("Failed to parse beats", json)
            }
        }
        return event
    }

    /**
     * Parse classification response.
     */
    fun parseClassification(json: String): GuideEvent<ClassificationResult> {
        val result = tryParse(json, object : TypeToken<ClassificationResponse>() {})
        return when (result) {
            is ParseResult.Success -> GuideEvent.Complete(result.data.result)
            is ParseResult.Repaired -> GuideEvent.Repaired(result.data.result, result.originalJson)
            null -> GuideEvent.Error("Failed to parse classification", json)
        }
    }

    /**
     * Parse glossary response.
     */
    fun parseGlossary(json: String): GuideEvent<List<GlossaryDraft>> {
        val result = tryParse(json, object : TypeToken<GlossaryResponse>() {})
        return when (result) {
            is ParseResult.Success -> GuideEvent.Complete(result.data.glossary)
            is ParseResult.Repaired -> GuideEvent.Repaired(result.data.glossary, result.originalJson)
            null -> GuideEvent.Error("Failed to parse glossary", json)
        }
    }

    /**
     * Parse execution result response.
     */
    fun parseExecutionResult(json: String): GuideEvent<ExecutionResult> {
        Log.d(TAG, "parseExecutionResult: input json length=${json.length}")
        val result = tryParse(json, object : TypeToken<ExecutionResult>() {})
        return when (result) {
            is ParseResult.Success -> {
                Log.d(TAG, "parseExecutionResult: Success")
                GuideEvent.Complete(result.data)
            }
            is ParseResult.Repaired -> {
                Log.d(TAG, "parseExecutionResult: Repaired")
                GuideEvent.Repaired(result.data, result.originalJson)
            }
            null -> {
                Log.e(TAG, "parseExecutionResult: Failed to parse")
                GuideEvent.Error("Failed to parse execution result", json)
            }
        }
    }

    /**
     * Result of parsing attempt - either direct success or repaired.
     */
    private sealed class ParseResult<T> {
        data class Success<T>(val data: T) : ParseResult<T>()
        data class Repaired<T>(val data: T, val originalJson: String) : ParseResult<T>()
    }

    /**
     * Try to parse JSON, with repair fallback.
     */
    private inline fun <reified T> tryParse(json: String, typeToken: TypeToken<T>): ParseResult<T>? {
        // Strip markdown code blocks if present
        var cleanedJson = json.trim()

        // Remove leading whitespace/newlines until we find { or [
        while (cleanedJson.isNotEmpty() && !cleanedJson.startsWith("{") && !cleanedJson.startsWith("[")) {
            // Strip markdown code block prefix
            if (cleanedJson.startsWith("```json")) {
                cleanedJson = cleanedJson.removePrefix("```json").trim()
            } else if (cleanedJson.startsWith("```")) {
                cleanedJson = cleanedJson.removePrefix("```").trim()
            } else {
                // Remove first character (likely whitespace or newline)
                cleanedJson = cleanedJson.substring(1).trim()
            }
        }

        // Strip trailing markdown code block if present (handle various formats)
        while (cleanedJson.endsWith("```") || cleanedJson.endsWith("\n```") || cleanedJson.endsWith("\n}\n```")) {
            if (cleanedJson.endsWith("```")) {
                cleanedJson = cleanedJson.removeSuffix("```").trim()
            }
        }

        // Also remove any trailing content after the last closing brace
        val lastBraceIndex = cleanedJson.lastIndexOf('}')
        if (lastBraceIndex > 0 && lastBraceIndex < cleanedJson.length - 1) {
            cleanedJson = cleanedJson.substring(0, lastBraceIndex + 1)
        }

        Log.d(TAG, "tryParse: cleaned json length=${cleanedJson.length}, starts with='${cleanedJson.take(20)}'")

        // First attempt: direct parse
        try {
            Log.d(TAG, "tryParse: attempting direct parse")
            val result = gson.fromJson<T>(cleanedJson, typeToken.type)
            Log.d(TAG, "tryParse: direct parse success")
            return ParseResult.Success(result)
        } catch (e: JsonSyntaxException) {
            Log.w(TAG, "tryParse: direct parse failed: ${e.message}")
        }

        // Second attempt: repair and parse
        if (JsonRepair.isRepairable(cleanedJson)) {
            Log.d(TAG, "tryParse: attempting repair")
            val repaired = JsonRepair.repair(cleanedJson)
            try {
                val result = gson.fromJson<T>(repaired, typeToken.type)
                Log.i(TAG, "tryParse: JSON repaired successfully")
                return ParseResult.Repaired(result, json)
            } catch (e: JsonSyntaxException) {
                Log.w(TAG, "tryParse: repair parse failed: ${e.message}")
            }
        } else {
            Log.d(TAG, "tryParse: JSON not repairable")
        }

        return null
    }

    // Response wrapper classes for Gson parsing
    private data class BeatResponse(val beats: List<BeatDraft>)
    private data class ClassificationResponse(val result: ClassificationResult)
    private data class GlossaryResponse(val glossary: List<GlossaryDraft>)
}
