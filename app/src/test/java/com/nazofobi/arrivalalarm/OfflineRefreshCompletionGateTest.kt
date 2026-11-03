package com.nazofobi.arrivalalarm

import androidx.work.WorkInfo
import java.util.UUID
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OfflineRefreshCompletionGateTest {
    private val firstWork = UUID.fromString("00000000-0000-0000-0000-000000000185")
    private val secondWork = UUID.fromString("00000000-0000-0000-0000-000000000186")

    @Test
    fun waitingRunningAndFailedJobsDoNotReloadOrConsumeAJobId() {
        val gate = OfflineRefreshCompletionGate()
        assertFalse(gate.shouldReload(firstWork, WorkInfo.State.ENQUEUED))
        assertFalse(gate.shouldReload(firstWork, WorkInfo.State.RUNNING))
        assertFalse(gate.shouldReload(firstWork, WorkInfo.State.FAILED))
        assertFalse(gate.shouldReload(null, WorkInfo.State.SUCCEEDED))
        assertFalse(gate.shouldReload(firstWork, null))
        assertTrue(gate.shouldReload(firstWork, WorkInfo.State.SUCCEEDED))
    }

    @Test
    fun oneSuccessfulWorkReloadsExactlyOnceAcrossRepeatedCallbacks() {
        val gate = OfflineRefreshCompletionGate()
        assertTrue(gate.shouldReload(firstWork, WorkInfo.State.SUCCEEDED))
        assertFalse(gate.shouldReload(firstWork, WorkInfo.State.SUCCEEDED))
        assertFalse(gate.shouldReload(firstWork, WorkInfo.State.CANCELLED))
        assertFalse(gate.shouldReload(firstWork, WorkInfo.State.SUCCEEDED))
    }

    @Test
    fun laterSuccessfulRefreshReloadsNewSnapshot() {
        val gate = OfflineRefreshCompletionGate()
        assertTrue(gate.shouldReload(firstWork, WorkInfo.State.SUCCEEDED))
        assertFalse(gate.shouldReload(secondWork, WorkInfo.State.ENQUEUED))
        assertTrue(gate.shouldReload(secondWork, WorkInfo.State.SUCCEEDED))
        assertFalse(gate.shouldReload(secondWork, WorkInfo.State.SUCCEEDED))
    }
}
