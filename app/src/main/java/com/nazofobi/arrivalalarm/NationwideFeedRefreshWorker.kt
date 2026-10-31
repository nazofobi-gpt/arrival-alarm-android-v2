package com.nazofobi.arrivalalarm

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.work.ForegroundInfo
import androidx.work.Worker
import androidx.work.WorkerParameters
import java.io.IOException
import java.util.concurrent.CancellationException
import java.util.concurrent.ExecutionException

/**
 * Refreshes only an existing known-good local index. Initial full-feed acquisition remains a
 * foreground/user-driven app operation; this periodic worker never initiates the first 272 MB pull.
 */
class NationwideFeedRefreshWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : Worker(appContext, workerParams) {

    override fun doWork(): Result {
        var index: NationwideTransitIndex? = null
        return try {
            index = NationwideTransitIndex(applicationContext)
            if (!index.isReady()) {
                // No existing offline pack: do not silently download the full feed in the background.
                return Result.success()
            }

            // A full Germany feed can take longer than WorkManager's ordinary ten-minute window.
            // Promote the work to a typed dataSync foreground service before the conditional request.
            setForegroundAsync(foregroundInfo(applicationContext, null)).get()

            index.importFeed(GtfsDeutschlandLoader.FEED_URL) { importedStopCount ->
                if (isStopped || Thread.currentThread().isInterrupted) {
                    throw CancellationException("Offline feed refresh was stopped")
                }
                // Stop counts are emitted by the importer at bounded intervals; this keeps the
                // notification useful without inventing a percentage or total row count.
                setForegroundAsync(foregroundInfo(applicationContext, importedStopCount))
            }
            Result.success()
        } catch (cancelled: CancellationException) {
            Result.retry()
        } catch (interrupted: InterruptedException) {
            Thread.currentThread().interrupt()
            Result.retry()
        } catch (foregroundFailure: ExecutionException) {
            Result.retry()
        } catch (networkFailure: IOException) {
            Result.retry()
        } catch (importFailure: RuntimeException) {
            // NationwideTransitIndex imports inside one SQLite transaction; failed refreshes retain
            // the previous known-good schedule graph and are retried with WorkManager backoff.
            Result.retry()
        } finally {
            runCatching { index?.close() }
        }
    }

    private fun foregroundInfo(context: Context, importedStopCount: Int?): ForegroundInfo {
        ensureNotificationChannel(context)
        val contentText = if (importedStopCount == null) {
            context.getString(R.string.offline_feed_refresh_notification_checking)
        } else {
            context.getString(R.string.offline_feed_refresh_notification_progress, importedStopCount)
        }
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(context, CHANNEL_ID)
        } else {
            Notification.Builder(context)
        }
        val notification = builder
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(context.getString(R.string.offline_feed_refresh_notification_title))
            .setContentText(contentText)
            .setCategory(Notification.CATEGORY_PROGRESS)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(0, 0, true)
            .build()

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }

    private fun ensureNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return

        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.offline_feed_refresh_notification_channel),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            setShowBadge(false)
            enableVibration(false)
            setSound(null, null)
        }
        manager.createNotificationChannel(channel)
    }

    private companion object {
        const val CHANNEL_ID = "offline_feed_refresh"
        const val NOTIFICATION_ID = 185
    }
}
