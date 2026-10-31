package com.nazofobi.arrivalalarm

import android.content.ContextWrapper
import android.text.format.DateUtils
import android.text.format.Formatter
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OfflinePackMetadataInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun readyIndexShowsStoredSizeSourceVersionAndLastUpdate() {
        val targetContext = InstrumentationRegistry.getInstrumentation().targetContext
        val database = File(targetContext.cacheDir, "offline-pack-metadata-test.db")
        val wal = File("${database.path}-wal")
        val shm = File("${database.path}-shm")
        val files = listOf(database, wal, shm)

        try {
            files.forEach { it.delete() }
            database.parentFile?.mkdirs()
            database.writeBytes(ByteArray(4_096))
            wal.writeBytes(ByteArray(1_024))
            shm.writeBytes(ByteArray(512))

            val context = object : ContextWrapper(targetContext) {
                override fun getDatabasePath(name: String): File = database
            }
            val expectedBytes = 5_632L
            assertEquals(expectedBytes, offlineIndexStorageBytes(context))

            val nowMillis = System.currentTimeMillis()
            val fetchedAtEpochSeconds = nowMillis / 1_000L - 17L * 60L
            val expectedRelativeUpdate = DateUtils.getRelativeTimeSpanString(
                fetchedAtEpochSeconds * 1_000L,
                nowMillis,
                DateUtils.MINUTE_IN_MILLIS,
            ).toString()
            val state = SettingsReadinessState(
                themeMode = ArrivalThemeMode.SYSTEM,
                locationPermission = true,
                notificationPermission = true,
                locationServicesAvailable = true,
                bluetoothPermission = true,
                guidancePermissionState = GuidancePermissionState.GRANTED,
                audioRoute = GuidanceAudioRoute.DEVICE,
                dataState = NationwideDataState.Ready(
                    stopCount = 12,
                    fetchedAtEpochSeconds = fetchedAtEpochSeconds,
                    sourceVersion = "\"etag-g185-test\"",
                ),
                backgroundJourneyActive = false,
                connectorConfigured = false,
                connectorConnected = false,
                searchHealth = TransitFunctionHealth.WORKING,
                routingHealth = TransitFunctionHealth.WORKING,
            )

            composeRule.setContent {
                CompositionLocalProvider(LocalContext provides context) {
                    MaterialTheme {
                        SettingsReadinessPanel(
                            state = state,
                            connectorBusy = false,
                            connectorStatus = "",
                            onThemeModeChange = {},
                            onRequestJourneyPermissions = {},
                            onRequestGuidancePermissions = {},
                            onRefreshReadiness = {},
                            onShareDiagnostics = {},
                            onOpenAppSettings = {},
                            onRetryData = {},
                            onConnectorAction = {},
                        )
                    }
                }
            }

            val metadataNode = composeRule.onNodeWithTag("offline-pack-metadata")
            metadataNode.assertIsDisplayed().assertTextContains("etag-g185-test")
            val expectedSize = Formatter.formatShortFileSize(context, expectedBytes)
            metadataNode.assertTextContains(expectedSize, substring = true)
            metadataNode.assertTextContains(expectedRelativeUpdate, substring = true)
        } finally {
            files.forEach { it.delete() }
        }
    }
}
