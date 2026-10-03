package com.weno.szuguardian

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 服务与界面共享的运行状态（同一进程内）。 */
object GuardianState {

    enum class Phase { IDLE, CHECKING, ONLINE, OFFLINE }

    data class Status(
        val phase: Phase = Phase.IDLE,
        val message: String = "尚未启动守护",
        val latencyMs: Int? = null,
    )

    private const val MAX_LOGS = 120

    private val _running = MutableStateFlow(false)
    val running = _running.asStateFlow()

    private val _status = MutableStateFlow(Status())
    val status = _status.asStateFlow()

    private val _logs = MutableStateFlow<List<String>>(emptyList())
    val logs = _logs.asStateFlow()

    fun setRunning(running: Boolean) {
        _running.value = running
    }

    fun update(phase: Phase, message: String, latencyMs: Int? = null) {
        _status.value = Status(phase, message, latencyMs)
    }

    fun log(message: String) {
        val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        _logs.value = (_logs.value + "$time  $message").takeLast(MAX_LOGS)
    }
}
