package com.nazofobi.arrivalalarm

import android.content.Context

/**
 * Local-only control for background GTFS refresh. Opting out does not delete
 * the installed transit graph or prevent an explicitly requested Wi-Fi check.
 */
internal object OfflinePackAutoUpdatePreference {
    private const val PREFERENCES = "offline_pack_controls"
    private const val AUTO_UPDATE_KEY = "automatic_updates_enabled"

    fun isEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .getBoolean(AUTO_UPDATE_KEY, true)

    /** Synchronous commit: workers must see opt-out before scheduling changes. */
    fun persist(context: Context, enabled: Boolean): Boolean =
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .edit().putBoolean(AUTO_UPDATE_KEY, enabled).commit()
}

/** Pure gate for both startup and in-flight refresh decisions. */
internal fun shouldRunPackRefresh(autoEnabled: Boolean, manualRequest: Boolean): Boolean =
    autoEnabled || manualRequest
