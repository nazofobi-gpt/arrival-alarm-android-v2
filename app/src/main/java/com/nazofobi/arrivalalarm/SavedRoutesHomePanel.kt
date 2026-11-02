package com.nazofobi.arrivalalarm

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/**
 * Saved routes are local endpoint templates, not trusted route results or active trips.
 * The parent must replan against the current data source when restoring a preset.
 */
@Composable
internal fun SavedRoutesHomePanel(
    presets: List<SavedRoutePreset>,
    canSave: Boolean,
    canRestore: Boolean,
    sourceSafe: Boolean,
    status: String?,
    onSave: () -> Unit,
    onRestore: (SavedRoutePreset) -> Unit,
    onRemove: (String) -> Unit,
    onRename: (SavedRoutePreset, String) -> Unit = { _, _ -> },
    onClearAll: (List<SavedRoutePreset>) -> Unit = {},
    undoPresets: List<SavedRoutePreset>? = null,
    onUndoClearAll: (List<SavedRoutePreset>) -> Unit = {},
    undoRemoval: SavedRouteRemovalUndo? = null,
    onUndoRemoval: (SavedRouteRemovalUndo) -> Unit = {},
) {
    // A confirmation is intentionally discarded after recreation rather than persisted.
    var pendingClearSnapshot by androidx.compose.runtime.remember {
        mutableStateOf<List<SavedRoutePreset>?>(null)
    }
    var savedRouteQuery by rememberSaveable { mutableStateOf("") }
    val normalizedQuery = savedRouteQuery.trim()
    val visiblePresets = presets.filter { preset ->
        savedRouteMatchesQuery(preset, normalizedQuery)
    }
    var pendingDeleteId by rememberSaveable { mutableStateOf<String?>(null) }
    val pendingDelete = presets.firstOrNull { it.id == pendingDeleteId }
    var pendingRenameId by rememberSaveable { mutableStateOf<String?>(null) }
    var proposedName by rememberSaveable { mutableStateOf("") }
    val pendingRename = presets.firstOrNull { it.id == pendingRenameId }
    if (pendingRenameId != null) {
        val trimmedName = proposedName.trim()
        val validChange = pendingRename != null && sourceSafe && canRestore &&
            trimmedName != pendingRename.name &&
            SavedRoutePresetsCodec.encode(listOf(pendingRename.copy(name = trimmedName))) != null
        AlertDialog(
            onDismissRequest = { pendingRenameId = null },
            title = { Text(stringResource(R.string.saved_routes_home_rename_title)) },
            text = {
                OutlinedTextField(
                    value = proposedName,
                    onValueChange = { proposedName = it.take(81) },
                    singleLine = true,
                    label = { Text(stringResource(R.string.saved_routes_home_rename_hint)) },
                    modifier = Modifier.fillMaxWidth().testTag("saved-route-rename-input"),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val candidate = pendingRename
                        pendingRenameId = null
                        if (validChange && candidate != null) onRename(candidate, trimmedName)
                    },
                    enabled = validChange,
                    modifier = Modifier.testTag("saved-route-confirm-rename"),
                ) {
                    Text(stringResource(R.string.saved_routes_home_rename_confirm))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { pendingRenameId = null },
                    modifier = Modifier.testTag("saved-route-cancel-rename"),
                ) {
                    Text(stringResource(R.string.saved_routes_home_delete_cancel))
                }
            },
        )
    }
    if (pendingDeleteId != null) {
        AlertDialog(
            onDismissRequest = { pendingDeleteId = null },
            title = { Text(stringResource(R.string.saved_routes_home_delete_confirm_title)) },
            text = {
                Text(stringResource(
                    R.string.saved_routes_home_delete_confirm_message,
                    pendingDelete?.name ?: "",
                ))
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val id = pendingDelete?.id
                        pendingDeleteId = null
                        if (id != null && sourceSafe && canRestore) onRemove(id)
                    },
                    enabled = pendingDelete != null && sourceSafe && canRestore,
                    modifier = Modifier.testTag("saved-route-confirm-delete"),
                ) {
                    Text(stringResource(R.string.saved_routes_home_delete))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { pendingDeleteId = null },
                    modifier = Modifier.testTag("saved-route-cancel-delete"),
                ) {
                    Text(stringResource(R.string.saved_routes_home_delete_cancel))
                }
            },
        )
    }

    pendingClearSnapshot?.let { expected ->
        val canConfirm = sourceSafe && canRestore && expected.size >= 2 && presets == expected
        AlertDialog(
            onDismissRequest = { pendingClearSnapshot = null },
            title = { Text(stringResource(R.string.saved_routes_home_clear_all_confirm_title)) },
            text = {
                Text(stringResource(
                    R.string.saved_routes_home_clear_all_confirm_message,
                    expected.size,
                ))
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingClearSnapshot = null
                        if (canConfirm) onClearAll(expected)
                    },
                    enabled = canConfirm,
                    modifier = Modifier.testTag("saved-routes-confirm-clear-all"),
                ) {
                    Text(stringResource(R.string.saved_routes_home_clear_all))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { pendingClearSnapshot = null },
                    modifier = Modifier.testTag("saved-routes-cancel-clear-all"),
                ) {
                    Text(stringResource(R.string.saved_routes_home_delete_cancel))
                }
            },
        )
    }

    Card(
        modifier = Modifier.fillMaxWidth().testTag("saved-routes-home"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.saved_routes_home_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text = stringResource(R.string.saved_routes_home_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (!sourceSafe) {
                Text(
                    text = stringResource(R.string.saved_routes_home_unsafe),
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.testTag("saved-routes-unsafe"),
                )
            }
            OutlinedButton(
                onClick = onSave,
                enabled = canSave && sourceSafe,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                    .testTag("saved-route-save"),
            ) {
                Text(stringResource(R.string.saved_routes_home_save))
            }
            status?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.testTag("saved-routes-status"),
                )
            }
            val canUndoRemoval = sourceSafe && canRestore && undoRemoval?.let { undo ->
                undo.before.count { it.id == undo.removedId } == 1 &&
                    presets == undo.before.filterNot { it.id == undo.removedId }
            } == true
            if (canUndoRemoval) {
                OutlinedButton(
                    onClick = { undoRemoval?.let(onUndoRemoval) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                        .testTag("saved-route-undo-delete"),
                ) {
                    Text(stringResource(R.string.saved_routes_home_undo_delete))
                }
            }
            if (sourceSafe && presets.isEmpty()) {
                Text(stringResource(R.string.saved_routes_home_empty))
                if (canRestore && undoPresets != null && undoPresets.size >= 2) {
                    OutlinedButton(
                        onClick = { onUndoClearAll(undoPresets) },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                            .testTag("saved-routes-undo-clear-all"),
                    ) {
                        Text(stringResource(R.string.saved_routes_home_undo_clear_all))
                    }
                }
            }
            if (sourceSafe && presets.size >= 2) {
                OutlinedTextField(
                    value = savedRouteQuery,
                    onValueChange = { savedRouteQuery = it.take(80) },
                    singleLine = true,
                    label = { Text(stringResource(R.string.saved_routes_home_filter)) },
                    modifier = Modifier.fillMaxWidth().testTag("saved-route-filter"),
                )
                if (normalizedQuery.isNotEmpty()) {
                    TextButton(
                        onClick = { savedRouteQuery = "" },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                            .testTag("saved-route-filter-clear"),
                    ) {
                        Text(stringResource(R.string.saved_routes_home_filter_clear))
                    }
                }
                if (normalizedQuery.isNotEmpty() && visiblePresets.isEmpty()) {
                    Text(
                        text = stringResource(R.string.saved_routes_home_filter_empty),
                        modifier = Modifier.testTag("saved-route-filter-empty"),
                    )
                }
            }
            if (sourceSafe) {
                visiblePresets.take(SavedRoutePresetsCodec.MAX_PRESETS).forEachIndexed { index, preset ->
                    val openLabel = stringResource(R.string.saved_routes_home_open_accessibility, preset.name)
                    val renameLabel = stringResource(R.string.saved_routes_home_rename_accessibility, preset.name)
                    val deleteLabel = stringResource(R.string.saved_routes_home_delete_accessibility, preset.name)
                    Text(
                        text = preset.name,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.testTag("saved-route-name-$index"),
                    )
                    Text(
                        text = stringResource(
                            R.string.saved_routes_home_endpoints,
                            preset.originLabel,
                            preset.destinationLabel,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.testTag("saved-route-endpoints-$index"),
                    )
                    val activePreferences = listOfNotNull(
                        if (preset.directOnlyRoutes) stringResource(R.string.saved_routes_home_preference_direct) else null,
                        if (preset.fewerTransfersFirst) stringResource(R.string.saved_routes_home_preference_fewer_transfers) else null,
                        if (preset.shorterWalkingFirst) stringResource(R.string.saved_routes_home_preference_shorter_walk) else null,
                        if (preset.atMostOneTransfer) stringResource(R.string.saved_routes_home_preference_max_one_transfer) else null,
                    )
                    Text(
                        text = if (activePreferences.isEmpty()) {
                            stringResource(R.string.saved_routes_home_preference_default)
                        } else {
                            activePreferences.joinToString(" · ")
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.testTag("saved-route-preferences-$index"),
                    )
                    OutlinedButton(
                        onClick = { onRestore(preset) },
                        enabled = canRestore,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                            .testTag("saved-route-open-$index")
                            .semantics { contentDescription = openLabel },
                    ) {
                        Text(stringResource(R.string.saved_routes_home_open))
                    }
                    OutlinedButton(
                        onClick = {
                            pendingRenameId = preset.id
                            proposedName = preset.name
                        },
                        enabled = canRestore,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                            .testTag("saved-route-rename-$index")
                            .semantics { contentDescription = renameLabel },
                    ) {
                        Text(stringResource(R.string.saved_routes_home_rename))
                    }
                    OutlinedButton(
                        onClick = { pendingDeleteId = preset.id },
                        enabled = canRestore,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                            .testTag("saved-route-delete-$index")
                            .semantics { contentDescription = deleteLabel },
                    ) {
                        Text(stringResource(R.string.saved_routes_home_delete))
                    }
                }
                if (presets.size >= 2) {
                    OutlinedButton(
                        onClick = { pendingClearSnapshot = presets.toList() },
                        enabled = canRestore,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                            .testTag("saved-routes-clear-all"),
                    ) {
                        Text(stringResource(R.string.saved_routes_home_clear_all))
                    }
                }
            }
        }
    }
}
