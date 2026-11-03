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
    @Test fun idleFirstRunAndOptionalReplayRemainAvailable() {
        assertTrue(shouldShowOnboarding(isComplete = false, replayRequested = false, phase = JourneyPhase.EMPTY))
        assertTrue(shouldShowOnboarding(isComplete = true, replayRequested = true, phase = JourneyPhase.EMPTY))
        assertFalse(shouldShowOnboarding(isComplete = true, replayRequested = false, phase = JourneyPhase.EMPTY))
    }

    @Test fun newlyArmedOrRestoredAlarmAlwaysOverridesOnboarding() {
        for (phase in listOf(JourneyPhase.ARMED, JourneyPhase.ARRIVED)) {
            assertFalse(shouldShowOnboarding(isComplete = true, replayRequested = true, phase = phase))
            assertFalse(shouldShowOnboarding(isComplete = false, replayRequested = false, phase = phase))
        }
    }

}
