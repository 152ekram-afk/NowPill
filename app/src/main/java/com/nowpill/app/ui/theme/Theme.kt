package com.nowpill.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.material3.Typography

// Expressive, high-chroma palette (Material 3 Expressive leans into bold,
// saturated tonal accents rather than flat neutral surfaces).
val PillPrimary = Color(0xFFB9C3FF)
val PillOnPrimary = Color(0xFF1D2A6B)
val PillSecondary = Color(0xFFFFD8A8)
val PillSurface = Color(0xFF141319)
val PillSurfaceBright = Color(0xFF211F2B)
val PillOnSurface = Color(0xFFEAE6F2)
val PillAccentGreen = Color(0xFF8CF2C5)
val PillAccentRed = Color(0xFFFFB4AB)

private val NowPillColorScheme = darkColorScheme(
    primary = PillPrimary,
    onPrimary = PillOnPrimary,
    secondary = PillSecondary,
    surface = PillSurface,
    surfaceVariant = PillSurfaceBright,
    onSurface = PillOnSurface,
    background = PillSurface,
    onBackground = PillOnSurface,
)

val ExpressiveTypography = Typography(
    titleMedium = TextStyle(fontWeight = FontWeight.Bold, fontSize = 16.sp()),
    bodyMedium = TextStyle(fontWeight = FontWeight.Medium, fontSize = 13.sp()),
)

private fun Int.sp() = androidx.compose.ui.unit.TextUnit(
    this.toFloat(), androidx.compose.ui.unit.TextUnitType.Sp
)

@Composable
fun NowPillTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = NowPillColorScheme,
        typography = ExpressiveTypography,
        content = content
    )
}
