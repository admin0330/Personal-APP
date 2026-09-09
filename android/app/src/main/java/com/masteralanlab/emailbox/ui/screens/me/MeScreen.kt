package com.masteralanlab.emailbox.ui.screens.me

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.masteralanlab.emailbox.ui.components.Ym1rIcons
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.masteralanlab.emailbox.data.Prefs
import com.masteralanlab.emailbox.data.SessionBus
import com.masteralanlab.emailbox.data.SessionManager
import com.masteralanlab.emailbox.ui.components.SectionTitle
import com.masteralanlab.emailbox.ui.components.StatusChip
import com.masteralanlab.emailbox.ui.components.Ym1rCard
import com.masteralanlab.emailbox.ui.components.MainTopBar
import com.masteralanlab.emailbox.ui.components.ProductSurface
import com.masteralanlab.emailbox.ui.nav.Route

/**
 * 「我的」页。
 *
 * 只读取 Prefs 里的本地状态，不发请求；跳转一律通过 onNavigate 交给外层导航处理，
 * 退出登录后由全局的会话失效广播把用户带回登录页。
 */

private data class MeEntry(
    val icon: ImageVector,
    val title: String,
    val subtitle: String,
    val danger: Boolean = false,
    val onClick: () -> Unit,
)

@Composable
fun MeScreen(onNavigate: (String) -> Unit) {
    val readOnly = Prefs.apiKeyMode
    val context = LocalContext.current
    var confirmLogout by remember { mutableStateOf(false) }
    val topBarScrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(topBarScrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            MainTopBar(
                title = "我的",
                scrollBehavior = topBarScrollBehavior,
            )
        },
    ) {
        innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState()),
        ) {
            Text(
                if (readOnly) "只读访问与本机偏好" else "账户、工作空间与本机偏好",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp),
            )
            UserHeaderCard(readOnly = readOnly, onClick = { onNavigate(Route.Profile) })

        if (!readOnly) {
            SectionTitle("工作空间")
            MeGroup(
                listOf(
                    MeEntry(
                        icon = Ym1rIcons.Sliders,
                        title = "切换工作空间",
                        subtitle = Prefs.tenantName ?: "未选择",
                    ) { onNavigate(Route.Workspaces) },
                    MeEntry(
                        icon = Ym1rIcons.Users,
                        title = "成员管理",
                        subtitle = "邀请成员并分配角色",
                    ) { onNavigate(Route.Members) },
                    MeEntry(
                        icon = Ym1rIcons.Key,
                        title = "API Key",
                        subtitle = "只读接口的访问凭据",
                    ) { onNavigate(Route.ApiKey) },
                    MeEntry(
                        icon = Ym1rIcons.Server,
                        title = "令牌维护",
                        subtitle = "刷新邮箱授权并查看执行记录",
                    ) { onNavigate(Route.Tokens) },
                )
            )

            if (Prefs.isPlatformAdmin) {
                SectionTitle("平台管理")
                MeGroup(
                    listOf(
                        MeEntry(
                            icon = Ym1rIcons.User,
                            title = "用户管理",
                            subtitle = "平台账号、角色与密码重置",
                        ) { onNavigate(Route.AdminUsers) },
                        MeEntry(
                            icon = Ym1rIcons.Activity,
                            title = "审计日志",
                            subtitle = "平台操作留痕",
                        ) { onNavigate(Route.AdminAudit) },
                    )
                )
            }
        }

        SectionTitle("其他")
        MeGroup(
            listOf(
                MeEntry(
                    icon = Ym1rIcons.Settings,
                    title = "设置",
                    subtitle = "外观、服务器与应用内更新",
                ) { onNavigate(Route.Settings) },
                MeEntry(
                    icon = Ym1rIcons.Activity,
                    title = "同步健康中心",
                    subtitle = if (Prefs.isPlatformAdmin) "检查服务链路并切换服务器节点" else "查看我的邮箱同步状态",
                ) { onNavigate(Route.SyncHealth) },
                MeEntry(
                    icon = Ym1rIcons.LogOut,
                    title = "退出登录",
                    subtitle = if (readOnly) "清除本地登录密钥，需要重新绑定" else "清除本地会话，需要重新输入密码",
                    danger = true,
                ) { confirmLogout = true },
            )
        )

            Spacer(Modifier.height(24.dp))
        }
    }

    if (confirmLogout) {
        AlertDialog(
            onDismissRequest = { confirmLogout = false },
            icon = { Icon(Ym1rIcons.LogOut, contentDescription = null) },
            title = { Text("退出登录") },
            text = {
                Text(
                    if (readOnly) {
                        "退出后需要重新输入登录密钥才能继续使用，本地保存的登录密钥与工作空间信息会被清除。"
                    } else {
                        "退出后需要重新输入用户名和密码才能继续使用，本地保存的会话与个人空间信息会被清除。"
                    },
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmLogout = false
                        SessionManager.logout(context)
                        SessionBus.emitExpired("已退出登录")
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                ) { Text("退出登录") }
            },
            dismissButton = {
                TextButton(onClick = { confirmLogout = false }) { Text("取消") }
            },
        )
    }
}

// ---------------------------------------------------------------- 头部卡片

@Composable
private fun UserHeaderCard(readOnly: Boolean, onClick: () -> Unit) {
    val name = if (readOnly) "个人登录密钥" else Prefs.username?.takeIf { it.isNotBlank() } ?: "未登录"
    val email = Prefs.userEmail?.takeIf { it.isNotBlank() }
    val isAdmin = Prefs.isPlatformAdmin

    ProductSurface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        shape = MaterialTheme.shapes.extraLarge,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        onClick = onClick.takeIf { !readOnly },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            UserAvatar(
                name = name,
                avatarPath = Prefs.avatarPath,
                modifier = Modifier.size(52.dp),
            )
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        name,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (isAdmin) {
                        Spacer(Modifier.width(8.dp))
                        StatusChip(
                            text = "平台管理员",
                            container = MaterialTheme.colorScheme.tertiaryContainer,
                            content = MaterialTheme.colorScheme.onTertiaryContainer,
                        )
                    }
                }
                if (email != null) {
                    Spacer(Modifier.height(3.dp))
                        Text(
                            email,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    "当前工作空间：${if (readOnly) Prefs.tenantId ?: "未选择" else Prefs.tenantName ?: "未选择"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (!readOnly) {
                Spacer(Modifier.width(8.dp))
                Icon(
                    Ym1rIcons.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

// ---------------------------------------------------------------- 分组列表

@Composable
private fun MeGroup(entries: List<MeEntry>) {
    Ym1rCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
    ) {
        entries.forEachIndexed { index, entry ->
            if (index > 0) {
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .55f),
                )
            }
            ListItem(
                headlineContent = {
                    Text(
                        entry.title,
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (entry.danger) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                    )
                },
                supportingContent = {
                    Text(
                        entry.subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                leadingContent = {
                    Box(
                        Modifier
                            .size(40.dp)
                            .clip(MaterialTheme.shapes.small)
                            .background(if (entry.danger) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceContainerHighest),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            entry.icon,
                            contentDescription = null,
                            tint = if (entry.danger) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                trailingContent = {
                    Icon(
                        Ym1rIcons.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.outline,
                    )
                },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                modifier = Modifier
                    .clip(MaterialTheme.shapes.medium)
                    .clickable(onClick = entry.onClick),
            )
        }
    }
}
