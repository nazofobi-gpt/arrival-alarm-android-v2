package com.nazofobi.arrivalalarm

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OnboardingReplayGateTest {
    @Test fun replayAvailableWhenNoJourneyIsArmed() {
        assertTrue(canReplayOnboarding(JourneyPhase.EMPTY))
    }

    @Test fun replayCannotHideArmedOrArrivalAlarmControls() {
        assertFalse(canReplayOnboarding(JourneyPhase.ARMED))
        assertFalse(canReplayOnboarding(JourneyPhase.ARRIVED))
    }
}
