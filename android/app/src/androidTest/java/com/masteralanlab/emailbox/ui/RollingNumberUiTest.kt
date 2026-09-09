package com.masteralanlab.emailbox.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.masteralanlab.emailbox.ui.components.RollingBadge
import com.masteralanlab.emailbox.ui.components.RollingMoney
import com.masteralanlab.emailbox.ui.components.RollingNumber
import com.masteralanlab.emailbox.ui.theme.EmailboxTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RollingNumberUiTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun rollingNumberDisplaysSemanticsAndUpdates() {
        var count by mutableStateOf(42L)
        rule.setContent {
            EmailboxTheme {
                RollingNumber(value = count)
            }
        }

        rule.onNodeWithText("42").assertIsDisplayed()

        rule.runOnUiThread {
            count = 108L
        }
        rule.waitForIdle()

        rule.onNodeWithText("108").assertIsDisplayed()
    }

    @Test
    fun rollingMoneyDisplaysFormattedAmount() {
        var amount by mutableStateOf(123450L)
        rule.setContent {
            EmailboxTheme {
                RollingMoney(amountMinor = amount, currency = "¥")
            }
        }

        rule.onNodeWithText("¥ 1,234.50").assertIsDisplayed()

        rule.runOnUiThread {
            amount = -5000L
        }
        rule.waitForIdle()

        rule.onNodeWithText("-¥ 50.00").assertIsDisplayed()
    }

    @Test
    fun rollingBadgeDisplaysCount() {
        var badgeCount by mutableStateOf(5)
        rule.setContent {
            EmailboxTheme {
                RollingBadge(count = badgeCount)
            }
        }

        rule.onNodeWithText("5").assertIsDisplayed()

        rule.runOnUiThread {
            badgeCount = 99
        }
        rule.waitForIdle()

        rule.onNodeWithText("99").assertIsDisplayed()
    }
}
