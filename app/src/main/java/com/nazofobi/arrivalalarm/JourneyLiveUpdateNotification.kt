package com.nazofobi.arrivalalarm

import android.app.Notification
import android.os.Build
import android.os.Bundle

/**
 * Opts a user-armed, ongoing location notification into Android 16 Live Updates.
 *
 * There is no authoritative trip-distance percentage in JourneyUiState: straight-line
 * origin/destination distances are not journey progress on winding transit routes.
 * Show an indeterminate tracker rather than a fabricated percentage. The real distance
 * to the target continues to be displayed by ActiveJourneyService.
 *
 * This does not opt the completed-arrival notification into Live Updates.
 */
object JourneyLiveUpdateNotification {
    // Stable Android 16 promotion request extra. Using its literal avoids depending on
    // the API 36.1 compile stub; the manifest permission and platform eligibility still apply.
    internal const val PROMOTION_REQUEST_KEY = "android.requestPromotedOngoing"

    fun decorateOngoingTracking(builder: Notification.Builder): Notification.Builder {
        if (Build.VERSION.SDK_INT >= 36) {
            builder.setStyle(Notification.ProgressStyle().setProgressIndeterminate(true))
            builder.addExtras(Bundle().apply {
                putBoolean(PROMOTION_REQUEST_KEY, true)
            })
        }
        return builder
    }
}
