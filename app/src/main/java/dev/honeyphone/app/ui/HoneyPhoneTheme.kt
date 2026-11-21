package dev.honeyphone.app.ui

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.*
import androidx.compose.ui.unit.sp
import dev.honeyphone.app.R

object HoneyStyle {
    val Background = Color(0xFF000000)
    val Surface = Color(0xFF090A0B)
    val Ink = Color(0xFFF4F4F4)
    val Muted = Color(0xFFB4B7BA)
    val Lime = Color(0xFFFFFFFF)
    val Aqua = Color(0xFFD0D3D6)
    val Amber = Color(0xFFF5B700)
    val Warning = Color(0xFFFFD166)
    val Line = Color(0xFF242830)
    val Mono = FontFamily(
        Font(R.font.jetbrains_mono_variable, FontWeight.Normal),
        Font(R.font.jetbrains_mono_variable, FontWeight.Medium),
    )
    val Dots = FontFamily(Font(R.font.doto, FontWeight.Bold))
    val Sans = FontFamily(
        Font(R.font.plus_jakarta_sans_variable, FontWeight.Normal),
        Font(R.font.plus_jakarta_sans_variable, FontWeight.Medium),
        Font(R.font.plus_jakarta_sans_variable, FontWeight.SemiBold),
        Font(R.font.plus_jakarta_sans_variable, FontWeight.Bold),
    )
    val Display = FontFamily(
        Font(R.font.unbounded_variable, FontWeight.Normal),
        Font(R.font.unbounded_variable, FontWeight.SemiBold),
        Font(R.font.unbounded_variable, FontWeight.Bold),
    )
}

private val HoneyColors = darkColorScheme(
    primary = HoneyStyle.Lime, onPrimary = HoneyStyle.Background,
    secondary = HoneyStyle.Aqua, onSecondary = HoneyStyle.Background,
    tertiary = HoneyStyle.Amber, onTertiary = HoneyStyle.Background,
    background = HoneyStyle.Background, onBackground = HoneyStyle.Ink,
    surface = HoneyStyle.Surface, onSurface = HoneyStyle.Ink,
    surfaceVariant = Color(0xFF121416), onSurfaceVariant = HoneyStyle.Muted,
    outline = HoneyStyle.Line, error = Color(0xFFFFA99B),
    surfaceContainer = HoneyStyle.Surface,
)
private val HoneyType = Typography(
    headlineLarge = TextStyle(fontFamily=HoneyStyle.Display, fontWeight=FontWeight.SemiBold, fontSize=34.sp, lineHeight=42.sp, letterSpacing=(-1).sp),
    headlineMedium = TextStyle(fontFamily=HoneyStyle.Display, fontWeight=FontWeight.SemiBold, fontSize=26.sp, lineHeight=34.sp, letterSpacing=(-0.6).sp),
    headlineSmall = TextStyle(fontFamily=HoneyStyle.Display, fontWeight=FontWeight.SemiBold, fontSize=22.sp, lineHeight=30.sp),
    titleLarge = TextStyle(fontFamily=HoneyStyle.Display, fontWeight=FontWeight.SemiBold, fontSize=18.sp, lineHeight=26.sp),
    titleMedium = TextStyle(fontFamily=HoneyStyle.Sans, fontWeight=FontWeight.SemiBold, fontSize=16.sp, lineHeight=24.sp),
    bodyLarge = TextStyle(fontFamily=HoneyStyle.Sans, fontSize=15.sp, lineHeight=24.sp),
    bodyMedium = TextStyle(fontFamily=HoneyStyle.Sans, fontSize=14.sp, lineHeight=22.sp),
    bodySmall = TextStyle(fontFamily=HoneyStyle.Sans, fontSize=12.sp, lineHeight=19.sp),
    labelLarge = TextStyle(fontFamily=HoneyStyle.Display, fontWeight=FontWeight.SemiBold, fontSize=12.sp, lineHeight=18.sp),
    labelMedium = TextStyle(fontFamily=HoneyStyle.Mono, fontSize=11.sp, lineHeight=16.sp),
    labelSmall = TextStyle(fontFamily=HoneyStyle.Mono, fontSize=10.sp, lineHeight=15.sp),
)

@Composable
fun HoneyPhoneTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme=HoneyColors, typography=HoneyType, content=content)
}
