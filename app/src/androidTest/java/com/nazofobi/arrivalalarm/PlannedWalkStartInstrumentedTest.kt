package com.nazofobi.arrivalalarm

import android.content.Intent
import android.provider.CalendarContract
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createComposeRule
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
                        stops = listOf(RouteStop(id = "stop-1", name = "Lohne", plannedDeparture = planned)),
                        accessWalking = walking?.let {
                            RouteWalkingLegEvidence("stop-1", "Lohne", it)
                        },
                    ),
                    calendarLauncher = calendarLauncher,
                )
            }
        }
    }

    @Test fun plannedEvidenceShowsWalkingStartWithSourceDisclosure() {
        show("2026-11-04T10:30:00+01:00", 600)
        rule.onNodeWithTag("route-planned-walk-start").assertExists()
        rule.onNodeWithTag("route-planned-walk-start-note").assertExists()
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
}
