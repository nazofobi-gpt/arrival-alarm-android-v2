package com.nazofobi.arrivalalarm

data class SelectedTripCancellationEvidence(
    val snapshotUpdatedAtEpochSeconds: Long,
    val cancelledTripInstances: Set<GtfsTripInstanceIdentity>,
)
