package com.nazofobi.arrivalalarm

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RestoredAlarmMapActionTest {
    @get:Rule val rule = createComposeRule()

    @Test fun mapActionOpensOnlyTheRestoredTargetOnExplicitTap() {
        val requested = mutableListOf<String>()
        rule.setContent {
            MaterialTheme {
                RestoredAlarmSummary(
                    journey = JourneyUiState(
                        phase = JourneyPhase.ARMED,
                        destination = GeoPoint(53.083, 8.813),
                    ),
                    onOpenTargetMap = { requested.add(it) },
                )
            }
        }
        rule.onNodeWithTag("restored-alarm-open-map").assertIsDisplayed()
        rule.runOnIdle { assertEquals(emptyList<String>(), requested) }
        rule.onNodeWithTag("restored-alarm-open-map").performClick()
        rule.runOnIdle { assertEquals(listOf("geo:0,0?q=53.08300,8.81300"), requested) }
    }

    @Test fun unarmedTargetNeverOffersMapAction() {
        rule.setContent {
            MaterialTheme {
                RestoredAlarmSummary(
                    journey = JourneyUiState(
                        phase = JourneyPhase.DESTINATION_SELECTED,
                        destination = GeoPoint(53.083, 8.813),
                    ),
                )
            }
        }
        rule.onNodeWithTag("restored-alarm-open-map").assertDoesNotExist()
    }
}
