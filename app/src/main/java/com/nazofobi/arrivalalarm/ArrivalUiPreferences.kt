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

    fun loadFewerTransfersFirst(): Boolean =
        runCatching { prefs.getBoolean(KEY_FEWER_TRANSFERS_FIRST, false) }.getOrDefault(false)

    fun saveFewerTransfersFirst(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_FEWER_TRANSFERS_FIRST, enabled).apply()
    }

    companion object {
        const val KEY_THEME_MODE = "theme_mode"
        const val KEY_DIRECT_ONLY_ROUTES = "direct_only_routes"
        const val KEY_FEWER_TRANSFERS_FIRST = "fewer_transfers_first"
    }
}
