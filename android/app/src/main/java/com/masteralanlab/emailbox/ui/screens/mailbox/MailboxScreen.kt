package com.masteralanlab.emailbox.ui.screens.mailbox

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import com.masteralanlab.emailbox.ui.components.Ym1rIcons
import com.masteralanlab.emailbox.ui.components.RollingNumber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import com.masteralanlab.emailbox.ui.components.ListSkeleton
import com.masteralanlab.emailbox.data.SecureMailCache
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import java.io.File
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.layout.ContentScale
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.masteralanlab.emailbox.data.Prefs
import com.masteralanlab.emailbox.data.MailPreload
import com.masteralanlab.emailbox.data.MailPreloadCache
import com.masteralanlab.emailbox.data.MailIntelligence
import com.masteralanlab.emailbox.data.MailInsight
import com.masteralanlab.emailbox.data.remote.ApiResult
import com.masteralanlab.emailbox.data.remote.MessageBatchRequest
import com.masteralanlab.emailbox.data.remote.MessageItem
import com.masteralanlab.emailbox.data.remote.MessageRef
import com.masteralanlab.emailbox.data.remote.apiCall
import com.masteralanlab.emailbox.ui.components.AppTopBar
import com.masteralanlab.emailbox.ui.components.EmptyBox
import com.masteralanlab.emailbox.ui.components.ErrorBox
import com.masteralanlab.emailbox.ui.components.Labels
import com.masteralanlab.emailbox.ui.components.LoadingBox
import com.masteralanlab.emailbox.ui.components.InitialAvatar
import com.masteralanlab.emailbox.ui.components.ProductSurface
import com.masteralanlab.emailbox.ui.components.StatusChip
import com.masteralanlab.emailbox.ui.nav.Route
import com.masteralanlab.emailbox.util.displayName
import com.masteralanlab.emailbox.util.formatShortTime
import com.masteralanlab.emailbox.util.initialOf
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 后端 folder 枚举：垃圾箱是 junkemail 不是 junk，all 只能用于列表。 */
private val FOLDERS = listOf(
    "inbox" to "收件箱",
    "junkemail" to "垃圾邮件",
    "deleteditems" to "已删除",
    "all" to "全部",
)

private const val PAGE_TOP = 25

data class MailboxState(
    val accountId: String = "",
    val email: String = "",
    val folder: String = "inbox",
    val items: List<MessageItem> = emptyList(),
    val insights: Map<String, MailInsight> = emptyMap(),
    val channel: String = "",
    val firstLoad: Boolean = true,
    val refreshing: Boolean = false,
    val loadingMore: Boolean = false,
    val endReached: Boolean = false,
    val acting: Boolean = false,
    val error: String? = null,
    val selected: Set<String> = emptySet(),
    val selecting: Boolean = false,
)

class MailboxViewModel : ViewModel() {

    private val _state = MutableStateFlow(MailboxState())
    val state: MutableStateFlow<MailboxState> get() = _state

    private val _message = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val message = _message.asSharedFlow()

    /** 请求代际号：刷新/切夹/加载更多并发时，只有最新一次请求允许写回状态，过期响应直接丢弃。 */
    private var loadSeq = 0
    private var loadJob: kotlinx.coroutines.Job? = null

    fun init(accountId: String, email: String) {
        if (_state.value.accountId == accountId && !_state.value.firstLoad) return
        viewModelScope.launch {
            val cached = Prefs.tenantId?.let { MailPreloadCache.get(it, accountId, "inbox") }
            _state.update {
                it.copy(
                    accountId = accountId,
                    email = email,
                    folder = "inbox",
                    items = cached?.items.orEmpty(),
                    insights = cached?.insights.orEmpty(),
                    channel = cached?.channel.orEmpty(),
                    firstLoad = cached == null,
                    refreshing = false,
                    loadingMore = false,
                    endReached = cached != null && cached.items.size < PAGE_TOP,
                    error = null,
                    selected = emptySet(),
                    selecting = false,
                )
            }
            load(reset = true)
        }
    }

    fun setFolder(folder: String) {
        if (_state.value.folder == folder) return
        val accountId = _state.value.accountId
        _state.update {
            it.copy(
                folder = folder,
                items = emptyList(),
                insights = emptyMap(),
                channel = "",
                firstLoad = true,
                endReached = false,
                selected = emptySet(),
                selecting = false,
            )
        }
        viewModelScope.launch {
            val cached = if (folder == "inbox") {
                Prefs.tenantId?.let { MailPreloadCache.get(it, accountId, folder) }
            } else {
                null
            }
            if (_state.value.accountId != accountId || _state.value.folder != folder) return@launch
            _state.update {
                it.copy(
                    items = cached?.items.orEmpty(),
                    insights = cached?.insights.orEmpty(),
                    channel = cached?.channel.orEmpty(),
                    firstLoad = cached == null,
                    endReached = cached != null && cached.items.size < PAGE_TOP,
                )
            }
            load(reset = true)
        }
    }

    fun load(reset: Boolean = true) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
        val seq = ++loadSeq
        val tenant = Prefs.tenantId
        val s = _state.value
        val requestAccountId = s.accountId
        val requestFolder = s.folder
        if (tenant.isNullOrBlank()) {
            _state.update { it.copy(firstLoad = false, error = "未选择工作空间") }
            return@launch
        }
        val skip = if (reset) 0 else s.items.size
        _state.update { it.copy(refreshing = reset, loadingMore = !reset, error = if (reset) null else it.error) }

        when (val r = apiCall {
            messages(
                tenant,
                requestAccountId,
                folder = requestFolder,
                skip = skip,
                top = PAGE_TOP,
                preferCache = Prefs.pullMode == Prefs.PULL_MODE_SERVER,
            )
        }) {
            is ApiResult.Success -> if (
                seq == loadSeq &&
                    _state.value.accountId == requestAccountId &&
                    _state.value.folder == requestFolder
            ) {
                val result = r.data
                val insights = withContext(Dispatchers.Default) {
                    result.items.associateBy(MailIntelligence::insightKey) {
                        MailIntelligence.analyze(it, overrideCategory = Prefs.categoryOverride(it.from))
                    }
                }
                _state.update {
                    val merged = if (reset) result.items else it.items + result.items
                    it.copy(
                        items = merged,
                        insights = if (reset) insights else it.insights + insights,
                        channel = result.channel,
                        firstLoad = false,
                        refreshing = false,
                        loadingMore = false,
                        // 每次调用都会扣一次每日取件配额，拿不满一页就认为到底了
                        endReached = result.items.size < PAGE_TOP,
                        error = null,
                        selected = if (reset) emptySet() else it.selected,
                    )
                }
                if (reset && requestFolder == "inbox") {
                    MailPreloadCache.put(
                        tenant,
                        requestAccountId,
                        requestFolder,
                        MailPreload(result.items, result.channel, insights),
                    )
                }
                // 正文按用户点击读取；每次刷新预取八封会与正在打开的邮件争抢连接和配额。

            }

            is ApiResult.Failure -> if (
                seq == loadSeq &&
                    _state.value.accountId == requestAccountId &&
                    _state.value.folder == requestFolder
            ) _state.update {
                it.copy(firstLoad = false, refreshing = false, loadingMore = false, error = r.message)
            }
        }
    }

    }

    fun loadMore() {
        val s = _state.value
        if (s.loadingMore || s.refreshing || s.firstLoad || s.endReached) return
        load(reset = false)
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

    private fun refs(): List<MessageRef> {
        val s = _state.value
        return s.items.filter { it.id in s.selected }
            .map { MessageRef(it.id, it.id_mode, it.folder) }
    }

    fun markRead() = viewModelScope.launch {
        if (Prefs.apiKeyMode) return@launch
        val tenant = Prefs.tenantId ?: return@launch
        val items = refs()
        if (items.isEmpty()) return@launch
        _state.update { it.copy(acting = true) }
        when (val r = apiCall { markRead(tenant, _state.value.accountId, MessageBatchRequest(items)) }) {
            is ApiResult.Success -> {
                _message.tryEmit("已标记 ${r.data.succeeded} 封为已读")
                clearSelection()
                load()
            }

            is ApiResult.Failure -> _message.tryEmit(r.message)
        }
        _state.update { it.copy(acting = false) }
    }

    fun delete() = viewModelScope.launch {
        if (Prefs.apiKeyMode) return@launch
        val tenant = Prefs.tenantId ?: return@launch
        val items = refs()
        if (items.isEmpty()) return@launch
        _state.update { it.copy(acting = true) }
        when (val r = apiCall { deleteMessages(tenant, _state.value.accountId, MessageBatchRequest(items)) }) {
            is ApiResult.Success -> {
                _message.tryEmit("已删除 ${r.data.succeeded} 封")
                clearSelection()
                load()
            }

            is ApiResult.Failure -> _message.tryEmit(r.message)
        }
        _state.update { it.copy(acting = false) }
    }

    fun markSingleRead(msg: MessageItem) = viewModelScope.launch {
        if (Prefs.apiKeyMode) return@launch
        val tenant = Prefs.tenantId ?: return@launch
        val item = listOf(MessageRef(msg.id, msg.id_mode, msg.folder))
        _state.update { s ->
            s.copy(items = s.items.map { if (it.id == msg.id) it.copy(is_read = true) else it })
        }
        when (val r = apiCall { markRead(tenant, _state.value.accountId, MessageBatchRequest(item)) }) {
            is ApiResult.Success -> _message.tryEmit("已标为已读")
            is ApiResult.Failure -> {
                _message.tryEmit(r.message)
                load()
            }
        }
    }

    fun deleteSingle(msg: MessageItem) = viewModelScope.launch {
        if (Prefs.apiKeyMode) return@launch
        val tenant = Prefs.tenantId ?: return@launch
        val item = listOf(MessageRef(msg.id, msg.id_mode, msg.folder))
        _state.update { s ->
            s.copy(items = s.items.filterNot { it.id == msg.id })
        }
        when (val r = apiCall { deleteMessages(tenant, _state.value.accountId, MessageBatchRequest(item)) }) {
            is ApiResult.Success -> _message.tryEmit("已删除")
            is ApiResult.Failure -> {
                _message.tryEmit(r.message)
                load()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class, ExperimentalSharedTransitionApi::class)
@Composable
fun MailboxScreen(
    accountId: String,
    email: String,
    onBack: () -> Unit,
    onOpenMessage: (MessageItem) -> Unit,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
) {
    val vm: MailboxViewModel = viewModel()
    val state by vm.state.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val readOnly = Prefs.apiKeyMode
    var confirmDelete by remember { mutableStateOf(false) }

    LaunchedEffect(accountId) { vm.init(accountId, email) }
    LaunchedEffect(vm) { vm.message.collect { snackbar.showSnackbar(it) } }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            AppTopBar(
                titleContent = {
                    if (state.selecting) {
                        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            Text(
                                "已选择 ",
                                style = MaterialTheme.typography.headlineSmall,
                            )
                            RollingNumber(
                                value = state.selected.size.toLong(),
                                style = MaterialTheme.typography.headlineSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                " 封",
                                style = MaterialTheme.typography.headlineSmall,
                            )
                        }
                    } else {
                        Text(
                            email,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.headlineSmall,
                        )
                    }
                },
                onBack = if (!readOnly && state.selecting) ({ vm.clearSelection() }) else onBack,
                actions = {
                    if (!state.selecting) {
                        InitialAvatar(
                            text = initialOf(email),
                            modifier = Modifier
                                .padding(end = 4.dp)
                                .size(32.dp),
                        )
                    }
                    if (!readOnly && state.selecting) {
                        IconButton(onClick = { vm.selectAll() }) {
                            Icon(Ym1rIcons.CheckCheck, contentDescription = "全选")
                        }
                    } else {
                        IconButton(onClick = { vm.load() }) {
                            Icon(Ym1rIcons.RefreshCw, contentDescription = "刷新")
                        }
                    }
                    if (!readOnly && state.selecting) {
                        IconButton(onClick = { vm.markRead() }) {
                            Icon(Ym1rIcons.Mail, contentDescription = "标记为已读")
                        }
                        IconButton(onClick = { confirmDelete = true }) {
                            Icon(Ym1rIcons.Trash2, contentDescription = "删除")
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding)) {
            PrimaryTabRow(selectedTabIndex = FOLDERS.indexOfFirst { it.first == state.folder }.coerceAtLeast(0)) {
                FOLDERS.forEach { (key, label) ->
                    Tab(
                        selected = state.folder == key,
                        onClick = { vm.setFolder(key) },
                        text = { Text(label, maxLines = 1) },
                    )
                }
            }

            if (state.acting) LinearProgressIndicator(Modifier.fillMaxWidth())

            when {
                state.firstLoad -> ListSkeleton(count = 5, contentPadding = PaddingValues(16.dp))
                state.error != null && state.items.isEmpty() -> ErrorBox(state.error!!) { vm.load() }
                state.items.isEmpty() -> EmptyBox("这个邮件夹里没有邮件", Ym1rIcons.Mail)
                else -> {
                    val listState = rememberLazyListState()
                    val pullState = rememberPullToRefreshState()
                    var dragStartedAtTop by remember { mutableStateOf(false) }

                    val pullGuardConnection = remember(listState) {
                        object : NestedScrollConnection {
                            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                                if (source == NestedScrollSource.UserInput && !listState.isScrollInProgress) {
                                    dragStartedAtTop = listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0
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
                        onRefresh = { vm.load() },
                        state = pullState,
                        indicator = {
                            PullToRefreshDefaults.Indicator(
                                state = pullState,
                                isRefreshing = state.refreshing,
                                modifier = Modifier
                                    .align(Alignment.TopCenter)
                                    .zIndex(4f),
                            )
                        },
                    ) {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier
                                .fillMaxSize()
                                .nestedScroll(pullGuardConnection),
                            contentPadding = PaddingValues(bottom = 96.dp),
                        ) {
                        item {
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    "共 ${state.items.size} 封",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                if (state.channel.isNotBlank()) {
                                    Spacer(Modifier.width(8.dp))
                                    FilterChip(
                                        selected = false,
                                        onClick = {},
                                        enabled = false,
                                        label = { Text(Labels.channel(state.channel)) },
                                    )
                                }
                            }
                        }
                        items(state.items, key = { it.id + it.id_mode }) { msg ->
                            Box(
                                Modifier.animateItem(
                                    fadeInSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
                                    placementSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
                                    fadeOutSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
                                ),
                            ) {
                                val dismissState = rememberSwipeToDismissBoxState(
                                    confirmValueChange = { dismissValue ->
                                        if (readOnly || state.selecting) return@rememberSwipeToDismissBoxState false
                                        when (dismissValue) {
                                            SwipeToDismissBoxValue.StartToEnd -> {
                                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                vm.markSingleRead(msg)
                                                false
                                            }
                                            SwipeToDismissBoxValue.EndToStart -> {
                                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                vm.deleteSingle(msg)
                                                true
                                            }
                                            SwipeToDismissBoxValue.Settled -> false
                                        }
                                    }
                                )

                                SwipeToDismissBox(
                                    state = dismissState,
                                    enableDismissFromStartToEnd = !readOnly && !state.selecting && !msg.is_read,
                                    enableDismissFromEndToStart = !readOnly && !state.selecting,
                                    backgroundContent = {
                                        val color = when (dismissState.dismissDirection) {
                                            SwipeToDismissBoxValue.StartToEnd -> MaterialTheme.colorScheme.primaryContainer
                                            SwipeToDismissBoxValue.EndToStart -> MaterialTheme.colorScheme.errorContainer
                                            SwipeToDismissBoxValue.Settled -> Color.Transparent
                                        }
                                        val icon = when (dismissState.dismissDirection) {
                                            SwipeToDismissBoxValue.StartToEnd -> Ym1rIcons.Check
                                            SwipeToDismissBoxValue.EndToStart -> Ym1rIcons.Trash2
                                            SwipeToDismissBoxValue.Settled -> null
                                        }
                                        val iconTint = when (dismissState.dismissDirection) {
                                            SwipeToDismissBoxValue.StartToEnd -> MaterialTheme.colorScheme.onPrimaryContainer
                                            SwipeToDismissBoxValue.EndToStart -> MaterialTheme.colorScheme.onErrorContainer
                                            SwipeToDismissBoxValue.Settled -> Color.Transparent
                                        }
                                        val alignment = when (dismissState.dismissDirection) {
                                            SwipeToDismissBoxValue.StartToEnd -> Alignment.CenterStart
                                            SwipeToDismissBoxValue.EndToStart -> Alignment.CenterEnd
                                            SwipeToDismissBoxValue.Settled -> Alignment.Center
                                        }
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .clip(MaterialTheme.shapes.medium)
                                                .background(color)
                                                .padding(horizontal = 20.dp),
                                            contentAlignment = alignment,
                                        ) {
                                            if (icon != null) {
                                                Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(24.dp))
                                            }
                                        }
                                    },
                                    content = {
                                        MessageRow(
                                            msg = msg,
                                            insight = state.insights[MailIntelligence.insightKey(msg)],
                                            selected = msg.id in state.selected,
                                            selecting = state.selecting,
                                            sharedTransitionScope = sharedTransitionScope,
                                            animatedVisibilityScope = animatedVisibilityScope,
                                            onClick = {
                                                if (!readOnly && state.selecting) vm.toggleSelect(msg.id)
                                                else onOpenMessage(msg)
                                            },
                                            onLongClick = { if (!readOnly) vm.toggleSelect(msg.id) },
                                        )
                                    }
                                )
                            }
                        }
                        if (!state.endReached) {
                            item {
                                LaunchedEffect(Unit) { vm.loadMore() }
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

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("删除 ${state.selected.size} 封邮件？") },
            text = { Text("这会直接删除上游服务器上的邮件，无法恢复。") },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; vm.delete() }) { Text("删除") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("取消") } },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalSharedTransitionApi::class)
@Composable
private fun MessageRow(
    msg: MessageItem,
    insight: MailInsight?,
    selected: Boolean,
    selecting: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    sharedTransitionScope: SharedTransitionScope?,
    animatedVisibilityScope: AnimatedVisibilityScope?,
) {
    val clipboard = LocalClipboardManager.current
    val sharedModifier = if (sharedTransitionScope != null && animatedVisibilityScope != null) {
        with(sharedTransitionScope) {
            Modifier.sharedBounds(
                rememberSharedContentState(key = "mail-message:${msg.id}:${msg.id_mode}"),
                animatedVisibilityScope = animatedVisibilityScope,
            )
        }
    } else {
        Modifier
    }
    ProductSurface(
        modifier = sharedModifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 3.dp)
            .heightIn(min = 88.dp)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        containerColor = if (selected) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.48f)
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        },
        shape = MaterialTheme.shapes.medium,
    ) {
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            if (selecting) {
                Checkbox(checked = selected, onCheckedChange = { onLongClick() })
            } else {
                InitialAvatar(initialOf(msg.from), selected = !msg.is_read)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        displayName(msg.from).ifBlank { "未知发件人" },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = if (msg.is_read) FontWeight.Normal else FontWeight.Bold,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        formatShortTime(msg.received_at),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (msg.is_read) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
                    )
                }
                Text(
                    msg.subject.ifBlank { "（无主题）" },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (msg.is_read) FontWeight.Normal else FontWeight.Medium,
                    modifier = Modifier.padding(top = 4.dp),
                )
                Text(
                    msg.body_preview.ifBlank { "没有摘要" },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
                Row(
                    Modifier.padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    insight?.let {
                        StatusChip(
                            text = it.category,
                            container = MaterialTheme.colorScheme.surfaceContainerHighest,
                            content = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (!msg.is_read) {
                        Text("未读", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    }
                    if (msg.has_attachments) {
                        Icon(Ym1rIcons.Paperclip, contentDescription = "有附件", Modifier.size(16.dp))
                    }
                    insight?.otp?.let { code ->
                        TextButton(onClick = { clipboard.setText(AnnotatedString(code)) }) {
                            Text("复制验证码", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
        }
    }
}
