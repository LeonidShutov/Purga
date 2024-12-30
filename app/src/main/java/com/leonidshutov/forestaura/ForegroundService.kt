package com.leonidshutov.forestaura

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.os.IBinder
import androidx.core.app.NotificationCompat
import timber.log.Timber

class ForegroundService : Service() {

    private val mediaPlayers = mutableMapOf<Int, MediaPlayer>()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Create a notification for the foreground service
        createNotificationChannel()
        val notification = createNotification()
        startForeground(1, notification)

        // Retrieve sound resource IDs from the intent
        val soundResourceIds = intent?.getIntegerArrayListExtra("soundResourceIds")
        soundResourceIds?.forEach { resourceId ->
            val mediaPlayer = MediaPlayer.create(this, resourceId)
            mediaPlayer?.let {
                mediaPlayers[resourceId] = it
                it.start()
            }
        }

        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        // Release all MediaPlayer resources when the service is destroyed
        mediaPlayers.values.forEach { mediaPlayer ->
            try {
                if (mediaPlayer.isPlaying) {
                    mediaPlayer.stop()
                }
                mediaPlayer.release()
            } catch (e: Exception) {
                Timber.e("Error releasing MediaPlayer: ${e.message}")
            }
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            "forest_aura_channel",
            "Forest Aura",
            NotificationManager.IMPORTANCE_LOW
        )
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)
    }

    private fun createNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)

        return NotificationCompat.Builder(this, "forest_aura_channel")
            .setContentTitle("Forest Aura")
            .setContentText("Playing nature sounds")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .build()
    }
}