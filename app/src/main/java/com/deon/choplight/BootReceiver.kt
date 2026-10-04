package com.deon.choplight

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build

/** Restarts chop listening after a reboot when autostart is enabled. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        if (!Prefs(context).autostart) return
        val start = Intent(context, ChopDetectionService::class.java)
            .setAction(ChopDetectionService.ACTION_START)
        try {
            if (Build.VERSION.SDK_INT >= 26) {
                context.startForegroundService(start)
            } else {
                context.startService(start)
            }
        } catch (_: Exception) { /* device-specific boot restrictions */ }
    }
}
