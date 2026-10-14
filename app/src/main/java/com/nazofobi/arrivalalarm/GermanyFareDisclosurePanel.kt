package com.nazofobi.arrivalalarm

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

/**
 * User-facing facts only. D-Ticket validity cannot be derived from GTFS trip type;
 * fare and operator checkout links are intentionally absent until sourced.
 */
@Composable
internal fun GermanyFareDisclosurePanel(bicycleAllowance: BicycleAllowance) {
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.testTag("journey-fare-bike-disclosure"),
    ) {
        Text(
            text = stringResource(R.string.germany_ticket_unknown),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.testTag("journey-ticket-unverified"),
        )
        val bikeMessage = when (bicycleAllowance) {
            BicycleAllowance.ALLOWED -> R.string.germany_bike_allowed
            BicycleAllowance.DISALLOWED -> R.string.germany_bike_disallowed
            BicycleAllowance.UNKNOWN -> R.string.germany_bike_unknown
        }
        Text(
            text = stringResource(bikeMessage),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.testTag("journey-bike-eligibility"),
        )
    }
}
