package com.weno.szuguardian.network

import com.weno.szuguardian.data.AppConfig
import kotlinx.coroutines.delay
import okhttp3.OkHttpClient
import okhttp3.Request

/** 宿舍区认证入口（eportal）。 */
private const val DORMITORY_LOGIN_URL = "http://172.30.255.42:801/eportal/portal/login/"

private val CHECK_TARGETS = listOf(
    "https://www.baidu.com/favicon.ico" to "baidu",
    "http://www.msftconnecttest.com/connecttest.txt" to "Microsoft Connect Test",
)

class NetworkClient(private val client: OkHttpClient = guardianHttpClient()) {

    data class ConnectionResult(
        val connected: Boolean,
        val message: String,
        val latencyMs: Int? = null,
    )

    private val verifyDelays = longArrayOf(2_000L, 4_000L, 8_000L)

    /** 与桌面端一致：直连外网可访问即视为在线。 */
    fun checkConnection(): ConnectionResult {
        val started = System.nanoTime()
        val errors = mutableListOf<String>()

        for ((url, expected) in CHECK_TARGETS) {
            try {
                val request = Request.Builder().url(url).get().build()
                client.newCall(request).execute().use { response ->
                    val valid = if (expected == "baidu") {
                        response.isSuccessful && response.request.url.host.endsWith("baidu.com")
                    } else {
                        val body = response.body?.string().orEmpty()
                        response.isSuccessful && body.contains(expected)
                    }
                    if (valid) {
                        val latency = ((System.nanoTime() - started) / 1_000_000).toInt()
                        return ConnectionResult(true, "网络连接正常", latency)
                    }
                    errors.add("检测页返回 ${response.code}")
                }
            } catch (e: Exception) {
                errors.add(e.javaClass.simpleName)
            }
        }

        val detail = errors.takeLast(2).joinToString(" / ").ifEmpty { "无响应" }
        return ConnectionResult(false, "直连外网不可用（$detail）")
    }

    fun sendLogin(config: AppConfig, onProgress: (String) -> Unit = {}): String = when (config.zone) {
        AppConfig.ZONE_DORMITORY -> {
            onProgress("正在使用宿舍区认证…")
            loginDormitory(config)
        }

        AppConfig.ZONE_OFFICE -> {
            onProgress("正在使用教学 / 办公区认证…")
            loginTeaching(config)
        }

        AppConfig.ZONE_AUTO -> loginAuto(config, onProgress)

        else -> throw IllegalStateException("无法确定校园网区域")
    }

    suspend fun ensureConnected(
        config: AppConfig,
        onProgress: (String) -> Unit = {},
    ): ConnectionResult {
        val current = checkConnection()
        if (current.connected) return current

        val loginMessage = sendLogin(config, onProgress)
        onProgress("$loginMessage，正在等待外网恢复…")

        verifyDelays.forEachIndexed { index, delayMs ->
            delay(delayMs)
            val verified = checkConnection()
            if (verified.connected) {
                return ConnectionResult(true, "已自动重连，网络恢复正常", verified.latencyMs)
            }
            if (index < verifyDelays.size - 1) {
                onProgress("外网尚未恢复，${verifyDelays[index + 1] / 1000} 秒后再次复查…")
            }
        }

        return ConnectionResult(
            false,
            "认证服务器已确认请求，但外网仍不可用；请检查账号状态或所在网络区域",
        )
    }

    private fun loginAuto(config: AppConfig, onProgress: (String) -> Unit): String {
        val errors = mutableListOf<String>()
        val attempts = listOf(
            "教学 / 办公区" to { loginTeaching(config) },
            "宿舍区" to { loginDormitory(config) },
        )
        for ((zoneName, login) in attempts) {
            onProgress("自动尝试：正在使用${zoneName}认证…")
            try {
                return login()
            } catch (e: Exception) {
                errors.add("$zoneName：${e.message}")
            }
        }
        throw IllegalStateException(
            "自动尝试均未成功，请手动选择实际区域。${errors.joinToString("；")}",
        )
    }

    private fun loginDormitory(config: AppConfig): String {
        val url = "$DORMITORY_LOGIN_URL?user_account=${encode(config.username)}" +
            "&user_password=${encode(config.password)}"
        val request = Request.Builder().url(url).get().build()
        val body = client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw java.io.IOException("宿舍区认证服务器返回 HTTP ${response.code}")
            }
            response.body?.string().orEmpty()
        }
        val payload = parseJsonp(body)
        val message = payload.optString("msg").trim()
        val result = payload.opt("result")
        val success = result?.toString() in setOf("1", "true") ||
            message.contains("success", ignoreCase = true) ||
            message.contains("成功")
        if (!success) {
            throw IllegalStateException(message.ifEmpty { "宿舍区认证失败，请检查账号和密码" })
        }
        return message.ifEmpty { "宿舍区认证成功" }
    }

    private fun loginTeaching(config: AppConfig): String {
        val result = SrunClient(config.username, config.password, client).login()
        if (!result.success) {
            throw IllegalStateException("教学 / 办公区认证失败：${result.message}")
        }
        return result.message
    }

    private fun encode(value: String): String =
        java.net.URLEncoder.encode(value, "UTF-8").replace("+", "%20")
}
