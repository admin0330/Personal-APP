package com.masteralanlab.emailbox

import com.masteralanlab.emailbox.ui.components.Ym1rIcons
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Text
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
        ThemeSettings.load()
        installDebugFixtureIfRequested(intent)
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

    private fun installDebugFixtureIfRequested(intent: Intent?) {
        if (!BuildConfig.DEBUG || intent?.getBooleanExtra("debug_fixture", false) != true) return
        runCatching {
            Class.forName("com.masteralanlab.emailbox.DebugFixtureHooks")
                .getMethod("install", android.content.Context::class.java)
                .invoke(null, this)
        }
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
    ) {
        if (unlocked) AppNav(pendingOpen = pendingOpen, onOpenConsumed = onOpenConsumed)
        else UnlockScreen(onUnlock)
    }
}

@Composable
private fun UnlockScreen(onUnlock: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Surface(
            onClick = onUnlock,
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier
                .size(88.dp),
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(
                    Ym1rIcons.Lock,
                    contentDescription = null,
                    modifier = Modifier.size(36.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
        Spacer(Modifier.size(24.dp))
        Text("Ym1r", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.size(6.dp))
        Text(
            "应用已锁定",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.size(20.dp))
        FilledTonalButton(onClick = onUnlock) {
            Text("使用指纹解锁")
        }
    }
}
