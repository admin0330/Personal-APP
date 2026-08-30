package com.masteralanlab.emailbox.ui.screens.accounts

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import android.app.Application
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DriveFileMove
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.ManageSearch
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.ToggleOff
import androidx.compose.material.icons.outlined.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.zIndex
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.masteralanlab.emailbox.data.AccountsCache
import com.masteralanlab.emailbox.data.Prefs
import com.masteralanlab.emailbox.data.MailPreloadCache
import com.masteralanlab.emailbox.data.RefreshBus
import com.masteralanlab.emailbox.data.remote.ApiClient
import com.masteralanlab.emailbox.data.remote.ApiResult
import com.masteralanlab.emailbox.data.remote.BatchIdsRequest
import com.masteralanlab.emailbox.data.remote.BatchMoveRequest
import com.masteralanlab.emailbox.data.remote.BatchProxyRequest
import com.masteralanlab.emailbox.data.remote.BatchStatusRequest
import com.masteralanlab.emailbox.data.remote.ExportAccountsRequest
import com.masteralanlab.emailbox.data.remote.MailAccount
import com.masteralanlab.emailbox.data.remote.MailGroup
import com.masteralanlab.emailbox.data.remote.apiCall
import com.masteralanlab.emailbox.data.remote.apiCallUnit
import com.masteralanlab.emailbox.ui.components.AppTopBar
import com.masteralanlab.emailbox.ui.components.MainTopBar
import com.masteralanlab.emailbox.ui.components.DropdownField
import com.masteralanlab.emailbox.ui.components.EmptyBox
import com.masteralanlab.emailbox.ui.components.ErrorBox
import com.masteralanlab.emailbox.ui.components.GroupPicker
import com.masteralanlab.emailbox.ui.components.Labels
import com.masteralanlab.emailbox.ui.components.LoadingBox
import com.masteralanlab.emailbox.ui.components.StatusChip
import com.masteralanlab.emailbox.ui.components.groupTint
import com.masteralanlab.emailbox.ui.components.statusContainer
import com.masteralanlab.emailbox.ui.components.statusContent
import com.masteralanlab.emailbox.ui.nav.Route
import com.masteralanlab.emailbox.ui.screens.oauth.ReauthorizeDialog
import com.masteralanlab.emailbox.util.FileSharing
import com.masteralanlab.emailbox.util.formatShortTime
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

private const val PAGE_SIZE = 50

data class AccountsFilter(
    val q: String = "",
    val groupId: String? = null,
    val status: String? = null,
    val refreshStatus: String? = null,
    val provider: String? = null,
    val sort: String = "sort_order",
    val order: String = "desc",
)

data class AccountsState(
    val groups: List<MailGroup> = emptyList(),
    val items: List<MailAccount> = emptyList(),
    val page: Int = 1,
    val pages: Int = 1,
    val total: Int = 0,
    val firstLoad: Boolean = true,
    val refreshing: Boolean = false,
    val loadingMore: Boolean = false,
    val acting: Boolean = false,
    val error: String? = null,
    val filter: AccountsFilter = AccountsFilter(),
    val selected: Set<String> = emptySet(),
    val selecting: Boolean = false,
)

class AccountsViewModel(app: Application) : AndroidViewModel(app) {

    private val _state = MutableStateFlow(AccountsState())
    val state: MutableStateFlow<AccountsState> get() = _state

    private val _message = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val message: SharedFlow<String> = _message.asSharedFlow()

    private val _exported = MutableSharedFlow<File>(extraBufferCapacity = 2)
    val exported: SharedFlow<File> = _exported.asSharedFlow()

    private val _reauth = MutableSharedFlow<MailAccount>(extraBufferCapacity = 1)
    val reauth: SharedFlow<MailAccount> = _reauth.asSharedFlow()

    init {
        loadGroups()
        loadCachedThenRefresh()
    }

    /**
     * 主页秒开：先直显磁盘缓存的账号列表（无网络等待），再后台向服务器刷新。
     * 只在默认视图（无筛选）下使用缓存——筛选结果与缓存语义不同，直接走网络。
     */
    private fun loadCachedThenRefresh() {
        val tenant = Prefs.tenantId
        if (!tenant.isNullOrBlank() && _state.value.filter == AccountsFilter()) {
            val cached = AccountsCache.load(getApplication(), tenant)
            if (!cached.isNullOrEmpty()) {
                _state.update {
                    it.copy(
                        items = cached,
                        total = cached.size,
                        firstLoad = false,
                    )
                }
            }
        }
        load(silent = true)
    }

    fun loadGroups() = viewModelScope.launch {
        val tenant = Prefs.tenantId
        if (tenant.isNullOrBlank()) {
            _state.update { it.copy(error = "未选择工作空间") }
            return@launch
        }
        // 分组只用于筛选与批量移动，失败不阻塞主列表
        when (val r = apiCall { groups(tenant) }) {
            is ApiResult.Success -> _state.update { it.copy(groups = r.data) }
            else -> Unit
        }
    }

    /** 请求代际号：筛选变化/下拉刷新与加载更多并发时，过期响应不允许写回（避免旧筛选的页混进新列表）。 */
    private var loadSeq = 0

    /**
     * silent = true：进入页面/后台同步用的静默刷新，不点亮刷新动画（体验要求：
     * 从导航进「邮箱」不打扰，只有用户手动下拉或点刷新按钮才出现动画）。
     */
    fun load(reset: Boolean = true, silent: Boolean = false) = viewModelScope.launch {
        val seq = ++loadSeq
        val tenant = Prefs.tenantId
        if (tenant.isNullOrBlank()) {
            _state.update { it.copy(firstLoad = false, error = "未选择工作空间，请在「我的 → 切换工作空间」里选择") }
            return@launch
        }
        val page = if (reset) 1 else _state.value.page + 1
        _state.update {
            it.copy(
                refreshing = reset && !silent,
                loadingMore = !reset,
                error = if (reset) null else it.error,
            )
        }
        val f = _state.value.filter
        when (val r = apiCall {
            accounts(
                tenantId = tenant,
                q = f.q.ifBlank { null },
                groupId = f.groupId,
                status = f.status,
                refreshStatus = f.refreshStatus,
                provider = f.provider,
                sort = f.sort,
                order = f.order,
                page = page,
                limit = PAGE_SIZE,
            )
        }) {
            is ApiResult.Success -> if (seq == loadSeq) {
                val paged = r.data
                _state.update {
                    // 刷新结果与当前列表完全一致时只收掉刷新标记，不重建列表——
                    // 避免每次刷新都整页重排、滚动位置跳动
                    if (reset && it.items == paged.items) {
                        it.copy(
                            page = paged.pagination?.page ?: page,
                            pages = paged.pagination?.pages ?: 1,
                            total = paged.pagination?.total ?: it.total,
                            firstLoad = false,
                            refreshing = false,
                            loadingMore = false,
                            error = null,
                            selected = emptySet(),
                        )
                    } else {
                        val merged = if (reset) paged.items else it.items + paged.items
                        it.copy(
                            items = merged,
                            page = paged.pagination?.page ?: page,
                            pages = paged.pagination?.pages ?: 1,
                            total = paged.pagination?.total ?: merged.size,
                            firstLoad = false,
                            refreshing = false,
                            loadingMore = false,
                            error = null,
                            selected = if (reset) emptySet() else it.selected,
                        )
                    }
                }
                if (reset) {
                    // 默认视图的首页结果落盘，供下次打开秒开
                    if (f == AccountsFilter()) AccountsCache.save(getApplication(), tenant, paged.items)
                    MailPreloadCache.prefetch(viewModelScope, tenant, paged.items)
                }
            }

            is ApiResult.Failure -> if (seq == loadSeq) _state.update {
                it.copy(firstLoad = false, refreshing = false, loadingMore = false, error = r.message)
            }
        }
    }

    fun loadMore() {
        val s = _state.value
        if (s.loadingMore || s.refreshing || s.firstLoad) return
        if (s.page >= s.pages) return
        load(reset = false)
    }

    fun updateFilter(f: AccountsFilter) {
        _state.update { it.copy(filter = f) }
        load()
    }

    fun toggleSelect(id: String) = _state.update { s ->
        val next = if (id in s.selected) s.selected - id else s.selected + id
        s.copy(selected = next, selecting = next.isNotEmpty())
    }

    fun selectAll() = _state.update { s ->
        val all = s.items.map { it.id }.toSet()
        if (s.selected.containsAll(all)) s.copy(selected = emptySet(), selecting = false)
        else s.copy(selected = all, selecting = true)
    }

    fun clearSelection() = _state.update { it.copy(selected = emptySet(), selecting = false) }

    fun refreshOne(account: MailAccount) = viewModelScope.launch {
        if (Prefs.apiKeyMode) return@launch
        val tenant = Prefs.tenantId ?: return@launch
        _state.update { it.copy(acting = true) }
        when (val r = apiCallUnit { refreshToken(tenant, account.id) }) {
            is ApiResult.Success -> {
                _message.tryEmit("${account.email} 刷新成功")
                load()
            }

            is ApiResult.Failure -> _message.tryEmit(r.message)
        }
        _state.update { it.copy(acting = false) }
    }

    fun batchMove(groupId: String) = viewModelScope.launch {
        if (Prefs.apiKeyMode) return@launch
        val tenant = Prefs.tenantId ?: return@launch
        val ids = _state.value.selected.toList()
        _state.update { it.copy(acting = true) }
        when (val r = apiCall { batchMove(tenant, BatchMoveRequest(ids, groupId)) }) {
            is ApiResult.Success -> _message.tryEmit("已移动 ${r.data.succeeded} 个，失败 ${r.data.failed} 个")
            is ApiResult.Failure -> _message.tryEmit(r.message)
        }
        clearSelection()
        _state.update { it.copy(acting = false) }
        load()
    }

    fun batchStatus(status: String) = viewModelScope.launch {
        if (Prefs.apiKeyMode) return@launch
        val tenant = Prefs.tenantId ?: return@launch
        val ids = _state.value.selected.toList()
        _state.update { it.copy(acting = true) }
        when (val r = apiCall { batchStatus(tenant, BatchStatusRequest(ids, status)) }) {
            is ApiResult.Success -> _message.tryEmit("已更新 ${r.data.succeeded} 个，失败 ${r.data.failed} 个")
            is ApiResult.Failure -> _message.tryEmit(r.message)
        }
        clearSelection()
        _state.update { it.copy(acting = false) }
        load()
    }

    fun batchProxy(proxy: String, fb1: String?, fb2: String?) = viewModelScope.launch {
        if (Prefs.apiKeyMode) return@launch
        val tenant = Prefs.tenantId ?: return@launch
        val ids = _state.value.selected.toList()
        _state.update { it.copy(acting = true) }
        when (val r = apiCall { batchProxy(tenant, BatchProxyRequest(ids, proxy, fb1, fb2)) }) {
            is ApiResult.Success -> _message.tryEmit("已更新 ${r.data.succeeded} 个，失败 ${r.data.failed} 个")
            is ApiResult.Failure -> _message.tryEmit(r.message)
        }
        clearSelection()
        _state.update { it.copy(acting = false) }
        load()
    }

    fun batchDelete() = viewModelScope.launch {
        if (Prefs.apiKeyMode) return@launch
        val tenant = Prefs.tenantId ?: return@launch
        val ids = _state.value.selected.toList()
        _state.update { it.copy(acting = true) }
        when (val r = apiCall { batchDelete(tenant, BatchIdsRequest(ids)) }) {
            is ApiResult.Success -> _message.tryEmit("已删除 ${r.data.succeeded} 个，失败 ${r.data.failed} 个")
            is ApiResult.Failure -> _message.tryEmit(r.message)
        }
        clearSelection()
        _state.update { it.copy(acting = false) }
        load()
    }

    fun exportSelected() = export("selected", _state.value.selected.toList())
    fun exportAll() = export("all", emptyList())

    private fun export(scope: String, ids: List<String>) = viewModelScope.launch {
        if (Prefs.apiKeyMode) return@launch
        val tenant = Prefs.tenantId ?: return@launch
        _state.update { it.copy(acting = true) }
        runCatching {
            val resp = ApiClient.service().exportAccounts(tenant, ExportAccountsRequest(scope, emptyList(), ids))
            if (!resp.isSuccessful) error("导出失败：HTTP ${resp.code()}")
            val body = resp.body() ?: error("导出失败：空响应")
            val name = resp.headers()["Content-Disposition"]
                ?.substringAfter("filename=", "")
                ?.trim('"')
                .takeIf { it.isNullOrBlank().not() } ?: "accounts.txt"
            FileSharing.save(getApplication(), body, name)
        }.onSuccess { file ->
            _exported.tryEmit(file)
        }.onFailure { e ->
            _message.tryEmit(e.message ?: "导出失败")
        }
        _state.update { it.copy(acting = false) }
    }

    fun requestReauth(account: MailAccount) { _reauth.tryEmit(account) }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun AccountsScreen(onNavigate: (String) -> Unit) {
    val vm: AccountsViewModel = viewModel()
    val state by vm.state.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current
    val readOnly = Prefs.apiKeyMode

    var showFilters by remember { mutableStateOf(false) }
    var searchVisible by remember { mutableStateOf(false) }
    var searchText by remember { mutableStateOf(state.filter.q) }
    val searchFocus = remember { FocusRequester() }
    var fabMenu by remember { mutableStateOf(false) }
    var batchAction by remember { mutableStateOf<BatchAction?>(null) }
    var reauthTarget by remember { mutableStateOf<MailAccount?>(null) }

    val listState = rememberLazyListState()

    // 长按进入选择模式后，系统返回（含左滑手势）先清空选择，而不是退出应用
    BackHandler(enabled = state.selecting) { vm.clearSelection() }
    val shouldLoadMore by remember {
        derivedStateOf {
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            last >= listState.layoutInfo.totalItemsCount - 5
        }
    }

    LaunchedEffect(vm) {
        vm.message.collect { snackbar.showSnackbar(it) }
    }
    LaunchedEffect(vm) {
        vm.exported.collect { file ->
            FileSharing.share(context, file, FileSharing.guessMime(file.name))
        }
    }
    LaunchedEffect(vm) {
        vm.reauth.collect { reauthTarget = it }
    }
    LaunchedEffect(vm) {
        RefreshBus.events.collect { if (it == RefreshBus.Kind.Accounts) vm.load(silent = true) }
    }
    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) vm.loadMore()
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            if (state.selecting) {
                AppTopBar(
                    title = "已选择 ${state.selected.size} 个",
                    onBack = { vm.clearSelection() },
                    actions = {
                        IconButton(onClick = { vm.selectAll() }) {
                            Icon(Icons.Outlined.SelectAll, contentDescription = "全选/取消")
                        }
                    },
                )
            } else {
                MainTopBar(
                    title = "邮箱",
                    navigationIcon = {
                        // 搜索入口收进左上角：点击下拉/收起搜索栏，收起时清掉过滤词
                        IconButton(onClick = {
                            val opening = !searchVisible
                            searchVisible = opening
                            if (!opening && searchText.isNotEmpty()) {
                                searchText = ""
                                vm.updateFilter(state.filter.copy(q = ""))
                            }
                        }) {
                            Icon(
                                if (searchVisible) Icons.Outlined.Close else Icons.Outlined.Search,
                                contentDescription = if (searchVisible) "关闭搜索" else "搜索",
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = { onNavigate(Route.MailSearch) }) {
                            Icon(Icons.Outlined.ManageSearch, contentDescription = "离线邮件搜索")
                        }
                        IconButton(onClick = { showFilters = !showFilters }) {
                            Icon(Icons.Outlined.FilterList, contentDescription = "筛选")
                        }
                        IconButton(onClick = { vm.load() }) {
                            Icon(Icons.Outlined.Refresh, contentDescription = "刷新")
                        }
                    },
                )
            }
        },
        floatingActionButton = {
            if (!readOnly && !state.selecting) {
                Box {
                    FloatingActionButton(onClick = { fabMenu = true }) {
                        Icon(Icons.Outlined.Add, contentDescription = "添加")
                    }
                    DropdownMenu(expanded = fabMenu, onDismissRequest = { fabMenu = false }) {
                        DropdownMenuItem(
                            text = { Text("手动添加账号") },
                            onClick = { fabMenu = false; onNavigate(Route.accountEdit()) },
                            leadingIcon = { Icon(Icons.Outlined.Add, null) },
                        )
                        DropdownMenuItem(
                            text = { Text("批量导入账号") },
                            onClick = { fabMenu = false; onNavigate(Route.accountImport()) },
                            leadingIcon = { Icon(Icons.Outlined.Upload, null) },
                        )
                    }
                }
            }
        },
        bottomBar = {
            if (!readOnly && state.selecting) {
                BatchBar(
                    count = state.selected.size,
                    acting = state.acting,
                    onMove = { batchAction = BatchAction.Move },
                    onStatus = { batchAction = BatchAction.Status },
                    onProxy = { batchAction = BatchAction.Proxy },
                    onDelete = { batchAction = BatchAction.Delete },
                    onExport = { vm.exportSelected() },
                )
            }
        },
    ) { padding ->
        Column(Modifier.padding(padding)) {
            // 展开后等输入框完成挂载再聚焦：FocusRequester 过早调用会直接崩掉进程
            LaunchedEffect(searchVisible) {
                if (searchVisible) {
                    delay(120)
                    runCatching { searchFocus.requestFocus() }
                }
            }
            AnimatedVisibility(visible = searchVisible) {
                OutlinedTextField(
                    value = searchText,
                    onValueChange = {
                        searchText = it
                        vm.updateFilter(state.filter.copy(q = it))
                    },
                    placeholder = { Text("搜索邮箱或备注") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .focusRequester(searchFocus),
                )
            }

            if (showFilters) {
                FilterPanel(
                    groups = state.groups,
                    filter = state.filter,
                    onChange = { vm.updateFilter(it) },
                )
            }

            if (state.acting) LinearProgressIndicator(Modifier.fillMaxWidth())

            when {
                state.firstLoad -> LoadingBox()
                state.error != null && state.items.isEmpty() ->
                    ErrorBox(state.error!!) { vm.load() }

                state.items.isEmpty() -> EmptyBox(
                    "还没有邮箱账号",
                    Icons.Outlined.Inbox,
                ) {
                    if (!readOnly) {
                        Row {
                            TextButton(onClick = { onNavigate(Route.accountImport()) }) { Text("批量导入") }
                            TextButton(onClick = { onNavigate(Route.accountEdit()) }) { Text("手动添加") }
                        }
                    }
                }

                else -> {
                    val pullState = rememberPullToRefreshState()
                    PullToRefreshBox(
                        isRefreshing = state.refreshing,
                        onRefresh = { vm.load(silent = false) },
                        state = pullState,
                        // 刷新圈停在分类标题那一行（标题行高 40dp），而不是默认的 80dp 深处
                        indicator = {
                            PullToRefreshDefaults.Indicator(
                                state = pullState,
                                isRefreshing = state.refreshing,
                                // 平行上移：刷新圈悬在列表上缘与顶栏之间，不压住「邮箱账户」卡片
                                modifier = Modifier
                                    .align(Alignment.TopCenter)
                                    .offset(y = (-52).dp)
                                    .zIndex(4f),
                                threshold = 56.dp,
                            )
                        },
                    ) {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(bottom = 96.dp),
                        ) {
                            item(key = "overview") { AccountOverviewCard(state) }
                            val byDomain = Prefs.accountCategory == Prefs.ACCOUNT_CATEGORY_DOMAIN
                            val displayEmail: (String) -> String = { email ->
                                if (byDomain && !Prefs.showAccountDomain) email.substringBefore('@') else email
                            }
                            val sections = if (byDomain) {
                                state.items.groupBy { accountDomain(it.email) }.toList()
                            } else {
                                listOf("" to state.items)
                            }
                            sections.forEach { (domain, accounts) ->
                                if (byDomain) {
                                    item(key = "domain:$domain") {
                                        Text(
                                            domain,
                                            style = MaterialTheme.typography.titleSmall,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                                        )
                                    }
                                }
                                items(accounts, key = { it.id }) { acc ->
                                    AccountRow(
                                        account = acc,
                                        displayEmail = displayEmail(acc.email),
                                        readOnly = readOnly,
                                        groupName = state.groups.firstOrNull { it.id == acc.group_id }?.name,
                                        groupColor = state.groups.firstOrNull { it.id == acc.group_id }?.color,
                                        selected = acc.id in state.selected,
                                        selecting = state.selecting,
                                        onClick = {
                                            if (state.selecting) vm.toggleSelect(acc.id)
                                            else onNavigate(Route.mailbox(acc.id, acc.email))
                                        },
                                        onLongClick = { if (!readOnly) vm.toggleSelect(acc.id) },
                                        onRefresh = { vm.refreshOne(acc) },
                                        onReauthorize = { vm.requestReauth(acc) },
                                        onEdit = { onNavigate(Route.accountEdit(accountId = acc.id)) },
                                        onViewMessages = { onNavigate(Route.mailbox(acc.id, acc.email)) },
                                    )
                                }
                            }
                            if (state.page < state.pages) {
                                item {
                                    Box(Modifier.fillMaxWidth().padding(16.dp), Alignment.Center) {
                                        androidx.compose.material3.CircularProgressIndicator(Modifier.size(24.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    when (batchAction) {
        BatchAction.Move -> MoveDialog(
            groups = state.groups,
            onDismiss = { batchAction = null },
            onConfirm = { batchAction = null; vm.batchMove(it) },
        )

        BatchAction.Status -> StatusDialog(
            onDismiss = { batchAction = null },
            onConfirm = { batchAction = null; vm.batchStatus(it) },
        )

        BatchAction.Proxy -> ProxyDialog(
            onDismiss = { batchAction = null },
            onConfirm = { a, b, c -> batchAction = null; vm.batchProxy(a, b, c) },
        )

        BatchAction.Delete -> ConfirmDialog(
            title = "删除 ${state.selected.size} 个账号？",
            text = "账号及其凭据会被删除，无法恢复。",
            confirmText = "删除",
            onDismiss = { batchAction = null },
            onConfirm = { batchAction = null; vm.batchDelete() },
        )

        null -> Unit
    }

    reauthTarget?.let { acc ->
        ReauthorizeDialog(
            accountId = acc.id,
            email = acc.email,
            onDismiss = { reauthTarget = null },
            onDone = {
                reauthTarget = null
                scope.launch { snackbar.showSnackbar("$it 已重新授权") }
                vm.load()
            },
        )
    }
}

@Composable
private fun AccountOverviewCard(state: AccountsState) {
    val activeLoaded = state.items.count { it.status == "active" }
    val providerCount = state.items.map { it.provider.ifBlank { accountDomain(it.email) } }.distinct().size
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        ),
        shape = MaterialTheme.shapes.large,
    ) {
        Column(Modifier.fillMaxWidth().padding(20.dp)) {
            Text("邮箱账户", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimary.copy(alpha = .82f))
            Spacer(Modifier.height(12.dp))
            Text("${state.total} 个邮箱", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("已加载 ${state.items.size}", style = MaterialTheme.typography.labelMedium)
                Text("可用 $activeLoaded · 来源 $providerCount", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

private fun accountDomain(email: String): String = email.substringAfter('@', "")
    .trim()
    .lowercase()
    .ifBlank { "其他" }

private enum class BatchAction { Move, Status, Proxy, Delete }

@Composable
private fun BatchBar(
    count: Int,
    acting: Boolean,
    onMove: () -> Unit,
    onStatus: () -> Unit,
    onProxy: () -> Unit,
    onDelete: () -> Unit,
    onExport: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        listOf(
            Icons.Outlined.DriveFileMove to "移动",
            Icons.Outlined.ToggleOff to "状态",
            Icons.Outlined.Shield to "代理",
            Icons.Outlined.FileDownload to "导出",
            Icons.Outlined.Delete to "删除",
        ).forEachIndexed { i, (icon, label) ->
            val action = when (i) {
                0 -> onMove
                1 -> onStatus
                2 -> onProxy
                3 -> onExport
                else -> onDelete
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                IconButton(onClick = action, enabled = !acting && count > 0) {
                    Icon(icon, contentDescription = label)
                }
                Text(label, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AccountRow(
    account: MailAccount,
    displayEmail: String? = null,
    readOnly: Boolean,
    groupName: String?,
    groupColor: String?,
    selected: Boolean,
    selecting: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onRefresh: () -> Unit,
    onReauthorize: () -> Unit,
    onEdit: () -> Unit,
    onViewMessages: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    val failed = account.last_refresh_status == "failed"

    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
    ListItem(
        modifier = Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick),
        leadingContent = {
            if (selecting) {
                Checkbox(checked = selected, onCheckedChange = { onLongClick() })
            } else {
                Icon(
                    Icons.Outlined.Inbox,
                    contentDescription = null,
                    tint = groupTint(groupColor),
                )
            }
        },
        headlineContent = {
            Text(
                displayEmail ?: account.email,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyLarge,
            )
        },
        supportingContent = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusChip(
                        Labels.accountStatus(account.status),
                        statusContainer(account.status),
                        statusContent(account.status),
                    )
                    Spacer(Modifier.width(6.dp))
                    StatusChip(
                        Labels.refreshStatus(account.last_refresh_status),
                        statusContainer(account.last_refresh_status),
                        statusContent(account.last_refresh_status),
                    )
                    if (!groupName.isNullOrBlank()) {
                        Spacer(Modifier.width(6.dp))
                        Text(
                            groupName,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (failed && !account.last_refresh_error.isNullOrBlank()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Outlined.ErrorOutline,
                            null,
                            Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.error,
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            Labels.errorKind(account.last_refresh_error_kind)
                                .ifBlank { account.last_refresh_error },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                if (!account.remark.isNullOrBlank()) {
                    Text(
                        account.remark!!,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    formatShortTime(account.last_refresh_at).ifBlank { "—" },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Box {
                    IconButton(onClick = { menu = true }) {
                        Icon(Icons.Outlined.MoreVert, contentDescription = "更多")
                    }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(
                            text = { Text("查看收件箱") },
                            onClick = { menu = false; onViewMessages() },
                            leadingIcon = { Icon(Icons.Outlined.Inbox, null) },
                        )
                        if (!readOnly) {
                            DropdownMenuItem(
                                text = { Text("刷新令牌") },
                                onClick = { menu = false; onRefresh() },
                                leadingIcon = { Icon(Icons.Outlined.Refresh, null) },
                            )
                            DropdownMenuItem(
                                text = { Text("重新授权") },
                                onClick = { menu = false; onReauthorize() },
                                leadingIcon = { Icon(Icons.Outlined.Key, null) },
                            )
                            DropdownMenuItem(
                                text = { Text("编辑账号") },
                                onClick = { menu = false; onEdit() },
                                leadingIcon = { Icon(Icons.Outlined.CheckCircle, null) },
                            )
                        }
                    }
                }
            }
        },
    )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterPanel(
    groups: List<MailGroup>,
    filter: AccountsFilter,
    onChange: (AccountsFilter) -> Unit,
) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        GroupPicker(
            groups = groups,
            selectedId = filter.groupId,
            onSelect = { onChange(filter.copy(groupId = it)) },
        )
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth()) {
            DropdownField(
                label = "状态",
                options = listOf(
                    null to "不限",
                    "active" to "正常",
                    "disabled" to "已停用",
                    "banned" to "已封禁",
                ),
                selected = filter.status,
                onSelect = { onChange(filter.copy(status = it)) },
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            DropdownField(
                label = "刷新结果",
                options = listOf(
                    null to "不限",
                    "success" to "成功",
                    "failed" to "失败",
                    "never" to "未刷新",
                ),
                selected = filter.refreshStatus,
                onSelect = { onChange(filter.copy(refreshStatus = it)) },
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth()) {
            DropdownField(
                label = "服务商",
                options = listOf(
                    null to "不限",
                    "outlook" to "Outlook",
                    "gmail" to "Gmail",
                    "qq" to "QQ",
                    "163" to "163",
                    "126" to "126",
                    "yahoo" to "Yahoo",
                    "aliyun" to "阿里",
                    "custom" to "自定义",
                ),
                selected = filter.provider,
                onSelect = { onChange(filter.copy(provider = it)) },
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            DropdownField(
                label = "排序",
                options = listOf(
                    "sort_order" to "自定义",
                    "created_at" to "创建时间",
                    "email" to "邮箱",
                    "last_refresh_at" to "刷新时间",
                ),
                selected = filter.sort,
                onSelect = { onChange(filter.copy(sort = it ?: "sort_order")) },
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = filter.order == "desc",
                onClick = { onChange(filter.copy(order = "desc")) },
                label = { Text("降序") },
            )
            FilterChip(
                selected = filter.order == "asc",
                onClick = { onChange(filter.copy(order = "asc")) },
                label = { Text("升序") },
            )
            FilterChip(
                selected = filter.groupId == null && filter.status == null &&
                    filter.refreshStatus == null && filter.provider == null,
                onClick = {
                    onChange(AccountsFilter(q = filter.q, sort = filter.sort, order = filter.order))
                },
                label = { Text("清除筛选") },
                leadingIcon = { Icon(Icons.Outlined.Close, null, Modifier.size(16.dp)) },
            )
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun MoveDialog(
    groups: List<MailGroup>,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var target by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("移动到分组") },
        text = { GroupPicker(groups = groups, selectedId = target, onSelect = { target = it }, allowEmpty = false) },
        confirmButton = {
            TextButton(enabled = target != null, onClick = { target?.let(onConfirm) }) { Text("移动") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun StatusDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var status by remember { mutableStateOf("active") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("修改状态") },
        text = {
            DropdownField(
                label = "状态",
                options = listOf("active" to "正常", "disabled" to "已停用", "banned" to "已封禁"),
                selected = status,
                onSelect = { status = it ?: "active" },
            )
        },
        confirmButton = { TextButton(onClick = { onConfirm(status) }) { Text("确定") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun ProxyDialog(onDismiss: () -> Unit, onConfirm: (String, String?, String?) -> Unit) {
    var main by remember { mutableStateOf("") }
    var fb1 by remember { mutableStateOf("") }
    var fb2 by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("设置代理") },
        text = {
            Column {
                Text(
                    "留空表示清空该账号的代理。三个字段都会整体覆盖，支持 {mail} 模板。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(main, { main = it }, label = { Text("主代理") }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(fb1, { fb1 = it }, label = { Text("备用代理 1") }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(fb2, { fb2 = it }, label = { Text("备用代理 2") }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(main, fb1, fb2) }) { Text("确定") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun ConfirmDialog(
    title: String,
    text: String,
    confirmText: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirmText) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}
