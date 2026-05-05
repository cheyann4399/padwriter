package com.aivoice.input.network.ai

import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import java.util.concurrent.TimeUnit

class MiniMaxClient(
    private val apiKey: String
) {
    private val client = OkHttpClient.Builder()
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()

    companion object {
        private const val TAG = "MiniMaxClient"
        private val JSON_MEDIA_TYPE = "application/json".toMediaType()
    }

    fun chatStream(prompt: String): Flow<String> = callbackFlow {
        val requestBody = buildRequestBody(prompt)
        val url = "${MiniMaxConfig.BASE_URL}/v1/messages"

        Log.d(TAG, "Starting stream request to: $url")
        Log.d(TAG, "Prompt length: ${prompt.length}")

        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $apiKey")
            .header("Content-Type", "application/json")
            .header("Accept", "text/event-stream")
            .post(requestBody.toRequestBody(JSON_MEDIA_TYPE))
            .build()

        val eventSourceFactory = EventSources.createFactory(client)

        val listener = object : EventSourceListener() {
            override fun onEvent(
                eventSource: EventSource,
                id: String?,
                type: String?,
                data: String
            ) {
                Log.d(TAG, "Received event: type=$type, data=$data")
                if (data == "[DONE]") {
                    Log.d(TAG, "Stream completed")
                    close()
                    return
                }

                val text = parseStreamChunk(data)
                if (text != null && text.isNotEmpty()) {
                    trySend(text)
                }
            }

            override fun onClosed(eventSource: EventSource) {
                Log.d(TAG, "Stream closed normally")
                close()
            }

            override fun onFailure(eventSource: EventSource, t: Throwable?, response: okhttp3.Response?) {
                Log.e(TAG, "Stream failure: ${t?.message}, response: ${response?.code} ${response?.message}")
                response?.body?.string()?.let { body ->
                    Log.e(TAG, "Error body: $body")
                }
                close(t ?: Exception("Unknown error"))
            }
        }

        eventSourceFactory.newEventSource(request, listener)

        awaitClose {
            // Cleanup if needed
        }
    }.flowOn(Dispatchers.IO)

    private fun buildRequestBody(prompt: String): String {
        val json = JsonObject().apply {
            addProperty("model", MiniMaxConfig.MODEL)
            addProperty("max_tokens", 8192)
            addProperty("stream", true)
            add("messages", gson.toJsonTree(listOf(
                mapOf("role" to "user", "content" to prompt)
            )))
        }
        return gson.toJson(json)
    }

    private fun parseStreamChunk(data: String): String? {
        return try {
            val json = gson.fromJson(data, JsonObject::class.java)

            // Try MiniMax/Anthropic content_block_delta format
            val delta = json.getAsJsonObject("delta")
            if (delta != null) {
                // text_delta format
                if (delta.has("text")) {
                    return delta.get("text").asString
                }
                // thinking_delta format (skip thinking content)
                if (delta.has("thinking")) {
                    return null  // Skip thinking blocks, only return text
                }
            }

            // Try OpenAI format
            val choices = json.getAsJsonArray("choices")
            if (choices != null && choices.size() > 0) {
                val deltaObj = choices[0].asJsonObject.getAsJsonObject("delta")
                if (deltaObj != null && deltaObj.has("content")) {
                    return deltaObj.get("content").asString
                }
            }

            null
        } catch (e: Exception) {
            Log.e(TAG, "Parse error: ${e.message}")
            null
        }
    }
}
