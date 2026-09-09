package com.masteralanlab.emailbox.ui.screens.home

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import com.masteralanlab.emailbox.data.SecureMailCache
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.masteralanlab.emailbox.data.NavigationSettings
import com.masteralanlab.emailbox.data.Prefs
import com.masteralanlab.emailbox.ui.components.DockIcons
import com.masteralanlab.emailbox.ui.components.LiquidGlassDock
import com.masteralanlab.emailbox.ui.components.XInteractiveDrawerLayout
import com.masteralanlab.emailbox.ui.components.XNavigationDrawerContent
import com.masteralanlab.emailbox.ui.components.rememberXDrawerState
import com.masteralanlab.emailbox.ui.screens.accounts.AccountsScreen
import com.masteralanlab.emailbox.ui.screens.ledger.LedgerScreen
import com.masteralanlab.emailbox.ui.screens.notes.NotesScreen
import kotlinx.coroutines.launch

private val DEFAULT_DOCK = listOf("mail", "overview", "ledger", "notes")

/**
 * Ym1r 主界面：结合 X (Twitter) 交互层级与液态玻璃 Dock 栏
 * - 紧凑、沉浸的页面容器；
 * - 连续右拉交互式抽屉 (XInteractiveDrawerLayout)，手指驱动平滑展开，支持打断与速度吸附；
 * - 原生 AndroidLiquidGlass 液态玻璃 Dock 栏，透镜位置严格绑定权威业务页；
 * - 各 Tab 状态深度记忆 (SaveableStateHolder)。
 */
@Composable
fun HomeScreen(onNavigate: (String) -> Unit) {
    var tab by rememberSaveable { mutableStateOf("mail") }
    var compactDock by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val drawerState = rememberXDrawerState()
    val readOnly = Prefs.apiKeyMode
    val tenant = Prefs.tenantId.orEmpty()
    var unreadCount by remember { mutableIntStateOf(0) }

    LaunchedEffect(tenant, tab) {
        if (tenant.isNotBlank()) {
            val stats = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                SecureMailCache.stats(tenant)
            }
            unreadCount = stats.unread
        }
    }

    val dockTabs = remember(NavigationSettings.order, readOnly) {
        NavigationSettings.order
            .filter { it in setOf("mail", "overview", "notes", "ledger") }
            .filter { !readOnly || it != "notes" }
            .ifEmpty { DEFAULT_DOCK.filter { !readOnly || it != "notes" } }
            .distinct()
    }
    val tabState = rememberSaveableStateHolder()
    val contentTabs = dockTabs

    LaunchedEffect(contentTabs) {
        if (tab !in contentTabs) tab = dockTabs.firstOrNull() ?: "mail"
    }

    BackHandler(enabled = !drawerState.isOpen && tab != dockTabs.firstOrNull()) {
        tab = dockTabs.firstOrNull() ?: "mail"
    }

    val dockScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                when {
                    available.y < -4f -> compactDock = true
                    available.y > 4f -> compactDock = false
                }
                return Offset.Zero
            }
        }
    }

    fun selectTab(id: String) {
        tab = id
        drawerState.close()
    }

    XInteractiveDrawerLayout(
        drawerState = drawerState,
        drawerWidth = 320.dp,
        drawerContent = {
            XNavigationDrawerContent(
                onNavigate = onNavigate,
                onClose = { drawerState.close() },
            )
        },
    ) {
        val backdrop = rememberLayerBackdrop()

        Box(Modifier.fillMaxSize()) {
            Box(
                Modifier
                    .fillMaxSize()
                    .nestedScroll(dockScrollConnection)
                    .layerBackdrop(backdrop),
            ) {
                Crossfade(
                    targetState = tab,
                    animationSpec = tween(durationMillis = 150),
                    label = "home-tabs",
                ) { selectedTab ->
                    tabState.SaveableStateProvider(selectedTab) {
                        when (selectedTab) {
                            "overview" -> HomeOverviewScreen(
                                onNavigate = onNavigate,
                                onOpenDrawer = { drawerState.open() },
                                onOpenAccounts = { selectTab("mail") },
                            )
                            "mail" -> AccountsScreen(
                                onNavigate = onNavigate,
                                onOpenDrawer = { drawerState.open() },
                            )
                            "ledger" -> LedgerScreen(onOpenDrawer = { drawerState.open() })
                            "notes" -> NotesScreen(onOpenDrawer = { drawerState.open() })
                        }
                    }
                }
            }

            LiquidGlassDock(
                tabs = dockTabs,
                selectedTab = tab,
                compact = compactDock,
                onSelect = ::selectTab,
                backdrop = backdrop,
                labelOf = ::dockLabel,
                iconOf = ::dockIcon,
                badgeOf = { if (it == "mail") unreadCount else null },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .navigationBarsPadding(),
            )
        }
    }
}

private fun dockLabel(tab: String): String = when (tab) {
    "overview" -> "概览"
    "mail" -> "邮箱"
    "ledger" -> "账本"
    "notes" -> "笔记"
    else -> tab
}

private fun dockIcon(tab: String) = when (tab) {
    "overview" -> DockIcons.Dashboard
    "mail" -> DockIcons.Inbox
    "ledger" -> DockIcons.Wallet
    "notes" -> DockIcons.FileText
    else -> DockIcons.Inbox
}
