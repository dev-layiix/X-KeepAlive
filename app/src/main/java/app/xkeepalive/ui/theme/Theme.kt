package app.xkeepalive.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val Ink = Color(0xFF0B0F14)
val Panel = Color(0xFF151B24)
val PanelHigh = Color(0xFF1E2733)
val Accent = Color(0xFF4DA3FF)
val Mist = Color(0xFF9AA8B8)
val Paper = Color(0xFFE8EEF4)
val Line = Color(0xFF2C3A4A)
val Danger = Color(0xFFFF5D73)
val Warning = Color(0xFFF0B45A)
val Ok = Color(0xFF3EE0A0)

private val Colors = darkColorScheme(
    primary = Accent,
    onPrimary = Color(0xFF041018),
    primaryContainer = Color(0xFF1B4F86),
    onPrimaryContainer = Paper,
    secondary = Color(0xFF7EB6FF),
    onSecondary = Color(0xFF041018),
    secondaryContainer = Color(0xFF243044),
    onSecondaryContainer = Paper,
    background = Ink,
    onBackground = Paper,
    surface = Panel,
    onSurface = Paper,
    surfaceVariant = PanelHigh,
    onSurfaceVariant = Mist,
    surfaceContainer = Panel,
    surfaceContainerHigh = PanelHigh,
    surfaceContainerHighest = Color(0xFF283444),
    error = Danger,
    onError = Ink,
    outline = Line,
    outlineVariant = Color(0xFF243040),
)

private val Type = Typography(
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 28.sp,
        letterSpacing = 0.sp,
    ),
    titleLarge = TextStyle(fontWeight = FontWeight.Medium, fontSize = 22.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.Medium, fontSize = 16.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontSize = 13.sp, lineHeight = 18.sp, color = Mist),
    labelLarge = TextStyle(fontWeight = FontWeight.Medium, fontSize = 14.sp),
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun XBackgroundTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = Colors,
        typography = Type,
        shapes = AppShapes,
        content = content,
    )
}
