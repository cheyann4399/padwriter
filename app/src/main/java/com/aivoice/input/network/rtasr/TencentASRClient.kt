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
import okio.ByteString.Companion.toByteString
import java.net.URLEncoder
import java.util.*
import java.util.concurrent.TimeUnit
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

class TencentASRClient(
    private val secretId: String,
    private val secretKey: String,
    private val appId: String
) {
    private val client = OkHttpClient.Builder()
        .pingInterval(java.time.Duration.ofSeconds(20))
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    private var webSocket: WebSocket? = null
    private val gson = Gson()
    private var voiceId: String = UUID.randomUUID().toString()
    private var isConnected = false

    companion object {
        private const val TAG = "TencentASR"
        private const val BASE_URL = "wss://asr.cloud.tencent.com/asr/v2"
    }

    fun connect(): Flow<RTASRResult> = callbackFlow {
        voiceId = UUID.randomUUID().toString()
        val url = buildUrl()
        Log.d(TAG, "Connecting to: $url")

        val request = Request.Builder()
            .url(url)
            .build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d(TAG, "WebSocket connected")
                isConnected = true
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

                    // 检查是否是最终结果
                    val isFinal = json.get("final")?.asInt == 1

                    // 解析识别结果
                    val result = parseResult(json, isFinal)
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

    private fun buildUrl(): String {
        val timestamp = System.currentTimeMillis() / 1000
        val expired = timestamp + 3600 // 1小时有效期
        val nonce = Random().nextInt(1000000000)

        // 构建参数（不含 signature）
        val params = mutableMapOf<String, String>()
        params["secretid"] = secretId
        params["timestamp"] = timestamp.toString()
        params["expired"] = expired.toString()
        params["nonce"] = nonce.toString()
        params["engine_model_type"] = "16k_zh"
        params["voice_id"] = voiceId
        params["voice_format"] = "1" // PCM

        // 按字典序排序参数
        val sortedParams = params.toSortedMap()

        // 拼接签名原文（不含协议头）
        val host = "asr.cloud.tencent.com"
        val path = "/asr/v2/$appId"
        val queryString = sortedParams.entries.joinToString("&") { "${it.key}=${it.value}" }
        val signStr = "$host$path?$queryString"

        Log.d(TAG, "Sign string: $signStr")

        // 计算 HMAC-SHA1 签名
        val signature = generateSignature(signStr, secretKey)

        // URL 编码 signature
        val encodedSignature = URLEncoder.encode(signature, "UTF-8")

        // 构建最终 URL
        return "$BASE_URL/$appId?$queryString&signature=$encodedSignature"
    }

    private fun generateSignature(data: String, key: String): String {
        val mac = Mac.getInstance("HmacSHA1")
        val secretKey = SecretKeySpec(key.toByteArray(Charsets.UTF_8), "HmacSHA1")
        mac.init(secretKey)
        val hmacBytes = mac.doFinal(data.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(hmacBytes, Base64.NO_WRAP)
    }

    fun sendAudio(audioData: ByteArray) {
        if (!isConnected) {
            Log.w(TAG, "WebSocket not connected, skipping audio frame")
            return
        }
        // 腾讯云直接发送二进制音频数据
        val sent = webSocket?.send(audioData.toByteString()) ?: false
        Log.d(TAG, "Audio frame sent: $sent, size=${audioData.size}")
    }

    fun end() {
        // 发送结束识别通知
        val message = """{"type":"end"}"""
        Log.d(TAG, "Sending end message")
        webSocket?.send(message)
    }

    fun disconnect() {
        webSocket?.close(1000, "Normal closure")
        webSocket = null
        isConnected = false
    }

    private fun parseResult(json: JsonObject, isFinal: Boolean): RTASRResult? {
        val result = json.getAsJsonObject("result") ?: return null

        // 获取识别文本
        val text = result.get("voice_text_str")?.asString ?: return null
        if (text.isEmpty()) return null

        val sliceType = result.get("slice_type")?.asInt ?: 0
        // slice_type: 0=开始, 1=识别中, 2=结束

        Log.d(TAG, "Parsed result: text=$text, sliceType=$sliceType, isFinal=$isFinal")

        return RTASRResult(
            text = text,
            isFinal = isFinal || sliceType == 2,
            isMiddle = sliceType == 1
        )
    }
}
