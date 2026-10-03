package com.weno.szuguardian.data

import android.content.Context

data class AppConfig(
    val username: String = "",
    val password: String = "",
    val zone: String = ZONE_OFFICE,
    val intervalMinutes: Int = 1,
    val autostart: Boolean = false,
    val startOnLaunch: Boolean = true,
) {
    companion object {
        const val ZONE_AUTO = "auto"
        const val ZONE_OFFICE = "office"
        const val ZONE_DORMITORY = "dormitory"
        val ZONES = setOf(ZONE_AUTO, ZONE_OFFICE, ZONE_DORMITORY)
    }

    val isUsable: Boolean
        get() = username.isNotBlank() && password.isNotEmpty()

    fun validate() {
        require(username.isNotBlank()) { "请输入校园网账号" }
        require(password.isNotEmpty()) { "请输入校园网密码" }
        require(zone in ZONES) { "请选择正确的网络区域" }
        require(intervalMinutes in 1..1440) { "监控间隔应在 1 到 1440 分钟之间" }
    }
}

class ConfigStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun load(): AppConfig = AppConfig(
        username = prefs.getString(KEY_USERNAME, "").orEmpty(),
        password = prefs.getString(KEY_PASSWORD, "").orEmpty(),
        zone = prefs.getString(KEY_ZONE, AppConfig.ZONE_OFFICE) ?: AppConfig.ZONE_OFFICE,
        intervalMinutes = prefs.getInt(KEY_INTERVAL, 1).coerceIn(1, 1440),
        autostart = prefs.getBoolean(KEY_AUTOSTART, false),
        startOnLaunch = prefs.getBoolean(KEY_START_ON_LAUNCH, true),
    )

    fun save(config: AppConfig) {
        prefs.edit()
            .putString(KEY_USERNAME, config.username.trim())
            .putString(KEY_PASSWORD, config.password)
            .putString(KEY_ZONE, config.zone)
            .putInt(KEY_INTERVAL, config.intervalMinutes.coerceIn(1, 1440))
            .putBoolean(KEY_AUTOSTART, config.autostart)
            .putBoolean(KEY_START_ON_LAUNCH, config.startOnLaunch)
            .apply()
    }

    private companion object {
        const val PREFS_NAME = "szu_guardian_config"
        const val KEY_USERNAME = "username"
        const val KEY_PASSWORD = "password"
        const val KEY_ZONE = "zone"
        const val KEY_INTERVAL = "interval_minutes"
        const val KEY_AUTOSTART = "autostart"
        const val KEY_START_ON_LAUNCH = "start_on_launch"
    }
}
