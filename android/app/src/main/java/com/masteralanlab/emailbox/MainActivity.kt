package com.masteralanlab.emailbox

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import com.masteralanlab.emailbox.data.Prefs
import com.masteralanlab.emailbox.data.BiometricUnlock
import com.masteralanlab.emailbox.ui.nav.AppNav
import com.masteralanlab.emailbox.ui.theme.EmailboxTheme
import com.masteralanlab.emailbox.ui.theme.ThemeSettings
import com.masteralanlab.emailbox.ui.theme.rememberDarkTheme

private const val RELock_AFTER_MS = 5 * 60 * 1000L

class MainActivity : FragmentActivity() {

    private var unlocked by mutableStateOf(false)
    private var backgroundAt = 0L
    private var promptShowing = false
    private var started = false

    /** 新邮件通知的点击深链：accountId to email，消费后置空。 */
    private var pendingOpen by mutableStateOf<Pair<String, String>?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Prefs.init(this)
        unlocked = !BiometricUnlock.shouldRequire(this)
        enableEdgeToEdge()
        handleOpen(intent)
        setContent {
            EmailboxRoot(
                unlocked = unlocked,
                onUnlock = ::requestUnlock,
                pendingOpen = pendingOpen,
                onOpenConsumed = { pendingOpen = null },
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleOpen(intent)
    }

    private fun handleOpen(intent: Intent?) {
        val accountId = intent?.getStringExtra("open_account_id") ?: return
        if (accountId.isBlank()) return
        pendingOpen = accountId to (intent.getStringExtra("open_email") ?: "")
    }

    override fun onStart() {
        super.onStart()
        val requiresUnlock = BiometricUnlock.shouldRequire(this)
        if (!requiresUnlock) {
            unlocked = true
            started = true
            return
        }
        val elapsed = if (backgroundAt == 0L) Long.MAX_VALUE else System.currentTimeMillis() - backgroundAt
        if (!started || elapsed >= RELock_AFTER_MS) unlocked = false
        started = true
        if (!unlocked) window.decorView.post(::requestUnlock)
    }

    override fun onStop() {
        if (Prefs.biometricUnlockEnabled && !isChangingConfigurations && !promptShowing) {
            backgroundAt = System.currentTimeMillis()
        }
        super.onStop()
    }

    private fun requestUnlock() {
        if (promptShowing || unlocked) return
        if (!BiometricUnlock.shouldRequire(this)) {
            unlocked = true
            return
        }
        promptShowing = true
        BiometricUnlock.authenticate(
            activity = this,
            onSuccess = {
                promptShowing = false
                backgroundAt = 0L
                unlocked = true
            },
            onFailure = { promptShowing = false },
        )
    }
}

@Composable
private fun EmailboxRoot(
    unlocked: Boolean,
    onUnlock: () -> Unit,
    pendingOpen: Pair<String, String>?,
    onOpenConsumed: () -> Unit,
) {
    EmailboxTheme(
        darkTheme = rememberDarkTheme(),
        dynamicColor = ThemeSettings.dynamicColor,
    ) {
        if (unlocked) AppNav(pendingOpen = pendingOpen, onOpenConsumed = onOpenConsumed)
        else UnlockScreen(onUnlock)
    }
}

@Composable
private fun UnlockScreen(onUnlock: () -> Unit) {
    // 只留一个锁图标，放在屏幕上方：系统指纹/密码弹窗出现在中下部，不会盖住它；
    // 点按图标等同于请求解锁（弹窗失败或被关掉后的手动入口）。
    Box(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding(),
    ) {
        Surface(
            onClick = onUnlock,
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 112.dp)
                .size(84.dp),
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Filled.Lock,
                    contentDescription = "点按解锁",
                    modifier = Modifier.size(34.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
