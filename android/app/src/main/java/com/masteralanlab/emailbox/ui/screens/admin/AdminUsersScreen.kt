package com.masteralanlab.emailbox.ui.screens.admin

import android.content.ClipData
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AdminPanelSettings
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.masteralanlab.emailbox.data.Prefs
import com.masteralanlab.emailbox.data.remote.AdminUser
import com.masteralanlab.emailbox.data.remote.ApiResult
import com.masteralanlab.emailbox.data.remote.PlatformStats
import com.masteralanlab.emailbox.data.remote.UpdateUserRequest
import com.masteralanlab.emailbox.data.remote.apiCall
import com.masteralanlab.emailbox.ui.components.AppTopBar
import com.masteralanlab.emailbox.ui.components.DropdownField
import com.masteralanlab.emailbox.ui.components.EmptyBox
import com.masteralanlab.emailbox.ui.components.ErrorBox
import com.masteralanlab.emailbox.ui.components.Labels
import com.masteralanlab.emailbox.ui.components.LoadingBox
import com.masteralanlab.emailbox.ui.components.StatusChip
import com.masteralanlab.emailbox.ui.components.statusContainer
import com.masteralanlab.emailbox.ui.components.statusContent
import com.masteralanlab.emailbox.util.formatShortTime
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** -1 在后端表示「不限」。 */
private fun limitText(value: Int): String = if (value < 0) "不限" else value.toString()

private enum class UserAction {
    Enable, Disable, GrantAdmin, RevokeAdmin, ResetPassword, Delete
}

// ---------------------------------------------------------------- 数据层

class AdminUsersViewModel : ViewModel() {

    private companion object {
        const val PAGE_SIZE = 20
        const val NO_PERMISSION = "需要平台管理员权限"
    }

    private val _stats = MutableStateFlow<PlatformStats?>(null)
    val stats: StateFlow<PlatformStats?> = _stats.asStateFlow()

    private val _statsError = MutableStateFlow<String?>(null)
    val statsError: StateFlow<String?> = _statsError.asStateFlow()

    private val _items = MutableStateFlow<List<AdminUser>>(emptyList())
    val items: StateFlow<List<AdminUser>> = _items.asStateFlow()

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _status = MutableStateFlow<String?>(null)
    val status: StateFlow<String?> = _status.asStateFlow()

    private val _role = MutableStateFlow<String?>(null)
    val role: StateFlow<String?> = _role.asStateFlow()

    private val _loading = MutableStateFlow(true)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    private val _loadingMore = MutableStateFlow(false)
    val loadingMore: StateFlow<Boolean> = _loadingMore.asStateFlow()

    private val _hasMore = MutableStateFlow(false)
    val hasMore: StateFlow<Boolean> = _hasMore.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _moreError = MutableStateFlow<String?>(null)
    val moreError: StateFlow<String?> = _moreError.asStateFlow()

    private val _acting = MutableStateFlow(false)
    val acting: StateFlow<Boolean> = _acting.asStateFlow()

    private val _actionError = MutableStateFlow<String?>(null)
    val actionError: StateFlow<String?> = _actionError.asStateFlow()

    private val _notice = MutableStateFlow<String?>(null)
    val notice: StateFlow<String?> = _notice.asStateFlow()

    private val _tempPassword = MutableStateFlow<String?>(null)
    val tempPassword: StateFlow<String?> = _tempPassword.asStateFlow()

    private var page = 1
    private var pages = 1

    init {
        refresh()
    }

    // ---- 输入 ----

    fun onQueryChange(value: String) {
        _query.value = value
    }

    fun onStatusChange(value: String?) {
        if (_status.value == value) return
        _status.value = value
        reload()
    }

    fun onRoleChange(value: String?) {
        if (_role.value == value) return
        _role.value = value
        reload()
    }

    fun submitSearch() {
        reload()
    }

    // ---- 加载 ----

    fun refresh() {
        viewModelScope.launch {
            _refreshing.value = true
            loadStats()
            page = 1
            loadPage(reset = true)
            _refreshing.value = false
            _loading.value = false
        }
    }

    fun reload() {
        viewModelScope.launch {
            _loading.value = true
            _error.value = null
            page = 1
            loadPage(reset = true)
            _loading.value = false
        }
    }

    fun loadMore() {
        if (_loadingMore.value || _loading.value || !_hasMore.value) return
        viewModelScope.launch {
            _loadingMore.value = true
            _moreError.value = null
            loadPage(reset = false)
            _loadingMore.value = false
        }
    }

    private suspend fun loadStats() {
        if (!Prefs.isPlatformAdmin) {
            _stats.value = null
            _statsError.value = NO_PERMISSION
            return
        }
        when (val r = apiCall { adminStats() }) {
            is ApiResult.Success -> {
                _stats.value = r.data
                _statsError.value = null
            }

            is ApiResult.Failure -> {
                _stats.value = null
                _statsError.value = if (r.httpStatus == 403) NO_PERMISSION else r.message
            }
        }
    }

    private suspend fun loadPage(reset: Boolean) {
        if (!Prefs.isPlatformAdmin) {
            if (reset) {
                _items.value = emptyList()
                _hasMore.value = false
                _error.value = NO_PERMISSION
            } else {
                _moreError.value = NO_PERMISSION
            }
            return
        }
        val keyword = _query.value.trim().ifBlank { null }
        when (val r = apiCall {
            adminUsers(
                q = keyword,
                status = _status.value,
                platformRole = _role.value,
                page = page,
                limit = PAGE_SIZE,
            )
        }) {
            is ApiResult.Success -> {
                val paged = r.data
                val p = paged.pagination
                page = p?.page ?: page
                pages = p?.pages ?: 1
                _items.value = if (reset) paged.items else _items.value + paged.items
                _hasMore.value = page < pages && paged.items.isNotEmpty()
                _error.value = null
                _moreError.value = null
            }

            is ApiResult.Failure -> {
                if (reset) {
                    _items.value = emptyList()
                    _hasMore.value = false
                    _error.value = if (r.httpStatus == 403) NO_PERMISSION else r.message
                } else {
                    _moreError.value = r.message
                }
            }
        }
    }

    /** 操作成功后静默刷新，不打断当前滚动位置之外的体验。 */
    private suspend fun reloadQuiet() {
        page = 1
        loadPage(reset = true)
        loadStats()
    }

    // ---- 操作 ----

    fun setStatus(user: AdminUser, status: String) {
        viewModelScope.launch {
            _acting.value = true
            _actionError.value = null
            when (val r = apiCall { updateAdminUser(user.id, UpdateUserRequest(status = status)) }) {
                is ApiResult.Success -> {
                    _notice.value = if (status == "active") "已启用「${user.username}」" else "已停用「${user.username}」"
                    reloadQuiet()
                }

                is ApiResult.Failure -> _actionError.value = r.message
            }
            _acting.value = false
        }
    }

    fun setPlatformRole(user: AdminUser, role: String) {
        viewModelScope.launch {
            _acting.value = true
            _actionError.value = null
            when (val r = apiCall { updateAdminUser(user.id, UpdateUserRequest(platform_role = role)) }) {
                is ApiResult.Success -> {
                    _notice.value = if (role == "admin") {
                        "已将「${user.username}」设为平台管理员"
                    } else {
                        "已取消「${user.username}」的平台管理员身份"
                    }
                    reloadQuiet()
                }

                is ApiResult.Failure -> _actionError.value = r.message
            }
            _acting.value = false
        }
    }

    fun resetPassword(user: AdminUser) {
        viewModelScope.launch {
            _acting.value = true
            _actionError.value = null
            when (val r = apiCall { resetUserPassword(user.id) }) {
                is ApiResult.Success -> _tempPassword.value = r.data.password
                is ApiResult.Failure -> _actionError.value = r.message
            }
            _acting.value = false
        }
    }

    fun deleteUser(user: AdminUser) {
        viewModelScope.launch {
            _acting.value = true
            _actionError.value = null
            when (val r = apiCall { deleteAdminUser(user.id) }) {
                is ApiResult.Success -> {
                    val deleted = r.data.deleted_accounts
                    _notice.value = if (deleted > 0) {
                        "已删除「${user.username}」，连带删除 $deleted 个邮箱账号"
                    } else {
                        "已删除「${user.username}」"
                    }
                    reloadQuiet()
                }

                is ApiResult.Failure -> _actionError.value = r.message
            }
            _acting.value = false
        }
    }

    // ---- 一次性状态 ----

    fun consumeNotice() {
        _notice.value = null
    }

    fun consumeActionError() {
        _actionError.value = null
    }

    fun consumePassword() {
        _tempPassword.value = null
    }
}

// ---------------------------------------------------------------- 页面

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminUsersScreen(onBack: () -> Unit) {
    val vm: AdminUsersViewModel = viewModel()

    val stats by vm.stats.collectAsState()
    val statsError by vm.statsError.collectAsState()
    val items by vm.items.collectAsState()
    val query by vm.query.collectAsState()
    val status by vm.status.collectAsState()
    val role by vm.role.collectAsState()
    val loading by vm.loading.collectAsState()
    val refreshing by vm.refreshing.collectAsState()
    val loadingMore by vm.loadingMore.collectAsState()
    val hasMore by vm.hasMore.collectAsState()
    val error by vm.error.collectAsState()
    val moreError by vm.moreError.collectAsState()
    val acting by vm.acting.collectAsState()
    val actionError by vm.actionError.collectAsState()
    val notice by vm.notice.collectAsState()
    val tempPassword by vm.tempPassword.collectAsState()

    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(notice) {
        notice?.let {
            snackbar.showSnackbar(it)
            vm.consumeNotice()
        }
    }
    LaunchedEffect(actionError) {
        actionError?.let {
            snackbar.showSnackbar(it)
            vm.consumeActionError()
        }
    }

    val isAdmin = remember { Prefs.isPlatformAdmin }

    var sheetUser by remember { mutableStateOf<AdminUser?>(null) }
    var pending by remember { mutableStateOf<Pair<UserAction, AdminUser>?>(null) }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = { AppTopBar(title = "用户管理", onBack = onBack) },
    ) { padding ->
        if (!isAdmin) {
            Box(Modifier.padding(padding).fillMaxSize()) {
                EmptyBox(
                    text = "需要平台管理员权限\n当前账号无权查看用户管理",
                    icon = Icons.Outlined.Lock,
                )
            }
        } else {
            Column(Modifier.padding(padding).fillMaxSize()) {
                PlatformStatsSection(
                    stats = stats,
                    error = statsError,
                    onRetry = vm::refresh,
                )
                HorizontalDivider()
                UserFilterRow(
                    query = query,
                    onQueryChange = vm::onQueryChange,
                    onSubmit = vm::submitSearch,
                    status = status,
                    onStatusChange = vm::onStatusChange,
                    role = role,
                    onRoleChange = vm::onRoleChange,
                )
                HorizontalDivider()

                Box(Modifier.weight(1f).fillMaxWidth()) {
                    when {
                        loading && items.isEmpty() -> LoadingBox(text = "正在加载用户…")
                        error != null && items.isEmpty() ->
                            ErrorBox(message = error ?: "加载失败", onRetry = vm::reload)
                        items.isEmpty() -> EmptyBox(text = "没有符合条件的用户")
                        else -> PullToRefreshBox(
                            isRefreshing = refreshing,
                            onRefresh = vm::refresh,
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                itemsIndexed(items, key = { _, u -> u.id }) { index, user ->
                                    AdminUserCard(user = user) {
                                        sheetUser = user
                                    }
                                    if (index == items.lastIndex && hasMore) {
                                        LaunchedEffect(items.size) { vm.loadMore() }
                                    }
                                }
                                if (loadingMore || moreError != null) {
                                    item {
                                        LoadMoreFooter(
                                            error = moreError,
                                            loading = loadingMore,
                                            onRetry = vm::loadMore,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    sheetUser?.let { user ->
        UserActionSheet(
            user = user,
            onDismiss = { sheetUser = null },
            onEnable = { pending = UserAction.Enable to user },
            onDisable = { pending = UserAction.Disable to user },
            onGrantAdmin = { pending = UserAction.GrantAdmin to user },
            onRevokeAdmin = { pending = UserAction.RevokeAdmin to user },
            onResetPassword = { pending = UserAction.ResetPassword to user },
            onDelete = { pending = UserAction.Delete to user },
        )
    }

    pending?.let { (action, user) ->
        UserConfirmDialog(
            action = action,
            user = user,
            busy = acting,
            onDismiss = { pending = null },
            onConfirm = {
                pending = null
                sheetUser = null
                when (action) {
                    UserAction.Enable -> vm.setStatus(user, "active")
                    UserAction.Disable -> vm.setStatus(user, "disabled")
                    UserAction.GrantAdmin -> vm.setPlatformRole(user, "admin")
                    UserAction.RevokeAdmin -> vm.setPlatformRole(user, "user")
                    UserAction.ResetPassword -> vm.resetPassword(user)
                    UserAction.Delete -> vm.deleteUser(user)
                }
            },
        )
    }

    tempPassword?.let { pwd ->
        TempPasswordDialog(
            password = pwd,
            onDismiss = vm::consumePassword,
        )
    }
}

// ---------------------------------------------------------------- 概览

@Composable
private fun PlatformStatsSection(
    stats: PlatformStats?,
    error: String?,
    onRetry: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(
            "平台概览",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(8.dp))
        when {
            error != null -> Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.errorContainer,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        error,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onRetry) {
                        Text("重试", color = MaterialTheme.colorScheme.onErrorContainer)
                    }
                }
            }

            stats == null -> Box(Modifier.fillMaxWidth().height(72.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(Modifier.size(24.dp))
            }

            else -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatCard("用户数", stats.user_count.toString(), Modifier.weight(1f))
                    StatCard("停用用户", stats.disabled_user_count.toString(), Modifier.weight(1f))
                    StatCard("管理员", stats.admin_count.toString(), Modifier.weight(1f))
                    StatCard("工作空间", stats.tenant_count.toString(), Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatCard("邮箱账号", stats.account_count.toString(), Modifier.weight(1f))
                    StatCard("封禁账号", stats.banned_account_count.toString(), Modifier.weight(1f))
                    StatCard("今日取件", stats.mail_fetch_today.toString(), Modifier.weight(1f))
                    StatCard("今日刷新", stats.token_refresh_today.toString(), Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun StatCard(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                value,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

// ---------------------------------------------------------------- 筛选

@Composable
private fun UserFilterRow(
    query: String,
    onQueryChange: (String) -> Unit,
    onSubmit: () -> Unit,
    status: String?,
    onStatusChange: (String?) -> Unit,
    role: String?,
    onRoleChange: (String?) -> Unit,
) {
    val focus = LocalFocusManager.current
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            label = { Text("搜索用户名或邮箱") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { onQueryChange(""); onSubmit() }) {
                        Icon(Icons.Outlined.Block, contentDescription = "清空")
                    }
                }
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = {
                onSubmit()
                focus.clearFocus()
            }),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            DropdownField(
                label = "状态",
                options = listOf(null to "全部", "active" to "正常", "disabled" to "停用"),
                selected = status,
                onSelect = onStatusChange,
                modifier = Modifier.weight(1f),
            )
            DropdownField(
                label = "平台角色",
                options = listOf(null to "全部", "user" to "用户", "admin" to "管理员"),
                selected = role,
                onSelect = onRoleChange,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

// ---------------------------------------------------------------- 列表

@Composable
private fun AdminUserCard(user: AdminUser, onClick: () -> Unit) {
    val overQuota = user.over_quota
    OutlinedCard(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    user.username,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f, fill = false),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.width(6.dp))
                StatusChip(
                    text = Labels.accountStatus(user.status),
                    container = statusContainer(user.status),
                    content = statusContent(user.status),
                )
                Spacer(Modifier.width(6.dp))
                if (user.platform_role == "admin") {
                    StatusChip(
                        text = "平台管理员",
                        container = MaterialTheme.colorScheme.tertiaryContainer,
                        content = MaterialTheme.colorScheme.onTertiaryContainer,
                    )
                }
            }
            if (!user.email.isNullOrBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    user.email ?: "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(6.dp))
            InfoText("工作空间", user.tenant_name?.takeIf { it.isNotBlank() } ?: "—")
            InfoText(
                label = "邮箱账号",
                value = "${user.account_count} / ${limitText(user.max_accounts)}",
                highlight = overQuota,
                suffix = if (overQuota) " · 已超额" else "",
            )
            val lastLogin = formatShortTime(user.last_login_at)
            InfoText("最后登录", lastLogin.ifBlank { "从未登录" })
        }
    }
}

@Composable
private fun InfoText(
    label: String,
    value: String,
    suffix: String = "",
    highlight: Boolean = false,
) {
    Row(Modifier.fillMaxWidth().padding(top = 2.dp)) {
        Text(
            "$label：",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            value + suffix,
            style = MaterialTheme.typography.bodySmall,
            color = if (highlight) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun LoadMoreFooter(error: String?, loading: Boolean, onRetry: () -> Unit) {
    Box(
        Modifier.fillMaxWidth().padding(vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (error != null) {
            TextButton(onClick = onRetry) {
                Text("$error · 点击重试", color = MaterialTheme.colorScheme.error)
            }
        } else if (loading) {
            CircularProgressIndicator(Modifier.size(24.dp))
        }
    }
}

// ---------------------------------------------------------------- 操作弹层

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UserActionSheet(
    user: AdminUser,
    onDismiss: () -> Unit,
    onEnable: () -> Unit,
    onDisable: () -> Unit,
    onGrantAdmin: () -> Unit,
    onRevokeAdmin: () -> Unit,
    onResetPassword: () -> Unit,
    onDelete: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState()) {
        Column(Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
            ListItem(
                headlineContent = { Text(user.username, style = MaterialTheme.typography.titleMedium) },
                supportingContent = {
                    Text(
                        user.email?.takeIf { it.isNotBlank() } ?: "未绑定邮箱",
                        style = MaterialTheme.typography.bodySmall,
                    )
                },
            )
            HorizontalDivider()
            if (user.status == "disabled") {
                ActionItem("启用账号", Icons.Outlined.Person, onEnable)
            } else {
                ActionItem("停用账号", Icons.Outlined.Block, onDisable)
            }
            if (user.platform_role == "admin") {
                ActionItem("取消平台管理员", Icons.Outlined.AdminPanelSettings, onRevokeAdmin)
            } else {
                ActionItem("设为平台管理员", Icons.Outlined.AdminPanelSettings, onGrantAdmin)
            }
            ActionItem("重置密码", Icons.Outlined.Lock, onResetPassword)
            ActionItem("删除用户", Icons.Outlined.Delete, onDelete, destructive = true)
        }
    }
}

@Composable
private fun ActionItem(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
    destructive: Boolean = false,
) {
    val color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    ListItem(
        headlineContent = { Text(text, color = color) },
        leadingContent = { Icon(icon, contentDescription = null, tint = color) },
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    )
}

@Composable
private fun UserConfirmDialog(
    action: UserAction,
    user: AdminUser,
    busy: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val isSelf = user.id.isNotBlank() && user.id == Prefs.userId
    val title = when (action) {
        UserAction.Enable -> "启用账号"
        UserAction.Disable -> "停用账号"
        UserAction.GrantAdmin -> "设为平台管理员"
        UserAction.RevokeAdmin -> "取消平台管理员"
        UserAction.ResetPassword -> "重置密码"
        UserAction.Delete -> "删除用户"
    }
    val body = when (action) {
        UserAction.Enable -> "确定启用「${user.username}」吗？启用后该用户可以正常登录。"
        UserAction.Disable -> "确定停用「${user.username}」吗？停用会清空该用户全部会话，需要重新登录。" +
            if (isSelf) "\n\n注意：这是你自己的账号，服务端会拒绝该操作。" else ""
        UserAction.GrantAdmin -> "确定将「${user.username}」设为平台管理员吗？该用户将可以管理平台上的全部用户与套餐。"
        UserAction.RevokeAdmin -> "确定取消「${user.username}」的平台管理员身份吗？\n\n最后一个管理员不可取消，服务端会拒绝该操作。"
        UserAction.ResetPassword -> "确定为「${user.username}」重置密码吗？\n\n系统会生成一个临时密码，且只会显示这一次，请立即复制保存。"
        UserAction.Delete -> "确定删除「${user.username}」吗？\n\n删除会连带软删除其个人工作空间下的全部邮箱账号，此操作不可恢复。" +
            if (isSelf) "\n\n注意：这是你自己的账号，服务端会拒绝该操作。" else ""
    }
    val confirmLabel = when (action) {
        UserAction.Enable -> "启用"
        UserAction.Disable -> "停用"
        UserAction.GrantAdmin -> "设为管理员"
        UserAction.RevokeAdmin -> "取消管理员"
        UserAction.ResetPassword -> "生成临时密码"
        UserAction.Delete -> "删除"
    }
    val destructive = action == UserAction.Delete || action == UserAction.Disable

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(title) },
        text = { Text(body) },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = !busy,
                colors = if (destructive) {
                    ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                } else {
                    ButtonDefaults.textButtonColors()
                },
            ) { Text(confirmLabel) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !busy) { Text("取消") }
        },
    )
}

@Composable
private fun TempPasswordDialog(password: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val doCopy = {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
        if (clipboard != null) {
            clipboard.setPrimaryClip(ClipData.newPlainText("临时密码", password))
            Toast.makeText(context, "已复制到剪贴板", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "复制失败，请手动记录", Toast.LENGTH_SHORT).show()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Outlined.Lock, contentDescription = null) },
        title = { Text("临时密码已生成") },
        text = {
            Column {
                Text("该密码只会出现这一次，关闭后无法再次查看，请立即复制并告知用户。")
                Spacer(Modifier.height(10.dp))
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            password,
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = doCopy) {
                            Icon(Icons.Outlined.ContentCopy, contentDescription = "复制")
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = doCopy) { Text("复制密码") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("关闭") } },
    )
}
