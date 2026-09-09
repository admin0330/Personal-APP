package com.masteralanlab.emailbox.ui.screens.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import com.masteralanlab.emailbox.ui.components.Ym1rIcons
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.Lifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.masteralanlab.emailbox.data.AccountsCache
import com.masteralanlab.emailbox.data.Prefs
import com.masteralanlab.emailbox.data.SecureMailCache
import com.masteralanlab.emailbox.ui.components.AppleBadge
import com.masteralanlab.emailbox.ui.components.RollingNumber
import com.masteralanlab.emailbox.ui.components.AppleColors
import com.masteralanlab.emailbox.ui.components.AppleHeroCard
import com.masteralanlab.emailbox.ui.components.AppleIconSquircle
import com.masteralanlab.emailbox.ui.components.AppleListRow
import com.masteralanlab.emailbox.ui.components.AppleListSection
import com.masteralanlab.emailbox.ui.components.MainTopBar
import com.masteralanlab.emailbox.ui.components.UserAvatarButton
import com.masteralanlab.emailbox.ui.components.appleClickable
import com.masteralanlab.emailbox.ui.nav.Route

/**
 * 工作台概览：
 * Apple HIG 经典展台 Hero 卡片，搭配 Inset Grouped 分组与功能色微图标。
 */
@Composable
fun HomeOverviewScreen(
    onNavigate: (String) -> Unit,
    onOpenDrawer: (() -> Unit)? = null,
    onOpenAccounts: () -> Unit,
) {
    val context = LocalContext.current
    val tenant = Prefs.tenantId
    var accounts by remember(tenant) { mutableStateOf(tenant?.let { AccountsCache.load(context, it).orEmpty() }.orEmpty()) }
    var cache by remember(tenant) { mutableStateOf(tenant?.let(SecureMailCache::stats)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        accounts = tenant?.let { AccountsCache.load(context, it).orEmpty() }.orEmpty()
        cache = tenant?.let(SecureMailCache::stats)
    }
    val readOnly = Prefs.apiKeyMode
    val workspace = if (readOnly) "只读访问 · 本机概况" else "邮箱与本机数据"
    val syncLabel = if (Prefs.pullMode == Prefs.PULL_MODE_SERVER) "后台同步模式" else "手动同步模式"

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            MainTopBar(
                title = "概览",
                subtitle = workspace,
                navigationIcon = onOpenDrawer?.let { open -> { UserAvatarButton(onClick = open) } },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // 1. 展台式 Hero 卡片
            AppleHeroCard(
                title = Prefs.username?.takeIf { it.isNotBlank() }?.let { "你好，$it" } ?: "欢迎使用 Ym1r",
                subtitle = if (readOnly) "当前处于 API Key 只读模式" else "把需要处理的重要邮件放在眼前",
                tag = workspace,
                trailing = {
                    AppleBadge(
                        text = if (readOnly) "只读" else "已登录",
                        containerColor = if (readOnly) AppleColors.Orange.copy(alpha = 0.15f) else AppleColors.Green.copy(alpha = 0.15f),
                        contentColor = if (readOnly) AppleColors.Orange else AppleColors.Green,
                    )
                },
            ) {
                // 3 列工作空间指标胶囊
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    StatPill(
                        label = "已缓存账号",
                        value = "${accounts.size}",
                        numericValue = accounts.size.toLong(),
                        accentColor = AppleColors.Blue,
                        modifier = Modifier.weight(1f),
                        onClick = onOpenAccounts,
                    )
                    StatPill(
                        label = "缓存未读",
                        value = "${cache?.unread ?: 0}",
                        numericValue = (cache?.unread ?: 0).toLong(),
                        accentColor = AppleColors.MusicRed,
                        modifier = Modifier.weight(1f),
                    )
                    StatPill(
                        label = "本地缓存",
                        value = "${cache?.messages ?: 0}",
                        numericValue = (cache?.messages ?: 0).toLong(),
                        accentColor = AppleColors.Indigo,
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            // 2. 服务与同步状态 (Inset Grouped)
            AppleListSection(
                title = "服务与同步",
                footer = "邮箱通过已配置的服务器同步；这里的数量仅统计本机缓存，不代表服务器全部邮件。",
            ) {
                AppleListRow(
                    title = syncLabel,
                    subtitle = "缓存邮件 ${cache?.messages ?: 0} 封 · 未读 ${cache?.unread ?: 0} 封",
                    icon = {
                        AppleIconSquircle(Ym1rIcons.RefreshCw, AppleColors.Green)
                    },
                    showDivider = true,
                )
                if (!readOnly) {
                    AppleListRow(
                        title = "维护令牌",
                        subtitle = "查看刷新任务状态和失败原因",
                        icon = {
                            AppleIconSquircle(Ym1rIcons.Key, AppleColors.Indigo)
                        },
                        onClick = { onNavigate(Route.Tokens) },
                        showDivider = true,
                    )
                }
                AppleListRow(
                    title = "同步健康中心",
                    subtitle = "检查网络连接、同步状态与错误原因",
                    icon = {
                        AppleIconSquircle(Ym1rIcons.Activity, AppleColors.Red)
                    },
                    onClick = { onNavigate(Route.SyncHealth) },
                    showDivider = false,
                )
            }

            // 3. 快捷操作 (Inset Grouped)
            AppleListSection(
                title = "快捷操作",
            ) {
                if (readOnly) {
                    AppleListRow(
                        title = "搜索邮件",
                        subtitle = "从本机加密缓存即时查找",
                        icon = {
                            AppleIconSquircle(Ym1rIcons.Search, AppleColors.Blue)
                        },
                        onClick = { onNavigate(Route.MailSearch) },
                        showDivider = true,
                    )
                } else {
                    AppleListRow(
                        title = "添加邮箱账号",
                        subtitle = "连接一个新邮箱服务",
                        icon = {
                            AppleIconSquircle(Ym1rIcons.Plus, AppleColors.Blue)
                        },
                        onClick = { onNavigate(Route.accountEdit()) },
                        showDivider = true,
                    )
                    AppleListRow(
                        title = "批量导入账号",
                        subtitle = "支持从文件或列表导入多个账号",
                        icon = {
                            AppleIconSquircle(Ym1rIcons.Upload, AppleColors.Teal)
                        },
                        onClick = { onNavigate(Route.accountImport()) },
                        showDivider = true,
                    )
                    AppleListRow(
                        title = "全局搜索",
                        subtitle = "跨账号搜索本机已缓存的邮件",
                        icon = {
                            AppleIconSquircle(Ym1rIcons.Search, AppleColors.Purple)
                        },
                        onClick = { onNavigate(Route.MailSearch) },
                        showDivider = true,
                    )
                }
                AppleListRow(
                    title = "应用设置",
                    subtitle = "外观、同步、缓存与应用更新",
                    icon = {
                        AppleIconSquircle(Ym1rIcons.Settings, AppleColors.Gray)
                    },
                    onClick = { onNavigate(Route.Settings) },
                    showDivider = false,
                )
            }
        }
    }
}

@Composable
private fun StatPill(
    label: String,
    value: String,
    numericValue: Long? = null,
    accentColor: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val clickModifier = if (onClick != null) {
        Modifier.appleClickable(pressedScale = 0.94f, pressedAlpha = 0.88f, onClick = onClick)
    } else Modifier

    Surface(
        shape = MaterialTheme.shapes.medium,
        color = accentColor.copy(alpha = 0.08f),
        modifier = modifier.then(clickModifier),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.Start,
        ) {
            if (numericValue != null) {
                RollingNumber(
                    value = numericValue,
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        color = accentColor,
                    ),
                    color = accentColor,
                )
            } else {
                Text(
                    text = value,
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        color = accentColor,
                    ),
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp,
                ),
            )
        }
    }
}
