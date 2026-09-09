package com.masteralanlab.emailbox.ui.screens.me

import com.masteralanlab.emailbox.ui.components.Ym1rIcons
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import com.masteralanlab.emailbox.ui.components.appleClickable
import com.masteralanlab.emailbox.ui.components.appleListRowClickable
import com.masteralanlab.emailbox.ui.theme.LocalDarkTheme
import com.masteralanlab.emailbox.ui.theme.LocalYm1rColors
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.Lifecycle
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import com.masteralanlab.emailbox.BuildConfig
import com.masteralanlab.emailbox.data.BiometricUnlock
import com.masteralanlab.emailbox.data.MailTranslation
import com.masteralanlab.emailbox.data.NavigationSettings
import com.masteralanlab.emailbox.data.Prefs
import com.masteralanlab.emailbox.data.SecureMailCache
import com.masteralanlab.emailbox.data.SessionBus
import com.masteralanlab.emailbox.data.SessionManager
import com.masteralanlab.emailbox.notify.MailNotifyService
import com.masteralanlab.emailbox.ui.components.AppleColors
import com.masteralanlab.emailbox.ui.components.AppleIconSquircle
import com.masteralanlab.emailbox.ui.components.AppleListRow
import com.masteralanlab.emailbox.ui.components.AppleListSection
import com.masteralanlab.emailbox.ui.components.AppleSearchField
import com.masteralanlab.emailbox.ui.components.AppleSegmentedControl
import com.masteralanlab.emailbox.ui.components.AppleToggle
import com.masteralanlab.emailbox.ui.theme.ThemeSettings
import com.masteralanlab.emailbox.update.UpdateManager
import com.masteralanlab.emailbox.update.UpdatePhase
import com.masteralanlab.emailbox.update.UpdateUiState
import com.masteralanlab.emailbox.update.UpdateViewModel
import com.masteralanlab.emailbox.util.formatBytes
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val REPO_URL = "https://github.com/admin0330/Personal-APP"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    updateVm: UpdateViewModel,
    onNavigateToProfile: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val biometricAvailability = remember(context) { BiometricUnlock.availability(context) }
    val hasSession = remember { runCatching { Prefs.hasSession }.getOrDefault(false) }

    // 外观与设计体系设置直接绑定 ThemeSettings 与 Prefs（可观察），切换即时生效并持久化
    val themeMode = ThemeSettings.mode
    val designStyle = ThemeSettings.designStyle
    val blockImages = Prefs.blockRemoteImages
    var biometricEnabled by remember { mutableStateOf(Prefs.biometricUnlockEnabled) }
    val accountCategory = Prefs.accountCategory
    val showDomain = Prefs.showAccountDomain
    val pullMode = Prefs.pullMode
    var offlineCache by remember { mutableStateOf(Prefs.offlineCacheEnabled) }
    var cacheDays by remember { mutableStateOf(Prefs.cacheDays) }
    var cacheStats by remember { mutableStateOf(Prefs.tenantId?.let(SecureMailCache::stats)) }
    var translationModelBusy by remember { mutableStateOf(false) }
    var notificationsGranted by remember(context) {
        mutableStateOf(MailNotifyService.notificationsAllowed(context))
    }
    var serviceRunning by remember { mutableStateOf(MailNotifyService.isRunning()) }
    var batteryOptimizationIgnored by remember(context) {
        mutableStateOf(MailNotifyService.batteryOptimizationIgnored(context))
    }

    var searchQuery by remember { mutableStateOf("") }
    var confirmLogout by remember { mutableStateOf(false) }

    val updateState by updateVm.state.collectAsState()

    val requestNotificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        notificationsGranted = granted
        MailNotifyService.sync(context)
        serviceRunning = MailNotifyService.isRunning()
    }

    LaunchedEffect(pullMode) {
        delay(400)
        serviceRunning = MailNotifyService.isRunning()
        batteryOptimizationIgnored = MailNotifyService.batteryOptimizationIgnored(context)
    }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        notificationsGranted = MailNotifyService.notificationsAllowed(context)
        batteryOptimizationIgnored = MailNotifyService.batteryOptimizationIgnored(context)
        serviceRunning = MailNotifyService.isRunning()
    }

    val isDark = LocalDarkTheme.current
    val ym1rColors = LocalYm1rColors.current
    val pageBg = ym1rColors.background

    fun matchesQuery(vararg texts: String?): Boolean {
        if (searchQuery.isBlank()) return true
        val q = searchQuery.trim()
        return texts.any { it?.contains(q, ignoreCase = true) == true }
    }

    Scaffold(
        containerColor = pageBg,
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            // ---- iOS 27 Beta 原生顶栏导航与大标题 ----
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(pageBg),
            ) {
                // 返回导航按钮
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .appleClickable(pressedScale = 0.94f, pressedAlpha = 0.65f, onClick = onBack)
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Ym1rIcons.ArrowLeft,
                            contentDescription = "返回",
                            tint = AppleColors.Blue,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            "返回",
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontWeight = FontWeight.Normal,
                                fontSize = 17.sp,
                                fontFamily = FontFamily.SansSerif,
                            ),
                            color = AppleColors.Blue,
                        )
                    }
                }

                // 大标题
                Text(
                    text = "设置",
                    style = MaterialTheme.typography.headlineLarge.copy(
                        fontSize = 34.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.SansSerif,
                        letterSpacing = (-0.5).sp,
                    ),
                    color = ym1rColors.textPrimary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
                )

                // 搜索栏
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 10.dp),
                ) {
                    AppleSearchField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = "搜索",
                        leadingIcon = {
                            Icon(
                                Ym1rIcons.Search,
                                contentDescription = null,
                                tint = AppleColors.Gray,
                                modifier = Modifier.size(18.dp),
                            )
                        },
                    )
                }
            }

            // ---- 列表项容器 (Inset Grouped) ----
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                // ---- 账户与安全 个人卡片 ----
                if (matchesQuery("个人", "账户", "账号", "安全", Prefs.username, Prefs.userEmail, Prefs.tenantName)) {
                    AppleListSection {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .then(
                                    if (onNavigateToProfile != null) {
                                        Modifier.clickable(onClick = onNavigateToProfile)
                                    } else Modifier
                                )
                                .padding(horizontal = 16.dp, vertical = 13.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            UserAvatar(
                                name = Prefs.username.orEmpty(),
                                avatarPath = Prefs.avatarPath,
                                modifier = Modifier.size(56.dp),
                            )
                            Spacer(Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = Prefs.username?.takeIf { it.isNotBlank() } ?: "未设置用户名",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontSize = 19.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        fontFamily = FontFamily.SansSerif,
                                    ),
                                    color = ym1rColors.textPrimary,
                                )
                                Spacer(Modifier.height(3.dp))
                                Text(
                                    text = (Prefs.userEmail?.takeIf { it.isNotBlank() }
                                        ?: (Prefs.tenantName ?: Prefs.tenantId ?: "个人账号")) + " · 账户与安全",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontSize = 12.5.sp,
                                        fontFamily = FontFamily.SansSerif,
                                    ),
                                    color = AppleColors.Gray,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            Icon(
                                Ym1rIcons.ChevronRight,
                                contentDescription = null,
                                tint = AppleColors.Gray.copy(alpha = 0.6f),
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }

                // ---- Section 1: 外观与交互体系 ----
                val showDesign = matchesQuery("设计体系", "清透", "经典", "主题", "风格")
                val showTheme = matchesQuery("外观", "深色", "浅色", "暗黑", "跟随系统")
                val showDock = matchesQuery("底部导航", "dock", "导航", "概览", "记账", "笔记", "邮箱")

                if (showDesign || showTheme || showDock) {
                    AppleListSection(
                        title = "通用与外观",
                        footer = if (designStyle == Prefs.STYLE_APPLE) {
                            "清透：冷静蓝色、清晰层次与平滑圆角。"
                        } else {
                            "经典：温暖陶土色、柔和背景与平滑圆角。"
                        },
                    ) {
                        if (showDesign) {
                            AppleListRow(
                                title = "主题风格",
                                subtitle = if (designStyle == Prefs.STYLE_APPLE) "清透（默认）" else "经典",
                                icon = { AppleIconSquircle(icon = Ym1rIcons.Sliders, backgroundColor = AppleColors.Pink) },
                                control = {
                                    Box(Modifier.fillMaxWidth()) {
                                        AppleSegmentedControl(
                                            options = listOf("清透", "经典"),
                                            selectedIndex = if (designStyle == Prefs.STYLE_APPLE) 0 else 1,
                                            onSelect = { index ->
                                                val style = if (index == 0) Prefs.STYLE_APPLE else Prefs.STYLE_CLAUDE
                                                ThemeSettings.applyDesignStyle(style)
                                            },
                                        )
                                    }
                                },
                                showDivider = showTheme || showDock,
                             )
                        }
                        if (showTheme) {
                            AppleListRow(
                                title = "外观模式",
                                subtitle = when (themeMode) {
                                    Prefs.THEME_LIGHT -> "始终浅色"
                                    Prefs.THEME_DARK -> "始终深色"
                                    else -> "跟随系统设置"
                                },
                                icon = { AppleIconSquircle(icon = Ym1rIcons.Sliders, backgroundColor = AppleColors.Indigo) },
                                control = {
                                    Box(Modifier.fillMaxWidth()) {
                                        AppleSegmentedControl(
                                            options = listOf("自动", "浅色", "深色"),
                                            selectedIndex = when (themeMode) {
                                                Prefs.THEME_LIGHT -> 1
                                                Prefs.THEME_DARK -> 2
                                                else -> 0
                                            },
                                            onSelect = { index ->
                                                val mode = when (index) {
                                                    1 -> Prefs.THEME_LIGHT
                                                    2 -> Prefs.THEME_DARK
                                                    else -> Prefs.THEME_SYSTEM
                                                }
                                                ThemeSettings.applyMode(mode)
                                            },
                                        )
                                    }
                                },
                                showDivider = showDock,
                            )
                        }
                        if (showDock) {
                            NavigationSettings.order.forEachIndexed { index, id ->
                                val (navIcon, navBg) = when (id) {
                                    "overview" -> Ym1rIcons.Sliders to AppleColors.Teal
                                    "ledger" -> Ym1rIcons.Wallet to AppleColors.Orange
                                    "notes" -> Ym1rIcons.FileText to AppleColors.Yellow
                                    else -> Ym1rIcons.Mail to AppleColors.Blue
                                }
                                AppleListRow(
                                    title = when (id) {
                                        "overview" -> "概览"
                                        "ledger" -> "记账"
                                        "notes" -> "笔记"
                                        else -> "邮箱"
                                    },
                                    subtitle = "Dock 栏第 ${index + 1} 位",
                                    icon = { AppleIconSquircle(icon = navIcon, backgroundColor = navBg) },
                                    trailing = {
                                        Row {
                                            IconButton(enabled = index > 0, onClick = { NavigationSettings.move(id, -1) }) {
                                                Icon(Ym1rIcons.ChevronUp, contentDescription = "上移")
                                            }
                                            IconButton(enabled = index < NavigationSettings.order.lastIndex, onClick = { NavigationSettings.move(id, 1) }) {
                                                Icon(Ym1rIcons.ChevronDown, contentDescription = "下移")
                                            }
                                        }
                                    },
                                    showDivider = index < NavigationSettings.order.lastIndex,
                                )
                            }
                        }
                    }
                }

                // ---- Section 2: 邮件与后台服务 ----
                val showBlockImages = matchesQuery("阻断远程图片", "图片", "追踪", "安全")
                val showCategory = matchesQuery("分类", "域名", "智能分类")
                val showPull = matchesQuery("拉取", "模式", "后台", "服务器", "同步")
                val showNotif = matchesQuery("通知", "权限", "新邮件")
                val showService = matchesQuery("后台运行", "常驻", "同步服务", "电量")

                if (showBlockImages || showCategory || showPull || showNotif || showService) {
                    AppleListSection(
                        title = "邮件与服务",
                        footer = "两种拉取模式都只通过 Emailbox HTTPS；本地模式进入页面或手动刷新，服务器模式由前台受控服务保持实时推送与邮件预加载。",
                    ) {
                        if (showBlockImages) {
                            AppleListRow(
                                title = "阻断远程图片",
                                subtitle = "防止发件人通过图片链接追踪阅读状态",
                                icon = { AppleIconSquircle(icon = Ym1rIcons.Image, backgroundColor = AppleColors.Teal) },
                                trailing = {
                                    AppleToggle(
                                        checked = blockImages,
                                        onCheckedChange = {
                                            Prefs.blockRemoteImages = it
                                        },
                                    )
                                },
                                showDivider = showCategory || showPull || showNotif || showService,
                            )
                        }
                        if (showCategory) {
                            AppleListRow(
                                title = "按域名智能分类",
                                subtitle = "仅影响账号列表展示，不改动服务端",
                                icon = { AppleIconSquircle(icon = Ym1rIcons.Tag, backgroundColor = AppleColors.Purple) },
                                trailing = {
                                    AppleToggle(
                                        checked = accountCategory == Prefs.ACCOUNT_CATEGORY_DOMAIN,
                                        onCheckedChange = { enabled ->
                                            val mode = if (enabled) Prefs.ACCOUNT_CATEGORY_DOMAIN else Prefs.ACCOUNT_CATEGORY_OFF
                                            Prefs.accountCategory = mode
                                        },
                                    )
                                },
                                showDivider = accountCategory == Prefs.ACCOUNT_CATEGORY_DOMAIN || showPull || showNotif || showService,
                            )
                            if (accountCategory == Prefs.ACCOUNT_CATEGORY_DOMAIN) {
                                AppleListRow(
                                    title = "显示域名后缀",
                                    subtitle = "关闭后列表只展示邮箱用户名前缀",
                                    trailing = {
                                        AppleToggle(
                                            checked = showDomain,
                                            onCheckedChange = {
                                                Prefs.showAccountDomain = it
                                            },
                                        )
                                    },
                                    showDivider = showPull || showNotif || showService,
                                )
                            }
                        }
                        if (showPull) {
                            AppleListRow(
                                title = "拉取模式",
                                subtitle = if (pullMode == Prefs.PULL_MODE_SERVER) "服务器受控保持" else "手动与前台拉取",
                                icon = { AppleIconSquircle(icon = Ym1rIcons.Mail, backgroundColor = AppleColors.Blue) },
                                control = {
                                    Box(Modifier.fillMaxWidth()) {
                                        AppleSegmentedControl(
                                            options = listOf("本地拉取", "服务器"),
                                            selectedIndex = if (pullMode == Prefs.PULL_MODE_SERVER) 1 else 0,
                                            onSelect = { idx ->
                                                val mode = if (idx == 1) Prefs.PULL_MODE_SERVER else Prefs.PULL_MODE_LOCAL
                                                Prefs.pullMode = mode
                                                if (mode == Prefs.PULL_MODE_SERVER && !notificationsGranted &&
                                                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                                                ) {
                                                    requestNotificationPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                                                } else {
                                                    MailNotifyService.sync(context)
                                                }
                                            },
                                        )
                                    }
                                },
                                showDivider = showNotif || showService,
                            )
                        }
                        if (showNotif) {
                            AppleListRow(
                                title = "新邮件通知",
                                subtitle = when {
                                    Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU -> "当前系统已受支持"
                                    notificationsGranted -> "已允许接收即时推送"
                                    else -> "未授权；服务器可预加载但无系统横幅"
                                },
                                icon = { AppleIconSquircle(icon = Ym1rIcons.AlertCircle, backgroundColor = AppleColors.Red) },
                                trailing = {
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !notificationsGranted) {
                                        TextButton(onClick = {
                                            requestNotificationPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                                        }) { Text("允许", color = AppleColors.Blue) }
                                    } else {
                                        Text("已开启", style = MaterialTheme.typography.bodyMedium, color = AppleColors.Green)
                                    }
                                },
                                showDivider = showService,
                            )
                        }
                        if (showService) {
                            AppleListRow(
                                title = "后台同步服务",
                                subtitle = when {
                                    pullMode != Prefs.PULL_MODE_SERVER -> "未开启服务器拉取"
                                    !batteryOptimizationIgnored -> "系统电池优化可能抑制同步，建议在系统设置允许后台常驻"
                                    serviceRunning -> "前台守护服务运行中，负责通知与邮件预存"
                                    else -> "已就绪，将在后台自动唤醒"
                                },
                                icon = { AppleIconSquircle(icon = Ym1rIcons.RefreshCw, backgroundColor = AppleColors.Green) },
                                trailing = {
                                    Text(
                                        if (pullMode == Prefs.PULL_MODE_SERVER && serviceRunning) "运行中" else "待机",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = if (pullMode == Prefs.PULL_MODE_SERVER && serviceRunning) {
                                            AppleColors.Green
                                        } else {
                                            AppleColors.Gray
                                        },
                                    )
                                },
                                showDivider = false,
                            )
                        }
                    }
                }

                // ---- Section 3: 隐私、安全与加密离线缓存 ----
                val showBio = matchesQuery("面容", "指纹", "锁", "生物识别", "安全", "密码")
                val showCache = matchesQuery("缓存", "离线", "加密", "keystore", "存储")
                val showTrans = matchesQuery("翻译", "模型", "语言", "英语", "中文")

                if (showBio || showCache || showTrans) {
                    AppleListSection(
                        title = "隐私与存储",
                        footer = "邮件正文与离线摘要使用 Android Keystore 硬件根密钥高阶加密存储，保护敏感数据。",
                    ) {
                        if (showBio) {
                            AppleListRow(
                                title = "面容 / 指纹与密码解锁",
                                subtitle = when {
                                    !hasSession -> "登录后可开启应用级锁屏保护"
                                    !biometricAvailability.available -> biometricAvailability.reason
                                    else -> "冷启动或切后台超 5 分钟需验证身份"
                                },
                                icon = { AppleIconSquircle(icon = Ym1rIcons.Lock, backgroundColor = AppleColors.Green) },
                                trailing = {
                                    AppleToggle(
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
                                showDivider = showCache || showTrans,
                            )
                        }
                        if (showCache) {
                            AppleListRow(
                                title = "Keystore 加密离线缓存",
                                subtitle = "离线免联网即时查阅最近往来邮件",
                                icon = { AppleIconSquircle(icon = Ym1rIcons.Server, backgroundColor = AppleColors.Orange) },
                                trailing = {
                                    AppleToggle(
                                        checked = offlineCache,
                                        onCheckedChange = {
                                            offlineCache = it
                                            Prefs.offlineCacheEnabled = it
                                            if (!it) {
                                                SecureMailCache.clear(Prefs.tenantId)
                                                cacheStats = Prefs.tenantId?.let(SecureMailCache::stats)
                                            }
                                        },
                                    )
                                },
                                showDivider = true,
                            )
                            AppleListRow(
                                title = "缓存保留期限",
                                subtitle = "超出期限的历史邮件自动回收",
                                icon = { AppleIconSquircle(icon = Ym1rIcons.Activity, backgroundColor = AppleColors.Blue) },
                                control = {
                                    Box(Modifier.fillMaxWidth()) {
                                        AppleSegmentedControl(
                                            options = listOf("7天", "30天", "90天"),
                                            selectedIndex = when (cacheDays) {
                                                30 -> 1
                                                90 -> 2
                                                else -> 0
                                             },
                                            onSelect = { idx ->
                                                val days = when (idx) {
                                                    1 -> 30
                                                    2 -> 90
                                                    else -> 7
                                                }
                                                cacheDays = days
                                                Prefs.cacheDays = days
                                            },
                                        )
                                    }
                                },
                                showDivider = true,
                            )
                            val stats = cacheStats
                            AppleListRow(
                                title = "离线缓存空间",
                                subtitle = if (stats == null) "暂无缓存文件" else "${stats.messages} 封邮件 · ${stats.accounts} 个邮箱 · ${formatBytes(stats.bytes)}",
                                icon = { AppleIconSquircle(icon = Ym1rIcons.Trash2, backgroundColor = AppleColors.Gray) },
                                trailing = {
                                    TextButton(onClick = {
                                        SecureMailCache.clear(Prefs.tenantId)
                                        cacheStats = Prefs.tenantId?.let(SecureMailCache::stats)
                                        scope.launch { snackbar.showSnackbar("离线缓存已全部清空") }
                                    }) {
                                        Text("清理", color = AppleColors.Blue)
                                    }
                                },
                                showDivider = showTrans,
                            )
                        }
                        if (showTrans) {
                            AppleListRow(
                                title = "离线翻译 (英中)",
                                subtitle = "设备端完全本地执行，不上传邮件正文",
                                icon = { AppleIconSquircle(icon = Ym1rIcons.Translate, backgroundColor = AppleColors.Teal) },
                                trailing = {
                                    TextButton(
                                        enabled = !translationModelBusy,
                                        onClick = {
                                            translationModelBusy = true
                                            scope.launch {
                                                runCatching { MailTranslation.deleteEnglishChineseModel() }
                                                    .onSuccess { snackbar.showSnackbar("本地翻译模型已删除") }
                                                    .onFailure { snackbar.showSnackbar("模型删除失败") }
                                                translationModelBusy = false
                                            }
                                        },
                                    ) {
                                        Text(
                                            if (translationModelBusy) "处理中…" else "删除模型",
                                            color = if (translationModelBusy) AppleColors.Gray else AppleColors.Red,
                                        )
                                    }
                                },
                                showDivider = false,
                            )
                        }
                    }
                }

                // ---- Section 4: 软件与系统更新 ----
                val showUpdate = matchesQuery("更新", "版本", "升级", "渠道", "测试版", "稳定版")
                if (showUpdate) {
                    AppleListSection(
                        title = "软件更新",
                        footer = "检查更新通过全球高速 CDN 直连获取，支持差量秒级热更与断点续传。",
                    ) {
                        AppleListRow(
                            title = "当前版本",
                            subtitle = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) · " + UpdateManager.channelLabel() +
                                if (Prefs.updateChannel == Prefs.UPDATE_CHANNEL_TEST) "（测试版）" else "（稳定版）",
                            icon = { AppleIconSquircle(icon = Ym1rIcons.Download, backgroundColor = AppleColors.Blue) },
                            trailing = {
                                Button(
                                    enabled = updateState.phase != UpdatePhase.Checking,
                                    onClick = { updateVm.check(manual = true) },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = AppleColors.Blue,
                                        contentColor = Color.White,
                                    ),
                                ) {
                                    Text(if (updateState.phase == UpdatePhase.Checking) "检查中…" else "检查更新")
                                }
                            },
                            showDivider = true,
                        )
                        AppleListRow(
                            title = "更新推送渠道",
                            subtitle = if (Prefs.updateChannel == Prefs.UPDATE_CHANNEL_TEST) "率先体验新功能与 UI 预览" else "常规稳定构建版本",
                            icon = { AppleIconSquircle(icon = Ym1rIcons.Sliders, backgroundColor = AppleColors.Indigo) },
                            control = {
                                Box(Modifier.fillMaxWidth()) {
                                    AppleSegmentedControl(
                                        options = listOf("稳定版", "测试版"),
                                        selectedIndex = if (Prefs.updateChannel == Prefs.UPDATE_CHANNEL_TEST) 1 else 0,
                                        onSelect = { idx ->
                                            val channel = if (idx == 1) Prefs.UPDATE_CHANNEL_TEST else Prefs.UPDATE_CHANNEL_STABLE
                                            Prefs.updateChannel = channel
                                            scope.launch {
                                                snackbar.showSnackbar("更新渠道已切换至：${if (channel == Prefs.UPDATE_CHANNEL_TEST) "测试版" else "稳定版"}")
                                            }
                                        },
                                    )
                                }
                            },
                            showDivider = true,
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
                        AppleListRow(
                            title = "清理更新安装包",
                            subtitle = "已下载安装包与临时断点共 " + formatBytes(UpdateManager.updateCacheSize(context)),
                            icon = { AppleIconSquircle(icon = Ym1rIcons.Trash2, backgroundColor = AppleColors.Gray) },
                            trailing = {
                                TextButton(onClick = {
                                    val freed = UpdateManager.clearUpdateCache(context)
                                    scope.launch {
                                        snackbar.showSnackbar(
                                            if (freed > 0) "已释放 " + formatBytes(freed) else "没有可清理的更新缓存",
                                        )
                                    }
                                }) {
                                    Text("清理", color = AppleColors.Blue)
                                }
                            },
                            showDivider = false,
                        )
                    }
                }

                // ---- Section 5: 关于 ----
                val showAbout = matchesQuery("关于", "ym1r", "仓库", "github", "版本")
                if (showAbout) {
                    AppleListSection(title = "关于") {
                        AppleListRow(
                            title = "Ym1r",
                            subtitle = "个人邮件与资产中枢 · 开源自托管",
                            icon = { AppleIconSquircle(icon = Ym1rIcons.AlertCircle, backgroundColor = AppleColors.Gray) },
                            showDivider = true,
                        )
                        AppleListRow(
                            title = "开源项目",
                            subtitle = REPO_URL,
                            icon = { AppleIconSquircle(icon = Ym1rIcons.ExternalLink, backgroundColor = AppleColors.Blue) },
                            showDivider = false,
                        )
                    }
                }

                // ---- Section 6: 退出登录 ----
                if (matchesQuery("退出", "登出", "注销", "logout")) {
                    AppleListSection {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .appleListRowClickable { confirmLogout = true }
                                .padding(vertical = 14.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                "退出登录",
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    fontFamily = FontFamily.SansSerif,
                                ),
                                color = AppleColors.Red,
                            )
                        }
                    }
                }

                Spacer(Modifier.height(100.dp))
            }
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

// ---------------------------------------------------------------- 更新状态

private class UpdateHint(val text: String, val isError: Boolean)

@Composable
private fun UpdateStatusBlock(
    state: UpdateUiState,
    onInstall: () -> Unit,
) {
    val hint: UpdateHint? = when (state.phase) {
        UpdatePhase.Checking -> UpdateHint("正在检查更新…", false)
        UpdatePhase.Downloading -> UpdateHint("高速下载中 ${(state.progress * 100).toInt()}%", false)
        UpdatePhase.Verifying -> UpdateHint("正在校验安装包哈希…", false)
        UpdatePhase.Done -> UpdateHint("安装包校验通过，点击立即升级", false)
        UpdatePhase.Failed -> UpdateHint(state.error ?: "更新失败", true)
        UpdatePhase.Idle -> state.error?.takeIf { it.isNotBlank() }?.let { UpdateHint(it, false) }
    }

    if (hint == null) return

    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        if (state.phase == UpdatePhase.Downloading) {
            LinearProgressIndicator(
                progress = { state.progress },
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp)),
                color = AppleColors.Blue,
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
            Button(
                onClick = onInstall,
                colors = ButtonDefaults.buttonColors(
                    containerColor = AppleColors.Blue,
                    contentColor = Color.White,
                ),
            ) {
                Text("立即安装")
            }
        }
    }
}
