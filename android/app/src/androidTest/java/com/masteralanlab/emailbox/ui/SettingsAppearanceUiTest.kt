package com.masteralanlab.emailbox.ui

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.masteralanlab.emailbox.data.Prefs
import com.masteralanlab.emailbox.ui.screens.me.SettingsScreen
import com.masteralanlab.emailbox.ui.theme.EmailboxTheme
import com.masteralanlab.emailbox.ui.theme.ThemeSettings
import com.masteralanlab.emailbox.update.UpdateViewModel
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsAppearanceUiTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Test fun actualSettingsRespondInAllFourPalettes() {
        showSettings()
        for ((style, name) in listOf(Prefs.STYLE_APPLE to "clear", Prefs.STYLE_CLAUDE to "classic")) {
            for ((mode, suffix) in listOf(Prefs.THEME_LIGHT to "light", Prefs.THEME_DARK to "dark")) {
                rule.onNode(hasText(if (style == Prefs.STYLE_APPLE) "清透" else "经典") and hasClickAction()).performScrollTo().performClick().assertIsSelected()
                rule.onNodeWithText(if (mode == Prefs.THEME_LIGHT) "浅色" else "深色").performScrollTo().performClick().assertIsSelected()
                rule.waitForIdle()
                rule.runOnIdle {
                    assertEquals(style, Prefs.designStyle)
                    assertEquals(mode, Prefs.themeMode)
                }
                capture("settings-$name-$suffix")
            }
        }
    }

    @Test fun largeTextKeepsThemeControlsVisibleAndClickable() {
        showSettings(fontScale = 1.6f)
        rule.onNode(hasText("经典") and hasClickAction()).performScrollTo().performClick().assertIsSelected()
        rule.onNodeWithText("深色").performScrollTo().performClick().assertIsSelected()
        capture("settings-large-text")
    }

    private fun showSettings(fontScale: Float = 1f) {
        rule.runOnUiThread {
            Prefs.init(rule.activity)
            ThemeSettings.applyMode(Prefs.THEME_LIGHT)
            ThemeSettings.applyDesignStyle(Prefs.STYLE_APPLE)
        }
        val vm = UpdateViewModel()
        rule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
                EmailboxTheme { SettingsScreen(onBack = {}, updateVm = vm) }
            }
        }
    }

    private fun capture(name: String) {
        rule.waitForIdle()
        val bitmap = rule.onRoot().captureToImage().asAndroidBitmap()
        File(rule.activity.getExternalFilesDir(null), "$name.png").outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}
