package com.nazofobi.arrivalalarm

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PinnedSavedCommuteHomeTest {
    @get:Rule val rule = createComposeRule()

    private fun preset(id: String) = SavedRoutePreset(
        id = id,
        name = "Route $id",
        originLatitude = 52.665,
        originLongitude = 8.237,
        originLabel = "Lohne",
        destinationLatitude = 53.083,
        destinationLongitude = 8.813,
        destinationLabel = "Bremen",
        directOnlyRoutes = false,
        fewerTransfersFirst = true,
        shorterWalkingFirst = true,
        atMostOneTransfer = false,
        savedAtEpochMillis = 1_000L,
    )

    @Test
    fun localPinMovesSavedCommuteToFirstActionWithoutOpeningOrArmingIt() {
        val prefs = InstrumentationRegistry.getInstrumentation().targetContext
            .getSharedPreferences("saved_route_home", Context.MODE_PRIVATE)
        val original = runCatching { prefs.getString("pinned_commute_id_v1", null) }.getOrNull()
        val originallyPresent = prefs.contains("pinned_commute_id_v1")
        val first = preset("first")
        val second = preset("second")
        var restored: SavedRoutePreset? = null
        try {
            prefs.edit().remove("pinned_commute_id_v1").commit()
            rule.setContent {
                MaterialTheme {
                    SavedRoutesHomePanel(
                        presets = listOf(first, second),
                        canSave = true,
                        canRestore = true,
                        sourceSafe = true,
                        status = null,
                        onSave = {},
                        onRestore = { restored = it },
                        onRemove = {},
                    )
                }
            }
            rule.onNodeWithTag("saved-route-pin-1").performClick()
            rule.runOnIdle {
                assertEquals("second", prefs.getString("pinned_commute_id_v1", null))
                assertEquals(null, restored)
            }
            rule.onNodeWithTag("saved-route-open-0").performClick()
            rule.runOnIdle { assertEquals(second, restored) }
            rule.onNodeWithTag("saved-route-pin-0").performClick()
            rule.runOnIdle {
                assertEquals(null, prefs.getString("pinned_commute_id_v1", null))
            }
            rule.onNodeWithTag("saved-route-open-0").performClick()
            rule.runOnIdle { assertEquals(first, restored) }
        } finally {
            val editor = prefs.edit().remove("pinned_commute_id_v1")
            if (originallyPresent && original != null) editor.putString("pinned_commute_id_v1", original)
            editor.commit()
        }
    }

    @Test
    fun activeJourneyDisablesLocalPinAndPreservesExistingSelection() {
        val prefs = InstrumentationRegistry.getInstrumentation().targetContext
            .getSharedPreferences("saved_route_home", Context.MODE_PRIVATE)
        val prior = runCatching { prefs.getString("pinned_commute_id_v1", null) }.getOrNull()
        var restored = false
        rule.setContent {
            MaterialTheme {
                SavedRoutesHomePanel(
                    presets = listOf(preset("only")),
                    canSave = false,
                    canRestore = false,
                    sourceSafe = true,
                    status = null,
                    onSave = {},
                    onRestore = { restored = true },
                    onRemove = {},
                )
            }
        }
        rule.onNodeWithTag("saved-route-pin-0").assertIsNotEnabled()
        rule.runOnIdle {
            assertEquals(prior, prefs.getString("pinned_commute_id_v1", null))
            assertEquals(false, restored)
        }
    }
}
