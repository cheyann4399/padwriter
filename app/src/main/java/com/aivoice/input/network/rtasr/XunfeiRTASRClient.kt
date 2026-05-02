package com.aivoice.input.network.rtasr

import android.util.Base64
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
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit as JavaTimeUnit

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
    private val gson = Gson()
    private var isConnected = false
    private val connectLatch = CountDownLatch(1)

    companion object {
        private const val TAG = "XunfeiRTASR"
        private const val BASE_URL = "wss://iat-api.xfyun.cn/v2/iat"
    }

    fun connect(): Flow<RTASRResult> = callbackFlow {
        val url = RTASRAuthBuilder.buildUrl(BASE_URL, appId, apiKey, apiSecret)
        Log.d(TAG, "Connecting to: $url")

        val request = Request.Builder()
            .url(url)
            .build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d(TAG, "WebSocket connected")
                isConnected = true
                connectLatch.countDown()
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    Log.d(TAG, "Received message: $text")
                    val json = gson.fromJson(text, JsonObject::class.java)
                    val code = json.get("code")?.asInt

                    if (code != null && code != 0) {
                        val message = json.get("message")?.asString ?: "Unknown error"
                        Log.e(TAG, "Error: code=$code, message=$message")
                        close(IllegalStateException("Error $code: $message"))
                        return
                    }

                    // 解析识别结果
                    val result = parseResult(json)
                    if (result != null) {
                        trySend(result)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Parse error: ${e.message}")
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "WebSocket failure: ${t.message}")
                isConnected = false
                connectLatch.countDown()
                close(t)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "WebSocket closed: $reason")
                isConnected = false
                close()
            }
        })

        awaitClose {
            disconnect()
        }
    }.flowOn(Dispatchers.IO)

    private fun waitForConnection() {
        if (!isConnected) {
            connectLatch.await(5, JavaTimeUnit.SECONDS)
        }
    }

    fun sendAudio(audioData: ByteArray) {
        waitForConnection()
        if (!isConnected) {
            Log.w(TAG, "WebSocket not connected, skipping audio frame")
            return
        }
        // 发送音频数据，使用 IAT 协议格式
        val base64Audio = Base64.encodeToString(audioData, Base64.NO_WRAP)
        val message = buildAudioMessage(base64Audio, status = 1)
        Log.d(TAG, "Sending audio frame, size=${audioData.size}")
        val sent = webSocket?.send(message) ?: false
        Log.d(TAG, "Audio frame sent: $sent")
    }

    fun sendFirstFrame(audioData: ByteArray) {
        waitForConnection()
        if (!isConnected) {
            Log.w(TAG, "WebSocket not connected, skipping first frame")
            return
        }
        val base64Audio = Base64.encodeToString(audioData, Base64.NO_WRAP)
        val message = buildAudioMessage(base64Audio, status = 0)
        Log.d(TAG, "Sending first frame, size=${audioData.size}")
        val sent = webSocket?.send(message) ?: false
        Log.d(TAG, "First frame sent: $sent")
    }

    fun end() {
        // 发送结束帧
        val message = buildAudioMessage("", status = 2)
        Log.d(TAG, "Sending end frame")
        webSocket?.send(message)
    }

    private fun buildAudioMessage(base64Audio: String, status: Int): String {
        val json = JsonObject().apply {
            add("common", JsonObject().apply {
                addProperty("app_id", appId)
            })
            add("business", JsonObject().apply {
                addProperty("language", "zh_cn")
                addProperty("domain", "iat")
                addProperty("accent", "mandarin")
                addProperty("vad_eos", 2000)
            })
            add("data", JsonObject().apply {
                addProperty("status", status)
                addProperty("format", "audio/L16;rate=16000")
                addProperty("encoding", "raw")
                addProperty("audio", base64Audio)
            })
        }
        return gson.toJson(json)
    }

    fun disconnect() {
        webSocket?.close(1000, "Normal closure")
        webSocket = null
    }

    private fun parseResult(json: JsonObject): RTASRResult? {
        val data = json.getAsJsonObject("data") ?: return null
        val result = data.getAsJsonObject("result") ?: return null

        val ws = result.getAsJsonArray("ws") ?: return null
        if (ws.size() == 0) return null

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

        val isFinal = result.get("ls")?.asBoolean ?: false
        val pg = result.getAsJsonObject("pg")
        val isMiddle = pg != null

        Log.d(TAG, "Parsed result: text=$text, isFinal=$isFinal, isMiddle=$isMiddle")

        return RTASRResult(
            text = text,
            isFinal = isFinal,
            isMiddle = isMiddle
        )
    }
}
