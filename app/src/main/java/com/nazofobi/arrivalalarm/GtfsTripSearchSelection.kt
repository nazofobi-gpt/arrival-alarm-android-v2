package com.nazofobi.arrivalalarm

/**
 * Exact identity carried by a local GTFS trip search hit before a service date
 * is chosen. Display labels are never used to infer the trip identity.
 */
internal data class GtfsTripSearchIdentityTemplate(
    val tripId: String,
    val routeId: String,
    val serviceId: String,
    val directionId: Int?,
    val displayLabel: String,
    val sourceVersion: String,
) {
    init {
        require(tripId.isNotBlank())
        require(routeId.isNotBlank())
        require(serviceId.isNotBlank())
        require(displayLabel.isNotBlank())
        require(sourceVersion.isNotBlank())
    }

    fun forServiceDate(serviceDate: GtfsServiceDate): GtfsTripSearchSelection =
        GtfsTripSearchSelection(
            identity = GtfsTripInstanceIdentity(
                tripId = tripId,
                serviceDate = serviceDate,
                serviceId = serviceId,
                routeId = routeId,
                directionId = directionId,
                frequencyStartSeconds = null,
            ),
            displayLabel = displayLabel,
            sourceVersion = sourceVersion,
        )

    companion object {
        fun from(
            result: TransitLocationResult,
            sourceVersion: String,
        ): GtfsTripSearchIdentityTemplate? {
            if (result.kind != TransitLocationKind.TRIP || sourceVersion.isBlank()) return null
            val tripId = result.tripId?.trim()?.takeIf(String::isNotEmpty) ?: return null
            val routeId = result.routeId?.trim()?.takeIf(String::isNotEmpty) ?: return null
            val serviceId = result.serviceId?.trim()?.takeIf(String::isNotEmpty) ?: return null
            val label = result.label.trim().takeIf(String::isNotEmpty) ?: return null
            if (result.key != "trip:$tripId") return null
            return runCatching {
                GtfsTripSearchIdentityTemplate(
                    tripId = tripId,
                    routeId = routeId,
                    serviceId = serviceId,
                    directionId = result.directionId,
                    displayLabel = label,
                    sourceVersion = sourceVersion,
                )
            }.getOrNull()
        }
    }
}

internal data class GtfsTripSearchSelection(
    val identity: GtfsTripInstanceIdentity,
    val displayLabel: String,
    val sourceVersion: String,
) {
    init {
        require(displayLabel.isNotBlank())
        require(sourceVersion.isNotBlank())
    }
}
