package com.masteralanlab.emailbox.ui.screens.notes

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.masteralanlab.emailbox.data.Prefs
import com.masteralanlab.emailbox.data.remote.ApiResult
import com.masteralanlab.emailbox.data.remote.CreateNoteRequest
import com.masteralanlab.emailbox.data.remote.Note
import com.masteralanlab.emailbox.data.remote.UpdateNoteRequest
import com.masteralanlab.emailbox.data.remote.apiCall
import com.masteralanlab.emailbox.data.remote.apiCallUnit
import com.masteralanlab.emailbox.ui.components.EmptyBox
import com.masteralanlab.emailbox.ui.components.MainTopBar
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class NotesState(
    val items: List<Note> = emptyList(),
    val loading: Boolean = true,
    val saving: Boolean = false,
)

class NotesViewModel : ViewModel() {
    val state = MutableStateFlow(NotesState())
    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages = _messages.asSharedFlow()
    private var initialized = false

    fun init() {
        if (initialized) return
        initialized = true
        load()
    }

    fun load() = viewModelScope.launch {
        val tenant = Prefs.tenantId.orEmpty()
        if (tenant.isBlank()) {
            state.update { it.copy(loading = false) }
            _messages.tryEmit("未选择工作空间")
            return@launch
        }
        state.update { it.copy(loading = true) }
        when (val result = apiCall { notes(tenant) }) {
            is ApiResult.Success -> state.value = NotesState(result.data)
            is ApiResult.Failure -> {
                state.update { it.copy(loading = false) }
                _messages.tryEmit(result.message)
            }
        }
    }

    fun save(existing: Note?, title: String, content: String, pinned: Boolean, done: () -> Unit) {
        val cleanTitle = title.trim()
        if (cleanTitle.isBlank()) {
            _messages.tryEmit("请输入标题")
            return
        }
        viewModelScope.launch {
            state.update { it.copy(saving = true) }
            val tenant = Prefs.tenantId.orEmpty()
            val result = if (existing == null) {
                apiCall { createNote(tenant, CreateNoteRequest(cleanTitle, content.trim(), pinned)) }
            } else {
                apiCall { updateNote(tenant, existing.id, UpdateNoteRequest(cleanTitle, content.trim(), pinned)) }
            }
            state.update { it.copy(saving = false) }
            when (result) {
                is ApiResult.Success -> {
                    done()
                    load()
                }
                is ApiResult.Failure -> _messages.tryEmit(result.message)
            }
        }
    }

    fun delete(note: Note, done: () -> Unit) = viewModelScope.launch {
        when (val result = apiCallUnit { deleteNote(Prefs.tenantId.orEmpty(), note.id) }) {
            is ApiResult.Success -> {
                done()
                state.update { value -> value.copy(items = value.items.filterNot { it.id == note.id }) }
            }
            is ApiResult.Failure -> _messages.tryEmit(result.message)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotesScreen(vm: NotesViewModel = viewModel()) {
    val state by vm.state.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    var query by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf<Note?>(null) }
    var creating by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        vm.init()
        vm.messages.collect { snackbar.showSnackbar(it) }
    }

    val visible = remember(state.items, query) {
        val q = query.trim()
        state.items
            .filter { q.isBlank() || it.title.contains(q, true) || it.content.contains(q, true) }
            .sortedWith(compareByDescending<Note> { it.is_pinned }.thenByDescending { it.updated_at })
    }

    Scaffold(
        topBar = { MainTopBar("笔记") },
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            FloatingActionButton(onClick = { creating = true }) {
                Icon(Icons.Outlined.Add, contentDescription = "新建笔记")
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text("搜索标题或正文") },
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(18.dp),
            )
            if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (!state.loading && visible.isEmpty()) {
                EmptyBox(
                    text = if (query.isBlank()) "还没有笔记" else "没有匹配的笔记",
                    icon = Icons.Outlined.Description,
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(visible, key = { it.id }) { note ->
                        Card(
                            modifier = Modifier.fillMaxWidth().clickable { editing = note },
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                        ) {
                            Column(Modifier.padding(16.dp)) {
                                Row(Modifier.fillMaxWidth()) {
                                    Text(
                                        note.title,
                                        style = MaterialTheme.typography.titleMedium,
                                        modifier = Modifier.weight(1f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    if (note.is_pinned) {
                                        Spacer(Modifier.width(8.dp))
                                        Icon(Icons.Outlined.PushPin, contentDescription = "已置顶")
                                    }
                                }
                                if (note.content.isNotBlank()) {
                                    Spacer(Modifier.height(8.dp))
                                    Text(
                                        note.content,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 4,
                                        overflow = TextOverflow.Ellipsis,
                                    )
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
            onSave = { title, content, pinned ->
                vm.save(editing, title, content, pinned) { creating = false; editing = null }
            },
            onDelete = editing?.let { note -> { vm.delete(note) { editing = null } } },
        )
    }
}

@Composable
private fun NoteEditor(
    note: Note?,
    saving: Boolean,
    onDismiss: () -> Unit,
    onSave: (String, String, Boolean) -> Unit,
    onDelete: (() -> Unit)?,
) {
    var title by remember(note?.id) { mutableStateOf(note?.title.orEmpty()) }
    var content by remember(note?.id) { mutableStateOf(note?.content.orEmpty()) }
    var pinned by remember(note?.id) { mutableStateOf(note?.is_pinned ?: false) }

    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text(if (note == null) "新建笔记" else "编辑笔记") },
        text = {
            Column {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("标题") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    label = { Text("内容") },
                    minLines = 6,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth()) {
                    FilterChip(
                        selected = pinned,
                        onClick = { pinned = !pinned },
                        label = { Text(if (pinned) "已置顶" else "置顶") },
                        leadingIcon = { Icon(Icons.Outlined.PushPin, contentDescription = null) },
                    )
                    Spacer(Modifier.weight(1f))
                    if (onDelete != null) {
                        IconButton(onClick = onDelete, enabled = !saving) {
                            Icon(Icons.Outlined.Delete, contentDescription = "删除", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(title, content, pinned) }, enabled = !saving) {
                if (saving) CircularProgressIndicator(Modifier.width(18.dp).height(18.dp), strokeWidth = 2.dp)
                else Text("保存")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !saving) { Text("取消") } },
    )
}
