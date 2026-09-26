package com.nazofobi.arrivalalarm

import org.junit.Assert.assertFalse
import androidx.compose.ui.graphics.Color
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

    @Test fun mattePaletteNormalTextContrastMeetsWcagAA() {
        val pairs = listOf(
            MatteLightColors.onBackground to MatteLightColors.background,
            MatteLightColors.onSurface to MatteLightColors.surface,
            MatteLightColors.onSurfaceVariant to MatteLightColors.surfaceVariant,
            MatteLightColors.onPrimary to MatteLightColors.primary,
            MatteLightColors.onPrimaryContainer to MatteLightColors.primaryContainer,
            MatteLightColors.onSecondary to MatteLightColors.secondary,
            MatteLightColors.secondary to MatteLightColors.surface,
            MatteLightColors.secondary to MatteLightColors.background,
            MatteLightColors.onSecondaryContainer to MatteLightColors.secondaryContainer,
            MatteLightColors.onTertiary to MatteLightColors.tertiary,
            MatteLightColors.tertiary to MatteLightColors.surface,
            MatteLightColors.tertiary to MatteLightColors.background,
            MatteLightColors.onTertiaryContainer to MatteLightColors.tertiaryContainer,
            MatteLightColors.onError to MatteLightColors.error,
            MatteLightColors.error to MatteLightColors.surface,
            MatteLightColors.error to MatteLightColors.background,
            MatteDarkColors.onBackground to MatteDarkColors.background,
            MatteDarkColors.onSurface to MatteDarkColors.surface,
            MatteDarkColors.onSurfaceVariant to MatteDarkColors.surfaceVariant,
            MatteDarkColors.onPrimary to MatteDarkColors.primary,
            MatteDarkColors.onPrimaryContainer to MatteDarkColors.primaryContainer,
            MatteDarkColors.onSecondary to MatteDarkColors.secondary,
            MatteDarkColors.onSecondaryContainer to MatteDarkColors.secondaryContainer,
            MatteDarkColors.onTertiary to MatteDarkColors.tertiary,
            MatteDarkColors.onTertiaryContainer to MatteDarkColors.tertiaryContainer,
            MatteDarkColors.onError to MatteDarkColors.error,
        )
        pairs.forEach { (foreground, background) ->
            assertTrue(
                "contrast=${contrastRatio(foreground, background)} for $foreground on $background",
                contrastRatio(foreground, background) >= 4.5,
            )
        }
    }

    private fun contrastRatio(first: Color, second: Color): Double {
        val l1 = relativeLuminance(first)
        val l2 = relativeLuminance(second)
        val lighter = maxOf(l1, l2)
        val darker = minOf(l1, l2)
        return (lighter + 0.05) / (darker + 0.05)
    }

    private fun relativeLuminance(color: Color): Double {
        fun linear(channel: Float): Double {
            val value = channel.toDouble()
            return if (value <= 0.04045) value / 12.92
            else Math.pow((value + 0.055) / 1.055, 2.4)
        }
        return 0.2126 * linear(color.red) +
            0.7152 * linear(color.green) +
            0.0722 * linear(color.blue)
    }
}
