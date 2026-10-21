package com.bitchat.android.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootCompletedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // Ensure preferences are initialized on cold boot before reading values
        try { MeshServicePreferences.init(context.applicationContext) } catch (_: Exception) { }

        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        if (!context.getSharedPreferences("bitchat_prefs", Context.MODE_PRIVATE).getBoolean("onboarding_complete", false)) return
        if (MeshServicePreferences.isAutoStartEnabled(true)) {
            try { MeshForegroundService.start(context.applicationContext) }
            catch (e: Exception) { android.util.Log.w("BootCompletedReceiver", "Android deferred mesh startup until the app opens", e) }
        }
    }
}
