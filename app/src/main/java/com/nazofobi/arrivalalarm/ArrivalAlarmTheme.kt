package com.nazofobi.arrivalalarm

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

enum class ArrivalThemeMode {
    SYSTEM,
    LIGHT,
    DARK,
}

private val MatteLightColors = lightColorScheme(
    primary = Color(0xFF355F7C),
    onPrimary = Color(0xFFF7FBFD),
    primaryContainer = Color(0xFFDCE8F0),
    onPrimaryContainer = Color(0xFF173446),
    secondary = Color(0xFF647F70),
    onSecondary = Color(0xFFF8FBF8),
    secondaryContainer = Color(0xFFDFE9E2),
    onSecondaryContainer = Color(0xFF263B30),
    tertiary = Color(0xFFA46F5C),
    onTertiary = Color(0xFFFFF8F5),
    tertiaryContainer = Color(0xFFF1E2DA),
    onTertiaryContainer = Color(0xFF4B2D23),
    background = Color(0xFFF4F2ED),
    onBackground = Color(0xFF17212A),
    surface = Color(0xFFFCFBF8),
    onSurface = Color(0xFF17212A),
    surfaceVariant = Color(0xFFE9E7E1),
    onSurfaceVariant = Color(0xFF56616A),
    outline = Color(0xFFA7ADB1),
    outlineVariant = Color(0xFFD3D2CD),
    error = Color(0xFFB85E5E),
    onError = Color(0xFFFFF7F7),
)

private val MatteDarkColors = darkColorScheme(
    primary = Color(0xFF91B4CD),
    onPrimary = Color(0xFF102838),
    primaryContainer = Color(0xFF27475D),
    onPrimaryContainer = Color(0xFFDDECF5),
    secondary = Color(0xFF91AD9B),
    onSecondary = Color(0xFF142B20),
    secondaryContainer = Color(0xFF2D4639),
    onSecondaryContainer = Color(0xFFDCE9E0),
    tertiary = Color(0xFFD09A84),
    onTertiary = Color(0xFF3B2018),
    tertiaryContainer = Color(0xFF5B382C),
    onTertiaryContainer = Color(0xFFF2DED5),
    background = Color(0xFF0E1720),
    onBackground = Color(0xFFE8EDF1),
    surface = Color(0xFF16222D),
    onSurface = Color(0xFFE8EDF1),
    surfaceVariant = Color(0xFF22313D),
    onSurfaceVariant = Color(0xFFB9C3CA),
    outline = Color(0xFF778692),
    outlineVariant = Color(0xFF344451),
    error = Color(0xFFE28B8B),
    onError = Color(0xFF431B1B),
)

private val ArrivalShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(30.dp),
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
        typography = Typography(),
        content = content,
    )
}
