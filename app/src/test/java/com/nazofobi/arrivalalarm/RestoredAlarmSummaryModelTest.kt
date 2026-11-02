package com.nazofobi.arrivalalarm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RestoredAlarmSummaryModelTest {
    @Test fun validArmedOrArrivedTargetSurvivesWithoutRoutePreview() {
        val state = JourneyUiState(phase = JourneyPhase.ARMED, destination = GeoPoint(53.083, 8.813))
        assertEquals("53.08300, 8.81300", restoredAlarmTargetCoordinates(state))
        assertEquals("53.08300, 8.81300",
            restoredAlarmTargetCoordinates(state.copy(phase = JourneyPhase.ARRIVED)))
        assertNull(restoredAlarmTargetCoordinates(state.copy(phase = JourneyPhase.DESTINATION_SELECTED)))
    }

    @Test fun mapLinkRequiresValidRestoredAlarmAndUsesOnlySavedCoordinates() {
        val state = JourneyUiState(phase = JourneyPhase.ARMED,
            destination = GeoPoint(53.083, 8.813))
        assertEquals("geo:0,0?q=53.08300,8.81300", restoredAlarmTargetMapUri(state))
        assertEquals("geo:0,0?q=53.08300,8.81300",
            restoredAlarmTargetMapUri(state.copy(phase = JourneyPhase.ARRIVED)))
        assertNull(restoredAlarmTargetMapUri(state.copy(phase = JourneyPhase.DESTINATION_SELECTED)))
        assertNull(restoredAlarmTargetMapUri(state.copy(destination = null)))
        assertNull(restoredAlarmTargetMapUri(state.copy(destination = GeoPoint(Double.NaN, 8.813))))
        assertNull(restoredAlarmTargetMapUri(state.copy(destination = GeoPoint(53.083, 181.0))))
    }

    @Test fun missingOrInvalidTargetNeverProducesCoordinates() {
        val state = JourneyUiState(phase = JourneyPhase.ARMED)
        assertNull(restoredAlarmTargetCoordinates(state))
        listOf(
            GeoPoint(Double.NaN, 8.0),
            GeoPoint(53.0, Double.POSITIVE_INFINITY),
            GeoPoint(90.001, 8.0),
            GeoPoint(53.0, -180.001),
        ).forEach { assertNull(restoredAlarmTargetCoordinates(state.copy(destination = it))) }
    }
}
