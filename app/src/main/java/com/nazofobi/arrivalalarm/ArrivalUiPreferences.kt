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

    fun loadDirectOnlyRoutes(): Boolean = prefs.getBoolean(KEY_DIRECT_ONLY_ROUTES, false)

    fun saveDirectOnlyRoutes(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_DIRECT_ONLY_ROUTES, enabled).apply()
    }

    companion object {
        const val KEY_THEME_MODE = "theme_mode"
        const val KEY_DIRECT_ONLY_ROUTES = "direct_only_routes"
    }
}
