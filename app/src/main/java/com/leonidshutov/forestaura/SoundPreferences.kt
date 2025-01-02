package com.leonidshutov.forestaura

import android.content.Context
import timber.log.Timber

class SoundPreferences(context: Context) {
    private val sharedPreferences = context.getSharedPreferences("sound_prefs", Context.MODE_PRIVATE)

    fun savePlayingSounds(soundIds: Set<Int>) {
        Timber.d("Saving playing sounds: $soundIds")
        sharedPreferences.edit().putStringSet("playing_sounds", soundIds.map { it.toString() }.toSet()).apply()
    }

    fun getPlayingSounds(): Set<Int> {
        val playingSounds = sharedPreferences.getStringSet("playing_sounds", emptySet())?.map { it.toInt() }?.toSet() ?: emptySet()
        Timber.d("Retrieved playing sounds: $playingSounds")
        return playingSounds
    }

    // Add new preference for playing sounds in the background
    fun setPlayInBackground(enabled: Boolean) {
        sharedPreferences.edit().putBoolean("play_in_background", enabled).apply()
    }

    fun getPlayInBackground(): Boolean {
        return sharedPreferences.getBoolean("play_in_background", true) // Default to true
    }
}