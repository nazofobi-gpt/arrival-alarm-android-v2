package com.nazofobi.arrivalalarm

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PlannedTransferWindowPanelInstrumentedTest {
    @get:Rule val rule = createComposeRule()

    private val evidence = RouteTransferWindowEvidence(
        arrivalStopId = "x",
        departureStopId = "y",
        arrivalStopName = "Bremen Hbf",
        departureStopName = "Bremen Süd",
        plannedGapMinutes = 6,
    )

    @Test fun sourceBackedTransferShowsLabeledGapAndExplicitCaveat() {
        rule.setContent {
            MaterialTheme { PlannedTransferWindowPanel(listOf(evidence)) }
        }
        rule.onNodeWithTag("journey-static-transfers").assertExists()
        rule.onNodeWithTag("journey-static-transfer-0").assertExists()
        rule.onNodeWithTag("journey-static-transfer-disclaimer").assertExists()
    }

    @Test fun rendersAgencyLocalDepartureTimeOnlyWhenSourceTimeExists() {
        rule.setContent {
            MaterialTheme {
                PlannedTransferWindowPanel(listOf(
                    evidence.copy(
                        plannedArrivalLocalTime = "25.10.2026 02:25 +01:00",
                        plannedDepartureLocalTime = "25.10.2026 02:30 +01:00",
                    ),
                ))
            }
        }
        rule.onNodeWithTag("journey-static-transfer-arrival-time-0").assertExists()
        rule.onNodeWithTag("journey-static-transfer-local-time-0").assertExists()
    }

    @Test fun sourcedScheduledPlatformsAppearSeparatelyWithoutLiveClaim() {
        rule.setContent {
            MaterialTheme {
                PlannedTransferWindowPanel(listOf(
                    evidence.copy(
                        plannedArrivalPlatformCode = "3",
                        plannedDeparturePlatformCode = "7B",
                    ),
                ))
            }
        }
        rule.onNodeWithTag("journey-static-transfer-arrival-platform-0").assertExists()
        rule.onNodeWithTag("journey-static-transfer-departure-platform-0").assertExists()
        rule.onNodeWithTag("journey-static-transfer-disclaimer").assertExists()
    }

    @Test fun unavailableOrMalformedGtfsPlatformsFailClosed() {
        rule.setContent {
            MaterialTheme {
                PlannedTransferWindowPanel(listOf(
                    evidence.copy(plannedDeparturePlatformCode = "1\n2"),
                ))
            }
        }
        rule.onNodeWithTag("journey-static-transfer-arrival-platform-0").assertDoesNotExist()
        rule.onNodeWithTag("journey-static-transfer-departure-platform-0").assertDoesNotExist()
    }

    @Test fun nearTermDepartureShowsOnlyScheduledForegroundCountdown() {
        val now = 1_800_000_000_000L
        rule.setContent {
            MaterialTheme {
                PlannedTransferWindowPanel(
                    listOf(evidence.copy(plannedDepartureEpochMillis = now + 61_000L)),
                    nowEpochMillis = now,
                )
            }
        }
        rule.onNodeWithTag("journey-static-transfer-countdown-0").assertExists()
        rule.onNodeWithTag("journey-static-transfer-disclaimer").assertExists()
    }

    @Test fun staleOrUnknownDepartureDoesNotShowCountdown() {
        val now = 1_800_000_000_000L
        rule.setContent {
            MaterialTheme {
                PlannedTransferWindowPanel(
                    listOf(evidence.copy(plannedDepartureEpochMillis = now - 1L)),
                    nowEpochMillis = now,
                )
            }
        }
        rule.onNodeWithTag("journey-static-transfer-0").assertExists()
        rule.onNodeWithTag("journey-static-transfer-countdown-0").assertDoesNotExist()
        rule.onNodeWithTag("journey-static-transfer-arrival-time-0").assertDoesNotExist()
        rule.onNodeWithTag("journey-static-transfer-local-time-0").assertDoesNotExist()
    }

    @Test fun justPassedScheduledDepartureShowsHonestNonLiveStatus() {
        val now = 1_800_000_000_000L
        rule.setContent {
            MaterialTheme {
                PlannedTransferWindowPanel(
                    listOf(evidence.copy(plannedDepartureEpochMillis = now - 61_000L)),
                    nowEpochMillis = now,
                )
            }
        }
        rule.onNodeWithTag("journey-static-transfer-departure-passed-0").assertExists()
        rule.onNodeWithTag("journey-static-transfer-countdown-0").assertDoesNotExist()
    }

    @Test fun oldScheduleDoesNotLeavePermanentPassedWarning() {
        val now = 1_800_000_000_000L
        rule.setContent {
            MaterialTheme {
                PlannedTransferWindowPanel(
                    listOf(evidence.copy(plannedDepartureEpochMillis = now - 10_800_001L)),
                    nowEpochMillis = now,
                )
            }
        }
        rule.onNodeWithTag("journey-static-transfer-departure-passed-0").assertDoesNotExist()
    }

    @Test fun explicitTransferWalkUsesOnlyPublicStopCoordsAndShowsDisclosure() {
        val coords = RouteTransferWalkingCoordinates(53.075, 8.808, 53.079, 8.813)
        var opened: String? = null
        rule.setContent {
            MaterialTheme {
                PlannedTransferWindowPanel(
                    listOf(evidence.copy(walkingCoordinates = coords)),
                    onOpenWalkingDirections = { opened = it },
                )
            }
        }
        rule.onNodeWithTag("journey-transfer-walk-map-0").assertExists().performClick()
        rule.onNodeWithTag("journey-transfer-walk-privacy-0").assertExists()
        rule.runOnIdle {
            assertEquals(plannedTransferWalkingDirectionsUrl(coords), opened)
        }
    }

    @Test fun transferWalkingButtonFailsClosedWithoutVerifiedStopPair() {
        rule.setContent {
            MaterialTheme { PlannedTransferWindowPanel(listOf(evidence)) }
        }
        rule.onNodeWithTag("journey-static-transfer-0").assertExists()
        rule.onNodeWithTag("journey-transfer-walk-map-0").assertDoesNotExist()
    }

    @Test fun missingTransferSourceDoesNotShowUnfoundedConnectionAdvice() {
        rule.setContent {
            MaterialTheme { PlannedTransferWindowPanel(emptyList()) }
        }
        rule.onNodeWithTag("journey-static-transfers").assertDoesNotExist()
        rule.onNodeWithTag("journey-static-transfer-0").assertDoesNotExist()
    }

    @Test fun sourceCoordinatesAndTightGapShowQualifiedWalkingRisk() {
        val longTransfer = evidence.copy(
            walkingCoordinates = RouteTransferWalkingCoordinates(53.075, 8.808, 53.095, 8.808),
        )
        rule.setContent {
            MaterialTheme { PlannedTransferWindowPanel(listOf(longTransfer)) }
        }
        rule.onNodeWithTag("journey-transfer-walk-risk-0").assertExists()
        rule.onNodeWithTag("journey-static-transfer-disclaimer").assertExists()
    }

    @Test fun missingOrShortWalkingEvidenceNeverShowsFeasibilityClaim() {
        rule.setContent {
            MaterialTheme {
                PlannedTransferWindowPanel(listOf(evidence.copy(
                    walkingCoordinates = RouteTransferWalkingCoordinates(
                        53.075, 8.808, 53.0751, 8.808,
                    ),
                )))
            }
        }
        rule.onNodeWithTag("journey-transfer-walk-risk-0").assertDoesNotExist()
        rule.onNodeWithTag("journey-static-transfer-disclaimer").assertExists()
    }
}
