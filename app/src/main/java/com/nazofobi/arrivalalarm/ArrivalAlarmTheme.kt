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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object ArrivalSpacing {
    val xSmall = 4.dp
    val small = 8.dp
    val medium = 12.dp
    val large = 16.dp
    val xLarge = 24.dp
    val section = 32.dp
}

object ArrivalElevation {
    val flat = 0.dp
    val raised = 1.dp
}

enum class ArrivalThemeMode { SYSTEM, LIGHT, DARK }

internal val MatteLightColors = lightColorScheme(
    primary = Color(0xFF355F7C), onPrimary = Color(0xFFF7FBFD),
    primaryContainer = Color(0xFFDCE8F0), onPrimaryContainer = Color(0xFF173446),
    secondary = Color(0xFF596F63), onSecondary = Color(0xFFF8FBF8),
    secondaryContainer = Color(0xFFDFE9E2), onSecondaryContainer = Color(0xFF263B30),
    tertiary = Color(0xFF925D4C), onTertiary = Color(0xFFFFF8F5),
    tertiaryContainer = Color(0xFFF1E2DA), onTertiaryContainer = Color(0xFF4B2D23),
    background = Color(0xFFF4F2ED), onBackground = Color(0xFF17212A),
    surface = Color(0xFFFCFBF8), onSurface = Color(0xFF17212A),
    surfaceVariant = Color(0xFFE9E7E1), onSurfaceVariant = Color(0xFF56616A),
    outline = Color(0xFF7A8288), outlineVariant = Color(0xFFD3D2CD),
    error = Color(0xFFA94E4E), onError = Color(0xFFFFF7F7),
)

internal val MatteDarkColors = darkColorScheme(
    primary = Color(0xFF91B4CD), onPrimary = Color(0xFF102838),
    primaryContainer = Color(0xFF27475D), onPrimaryContainer = Color(0xFFDDECF5),
    secondary = Color(0xFF91AD9B), onSecondary = Color(0xFF142B20),
    secondaryContainer = Color(0xFF2D4639), onSecondaryContainer = Color(0xFFDCE9E0),
    tertiary = Color(0xFFD09A84), onTertiary = Color(0xFF3B2018),
    tertiaryContainer = Color(0xFF5B382C), onTertiaryContainer = Color(0xFFF2DED5),
    background = Color(0xFF0E1720), onBackground = Color(0xFFE8EDF1),
    surface = Color(0xFF16222D), onSurface = Color(0xFFE8EDF1),
    surfaceVariant = Color(0xFF22313D), onSurfaceVariant = Color(0xFFB9C3CA),
    outline = Color(0xFF778692), outlineVariant = Color(0xFF344451),
    error = Color(0xFFE28B8B), onError = Color(0xFF431B1B),
)

private val ArrivalShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(30.dp),
)

private val ArrivalTypography = Typography(
    displaySmall = TextStyle(fontSize = 36.sp, lineHeight = 42.sp, fontWeight = FontWeight.Bold),
    headlineLarge = TextStyle(fontSize = 30.sp, lineHeight = 36.sp, fontWeight = FontWeight.Bold),
    headlineMedium = TextStyle(fontSize = 26.sp, lineHeight = 32.sp, fontWeight = FontWeight.SemiBold),
    headlineSmall = TextStyle(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold),
    titleLarge = TextStyle(fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.Medium),
    titleSmall = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp, fontWeight = FontWeight.Normal),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 21.sp, fontWeight = FontWeight.Normal),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 18.sp, fontWeight = FontWeight.Normal),
    labelLarge = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold),
    labelMedium = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium),
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
