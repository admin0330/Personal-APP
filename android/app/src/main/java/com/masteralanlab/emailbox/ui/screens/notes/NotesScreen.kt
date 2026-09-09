package com.masteralanlab.emailbox.ui.screens.notes

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import com.masteralanlab.emailbox.ui.components.appleClickable
import com.masteralanlab.emailbox.ui.components.appleCombinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import com.masteralanlab.emailbox.ui.components.Ym1rIcons
import com.masteralanlab.emailbox.ui.components.RollingNumber
import androidx.compose.ui.platform.LocalContext
import com.masteralanlab.emailbox.util.FileSharing
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.masteralanlab.emailbox.ui.components.UserAvatarButton
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.masteralanlab.emailbox.data.Prefs
import com.masteralanlab.emailbox.data.remote.ApiResult
import com.masteralanlab.emailbox.data.remote.CreateNoteRequest
import com.masteralanlab.emailbox.data.remote.Note
import com.masteralanlab.emailbox.data.remote.UpdateNoteRequest
import com.masteralanlab.emailbox.data.NotesLocalData
import com.masteralanlab.emailbox.data.NotesLocalStore
import com.masteralanlab.emailbox.data.NotesStorageException
import com.masteralanlab.emailbox.data.PendingNoteOp
import com.masteralanlab.emailbox.data.remote.apiCall
import com.masteralanlab.emailbox.data.remote.apiCallUnit
import com.masteralanlab.emailbox.ui.components.EmptyBox
import com.masteralanlab.emailbox.ui.components.AppTopBar
import com.masteralanlab.emailbox.ui.components.MainTopBar
import com.masteralanlab.emailbox.ui.components.AppleColors
import com.masteralanlab.emailbox.ui.components.AppleSearchField
import com.masteralanlab.emailbox.ui.components.AppleSegmentedControl
import com.masteralanlab.emailbox.ui.components.ProductField
import com.masteralanlab.emailbox.ui.components.ProductSurface
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class NotesState(
    val items: List<Note> = emptyList(),
    val loading: Boolean = true,
    val saving: Boolean = false,
)

class NotesViewModel(app: android.app.Application) : androidx.lifecycle.AndroidViewModel(app) {
    val state = MutableStateFlow(NotesState())
    // StateFlow 保留首条存储错误，避免 init 在 UI collector 建立前 tryEmit 被丢弃，
    // 从而把损坏/写满磁盘误显示成「没有笔记」。
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message
    private var initialized = false
    private var flushing = false

    private fun loadLocalOrReport(tenant: String): NotesLocalData? = try {
        NotesLocalStore.load(tenant)
    } catch (e: NotesStorageException) {
        _message.value = com.masteralanlab.emailbox.data.remote.presentableErrorMessage(e.message)
        null
    }

    private fun reportStorageError(error: Throwable) {
        _message.value = com.masteralanlab.emailbox.data.remote.presentableErrorMessage(error.message)
    }

    fun consumeMessage() { _message.value = null }

    fun init() {
        if (initialized) return
        initialized = true
        // 缓存直显（秒级），随后静默刷新——进页永远没有加载动画
        val tenant = Prefs.tenantId.orEmpty()
        if (tenant.isNotBlank()) {
            loadLocalOrReport(tenant)?.let { state.value = NotesState(items = it.notes) }
        }
        refresh(silent = true)
        flush()
    }

    /** 静默拉取服务器最新并覆盖本地缓存；加载过程不出现任何动画。 */
    fun refresh(silent: Boolean = true) {
        val tenant = Prefs.tenantId.orEmpty()
        if (tenant.isBlank()) return
        viewModelScope.launch {
            when (val result = apiCall { notes(tenant) }) {
                is ApiResult.Success -> {
                    val local = loadLocalOrReport(tenant)
                    if (local == null) {
                        // 本地快照损坏时可以展示服务器结果，但不能用它覆盖本地文件。
                        state.value = NotesState(items = result.data)
                        return@launch
                    }
                    if (local.pendingOps.isEmpty()) {
                        try {
                            NotesLocalStore.save(NotesLocalData(tenant, result.data))
                            state.value = NotesState(items = result.data)
                        } catch (e: NotesStorageException) {
                            reportStorageError(e)
                            state.value = NotesState(items = result.data)
                        }
                    } else {
                        // 在途的本地优先操作不能被较早的服务器快照覆盖。
                        state.value = NotesState(items = local.notes)
                    }
                }
                is ApiResult.Failure -> if (!silent) _message.value = result.message
            }
        }
    }

    /** 本地优先：先写本地（立即显示），入队后后台按顺序同步服务器。 */
    fun save(existing: Note?, title: String, content: String, pinned: Boolean, completed: Boolean, done: () -> Unit) {
        val cleanTitle = title.trim()
        if (cleanTitle.isBlank()) {
            _message.value = "请输入标题"
            return
        }
        val tenant = Prefs.tenantId.orEmpty()
        if (tenant.isBlank()) { _message.value = "未选择工作空间"; return }
        val now = java.time.OffsetDateTime.now().toString()
        val localId = existing?.id ?: NotesLocalStore.newLocalId()
        val localNote = Note(
            id = localId, tenant_id = tenant, title = cleanTitle, content = content.trim(),
            is_pinned = pinned, is_completed = completed,
            created_at = existing?.created_at ?: now, updated_at = now,
        )
        try {
            // 1) 本地立即生效
            NotesLocalStore.putNote(tenant, localNote)
            state.value = NotesState(items = NotesLocalStore.load(tenant).notes)
            // 2) 入队（新建/更新统一为 upsert 语义，删除仅对已有服务器 id 的条目）
            NotesLocalStore.enqueueOp(
                tenant,
                PendingNoteOp(
                    kind = if (existing == null) "create" else "update",
                    localId = localId,
                    serverId = existing?.id,
                    title = cleanTitle, content = content.trim(), pinned = pinned, completed = completed,
                ),
            )
        } catch (e: NotesStorageException) {
            reportStorageError(e)
            return
        }
        done()
        flush()
    }

    fun setCompleted(note: Note, completed: Boolean) {
        save(note, note.title, note.content, note.is_pinned, completed) {}
    }

    fun delete(note: Note, done: () -> Unit) = deleteNotes(listOf(note), done)

    fun deleteNotes(notes: List<Note>, done: () -> Unit) {
        val tenant = Prefs.tenantId.orEmpty()
        if (tenant.isBlank() || notes.isEmpty()) return
        // 本地立即移除；批量操作只做一次加密快照写入，避免 95 条逐条触发 Keystore I/O。
        val updated = try {
            NotesLocalStore.removeNotesAndQueueDeletes(tenant, notes)
        } catch (e: NotesStorageException) {
            reportStorageError(e)
            return
        }
        state.value = NotesState(items = updated.notes)
        done()
        // 服务器笔记的删除已在同一快照中排队；纯本地草稿无需发请求。
        if (notes.any { !it.id.startsWith("local-") }) flush()
    }

    /** 按队列顺序回放待同步操作；单条失败保留在队首等待下次（指数外置退避由调用方控制）。 */
    fun flush() {
        val tenant = Prefs.tenantId.orEmpty()
        if (tenant.isBlank() || flushing) return
        flushing = true
        viewModelScope.launch {
            var continueDrain = false
            try {
                var guard = 0
                var failed = false
                while (guard++ < 50) {
                    val ops = loadLocalOrReport(tenant)?.pendingOps ?: return@launch
                    val op = ops.firstOrNull() ?: break
                    val ok = when (op.kind) {
                        "create" -> runCatching {
                            // 1.4.5 以前 promote 会把已完成的 create 留在队列里；旧队列的
                            // server id 不是 local-*，先更新原记录，避免升级后再复制一次。
                            val staleCreate = !op.localId.startsWith("local-")
                            var recreated = false
                            val r = if (staleCreate) {
                                val updated = apiCall {
                                    updateNote(
                                        tenant,
                                        op.localId,
                                        UpdateNoteRequest(op.title, op.content, op.pinned, op.completed),
                                    )
                                }
                                if (updated is ApiResult.Failure && updated.httpStatus == 404) {
                                    recreated = true
                                    apiCall { createNote(tenant, CreateNoteRequest(op.title, op.content, op.pinned, op.completed)) }
                                } else updated
                            } else {
                                apiCall { createNote(tenant, CreateNoteRequest(op.title, op.content, op.pinned, op.completed)) }
                            }
                            if (r is ApiResult.Success) {
                                if (staleCreate && !recreated) NotesLocalStore.putNote(tenant, r.data)
                                else NotesLocalStore.promote(tenant, op, r.data)
                                state.value = NotesState(items = NotesLocalStore.load(tenant).notes)
                            }
                            r is ApiResult.Success
                        }.onFailure(::reportStorageError).getOrDefault(false)

                        "update" -> runCatching {
                            val r = apiCall {
                                updateNote(tenant, op.serverId ?: op.localId, UpdateNoteRequest(op.title, op.content, op.pinned, op.completed))
                            }
                            if (r is ApiResult.Failure && r.httpStatus == 404) {
                                // 服务器没有这条：退化为新建
                                val c = apiCall { createNote(tenant, CreateNoteRequest(op.title, op.content, op.pinned, op.completed)) }
                                if (c is ApiResult.Success) NotesLocalStore.promote(tenant, op, c.data)
                                c is ApiResult.Success
                            } else r is ApiResult.Success
                        }.onFailure(::reportStorageError).getOrDefault(false)

                        "delete" -> runCatching {
                            val r = apiCallUnit { deleteNote(tenant, op.serverId ?: op.localId) }
                            // 404 视为已删除成功
                            r is ApiResult.Success || (r as? ApiResult.Failure)?.httpStatus == 404
                        }.onFailure(::reportStorageError).getOrDefault(false)

                        else -> true
                    }
                    if (!ok) {
                        failed = true
                        break
                    }
                    try {
                        NotesLocalStore.popOp(tenant, op)
                    } catch (e: NotesStorageException) {
                        reportStorageError(e)
                        break
                    }
                }
                // 队列清空后拉一次服务器权威数据
                val remaining = loadLocalOrReport(tenant)?.pendingOps ?: return@launch
                if (remaining.isEmpty()) refresh(silent = true)
                else if (!failed) continueDrain = true
            } finally {
                flushing = false
            }
            // 单次最多回放 50 条，批量删除等长队列分段继续，不把 95 条操作堆进一次 UI 事件。
            if (continueDrain) flush()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class, androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun NotesScreen(
    vm: NotesViewModel = viewModel(),
    onOpenDrawer: (() -> Unit)? = null,
) {
    val state by vm.state.collectAsState()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    var query by rememberSaveable { mutableStateOf("") }
    var searchVisible by rememberSaveable { mutableStateOf(false) }
    val searchFocus = remember { FocusRequester() }
    var filter by rememberSaveable { mutableStateOf(0) } // 0 全部，1 进行中，2 已完成
    var editing by remember { mutableStateOf<Note?>(null) }
    var creating by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var confirmDelete by remember { mutableStateOf(false) }
    val message by vm.message.collectAsState()
    val topBarScrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())

    LaunchedEffect(Unit) {
        vm.init()
    }
    LaunchedEffect(message) {
        message?.let {
            snackbar.showSnackbar(it)
            vm.consumeMessage()
        }
    }

    val visible = remember(state.items, query, filter) {
        val q = query.trim()
        state.items
            .filter { filter == 0 || (filter == 1 && !it.is_completed) || (filter == 2 && it.is_completed) }
            .filter { q.isBlank() || it.title.contains(q, true) || it.content.contains(q, true) }
            .sortedWith(compareByDescending<Note> { it.is_pinned }.thenByDescending { it.updated_at })
    }
    val selecting = selectedIds.isNotEmpty()
    val visibleIds = visible.map { it.id }.toSet()
    val allVisibleSelected = visibleIds.isNotEmpty() && selectedIds.containsAll(visibleIds)
    val selectedNotes = state.items.filter { it.id in selectedIds }

    fun toggleSelection(id: String) {
        if (!selecting) {
            searchVisible = false
            query = ""
        }
        selectedIds = if (id in selectedIds) selectedIds - id else selectedIds + id
    }

    fun toggleAllVisible() {
        selectedIds = if (allVisibleSelected) selectedIds - visibleIds else selectedIds + visibleIds
    }

    // 长按进入多选后，系统返回只退出选择模式，不离开笔记页。
    BackHandler(enabled = selecting) { selectedIds = emptySet() }

    Scaffold(
        modifier = Modifier.nestedScroll(topBarScrollBehavior.nestedScrollConnection),
        topBar = {
            if (selecting) {
                AppTopBar(
                    titleContent = {
                        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            Text(
                                "已选 ",
                                style = MaterialTheme.typography.headlineSmall,
                            )
                            RollingNumber(
                                value = selectedIds.size.toLong(),
                                style = MaterialTheme.typography.headlineSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                " 条",
                                style = MaterialTheme.typography.headlineSmall,
                            )
                        }
                    },
                    onBack = { selectedIds = emptySet() },
                    actions = {
                        IconButton(onClick = ::toggleAllVisible) {
                            Icon(
                                Ym1rIcons.CheckCheck,
                                contentDescription = if (allVisibleSelected) "取消全选" else "全选",
                            )
                        }
                    },
                )
            } else {
                MainTopBar(
                    title = "笔记",
                    navigationIcon = onOpenDrawer?.let { open -> { UserAvatarButton(onClick = open) } },
                    scrollBehavior = topBarScrollBehavior,
                    actions = {
                        IconButton(onClick = {
                            val opening = !searchVisible
                            searchVisible = opening
                            if (!opening) query = ""
                        }) {
                            Icon(
                                if (searchVisible) Ym1rIcons.X else Ym1rIcons.Search,
                                contentDescription = if (searchVisible) "关闭搜索" else "搜索笔记",
                            )
                        }
                        if (state.items.isNotEmpty()) {
                            IconButton(onClick = {
                                val sb = StringBuilder()
                                sb.append("# Emailbox 笔记导出\n\n")
                                sb.append("> 导出时间: ${java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))}\n\n---\n\n")
                                state.items.forEach { n ->
                                    val status = if (n.is_completed) "✅ [已完成]" else "⏳ [进行中]"
                                    val pinned = if (n.is_pinned) " 📌" else ""
                                    sb.append("## ${n.title.ifBlank { "无标题" }}$pinned $status\n\n")
                                    sb.append("- 更新于: ${n.updated_at.take(16).replace('T', ' ')}\n\n")
                                    if (n.content.isNotBlank()) {
                                        sb.append("${n.content}\n\n")
                                    }
                                    sb.append("---\n\n")
                                }
                                val dateStr = java.time.LocalDate.now().toString()
                                FileSharing.shareText(context, sb.toString(), "Emailbox-Notes-${dateStr}.md", "text/markdown")
                            }) {
                                Icon(Ym1rIcons.Download, contentDescription = "导出笔记")
                            }
                        }
                    },
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            if (selecting) {
                FloatingActionButton(
                    onClick = { confirmDelete = true },
                    shape = CircleShape,
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                    modifier = Modifier
                        .padding(bottom = 80.dp)
                        .size(56.dp),
                ) {
                    Icon(Ym1rIcons.Trash2, contentDescription = "删除已选笔记", modifier = Modifier.size(28.dp))
                }
            } else {
                FloatingActionButton(
                    onClick = { creating = true },
                    shape = CircleShape,
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier
                        .padding(bottom = 80.dp)
                        .size(56.dp),
                ) {
                    Icon(Ym1rIcons.Plus, contentDescription = "新建笔记", modifier = Modifier.size(28.dp))
                }
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            LaunchedEffect(searchVisible) {
                if (searchVisible) {
                    kotlinx.coroutines.delay(120)
                    runCatching { searchFocus.requestFocus() }
                }
            }
            AnimatedVisibility(visible = searchVisible) {
                AppleSearchField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = "搜索标题或正文",
                    leadingIcon = {
                        Icon(
                            Ym1rIcons.Search,
                            contentDescription = null,
                            tint = AppleColors.Gray,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .focusRequester(searchFocus),
                )
            }
            AppleSegmentedControl(
                options = listOf("全部", "进行中", "已完成"),
                selectedIndex = filter,
                onSelect = { filter = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
            )
            if (visible.isEmpty()) {
                EmptyBox(
                    text = if (state.items.isEmpty()) "还没有笔记" else "没有匹配的笔记",
                    icon = Ym1rIcons.FileText,
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 160.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(visible, key = { it.id }) { note ->
                        val cardShape = MaterialTheme.shapes.medium
                        val selected = note.id in selectedIds
                        ProductSurface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .appleCombinedClickable(
                                    pressedScale = 0.97f,
                                    pressedAlpha = 0.92f,
                                    onClick = {
                                        if (selecting) toggleSelection(note.id) else editing = note
                                    },
                                    onLongClick = { toggleSelection(note.id) },
                                ),
                            shape = cardShape,
                            containerColor = if (selected) {
                                MaterialTheme.colorScheme.secondaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerLow
                            },
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = androidx.compose.ui.Alignment.Top,
                            ) {
                                Checkbox(
                                    checked = note.is_completed,
                                    enabled = !selecting,
                                    onCheckedChange = { if (!selecting) vm.setCompleted(note, it) },
                                    modifier = Modifier
                                        .size(24.dp)
                                        .padding(top = 2.dp),
                                )
                                Spacer(Modifier.width(10.dp))
                                Column(
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                                    ) {
                                        AnimatedNoteTitle(note, Modifier.weight(1f))
                                        if (selecting) {
                                            Spacer(Modifier.width(8.dp))
                                            Icon(
                                                if (selected) Ym1rIcons.Check else Ym1rIcons.Check,
                                                contentDescription = if (selected) "已选中" else "未选中",
                                                tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(20.dp),
                                            )
                                        } else if (note.is_pinned) {
                                            Spacer(Modifier.width(8.dp))
                                            Icon(
                                                Ym1rIcons.Tag,
                                                contentDescription = "已置顶",
                                                modifier = Modifier.size(16.dp),
                                                tint = MaterialTheme.colorScheme.primary,
                                            )
                                        }
                                    }
                                    if (note.content.isNotBlank()) {
                                        Spacer(Modifier.height(3.dp))
                                        Text(
                                            note.content,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 4,
                                            overflow = TextOverflow.Ellipsis,
                                            textDecoration = if (note.is_completed) TextDecoration.LineThrough else null,
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

    if (creating || editing != null) {
        NoteEditor(
            note = editing,
            saving = state.saving,
            onDismiss = { creating = false; editing = null },
            onSave = { title, content, pinned, completed ->
                vm.save(editing, title, content, pinned, completed) { creating = false; editing = null }
            },
            onDelete = editing?.let { note -> { vm.delete(note) { editing = null } } },
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("删除 ${selectedNotes.size} 条笔记？") },
            text = { Text("删除后无法恢复，请确认操作。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        vm.deleteNotes(selectedNotes) {
                            selectedIds = emptySet()
                            confirmDelete = false
                        }
                    },
                ) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("取消") }
            },
        )
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun AnimatedNoteTitle(note: Note, modifier: Modifier = Modifier) {
    val progress by animateFloatAsState(
        targetValue = if (note.is_completed) 1f else 0f,
        animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
        label = "note-title-strike",
    )
    val strikeColor = MaterialTheme.colorScheme.onSurface
    var layout by remember(note.id) { mutableStateOf<TextLayoutResult?>(null) }

    Text(
        text = note.title,
        modifier = modifier.drawWithContent {
            drawContent()
            val textLayout = layout
            if (textLayout != null && progress > 0f) {
                val line = 0
                val start = textLayout.getLineLeft(line)
                val end = textLayout.getLineRight(line)
                val y = (textLayout.getLineTop(line) + textLayout.getLineBottom(line)) / 2f
                drawLine(
                    color = strikeColor,
                    start = Offset(start, y),
                    end = Offset(start + (end - start) * progress, y),
                    strokeWidth = 1.5.dp.toPx(),
                )
            }
        },
        onTextLayout = { layout = it },
        style = MaterialTheme.typography.titleMedium,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun NoteEditor(
    note: Note?,
    saving: Boolean,
    onDismiss: () -> Unit,
    onSave: (String, String, Boolean, Boolean) -> Unit,
    onDelete: (() -> Unit)?,
) {
    var title by remember(note?.id) { mutableStateOf(note?.title.orEmpty()) }
    var content by remember(note?.id) { mutableStateOf(note?.content.orEmpty()) }
    var pinned by remember(note?.id) { mutableStateOf(note?.is_pinned ?: false) }
    var completed by remember(note?.id) { mutableStateOf(note?.is_completed ?: false) }

    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text(if (note == null) "新建笔记" else "编辑笔记") },
        text = {
            Column {
                ProductField(
                    value = title,
                    onValueChange = { title = it },
                    label = "标题",
                    placeholder = "写下一个清晰的标题",
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))
                ProductField(
                    value = content,
                    onValueChange = { content = it },
                    label = "内容",
                    placeholder = "记录想法、待办或上下文",
                    minLines = 6,
                    singleLine = false,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth()) {
                    FilterChip(
                        selected = pinned,
                        onClick = { pinned = !pinned },
                        label = { Text(if (pinned) "已置顶" else "置顶") },
                        leadingIcon = { Icon(Ym1rIcons.Tag, contentDescription = null) },
                    )
                    Spacer(Modifier.width(8.dp))
                    FilterChip(
                        selected = completed,
                        onClick = { completed = !completed },
                        label = { Text(if (completed) "已完成" else "标记完成") },
                        leadingIcon = { Icon(Ym1rIcons.Check, contentDescription = null) },
                    )
                    Spacer(Modifier.weight(1f))
                    if (onDelete != null) {
                        IconButton(onClick = onDelete, enabled = !saving) {
                            Icon(Ym1rIcons.Trash2, contentDescription = "删除", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(title, content, pinned, completed) }, enabled = !saving) {
                if (saving) CircularProgressIndicator(Modifier.width(18.dp).height(18.dp), strokeWidth = 2.dp)
                else Text("保存")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !saving) { Text("取消") } },
    )
}
