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

    fun loadShorterWalkingFirst(): Boolean =
        runCatching { prefs.getBoolean(KEY_SHORTER_WALKING_FIRST, false) }.getOrDefault(false)

    fun saveShorterWalkingFirst(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SHORTER_WALKING_FIRST, enabled).apply()
    }

    fun loadAtMostOneTransfer(): Boolean =
        runCatching { prefs.getBoolean(KEY_AT_MOST_ONE_TRANSFER, false) }.getOrDefault(false)

    fun saveAtMostOneTransfer(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AT_MOST_ONE_TRANSFER, enabled).apply()
    }

    fun loadMaxWalking15Minutes(): Boolean =
        runCatching { prefs.getBoolean(KEY_MAX_WALKING_15_MINUTES, false) }.getOrDefault(false)

    fun saveMaxWalking15Minutes(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_MAX_WALKING_15_MINUTES, enabled).apply()
    }

    companion object {
        const val KEY_AT_MOST_ONE_TRANSFER = "at_most_one_transfer"
        const val KEY_MAX_WALKING_15_MINUTES = "max_walking_15_minutes"
        const val KEY_THEME_MODE = "theme_mode"
        const val KEY_DIRECT_ONLY_ROUTES = "direct_only_routes"
        const val KEY_FEWER_TRANSFERS_FIRST = "fewer_transfers_first"
        const val KEY_SHORTER_WALKING_FIRST = "shorter_walking_first"
    }
}
