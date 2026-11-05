package com.nazofobi.arrivalalarm

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/**
 * Installs one persistent, low-impact refresh schedule for an already downloaded Germany GTFS index.
 *
 * UNMETERED is used rather than CONNECTED so a 272 MB feed is never fetched on a metered network
 * by default. WorkManager persists the unique periodic request across process death and reboot.
 */
object NationwideFeedRefreshScheduler {
    const val UNIQUE_WORK_NAME = "nationwide-germany-gtfs-refresh"
    const val MANUAL_WORK_NAME = "nationwide-germany-gtfs-manual-refresh"
    const val REPEAT_INTERVAL_HOURS = 24L
    const val FLEX_INTERVAL_HOURS = 6L
    const val INITIAL_DELAY_HOURS = 24L

    internal fun buildConstraints(): Constraints =
        Constraints.Builder()
            .setRequiredNetworkType(NetworkType.UNMETERED)
            .setRequiresCharging(true)
            .setRequiresBatteryNotLow(true)
            .setRequiresStorageNotLow(true)
            .build()

    internal fun createPeriodicRequest(): PeriodicWorkRequest =
        PeriodicWorkRequest.Builder(
            NationwideFeedRefreshWorker::class.java,
            REPEAT_INTERVAL_HOURS,
            TimeUnit.HOURS,
            FLEX_INTERVAL_HOURS,
            TimeUnit.HOURS,
        )
            .setConstraints(buildConstraints())
            .setInitialDelay(INITIAL_DELAY_HOURS, TimeUnit.HOURS)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.MINUTES)
            .addTag(UNIQUE_WORK_NAME)
            .build()

    /**
     * User-initiated refresh of an already installed pack. Keep Wi-Fi/unmetered and
     * device-health safeguards: a Germany-wide feed can exceed 270 MB. This request
     * never replaces the existing periodic schedule, and duplicate taps share one job.
     */
    internal fun createManualRequest(): OneTimeWorkRequest =
        OneTimeWorkRequest.Builder(NationwideFeedRefreshWorker::class.java)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.UNMETERED)
                    .setRequiresBatteryNotLow(true)
                    .setRequiresStorageNotLow(true)
                    .build()
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.MINUTES)
            .addTag(MANUAL_WORK_NAME)
            .build()

    fun requestInstalledPackRefresh(context: Context) {
        // The Settings UI only enables this action for an installed, nonempty index.
        // The worker re-checks the index before any download. Do not block the UI
        // thread with an SQLite read; a vanished pack safely yields a no-op worker.
        WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
            MANUAL_WORK_NAME,
            ExistingWorkPolicy.KEEP,
            createManualRequest(),
        )
    }

    /** Cancel only the manual one-shot request; keep the periodic refresh intact. */
    fun cancelInstalledPackRefresh(context: Context) {
        WorkManager.getInstance(context.applicationContext)
            .cancelUniqueWork(MANUAL_WORK_NAME)
    }

    /** Persist an opt-out before cancelling periodic work. Manual Wi-Fi refresh remains available. */
    fun setAutomaticUpdates(context: Context, enabled: Boolean): Boolean {
        if (!OfflinePackAutoUpdatePreference.persist(context, enabled)) return false
        if (enabled) ensureScheduled(context)
        else WorkManager.getInstance(context.applicationContext).cancelUniqueWork(UNIQUE_WORK_NAME)
        return true
    }

    fun ensureScheduled(context: Context) {
        if (!OfflinePackAutoUpdatePreference.isEnabled(context)) {
            WorkManager.getInstance(context.applicationContext).cancelUniqueWork(UNIQUE_WORK_NAME)
            return
        }
        WorkManager.getInstance(context.applicationContext)
            .enqueueUniquePeriodicWork(
                UNIQUE_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                createPeriodicRequest(),
            )
    }
}
