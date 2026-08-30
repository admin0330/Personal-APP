package com.masteralanlab.emailbox.ui.nav

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.masteralanlab.emailbox.data.Prefs
import com.masteralanlab.emailbox.data.RefreshBus
import com.masteralanlab.emailbox.data.SessionBus
import com.masteralanlab.emailbox.data.remote.ApiClient
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
        if (startDestination == Route.Home && open.first.isNotBlank()) {
            nav.navigate(Route.mailbox(open.first, open.second)) { launchSingleTop = true }
        }
        onOpenConsumed()
    }

    LaunchedEffect(Unit) {
        SessionBus.expired.collect { reason ->
            Prefs.clearSession()
            ApiClient.invalidate()
            MailNotifyService.sync(context)
            Toast.makeText(context, reason, Toast.LENGTH_SHORT).show()
            nav.navigate(Route.Setup) {
                popUpTo(0) { inclusive = true }
                launchSingleTop = true
            }
        }
    }

    val destination = startDestination ?: return

    NavHost(navController = nav, startDestination = destination) {

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

        composable(
            Route.Mailbox,
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

        composable(
            Route.MessageDetail,
            arguments = listOf(
                navArgument("accountId") { type = NavType.StringType },
                navArgument("messageId") { type = NavType.StringType },
                navArgument("folder") { type = NavType.StringType; defaultValue = "inbox" },
                navArgument("idMode") { type = NavType.StringType; defaultValue = "" },
                navArgument("subject") { type = NavType.StringType; defaultValue = "" },
            ),
        ) { entry ->
            val a = entry.arguments ?: return@composable
            MessageScreen(
                accountId = a.getString("accountId").orEmpty(),
                messageId = dec(a.getString("messageId")),
                folder = a.getString("folder") ?: "inbox",
                idMode = a.getString("idMode") ?: "",
                subject = dec(a.getString("subject")),
                onBack = { nav.popBackStack() },
            )
        }

        composable(
            Route.AccountEdit,
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

        composable(
            Route.AccountImport,
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

        composable(
            Route.GroupEdit,
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

        composable(
            Route.JobDetail,
            arguments = listOf(navArgument("jobId") { type = NavType.StringType }),
        ) { entry ->
            JobDetailScreen(jobId = entry.arguments?.getString("jobId").orEmpty(), onBack = { nav.popBackStack() })
        }

        composable(Route.RefreshLogs) { RefreshLogsScreen(onBack = { nav.popBackStack() }) }
        composable(Route.Tokens) {
            TokensScreen(
                onOpenJob = { nav.navigate(Route.job(it)) },
                onOpenLogs = { nav.navigate(Route.RefreshLogs) },
            )
        }
        composable(Route.MailSearch) {
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
        composable(Route.AdminUsers) { AdminUsersScreen(onBack = { nav.popBackStack() }) }
        composable(Route.AdminPlans) { AdminPlansScreen(onBack = { nav.popBackStack() }) }
        composable(Route.AdminAudit) { AdminAuditScreen(onBack = { nav.popBackStack() }) }
        composable(Route.Profile) { ProfileScreen(onBack = { nav.popBackStack() }) }
        composable(Route.Members) { MembersScreen(onBack = { nav.popBackStack() }) }
        composable(Route.ApiKey) { ApiKeyScreen(onBack = { nav.popBackStack() }) }
        composable(Route.Quota) { QuotaScreen(onBack = { nav.popBackStack() }) }
        composable(Route.Workspaces) { WorkspacesScreen(onBack = { nav.popBackStack() }) }
        composable(Route.Settings) {
            SettingsScreen(onBack = { nav.popBackStack() }, updateVm = updateVm)
        }
        composable(Route.SyncHealth) { SyncHealthScreen(onBack = { nav.popBackStack() }) }
    }

    // 全局更新弹窗：任何页面都可能弹出。
    // 手动「检查更新」是用户主动要求，无视「下次再说」的跳过标记；
    // 只有启动静默检查尊重它，避免打扰。
    val updateState by updateVm.state.collectAsStateWithLifecycle()
    val info = updateState.info
    if (info != null && (updateState.manual || info.versionCode > Prefs.skippedVersion)) {
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
