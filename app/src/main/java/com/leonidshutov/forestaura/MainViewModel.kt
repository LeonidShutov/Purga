package com.leonidshutov.forestaura

import android.content.Context
import android.media.MediaPlayer
import androidx.compose.runtime.mutableStateMapOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import timber.log.Timber

class MainViewModel : ViewModel() {
    private val _mediaPlayersMap = mutableStateMapOf<Int, ButtonData>()
    val mediaPlayersMap: Map<Int, ButtonData> get() = _mediaPlayersMap

    fun initializeMediaPlayers(context: Context, soundResources: List<Pair<Int, String>>) {
        viewModelScope.launch {
            soundResources.forEach { (rawResourceId, fileName) ->
                try {
                    val mediaPlayer = MediaPlayer.create(context, rawResourceId)
                        ?: throw IllegalStateException("Failed to create MediaPlayer for resource $rawResourceId")
                    mediaPlayer.isLooping = true // Set the MediaPlayer to loop indefinitely
                    mediaPlayer.setOnCompletionListener {
                        _mediaPlayersMap[rawResourceId]?.let { buttonData ->
                            if (buttonData.mediaPlayer.isPlaying) { // Check if the player is still playing
                                buttonData.lastPosition = 0
                                prepareAndStartMediaPlayer(buttonData, context)
                            }
                        }
                    }
                    Timber.d("Initialized MediaPlayer for: $fileName (ID: $rawResourceId)")

                    _mediaPlayersMap[rawResourceId] = ButtonData(
                        mediaPlayer = mediaPlayer,
                        context = context,
                        rawResourceId = rawResourceId,
                        fileName = fileName
                    )
                } catch (e: Exception) {
                    Timber.e("Error initializing MediaPlayer: ${e.message}")
                }
            }
        }
    }

    fun prepareAndStartMediaPlayer(buttonData: ButtonData, context: Context) {
        val mediaPlayer = buttonData.mediaPlayer
        val rawResourceId = buttonData.rawResourceId

        try {
            mediaPlayer.reset()
            context.resources.openRawResourceFd(rawResourceId).use { rawFileDescriptor ->
                mediaPlayer.setDataSource(
                    rawFileDescriptor.fileDescriptor,
                    rawFileDescriptor.startOffset,
                    rawFileDescriptor.length
                )
                mediaPlayer.prepare()
                mediaPlayer.isLooping = true // Ensure the MediaPlayer is set to loop
                if (buttonData.lastPosition > 0) {
                    mediaPlayer.seekTo(buttonData.lastPosition)
                }
                mediaPlayer.start()
                buttonData.isPlaying.value = true // Update playing state

                // Start the foreground service if it's not already running
                val activePlayers = _mediaPlayersMap.values.filter { it.isPlaying.value }
                if (activePlayers.size == 1) { // Start service only when the first sound is played
                    try {
                        startForegroundService(context, activePlayers.map { it.rawResourceId })
                    } catch (e: Exception) {
                        // Fallback: Log the error and continue without the service
                        Timber.e("Failed to start foreground service: ${e.message}")
                    }
                }
            }
        } catch (e: Exception) {
            Timber.e("Error preparing or starting MediaPlayer: ${e.message}")
        }
    }

    fun stopAllPlayers(context: Context) {
        _mediaPlayersMap.values.forEach { buttonData ->
            try {
                if (buttonData.mediaPlayer.isPlaying) {
                    buttonData.mediaPlayer.pause()
                }
                buttonData.mediaPlayer.seekTo(0)
                buttonData.lastPosition = 0
                buttonData.isPlaying.value = false // Update playing state
            } catch (e: Exception) {
                Timber.e("Error stopping MediaPlayer: ${e.message}")
            }
        }
        stopForegroundService(context)
    }
}