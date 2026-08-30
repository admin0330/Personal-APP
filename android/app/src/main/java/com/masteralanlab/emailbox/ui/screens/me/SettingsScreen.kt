package com.masteralanlab.emailbox.ui.screens.me

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.ImageNotSupported
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.material3.LocalContentColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.masteralanlab.emailbox.BuildConfig
import com.masteralanlab.emailbox.data.BiometricUnlock
import com.masteralanlab.emailbox.data.Prefs
import com.masteralanlab.emailbox.data.NavigationSettings
import com.masteralanlab.emailbox.data.SecureMailCache
import com.masteralanlab.emailbox.data.SessionBus
import com.masteralanlab.emailbox.data.remote.ApiClient
import com.masteralanlab.emailbox.notify.MailNotifyService
import com.masteralanlab.emailbox.ui.components.AppTopBar
import com.masteralanlab.emailbox.ui.theme.ThemeSettings
import com.masteralanlab.emailbox.util.formatBytes
import com.masteralanlab.emailbox.ui.components.RadioOptionList
import kotlinx.coroutines.launch
import com.masteralanlab.emailbox.update.UpdateDialog
import com.masteralanlab.emailbox.update.UpdateManager
import com.masteralanlab.emailbox.update.UpdatePhase
import com.masteralanlab.emailbox.update.UpdateUiState
import com.masteralanlab.emailbox.update.UpdateViewModel
import androidx.fragment.app.FragmentActivity

private const val REPO_URL = "https://github.com/admin0330/Personal-APP"

private val THEME_OPTIONS = listOf(
    Prefs.THEME_SYSTEM to "跟随系统",
    Prefs.THEME_LIGHT to "浅色",
    Prefs.THEME_DARK to "深色",
)

/** Android 12（API 31）以下不支持动态取色。 */
private val dynamicColorSupported: Boolean
    get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

// ---------------------------------------------------------------- 页面

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    updateVm: UpdateViewModel,
) {
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val biometricAvailability = remember(context) { BiometricUnlock.availability(context) }
    val hasSession = remember { runCatching { Prefs.hasSession }.getOrDefault(false) }

    // 外观设置直接绑定 ThemeSettings（可观察），切换即时生效并持久化到 Prefs
    var themeMode by remember { mutableStateOf(ThemeSettings.mode) }
    var dynamicColor by remember { mutableStateOf(ThemeSettings.dynamicColor) }
    var blockImages by remember { mutableStateOf(Prefs.blockRemoteImages) }
    var biometricEnabled by remember { mutableStateOf(Prefs.biometricUnlockEnabled) }
    var accountCategory by remember { mutableStateOf(Prefs.accountCategory) }
    var showDomain by remember { mutableStateOf(Prefs.showAccountDomain) }
    var pullMode by remember { mutableStateOf(Prefs.pullMode) }
    var offlineCache by remember { mutableStateOf(Prefs.offlineCacheEnabled) }
    var cacheDays by remember { mutableStateOf(Prefs.cacheDays) }
    var cacheStats by remember { mutableStateOf(Prefs.tenantId?.let(SecureMailCache::stats)) }

    var confirmLogout by remember { mutableStateOf(false) }
    var channelMenu by remember { mutableStateOf(false) }

    val updateState by updateVm.state.collectAsState()

    updateState.info?.let { info ->
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

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = { AppTopBar(title = "设置", onBack = onBack) },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // ---- 外观 ----
            SectionCard(title = "外观", icon = {
                Icon(Icons.Outlined.Palette, contentDescription = null)
            }) {
                Text(
                    "主题模式",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
                RadioOptionList(
                    options = THEME_OPTIONS,
                    selected = themeMode,
                    onSelect = {
                        themeMode = it
                        ThemeSettings.applyMode(it)
                    },
                )
                HorizontalDivider()
                ListItem(
                    headlineContent = { Text("Material You 动态取色") },
                    supportingContent = {
                        Text(
                            if (dynamicColorSupported) {
                                "从系统壁纸中提取配色"
                            } else {
                                "仅 Android 12 及以上支持，当前系统版本不可用"
                            }
                        )
                    },
                    trailingContent = {
                        Switch(
                            checked = dynamicColor && dynamicColorSupported,
                            enabled = dynamicColorSupported,
                            onCheckedChange = {
                                dynamicColor = it
                                ThemeSettings.applyDynamicColor(it)
                            },
                        )
                    },
                )
            }

            SectionCard(title = "底部导航", icon = {
                Icon(Icons.Outlined.SwapVert, contentDescription = null)
            }) {
                NavigationSettings.order.forEachIndexed { index, id ->
                    if (index > 0) HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                    ListItem(
                        headlineContent = { Text(when (id) { "ledger" -> "记账"; "notes" -> "笔记"; else -> "邮箱" }) },
                        supportingContent = { Text("第 ${index + 1} 项") },
                        trailingContent = {
                            Row {
                                IconButton(enabled = index > 0, onClick = { NavigationSettings.move(id, -1) }) {
                                    Icon(Icons.Outlined.KeyboardArrowUp, contentDescription = "上移")
                                }
                                IconButton(enabled = index < NavigationSettings.order.lastIndex, onClick = { NavigationSettings.move(id, 1) }) {
                                    Icon(Icons.Outlined.KeyboardArrowDown, contentDescription = "下移")
                                }
                            }
                        },
                    )
                }
                Text(
                    "“我的”固定在末尾，其余入口调整后立即生效。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                )
            }

            // ---- 邮件 ----
            SectionCard(title = "邮件", icon = {
                Icon(Icons.Outlined.ImageNotSupported, contentDescription = null)
            }) {
                ListItem(
                    headlineContent = { Text("阻断远程图片") },
                    supportingContent = {
                        Text("默认阻断邮件正文里的远程图片，防止被发件人追踪；关闭后会直接加载。")
                    },
                    trailingContent = {
                        Switch(
                            checked = blockImages,
                            onCheckedChange = {
                                blockImages = it
                                Prefs.blockRemoteImages = it
                            },
                        )
                    },
                )
            }

            SectionCard(title = "加密离线缓存", icon = {
                Icon(Icons.Outlined.Storage, contentDescription = null)
            }) {
                ListItem(
                    headlineContent = { Text("缓存最近邮件") },
                    supportingContent = { Text("正文与摘要使用 Android Keystore 加密，仅保存在本机且不参与云备份") },
                    trailingContent = {
                        Switch(checked = offlineCache, onCheckedChange = {
                            offlineCache = it; Prefs.offlineCacheEnabled = it
                            if (!it) { SecureMailCache.clear(Prefs.tenantId); cacheStats = Prefs.tenantId?.let(SecureMailCache::stats) }
                        })
                    },
                )
                HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                Text("保留时间", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                RadioOptionList(
                    options = listOf("7" to "7 天", "30" to "30 天", "90" to "90 天"),
                    selected = cacheDays.toString(),
                    onSelect = { cacheDays = it.toInt(); Prefs.cacheDays = cacheDays },
                )
                val stats = cacheStats
                ListItem(
                    headlineContent = { Text("缓存占用") },
                    supportingContent = { Text(if (stats == null) "暂无缓存" else "${stats.messages} 封 · ${stats.accounts} 个邮箱 · ${formatBytes(stats.bytes)}") },
                    trailingContent = {
                        TextButton(onClick = {
                            SecureMailCache.clear(Prefs.tenantId)
                            cacheStats = Prefs.tenantId?.let(SecureMailCache::stats)
                            scope.launch { snackbar.showSnackbar("离线缓存已清理") }
                        }) { Text("清理") }
                    },
                )
                HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                ListItem(
                    headlineContent = { Text("桌面小组件") },
                    supportingContent = { Text("长按桌面空白处 → 小组件 → Ym1r；只显示缓存未读数与同步状态") },
                )
            }

            // ---- 安全 ----
            SectionCard(title = "安全", icon = {
                Icon(Icons.Outlined.Lock, contentDescription = null)
            }) {
                ListItem(
                    headlineContent = { Text("指纹/设备密码解锁") },
                    supportingContent = {
                        Text(
                            when {
                                !hasSession -> "登录后可开启应用解锁"
                                !biometricAvailability.available -> biometricAvailability.reason
                                else -> "冷启动或后台超过 5 分钟需要验证；设备密码可作为备用"
                            }
                        )
                    },
                    trailingContent = {
                        Switch(
                            checked = biometricEnabled && biometricAvailability.available,
                            enabled = hasSession && biometricAvailability.available,
                            onCheckedChange = { enabled ->
                                if (!enabled) {
                                    biometricEnabled = false
                                    Prefs.biometricUnlockEnabled = false
                                } else {
                                    val activity = context as? FragmentActivity
                                    if (activity == null) {
                                        scope.launch { snackbar.showSnackbar("当前页面无法启动系统解锁") }
                                    } else {
                                        BiometricUnlock.authenticate(
                                            activity = activity,
                                            onSuccess = {
                                                biometricEnabled = true
                                                Prefs.biometricUnlockEnabled = true
                                            },
                                            onFailure = { reason ->
                                                scope.launch { snackbar.showSnackbar(reason) }
                                            },
                                        )
                                    }
                                }
                            },
                        )
                    },
                )
            }

            // ---- 分类 ----
            SectionCard(title = "分类") {
                RadioOptionList(
                    options = listOf(
                        Prefs.ACCOUNT_CATEGORY_OFF to "关闭",
                        Prefs.ACCOUNT_CATEGORY_DOMAIN to "按域名分类",
                    ),
                    selected = accountCategory,
                    onSelect = {
                        accountCategory = it
                        Prefs.accountCategory = it
                    },
                    // 选中「按域名分类」时，该行右侧出现「是否显示域名」开关：
                    // 关闭后主页行标题只显示 @ 之前的本地部分，长邮箱名不被截断
                    optionTrailing = { value ->
                        if (value == Prefs.ACCOUNT_CATEGORY_DOMAIN) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "显示域名",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Spacer(Modifier.width(6.dp))
                                Switch(
                                    checked = showDomain,
                                    onCheckedChange = {
                                        showDomain = it
                                        Prefs.showAccountDomain = it
                                    },
                                )
                            }
                        }
                    },
                )
                Text(
                    "仅影响账号列表展示，不会修改服务器分组。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }

            // ---- 拉取设置 ----
            SectionCard(title = "拉取设置") {
                RadioOptionList(
                    options = listOf(
                        Prefs.PULL_MODE_LOCAL to "本地拉取（App 主动请求）",
                        Prefs.PULL_MODE_SERVER to "阿里云服务器拉取（推荐）",
                    ),
                    selected = pullMode,
                    onSelect = {
                        pullMode = it
                        Prefs.pullMode = it
                        // 服务器拉取依赖常驻通知服务；本地拉取即停服省电
                        MailNotifyService.sync(context)
                    },
                )
                Text(
                    "两种模式都只通过 Emailbox HTTPS；本地模式进入页面或手动刷新，服务器模式由通知服务预取。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }

            // ---- 应用内更新 ----
            SectionCard(
                title = "应用内更新",
                icon = {
                    Icon(Icons.Outlined.SystemUpdate, contentDescription = null)
                },
                titleTrailing = {
                    Box {
                        IconButton(onClick = { channelMenu = true }) {
                            Icon(Icons.Outlined.Settings, contentDescription = "更新渠道设置")
                        }
                        DropdownMenu(
                            expanded = channelMenu,
                            onDismissRequest = { channelMenu = false },
                        ) {
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        "稳定版" + if (Prefs.updateChannel == Prefs.UPDATE_CHANNEL_STABLE) "（当前）" else "",
                                    )
                                },
                                onClick = {
                                    Prefs.updateChannel = Prefs.UPDATE_CHANNEL_STABLE
                                    channelMenu = false
                                    scope.launch { snackbar.showSnackbar("更新渠道：稳定版") }
                                },
                            )
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        "测试版" + if (Prefs.updateChannel == Prefs.UPDATE_CHANNEL_TEST) "（当前）" else "",
                                    )
                                },
                                onClick = {
                                    Prefs.updateChannel = Prefs.UPDATE_CHANNEL_TEST
                                    channelMenu = false
                                    scope.launch { snackbar.showSnackbar("更新渠道：测试版") }
                                },
                            )
                        }
                    }
                },
            ) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                "当前版本",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Text(
                                "${BuildConfig.VERSION_NAME}（${BuildConfig.VERSION_CODE}）",
                                style = MaterialTheme.typography.titleSmall,
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Button(
                            enabled = updateState.phase != UpdatePhase.Checking,
                            onClick = { updateVm.check(manual = true) },
                        ) {
                            Text(if (updateState.phase == UpdatePhase.Checking) "检查中…" else "检查更新")
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "更新渠道：" + UpdateManager.channelLabel() +
                            if (Prefs.updateChannel == Prefs.UPDATE_CHANNEL_TEST) "（可能包含未充分验证的版本）" else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    UpdateStatusBlock(
                        state = updateState,
                        onInstall = {
                            if (!UpdateManager.canInstall(context)) {
                                UpdateManager.openInstallPermissionSetting(context)
                            } else {
                                updateVm.launchInstall(context)
                            }
                        },
                    )

                    HorizontalDivider(Modifier.padding(vertical = 8.dp))
                    ListItem(
                        headlineContent = { Text("清理更新缓存") },
                        supportingContent = {
                            Text(
                                "已下载安装包与断点文件共 " +
                                    formatBytes(UpdateManager.updateCacheSize(context)) +
                                    "；清理后下次更新将重新下载",
                            )
                        },
                        trailingContent = {
                            TextButton(onClick = {
                                val freed = UpdateManager.clearUpdateCache(context)
                                scope.launch {
                                    snackbar.showSnackbar(
                                        if (freed > 0) "已清理 " + formatBytes(freed) else "没有可清理的缓存",
                                    )
                                }
                            }) { Text("清理") }
                        },
                    )
                }
            }

            // ---- 关于 ----
            SectionCard(title = "关于", icon = {
                Icon(Icons.Outlined.Info, contentDescription = null)
            }) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        "Ym1r 是你的个人邮件与记账中枢，连接自托管 Emailbox 服务端，" +
                            "所有邮箱数据与凭据都保存在你的服务器上，不会经过任何第三方。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "仓库地址：$REPO_URL",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // ---- 退出登录 ----
            Button(
                onClick = { confirmLogout = true },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                ),
            ) {
                Icon(Icons.AutoMirrored.Outlined.Logout, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("退出登录")
            }

            Spacer(Modifier.height(20.dp))
        }
    }

    if (confirmLogout) {
        AlertDialog(
            onDismissRequest = { confirmLogout = false },
            title = { Text("退出登录") },
            text = { Text("退出后需要重新输入用户名和密码才能继续使用。") },
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

// ---------------------------------------------------------------- 通用分区

@Composable
private fun SectionCard(
    title: String,
    icon: @Composable () -> Unit = {},
    /** 显示在标题行右侧（如设置齿轮）；不传则标题占满。 */
    titleTrailing: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.fillMaxWidth()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = if (titleTrailing != null) 4.dp else 16.dp, top = 14.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(40.dp).clip(MaterialTheme.shapes.small).background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.primary) { icon() }
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.weight(1f))
                titleTrailing?.invoke()
            }
            content()
        }
    }
}

// ---------------------------------------------------------------- 更新状态

private class UpdateHint(val text: String, val isError: Boolean)

@Composable
private fun UpdateStatusBlock(
    state: UpdateUiState,
    onInstall: () -> Unit,
) {
    // 手动检查没有新版本时 error 里是「当前已是最新版本」，这是成功语义，按提示展示
    val hint: UpdateHint? = when (state.phase) {
        UpdatePhase.Checking -> UpdateHint("正在检查更新…", false)
        UpdatePhase.Downloading -> UpdateHint("下载中 ${(state.progress * 100).toInt()}%", false)
        UpdatePhase.Verifying -> UpdateHint("正在校验安装包…", false)
        UpdatePhase.Done -> UpdateHint("安装包已就绪，点击安装即可完成升级", false)
        UpdatePhase.Failed -> UpdateHint(state.error ?: "更新失败", true)
        UpdatePhase.Idle -> state.error?.takeIf { it.isNotBlank() }?.let { UpdateHint(it, false) }
    }

    if (hint == null) return

    Spacer(Modifier.height(12.dp))
    if (state.phase == UpdatePhase.Downloading) {
        LinearProgressIndicator(
            progress = { state.progress },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(6.dp))
    }
    Text(
        hint.text,
        style = MaterialTheme.typography.bodyMedium,
        color = if (hint.isError) {
            MaterialTheme.colorScheme.error
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
    )
    if (state.phase == UpdatePhase.Done) {
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onInstall) { Text("立即安装") }
    }
}
