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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccessibilityNew
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.Chat
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.DirectionsTransit
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

enum class ReadinessLevel { READY, ACTION_NEEDED, BLOCKED, INFO }

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
) {
    val overallLevel: ReadinessLevel
        get() = when {
            !locationPermission || !notificationPermission || !locationServicesAvailable ->
                ReadinessLevel.BLOCKED
            dataState !is NationwideDataState.Ready ||
                guidancePermissionState != GuidancePermissionState.GRANTED ||
                !bluetoothPermission -> ReadinessLevel.ACTION_NEEDED
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
            val locationServicesAvailable =
                runCatching { locationManager.getProviders(true).isNotEmpty() }.getOrDefault(false)

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
    onOpenAppSettings: () -> Unit,
    onRetryData: () -> Unit,
    onConnectorAction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var themeSheetVisible by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("settings-readiness-panel"),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = stringResource(R.string.settings_readiness_title),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text = readinessSummaryText(state.overallLevel),
                style = MaterialTheme.typography.bodyMedium,
                color = readinessColor(state.overallLevel),
                modifier = Modifier.testTag("readiness-summary"),
            )
        }

        ArrivalSelectionRow(
            title = stringResource(R.string.theme_mode_title),
            value = themeModeLabel(state.themeMode),
            icon = Icons.Rounded.Palette,
            onClick = { themeSheetVisible = true },
            testTag = "theme-mode-selector",
        )
        Text(
            text = stringResource(R.string.theme_mode_status, themeModeLabel(state.themeMode)),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.testTag("theme-mode-status"),
        )

        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)) {
                ReadinessRow(
                    label = stringResource(R.string.readiness_location),
                    value = when {
                        !state.locationPermission -> stringResource(R.string.readiness_permission_required)
                        !state.locationServicesAvailable -> stringResource(R.string.readiness_location_services_off)
                        else -> stringResource(R.string.readiness_ready)
                    },
                    level = if (state.locationPermission && state.locationServicesAvailable) {
                        ReadinessLevel.READY
                    } else {
                        ReadinessLevel.BLOCKED
                    },
                    icon = Icons.Rounded.LocationOn,
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
                    icon = Icons.Rounded.Notifications,
                    testTag = "readiness-notifications",
                )
                ReadinessRow(
                    label = stringResource(R.string.readiness_data),
                    value = when (val data = state.dataState) {
                        NationwideDataState.Idle -> stringResource(R.string.readiness_data_not_ready)
                        is NationwideDataState.Loading -> stringResource(R.string.readiness_data_loading)
                        is NationwideDataState.Ready -> stringResource(R.string.readiness_data_ready, data.stopCount)
                        is NationwideDataState.Error -> stringResource(R.string.readiness_data_unavailable)
                    },
                    level = when (state.dataState) {
                        is NationwideDataState.Ready -> ReadinessLevel.READY
                        is NationwideDataState.Loading -> ReadinessLevel.INFO
                        else -> ReadinessLevel.ACTION_NEEDED
                    },
                    icon = Icons.Rounded.DirectionsTransit,
                    testTag = "nationwide-data-state",
                )
                ReadinessRow(
                    label = stringResource(R.string.readiness_audio),
                    value = guidanceReadinessText(state),
                    level = when {
                        state.guidancePermissionState == GuidancePermissionState.DENIED ->
                            ReadinessLevel.ACTION_NEEDED
                        state.guidancePermissionState == GuidancePermissionState.PARTIAL ||
                            !state.bluetoothPermission -> ReadinessLevel.ACTION_NEEDED
                        else -> ReadinessLevel.READY
                    },
                    icon = if (state.audioRoute == GuidanceAudioRoute.BLUETOOTH_CONNECTED) {
                        Icons.Rounded.Bluetooth
                    } else {
                        Icons.Rounded.VolumeUp
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
                    icon = Icons.Rounded.Refresh,
                    testTag = "readiness-background",
                )
                ReadinessRow(
                    label = stringResource(R.string.readiness_connector),
                    value = when {
                        !state.connectorConfigured ->
                            stringResource(R.string.readiness_connector_optional_unconfigured)
                        state.connectorConnected ->
                            stringResource(R.string.readiness_connector_connected)
                        else -> stringResource(R.string.readiness_connector_optional_disconnected)
                    },
                    level = if (state.connectorConnected) ReadinessLevel.READY else ReadinessLevel.INFO,
                    icon = Icons.Rounded.Chat,
                    testTag = "connector-setup-status",
                    supporting = connectorStatus,
                )
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ArrivalSelectionRow(
                title = stringResource(R.string.readiness_review_journey_permissions),
                value = stringResource(R.string.readiness_location),
                icon = Icons.Rounded.LocationOn,
                onClick = onRequestJourneyPermissions,
                testTag = "readiness-permissions",
            )
            ArrivalSelectionRow(
                title = stringResource(R.string.readiness_review_guidance_permissions),
                value = stringResource(R.string.readiness_audio),
                icon = Icons.Rounded.VolumeUp,
                onClick = onRequestGuidancePermissions,
                testTag = "readiness-guidance-permissions",
            )
            ArrivalSelectionRow(
                title = stringResource(R.string.readiness_refresh),
                value = readinessSummaryText(state.overallLevel),
                icon = Icons.Rounded.Refresh,
                onClick = onRefreshReadiness,
                testTag = "readiness-refresh",
            )
            ArrivalSelectionRow(
                title = stringResource(R.string.readiness_open_app_settings),
                value = stringResource(R.string.app_name),
                icon = Icons.Rounded.Settings,
                onClick = onOpenAppSettings,
                testTag = "readiness-app-settings",
            )
            if (state.dataState !is NationwideDataState.Ready &&
                state.dataState !is NationwideDataState.Loading
            ) {
                ArrivalSelectionRow(
                    title = stringResource(R.string.download_germany_index),
                    value = stringResource(R.string.readiness_data_not_ready),
                    icon = Icons.Rounded.CloudDownload,
                    onClick = onRetryData,
                    testTag = "nationwide-index-download",
                )
            }
            ArrivalSelectionRow(
                title = when {
                    connectorBusy -> stringResource(R.string.processing)
                    state.connectorConnected -> stringResource(R.string.disconnect_chatgpt)
                    else -> stringResource(R.string.connect_chatgpt)
                },
                value = connectorStatus,
                icon = if (state.connectorConnected) Icons.Rounded.Link else Icons.Rounded.Chat,
                onClick = onConnectorAction,
                enabled = !connectorBusy && state.connectorConfigured,
                testTag = if (state.connectorConnected) "connector-disconnect" else "connector-connect",
            )
        }

        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                InfoBlock(
                    icon = Icons.Rounded.AccessibilityNew,
                    title = stringResource(R.string.readiness_accessibility_title),
                    body = stringResource(R.string.readiness_accessibility_body),
                    testTag = "readiness-accessibility",
                )
                InfoBlock(
                    icon = Icons.Rounded.Security,
                    title = stringResource(R.string.readiness_privacy_title),
                    body = stringResource(R.string.readiness_privacy_body),
                    testTag = "readiness-privacy",
                )
            }
        }
    }

    ArrivalChoiceSheet(
        visible = themeSheetVisible,
        title = stringResource(R.string.theme_mode_title),
        options = ArrivalThemeMode.values().toList(),
        selected = state.themeMode,
        optionLabel = { themeModeLabel(it) },
        optionTag = {
            when (it) {
                ArrivalThemeMode.SYSTEM -> "theme-system"
                ArrivalThemeMode.LIGHT -> "theme-light"
                ArrivalThemeMode.DARK -> "theme-dark"
            }
        },
        onSelect = {
            onThemeModeChange(it)
            themeSheetVisible = false
        },
        onDismiss = { themeSheetVisible = false },
    )
}

@Composable
private fun ReadinessRow(
    label: String,
    value: String,
    level: ReadinessLevel,
    icon: ImageVector,
    testTag: String,
    supporting: String? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(testTag)
            .padding(vertical = 9.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = readinessColor(level),
            modifier = Modifier.padding(top = 2.dp),
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp),
            verticalArrangement = Arrangement.spacedBy(1.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
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
}

@Composable
private fun InfoBlock(
    icon: ImageVector,
    title: String,
    body: String,
    testTag: String,
) {
    Row(modifier = Modifier.fillMaxWidth().testTag(testTag)) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
        )
        Column(
            modifier = Modifier.padding(start = 10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = body,
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
private fun readinessColor(level: ReadinessLevel): Color = when (level) {
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
