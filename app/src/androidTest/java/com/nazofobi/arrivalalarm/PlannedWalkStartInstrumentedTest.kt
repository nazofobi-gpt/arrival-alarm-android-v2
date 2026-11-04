package com.nazofobi.arrivalalarm

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PlannedWalkStartInstrumentedTest {
    @get:Rule val rule = createComposeRule()

    private fun show(planned: String?, walking: Int?) {
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
}
