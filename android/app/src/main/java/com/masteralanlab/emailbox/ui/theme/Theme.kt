package com.masteralanlab.emailbox.ui.theme

import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.masteralanlab.emailbox.data.Prefs

/** Persisted theme selection; changing appearance never recreates the navigation tree. */
object ThemeSettings {
    var mode by mutableStateOf(Prefs.THEME_SYSTEM)
        private set
    var designStyle by mutableStateOf(Prefs.STYLE_APPLE)
        private set
    fun load() { mode = Prefs.themeMode; designStyle = Prefs.designStyle }
    fun applyMode(value: String) { Prefs.themeMode = value; mode = value }
    fun applyDesignStyle(value: String) { Prefs.designStyle = value; designStyle = value }
}

private val AppShapes = Shapes(
    extraSmall = IosContinuousCornerShape(8.dp),
    small = IosContinuousCornerShape(12.dp),
    medium = IosContinuousCornerShape(16.dp),
    large = IosContinuousCornerShape(20.dp),
    extraLarge = IosContinuousCornerShape(24.dp),
)

/** Material and custom components share exactly the same palette, including during animation. */
internal fun materialColors(colors: Ym1rColors): ColorScheme {
    val base = if (colors.isDark) darkColorScheme() else lightColorScheme()
    val selection = colors.selection.compositeOver(colors.surface)
    val onAccent = if (colors.accent.luminance() > 0.179f) Color.Black else Color.White
    val onDanger = if (colors.danger.luminance() > 0.179f) Color.Black else Color.White
    return base.copy(
        primary = colors.accent, onPrimary = onAccent,
        primaryContainer = selection, onPrimaryContainer = colors.accent,
        secondary = colors.accent, onSecondary = onAccent,
        secondaryContainer = selection, onSecondaryContainer = colors.accent,
        tertiary = colors.success, onTertiary = if (colors.success.luminance() > 0.179f) Color.Black else Color.White,
        tertiaryContainer = colors.success.copy(alpha = 0.12f).compositeOver(colors.surface),
        onTertiaryContainer = colors.success,
        background = colors.background, onBackground = colors.textPrimary,
        surface = colors.surface, onSurface = colors.textPrimary,
        surfaceVariant = colors.surfaceRaised, onSurfaceVariant = colors.textSecondary,
        surfaceTint = colors.accent,
        surfaceDim = colors.background, surfaceBright = colors.surfaceRaised,
        surfaceContainerLowest = colors.background, surfaceContainerLow = colors.surface,
        surfaceContainer = colors.surface, surfaceContainerHigh = colors.surfaceRaised,
        surfaceContainerHighest = colors.surfaceRaised,
        outline = colors.textSecondary, outlineVariant = colors.separator,
        error = colors.danger, onError = onDanger,
        errorContainer = colors.danger.copy(alpha = 0.10f).compositeOver(colors.surface),
        onErrorContainer = colors.danger,
        inverseSurface = colors.textPrimary, inverseOnSurface = colors.background,
        inversePrimary = colors.background, scrim = Color.Black,
    )
}

private val AppTypography = Typography(
    displayLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 34.sp, lineHeight = 41.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.4).sp),
    displayMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 28.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.3).sp),
    displaySmall = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.2).sp),
    headlineLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 34.sp, lineHeight = 41.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.4).sp), // iOS Large Title
    headlineMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 28.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.3).sp), // iOS Title 1
    headlineSmall = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.2).sp), // iOS Title 2
    titleLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 20.sp, lineHeight = 25.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.1).sp), // iOS Title 3
    titleMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 17.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.1).sp), // iOS Headline
    titleSmall = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold), // iOS Subheadline Bold
    bodyLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 17.sp, lineHeight = 22.sp, fontWeight = FontWeight.Normal), // iOS Body
    bodyMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 16.sp, lineHeight = 21.sp, fontWeight = FontWeight.Normal), // iOS Callout
    bodySmall = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.Normal), // iOS Subheadline
    labelLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold),
    labelMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Normal), // iOS Footnote
    labelSmall = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight.Medium), // iOS Caption 2
)

@Composable
private fun animateYm1rColors(target: Ym1rColors): Ym1rColors {
    val spec: AnimationSpec<Color> = tween(durationMillis = 200, easing = FastOutSlowInEasing)
    return Ym1rColors(
        background = animateColorAsState(target.background, spec, label = "y_bg").value,
        surface = animateColorAsState(target.surface, spec, label = "y_sf").value,
        surfaceRaised = animateColorAsState(target.surfaceRaised, spec, label = "y_sfr").value,
        textPrimary = animateColorAsState(target.textPrimary, spec, label = "y_tp").value,
        textSecondary = animateColorAsState(target.textSecondary, spec, label = "y_ts").value,
        separator = animateColorAsState(target.separator, spec, label = "y_sep").value,
        accent = animateColorAsState(target.accent, spec, label = "y_acc").value,
        success = animateColorAsState(target.success, spec, label = "y_suc").value,
        warning = animateColorAsState(target.warning, spec, label = "y_warn").value,
        danger = animateColorAsState(target.danger, spec, label = "y_dan").value,
        disabled = animateColorAsState(target.disabled, spec, label = "y_dis").value,
        selection = animateColorAsState(target.selection, spec, label = "y_sel").value,
        isDark = target.isDark,
    )
}


@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun EmailboxTheme(darkTheme: Boolean = rememberDarkTheme(), content: @Composable () -> Unit) {
    val target = when {
        ThemeSettings.designStyle == Prefs.STYLE_CLAUDE && darkTheme -> Ym1rClassicDarkColors
        ThemeSettings.designStyle == Prefs.STYLE_CLAUDE -> Ym1rClassicLightColors
        darkTheme -> Ym1rClearDarkColors
        else -> Ym1rClearLightColors
    }
    val colors = animateYm1rColors(target)
    val scheme = materialColors(colors)
    val view = LocalView.current
    // Window setup runs on theme selection, not on every animation frame.
    DisposableEffect(view, darkTheme, target.background) {
        val activity = view.context as? ComponentActivity
        if (!view.isInEditMode && activity != null) {
            val background = target.background.toArgb()
            val bar = if (darkTheme) SystemBarStyle.dark(background) else SystemBarStyle.light(background, background)
            activity.enableEdgeToEdge(statusBarStyle = bar, navigationBarStyle = bar)
            activity.window.decorView.setBackgroundColor(background)
        }
        onDispose { }
    }
    CompositionLocalProvider(LocalYm1rColors provides colors, LocalDarkTheme provides darkTheme) {
        MaterialExpressiveTheme(
            colorScheme = scheme, motionScheme = MotionScheme.standard(),
            typography = AppTypography, shapes = AppShapes, content = content,
        )
    }
}

val LocalDarkTheme = compositionLocalOf { false }

@Composable
fun rememberDarkTheme(): Boolean = when (ThemeSettings.mode) {
    Prefs.THEME_LIGHT -> false
    Prefs.THEME_DARK -> true
    else -> isSystemInDarkTheme()
}
