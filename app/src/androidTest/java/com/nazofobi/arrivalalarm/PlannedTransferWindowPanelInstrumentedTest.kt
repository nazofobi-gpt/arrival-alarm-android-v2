package com.nazofobi.arrivalalarm

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
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

    @Test fun missingTransferSourceDoesNotShowUnfoundedConnectionAdvice() {
        rule.setContent {
            MaterialTheme { PlannedTransferWindowPanel(emptyList()) }
        }
        rule.onNodeWithTag("journey-static-transfers").assertDoesNotExist()
        rule.onNodeWithTag("journey-static-transfer-0").assertDoesNotExist()
    }
}
