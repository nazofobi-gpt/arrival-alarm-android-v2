package com.nazofobi.arrivalalarm

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ArrivalAlarmThemeTest {
    @Test fun systemModeTracksAndroidAppearance() {
        assertFalse(resolveArrivalDarkTheme(ArrivalThemeMode.SYSTEM, systemDark = false))
        assertTrue(resolveArrivalDarkTheme(ArrivalThemeMode.SYSTEM, systemDark = true))
    }

    @Test fun manualModesOverrideAndroidAppearance() {
        assertFalse(resolveArrivalDarkTheme(ArrivalThemeMode.LIGHT, systemDark = true))
        assertTrue(resolveArrivalDarkTheme(ArrivalThemeMode.DARK, systemDark = false))
    }
}
