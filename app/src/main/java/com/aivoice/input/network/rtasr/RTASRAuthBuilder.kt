package com.aivoice.input.network.rtasr

import android.util.Base64
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.*
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

object RTASRAuthBuilder {

    private const val HMAC_SHA256 = "HmacSHA256"

    fun buildUrl(
        baseUrl: String,
        appId: String,
        apiKey: String,
        apiSecret: String
    ): String {
        // RFC1123 格式的 UTC 时间
        val dateFormat = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.US)
        dateFormat.timeZone = TimeZone.getTimeZone("GMT")
        val date = dateFormat.format(Date())

        val host = "iat-api.xfyun.cn"

        // 构造 signature 原始字段
        val signatureOrigin = "host: $host\ndate: $date\nGET /v2/iat HTTP/1.1"

        // 使用 apiSecret 进行 hmac-sha256 签名
        val signature = generateSignature(signatureOrigin, apiSecret)

        // 构造 authorization_origin
        val authorizationOrigin = "api_key=\"$apiKey\", algorithm=\"hmac-sha256\", headers=\"host date request-line\", signature=\"$signature\""

        // base64 编码
        val authorization = Base64.encodeToString(authorizationOrigin.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)

        // URL 编码
        val encodedAuthorization = URLEncoder.encode(authorization, "UTF-8")
        val encodedDate = URLEncoder.encode(date, "UTF-8")

        return "$baseUrl?authorization=$encodedAuthorization&date=$encodedDate&host=$host"
    }

    private fun generateSignature(data: String, secret: String): String {
        val mac = Mac.getInstance(HMAC_SHA256)
        val secretKey = SecretKeySpec(secret.toByteArray(Charsets.UTF_8), HMAC_SHA256)
        mac.init(secretKey)
        val hmacBytes = mac.doFinal(data.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(hmacBytes, Base64.NO_WRAP)
    }
}
