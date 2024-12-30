package com.leonidshutov.forestaura

import android.content.Context
import android.media.MediaPlayer
import androidx.compose.runtime.mutableStateMapOf
import androidx.lifecycle.ViewModel
import timber.log.Timber

class MainViewModel : ViewModel() {
    private val _mediaPlayersMap = mutableStateMapOf<Int, ButtonData>()
    val mediaPlayersMap: Map<Int, ButtonData> get() = _mediaPlayersMap

    private var soundPreferences: SoundPreferences? = null
    private var lastSavedPlayingSounds: Set<Int> = emptySet() // Track last saved playing sounds

    fun initialize(context: Context, soundResources: List<Pair<Int, String>>) {
        soundPreferences = SoundPreferences(context)
        initializeMediaPlayers(context, soundResources)
        restorePlayingSounds(context)
    }

    private fun initializeMediaPlayers(context: Context, soundResources: List<Pair<Int, String>>) {
        soundResources.forEach { (rawResourceId, fileName) ->
            val mediaPlayer = MediaPlayer.create(context, rawResourceId)
                ?: throw IllegalStateException("Failed to create MediaPlayer for resource $rawResourceId")
            mediaPlayer.isLooping = true
            mediaPlayer.setOnCompletionListener {
                _mediaPlayersMap[rawResourceId]?.let { buttonData ->
                    if (buttonData.mediaPlayer.isPlaying) {
                        buttonData.lastPosition = 0
                        prepareAndStartMediaPlayer(buttonData, context)
                    }
                }
            }
            _mediaPlayersMap[rawResourceId] = ButtonData(
                mediaPlayer = mediaPlayer,
                context = context,
                rawResourceId = rawResourceId,
                fileName = fileName
            )
        }
    }

    private fun restorePlayingSounds(context: Context) {
        val playingSounds = soundPreferences?.getPlayingSounds() ?: return
        Timber.d("Restoring playing sounds: $playingSounds")
        playingSounds.forEach { soundId ->
            _mediaPlayersMap[soundId]?.let { buttonData ->
                prepareAndStartMediaPlayer(buttonData, context)
            }
        }
    }

    fun stopAllPlayers(context: Context) {
        // Save the currently playing sounds before stopping them
        savePlayingSounds()

        _mediaPlayersMap.values.forEach { buttonData ->
            if (buttonData.mediaPlayer.isPlaying) {
                buttonData.mediaPlayer.pause()
                buttonData.isPlaying.value = false // Update playing state
                Timber.d("MediaPlayer paused for: ${buttonData.fileName}")
            }
            buttonData.mediaPlayer.seekTo(0)
            buttonData.lastPosition = 0
        }
        stopForegroundService(context)
    }

    fun resumePlayers(context: Context) {
        Timber.d("Resuming playing sounds: $lastSavedPlayingSounds")
        lastSavedPlayingSounds.forEach { soundId ->
            _mediaPlayersMap[soundId]?.let { buttonData ->
                prepareAndStartMediaPlayer(buttonData, context)
            }
        }
    }

    fun savePlayingSounds(saveLastSaved: Boolean = false) {
        val playingSounds = if (saveLastSaved) {
            lastSavedPlayingSounds
        } else {
            _mediaPlayersMap.values
                .filter { it.isPlaying.value }
                .map { it.rawResourceId }
                .toSet()
        }
        lastSavedPlayingSounds = playingSounds // Update last saved playing sounds
        Timber.d("Saving playing sounds: $playingSounds")
        soundPreferences?.savePlayingSounds(playingSounds)
    }

    fun prepareAndStartMediaPlayer(buttonData: ButtonData, context: Context) {
        Timber.d("Preparing and starting MediaPlayer for: ${buttonData.fileName}")
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
                mediaPlayer.isLooping = true
                if (buttonData.lastPosition > 0) {
                    mediaPlayer.seekTo(buttonData.lastPosition)
                }
                mediaPlayer.start()
                buttonData.isPlaying.value = true

                // Start the foreground service if it's not already running
                val activePlayers = _mediaPlayersMap.values.filter { it.isPlaying.value }
                if (activePlayers.size == 1) {
                    try {
                        startForegroundService(context, activePlayers.map { it.rawResourceId })
                    } catch (e: Exception) {
                        Timber.e("Failed to start foreground service: ${e.message}")
                    }
                }
            }
        } catch (e: Exception) {
            Timber.e("Error preparing or starting MediaPlayer: ${e.message}")
        }
    }
}