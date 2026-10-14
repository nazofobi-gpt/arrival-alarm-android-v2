package com.nazofobi.arrivalalarm

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

/**
 * G-188: fare, bicycle and Deutschland-Ticket eligibility are UNKNOWN until verified
 * against this specific journey. A route line name or GTFS mode is not fare evidence.
 * This information link does not transmit any origin, destination or trip identifiers.
 */
internal const val DEUTSCHLAND_TICKET_INFORMATION_URL =
    "https://www.bahn.de/angebot/regio/deutschland-ticket/"

@Composable
internal fun JourneyTicketInformation(modifier: Modifier = Modifier) {
    val uriHandler = LocalUriHandler.current
    Card(
        modifier = modifier.fillMaxWidth().testTag("journey-ticket-unverified"),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.journey_ticket_information_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = stringResource(R.string.journey_ticket_information_unverified),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(
                onClick = { uriHandler.openUri(DEUTSCHLAND_TICKET_INFORMATION_URL) },
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                    .testTag("journey-ticket-official-information"),
            ) {
                Text(stringResource(R.string.journey_ticket_information_link))
            }
        }
    }
}
