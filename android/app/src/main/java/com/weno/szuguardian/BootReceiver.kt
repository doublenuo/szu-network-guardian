package com.weno.szuguardian

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.weno.szuguardian.data.ConfigStore

/** 开机 / 应用更新后按保存的设置自动恢复守护。 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            return
        }
        val config = ConfigStore(context).load()
        if (!config.autostart || !config.isUsable) return
        GuardianService.start(context)
    }
}
