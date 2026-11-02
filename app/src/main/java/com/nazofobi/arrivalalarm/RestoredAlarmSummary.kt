package com.nazofobi.arrivalalarm

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.util.Locale

/** Display only persisted GPS alarm evidence after losing the route preview. */
internal fun restoredAlarmTargetCoordinates(journey: JourneyUiState): String? {
    if (journey.phase != JourneyPhase.ARMED && journey.phase != JourneyPhase.ARRIVED) return null
    val target = journey.destination ?: return null
    if (!target.latitude.isFinite() || target.latitude !in -90.0..90.0 ||
        !target.longitude.isFinite() || target.longitude !in -180.0..180.0
    ) return null
    return String.format(Locale.ROOT, "%.5f, %.5f", target.latitude, target.longitude)
}

@Composable
internal fun RestoredAlarmSummary(journey: JourneyUiState, modifier: Modifier = Modifier) {
    val coordinates = restoredAlarmTargetCoordinates(journey) ?: return
    val lastDistance = journey.distanceMeters?.takeIf {
        it.isFinite() && it in 0.0..20_000_000.0
    }
    Card(modifier = modifier.fillMaxWidth().testTag("restored-alarm-summary")) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = stringResource(R.string.restored_alarm_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = stringResource(R.string.live_trip_alarm) + ": " + journeyPhaseText(journey.phase),
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(text = stringResource(R.string.restored_alarm_notice))
            Text(
                text = stringResource(R.string.restored_alarm_target_format, coordinates),
                modifier = Modifier.testTag("restored-alarm-target"),
            )
            Text(
                text = if (lastDistance != null) {
                    stringResource(
                        R.string.restored_alarm_last_distance_format,
                        String.format(Locale.ROOT, "%.0f", lastDistance),
                    )
                } else stringResource(R.string.restored_alarm_distance_unavailable),
                modifier = Modifier.testTag("restored-alarm-distance"),
            )
        }
    }
}
