package com.vanago.alertdot.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Settings
import com.vanago.alertdot.app
import kotlinx.coroutines.launch

class StopSoundReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        context.app.player.stop()
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) return
        val pending = goAsync()
        val app = context.app
        app.appScope.launch {
            try {
                val s = app.settingsRepo.current()
                if (s.showOverlay && s.autostart && Settings.canDrawOverlays(context)) {
                    OverlayService.start(context)
                }
            } finally {
                pending.finish()
            }
        }
    }
}
