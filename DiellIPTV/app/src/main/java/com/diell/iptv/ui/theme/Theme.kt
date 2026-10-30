package com.diell.iptv.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Diell palette: sun yellow on a deep evergreen night.
val Sun = Color(0xFFF8C400)
val SunDim = Color(0xFF8A6F12)
val Night = Color(0xFF0E1412)
val Panel = Color(0xFF16201C)
val PanelHigh = Color(0xFF1F2B26)
val Ink = Color(0xFFEFF3F0)
val InkMuted = Color(0xFF9AA8A1)
val Danger = Color(0xFFF2766B)

private val colors = darkColorScheme(
    primary = Sun,
    onPrimary = Night,
    secondary = Sun,
    onSecondary = Night,
    background = Night,
    onBackground = Ink,
    surface = Panel,
    onSurface = Ink,
    surfaceVariant = PanelHigh,
    onSurfaceVariant = InkMuted,
    surfaceContainer = Panel,
    surfaceContainerHigh = PanelHigh,
    surfaceContainerHighest = PanelHigh,
    outline = Color(0xFF34443D),
    error = Danger,
)

private val type = Typography(
    headlineSmall = TextStyle(fontWeight = FontWeight.ExtraBold, fontSize = 24.sp),
    titleLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 20.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 16.sp),
    bodyMedium = TextStyle(fontSize = 14.sp),
    labelSmall = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 11.sp, letterSpacing = 0.6.sp),
)

@Composable
fun DiellTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = colors, typography = type, content = content)
}
