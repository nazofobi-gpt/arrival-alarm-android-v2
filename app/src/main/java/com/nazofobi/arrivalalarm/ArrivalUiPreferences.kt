package com.nazofobi.arrivalalarm

import android.content.Context

class ArrivalUiPreferences(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences("app_preferences", Context.MODE_PRIVATE)

    fun loadThemeMode(): ArrivalThemeMode =
        runCatching {
            ArrivalThemeMode.valueOf(
                prefs.getString(KEY_THEME_MODE, ArrivalThemeMode.SYSTEM.name)
                    ?: ArrivalThemeMode.SYSTEM.name
            )
        }.getOrDefault(ArrivalThemeMode.SYSTEM)

    fun saveThemeMode(mode: ArrivalThemeMode) {
        prefs.edit().putString(KEY_THEME_MODE, mode.name).apply()
    }

    companion object {
        const val KEY_THEME_MODE = "theme_mode"
    }
}
