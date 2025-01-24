package com.leonidshutov.purga

import android.content.Context

class ThemeManager(context: Context) {
    private val sharedPreferences = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)

    fun getSelectedTheme(): String {
        return sharedPreferences.getString("theme", "system") ?: "system"
    }

    fun setSelectedTheme(theme: String) {
        sharedPreferences.edit().putString("theme", theme).apply()
    }
}