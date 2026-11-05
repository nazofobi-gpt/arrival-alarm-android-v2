package com.nazofobi.arrivalalarm

import android.content.Intent
import android.provider.CalendarContract
import android.provider.Settings
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PlannedWalkStartInstrumentedTest {
    @get:Rule val rule = createComposeRule()

    private fun show(
        planned: String?, walking: Int?,
        nowMillisForTest: Long? = null,
        stopLatitude: Double? = null,
        stopLongitude: Double? = null,
        directionsLauncher: ((Intent) -> Boolean)? = null,
        bufferMinutesForTest: Int? = 0,
        walkingStopId: String = "stop-1",
        notificationPermissionReadyForTest: Boolean? = true,
        notificationChannelMutedForTest: Boolean? = null,
        notificationSettingsLauncher: ((Intent) -> Boolean)? = null,
        notificationPreviewSender: (() -> Boolean)? = null,
        calendarLauncher: ((Intent) -> Boolean)? = null,
    ) {
        rule.setContent {
            MaterialTheme {
                // Match the real RouteJourneyPanel vertical layout; otherwise sibling
                // controls overlap at the origin and Compose clicks hit the wrong node.
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    PlannedWalkStartNotice(
                    RouteOption(
                        id = "gtfs-1",
                        origin = MapPoint(52.67, 8.24, "Lohne"),
                        destination = MapPoint(53.08, 8.81, "Bremen Hbf"),
                        line = "RE", direction = "Bremen",
                        departure = "10:30", arrival = "11:30", walkingMinutes = 10,
                        transfers = 0,
                        stops = listOf(RouteStop(id = "stop-1", name = "Lohne",
                            latitude = stopLatitude, longitude = stopLongitude,
                            plannedDeparture = planned)),
                        accessWalking = walking?.let {
                            RouteWalkingLegEvidence(walkingStopId, "Lohne", it)
                        },
                    ),
                    calendarLauncher = calendarLauncher,
                    nowMillisForTest = nowMillisForTest,
                    directionsLauncher = directionsLauncher,
                    bufferMinutesForTest = bufferMinutesForTest,
                    notificationPermissionReadyForTest = notificationPermissionReadyForTest,
                    notificationChannelMutedForTest = notificationChannelMutedForTest,
                    notificationSettingsLauncher = notificationSettingsLauncher,
                    notificationPreviewSender = notificationPreviewSender,
                    )
                }
            }
        }
    }

    /** Within the seven-day scheduling window, using an explicit source UTC offset. */
    private fun nearFutureDeparture(): String = java.time.Instant.now()
        .plusSeconds(7_200L)
        .atOffset(java.time.ZoneOffset.ofHours(1))
        .withNano(0)
        .toString()

    @Test fun overnightWalkStartShowsTheCalendarDateNotOnlyTheClock() {
        // First boarding stop leaves after midnight in the source's +01:00 offset;
        // the suggested walk begins on the preceding local calendar day.
        show("2099-11-05T00:10:00+01:00", 600)
        val expected = java.text.DateFormat.getDateTimeInstance(
            java.text.DateFormat.MEDIUM, java.text.DateFormat.SHORT,
        ).format(java.util.Date(java.time.Instant.parse("2099-11-04T23:00:00Z").toEpochMilli()))
        rule.onNodeWithTag("route-planned-walk-start")
            .assertTextContains(expected, substring = true)
    }

    @Test fun upcomingWalkShowsRoundedMinutesButNotLateWarning() {
        val start = java.time.Instant.parse("2099-11-04T09:20:00Z").toEpochMilli()
        show("2099-11-04T10:30:00+01:00", 600, nowMillisForTest = start - 1L)
        rule.onNodeWithTag("route-walk-minutes-remaining")
            .assertTextContains("1", substring = true)
        rule.onNodeWithTag("route-walk-start-passed").assertDoesNotExist()
    }

    @Test fun elapsedWalkDoesNotShowCountdown() {
        val start = java.time.Instant.parse("2099-11-04T09:20:00Z").toEpochMilli()
        show("2099-11-04T10:30:00+01:00", 600, nowMillisForTest = start)
        rule.onNodeWithTag("route-walk-minutes-remaining").assertDoesNotExist()
    }

    @Test fun missingEvidenceDoesNotShowCountdown() {
        val start = java.time.Instant.parse("2099-11-04T09:20:00Z").toEpochMilli()
        show(null, 600, nowMillisForTest = start - 60_000L)
        rule.onNodeWithTag("route-walk-minutes-remaining").assertDoesNotExist()
    }

    @Test fun plannedEvidenceShowsWalkingStartWithSourceDisclosure() {
        show("2026-11-04T10:30:00+01:00", 600)
        rule.onNodeWithTag("route-planned-walk-start").assertExists()
        rule.onNodeWithTag("route-planned-walk-start-note").assertExists()
    }

    @Test fun selectingBufferMovesCalendarDraftButKeepsScheduledDeparture() {
        var observed: Intent? = null
        show(
            "2099-11-04T10:30:00+01:00", 600,
            bufferMinutesForTest = 0,
            calendarLauncher = { observed = it; true },
        )
        rule.onNodeWithTag("route-walk-safety-buffer").performScrollTo().performClick()
        rule.onNodeWithTag("route-planned-walk-calendar").performScrollTo().performClick()
        rule.runOnIdle {
            val draft = requireNotNull(observed)
            assertEquals(
                java.time.Instant.parse("2099-11-04T09:15:00Z").toEpochMilli(),
                draft.getLongExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, -1L),
            )
            assertEquals(
                java.time.Instant.parse("2099-11-04T09:30:00Z").toEpochMilli(),
                draft.getLongExtra(CalendarContract.EXTRA_EVENT_END_TIME, -1L),
            )
        }
    }

    @Test fun chosenBufferIsPersistedWithoutAutoCreatingCalendarEvent() {
        val preferences = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
            .targetContext.getSharedPreferences(
                "route_walk_preferences", android.content.Context.MODE_PRIVATE)
        preferences.edit().remove("safety_buffer_minutes").commit()
        try {
            show("2099-11-04T10:30:00+01:00", 600, bufferMinutesForTest = null)
            rule.onNodeWithTag("route-walk-safety-buffer").performScrollTo().performClick()
            rule.runOnIdle { assertEquals(5, preferences.getInt("safety_buffer_minutes", -1)) }
        } finally {
            preferences.edit().remove("safety_buffer_minutes").commit()
        }
    }

    @Test fun otherBoardingStopWalkHidesTimeAndCalendarAction() {
        show("2099-11-04T10:30:00+01:00", 600, walkingStopId = "other-stop")
        rule.onNodeWithTag("route-planned-walk-start").assertDoesNotExist()
        rule.onNodeWithTag("route-planned-walk-calendar").assertDoesNotExist()
        rule.onNodeWithTag("route-walk-directions").assertDoesNotExist()
    }

    @Test fun missingPlannedDepartureDoesNotInventWalkingStart() {
        show(null, 600)
        rule.onNodeWithTag("route-planned-walk-start").assertDoesNotExist()
    }

    @Test fun missingWalkingEvidenceDoesNotInventWalkingStart() {
        show("2026-11-04T10:30:00+01:00", null)
        rule.onNodeWithTag("route-planned-walk-start").assertDoesNotExist()
    }
    @Test fun futureWalkingWindowProducesOnlyCalendarInsertDraft() {
        var observed: Intent? = null
        show("2099-11-04T10:30:00+01:00", 600) { intent ->
            observed = intent
            true
        }
        rule.onNodeWithTag("route-planned-walk-calendar").performScrollTo().performClick()
        rule.runOnIdle {
            val intent = requireNotNull(observed)
            assertEquals(Intent.ACTION_INSERT, intent.action)
            assertEquals(CalendarContract.Events.CONTENT_URI, intent.data)
            assertEquals(
                java.time.Instant.parse("2099-11-04T09:20:00Z").toEpochMilli(),
                intent.getLongExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, -1L),
            )
            assertEquals(
                java.time.Instant.parse("2099-11-04T09:30:00Z").toEpochMilli(),
                intent.getLongExtra(CalendarContract.EXTRA_EVENT_END_TIME, -1L),
            )
            assertTrue(intent.getStringExtra(CalendarContract.Events.TITLE)?.isNotBlank() == true)
            assertTrue(intent.getStringExtra(CalendarContract.Events.DESCRIPTION)?.isNotBlank() == true)
        }
    }

    @Test fun missingEvidenceNeverOffersCalendarDraft() {
        show(null, 600)
        rule.onNodeWithTag("route-planned-walk-calendar").assertDoesNotExist()
    }

    @Test fun pastWalkingStartNeverOffersCalendarDraft() {
        show("2020-11-04T10:30:00+01:00", 600)
        rule.onNodeWithTag("route-planned-walk-calendar").assertDoesNotExist()
    }

    @Test fun calendarFailureVisibleWithoutClaimingSavedEvent() {
        show("2099-11-04T10:30:00+01:00", 600) { false }
        rule.onNodeWithTag("route-planned-walk-calendar").performScrollTo().performClick()
        rule.onNodeWithTag("route-planned-walk-calendar-failure").assertExists()
    }
    @Test fun showsWarningAfterPlannedWalkStart() {
        show("2099-11-04T10:30:00+01:00", 600,
            nowMillisForTest = java.time.Instant.parse("2099-11-04T09:20:00Z").toEpochMilli())
        rule.onNodeWithTag("route-walk-start-passed").assertExists()
        rule.onNodeWithTag("route-walk-departure-passed").assertDoesNotExist()
    }
    @Test fun showsDifferentWarningAfterPlannedDeparture() {
        show("2099-11-04T10:30:00+01:00", 600,
            nowMillisForTest = java.time.Instant.parse("2099-11-04T09:30:00Z").toEpochMilli())
        rule.onNodeWithTag("route-walk-departure-passed").assertExists()
        rule.onNodeWithTag("route-walk-start-passed").assertDoesNotExist()
    }
    @Test fun noWarningBeforeStartOrWithoutEvidence() {
        show("2099-11-04T10:30:00+01:00", 600,
            nowMillisForTest = java.time.Instant.parse("2099-11-04T09:19:59Z").toEpochMilli())
        rule.onNodeWithTag("route-walk-start-passed").assertDoesNotExist()
        rule.onNodeWithTag("route-walk-departure-passed").assertDoesNotExist()
    }
    @Test fun validStopOpensExternalDirectionsOnlyAfterUserTap() {
        var observed: Intent? = null
        show("2099-11-04T10:30:00+01:00", 600,
            stopLatitude = 52.67, stopLongitude = 8.24,
            directionsLauncher = { observed = it; true })
        rule.onNodeWithTag("route-walk-directions").assertExists()
        rule.runOnIdle { assertEquals(null, observed) }
        rule.onNodeWithTag("route-walk-directions").performScrollTo().performClick()
        rule.runOnIdle {
            assertEquals(Intent.ACTION_VIEW, requireNotNull(observed).action)
            assertEquals(
                "https://www.google.com/maps/dir/?api=1&destination=52.67%2C8.24&travelmode=walking&dir_action=navigate",
                requireNotNull(observed).data.toString(),
            )
        }
    }
    @Test fun noStopCoordinateEvidenceHidesDirections() {
        show("2099-11-04T10:30:00+01:00", 600)
        rule.onNodeWithTag("route-walk-directions").assertDoesNotExist()
    }
    @Test fun failedExternalLaunchIsVisible() {
        show("2099-11-04T10:30:00+01:00", 600, stopLatitude = 52.67,
            stopLongitude = 8.24, directionsLauncher = { false })
        rule.onNodeWithTag("route-walk-directions").performScrollTo().performClick()
        rule.onNodeWithTag("route-walk-directions-failure").assertExists()
    }
    @Test fun departedTripHidesDirections() {
        show("2099-11-04T10:30:00+01:00", 600,
            nowMillisForTest = java.time.Instant.parse("2099-11-04T09:30:00Z").toEpochMilli(),
            stopLatitude = 52.67, stopLongitude = 8.24)
        rule.onNodeWithTag("route-walk-directions").assertDoesNotExist()
    }


    @Test fun explicitReminderChoiceAndBestEffortDisclosureAppearOnlyWithEvidence() {
        val start = java.time.Instant.parse("2099-11-04T09:20:00Z").toEpochMilli()
        show("2099-11-04T10:30:00+01:00", 600, nowMillisForTest = start - 600_000L)
        rule.onNodeWithTag("route-walk-reminder-toggle").assertExists()
        rule.onNodeWithTag("route-walk-reminder-note").assertExists()
    }
 
    @Test fun pendingOtherRouteReminderCanBeCancelledWithoutCurrentGtfsEvidence() {
        val preferences = androidx.test.platform.app.InstrumentationRegistry
            .getInstrumentation().targetContext.getSharedPreferences(
                PlannedWalkReminderScheduler.PREFS, android.content.Context.MODE_PRIVATE)
        preferences.edit().putString(PlannedWalkReminderScheduler.KEY_TOKEN, "other-route-fixture")
            .putString(PlannedWalkReminderScheduler.KEY_ROUTE, "previous-route").commit()
        try {
            show(null, 600)
            rule.onNodeWithTag("route-walk-reminder-other-route").assertExists()
            rule.onNodeWithTag("route-walk-reminder-toggle").assertTextContains(
                androidx.test.platform.app.InstrumentationRegistry
                    .getInstrumentation().targetContext.getString(
                        R.string.route_walk_reminder_cancel), substring = true)
        } finally {
            preferences.edit().clear().commit()
        }
    }

    @Test fun plannedNextRouteDoesNotSilentlyReplacePreviousReminder() {
        val preferences = androidx.test.platform.app.InstrumentationRegistry
            .getInstrumentation().targetContext.getSharedPreferences(
                PlannedWalkReminderScheduler.PREFS, android.content.Context.MODE_PRIVATE)
        preferences.edit().putString(PlannedWalkReminderScheduler.KEY_TOKEN, "other-route-fixture")
            .putString(PlannedWalkReminderScheduler.KEY_ROUTE, "previous-route").commit()
        try {
            show("2099-11-04T10:30:00+01:00", 600)
            rule.onNodeWithTag("route-walk-reminder-other-route").assertExists()
            rule.onNodeWithTag("route-walk-reminder-toggle").assertTextContains(
                androidx.test.platform.app.InstrumentationRegistry
                    .getInstrumentation().targetContext.getString(
                        R.string.route_walk_reminder_cancel), substring = true)
        } finally {
            preferences.edit().clear().commit()
        }
    }
    @Test fun queuedWalkReminderCanBeCancelledWithNoRouteResults() {
        val context = androidx.test.platform.app.InstrumentationRegistry
            .getInstrumentation().targetContext
        val preferences = context.getSharedPreferences(
            PlannedWalkReminderScheduler.PREFS, android.content.Context.MODE_PRIVATE)
        preferences.edit()
            .putString(PlannedWalkReminderScheduler.KEY_TOKEN, "no-routes-test-token")
            .putString(PlannedWalkReminderScheduler.KEY_ROUTE, "disappeared-route")
            .putLong(PlannedWalkReminderScheduler.KEY_START, 4_000_000_000_000L)
            .commit()
        try {
            rule.setContent {
                MaterialTheme {
                    RouteJourneyPanel(
                        origin = null, destination = null, routeOptions = emptyList(),
                        routeBusy = false, routeStatus = null, routeOffline = true,
                        selectedRouteId = null, journey = JourneyUiState(),
                        onRefreshRoutes = {}, onSelectRoute = {},
                        onArmAlarm = {}, onCancelAlarm = {},
                    )
                }
            }
            rule.onNodeWithTag("route-results-empty").assertExists()
            rule.onNodeWithTag("route-walk-reminder-global-cancel").assertExists()
                .performClick()
            rule.waitUntil(10_000L) {
                preferences.getString(PlannedWalkReminderScheduler.KEY_TOKEN, null) == null
            }
            rule.onNodeWithTag("route-walk-reminder-global-card").assertDoesNotExist()
        } finally {
            preferences.edit()
                .remove(PlannedWalkReminderScheduler.KEY_TOKEN)
                .remove(PlannedWalkReminderScheduler.KEY_ROUTE)
                .remove(PlannedWalkReminderScheduler.KEY_START).commit()
        }
    }


    @Test fun queuedReminderShowsSavedStopAndTimeEvenWithoutRouteResults() {
        val context = androidx.test.platform.app.InstrumentationRegistry
            .getInstrumentation().targetContext
        val preferences = context.getSharedPreferences(
            PlannedWalkReminderScheduler.PREFS, android.content.Context.MODE_PRIVATE)
        preferences.edit().clear()
            .putString(PlannedWalkReminderScheduler.KEY_TOKEN, "saved-details-token")
            .putString(PlannedWalkReminderScheduler.KEY_DETAILS_TOKEN, "saved-details-token")
            .putString(PlannedWalkReminderScheduler.KEY_ROUTE, "previous-gtfs-trip")
            .putString(PlannedWalkReminderScheduler.KEY_STOP, "Lohne")
            .putLong(PlannedWalkReminderScheduler.KEY_START,
                java.time.Instant.parse("2099-11-04T09:20:00Z").toEpochMilli())
            .commit()
        try {
            rule.setContent {
                MaterialTheme {
                    RouteJourneyPanel(
                        origin = null, destination = null, routeOptions = emptyList(),
                        routeBusy = false, routeStatus = null, routeOffline = true,
                        selectedRouteId = null, journey = JourneyUiState(),
                        onRefreshRoutes = {}, onSelectRoute = {},
                        onArmAlarm = {}, onCancelAlarm = {},
                    )
                }
            }
            rule.onNodeWithTag("route-walk-reminder-global-cancel").assertExists()
            rule.onNodeWithTag("route-walk-reminder-details")
                .assertTextContains("Lohne", substring = true)
        } finally {
            preferences.edit().clear().commit()
        }
    }

    @Test fun staleOrLegacyDetailsAreNotAttributedToAnotherReminder() {
        val context = androidx.test.platform.app.InstrumentationRegistry
            .getInstrumentation().targetContext
        val preferences = context.getSharedPreferences(
            PlannedWalkReminderScheduler.PREFS, android.content.Context.MODE_PRIVATE)
        preferences.edit().clear()
            .putString(PlannedWalkReminderScheduler.KEY_TOKEN, "new-reminder-token")
            .putString(PlannedWalkReminderScheduler.KEY_DETAILS_TOKEN, "previous-token")
            .putString(PlannedWalkReminderScheduler.KEY_ROUTE, "new-gtfs-trip")
            .putString(PlannedWalkReminderScheduler.KEY_STOP, "Old Stop")
            .putLong(PlannedWalkReminderScheduler.KEY_START,
                java.time.Instant.parse("2099-11-04T09:20:00Z").toEpochMilli())
            .commit()
        try {
            rule.setContent {
                MaterialTheme {
                    RouteJourneyPanel(
                        origin = null, destination = null, routeOptions = emptyList(),
                        routeBusy = false, routeStatus = null, routeOffline = true,
                        selectedRouteId = null, journey = JourneyUiState(),
                        onRefreshRoutes = {}, onSelectRoute = {},
                        onArmAlarm = {}, onCancelAlarm = {},
                    )
                }
            }
            rule.onNodeWithTag("route-walk-reminder-global-cancel").assertExists()
            rule.onNodeWithTag("route-walk-reminder-details").assertDoesNotExist()
        } finally {
            preferences.edit().clear().commit()
        }
    }
    @Test fun absentWalkReminderNeverInventsGlobalCancelAction() {
        val context = androidx.test.platform.app.InstrumentationRegistry
            .getInstrumentation().targetContext
        val preferences = context.getSharedPreferences(
            PlannedWalkReminderScheduler.PREFS, android.content.Context.MODE_PRIVATE)
        preferences.edit().remove(PlannedWalkReminderScheduler.KEY_TOKEN)
            .remove(PlannedWalkReminderScheduler.KEY_ROUTE)
            .remove(PlannedWalkReminderScheduler.KEY_START).commit()
        rule.setContent {
            MaterialTheme {
                RouteJourneyPanel(
                    origin = null, destination = null, routeOptions = emptyList(),
                    routeBusy = false, routeStatus = null, routeOffline = true,
                    selectedRouteId = null, journey = JourneyUiState(),
                    onRefreshRoutes = {}, onSelectRoute = {},
                    onArmAlarm = {}, onCancelAlarm = {},
                )
            }
        }
        rule.onNodeWithTag("route-walk-reminder-global-card").assertDoesNotExist()
    }

    @Test fun disabledNotificationsOfferSettingsInsteadOfFalseSchedule() {
        var observed: Intent? = null
        show(nearFutureDeparture(), 600,
            notificationPermissionReadyForTest = false,
            notificationSettingsLauncher = { observed = it; true })
        rule.onNodeWithTag("route-walk-reminder-notifications-disabled").assertExists()
        rule.onNodeWithTag("route-walk-reminder-toggle").assertIsNotEnabled()
        rule.onNodeWithTag("route-walk-reminder-settings").performScrollTo().performClick()
        rule.runOnIdle {
            val intent = requireNotNull(observed)
            assertEquals(Settings.ACTION_APP_NOTIFICATION_SETTINGS, intent.action)
            assertEquals(
                androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
                    .targetContext.packageName,
                intent.getStringExtra(Settings.EXTRA_APP_PACKAGE),
            )
        }
    }

    @Test fun enabledNotificationsKeepOptInAndHideSettingsRecovery() {
        show(nearFutureDeparture(), 600,
            notificationPermissionReadyForTest = true)
        rule.onNodeWithTag("route-walk-reminder-toggle").assertIsEnabled()
        rule.onNodeWithTag("route-walk-reminder-settings").assertDoesNotExist()
    }

    @Test fun unavailableNotificationSettingsHandlerDoesNotSchedule() {
        show(nearFutureDeparture(), 600,
            notificationPermissionReadyForTest = false,
            notificationSettingsLauncher = { false })
        rule.onNodeWithTag("route-walk-reminder-settings").performScrollTo().performClick()
        rule.onNodeWithTag("route-walk-reminder-settings-failure").assertExists()
        rule.onNodeWithTag("route-walk-reminder-toggle").assertIsNotEnabled()
    }

    @Test fun mutedChannelDisablesOptInAndOpensChannelSpecificAndroidSettings() {
        var observed: Intent? = null
        show(nearFutureDeparture(), 600,
            notificationPermissionReadyForTest = true,
            notificationChannelMutedForTest = true,
            notificationSettingsLauncher = { observed = it; true })
        rule.onNodeWithTag("route-walk-reminder-notifications-disabled").assertExists()
        rule.onNodeWithTag("route-walk-reminder-toggle").assertIsNotEnabled()
        rule.onNodeWithTag("route-walk-reminder-settings").performScrollTo().performClick()
        rule.runOnIdle {
            val intent = requireNotNull(observed)
            assertEquals(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS, intent.action)
            assertEquals(PlannedWalkReminderScheduler.CHANNEL,
                intent.getStringExtra(Settings.EXTRA_CHANNEL_ID))
            assertEquals(
                androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
                    .targetContext.packageName,
                intent.getStringExtra(Settings.EXTRA_APP_PACKAGE),
            )
        }
    }

    @Test fun manualWalkNotificationPreviewIsOptInAndDoesNotScheduleReminder() {
        var submissions = 0
        show(nearFutureDeparture(), 600,
            notificationPermissionReadyForTest = true,
            notificationPreviewSender = { submissions++; true })
        rule.runOnIdle { assertEquals(0, submissions) }
        rule.onNodeWithTag("route-walk-reminder-preview").performScrollTo().performClick()
        rule.onNodeWithTag("route-walk-reminder-preview-result").assertExists()
        rule.runOnIdle {
            assertEquals(1, submissions)
        }
    }

    @Test fun manualWalkNotificationPreviewFailureIsVisible() {
        show(nearFutureDeparture(), 600,
            notificationPermissionReadyForTest = true,
            notificationPreviewSender = { false })
        rule.onNodeWithTag("route-walk-reminder-preview").performScrollTo().performClick()
        rule.onNodeWithTag("route-walk-reminder-preview-result").assertExists()
        rule.onNodeWithTag("route-walk-reminder-toggle").assertIsEnabled()
    }

    @Test fun disabledNotificationsBlockWalkPreviewWithoutInvokingSender() {
        var submissions = 0
        show(nearFutureDeparture(), 600,
            notificationPermissionReadyForTest = false,
            notificationPreviewSender = { submissions++; true })
        rule.onNodeWithTag("route-walk-reminder-preview").assertIsNotEnabled()
        rule.runOnIdle { assertEquals(0, submissions) }
        rule.onNodeWithTag("route-walk-reminder-settings").assertExists()
    }

    @Test fun clearedWorkerTokenUpdatesOpenRouteReminderStatusOnClockTick() {
        val context = androidx.test.platform.app.InstrumentationRegistry
            .getInstrumentation().targetContext
        val preferences = context.getSharedPreferences(
            PlannedWalkReminderScheduler.PREFS, android.content.Context.MODE_PRIVATE)
        preferences.edit().clear()
            .putString(PlannedWalkReminderScheduler.KEY_TOKEN, "delivered-token")
            .putString(PlannedWalkReminderScheduler.KEY_ROUTE, "previous-gtfs-route")
            .putLong(PlannedWalkReminderScheduler.KEY_START,
                System.currentTimeMillis() + 300_000L)
            .commit()
        val clock = androidx.compose.runtime.mutableLongStateOf(System.currentTimeMillis())
        try {
            val option = RouteOption(
                id = "gtfs-1", origin = MapPoint(52.67, 8.24, "Lohne"),
                destination = MapPoint(53.08, 8.81, "Bremen Hbf"),
                line = "RE", direction = "Bremen",
                departure = "10:30", arrival = "11:30", walkingMinutes = 10,
                transfers = 0,
                stops = listOf(RouteStop(id = "stop-1", name = "Lohne",
                    plannedDeparture = nearFutureDeparture())),
                accessWalking = RouteWalkingLegEvidence("stop-1", "Lohne", 600),
            )
            rule.setContent {
                MaterialTheme {
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        PlannedWalkStartNotice(
                            option = option, nowMillisForTest = clock.longValue,
                            bufferMinutesForTest = 0,
                            notificationPermissionReadyForTest = true,
                        )
                    }
                }
            }
            rule.onNodeWithTag("route-walk-reminder-other-route").assertExists()
            rule.onNodeWithTag("route-walk-reminder-queued").assertExists()
            // Simulate the independent worker clearing the persisted opt-in token.
            preferences.edit().clear().commit()
            rule.runOnIdle { clock.longValue += 1_000L }
            rule.onNodeWithTag("route-walk-reminder-other-route").assertDoesNotExist()
            rule.onNodeWithTag("route-walk-reminder-queued").assertDoesNotExist()
            rule.onNodeWithTag("route-walk-reminder-toggle").assertTextContains(
                context.getString(R.string.route_walk_reminder_set), substring = true)
        } finally {
            preferences.edit().clear().commit()
        }
    }

    @Test fun overdueReminderDoesNotBlockNextTripAfterWorkerFreshnessWindow() {
        val context = androidx.test.platform.app.InstrumentationRegistry
            .getInstrumentation().targetContext
        val preferences = context.getSharedPreferences(
            PlannedWalkReminderScheduler.PREFS, android.content.Context.MODE_PRIVATE)
        preferences.edit().clear()
            .putString(PlannedWalkReminderScheduler.KEY_TOKEN, "expired-token")
            .putString(PlannedWalkReminderScheduler.KEY_ROUTE, "previous-gtfs-route")
            .putString(PlannedWalkReminderScheduler.KEY_DETAILS_TOKEN, "expired-token")
            .putString(PlannedWalkReminderScheduler.KEY_STOP, "Previous Stop")
            .putLong(PlannedWalkReminderScheduler.KEY_START,
                System.currentTimeMillis() - 180_000L)
            .commit()
        try {
            val scheduler = PlannedWalkReminderScheduler(context)
            assertEquals(null, scheduler.scheduledRouteId())
            assertEquals(null, scheduler.scheduledDetails())
            show(nearFutureDeparture(), 600)
            rule.onNodeWithTag("route-walk-reminder-other-route").assertDoesNotExist()
            rule.onNodeWithTag("route-walk-reminder-toggle").assertIsEnabled()
        } finally {
            preferences.edit().clear().commit()
        }
    }

    @Test fun schedulerRefusesToReplaceAnotherTripsStillCurrentReminder() {
        val context = androidx.test.platform.app.InstrumentationRegistry
            .getInstrumentation().targetContext
        val preferences = context.getSharedPreferences(
            PlannedWalkReminderScheduler.PREFS, android.content.Context.MODE_PRIVATE)
        preferences.edit().clear()
            .putString(PlannedWalkReminderScheduler.KEY_TOKEN, "preserve-token")
            .putString(PlannedWalkReminderScheduler.KEY_ROUTE, "previous-gtfs-route")
            .putLong(PlannedWalkReminderScheduler.KEY_START,
                System.currentTimeMillis() + 300_000L)
            .commit()
        try {
            val scheduler = PlannedWalkReminderScheduler(context)
            assertTrue(scheduler.hasConflictingActiveReminder("different-route"))
            assertTrue(!scheduler.hasConflictingActiveReminder("previous-gtfs-route"))
            val option = RouteOption(
                id = "different-route", origin = MapPoint(52.67, 8.24, "Lohne"),
                destination = MapPoint(53.08, 8.81, "Bremen"),
                line = "RE", direction = "Bremen", departure = "10:30",
                arrival = "11:30", walkingMinutes = 10, transfers = 0,
                stops = listOf(RouteStop(id = "stop-1", name = "Lohne",
                    plannedDeparture = nearFutureDeparture())),
                accessWalking = RouteWalkingLegEvidence("stop-1", "Lohne", 600),
            )
            assertTrue(!scheduler.schedule(option, 0))
            assertEquals("preserve-token", preferences.getString(
                PlannedWalkReminderScheduler.KEY_TOKEN, null))
            assertEquals("previous-gtfs-route", preferences.getString(
                PlannedWalkReminderScheduler.KEY_ROUTE, null))
        } finally {
            preferences.edit().clear().commit()
        }
    }

    @Test fun onlyMutedAndroidChannelIsRejectedWhenAppWideNotificationsAreOn() {
        val context = androidx.test.platform.app.InstrumentationRegistry
            .getInstrumentation().targetContext
        val manager = context.getSystemService(android.content.Context.NOTIFICATION_SERVICE)
            as android.app.NotificationManager
        val channelId = "walk_reminder_ci_muted_" + System.nanoTime()
        try {
            assertTrue(!PlannedWalkReminderScheduler.notificationChannelMuted(context, channelId))
            manager.createNotificationChannel(android.app.NotificationChannel(
                channelId, "Walk reminder CI", android.app.NotificationManager.IMPORTANCE_NONE))
            assertTrue(PlannedWalkReminderScheduler.notificationChannelMuted(context, channelId))
        } finally {
            manager.deleteNotificationChannel(channelId)
        }
    }
}
