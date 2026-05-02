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
        val result = tryParse(json, object : TypeToken<BeatResponse>() {})
        return when (result) {
            is ParseResult.Success -> GuideEvent.Complete(result.data.beats)
            is ParseResult.Repaired -> GuideEvent.Repaired(result.data.beats, result.originalJson)
            null -> GuideEvent.Error("Failed to parse beats", json)
        }
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
        // First attempt: direct parse
        try {
            val result = gson.fromJson<T>(json, typeToken.type)
            return ParseResult.Success(result)
        } catch (e: JsonSyntaxException) {
            Log.w(TAG, "Direct parse failed: ${e.message}")
        }

        // Second attempt: repair and parse
        if (JsonRepair.isRepairable(json)) {
            val repaired = JsonRepair.repair(json)
            try {
                val result = gson.fromJson<T>(repaired, typeToken.type)
                Log.i(TAG, "JSON repaired successfully")
                return ParseResult.Repaired(result, json)
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
