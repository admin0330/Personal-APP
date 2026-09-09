package com.masteralanlab.emailbox.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.masteralanlab.emailbox.data.Prefs
import com.masteralanlab.emailbox.data.SessionBus
import com.masteralanlab.emailbox.ui.nav.Route
import com.masteralanlab.emailbox.ui.theme.IosContinuousCornerShape
import com.masteralanlab.emailbox.ui.theme.LocalDarkTheme
import com.masteralanlab.emailbox.ui.theme.LocalYm1rColors
import com.masteralanlab.emailbox.ui.theme.ThemeSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * X App 导航抽屉视图规范：
 * 1. 顶部 Header：大头像 + 昵称/身份 + 空间切换胶囊；
 * 2. 中部清晰业务分组：资料、服务、空间与偏好；
 * 3. 底部快捷工具栏：明暗主题切换 + 经典/清透切换 + 退出登录。
 */
@Composable
fun XNavigationDrawerContent(
    onNavigate: (String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalYm1rColors.current
    val dark = LocalDarkTheme.current
    val identity = Prefs.username?.takeIf { it.isNotBlank() }
        ?: Prefs.userEmail?.takeIf { it.isNotBlank() }
        ?: "未登录用户"
    val userEmail = Prefs.userEmail?.takeIf { it.isNotBlank() } ?: "未绑定邮箱"
    val workspace = Prefs.tenantName?.takeIf { it.isNotBlank() } ?: "默认空间"

    Surface(
        modifier = modifier
            .fillMaxHeight()
            .fillMaxWidth(),
        shape = IosContinuousCornerShape(topEnd = 24.dp, bottomEnd = 24.dp),
        color = colors.surface,
        tonalElevation = 2.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            // Header 个人资料区
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    UserAvatarButton(
                        onClick = {
                            onClose()
                            onNavigate(Route.Profile)
                        },
                        contentDescription = "打开个人资料",
                    )

                    // 空间指示器胶囊
                    Surface(
                        onClick = {
                            onClose()
                            onNavigate(Route.Workspaces)
                        },
                        shape = IosContinuousCornerShape(12.dp),
                        color = colors.surfaceRaised,
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                Ym1rIcons.Globe,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = colors.accent,
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = workspace,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = colors.textPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                Text(
                    text = identity,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = userEmail,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            HorizontalDivider(thickness = 0.5.dp, color = colors.separator)

            // 滚动导航列表
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 8.dp),
            ) {
                DrawerSectionHeader("账户与资料")
                DrawerNavItem("个人资料", Ym1rIcons.User) { onClose(); onNavigate(Route.Profile) }
                DrawerNavItem("成员管理", Ym1rIcons.Users) { onClose(); onNavigate(Route.Members) }
                DrawerNavItem("工作空间", Ym1rIcons.Globe) { onClose(); onNavigate(Route.Workspaces) }

                Spacer(Modifier.height(8.dp))
                HorizontalDivider(thickness = 0.5.dp, color = colors.separator, modifier = Modifier.padding(horizontal = 20.dp))
                Spacer(Modifier.height(8.dp))

                DrawerSectionHeader("空间与安全")
                DrawerNavItem("空间配额", Ym1rIcons.Activity) { onClose(); onNavigate(Route.Quota) }
                DrawerNavItem("API 密钥", Ym1rIcons.Key) { onClose(); onNavigate(Route.ApiKey) }
                DrawerNavItem("令牌管理", Ym1rIcons.Shield) { onClose(); onNavigate(Route.Tokens) }
                DrawerNavItem("同步健康", Ym1rIcons.RefreshCw) { onClose(); onNavigate(Route.SyncHealth) }
                DrawerNavItem("审计日志", Ym1rIcons.FileText) { onClose(); onNavigate(Route.AdminAudit) }

                Spacer(Modifier.height(8.dp))
                HorizontalDivider(thickness = 0.5.dp, color = colors.separator, modifier = Modifier.padding(horizontal = 20.dp))
                Spacer(Modifier.height(8.dp))

                DrawerSectionHeader("系统与设置")
                DrawerNavItem("偏好设置", Ym1rIcons.Settings) { onClose(); onNavigate(Route.Settings) }
                DrawerNavItem("刷新日志", Ym1rIcons.Sliders) { onClose(); onNavigate(Route.RefreshLogs) }
            }

            HorizontalDivider(thickness = 0.5.dp, color = colors.separator)

            // 底部快捷切换与退出栏
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // 主题明暗快速切换
                Surface(
                    onClick = {
                        val newMode = if (dark) Prefs.THEME_LIGHT else Prefs.THEME_DARK
                        ThemeSettings.applyMode(newMode)
                    },
                    shape = CircleShape,
                    color = colors.surfaceRaised,
                    modifier = Modifier.size(38.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            if (dark) Ym1rIcons.Eye else Ym1rIcons.EyeOff,
                            contentDescription = "切换明暗主题",
                            modifier = Modifier.size(18.dp),
                            tint = colors.textPrimary,
                        )
                    }
                }

                // 风格清透/经典快速切换
                Surface(
                    onClick = {
                        val newStyle = if (ThemeSettings.designStyle == Prefs.STYLE_APPLE) Prefs.STYLE_CLAUDE else Prefs.STYLE_APPLE
                        ThemeSettings.applyDesignStyle(newStyle)
                    },
                    shape = IosContinuousCornerShape(10.dp),
                    color = colors.surfaceRaised,
                ) {
                    Text(
                        text = if (ThemeSettings.designStyle == Prefs.STYLE_APPLE) "清透" else "经典",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.textPrimary,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    )
                }

                // 退出登录
                Surface(
                    onClick = {
                        onClose()
                        SessionBus.emitExpired("用户主动退出登录")
                    },
                    shape = CircleShape,
                    color = colors.surfaceRaised,
                    modifier = Modifier.size(38.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Ym1rIcons.LogOut,
                            contentDescription = "退出登录",
                            modifier = Modifier.size(18.dp),
                            tint = colors.textSecondary,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DrawerSectionHeader(title: String) {
    val colors = LocalYm1rColors.current
    Text(
        text = title,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        color = colors.textSecondary,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
    )
}

@Composable
private fun DrawerNavItem(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
) {
    val colors = LocalYm1rColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = colors.textPrimary,
        )
        Spacer(Modifier.width(16.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = colors.textPrimary,
            modifier = Modifier.weight(1f),
        )
        Icon(
            Ym1rIcons.ChevronRight,
            contentDescription = null,
            modifier = Modifier.size(14.dp),
            tint = colors.textSecondary.copy(alpha = 0.5f),
        )
    }
}
