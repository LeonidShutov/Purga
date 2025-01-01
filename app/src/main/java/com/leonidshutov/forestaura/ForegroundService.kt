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
        private lateinit var viewModel: MainViewModel
    private val mediaPlayers = mutableMapOf<Int, MediaPlayer>()

    override fun onBind(intent: Intent?): IBinder? = null
        override fun onCreate() {
            super.onCreate()
            viewModel = MainViewModel() // Initialize the ViewModel
        }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            "STOP_ALL" -> {
                Timber.d("Stop All action received in service")
                viewModel.stopAllPlayers()
                updateNotification(isPlaying = false) // Update notification to show "Resume"
            }
            "RESUME" -> {
                Timber.d("Resume action received in service")
                viewModel.resumePlayers(applicationContext)
                updateNotification(isPlaying = true) // Update notification to show "Pause"
            }
            else -> {
                // Create a notification for the foreground service
                createNotificationChannel()
                val notification = createNotification(isPlaying = true)
                startForeground(1, notification)
                Timber.d("Foreground service started with notification")

                // Retrieve sound resource IDs from the intent
                val soundResourceIds = intent?.getIntegerArrayListExtra("soundResourceIds")
                soundResourceIds?.forEach { resourceId ->
                    val mediaPlayer = MediaPlayer.create(this, resourceId)
                    mediaPlayer?.let {
                        mediaPlayers[resourceId] = it
                        it.start()
                    }
                }
            }
        }
        return START_STICKY
    }

    private fun updateNotification(isPlaying: Boolean) {
        val notification = createNotification(isPlaying)
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(1, notification)
    }

    override fun onDestroy() {
        super.onDestroy()
        // Release all MediaPlayer resources when the service is destroyed
        mediaPlayers.values.forEach { it.release() }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            "forest_aura_channel",
            "Forest Aura",
            NotificationManager.IMPORTANCE_LOW
        )
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)
        Timber.d("Notification channel created")
    }

    private fun createNotification(isPlaying: Boolean): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)

        // Create the appropriate action based on the playback state
        val actionIntent = Intent(this, NotificationActionReceiver::class.java).apply {
            action = if (isPlaying) "STOP_ALL" else "RESUME"
        }
        val actionPendingIntent = PendingIntent.getBroadcast(this, 1, actionIntent, PendingIntent.FLAG_IMMUTABLE)
        val action = NotificationCompat.Action(
            if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play,
            if (isPlaying) "Pause" else "Resume",
            actionPendingIntent
        )

        Timber.d("Creating notification for foreground service")
        return NotificationCompat.Builder(this, "forest_aura_channel")
            .setContentTitle("Forest Aura")
            .setContentText("Playing nature sounds")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .addAction(action)
            .setOngoing(true) // Make the notification persistent
            .build()
    }
}