package com.nazofobi.arrivalalarm

import android.content.Context
import android.content.ContextWrapper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.ListenableWorker
import androidx.work.NetworkType
import androidx.work.testing.TestWorkerBuilder
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NationwideFeedRefreshWorkerInstrumentedTest {

    @Test
    fun periodicRefreshRequiresUnmeteredNetworkChargingAndHealthyStorage() {
        val constraints = NationwideFeedRefreshScheduler.buildConstraints()

        assertEquals(NetworkType.UNMETERED, constraints.requiredNetworkType)
        assertTrue(constraints.requiresCharging())
        assertTrue(constraints.requiresBatteryNotLow())
        assertTrue(constraints.requiresStorageNotLow())
        assertEquals(24L, NationwideFeedRefreshScheduler.REPEAT_INTERVAL_HOURS)
        assertEquals(6L, NationwideFeedRefreshScheduler.FLEX_INTERVAL_HOURS)
        assertEquals(24L, NationwideFeedRefreshScheduler.INITIAL_DELAY_HOURS)
    }

    @Test
    fun workerSkipsWithoutExistingOfflineIndex() {
        val base = ApplicationProvider.getApplicationContext<Context>()
        val directory = File(base.cacheDir, "g185-refresh-worker-test")
        directory.deleteRecursively()
        assertTrue(directory.mkdirs() || directory.isDirectory)
        val isolatedContext = IsolatedDatabaseContext(base, directory)

        try {
            val worker = TestWorkerBuilder<NationwideFeedRefreshWorker>(isolatedContext).build()
            assertEquals(ListenableWorker.Result.success(), worker.doWork())
        } finally {
            directory.deleteRecursively()
        }
    }

    private class IsolatedDatabaseContext(
        base: Context,
        private val directory: File,
    ) : ContextWrapper(base) {
        override fun getApplicationContext(): Context = this

        override fun getDatabasePath(name: String): File = File(directory, name)
    }
}
