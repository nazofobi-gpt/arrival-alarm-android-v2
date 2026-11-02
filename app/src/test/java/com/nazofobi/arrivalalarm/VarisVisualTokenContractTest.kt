package com.nazofobi.arrivalalarm

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Source of truth: docs/design/varis_design_tokens_v1.json.
 * This verifies theme-token parity, NOT Figma/device visual acceptance (G-167).
 */
class VarisVisualTokenContractTest {
    @Test
    fun lightPaletteMatchesVisualSourceOfTruth() {
        assertEquals(Color(0xFFF7F5F1), MatteLightColors.background)
        assertEquals(Color(0xFFFFFDF9), MatteLightColors.surface)
        assertEquals(Color(0xFFF0F2F5), MatteLightColors.surfaceVariant)
        assertEquals(Color(0xFF14213D), MatteLightColors.onSurface)
        assertEquals(Color(0xFF596779), MatteLightColors.onSurfaceVariant)
        assertEquals(Color(0xFF285F9E), MatteLightColors.primary)
        assertEquals(Color(0xFFDCEBFA), MatteLightColors.primaryContainer)
        assertEquals(Color(0xFF0F355D), MatteLightColors.onPrimaryContainer)
        assertEquals(Color(0xFF3C7B67), MatteLightColors.secondary)
        assertEquals(Color(0xFFDCEDE6), MatteLightColors.secondaryContainer)
        assertEquals(Color(0xFFB85B38), MatteLightColors.tertiary)
        assertEquals(Color(0xFFFBE4D9), MatteLightColors.tertiaryContainer)
        assertEquals(Color(0xFFC5484B), MatteLightColors.error)
        assertEquals(Color(0xFFFCE3E3), MatteLightColors.errorContainer)
        assertEquals(Color(0xFFD9DEE6), MatteLightColors.outlineVariant)
    }

    @Test
    fun darkPaletteMatchesVisualSourceOfTruth() {
        assertEquals(Color(0xFF0C1724), MatteDarkColors.background)
        assertEquals(Color(0xFF122235), MatteDarkColors.surface)
        assertEquals(Color(0xFF1B2D42), MatteDarkColors.surfaceVariant)
        assertEquals(Color(0xFFF2F5FA), MatteDarkColors.onSurface)
        assertEquals(Color(0xFFB7C4D4), MatteDarkColors.onSurfaceVariant)
        assertEquals(Color(0xFF6DA9FF), MatteDarkColors.primary)
        assertEquals(Color(0xFF0F355D), MatteDarkColors.primaryContainer)
        assertEquals(Color(0xFFDCEBFA), MatteDarkColors.onPrimaryContainer)
        assertEquals(Color(0xFF74C8AA), MatteDarkColors.secondary)
        assertEquals(Color(0xFF173D31), MatteDarkColors.secondaryContainer)
        assertEquals(Color(0xFFFF9A77), MatteDarkColors.tertiary)
        assertEquals(Color(0xFF6A2C17), MatteDarkColors.tertiaryContainer)
        assertEquals(Color(0xFFFF8A8E), MatteDarkColors.error)
        assertEquals(Color(0xFF4C171A), MatteDarkColors.errorContainer)
        assertEquals(Color(0xFF2C4057), MatteDarkColors.outlineVariant)
    }

    @Test
    fun typographyMatchesMobileTransitScale() {
        assertEquals(FontFamily.SansSerif, ArrivalTypography.bodyLarge.fontFamily)
        assertEquals(32.sp, ArrivalTypography.displaySmall.fontSize)
        assertEquals(38.sp, ArrivalTypography.displaySmall.lineHeight)
        assertEquals(26.sp, ArrivalTypography.headlineLarge.fontSize)
        assertEquals(32.sp, ArrivalTypography.headlineLarge.lineHeight)
        assertEquals(20.sp, ArrivalTypography.headlineSmall.fontSize)
        assertEquals(26.sp, ArrivalTypography.headlineSmall.lineHeight)
        assertEquals(17.sp, ArrivalTypography.titleMedium.fontSize)
        assertEquals(22.sp, ArrivalTypography.titleMedium.lineHeight)
        assertEquals(16.sp, ArrivalTypography.bodyMedium.fontSize)
        assertEquals(22.sp, ArrivalTypography.bodyMedium.lineHeight)
        assertEquals(13.sp, ArrivalTypography.bodySmall.fontSize)
        assertEquals(18.sp, ArrivalTypography.bodySmall.lineHeight)
        assertEquals(13.sp, ArrivalTypography.labelLarge.fontSize)
    }
}
