package com.leonidshutov.forestaura

import android.content.Context
import android.media.MediaPlayer
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import timber.log.Timber

class MainViewModel : ViewModel() {
    private val _mediaPlayersMap = mutableStateMapOf<Int, ButtonData>()
    val mediaPlayersMap: Map<Int, ButtonData> get() = _mediaPlayersMap

    private var soundPreferences: SoundPreferences? = null
    private var lastSavedPlayingSounds: Set<Int> = emptySet() // Track last saved playing sounds

    private val _playInBackground = mutableStateOf(true)
    val playInBackground: State<Boolean> get() = _playInBackground

    fun initialize(context: Context, soundResources: List<Triple<Int, String, String>>) {
        Timber.d("Initializing MediaPlayers")
        soundPreferences = SoundPreferences(context)
        _playInBackground.value = soundPreferences?.getPlayInBackground() ?: true // Load preference
        initializeMediaPlayers(context, soundResources)
    }

    fun setPlayInBackground(enabled: Boolean) {
        _playInBackground.value = enabled
        soundPreferences?.setPlayInBackground(enabled)
    }

    private fun initializeMediaPlayers(context: Context, soundResources: List<Triple<Int, String, String>>) {
        soundResources.forEach { (rawResourceId, fileName, label) ->
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
                fileName = fileName, // Original file name
                label = label       // Localized label
            )
            Timber.d("Initialized MediaPlayer for: resourceId=$rawResourceId, fileName=$fileName, label=$label")
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

    fun stopAllPlayers() {
        Timber.d("stopAllPlayers called")
        savePlayingSounds()

        _mediaPlayersMap.values.forEach { buttonData ->
            Timber.d("MediaPlayer state for ${buttonData.fileName}: isPlaying=${buttonData.mediaPlayer.isPlaying}")
            if (buttonData.mediaPlayer.isPlaying) {
                Timber.d("Pausing MediaPlayer for: ${buttonData.fileName}")
                buttonData.mediaPlayer.pause()
                buttonData.isPlaying.value = false
            }
            Timber.d("Resetting MediaPlayer for: ${buttonData.fileName}")
            buttonData.mediaPlayer.seekTo(0)
            buttonData.lastPosition = 0
        }
    }

    fun resumePlayers(context: Context) {
        Timber.d("resumePlayers called")
        val playingSounds = soundPreferences?.getPlayingSounds() ?: return
        playingSounds.forEach { soundId ->
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
                buttonData.isPlaying.value = true // Update playing state
            }
        } catch (e: Exception) {
            Timber.e("Error preparing or starting MediaPlayer: ${e.message}")
        }
    }
}