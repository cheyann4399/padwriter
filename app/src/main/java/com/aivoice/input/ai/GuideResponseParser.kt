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
