package com.masteralanlab.emailbox.ui.nav

import android.widget.Toast
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.ui.Modifier
import com.masteralanlab.emailbox.ui.nav.IosNavigationContainer
import com.masteralanlab.emailbox.ui.nav.IosTransitions
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.masteralanlab.emailbox.data.Prefs
import com.masteralanlab.emailbox.data.RefreshBus
import com.masteralanlab.emailbox.data.SessionBus
import com.masteralanlab.emailbox.data.SessionManager
import com.masteralanlab.emailbox.ui.screens.account.AccountEditScreen
import com.masteralanlab.emailbox.ui.screens.account.AccountImportScreen
import com.masteralanlab.emailbox.ui.screens.admin.AdminAuditScreen
import com.masteralanlab.emailbox.ui.screens.admin.AdminPlansScreen
import com.masteralanlab.emailbox.ui.screens.admin.AdminUsersScreen
import com.masteralanlab.emailbox.ui.screens.auth.SetupScreen
import com.masteralanlab.emailbox.ui.screens.groups.GroupEditScreen
import com.masteralanlab.emailbox.ui.screens.home.HomeScreen
import com.masteralanlab.emailbox.ui.screens.mailbox.MailboxScreen
import com.masteralanlab.emailbox.ui.screens.me.ApiKeyScreen
import com.masteralanlab.emailbox.ui.screens.me.MembersScreen
import com.masteralanlab.emailbox.ui.screens.me.ProfileScreen
import com.masteralanlab.emailbox.ui.screens.me.QuotaScreen
import com.masteralanlab.emailbox.ui.screens.me.SettingsScreen
import com.masteralanlab.emailbox.ui.screens.me.WorkspacesScreen
import com.masteralanlab.emailbox.ui.screens.me.SyncHealthScreen
import com.masteralanlab.emailbox.ui.screens.message.MessageScreen
import com.masteralanlab.emailbox.ui.screens.search.MailSearchScreen
import com.masteralanlab.emailbox.notify.MailNotifyService
import com.masteralanlab.emailbox.ui.screens.tokens.JobDetailScreen
import com.masteralanlab.emailbox.ui.screens.tokens.RefreshLogsScreen
import com.masteralanlab.emailbox.ui.screens.tokens.TokensScreen
import com.masteralanlab.emailbox.update.UpdateDialog
import com.masteralanlab.emailbox.update.UpdateManager
import com.masteralanlab.emailbox.update.UpdateViewModel
import java.net.URLDecoder

private fun dec(v: String?) = runCatching { URLDecoder.decode(v.orEmpty(), "UTF-8") }.getOrDefault(v.orEmpty())

@OptIn(ExperimentalSharedTransitionApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AppNav(pendingOpen: Pair<String, String>? = null, onOpenConsumed: () -> Unit = {}) {
    val nav = rememberNavController()
    val context = LocalContext.current
    val updateVm: UpdateViewModel = viewModel()

    val loggedIn = remember {
        if (Prefs.apiKeyMode) !Prefs.apiKey.isNullOrBlank() else !Prefs.sessionToken.isNullOrBlank()
    }
    var startDestination by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        startDestination = if (loggedIn) Route.Home else Route.Setup
        updateVm.checkOnStart()
    }

    // 登录后拉起「服务器拉取」通知服务（幂等：模式或登录态不满足时它自己会停）
    LaunchedEffect(startDestination) {
        if (startDestination == Route.Home) {
            MailNotifyService.sync(context)
        }
    }

    // 新邮件通知的点击深链：已登录时直达对应邮箱的收件箱
    LaunchedEffect(pendingOpen, startDestination) {
        val open = pendingOpen ?: return@LaunchedEffect
        val destination = startDestination ?: return@LaunchedEffect
        if (destination == Route.Home && open.first.isNotBlank()) {
            // 首次 composition 中 NavHost 还没把 graph 挂到 NavController，等一帧再消费通知深链。
            withFrameNanos { }
            nav.navigate(Route.mailbox(open.first, open.second)) { launchSingleTop = true }
        }
        onOpenConsumed()
    }

    LaunchedEffect(Unit) {
        SessionBus.expired.collect { reason ->
            SessionManager.clearLocal(context)
            Toast.makeText(context, reason, Toast.LENGTH_SHORT).show()
            nav.navigate(Route.Setup) {
                popUpTo(0) { inclusive = true }
                launchSingleTop = true
            }
        }
    }

    val destination = startDestination ?: return

    val currentEntry by nav.currentBackStackEntryAsState()
    val currentRoute = currentEntry?.destination?.route
    val canPop = currentRoute != null && currentRoute != Route.Home && currentRoute != Route.Setup

    SharedTransitionLayout {
        IosNavigationContainer(
            navController = nav,
            enabled = canPop,
        ) {
            NavHost(
                navController = nav,
                startDestination = destination,
                // 纯正 iOS 页面推入/退出与 -30% 视差转场 (阻尼 0.86, 刚度 750)
                enterTransition = { IosTransitions.PushEnter },
                exitTransition = { IosTransitions.PushExit },
                popEnterTransition = { IosTransitions.PopEnter },
                popExitTransition = { IosTransitions.PopExit },
            ) {

        composable(Route.Setup) {
            SetupScreen(
                onLoggedIn = {
                    MailNotifyService.sync(context)
                    nav.navigate(Route.Home) {
                        popUpTo(Route.Setup) { inclusive = true }
                        launchSingleTop = true
                    }
                },
            )
        }

        composable(Route.Home) {
            HomeScreen(onNavigate = { route -> nav.navigate(route) })
        }

        iosComposable(
            Route.Mailbox,
            onBack = { nav.popBackStack() },
            arguments = listOf(
                navArgument("accountId") { type = NavType.StringType },
                navArgument("email") { type = NavType.StringType },
            ),
        ) { entry ->
            val accountId = entry.arguments?.getString("accountId").orEmpty()
            val email = dec(entry.arguments?.getString("email"))
            MailboxScreen(
                accountId = accountId,
                email = email,
                onBack = { nav.popBackStack() },
                sharedTransitionScope = this@SharedTransitionLayout,
                animatedVisibilityScope = this,
                onOpenMessage = { msg ->
                    nav.navigate(
                        Route.messageDetail(
                            accountId = accountId,
                            messageId = msg.id,
                            folder = msg.folder,
                            idMode = msg.id_mode,
                            subject = msg.subject,
                        ),
                    )
                },
            )
        }

        iosComposable(
            Route.MessageDetail,
            onBack = { nav.popBackStack() },
            arguments = listOf(
                navArgument("accountId") { type = NavType.StringType },
                navArgument("messageId") { type = NavType.StringType },
                navArgument("folder") { type = NavType.StringType; defaultValue = "inbox" },
                navArgument("idMode") { type = NavType.StringType; defaultValue = "" },
                navArgument("subject") { type = NavType.StringType; defaultValue = "" },
            ),
        ) { entry ->
            val a = entry.arguments ?: return@iosComposable
            MessageScreen(
                accountId = a.getString("accountId").orEmpty(),
                messageId = dec(a.getString("messageId")),
                folder = a.getString("folder") ?: "inbox",
                idMode = a.getString("idMode") ?: "",
                subject = dec(a.getString("subject")),
                onBack = { nav.popBackStack() },
                sharedTransitionScope = this@SharedTransitionLayout,
                animatedVisibilityScope = this,
            )
        }

        iosComposable(
            Route.AccountEdit,
            onBack = { nav.popBackStack() },
            arguments = listOf(
                navArgument("accountId") { type = NavType.StringType; nullable = true; defaultValue = null },
                navArgument("groupId") { type = NavType.StringType; nullable = true; defaultValue = null },
            ),
        ) { entry ->
            AccountEditScreen(
                accountId = entry.arguments?.getString("accountId"),
                groupId = entry.arguments?.getString("groupId"),
                onBack = { nav.popBackStack() },
                onSaved = {
                    RefreshBus.request(RefreshBus.Kind.Accounts)
                    nav.popBackStack()
                },
            )
        }

        iosComposable(
            Route.AccountImport,
            onBack = { nav.popBackStack() },
            arguments = listOf(
                navArgument("groupId") { type = NavType.StringType; nullable = true; defaultValue = null },
            ),
        ) { entry ->
            AccountImportScreen(
                groupId = entry.arguments?.getString("groupId"),
                onBack = { nav.popBackStack() },
                onDone = {
                    RefreshBus.request(RefreshBus.Kind.Accounts)
                    nav.popBackStack()
                },
            )
        }

        iosComposable(
            Route.GroupEdit,
            onBack = { nav.popBackStack() },
            arguments = listOf(
                navArgument("groupId") { type = NavType.StringType; nullable = true; defaultValue = null },
            ),
        ) { entry ->
            GroupEditScreen(
                groupId = entry.arguments?.getString("groupId"),
                onBack = { nav.popBackStack() },
                onSaved = {
                    RefreshBus.request(RefreshBus.Kind.Groups)
                    nav.popBackStack()
                },
            )
        }

        iosComposable(
            Route.JobDetail,
            onBack = { nav.popBackStack() },
            arguments = listOf(navArgument("jobId") { type = NavType.StringType }),
        ) { entry ->
            JobDetailScreen(jobId = entry.arguments?.getString("jobId").orEmpty(), onBack = { nav.popBackStack() })
        }

        iosComposable(Route.RefreshLogs, onBack = { nav.popBackStack() }) { RefreshLogsScreen(onBack = { nav.popBackStack() }) }
        iosComposable(Route.Tokens, onBack = { nav.popBackStack() }) {
            TokensScreen(
                onBack = { nav.popBackStack() },
                onOpenJob = { nav.navigate(Route.job(it)) },
                onOpenLogs = { nav.navigate(Route.RefreshLogs) },
            )
        }
        iosComposable(Route.MailSearch, onBack = { nav.popBackStack() }) {
            MailSearchScreen(
                onBack = { nav.popBackStack() },
                onOpen = { cached ->
                    nav.navigate(
                        Route.messageDetail(
                            cached.accountId, cached.item.id, cached.item.folder,
                            cached.item.id_mode, cached.item.subject,
                        ),
                    )
                },
            )
        }
        iosComposable(Route.AdminUsers, onBack = { nav.popBackStack() }) {
            AdminUsersScreen(onBack = { nav.popBackStack() }, onNavigate = { nav.navigate(it) })
        }
        iosComposable(Route.AdminPlans, onBack = { nav.popBackStack() }) { AdminPlansScreen(onBack = { nav.popBackStack() }) }
        iosComposable(Route.AdminAudit, onBack = { nav.popBackStack() }) { AdminAuditScreen(onBack = { nav.popBackStack() }) }
        iosComposable(Route.Profile, onBack = { nav.popBackStack() }) { ProfileScreen(onBack = { nav.popBackStack() }) }
        iosComposable(Route.Members, onBack = { nav.popBackStack() }) { MembersScreen(onBack = { nav.popBackStack() }) }
        iosComposable(Route.ApiKey, onBack = { nav.popBackStack() }) { ApiKeyScreen(onBack = { nav.popBackStack() }) }
        iosComposable(Route.Quota, onBack = { nav.popBackStack() }) { QuotaScreen(onBack = { nav.popBackStack() }) }
        iosComposable(Route.Workspaces, onBack = { nav.popBackStack() }) { WorkspacesScreen(onBack = { nav.popBackStack() }) }
        iosComposable(Route.Settings, onBack = { nav.popBackStack() }) {
            SettingsScreen(
                onBack = { nav.popBackStack() },
                updateVm = updateVm,
                onNavigateToProfile = { nav.navigate(Route.Profile) },
            )
        }
        iosComposable(Route.SyncHealth, onBack = { nav.popBackStack() }) { SyncHealthScreen(onBack = { nav.popBackStack() }) }
            }
        }
    }

    // 全局更新弹窗：任何页面都可能弹出。
    // 只要公网清单高于当前 versionCode，就展示更新；关闭只影响当前弹窗。
    val updateState by updateVm.state.collectAsStateWithLifecycle()
    val info = updateState.info
    if (info != null) {
        UpdateDialog(
            state = updateState,
            onDismiss = { updateVm.dismiss(info) },
            onDownload = { updateVm.downloadAndInstall(context) },
            onInstall = {
                if (!UpdateManager.canInstall(context)) {
                    UpdateManager.openInstallPermissionSetting(context)
                } else {
                    updateVm.launchInstall(context)
                }
            },
        )
    }
}
