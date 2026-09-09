package com.masteralanlab.emailbox.ui

import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.espresso.Espresso.pressBack
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.masteralanlab.emailbox.ui.screens.message.OtpCard
import com.masteralanlab.emailbox.ui.theme.EmailboxTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OtpCopyUiTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun successfulCopyShowsFeedbackAndResets() {
        rule.setContent {
            EmailboxTheme(darkTheme = false) {
                OtpCard(code = "482913", onCopy = { true })
            }
        }

        rule.onNodeWithText("复制验证码").performClick()
        rule.onNodeWithText("已复制").assertIsDisplayed()

        rule.mainClock.autoAdvance = false
        rule.mainClock.advanceTimeBy(1_600)
        rule.waitForIdle()
        rule.onNodeWithText("复制验证码").assertIsDisplayed()
    }

    @Test
    fun failedCopyNeverShowsSuccess() {
        rule.setContent {
            EmailboxTheme(darkTheme = false) {
                OtpCard(code = "482913", onCopy = { false })
            }
        }

        rule.onNodeWithText("复制验证码").performClick()
        rule.onNodeWithText("复制验证码").assertIsDisplayed()
    }

    @Test
    fun systemBackIsDeliveredToHost() {
        var wentBack = false
        rule.setContent {
            EmailboxTheme(darkTheme = false) {
                BackHandler { wentBack = true }
                OtpCard(code = "482913", onCopy = { true })
            }
        }

        pressBack()
        rule.runOnIdle { assertTrue(wentBack) }
    }
}
