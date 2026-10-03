package com.weno.szuguardian.network

import android.util.Base64
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.security.MessageDigest
import java.util.Locale
import java.util.regex.Pattern
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * 深澜（SRun）认证，用于教学 / 办公区。
 *
 * 协议实现移植自桌面端 szu_guardian/srun.py，后者是 MIT 许可的
 * Sleepstars/SZU-login、vidar-team/srun-login 项目的 Python 移植。
 * 许可证说明见仓库根目录 THIRD_PARTY_NOTICES.md。
 */
private const val SRUN_BASE_URL = "https://net.szu.edu.cn"
private const val SRUN_ALPHA = "LVoJPiCN2R8G90yg+hmFHuacZ1OWMnrsSTXkYpUq/3dlbfKwv6xztjI7DeBE45QA"
private const val STANDARD_ALPHA = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"

private val JSONP_PATTERN = Pattern.compile("^[^(]*\\((.*)\\)\\s*;?\\s*$", Pattern.DOTALL)

data class SrunLoginResult(
    val success: Boolean,
    val message: String,
    val clientIp: String = "",
)

class SrunClient(
    private val username: String,
    private val password: String,
    private val client: OkHttpClient = guardianHttpClient(),
    private val baseUrl: String = SRUN_BASE_URL,
) {

    fun login(): SrunLoginResult {
        val acId = discoverAcId()
        val challengeData = getJsonp(
            "/cgi-bin/get_challenge",
            mapOf("callback" to "_", "username" to username, "ip" to ""),
        )

        val error = challengeData.optString("error")
        if (error.isNotEmpty() && error != "ok") {
            return SrunLoginResult(
                false,
                challengeData.optString("error_msg").ifBlank { error },
            )
        }

        val challenge = challengeData.optString("challenge")
        val clientIp = challengeData.optString("client_ip")
        if (challenge.isBlank() || clientIp.isBlank()) {
            return SrunLoginResult(false, "认证服务器未返回 challenge 或客户端 IP")
        }

        val passwordMd5 = hmacMd5(password, challenge)
        val infoJson = JSONObject().apply {
            put("username", username)
            put("password", password)
            put("ip", clientIp)
            put("acid", acId)
            put("enc_ver", "srun_bx1")
        }.toString()
        val info = "{SRBX1}" + srunBase64(xencode(infoJson, challenge))
        val checksumSource = buildString {
            append(challenge).append(username)
            append(challenge).append(passwordMd5)
            append(challenge).append(acId)
            append(challenge).append(clientIp)
            append(challenge).append("200")
            append(challenge).append("1")
            append(challenge).append(info)
        }
        val checksum = sha1Hex(checksumSource)

        val portalData = getJsonp(
            "/cgi-bin/srun_portal",
            mapOf(
                "callback" to "_",
                "action" to "login",
                "username" to username,
                "password" to "{MD5}$passwordMd5",
                "os" to "Android",
                "name" to "Android",
                "double_stack" to "0",
                "info" to info,
                "chksum" to checksum,
                "ac_id" to acId,
                "ip" to clientIp,
                "n" to "200",
                "type" to "1",
            ),
        )

        val success = portalData.optString("error") == "ok" ||
            portalData.optString("res") == "ok" ||
            portalData.opt("st")?.toString() == "1"

        if (success) {
            return SrunLoginResult(true, "教学 / 办公区认证成功", clientIp)
        }

        val message = portalData.optString("error_msg").takeIf { it.isNotBlank() }
            ?: portalData.optString("error").takeIf { it.isNotBlank() }
            ?: portalData.optString("res").takeIf { it.isNotBlank() }
            ?: "认证失败"
        return SrunLoginResult(false, message, clientIp)
    }

    fun discoverAcId(): String = try {
        val request = Request.Builder().url(baseUrl.trimEnd('/') + "/").get().build()
        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            Regex("ac_id=(\\d+)").find(body)?.groupValues?.get(1)
        } ?: DEFAULT_AC_ID
    } catch (e: Exception) {
        DEFAULT_AC_ID
    }

    private fun getJsonp(path: String, params: Map<String, String>): JSONObject {
        val query = params.entries.joinToString("&") { (key, value) ->
            "${encode(key)}=${encode(value)}"
        }
        val url = "${baseUrl.trimEnd('/')}$path?$query"
        val request = Request.Builder().url(url).get().build()
        val body = client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw java.io.IOException("认证服务器返回 HTTP ${response.code}")
            }
            response.body?.string().orEmpty()
        }
        return parseJsonp(body)
    }

    private fun encode(value: String): String =
        URLEncoder.encode(value, "UTF-8").replace("+", "%20")

    companion object {
        private const val DEFAULT_AC_ID = "1"
    }
}

internal fun parseJsonp(text: String): JSONObject {
    val matcher = JSONP_PATTERN.matcher(text.trim())
    if (!matcher.find()) {
        throw java.io.IOException("认证服务器返回了无法识别的数据")
    }
    return try {
        JSONObject(matcher.group(1) ?: "")
    } catch (e: Exception) {
        throw java.io.IOException("认证服务器返回了无效 JSON", e)
    }
}

/** SRun 使用的 XXTEA 变体。 */
internal fun xencode(content: String, key: String): ByteArray {
    if (content.isEmpty()) return ByteArray(0)

    val values = words(content.toByteArray(Charsets.UTF_8), true)
    val rawKey = words(key.toByteArray(Charsets.UTF_8), false)
    val keyValues = if (rawKey.size >= 4) {
        rawKey
    } else {
        IntArray(4).also { rawKey.copyInto(it) }
    }

    val n = values.size - 1
    var z = values[n]
    var y = values[0]
    val delta = 0x9E3779B9.toInt()
    var total = 0
    var rounds = 6 + 52 / (n + 1)

    while (rounds > 0) {
        total += delta
        val e = (total ushr 2) and 3
        for (p in 0 until n) {
            y = values[p + 1]
            var mixed = (z ushr 5) xor (y shl 2)
            mixed += ((y ushr 3) xor (z shl 4)) xor (total xor y)
            mixed += keyValues[(p and 3) xor e] xor z
            values[p] += mixed
            z = values[p]
        }
        y = values[0]
        var mixed = (z ushr 5) xor (y shl 2)
        mixed += ((y ushr 3) xor (z shl 4)) xor (total xor y)
        mixed += keyValues[(n and 3) xor e] xor z
        values[n] += mixed
        z = values[n]
        rounds--
    }

    val output = ByteArray(values.size * 4)
    values.forEachIndexed { index, value ->
        output[index * 4] = value.toByte()
        output[index * 4 + 1] = (value ushr 8).toByte()
        output[index * 4 + 2] = (value ushr 16).toByte()
        output[index * 4 + 3] = (value ushr 24).toByte()
    }
    return output
}

private fun words(content: ByteArray, includeLength: Boolean): IntArray {
    val size = (content.size + 3) / 4 + if (includeLength) 1 else 0
    val result = IntArray(size)
    for (index in content.indices step 4) {
        var value = 0
        for (offset in 0..3) {
            val byte = if (index + offset < content.size) {
                content[index + offset].toInt() and 0xFF
            } else {
                0
            }
            value = value or (byte shl (offset * 8))
        }
        result[index / 4] = value
    }
    if (includeLength) result[size - 1] = content.size
    return result
}

internal fun srunBase64(value: ByteArray): String {
    val encoded = Base64.encodeToString(value, Base64.NO_WRAP)
    return encoded.map { char ->
        val index = STANDARD_ALPHA.indexOf(char)
        if (index >= 0) SRUN_ALPHA[index] else char
    }.joinToString("")
}

internal fun hmacMd5(password: String, challenge: String): String {
    val mac = Mac.getInstance("HmacMD5").apply {
        init(SecretKeySpec(challenge.toByteArray(Charsets.UTF_8), "HmacMD5"))
    }
    return mac.doFinal(password.toByteArray(Charsets.UTF_8)).toHex()
}

internal fun sha1Hex(value: String): String =
    MessageDigest.getInstance("SHA-1").digest(value.toByteArray(Charsets.UTF_8)).toHex()

private fun ByteArray.toHex(): String = joinToString("") { byte ->
    String.format(Locale.US, "%02x", byte.toInt() and 0xFF)
}
