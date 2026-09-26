package com.nazofobi.arrivalalarm

import org.junit.Assert.assertEquals
import org.junit.Test

class ConnectorRuntimeCoordinatorTest {
    private class FakeRuntime : ConnectorRuntimeControl {
        var starts = 0
        var stops = 0
        var syncs = 0

        override fun start() { starts += 1 }
        override fun stop() { stops += 1 }
        override fun syncNow(): ConnectorRuntimeStatus {
            syncs += 1
            return ConnectorRuntimeStatus(ConnectorRuntimeState.CONNECTED)
        }
    }

    @Test fun runtimeStaysAliveUntilLastOwnerReleasesIt() {
        val runtime = FakeRuntime()
        val coordinator = ConnectorRuntimeCoordinator(runtime)

        coordinator.acquire("ui")
        coordinator.acquire("service")
        coordinator.acquire("service")

        assertEquals(1, runtime.starts)
        assertEquals(2, coordinator.activeOwnerCount())

        coordinator.release("ui")
        assertEquals(0, runtime.stops)

        coordinator.release("service")
        assertEquals(1, runtime.stops)
        assertEquals(0, coordinator.activeOwnerCount())
    }

    @Test fun syncDelegatesWithoutChangingOwnership() {
        val runtime = FakeRuntime()
        val coordinator = ConnectorRuntimeCoordinator(runtime)
        coordinator.acquire("service")

        val result = coordinator.syncNow()

        assertEquals(1, runtime.syncs)
        assertEquals(ConnectorRuntimeState.CONNECTED, result.state)
        assertEquals(1, coordinator.activeOwnerCount())
    }
}
