package com.aivoice.input.network.rtasr

import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import okhttp3.*
import java.util.concurrent.TimeUnit

class XunfeiRTASRClient(
    private val appId: String,
    private val apiKey: String,
    private val apiSecret: String
) {
    private val client = OkHttpClient.Builder()
        .pingInterval(java.time.Duration.ofSeconds(20))
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    private var webSocket: WebSocket? = null
    private var sessionId: String? = null
    private val gson = Gson()

    companion object {
        private const val TAG = "XunfeiRTASR"
        private const val BASE_URL = "wss://office-api-ast-dx.iflyaisol.com/ast/communicate/v1"
    }

    fun connect(): Flow<RTASRResult> = callbackFlow {
        val url = RTASRAuthBuilder.buildUrl(BASE_URL, appId, apiKey, apiSecret)

        val request = Request.Builder()
            .url(url)
            .build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d(TAG, "WebSocket connected")
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    val json = gson.fromJson(text, JsonObject::class.java)
                    val action = json.get("action")?.asString

                    when (action) {
                        "started" -> {
                            sessionId = json.get("data")?.asJsonObject?.get("sessionId")?.asString
                            Log.d(TAG, "Session started: $sessionId")
                        }
                        "result" -> {
                            val result = parseResult(json)
                            if (result != null) {
                                trySend(result)
                            }
                        }
                        "error" -> {
                            val errorMsg = json.get("data")?.asJsonObject?.get("message")?.asString
                            Log.e(TAG, "Error: $errorMsg")
                            close(IllegalStateException(errorMsg))
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Parse error: ${e.message}")
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "WebSocket failure: ${t.message}")
                close(t)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "WebSocket closed: $reason")
                close()
            }
        })

        awaitClose {
            disconnect()
        }
    }.flowOn(Dispatchers.IO)

    fun sendAudio(audioData: ByteArray) {
        webSocket?.send(audioData.toByteString())
    }

    fun end() {
        sessionId?.let { sid ->
            val endMessage = """{"end": true, "sessionId": "$sid"}"""
            webSocket?.send(endMessage)
        }
    }

    fun disconnect() {
        webSocket?.close(1000, "Normal closure")
        webSocket = null
        sessionId = null
    }

    private fun parseResult(json: JsonObject): RTASRResult? {
        val data = json.getAsJsonObject("data") ?: return null
        val cn = data.getAsJsonObject("cn") ?: return null
        val st = cn.getAsJsonObject("st") ?: return null
        val rt = st.getAsJsonArray("rt") ?: return null

        if (rt.size() == 0) return null

        val ws = rt[0].asJsonObject.getAsJsonArray("ws") ?: return null

        val textBuilder = StringBuilder()
        for (wsItem in ws) {
            val cwArray = wsItem.asJsonObject.getAsJsonArray("cw")
            for (cwItem in cwArray) {
                val w = cwItem.asJsonObject.get("w")?.asString ?: ""
                textBuilder.append(w)
            }
        }

        val text = textBuilder.toString()
        if (text.isEmpty()) return null

        val type = st.get("type")?.asInt ?: 0
        val isFinal = data.get("ls")?.asBoolean ?: false

        return RTASRResult(
            text = text,
            isFinal = isFinal,
            isMiddle = type == 1
        )
    }

    private fun ByteArray.toByteString(): okio.ByteString {
        return okio.ByteString.of(*this)
    }
}
