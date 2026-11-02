package com.nazofobi.arrivalalarm

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
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
    fun savedCommutesFilterByStoredEndpointWithoutOpeningOrChangingRoutes() {
        val another = preset.copy(
            id = "test-route-2",
            name = "Weekend ride",
            originLatitude = 53.55,
            originLongitude = 10.0,
            originLabel = "Hamburg",
            destinationLatitude = 52.52,
            destinationLongitude = 13.4,
            destinationLabel = "Berlin",
        )
        val opened = mutableListOf<String>()
        val removed = mutableListOf<String>()
        rule.setContent {
            MaterialTheme {
                SavedRoutesHomePanel(
                    presets = listOf(preset, another),
                    canSave = false,
                    canRestore = true,
                    sourceSafe = true,
                    status = null,
                    onSave = {},
                    onRestore = { opened += it.id },
                    onRemove = { removed += it },
                )
            }
        }

        rule.onNodeWithTag("saved-route-filter").performTextInput("berLIN")
        rule.onNodeWithTag("saved-route-name-0").assertTextEquals("Weekend ride")
        rule.onNodeWithTag("saved-route-name-1").assertDoesNotExist()
        rule.runOnIdle {
            assertEquals(emptyList<String>(), opened)
            assertEquals(emptyList<String>(), removed)
        }
        rule.onNodeWithTag("saved-route-open-0").performClick()
        rule.runOnIdle { assertEquals(listOf(another.id), opened) }

        rule.onNodeWithTag("saved-route-filter").performTextClearance()
        rule.onNodeWithTag("saved-route-name-0").assertTextEquals(preset.name)
        rule.onNodeWithTag("saved-route-name-1").assertTextEquals("Weekend ride")
        rule.onNodeWithTag("saved-route-filter").performTextInput("unknown-place")
        rule.onNodeWithTag("saved-route-filter-empty").assertExists()
        rule.onNodeWithTag("saved-route-open-0").assertDoesNotExist()
        rule.runOnIdle { assertEquals(emptyList<String>(), removed) }
    }

    @Test
    fun unsafeSavedCommutesDoNotExposeFilterOrRouteActions() {
        rule.setContent {
            MaterialTheme {
                SavedRoutesHomePanel(
                    presets = listOf(preset, preset.copy(id = "test-route-2")),
                    canSave = true,
                    canRestore = true,
                    sourceSafe = false,
                    status = null,
                    onSave = {},
                    onRestore = {},
                    onRemove = {},
                )
            }
        }
        rule.onNodeWithTag("saved-routes-unsafe").assertExists()
        rule.onNodeWithTag("saved-route-filter").assertDoesNotExist()
        rule.onNodeWithTag("saved-route-open-0").assertDoesNotExist()
    }

    @Test
    fun savedRoutePreviewShowsStoredEndpointsWithoutStartingAnAction() {
        var opened = 0
        val named = preset.copy(name = "Morning commute")
        rule.setContent {
            MaterialTheme {
                SavedRoutesHomePanel(
                    presets = listOf(named),
                    canSave = true,
                    canRestore = true,
                    sourceSafe = true,
                    status = null,
                    onSave = {},
                    onRestore = { opened++ },
                    onRemove = {},
                )
            }
        }
        val context = androidx.test.platform.app.InstrumentationRegistry
            .getInstrumentation().targetContext
        val expected = context.getString(
            R.string.saved_routes_home_endpoints,
            "Lohne",
            "Bremen Hbf",
        )
        rule.onNodeWithTag("saved-route-endpoints-0").assertTextEquals(expected)
        rule.runOnIdle { assertEquals(0, opened) }
    }

    @Test
    fun routeActionsIdentifyTheCorrectSavedCommuteForScreenReaders() {
        val other = preset.copy(id = "test-route-2", name = "Other commute")
        val opened = mutableListOf<String>()
        rule.setContent {
            MaterialTheme {
                SavedRoutesHomePanel(
                    presets = listOf(preset, other),
                    canSave = false,
                    canRestore = true,
                    sourceSafe = true,
                    status = null,
                    onSave = {},
                    onRestore = { opened += it.id },
                    onRemove = {},
                )
            }
        }
        val context = androidx.test.platform.app.InstrumentationRegistry
            .getInstrumentation().targetContext
        rule.onNodeWithTag("saved-route-open-0").assertContentDescriptionEquals(
            context.getString(R.string.saved_routes_home_open_accessibility, preset.name)
        )
        rule.onNodeWithTag("saved-route-open-1").assertContentDescriptionEquals(
            context.getString(R.string.saved_routes_home_open_accessibility, other.name)
        )
        rule.onNodeWithTag("saved-route-rename-1").assertContentDescriptionEquals(
            context.getString(R.string.saved_routes_home_rename_accessibility, other.name)
        )
        rule.onNodeWithTag("saved-route-delete-1").assertContentDescriptionEquals(
            context.getString(R.string.saved_routes_home_delete_accessibility, other.name)
        )
        rule.runOnIdle { assertEquals(emptyList<String>(), opened) }
        rule.onNodeWithTag("saved-route-open-1").performClick()
        rule.runOnIdle { assertEquals(listOf(other.id), opened) }
    }

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
    fun savedCommuteCanBeRenamedOnlyAfterValidExplicitConfirmation() {
        val renames = mutableListOf<String>()
        rule.setContent {
            MaterialTheme {
                SavedRoutesHomePanel(
                    presets = listOf(preset),
                    canSave = true,
                    canRestore = true,
                    sourceSafe = true,
                    status = null,
                    onSave = {},
                    onRestore = {},
                    onRemove = {},
                    onRename = { _, name -> renames += name },
                )
            }
        }
        rule.onNodeWithTag("saved-route-rename-0").performClick()
        rule.onNodeWithTag("saved-route-rename-input").performTextClearance()
        rule.onNodeWithTag("saved-route-confirm-rename").assertIsNotEnabled()
        rule.onNodeWithTag("saved-route-rename-input").performTextInput("Morning commute")
        rule.onNodeWithTag("saved-route-cancel-rename").performClick()
        rule.runOnIdle { assertEquals(emptyList<String>(), renames) }

        rule.onNodeWithTag("saved-route-rename-0").performClick()
        rule.onNodeWithTag("saved-route-rename-input").performTextClearance()
        rule.onNodeWithTag("saved-route-rename-input").performTextInput("Morning commute")
        rule.onNodeWithTag("saved-route-confirm-rename").performClick()
        rule.runOnIdle { assertEquals(listOf("Morning commute"), renames) }
    }

    @Test
    fun clearingAllSavedRoutesRequiresVisibleUserConfirmation() {
        val second = preset.copy(
            id = "test-route-2",
            name = "Other commute",
            originLatitude = 51.0,
            destinationLatitude = 54.0,
        )
        val confirmed = mutableListOf<List<SavedRoutePreset>>()
        rule.setContent {
            MaterialTheme {
                SavedRoutesHomePanel(
                    presets = listOf(preset, second),
                    canSave = false,
                    canRestore = true,
                    sourceSafe = true,
                    status = null,
                    onSave = {},
                    onRestore = {},
                    onRemove = {},
                    onClearAll = { confirmed += it },
                )
            }
        }
        rule.onNodeWithTag("saved-routes-clear-all").performClick()
        rule.onNodeWithTag("saved-routes-confirm-clear-all").assertExists()
        rule.runOnIdle { assertEquals(0, confirmed.size) }
        rule.onNodeWithTag("saved-routes-cancel-clear-all").performClick()
        rule.runOnIdle { assertEquals(0, confirmed.size) }
        rule.onNodeWithTag("saved-routes-clear-all").performClick()
        rule.onNodeWithTag("saved-routes-confirm-clear-all").performClick()
        rule.runOnIdle { assertEquals(listOf(listOf(preset, second)), confirmed) }
    }

    @Test
    fun clearedSavedRoutesOfferOneShotUndoAndRestoreBothCards() {
        val second = preset.copy(
            id = "test-route-2",
            name = "Other commute",
            originLatitude = 51.0,
            destinationLatitude = 54.0,
        )
        val shown = mutableStateOf(listOf(preset, second))
        val undo = mutableStateOf<List<SavedRoutePreset>?>(null)
        rule.setContent {
            MaterialTheme {
                SavedRoutesHomePanel(
                    presets = shown.value,
                    canSave = false,
                    canRestore = true,
                    sourceSafe = true,
                    status = null,
                    onSave = {},
                    onRestore = {},
                    onRemove = {},
                    onClearAll = { expected ->
                        undo.value = expected
                        shown.value = emptyList()
                    },
                    undoPresets = undo.value,
                    onUndoClearAll = { previous ->
                        shown.value = previous
                        undo.value = null
                    },
                )
            }
        }
        rule.onNodeWithTag("saved-routes-clear-all").performClick()
        rule.onNodeWithTag("saved-routes-confirm-clear-all").performClick()
        rule.onNodeWithTag("saved-routes-undo-clear-all").assertExists().performClick()
        rule.onNodeWithTag("saved-route-name-0").assertExists()
        rule.onNodeWithTag("saved-route-name-1").assertExists()
        rule.onNodeWithTag("saved-routes-undo-clear-all").assertDoesNotExist()
        rule.runOnIdle { assertEquals(listOf(preset, second), shown.value) }
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
        rule.onNodeWithTag("saved-route-rename-0").assertIsNotEnabled()
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
        rule.onNodeWithTag("saved-route-rename-0").assertDoesNotExist()
        rule.runOnIdle {
            assertEquals(false, saved)
            assertEquals(false, opened)
            assertEquals(false, removed)
        }
    }
}
