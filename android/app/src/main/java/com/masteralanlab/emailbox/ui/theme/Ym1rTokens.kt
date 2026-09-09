package com.masteralanlab.emailbox.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Ym1r 集中语义色彩令牌
 */
data class Ym1rColors(
    val background: Color,
    val surface: Color,
    val surfaceRaised: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val separator: Color,
    val accent: Color,
    val success: Color,
    val warning: Color,
    val danger: Color,
    val disabled: Color,
    val selection: Color,
    val isDark: Boolean,
)

// 风格 A：“清透”默认视觉体系 (Translucent / Clear)
val Ym1rClearLightColors = Ym1rColors(
    background = Color(0xFFF2F2F7),
    surface = Color(0xFFFFFFFF),
    surfaceRaised = Color(0xFFE9EDF3),
    textPrimary = Color(0xFF111827),
    textSecondary = Color(0xFF586476),
    separator = Color(0xFFE5E7EB),
    accent = Color(0xFF0066D6),
    success = Color(0xFF237A45),
    warning = Color(0xFF9A5B00),
    danger = Color(0xFFC32F3B),
    disabled = Color(0xFFD1D5DB),
    selection = Color(0xFF0066D6).copy(alpha = 0.12f),
    isDark = false,
)

val Ym1rClearDarkColors = Ym1rColors(
    background = Color(0xFF000000),
    surface = Color(0xFF1C1C1E),
    surfaceRaised = Color(0xFF2C2C2E),
    textPrimary = Color(0xFFF9FAFB),
    textSecondary = Color(0xFF9CA3AF),
    separator = Color(0xFF2C2C2E),
    accent = Color(0xFF7AB8FF),
    success = Color(0xFF70D89D),
    warning = Color(0xFFF1BD63),
    danger = Color(0xFFFF8C94),
    disabled = Color(0xFF4B5563),
    selection = Color(0xFF7AB8FF).copy(alpha = 0.18f),
    isDark = true,
)

// 风格 B：“经典”视觉体系 (Classic)
val Ym1rClassicLightColors = Ym1rColors(
    background = Color(0xFFFBF8F3),
    surface = Color(0xFFFFFFFF),
    surfaceRaised = Color(0xFFF6EFE6),
    textPrimary = Color(0xFF221F1D),
    textSecondary = Color(0xFF6E6155),
    separator = Color(0xFFEADBCE),
    accent = Color(0xFFA84D2F),
    success = Color(0xFF237A45),
    warning = Color(0xFF9A5B00),
    danger = Color(0xFFC32F3B),
    disabled = Color(0xFFC7BDB3),
    selection = Color(0xFFA84D2F).copy(alpha = 0.12f),
    isDark = false,
)

val Ym1rClassicDarkColors = Ym1rColors(
    background = Color(0xFF161412),
    surface = Color(0xFF211D1A),
    surfaceRaised = Color(0xFF2D2723),
    textPrimary = Color(0xFFF7F1EB),
    textSecondary = Color(0xFFA5978B),
    separator = Color(0xFF38312B),
    accent = Color(0xFFF4AF8F),
    success = Color(0xFF70D89D),
    warning = Color(0xFFF1BD63),
    danger = Color(0xFFFF8C94),
    disabled = Color(0xFF5E544C),
    selection = Color(0xFFF4AF8F).copy(alpha = 0.18f),
    isDark = true,
)

val LocalYm1rColors = compositionLocalOf { Ym1rClearLightColors }

object Ym1rThemeTokens {
    val colors: Ym1rColors
        @Composable
        @ReadOnlyComposable
        get() = LocalYm1rColors.current
}

/**
 * 集中定义的标准平滑圆角预设
 */
object Ym1rShapes {
    val Icon = IosContinuousCornerShape(8.dp)
    val Row = IosContinuousCornerShape(10.dp)
    val Button = IosContinuousCornerShape(12.dp)
    val Card = IosContinuousCornerShape(16.dp)
    val Hero = IosContinuousCornerShape(20.dp)
    val Sheet = IosContinuousCornerShape(topStart = 24.dp, topEnd = 24.dp)
}

/**
 * 集中定义的字阶标准排印
 */
object Ym1rTypography {
    val LargeTitle = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 32.sp,
        lineHeight = 38.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.4).sp,
    )
    val NavTitle = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 17.sp,
        lineHeight = 22.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.2).sp,
    )
    val Title = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 20.sp,
        lineHeight = 25.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.2).sp,
    )
    val Body = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 16.sp,
        lineHeight = 22.sp,
        fontWeight = FontWeight.Normal,
    )
    val Callout = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 15.sp,
        lineHeight = 20.sp,
        fontWeight = FontWeight.Normal,
    )
    val Subheadline = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 14.sp,
        lineHeight = 19.sp,
        fontWeight = FontWeight.Normal,
    )
    val Footnote = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        fontWeight = FontWeight.Normal,
    )
    val Caption = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        fontWeight = FontWeight.Medium,
    )
}
