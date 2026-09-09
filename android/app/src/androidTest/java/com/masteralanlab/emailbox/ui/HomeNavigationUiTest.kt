package com.masteralanlab.emailbox.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.graphics.asAndroidBitmap
import android.graphics.Bitmap
import java.io.File
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.test.espresso.Espresso.pressBack
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.masteralanlab.emailbox.data.Prefs
import com.masteralanlab.emailbox.data.SecureMailCache
import com.masteralanlab.emailbox.ui.screens.home.HomeScreen
import com.masteralanlab.emailbox.ui.theme.EmailboxTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HomeNavigationUiTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun drawerOpensByAvatarAndClosesWithNormalBack() {
        rule.runOnUiThread {
            Prefs.init(rule.activity)
            SecureMailCache.init(rule.activity)
        }
        rule.setContent {
            EmailboxTheme(darkTheme = false) {
                HomeScreen(onNavigate = {})
            }
        }

        rule.onNodeWithContentDescription("打开账户与导航菜单").performClick()
        rule.waitForIdle()
        File(rule.activity.getExternalFilesDir(null), "drawer-open.png").outputStream().use {
            rule.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        rule.onNodeWithContentDescription("打开个人资料").assertIsDisplayed()
        pressBack()
        rule.onNodeWithContentDescription("打开账户与导航菜单").assertIsDisplayed()
        rule.onNodeWithContentDescription("打开个人资料").assertIsNotDisplayed()
    }
}
