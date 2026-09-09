package com.masteralanlab.emailbox.ui.screens.accounts

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import android.app.Application
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import com.masteralanlab.emailbox.ui.components.appleCombinedClickable
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import com.masteralanlab.emailbox.ui.components.Ym1rIcons
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.zIndex
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.masteralanlab.emailbox.ui.components.AppleColors
import com.masteralanlab.emailbox.ui.components.AppleSearchField
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
import com.masteralanlab.emailbox.ui.components.UserAvatarButton
import com.masteralanlab.emailbox.ui.components.DropdownField
import com.masteralanlab.emailbox.ui.components.EmptyBox
import com.masteralanlab.emailbox.ui.components.ErrorBox
import com.masteralanlab.emailbox.ui.components.GroupPicker
import com.masteralanlab.emailbox.ui.components.Labels
import com.masteralanlab.emailbox.ui.components.LoadingBox
import com.masteralanlab.emailbox.ui.components.ListSkeleton
import com.masteralanlab.emailbox.ui.components.InitialAvatar
import com.masteralanlab.emailbox.ui.components.ProductField
import com.masteralanlab.emailbox.ui.components.ProductSurface
import com.masteralanlab.emailbox.ui.components.StatusChip
import com.masteralanlab.emailbox.ui.components.statusContainer
import com.masteralanlab.emailbox.ui.components.statusContent
import com.masteralanlab.emailbox.ui.nav.Route
import com.masteralanlab.emailbox.ui.screens.oauth.ReauthorizeDialog
import com.masteralanlab.emailbox.util.FileSharing
import com.masteralanlab.emailbox.util.formatShortTime
import com.masteralanlab.emailbox.util.initialOf
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import androidx.compose.foundation.shape.CircleShape

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
            _state.update { it.copy(error = "未选择工作空间，请点击左上角头像在侧栏中选择工作空间") }
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
            _state.update { it.copy(firstLoad = false, error = "未选择工作空间，请点击左上角头像在侧栏中选择工作空间") }
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
        _state.update { it.copy(filter = f, selected = emptySet(), selecting = false) }
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
            _message.tryEmit(com.masteralanlab.emailbox.data.remote.presentableErrorMessage(e.message).ifBlank { "导出失败" })
        }
        _state.update { it.copy(acting = false) }
    }

    fun requestReauth(account: MailAccount) { _reauth.tryEmit(account) }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun AccountsScreen(
    onNavigate: (String) -> Unit,
    onOpenDrawer: (() -> Unit)? = null,
) {
    val vm: AccountsViewModel = viewModel()
    val state by vm.state.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current
    val readOnly = Prefs.apiKeyMode

    var showFilters by rememberSaveable { mutableStateOf(false) }
    var searchVisible by rememberSaveable { mutableStateOf(false) }
    var searchText by rememberSaveable { mutableStateOf(state.filter.q) }
    val searchFocus = remember { FocusRequester() }
    var fabMenu by remember { mutableStateOf(false) }
    var batchAction by remember { mutableStateOf<BatchAction?>(null) }
    var reauthTarget by remember { mutableStateOf<MailAccount?>(null) }

    val listState = rememberLazyListState()
    // 把刷新指示器提升到邮箱页根容器，避免被账号卡片的阴影/图层盖住。
    val pullState = rememberPullToRefreshState()

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
                            Icon(Ym1rIcons.CheckCheck, contentDescription = "全选/取消")
                        }
                    },
                )
            } else {
                MainTopBar(
                    title = "邮箱",
                    navigationIcon = onOpenDrawer?.let { open -> { UserAvatarButton(onClick = open) } },
                    actions = {
                        // 搜索与筛选统一收在右侧操作区，符合 Pixel 顶栏的动作层级。
                        IconButton(onClick = {
                            val opening = !searchVisible
                            searchVisible = opening
                            if (!opening && searchText.isNotEmpty()) {
                                searchText = ""
                                vm.updateFilter(state.filter.copy(q = ""))
                            }
                        }) {
                            Icon(
                                if (searchVisible) Ym1rIcons.X else Ym1rIcons.Search,
                                contentDescription = if (searchVisible) "关闭搜索" else "搜索",
                            )
                        }
                        IconButton(onClick = { onNavigate(Route.MailSearch) }) {
                            Icon(Ym1rIcons.MailSearch, contentDescription = "离线邮件搜索")
                        }
                        IconButton(onClick = { showFilters = !showFilters }) {
                            Icon(Ym1rIcons.Filter, contentDescription = "筛选")
                        }
                        IconButton(onClick = { vm.load() }) {
                            Icon(Ym1rIcons.RefreshCw, contentDescription = "刷新")
                        }
                    },
                )
            }
        },
        floatingActionButton = {
            if (!readOnly && !state.selecting) {
                FloatingActionButton(
                    onClick = { fabMenu = true },
                    shape = CircleShape,
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier
                        .padding(bottom = 80.dp)
                        .size(56.dp),
                ) {
                    Icon(Ym1rIcons.Plus, contentDescription = "添加", modifier = Modifier.size(24.dp))
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
        Box(Modifier.padding(padding).fillMaxSize()) {
            Column(Modifier.fillMaxSize()) {
            // 展开后等输入框完成挂载再聚焦：FocusRequester 过早调用会直接崩掉进程
            LaunchedEffect(searchVisible) {
                if (searchVisible) {
                    delay(120)
                    runCatching { searchFocus.requestFocus() }
                }
            }
            AnimatedVisibility(visible = searchVisible) {
                AppleSearchField(
                    value = searchText,
                    onValueChange = {
                        searchText = it
                        vm.updateFilter(state.filter.copy(q = it))
                    },
                    placeholder = "搜索邮箱或备注",
                    leadingIcon = {
                        Icon(
                            Ym1rIcons.Search,
                            contentDescription = null,
                            tint = AppleColors.Gray,
                            modifier = Modifier.size(18.dp),
                        )
                    },
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
                state.firstLoad -> ListSkeleton(count = 5, contentPadding = PaddingValues(16.dp))
                state.error != null && state.items.isEmpty() ->
                    ErrorBox(state.error!!) { vm.load() }

                state.items.isEmpty() -> EmptyBox(
                    "还没有邮箱账号",
                    Ym1rIcons.Inbox,
                ) {
                    if (!readOnly) {
                        Row {
                            TextButton(onClick = { onNavigate(Route.accountImport()) }) { Text("批量导入") }
                            TextButton(onClick = { onNavigate(Route.accountEdit()) }) { Text("手动添加") }
                        }
                    }
                }

                else -> {
                    var dragStartedAtTop by remember { mutableStateOf(false) }

                    val pullGuardConnection = remember(listState) {
                        object : NestedScrollConnection {
                            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                                if (source == NestedScrollSource.UserInput) {
                                    dragStartedAtTop = !listState.canScrollBackward
                                }
                                return Offset.Zero
                            }

                            override fun onPostScroll(
                                consumed: Offset,
                                available: Offset,
                                source: NestedScrollSource,
                            ): Offset {
                                if (!dragStartedAtTop && available.y > 0f) {
                                    return Offset(0f, available.y)
                                }
                                return Offset.Zero
                            }
                        }
                    }

                    PullToRefreshBox(
                        isRefreshing = state.refreshing,
                        onRefresh = { vm.load(silent = false) },
                        state = pullState,
                        // 指示器由外层 Box 统一绘制，避免落入 LazyColumn/卡片的绘制层。
                        indicator = {},
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier
                                .fillMaxSize()
                                .nestedScroll(pullGuardConnection),
                            contentPadding = PaddingValues(bottom = 160.dp),
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
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
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
            // 外层最后绘制 + 高 zIndex：始终位于邮箱卡片、列表和进度条之上。
            PullToRefreshDefaults.Indicator(
                state = pullState,
                isRefreshing = state.refreshing,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset(y = (-16).dp)
                    .zIndex(100f),
            )
        }
    }

    if (fabMenu) {
        ModalBottomSheet(
            onDismissRequest = { fabMenu = false },
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp)
                    .padding(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    "邮箱操作",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
                AccountActionOption(
                    title = "手动添加账号",
                    subtitle = "通过 IMAP / SMTP 或微软协议连接新邮箱",
                    icon = Ym1rIcons.Plus,
                    onClick = {
                        fabMenu = false
                        onNavigate(Route.accountEdit())
                    },
                )
                AccountActionOption(
                    title = "批量导入账号",
                    subtitle = "支持文本批量粘贴与表格数据导入",
                    icon = Ym1rIcons.Upload,
                    onClick = {
                        fabMenu = false
                        onNavigate(Route.accountImport())
                    },
                )
                AccountActionOption(
                    title = "搜索邮件",
                    subtitle = "从本机加密缓存中全文搜索邮件内容",
                    icon = Ym1rIcons.Search,
                    onClick = {
                        fabMenu = false
                        onNavigate(Route.MailSearch)
                    },
                )
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
    ProductSurface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.medium,
    ) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("邮箱账户", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Text("${state.total} 个邮箱", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(top = 3.dp))
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("可用 $activeLoaded", style = MaterialTheme.typography.bodyMedium)
                Text("${state.items.size} 已加载 · $providerCount 个来源", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
            Ym1rIcons.Folder to "移动",
            Ym1rIcons.Sliders to "状态",
            Ym1rIcons.Shield to "代理",
            Ym1rIcons.Download to "导出",
            Ym1rIcons.Trash2 to "删除",
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

    ProductSurface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 2.dp)
            .heightIn(min = 80.dp)
            .appleCombinedClickable(
                pressedScale = 0.97f,
                pressedAlpha = 0.92f,
                onClick = onClick,
                onLongClick = onLongClick,
            ),
        shape = MaterialTheme.shapes.medium,
    ) {
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (selecting) {
                Checkbox(checked = selected, onCheckedChange = { onLongClick() })
            } else {
                InitialAvatar(
                    text = initialOf(account.email),
                    selected = selected,
                    modifier = Modifier.size(44.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        displayEmail ?: account.email,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        formatShortTime(account.last_refresh_at).ifBlank { "—" },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    StatusChip(Labels.accountStatus(account.status), statusContainer(account.status), statusContent(account.status))
                    Spacer(Modifier.width(6.dp))
                    StatusChip(Labels.refreshStatus(account.last_refresh_status), statusContainer(account.last_refresh_status), statusContent(account.last_refresh_status))
                    if (!groupName.isNullOrBlank()) {
                        Spacer(Modifier.width(6.dp))
                        Text(groupName, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (failed && !account.last_refresh_error.isNullOrBlank()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Ym1rIcons.AlertCircle,
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
                        account.remark,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Box {
                Box {
                    IconButton(onClick = { menu = true }) {
                        Icon(Ym1rIcons.MoreVertical, contentDescription = "更多")
                    }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(
                            text = { Text("查看收件箱") },
                            onClick = { menu = false; onViewMessages() },
                            leadingIcon = { Icon(Ym1rIcons.Inbox, null) },
                        )
                        if (!readOnly) {
                            DropdownMenuItem(
                                text = { Text("刷新令牌") },
                                onClick = { menu = false; onRefresh() },
                                leadingIcon = { Icon(Ym1rIcons.RefreshCw, null) },
                            )
                            DropdownMenuItem(
                                text = { Text("重新授权") },
                                onClick = { menu = false; onReauthorize() },
                                leadingIcon = { Icon(Ym1rIcons.Key, null) },
                            )
                            DropdownMenuItem(
                                text = { Text("编辑账号") },
                                onClick = { menu = false; onEdit() },
                                leadingIcon = { Icon(Ym1rIcons.Pencil, null) },
                            )
                        }
                    }
                }
            }
        }
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
                leadingIcon = { Icon(Ym1rIcons.X, null, Modifier.size(16.dp)) },
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
                ProductField(main, { main = it }, label = "主代理", modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                ProductField(fb1, { fb1 = it }, label = "备用代理 1", modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                ProductField(fb2, { fb2 = it }, label = "备用代理 2", modifier = Modifier.fillMaxWidth())
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

@Composable
private fun AccountActionOption(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                modifier = Modifier.size(38.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.primary,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, Modifier.size(20.dp))
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
