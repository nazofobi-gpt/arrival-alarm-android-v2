package com.nazofobi.arrivalalarm

import androidx.work.WorkInfo
import java.util.UUID

/**
 * Reconcile a completed manual GTFS refresh with the UI's in-memory index snapshot.
 *
 * WorkManager can re-emit the same terminal WorkInfo when the Settings screen resumes.
 * Only a new successful request should trigger a fresh index read. Failed and waiting
 * jobs must not be presented as refreshed data.
 */
internal class OfflineRefreshCompletionGate {
    private var lastHandledSuccessId: UUID? = null

    fun shouldReload(workId: UUID?, state: WorkInfo.State?): Boolean {
        if (workId == null || state != WorkInfo.State.SUCCEEDED ||
            workId == lastHandledSuccessId
        ) {
            return false
        }
        lastHandledSuccessId = workId
        return true
    }
}
