package com.nazofobi.arrivalalarm

import android.content.Intent
import android.provider.CalendarContract

/**
 * Create a Calendar insert draft for the evidenced access-walking interval.
 * The user must confirm in Calendar; this never saves an event or sets a reminder.
 */
internal fun plannedWalkCalendarDraftIntent(
    option: RouteOption,
    nowMillis: Long,
    title: String,
    description: String,
    safetyBufferMinutes: Int = 0,
): Intent? {
    val start = plannedWalkStartEpochMillis(option, safetyBufferMinutes) ?: return null
    val walkSeconds = option.accessWalking?.walkSeconds ?: return null
    val end = runCatching {
        Math.addExact(
            start,
            Math.multiplyExact(walkSeconds.toLong() + safetyBufferMinutes.toLong() * 60L, 1_000L),
        )
    }.getOrNull() ?: return null
    if (start <= nowMillis || end <= start) return null

    return Intent(Intent.ACTION_INSERT)
        .setData(CalendarContract.Events.CONTENT_URI)
        .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, start)
        .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, end)
        .putExtra(CalendarContract.Events.TITLE, title)
        .putExtra(CalendarContract.Events.DESCRIPTION, description)
}
