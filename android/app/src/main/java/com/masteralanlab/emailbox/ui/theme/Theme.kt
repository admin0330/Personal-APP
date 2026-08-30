package com.masteralanlab.emailbox.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.masteralanlab.emailbox.data.Prefs

/**
 * 主题设置的可观察镜像。
 * 直接在组合里读 SharedPreferences 不会触发重组——设置页里切换浅色/深色或关闭动态取色
 * 后界面毫无反应，要重启 App 才生效。这里用 mutableStateOf 承载，写入方（设置页）与
 * 读取方（EmailboxRoot）共享同一份状态；Prefs 仍是持久层，App 启动时 load 一次。
 */
object ThemeSettings {

    var mode by mutableStateOf(Prefs.THEME_SYSTEM)
        private set

    var dynamicColor by mutableStateOf(true)
        private set

    /** App 启动时（Prefs.init 之后）从持久层装载一次。 */
    fun load() {
        mode = Prefs.themeMode
        dynamicColor = Prefs.dynamicColor
    }

    fun applyMode(value: String) {
        mode = value
        Prefs.themeMode = value
    }

    fun applyDynamicColor(value: Boolean) {
        dynamicColor = value
        Prefs.dynamicColor = value
    }
}

/**
 * Ym1r 的静态回退色板。Android 12+ 仍优先使用系统动态色；关闭动态色或低版本设备上
 * 回退到设计稿里的钴蓝紫，保证品牌页与正式截图保持一致。
 */
private val LightColors = lightColorScheme(
    primary = Color(0xFF4B5BD7),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDFE2FF),
    onPrimaryContainer = Color(0xFF111C62),
    secondary = Color(0xFF685AB0),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE8DDFF),
    onSecondaryContainer = Color(0xFF241052),
    tertiary = Color(0xFF006C4D),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFBAF1D9),
    onTertiaryContainer = Color(0xFF002116),
    background = Color(0xFFF9F7FF),
    onBackground = Color(0xFF1D1B20),
    surface = Color(0xFFFFFAFF),
    onSurface = Color(0xFF1D1B20),
    surfaceVariant = Color(0xFFE6E1EC),
    onSurfaceVariant = Color(0xFF625F69),
    outline = Color(0xFF797680),
    outlineVariant = Color(0xFFCAC6D0),
    error = Color(0xFFBA1A1A),
    errorContainer = Color(0xFFFFDAD6),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFBBC3FF),
    onPrimary = Color(0xFF182575),
    primaryContainer = Color(0xFF303E9E),
    onPrimaryContainer = Color(0xFFDFE2FF),
    secondary = Color(0xFFCDC0FF),
    onSecondary = Color(0xFF38266D),
    secondaryContainer = Color(0xFF50438A),
    onSecondaryContainer = Color(0xFFE8DDFF),
    tertiary = Color(0xFF9DD6BE),
    onTertiary = Color(0xFF003828),
    tertiaryContainer = Color(0xFF00513A),
    onTertiaryContainer = Color(0xFFBAF1D9),
    background = Color(0xFF131318),
    onBackground = Color(0xFFE5E1E9),
    surface = Color(0xFF131318),
    onSurface = Color(0xFFE5E1E9),
    surfaceVariant = Color(0xFF47464F),
    onSurfaceVariant = Color(0xFFCAC6D0),
    outline = Color(0xFF938F99),
    outlineVariant = Color(0xFF47464F),
    error = Color(0xFFFFB4AB),
    errorContainer = Color(0xFF93000A),
)

private val Ym1rShapes = Shapes(
    extraSmall = RoundedCornerShape(12.dp),
    small = RoundedCornerShape(16.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

private val Ym1rTypography = Typography(
    displaySmall = TextStyle(fontSize = 36.sp, lineHeight = 42.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.6f).sp),
    headlineMedium = TextStyle(fontSize = 28.sp, lineHeight = 34.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.35f).sp),
    headlineSmall = TextStyle(fontSize = 24.sp, lineHeight = 30.sp, fontWeight = FontWeight.SemiBold),
    titleLarge = TextStyle(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 17.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold),
    titleSmall = TextStyle(fontSize = 15.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp, fontWeight = FontWeight.Normal),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 21.sp, fontWeight = FontWeight.Normal),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 18.sp, fontWeight = FontWeight.Normal),
    labelLarge = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold),
    labelMedium = TextStyle(fontSize = 12.sp, lineHeight = 17.sp, fontWeight = FontWeight.SemiBold),
    labelSmall = TextStyle(fontSize = 11.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium),
)

/**
 * Material You：Android 12+ 默认使用系统壁纸取色（动态配色），
 * 低版本或用户关闭时回落到 Material 3 基线配色。
 */
@Composable
fun EmailboxTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColors
        else -> LightColors
    }

    // 应用内主题独立于系统开关，状态栏/导航栏图标明暗必须跟随应用内的深色状态，
    // 否则「系统浅色 + 应用深色」时深色图标落在深色背景上不可见。
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = !darkTheme
            controller.isAppearanceLightNavigationBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Ym1rTypography,
        shapes = Ym1rShapes,
        content = content,
    )
}

/** 依据用户设置解析出最终是否使用深色；设置变化时即时驱动重组。 */
@Composable
fun rememberDarkTheme(): Boolean = when (ThemeSettings.mode) {
    Prefs.THEME_LIGHT -> false
    Prefs.THEME_DARK -> true
    else -> isSystemInDarkTheme()
}
