package com.masteralanlab.emailbox.ui.screens.me

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowForward
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.CardMembership
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.MonitorHeart
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.ManageAccounts
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.masteralanlab.emailbox.data.Prefs
import com.masteralanlab.emailbox.data.SessionBus
import com.masteralanlab.emailbox.data.remote.ApiClient
import com.masteralanlab.emailbox.ui.components.SectionTitle
import com.masteralanlab.emailbox.ui.components.StatusChip
import com.masteralanlab.emailbox.ui.nav.Route
import com.masteralanlab.emailbox.util.initialOf

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
    var confirmLogout by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            // 本页没有顶栏，状态栏高度自己留：外层不再兜底（见 HomeScreen insets 说明）
            .statusBarsPadding()
            .verticalScroll(rememberScrollState()),
    ) {
        UserHeaderCard(readOnly = readOnly, onClick = { onNavigate(Route.Profile) })

        if (!readOnly) {
            SectionTitle("工作空间")
            MeGroup(
                listOf(
                    MeEntry(
                        icon = Icons.Outlined.SwapHoriz,
                        title = "切换工作空间",
                        subtitle = Prefs.tenantName ?: "未选择",
                    ) { onNavigate(Route.Workspaces) },
                    MeEntry(
                        icon = Icons.Outlined.Group,
                        title = "成员管理",
                        subtitle = "邀请成员并分配角色",
                    ) { onNavigate(Route.Members) },
                    MeEntry(
                        icon = Icons.Outlined.Key,
                        title = "API Key",
                        subtitle = "只读接口的访问凭据",
                    ) { onNavigate(Route.ApiKey) },
                    MeEntry(
                        icon = Icons.Outlined.Storage,
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
                            icon = Icons.Outlined.ManageAccounts,
                            title = "用户管理",
                            subtitle = "平台账号、角色与密码重置",
                        ) { onNavigate(Route.AdminUsers) },
                        MeEntry(
                            icon = Icons.Outlined.History,
                            title = "审计日志",
                            subtitle = "平台操作留痕",
                        ) { onNavigate(Route.AdminAudit) },
                        MeEntry(
                            icon = Icons.Outlined.MonitorHeart,
                            title = "同步健康中心",
                            subtitle = "检查服务链路并切换服务器节点",
                        ) { onNavigate(Route.SyncHealth) },
                    )
                )
            }
        }

        SectionTitle("其他")
        MeGroup(
            listOf(
                MeEntry(
                    icon = Icons.Outlined.Settings,
                    title = "设置",
                    subtitle = "外观、服务器与应用内更新",
                ) { onNavigate(Route.Settings) },
                MeEntry(
                    icon = Icons.AutoMirrored.Outlined.Logout,
                    title = "退出登录",
                    subtitle = if (readOnly) "清除本地登录密钥，需要重新绑定" else "清除本地会话，需要重新输入密码",
                    danger = true,
                ) { confirmLogout = true },
            )
        )

        Spacer(Modifier.height(24.dp))
    }

    if (confirmLogout) {
        AlertDialog(
            onDismissRequest = { confirmLogout = false },
            icon = { Icon(Icons.AutoMirrored.Outlined.Logout, contentDescription = null) },
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
                        Prefs.clearSession()
                        ApiClient.invalidate()
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

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .clickable(enabled = !readOnly, onClick = onClick),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.onPrimary),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    initialOf(name),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
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
                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = .8f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    "当前工作空间：${if (readOnly) Prefs.tenantId ?: "未选择" else Prefs.tenantName ?: "未选择"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = .8f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (!readOnly) {
                Spacer(Modifier.width(8.dp))
                Icon(
                    Icons.Outlined.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary.copy(alpha = .75f),
                )
            }
        }
    }
}

// ---------------------------------------------------------------- 分组列表

@Composable
private fun MeGroup(entries: List<MeEntry>) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        entries.forEachIndexed { index, entry ->
            if (index > 0) HorizontalDivider(Modifier.padding(horizontal = 16.dp))
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
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (entry.danger) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            entry.icon,
                            contentDescription = null,
                            tint = if (entry.danger) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                    }
                },
                trailingContent = {
                    Icon(
                        Icons.Outlined.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.outline,
                    )
                },
                modifier = Modifier.clickable(onClick = entry.onClick),
            )
        }
    }
}
