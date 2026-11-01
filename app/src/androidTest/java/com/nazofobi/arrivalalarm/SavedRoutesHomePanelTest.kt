package com.nazofobi.arrivalalarm

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SavedRoutesHomePanelTest {
    @get:Rule
    val rule = createComposeRule()

    private val preset = SavedRoutePreset(
        id = "test-route-1",
        name = "Lohne → Bremen Hbf",
        originLatitude = 52.665,
        originLongitude = 8.237,
        originLabel = "Lohne",
        destinationLatitude = 53.083,
        destinationLongitude = 8.813,
        destinationLabel = "Bremen Hbf",
        directOnlyRoutes = false,
        fewerTransfersFirst = true,
        shorterWalkingFirst = true,
        atMostOneTransfer = true,
        savedAtEpochMillis = 1_000L,
    )

    @Test
    fun savedRouteCanBeOpenedButDeleteRequiresExplicitConfirmation() {
        var saves = 0
        var opened: SavedRoutePreset? = null
        val removed = mutableListOf<String>()
        rule.setContent {
            MaterialTheme {
                SavedRoutesHomePanel(
                    presets = listOf(preset),
                    canSave = true,
                    canRestore = true,
                    sourceSafe = true,
                    status = null,
                    onSave = { saves++ },
                    onRestore = { opened = it },
                    onRemove = { removed += it },
                )
            }
        }

        rule.onNodeWithTag("saved-route-save").performClick()
        rule.onNodeWithTag("saved-route-open-0").performClick()
        rule.runOnIdle {
            assertEquals(1, saves)
            assertEquals(preset, opened)
            assertEquals(emptyList<String>(), removed)
        }

        rule.onNodeWithTag("saved-route-delete-0").performClick()
        rule.onNodeWithTag("saved-route-confirm-delete").assertExists()
        rule.runOnIdle { assertEquals(emptyList<String>(), removed) }

        rule.onNodeWithTag("saved-route-cancel-delete").performClick()
        rule.onNodeWithTag("saved-route-confirm-delete").assertDoesNotExist()
        rule.runOnIdle { assertEquals(emptyList<String>(), removed) }

        rule.onNodeWithTag("saved-route-delete-0").performClick()
        rule.onNodeWithTag("saved-route-confirm-delete").performClick()
        rule.runOnIdle { assertEquals(listOf(preset.id), removed) }
    }

    @Test
    fun activeJourneyCannotOpenOrDeleteSavedRoute() {
        var opened = false
        var removed = false
        rule.setContent {
            MaterialTheme {
                SavedRoutesHomePanel(
                    presets = listOf(preset),
                    canSave = false,
                    canRestore = false,
                    sourceSafe = true,
                    status = null,
                    onSave = {},
                    onRestore = { opened = true },
                    onRemove = { removed = true },
                )
            }
        }

        rule.onNodeWithTag("saved-route-save").assertIsNotEnabled()
        rule.onNodeWithTag("saved-route-open-0").assertIsNotEnabled()
        rule.onNodeWithTag("saved-route-delete-0").assertIsNotEnabled()
        rule.runOnIdle {
            assertEquals(false, opened)
            assertEquals(false, removed)
        }
    }

    @Test
    fun corruptSavedRouteSnapshotIsDisclosedAndCannotBeReplaced() {
        var saved = false
        var opened = false
        var removed = false
        rule.setContent {
            MaterialTheme {
                SavedRoutesHomePanel(
                    presets = listOf(preset),
                    canSave = true,
                    canRestore = true,
                    sourceSafe = false,
                    status = null,
                    onSave = { saved = true },
                    onRestore = { opened = true },
                    onRemove = { removed = true },
                )
            }
        }

        rule.onNodeWithTag("saved-routes-unsafe").assertExists()
        rule.onNodeWithTag("saved-route-save").assertIsNotEnabled()
        rule.onNodeWithTag("saved-route-open-0").assertDoesNotExist()
        rule.onNodeWithTag("saved-route-delete-0").assertDoesNotExist()
        rule.runOnIdle {
            assertEquals(false, saved)
            assertEquals(false, opened)
            assertEquals(false, removed)
        }
    }
}
