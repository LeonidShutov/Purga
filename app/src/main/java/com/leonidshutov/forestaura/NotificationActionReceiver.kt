package com.leonidshutov.forestaura

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import timber.log.Timber

class NotificationActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        when (intent?.action) {
            "STOP_ALL" -> {
                Timber.d("Stop All action received")
                val stopIntent = Intent(context, ForegroundService::class.java).apply {
                    action = "STOP_ALL"
                }
                context.startService(stopIntent)
            }
            "RESUME" -> {
                Timber.d("Resume action received")
                val resumeIntent = Intent(context, ForegroundService::class.java).apply {
                    action = "RESUME"
                }
                context.startService(resumeIntent)
            }
        }
    }
}