package com.xiaohei.tv.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val Accent = Color(0xFFFF5A36)
val Gold = Color(0xFFFFC857)
val BgDark = Color(0xFF0B0D10)
val SurfaceDark = Color(0xFF161A21)
val SurfaceHigh = Color(0xFF222833)
val TextMain = Color(0xFFF4F6F8)
val TextDim = Color(0xFF9CA5B2)
val Divider = Color(0xFF2A313C)
val Success = Color(0xFF4CD08A)
val Danger = Color(0xFFFF6B6B)

private val Colors = darkColorScheme(
    primary = Accent,
    onPrimary = Color.White,
    secondary = Gold,
    onSecondary = BgDark,
    background = BgDark,
    onBackground = TextMain,
    surface = SurfaceDark,
    onSurface = TextMain,
    surfaceVariant = SurfaceHigh,
    onSurfaceVariant = TextDim,
    error = Danger,
)

private val TvTypography = Typography(
    displaySmall = TextStyle(fontSize = 34.sp, fontWeight = FontWeight.Bold),
    headlineLarge = TextStyle(fontSize = 30.sp, fontWeight = FontWeight.Bold),
    headlineMedium = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.SemiBold),
    titleLarge = TextStyle(fontSize = 21.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.Medium),
    bodyLarge = TextStyle(fontSize = 16.sp),
    bodyMedium = TextStyle(fontSize = 14.sp),
    labelLarge = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Medium),
    labelMedium = TextStyle(fontSize = 13.sp),
)

@Composable
fun XiaoheiTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = Colors,
        typography = TvTypography,
        content = content,
    )
}
