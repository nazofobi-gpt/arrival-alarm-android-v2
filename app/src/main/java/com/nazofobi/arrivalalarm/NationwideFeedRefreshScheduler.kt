package com.nazofobi.arrivalalarm

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
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

    fun ensureScheduled(context: Context) {
        WorkManager.getInstance(context.applicationContext)
            .enqueueUniquePeriodicWork(
                UNIQUE_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                createPeriodicRequest(),
            )
    }
}
