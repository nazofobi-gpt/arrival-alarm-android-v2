package com.nazofobi.arrivalalarm

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
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
) {
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
            if (sourceSafe && presets.isEmpty()) {
                Text(stringResource(R.string.saved_routes_home_empty))
            }
            if (sourceSafe) {
                presets.take(SavedRoutePresetsCodec.MAX_PRESETS).forEachIndexed { index, preset ->
                    Text(
                        text = preset.name,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.testTag("saved-route-name-$index"),
                    )
                    OutlinedButton(
                        onClick = { onRestore(preset) },
                        enabled = canRestore,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                            .testTag("saved-route-open-$index"),
                    ) {
                        Text(stringResource(R.string.saved_routes_home_open))
                    }
                    OutlinedButton(
                        onClick = { onRemove(preset.id) },
                        enabled = canRestore,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                            .testTag("saved-route-delete-$index"),
                    ) {
                        Text(stringResource(R.string.saved_routes_home_delete))
                    }
                }
            }
        }
    }
}
