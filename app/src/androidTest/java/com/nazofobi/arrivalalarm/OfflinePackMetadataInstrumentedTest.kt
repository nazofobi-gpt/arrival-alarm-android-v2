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
            metadataNode.assertIsDisplayed().assertTextContains("etag-g185-test", substring = true)
            fun normalizeWhitespace(value: String): String = value
                .replace('\u00A0', ' ')
                .replace('\u202F', ' ')
                .replace(Regex("\\s+"), " ")
                .trim()

            fun assertContainsNormalizedTokens(expected: String) {
                normalizeWhitespace(expected)
                    .split(" ")
                    .filter { it.isNotBlank() }
                    .forEach { token -> metadataNode.assertTextContains(token, substring = true) }
            }

            val expectedSize = Formatter.formatShortFileSize(context, expectedBytes)
            assertContainsNormalizedTokens(expectedSize)
            assertContainsNormalizedTokens(expectedRelativeUpdate)
        } finally {
            files.forEach { it.delete() }
        }
    }

    @Test
    fun untrustedFeedVersionsCannotSpoofOfflinePackMetadata() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val unknown = context.getString(R.string.offline_pack_version_unknown)
        val nowMillis = 1_783_000_000_000L
        // Embedded newlines, bidi overrides, line separators and zero-width format
        // controls are not valid user-visible version identifiers.
        for (version in listOf(
            "\"release\ntrusted\"",
            "\"release\u202Etrusted\"",
            "\"release\u2028trusted\"",
            "\"release\u200Btrusted\"",
        )) {
            val text = offlinePackMetadataText(
                context,
                NationwideDataState.Ready(
                    stopCount = 10_000,
                    fetchedAtEpochSeconds = nowMillis / 1_000L - 60L,
                    sourceVersion = version,
                ),
                nowMillis = nowMillis,
            )
            assertTrue("Unsafe provider version must be unknown: $text", text.contains(unknown))
            assertTrue("Unsafe provider version must not be shown: $text", !text.contains("release"))
        }
        val safe = offlinePackMetadataText(
            context,
            NationwideDataState.Ready(
                stopCount = 10_000,
                fetchedAtEpochSeconds = nowMillis / 1_000L - 60L,
                sourceVersion = "\"etag-g185-safe\"",
            ),
            nowMillis = nowMillis,
        )
        assertTrue("Safe ETag must be preserved: $safe", safe.contains("etag-g185-safe"))
    }

    @Test
    fun malformedUnicodeVersionsFailClosedAndTruncationPreservesCodePoints() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val unknown = context.getString(R.string.offline_pack_version_unknown)
        val nowMillis = 1_783_000_000_000L

        fun metadata(version: String) = offlinePackMetadataText(
            context,
            NationwideDataState.Ready(
                stopCount = 10_000,
                fetchedAtEpochSeconds = nowMillis / 1_000L - 60L,
                sourceVersion = version,
            ),
            nowMillis = nowMillis,
        )

        for (invalidVersion in listOf("etag-\uD800", "etag-\uDC00")) {
            val rendered = metadata(invalidVersion)
            assertTrue("Incomplete UTF-16 must be unknown: $rendered", rendered.contains(unknown))
            assertTrue("Untrusted label must not appear: $rendered", !rendered.contains("etag-"))
        }

        // 26 BMP characters + a supplementary bus emoji + suffix exceed the
        // 28-code-unit limit; the ellipsis must not split the surrogate pair.
        val truncated = metadata("v".repeat(26) + "🚌" + "x")
        assertTrue("Truncation must end before the pair: $truncated", truncated.contains("v".repeat(26) + "…"))
        assertTrue("No replacement character: $truncated", !truncated.contains("\uFFFD"))
        assertTrue("No dangling surrogate: $truncated", !truncated.contains("\uD83D"))
    }

    @Test
    fun invalidFetchTimesMustNotBePresentedAsRecentPackUpdates() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val nowMillis = 1_783_000_000_000L
        val unknown = context.getString(R.string.offline_pack_updated_unknown)
        for (timestamp in listOf(0L, -1L, Long.MAX_VALUE, nowMillis / 1_000L + 3_600L)) {
            val text = offlinePackMetadataText(
                context,
                NationwideDataState.Ready(
                    stopCount = 10_000,
                    fetchedAtEpochSeconds = timestamp,
                    sourceVersion = "\"feed-1\"",
                ),
                nowMillis = nowMillis,
            )
            assertTrue("Invalid timestamp $timestamp must be shown as unknown: $text", text.contains(unknown))
            assertTrue("Feed version must remain visible", text.contains("feed-1"))
        }
    }
}
