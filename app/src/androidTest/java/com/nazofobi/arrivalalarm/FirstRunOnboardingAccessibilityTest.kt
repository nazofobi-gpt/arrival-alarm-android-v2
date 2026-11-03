package com.nazofobi.arrivalalarm

import android.provider.Settings
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FirstRunOnboardingAccessibilityTest {
    @get:Rule val rule = createComposeRule()

    @Test fun optionalQuickSetupIsDiscoverableAndDoesNotBlockContinue() {
        val continued = mutableStateOf(false)
        rule.setContent {
            MaterialTheme {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    FirstRunOnboarding(onContinue = { continued.value = true })
                }
            }
        }
        rule.onNodeWithTag("onboarding-accessibility-setup")
            .performScrollTo().assertIsDisplayed()
        rule.onNodeWithTag("onboarding-accessibility-settings")
            .performScrollTo().assertIsDisplayed()
        rule.onNodeWithTag("onboarding-complete")
            .performScrollTo().performClick()
        rule.runOnIdle { assertTrue(continued.value) }
    }

    @Test fun quickSetupUsesOnlySystemAccessibilitySettings() {
        val intent = accessibilityQuickSetupIntent()
        assertEquals(Settings.ACTION_ACCESSIBILITY_SETTINGS, intent.action)
        assertEquals(null, intent.component)
    }
}
