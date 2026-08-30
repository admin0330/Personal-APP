package com.masteralanlab.emailbox.ui.screens.home

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.masteralanlab.emailbox.data.Prefs
import com.masteralanlab.emailbox.data.NavigationSettings
import com.masteralanlab.emailbox.ui.nav.Route
import com.masteralanlab.emailbox.ui.screens.accounts.AccountsScreen
import com.masteralanlab.emailbox.ui.screens.ledger.LedgerScreen
import com.masteralanlab.emailbox.ui.screens.me.MeScreen
import com.masteralanlab.emailbox.ui.screens.notes.NotesScreen

@Composable
fun HomeScreen(onNavigate: (String) -> Unit) {
    var tab by rememberSaveable { mutableStateOf("mail") }
    val readOnly = Prefs.apiKeyMode
    val ordered = NavigationSettings.order.filter { !readOnly || it != "notes" }
    val tabs = ordered + "me"
    LaunchedEffect(tabs) { if (tab !in tabs) tab = ordered.firstOrNull() ?: "mail" }

    // 非首页时先回到首页，再交给系统处理返回
    BackHandler(enabled = tab != ordered.firstOrNull()) { tab = ordered.firstOrNull() ?: "mail" }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        // 内层页面各自的 Scaffold/顶栏已处理状态栏高度；外层再算一次就是「主页顶部多余空白」
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 0.dp,
            ) {
                tabs.forEach { id ->
                    NavigationBarItem(
                        selected = tab == id,
                        onClick = { tab = id },
                        icon = {
                            Icon(
                                when (id) {
                                    "ledger" -> Icons.Outlined.AccountBalanceWallet
                                    "notes" -> Icons.Outlined.Description
                                    "me" -> Icons.Outlined.Person
                                    else -> Icons.Outlined.Inbox
                                },
                                contentDescription = null,
                            )
                        },
                        label = {
                            Text(when (id) { "ledger" -> "记账"; "notes" -> "笔记"; "me" -> "我的"; else -> "邮箱" })
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                        ),
                    )
                }
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding)) {
            when (tab) {
                "mail" -> AccountsScreen(onNavigate = onNavigate)
                "ledger" -> LedgerScreen()
                "notes" -> NotesScreen()
                else -> MeScreen(onNavigate = onNavigate)
            }
        }
    }
}
