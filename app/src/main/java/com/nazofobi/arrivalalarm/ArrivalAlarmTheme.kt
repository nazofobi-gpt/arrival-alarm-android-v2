package com.nazofobi.arrivalalarm

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

enum class ArrivalThemeMode {
    SYSTEM,
    LIGHT,
    DARK,
}

// Source: docs/design/varis_design_tokens_v1.json (Varış V2 visual contract v1.0).
// Full-screen composition / real-device visual acceptance remains gated by G-167.
internal val MatteLightColors = lightColorScheme(
    primary = Color(0xFF285F9E),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFDCEBFA),
    onPrimaryContainer = Color(0xFF0F355D),
    secondary = Color(0xFF3C7B67),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFDCEDE6),
    onSecondaryContainer = Color(0xFF173D31),
    tertiary = Color(0xFFB85B38),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFBE4D9),
    onTertiaryContainer = Color(0xFF6A2C17),
    background = Color(0xFFF7F5F1),
    onBackground = Color(0xFF14213D),
    surface = Color(0xFFFFFDF9),
    onSurface = Color(0xFF14213D),
    surfaceVariant = Color(0xFFF0F2F5),
    onSurfaceVariant = Color(0xFF596779),
    // Outline is deliberately stronger than outlineVariant for WCAG 3:1.
    outline = Color(0xFF738193),
    outlineVariant = Color(0xFFD9DEE6),
    error = Color(0xFFC5484B),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFCE3E3),
)

internal val MatteDarkColors = darkColorScheme(
    primary = Color(0xFF6DA9FF),
    onPrimary = Color(0xFF0C1724),
    primaryContainer = Color(0xFF0F355D),
    onPrimaryContainer = Color(0xFFDCEBFA),
    secondary = Color(0xFF74C8AA),
    onSecondary = Color(0xFF0C1724),
    secondaryContainer = Color(0xFF173D31),
    onSecondaryContainer = Color(0xFFDCEDE6),
    tertiary = Color(0xFFFF9A77),
    onTertiary = Color(0xFF0C1724),
    tertiaryContainer = Color(0xFF6A2C17),
    onTertiaryContainer = Color(0xFFFFE4D9),
    background = Color(0xFF0C1724),
    onBackground = Color(0xFFF2F5FA),
    surface = Color(0xFF122235),
    onSurface = Color(0xFFF2F5FA),
    surfaceVariant = Color(0xFF1B2D42),
    onSurfaceVariant = Color(0xFFB7C4D4),
    outline = Color(0xFF8394A9),
    outlineVariant = Color(0xFF2C4057),
    error = Color(0xFFFF8A8E),
    onError = Color(0xFF0C1724),
    errorContainer = Color(0xFF4C171A),
)

private val ArrivalShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(24.dp),
)

// Android SansSerif resolves to Roboto on standard devices; these styles
// materialize the visual token scale without changing any screen hierarchy.
internal val ArrivalTypography = Typography(
    displaySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 32.sp,
        lineHeight = 38.sp,
    ),
    headlineLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 26.sp,
        lineHeight = 32.sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp,
        lineHeight = 26.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp,
        lineHeight = 26.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 17.sp,
        lineHeight = 22.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 22.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 22.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 18.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        lineHeight = 18.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        lineHeight = 18.sp,
    ),
)

@Composable
fun ArrivalAlarmTheme(
    mode: ArrivalThemeMode = ArrivalThemeMode.SYSTEM,
    content: @Composable () -> Unit,
) {
    val darkTheme = when (mode) {
        ArrivalThemeMode.SYSTEM -> isSystemInDarkTheme()
        ArrivalThemeMode.LIGHT -> false
        ArrivalThemeMode.DARK -> true
    }

    MaterialTheme(
        colorScheme = if (darkTheme) MatteDarkColors else MatteLightColors,
        shapes = ArrivalShapes,
        typography = ArrivalTypography,
        content = content,
    )
}
