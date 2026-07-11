package com.miearn.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.foundation.shape.RoundedCornerShape

val Purple = Color(0xFF6A20AC)
val PurpleDark = Color(0xFF35105F)
val Magenta = Color(0xFFB72A87)
val Sunset = Color(0xFFFF8128)
val Sun = Color(0xFFFFC857)
val WarmWhite = Color(0xFFFFF9F2)
val Ink = Color(0xFF281D2F)
val Mist = Color(0xFFF3EAF7)
val Peach = Color(0xFFFFE1BF)
val Lavender = Color(0xFFE8D3FA)
val Success = Color(0xFF23856D)
val Danger = Color(0xFFD1495B)

private val LightColors = lightColorScheme(
    primary = Purple,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFEBD8FA),
    onPrimaryContainer = PurpleDark,
    secondary = Sunset,
    onSecondary = Color.White,
    secondaryContainer = Peach,
    onSecondaryContainer = Color(0xFF5A2B08),
    tertiary = Magenta,
    background = WarmWhite,
    onBackground = Ink,
    surface = Color(0xFFFFFCFA),
    onSurface = Ink,
    surfaceVariant = Mist,
    onSurfaceVariant = Color(0xFF706477),
    outline = Color(0xFFCFC2D3),
    error = Danger,
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFDAB7F5),
    onPrimary = Color(0xFF3D075F),
    primaryContainer = Color(0xFF552278),
    onPrimaryContainer = Color(0xFFF3E3FF),
    secondary = Color(0xFFFFB56B),
    onSecondary = Color(0xFF4D2600),
    secondaryContainer = Color(0xFF5D3515),
    onSecondaryContainer = Color(0xFFFFDCC1),
    tertiary = Color(0xFFF0A9D5),
    background = Color(0xFF171119),
    onBackground = Color(0xFFF2EAF3),
    surface = Color(0xFF211923),
    onSurface = Color(0xFFF2EAF3),
    surfaceVariant = Color(0xFF312638),
    onSurfaceVariant = Color(0xFFD3C4D6),
    outline = Color(0xFF75667A),
    error = Color(0xFFFFB2BB),
)

private val MIearnTypography = Typography(
    displaySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 36.sp,
        lineHeight = 42.sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 27.sp,
        lineHeight = 34.sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 23.sp,
        lineHeight = 30.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 21.sp,
        lineHeight = 27.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 16.sp,
        lineHeight = 24.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 14.sp,
        lineHeight = 21.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 12.sp,
        lineHeight = 18.sp,
    ),
)

private val MIearnShapes = Shapes(
    extraSmall = RoundedCornerShape(12.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun MIearnTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = MIearnTypography,
        shapes = MIearnShapes,
        content = content,
    )
}
