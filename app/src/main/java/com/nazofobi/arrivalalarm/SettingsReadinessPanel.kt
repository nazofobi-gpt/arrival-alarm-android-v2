package com.nazofobi.arrivalalarm

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.RadioButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.Role

enum class ReadinessLevel { READY, ACTION_NEEDED, BLOCKED, INFO }

/** A downloaded index is not evidence that a search or route request succeeded. */
enum class TransitFunctionHealth { UNVERIFIED, WORKING, DEGRADED, FAILED }

// A downloaded feed is not a usable offline index when it contains zero stops.
internal fun hasUsableNationwideIndex(state: NationwideDataState): Boolean =
    state is NationwideDataState.Ready && state.stopCount > 0

/** A zero-stop Ready snapshot is unusable and must offer recovery, not a dead end. */
internal fun shouldOfferNationwideDataRetry(state: NationwideDataState): Boolean =
    state !is NationwideDataState.Loading && !hasUsableNationwideIndex(state)

/** Stale session flags cannot establish a connection after its configuration is removed. */
internal fun hasVerifiedConnectorConnection(configured: Boolean, connected: Boolean): Boolean =
    configured && connected

/** Bluetooth permission is optional for device-speaker guidance, but required for a BT route. */
internal fun hasUsableGuidanceAudioPath(state: SettingsReadinessState): Boolean =
    state.guidancePermissionState != GuidancePermissionState.DENIED &&
        (state.audioRoute != GuidanceAudioRoute.BLUETOOTH_CONNECTED || state.bluetoothPermission)

data class SettingsReadinessState(
    val themeMode: ArrivalThemeMode,
    val locationPermission: Boolean,
    val notificationPermission: Boolean,
    val locationServicesAvailable: Boolean,
    val bluetoothPermission: Boolean,
    val guidancePermissionState: GuidancePermissionState,
    val audioRoute: GuidanceAudioRoute,
    val dataState: NationwideDataState,
    val backgroundJourneyActive: Boolean,
    val connectorConfigured: Boolean,
    val connectorConnected: Boolean,
    val searchHealth: TransitFunctionHealth = TransitFunctionHealth.UNVERIFIED,
    val routingHealth: TransitFunctionHealth = TransitFunctionHealth.UNVERIFIED,
) {
    val overallLevel: ReadinessLevel
        get() = when {
            !locationPermission || !notificationPermission || !locationServicesAvailable ->
                ReadinessLevel.BLOCKED
            !hasUsableNationwideIndex(dataState) ||
                !hasUsableGuidanceAudioPath(this) ||
                searchHealth != TransitFunctionHealth.WORKING ||
                routingHealth != TransitFunctionHealth.WORKING -> ReadinessLevel.ACTION_NEEDED
            else -> ReadinessLevel.READY
        }

    companion object {
        fun from(
            context: Context,
            themeMode: ArrivalThemeMode,
            dataState: NationwideDataState,
            guidanceState: GuidanceReliabilityState,
            backgroundJourneyActive: Boolean,
            connectorConfigured: Boolean,
            connectorConnected: Boolean,
            searchHealth: TransitFunctionHealth = TransitFunctionHealth.UNVERIFIED,
            routingHealth: TransitFunctionHealth = TransitFunctionHealth.UNVERIFIED,
        ): SettingsReadinessState {
            fun granted(permission: String): Boolean =
                context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

            val locationPermission =
                granted(Manifest.permission.ACCESS_FINE_LOCATION) ||
                    granted(Manifest.permission.ACCESS_COARSE_LOCATION)
            val notificationPermission =
                Build.VERSION.SDK_INT < 33 || granted(Manifest.permission.POST_NOTIFICATIONS)
            val bluetoothPermission =
                Build.VERSION.SDK_INT < 31 || granted(Manifest.permission.BLUETOOTH_CONNECT)
            val locationManager =
                context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            val locationServicesAvailable = isDeviceLocationEnabledForTracking(locationManager)

            return SettingsReadinessState(
                themeMode = themeMode,
                locationPermission = locationPermission,
                notificationPermission = notificationPermission,
                locationServicesAvailable = locationServicesAvailable,
                bluetoothPermission = bluetoothPermission,
                guidancePermissionState = guidanceState.permissionState,
                audioRoute = guidanceState.audioRoute,
                dataState = dataState,
                backgroundJourneyActive = backgroundJourneyActive,
                connectorConfigured = connectorConfigured,
                connectorConnected = connectorConnected,
                searchHealth = searchHealth,
                routingHealth = routingHealth,
            )
        }
    }
}

@Composable
fun SettingsReadinessPanel(
    state: SettingsReadinessState,
    connectorBusy: Boolean,
    connectorStatus: String,
    onThemeModeChange: (ArrivalThemeMode) -> Unit,
    onRequestJourneyPermissions: () -> Unit,
    onRequestGuidancePermissions: () -> Unit,
    onRefreshReadiness: () -> Unit,
    onShareDiagnostics: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onRetryData: () -> Unit,
    onConnectorAction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var themeOptionsExpanded by rememberSaveable { mutableStateOf(false) }
    val connectorReady = hasVerifiedConnectorConnection(state.connectorConfigured, state.connectorConnected)

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("settings-readiness-panel"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.settings_readiness_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text = readinessSummaryText(state.overallLevel),
                style = MaterialTheme.typography.bodyLarge,
                color = readinessColor(state.overallLevel),
                modifier = Modifier.testTag("readiness-summary"),
            )

            HorizontalDivider()

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .clickable { themeOptionsExpanded = !themeOptionsExpanded }
                    .testTag("theme-mode-picker"),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.theme_mode_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = stringResource(
                            R.string.theme_mode_status,
                            themeModeLabel(state.themeMode),
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.testTag("theme-mode-status"),
                    )
                }
            }
            if (themeOptionsExpanded) {
                Column(
                    modifier = Modifier.fillMaxWidth().selectableGroup(),
                ) {
                    ThemeModeChoice(
                        mode = ArrivalThemeMode.SYSTEM,
                        selected = state.themeMode == ArrivalThemeMode.SYSTEM,
                        label = stringResource(R.string.theme_system),
                        testTag = "theme-system",
                        onSelect = { onThemeModeChange(it); themeOptionsExpanded = false },
                    )
                    ThemeModeChoice(
                        mode = ArrivalThemeMode.LIGHT,
                        selected = state.themeMode == ArrivalThemeMode.LIGHT,
                        label = stringResource(R.string.theme_light),
                        testTag = "theme-light",
                        onSelect = { onThemeModeChange(it); themeOptionsExpanded = false },
                    )
                    ThemeModeChoice(
                        mode = ArrivalThemeMode.DARK,
                        selected = state.themeMode == ArrivalThemeMode.DARK,
                        label = stringResource(R.string.theme_dark),
                        testTag = "theme-dark",
                        onSelect = { onThemeModeChange(it); themeOptionsExpanded = false },
                    )
                }
            }

            HorizontalDivider()

            ReadinessRow(
                label = stringResource(R.string.readiness_location),
                value = when {
                    !state.locationPermission ->
                        stringResource(R.string.readiness_permission_required)
                    !state.locationServicesAvailable ->
                        stringResource(R.string.readiness_location_services_off)
                    else -> stringResource(R.string.readiness_ready)
                },
                level = if (state.locationPermission && state.locationServicesAvailable) {
                    ReadinessLevel.READY
                } else {
                    ReadinessLevel.BLOCKED
                },
                testTag = "readiness-location",
            )
            ReadinessRow(
                label = stringResource(R.string.readiness_notifications),
                value = if (state.notificationPermission) {
                    stringResource(R.string.readiness_ready)
                } else {
                    stringResource(R.string.readiness_permission_required)
                },
                level = if (state.notificationPermission) ReadinessLevel.READY else ReadinessLevel.BLOCKED,
                testTag = "readiness-notifications",
            )
            ReadinessRow(
                label = stringResource(R.string.readiness_data),
                value = when (val data = state.dataState) {
                    NationwideDataState.Idle -> stringResource(R.string.readiness_data_not_ready)
                    is NationwideDataState.Loading -> stringResource(R.string.readiness_data_loading)
                    is NationwideDataState.Ready -> if (hasUsableNationwideIndex(data)) {
                        stringResource(R.string.readiness_data_ready, data.stopCount)
                    } else {
                        stringResource(R.string.readiness_data_unavailable)
                    }
                    is NationwideDataState.Error -> stringResource(R.string.readiness_data_unavailable)
                },
                level = when (state.dataState) {
                    is NationwideDataState.Ready -> if (hasUsableNationwideIndex(state.dataState)) ReadinessLevel.READY else ReadinessLevel.ACTION_NEEDED
                    is NationwideDataState.Loading -> ReadinessLevel.INFO
                    else -> ReadinessLevel.ACTION_NEEDED
                },
                testTag = "nationwide-data-state",
            )
            ReadinessRow(
                label = stringResource(R.string.readiness_search_function),
                value = transitFunctionHealthLabel(state.searchHealth),
                level = transitFunctionReadinessLevel(state.searchHealth),
                testTag = "readiness-search-health",
            )
            ReadinessRow(
                label = stringResource(R.string.readiness_routing_function),
                value = transitFunctionHealthLabel(state.routingHealth),
                level = transitFunctionReadinessLevel(state.routingHealth),
                testTag = "readiness-routing-health",
            )
            ReadinessRow(
                label = stringResource(R.string.readiness_audio),
                value = guidanceReadinessText(state),
                level = if (hasUsableGuidanceAudioPath(state)) {
                    ReadinessLevel.READY
                } else {
                    ReadinessLevel.ACTION_NEEDED
                },
                testTag = "readiness-audio",
            )
            ReadinessRow(
                label = stringResource(R.string.readiness_background),
                value = if (state.backgroundJourneyActive) {
                    stringResource(R.string.readiness_background_active)
                } else {
                    stringResource(R.string.readiness_background_inactive)
                },
                level = if (state.backgroundJourneyActive) ReadinessLevel.READY else ReadinessLevel.INFO,
                testTag = "readiness-background",
            )
            ReadinessRow(
                label = stringResource(R.string.readiness_connector),
                value = when {
                    !state.connectorConfigured ->
                        stringResource(R.string.readiness_connector_optional_unconfigured)
                    connectorReady ->
                        stringResource(R.string.readiness_connector_connected)
                    else -> stringResource(R.string.readiness_connector_optional_disconnected)
                },
                level = if (connectorReady) ReadinessLevel.READY else ReadinessLevel.INFO,
                testTag = "connector-setup-status",
                supporting = if (state.connectorConfigured) {
                    connectorStatus
                } else {
                    stringResource(R.string.connector_url_missing)
                },
            )

            OutlinedButton(
                onClick = onRequestJourneyPermissions,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .testTag("readiness-permissions"),
            ) {
                Text(stringResource(R.string.readiness_review_journey_permissions))
            }
            OutlinedButton(
                onClick = onRequestGuidancePermissions,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .testTag("readiness-guidance-permissions"),
            ) {
                Text(stringResource(R.string.readiness_review_guidance_permissions))
            }
            OutlinedButton(
                onClick = onRefreshReadiness,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .testTag("readiness-refresh"),
            ) {
                Text(stringResource(R.string.readiness_refresh))
            }
            OutlinedButton(
                onClick = onShareDiagnostics,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .testTag("readiness-share-diagnostics"),
            ) {
                Text(stringResource(R.string.readiness_share_diagnostics))
            }
            Text(
                text = stringResource(R.string.readiness_share_diagnostics_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(
                onClick = onOpenAppSettings,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .testTag("readiness-app-settings"),
            ) {
                Text(stringResource(R.string.readiness_open_app_settings))
            }

            if (shouldOfferNationwideDataRetry(state.dataState)) {
                Button(
                    onClick = onRetryData,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag("nationwide-index-download"),
                ) {
                    Text(stringResource(R.string.download_germany_index))
                }
            }

            Button(
                onClick = onConnectorAction,
                enabled = !connectorBusy && state.connectorConfigured,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .testTag(
                        if (connectorReady) "connector-disconnect" else "connector-connect"
                    ),
            ) {
                Text(
                    when {
                        connectorBusy -> stringResource(R.string.processing)
                        connectorReady -> stringResource(R.string.disconnect_chatgpt)
                        else -> stringResource(R.string.connect_chatgpt)
                    }
                )
            }

            HorizontalDivider()

            Text(
                text = stringResource(R.string.readiness_accessibility_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = stringResource(R.string.readiness_accessibility_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag("readiness-accessibility"),
            )
            Text(
                text = stringResource(R.string.readiness_privacy_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = stringResource(R.string.readiness_privacy_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag("readiness-privacy"),
            )
        }
    }
}

@Composable
private fun ThemeModeChoice(
    mode: ArrivalThemeMode,
    selected: Boolean,
    label: String,
    testTag: String,
    onSelect: (ArrivalThemeMode) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .selectable(
                selected = selected,
                role = Role.RadioButton,
                onClick = { onSelect(mode) },
            )
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(text = label, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun ReadinessRow(
    label: String,
    value: String,
    level: ReadinessLevel,
    testTag: String,
    supporting: String? = null,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(testTag),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            color = readinessColor(level),
        )
        supporting?.takeIf { it.isNotBlank() }?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun readinessSummaryText(level: ReadinessLevel): String =
    stringResource(
        when (level) {
            ReadinessLevel.READY -> R.string.readiness_summary_ready
            ReadinessLevel.ACTION_NEEDED -> R.string.readiness_summary_degraded
            ReadinessLevel.BLOCKED -> R.string.readiness_summary_blocked
            ReadinessLevel.INFO -> R.string.readiness_summary_degraded
        }
    )

@Composable
private fun readinessColor(level: ReadinessLevel) = when (level) {
    ReadinessLevel.READY -> MaterialTheme.colorScheme.secondary
    ReadinessLevel.ACTION_NEEDED -> MaterialTheme.colorScheme.tertiary
    ReadinessLevel.BLOCKED -> MaterialTheme.colorScheme.error
    ReadinessLevel.INFO -> MaterialTheme.colorScheme.onSurfaceVariant
}

@Composable
private fun themeModeLabel(mode: ArrivalThemeMode): String =
    stringResource(
        when (mode) {
            ArrivalThemeMode.SYSTEM -> R.string.theme_system
            ArrivalThemeMode.LIGHT -> R.string.theme_light
            ArrivalThemeMode.DARK -> R.string.theme_dark
        }
    )

@Composable
private fun guidanceReadinessText(state: SettingsReadinessState): String =
    when {
        state.guidancePermissionState == GuidancePermissionState.DENIED ->
            stringResource(R.string.guidance_permission_needed)
        state.guidancePermissionState == GuidancePermissionState.PARTIAL ||
            !state.bluetoothPermission -> stringResource(R.string.guidance_partial)
        state.audioRoute == GuidanceAudioRoute.BLUETOOTH_CONNECTED ->
            stringResource(R.string.guidance_bluetooth)
        else -> stringResource(R.string.guidance_device)
    }

fun openArrivalAlarmAppSettings(context: Context) {
    val intent = Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.fromParts("package", context.packageName, null),
    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(intent)
}

@Composable
private fun transitFunctionHealthLabel(health: TransitFunctionHealth): String =
    stringResource(
        when (health) {
            TransitFunctionHealth.UNVERIFIED -> R.string.readiness_function_unverified
            TransitFunctionHealth.WORKING -> R.string.readiness_function_working
            TransitFunctionHealth.DEGRADED -> R.string.readiness_function_degraded
            TransitFunctionHealth.FAILED -> R.string.readiness_function_failed
        },
    )

private fun transitFunctionReadinessLevel(health: TransitFunctionHealth): ReadinessLevel =
    when (health) {
        TransitFunctionHealth.WORKING -> ReadinessLevel.READY
        TransitFunctionHealth.UNVERIFIED -> ReadinessLevel.INFO
        TransitFunctionHealth.DEGRADED, TransitFunctionHealth.FAILED -> ReadinessLevel.ACTION_NEEDED
    }
