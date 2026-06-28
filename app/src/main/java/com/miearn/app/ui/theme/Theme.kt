package com.miearn.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val Purple = Color(0xFF5A189A)
val PurpleDark = Color(0xFF35105F)
val Magenta = Color(0xFFB72A87)
val Sunset = Color(0xFFFF8A2A)
val Sun = Color(0xFFFFC857)
val WarmWhite = Color(0xFFFFFBF4)
val Ink = Color(0xFF211A2A)
val Mist = Color(0xFFF1EAF5)
val Success = Color(0xFF23856D)
val Danger = Color(0xFFD1495B)

private val MIearnColors = lightColorScheme(
    primary = Purple,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFECD9FF),
    onPrimaryContainer = PurpleDark,
    secondary = Sunset,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFE3C2),
    onSecondaryContainer = Color(0xFF522400),
    tertiary = Magenta,
    background = WarmWhite,
    onBackground = Ink,
    surface = Color.White,
    onSurface = Ink,
    surfaceVariant = Mist,
    error = Danger,
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
        fontSize = 26.sp,
        lineHeight = 32.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 21.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 16.sp,
        lineHeight = 24.sp,
    ),
)

@Composable
fun MIearnTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = MIearnColors,
        typography = MIearnTypography,
        content = content,
    )
}

