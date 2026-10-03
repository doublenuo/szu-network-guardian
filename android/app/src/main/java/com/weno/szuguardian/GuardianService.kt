package com.weno.szuguardian

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.weno.szuguardian.data.AppConfig
import com.weno.szuguardian.data.ConfigStore
import com.weno.szuguardian.network.NetworkClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * 常驻前台服务：Android 上没有系统托盘，常驻通知栏就是等价物。
 * 点击通知回到主界面，通知上的按钮可以立即检测 / 停止守护。
 */
class GuardianService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val wake = Channel<Unit>(Channel.CONFLATED)
    private var loopJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        createChannel(this)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        promoteToForeground()
        when (intent?.action) {
            ACTION_STOP -> stopSelf()
            ACTION_CHECK -> {
                if (loopJob?.isActive == true) wake.trySend(Unit) else runOnce()
            }

            else -> startLoop()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        loopJob?.cancel()
        scope.cancel()
        GuardianState.setRunning(false)
        super.onDestroy()
    }

    private fun promoteToForeground() {
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            buildNotification(),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
        )
    }

    private fun startLoop() {
        if (loopJob?.isActive == true) {
            wake.trySend(Unit)
            return
        }
        GuardianState.setRunning(true)
        loopJob = scope.launch {
            while (isActive) {
                val config = ConfigStore(this@GuardianService).load()
                runCheck(config)
                val waitMs = config.intervalMinutes.coerceAtLeast(1) * 60_000L
                withTimeoutOrNull(waitMs) { wake.receive() }
            }
        }
    }

    private fun runOnce() {
        GuardianState.setRunning(true)
        scope.launch {
            runCheck(ConfigStore(this@GuardianService).load())
            if (loopJob?.isActive != true) {
                GuardianState.setRunning(false)
                stopSelf()
            }
        }
    }

    private suspend fun runCheck(config: AppConfig) {
        if (!config.isUsable) {
            val message = "尚未填写账号和密码，无法自动重连"
            GuardianState.update(GuardianState.Phase.OFFLINE, message)
            GuardianState.log(message)
            refreshNotification()
            return
        }

        val client = NetworkClient()
        try {
            GuardianState.update(GuardianState.Phase.CHECKING, "正在检测网络状态…")
            refreshNotification()

            val result = client.ensureConnected(config) { progress ->
                GuardianState.update(GuardianState.Phase.CHECKING, progress)
                refreshNotification()
            }

            if (result.connected) {
                val detail = result.latencyMs?.let { "${result.message}（${it} ms）" } ?: result.message
                GuardianState.update(GuardianState.Phase.ONLINE, detail, result.latencyMs)
                GuardianState.log(result.message)
            } else {
                GuardianState.update(GuardianState.Phase.OFFLINE, result.message)
                GuardianState.log(result.message)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            val message = "检测或重连失败：${error.message ?: error.javaClass.simpleName}"
            GuardianState.update(GuardianState.Phase.OFFLINE, message)
            GuardianState.log(message)
        }
        refreshNotification()
    }

    private fun refreshNotification() {
        val manager = getSystemService(NotificationManager::class.java) ?: return
        manager.notify(NOTIFICATION_ID, buildNotification())
    }

    private fun buildNotification(): Notification {
        val status = GuardianState.status.value
        val openIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val title = if (GuardianState.running.value) {
            "SZU 网络守护 · 守护中"
        } else {
            "SZU 网络守护"
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_guardian)
            .setContentTitle(title)
            .setContentText(status.message)
            .setContentIntent(openIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(Notification.CATEGORY_SERVICE)
            .addAction(0, "立即检测", serviceIntent(ACTION_CHECK, 1))
            .addAction(0, "停止守护", serviceIntent(ACTION_STOP, 2))
            .build()
    }

    private fun serviceIntent(action: String, requestCode: Int): PendingIntent =
        PendingIntent.getService(
            this,
            requestCode,
            Intent(this, GuardianService::class.java).setAction(action),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    companion object {
        private const val ACTION_START = "com.weno.szuguardian.action.START"
        private const val ACTION_STOP = "com.weno.szuguardian.action.STOP"
        private const val ACTION_CHECK = "com.weno.szuguardian.action.CHECK"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "szu_guardian"

        fun createChannel(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val channel = NotificationChannel(
                CHANNEL_ID,
                "校园网守护",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "显示守护状态，点击可打开应用"
                setShowBadge(false)
            }
            context.getSystemService(NotificationManager::class.java)
                ?.createNotificationChannel(channel)
        }

        fun start(context: Context) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, GuardianService::class.java).setAction(ACTION_START),
            )
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, GuardianService::class.java))
        }

        fun checkNow(context: Context) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, GuardianService::class.java).setAction(ACTION_CHECK),
            )
        }
    }
}
