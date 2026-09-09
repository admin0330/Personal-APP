package com.masteralanlab.emailbox.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.masteralanlab.emailbox.data.Prefs
import com.masteralanlab.emailbox.ui.components.AppleSegmentedControl
import com.masteralanlab.emailbox.ui.theme.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppearanceUiTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Test fun themeChangesInPlaceAndRapidSelectionKeepsLatestChoice() {
        var current = Ym1rClearLightColors
        rule.runOnUiThread {
            Prefs.init(rule.activity)
            ThemeSettings.applyMode(Prefs.THEME_LIGHT)
            ThemeSettings.applyDesignStyle(Prefs.STYLE_APPLE)
        }
        rule.setContent {
            EmailboxTheme {
                val colors = LocalYm1rColors.current
                SideEffect { current = colors }
                var draft by remember { mutableStateOf("") }
                Column {
                    AppleSegmentedControl(
                        options = listOf("清透", "经典"),
                        selectedIndex = if (ThemeSettings.designStyle == Prefs.STYLE_APPLE) 0 else 1,
                        onSelect = { ThemeSettings.applyDesignStyle(if (it == 0) Prefs.STYLE_APPLE else Prefs.STYLE_CLAUDE) },
                    )
                    OutlinedTextField(value = draft, onValueChange = { draft = it })
                }
            }
        }
        rule.onNode(hasSetTextAction()).performTextInput("未保存的输入")
        rule.onNodeWithText("经典").performClick().assertIsSelected()
        rule.waitForIdle()
        rule.runOnIdle { assertEquals(Ym1rClassicLightColors.background, current.background) }
        rule.onNodeWithText("未保存的输入").assertIsDisplayed()
        rule.onNodeWithText("清透").performClick()
        rule.onNodeWithText("经典").performClick()
        rule.waitForIdle()
        rule.runOnIdle {
            assertEquals(Prefs.STYLE_CLAUDE, Prefs.designStyle)
            ThemeSettings.applyMode(Prefs.THEME_DARK)
        }
        rule.waitForIdle()
        rule.runOnIdle { assertEquals(Ym1rClassicDarkColors.background, current.background) }
        rule.onNodeWithText("未保存的输入").assertIsDisplayed()
    }
}
