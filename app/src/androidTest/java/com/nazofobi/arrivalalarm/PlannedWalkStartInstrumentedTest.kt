package com.nazofobi.arrivalalarm

import android.content.Intent
import android.provider.CalendarContract
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.assertTextContains
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
        calendarLauncher: ((Intent) -> Boolean)? = null,
    ) {
        rule.setContent {
            MaterialTheme {
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
                )
            }
        }
    }

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
        rule.onNodeWithTag("route-walk-safety-buffer").performClick()
        rule.onNodeWithTag("route-planned-walk-calendar").performClick()
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
            rule.onNodeWithTag("route-walk-safety-buffer").performClick()
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
        rule.onNodeWithTag("route-planned-walk-calendar").performClick()
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
        rule.onNodeWithTag("route-planned-walk-calendar").performClick()
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
        rule.onNodeWithTag("route-walk-directions").performClick()
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
        rule.onNodeWithTag("route-walk-directions").performClick()
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

}
