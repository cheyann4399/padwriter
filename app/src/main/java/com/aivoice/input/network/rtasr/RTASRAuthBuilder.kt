package com.aivoice.input.network.rtasr

import android.util.Base64
import java.net.URLEncoder
import java.security.InvalidKeyException
import java.security.NoSuchAlgorithmException
import java.text.SimpleDateFormat
import java.util.*
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

object RTASRAuthBuilder {

    private const val HMAC_SHA1 = "HmacSHA1"

    fun buildUrl(
        baseUrl: String,
        appId: String,
        apiKey: String,
        apiSecret: String
    ): String {
        val dateFormat = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.US)
        dateFormat.timeZone = TimeZone.getTimeZone("GMT")
        val date = dateFormat.format(Date())

        val authorizationOrigin = "api_key=\"$apiKey\", algorithm=\"hmac-sha1\", headers=\"host date\", signature=\"\""
        val signature = generateSignature(authorizationOrigin, apiSecret)

        val authorization = "$authorizationOrigin, signature=\"$signature\""

        val encodedAuthorization = URLEncoder.encode(authorization, "UTF-8")
        val encodedDate = URLEncoder.encode(date, "UTF-8")

        return "$baseUrl?host=office-api-ast-dx.iflyaisol.com&date=$encodedDate&authorization=$encodedAuthorization"
    }

    private fun generateSignature(data: String, secret: String): String {
        return try {
            val mac = Mac.getInstance(HMAC_SHA1)
            val secretKey = SecretKeySpec(secret.toByteArray(Charsets.UTF_8), HMAC_SHA1)
            mac.init(secretKey)
            val hmacBytes = mac.doFinal(data.toByteArray(Charsets.UTF_8))
            Base64.encodeToString(hmacBytes, Base64.NO_WRAP)
        } catch (e: NoSuchAlgorithmException) {
            ""
        } catch (e: InvalidKeyException) {
            ""
        }
    }
}
