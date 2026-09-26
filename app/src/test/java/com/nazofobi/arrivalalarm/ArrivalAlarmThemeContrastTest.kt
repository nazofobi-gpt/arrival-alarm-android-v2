package com.nazofobi.arrivalalarm

import androidx.compose.ui.graphics.Color
import kotlin.math.pow
import org.junit.Assert.assertTrue
import org.junit.Test

class ArrivalAlarmThemeContrastTest {
    @Test
    fun semanticStatusColorsMeetNormalTextContrastOnSurfaces() {
        val pairs = listOf(
            "light primary" to (MatteLightColors.primary to MatteLightColors.surface),
            "light secondary" to (MatteLightColors.secondary to MatteLightColors.surface),
            "light tertiary" to (MatteLightColors.tertiary to MatteLightColors.surface),
            "light error" to (MatteLightColors.error to MatteLightColors.surface),
            "light onSurfaceVariant" to (MatteLightColors.onSurfaceVariant to MatteLightColors.surface),
            "dark primary" to (MatteDarkColors.primary to MatteDarkColors.surface),
            "dark secondary" to (MatteDarkColors.secondary to MatteDarkColors.surface),
            "dark tertiary" to (MatteDarkColors.tertiary to MatteDarkColors.surface),
            "dark error" to (MatteDarkColors.error to MatteDarkColors.surface),
            "dark onSurfaceVariant" to (MatteDarkColors.onSurfaceVariant to MatteDarkColors.surface),
        )

        pairs.forEach { (label, colors) ->
            val ratio = contrastRatio(colors.first, colors.second)
            assertTrue("$label contrast was $ratio; expected >= 4.5", ratio >= 4.5)
        }
    }

    @Test
    fun primaryOutlinesMeetNonTextContrastOnSurfaces() {
        val lightRatio = contrastRatio(MatteLightColors.outline, MatteLightColors.surface)
        val darkRatio = contrastRatio(MatteDarkColors.outline, MatteDarkColors.surface)

        assertTrue("light outline contrast was $lightRatio; expected >= 3.0", lightRatio >= 3.0)
        assertTrue("dark outline contrast was $darkRatio; expected >= 3.0", darkRatio >= 3.0)
    }

    private fun contrastRatio(foreground: Color, background: Color): Double {
        val foregroundLuminance = relativeLuminance(foreground)
        val backgroundLuminance = relativeLuminance(background)
        val lighter = maxOf(foregroundLuminance, backgroundLuminance)
        val darker = minOf(foregroundLuminance, backgroundLuminance)
        return (lighter + 0.05) / (darker + 0.05)
    }

    private fun relativeLuminance(color: Color): Double {
        fun linearized(component: Float): Double {
            val value = component.toDouble()
            return if (value <= 0.04045) {
                value / 12.92
            } else {
                ((value + 0.055) / 1.055).pow(2.4)
            }
        }

        return 0.2126 * linearized(color.red) +
            0.7152 * linearized(color.green) +
            0.0722 * linearized(color.blue)
    }
}
