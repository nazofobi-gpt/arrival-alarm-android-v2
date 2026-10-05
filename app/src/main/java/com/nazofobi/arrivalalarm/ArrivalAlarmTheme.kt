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

internal val MatteLightColors = lightColorScheme(
    primary = Color(0xFF285F9E),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFDCEBFA),
    onPrimaryContainer = Color(0xFF0F355D),
    secondary = Color(0xFF3C7B67),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFDCEDE6),
    onSecondaryContainer = Color(0xFF173D31),
    tertiary = Color(0xFFD96B42),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFBE4D9),
    onTertiaryContainer = Color(0xFF6A2C17),
    background = Color(0xFFF7F5F1),
    onBackground = Color(0xFF14213D),
    surface = Color(0xFFFFFDF9),
    onSurface = Color(0xFF14213D),
    surfaceVariant = Color(0xFFF0F2F5),
    onSurfaceVariant = Color(0xFF596779),
    outline = Color(0xFF8792A3),
    outlineVariant = Color(0xFFD9DEE6),
    error = Color(0xFFC5484B),
    onError = Color(0xFFFFFFFF),
)

internal val MatteDarkColors = darkColorScheme(
    primary = Color(0xFF6DA9FF),
    onPrimary = Color(0xFF0B2848),
    primaryContainer = Color(0xFF183D67),
    onPrimaryContainer = Color(0xFFD8E8FF),
    secondary = Color(0xFF74C8AA),
    onSecondary = Color(0xFF0F3328),
    secondaryContainer = Color(0xFF20483C),
    onSecondaryContainer = Color(0xFFD6F1E7),
    tertiary = Color(0xFFFF8A68),
    onTertiary = Color(0xFF4C1F13),
    tertiaryContainer = Color(0xFF643325),
    onTertiaryContainer = Color(0xFFFFDDD2),
    background = Color(0xFF0C1724),
    onBackground = Color(0xFFF2F5FA),
    surface = Color(0xFF122235),
    onSurface = Color(0xFFF2F5FA),
    surfaceVariant = Color(0xFF1B2D42),
    onSurfaceVariant = Color(0xFFB7C4D4),
    outline = Color(0xFF75869B),
    outlineVariant = Color(0xFF2C4057),
    error = Color(0xFFFF8A8E),
    onError = Color(0xFF4C171A),
)

private val ArrivalShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

private val ArrivalTypography = Typography(
    headlineLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 30.sp,
        lineHeight = 36.sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 25.sp,
        lineHeight = 31.sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp,
        lineHeight = 26.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp,
    ),
    titleSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
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
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 17.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 18.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
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
