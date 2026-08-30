package com.masteralanlab.emailbox.ui.screens.mailbox

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Attachment
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Drafts
import androidx.compose.material.icons.outlined.Mail
import androidx.compose.material.icons.outlined.MailOutline
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
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
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.masteralanlab.emailbox.data.Prefs
import com.masteralanlab.emailbox.data.MailPreload
import com.masteralanlab.emailbox.data.MailPreloadCache
import com.masteralanlab.emailbox.data.MailIntelligence
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
import com.masteralanlab.emailbox.ui.nav.Route
import com.masteralanlab.emailbox.util.displayName
import com.masteralanlab.emailbox.util.formatShortTime
import com.masteralanlab.emailbox.util.initialOf
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

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

    fun init(accountId: String, email: String) {
        if (_state.value.accountId == accountId && !_state.value.firstLoad) return
        val cached = Prefs.tenantId?.let { MailPreloadCache.get(it, accountId, "inbox") }
        _state.update {
            it.copy(
                accountId = accountId,
                email = email,
                folder = "inbox",
                items = cached?.items.orEmpty(),
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

    fun setFolder(folder: String) {
        if (_state.value.folder == folder) return
        val cached = if (folder == "inbox") {
            Prefs.tenantId?.let { MailPreloadCache.get(it, _state.value.accountId, folder) }
        } else {
            null
        }
        _state.update {
            it.copy(
                folder = folder,
                items = cached?.items.orEmpty(),
                channel = cached?.channel.orEmpty(),
                firstLoad = cached == null,
                endReached = cached != null && cached.items.size < PAGE_TOP,
                selected = emptySet(),
                selecting = false,
            )
        }
        load(reset = true)
    }

    fun load(reset: Boolean = true) = viewModelScope.launch {
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
                _state.update {
                    val merged = if (reset) result.items else it.items + result.items
                    it.copy(
                        items = merged,
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
                        MailPreload(result.items, result.channel),
                    )
                }
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
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun MailboxScreen(
    accountId: String,
    email: String,
    onBack: () -> Unit,
    onOpenMessage: (MessageItem) -> Unit,
) {
    val vm: MailboxViewModel = viewModel()
    val state by vm.state.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val readOnly = Prefs.apiKeyMode
    var confirmDelete by remember { mutableStateOf(false) }

    LaunchedEffect(accountId) { vm.init(accountId, email) }
    LaunchedEffect(vm) { vm.message.collect { snackbar.showSnackbar(it) } }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            AppTopBar(
                title = if (state.selecting) "已选择 ${state.selected.size} 封" else email,
                onBack = if (!readOnly && state.selecting) ({ vm.clearSelection() }) else onBack,
                actions = {
                    if (!readOnly && state.selecting) {
                        IconButton(onClick = { vm.selectAll() }) {
                            Icon(Icons.Outlined.SelectAll, contentDescription = "全选")
                        }
                    } else {
                        IconButton(onClick = { vm.load() }) {
                            Icon(Icons.Outlined.Refresh, contentDescription = "刷新")
                        }
                    }
                },
            )
        },
        bottomBar = {
            if (!readOnly && state.selecting) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    TextButton(onClick = { vm.markRead() }, enabled = !state.acting) { Text("标记已读") }
                    TextButton(onClick = { confirmDelete = true }, enabled = !state.acting) { Text("删除") }
                }
            }
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
                state.firstLoad -> LoadingBox(text = "正在从上游拉取邮件…")
                state.error != null && state.items.isEmpty() -> ErrorBox(state.error!!) { vm.load() }
                state.items.isEmpty() -> EmptyBox("这个邮件夹里没有邮件", Icons.Outlined.MailOutline)
                else -> PullToRefreshBox(isRefreshing = state.refreshing, onRefresh = { vm.load() }) {
                    LazyColumn(
                        Modifier.fillMaxSize(),
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
                            MessageRow(
                                msg = msg,
                                selected = msg.id in state.selected,
                                selecting = state.selecting,
                                onClick = {
                                    if (!readOnly && state.selecting) vm.toggleSelect(msg.id)
                                    else onOpenMessage(msg)
                                },
                                onLongClick = { if (!readOnly) vm.toggleSelect(msg.id) },
                            )
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessageRow(
    msg: MessageItem,
    selected: Boolean,
    selecting: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val insight = remember(msg) { MailIntelligence.analyze(msg, overrideCategory = Prefs.categoryOverride(msg.from)) }
    val clipboard = LocalClipboardManager.current
    ListItem(
        modifier = Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick),
        leadingContent = {
            if (selecting) {
                Checkbox(checked = selected, onCheckedChange = { onLongClick() })
            } else {
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = if (msg.is_read) MaterialTheme.colorScheme.surfaceVariant
                    else MaterialTheme.colorScheme.primaryContainer,
                    contentColor = if (msg.is_read) MaterialTheme.colorScheme.onSurfaceVariant
                    else MaterialTheme.colorScheme.onPrimaryContainer,
                ) {
                    Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                        Text(initialOf(msg.from), style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        },
        headlineContent = {
            Text(
                displayName(msg.from),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (msg.is_read) FontWeight.Normal else FontWeight.Bold,
            )
        },
        supportingContent = {
            Column {
                Text(
                    msg.subject.ifBlank { "（无主题）" },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (msg.is_read) FontWeight.Normal else FontWeight.Medium,
                )
                Text(
                    msg.body_preview,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    AssistChip(onClick = {}, enabled = false, label = { Text(insight.category) })
                    insight.otp?.let { code ->
                        AssistChip(
                            onClick = { clipboard.setText(AnnotatedString(code)) },
                            label = { Text("复制 $code") },
                        )
                    }
                }
            }
        },
        trailingContent = {
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    formatShortTime(msg.received_at),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (msg.has_attachments) {
                    Icon(
                        Icons.Outlined.Attachment,
                        null,
                        Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
    )
}
