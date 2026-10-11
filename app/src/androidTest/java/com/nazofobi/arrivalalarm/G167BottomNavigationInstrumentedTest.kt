package com.nazofobi.arrivalalarm

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * G-167: Preserve navigation and TalkBack-visible text while replacing Unicode icons
 * with decorative Material vector icons. This is an automated semantics gate, not a
 * substitute for physical light/dark, large-text or TalkBack acceptance.
 */
@RunWith(AndroidJUnit4::class)
class G167BottomNavigationInstrumentedTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun fiveDestinationsHaveLocalizedLabelsAndRemainSelectable() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val current = mutableStateOf(ArrivalAppScreen.HOME)
        val selections = mutableListOf<ArrivalAppScreen>()
        rule.setContent {
            MaterialTheme {
                ArrivalBottomNavigation(
                    current = current.value,
                    onSelect = {
                        current.value = it
                        selections += it
                    },
                )
            }
        }

        val tabs = listOf(
            Triple(ArrivalAppScreen.HOME, "nav-home", R.string.nav_home),
            Triple(ArrivalAppScreen.SEARCH, "nav-search", R.string.nav_search),
            Triple(ArrivalAppScreen.JOURNEY, "nav-journey", R.string.nav_journey),
            Triple(ArrivalAppScreen.DEPARTURES, "nav-departures", R.string.nav_departures),
            Triple(ArrivalAppScreen.SETTINGS, "nav-settings", R.string.nav_settings),
        )

        rule.onNodeWithTag("bottom-navigation").assertIsDisplayed()
        tabs.forEach { (_, tag, stringId) ->
            rule.onNodeWithTag(tag)
                .assertIsDisplayed()
                .assertTextContains(context.getString(stringId), substring = true)
        }

        rule.onNodeWithTag("nav-home").assertIsSelected()
        tabs.drop(1).forEach { (screen, tag, _) ->
            rule.onNodeWithTag(tag).performClick()
            rule.waitForIdle()
            rule.onNodeWithTag(tag).assertIsSelected()
            tabs.filter { it.first != screen }.forEach { (_, otherTag, _) ->
                rule.onNodeWithTag(otherTag).assertIsNotSelected()
            }
            rule.runOnIdle {
                assertEquals(screen, current.value)
                assertEquals(screen, selections.last())
            }
        }
        assertEquals(tabs.drop(1).map { it.first }, selections)
    }

    @Test
    fun routeAndLiveSubscreensKeepJourneyTabSelected() {
        val current = mutableStateOf(ArrivalAppScreen.ROUTES)
        rule.setContent {
            MaterialTheme {
                ArrivalBottomNavigation(
                    current = current.value,
                    onSelect = { current.value = it },
                )
            }
        }
        rule.onNodeWithTag("nav-journey").assertIsSelected()
        rule.onNodeWithTag("nav-home").assertIsNotSelected()

        rule.runOnIdle { current.value = ArrivalAppScreen.LIVE }
        rule.onNodeWithTag("nav-journey").assertIsSelected()

        rule.onNodeWithTag("nav-home").performClick()
        rule.onNodeWithTag("nav-home").assertIsSelected()
        rule.onNodeWithTag("nav-journey").assertIsNotSelected()
    }
}
