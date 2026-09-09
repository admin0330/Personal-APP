package com.masteralanlab.emailbox.ui.screens.groups

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
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
import com.masteralanlab.emailbox.ui.components.Ym1rIcons
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.masteralanlab.emailbox.data.Prefs
import com.masteralanlab.emailbox.data.RefreshBus
import com.masteralanlab.emailbox.data.remote.ApiResult
import com.masteralanlab.emailbox.data.remote.CreateGroupRequest
import com.masteralanlab.emailbox.data.remote.MailGroup
import com.masteralanlab.emailbox.data.remote.ReorderGroupsRequest
import com.masteralanlab.emailbox.data.remote.UpdateGroupRequest
import com.masteralanlab.emailbox.data.remote.apiCall
import com.masteralanlab.emailbox.data.remote.apiCallUnit
import com.masteralanlab.emailbox.ui.components.AppTopBar
import com.masteralanlab.emailbox.ui.components.DropdownField
import com.masteralanlab.emailbox.ui.components.EmptyBox
import com.masteralanlab.emailbox.ui.components.ErrorBox
import com.masteralanlab.emailbox.ui.components.LabeledField
import com.masteralanlab.emailbox.ui.components.LoadingBox
import com.masteralanlab.emailbox.ui.components.ProductField
import com.masteralanlab.emailbox.ui.components.groupTint
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

val GROUP_COLORS = listOf(
    "blue" to "蓝色",
    "green" to "绿色",
    "amber" to "橙色",
    "red" to "红色",
    "purple" to "紫色",
    "gray" to "灰色",
)

data class GroupsState(
    val groups: List<MailGroup> = emptyList(),
    val loading: Boolean = true,
    val acting: Boolean = false,
    val error: String? = null,
)

class GroupsViewModel : ViewModel() {

    private val _state = MutableStateFlow(GroupsState())
    val state: MutableStateFlow<GroupsState> get() = _state

    private val _message = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val message = _message.asSharedFlow()

    fun load() = viewModelScope.launch {
        val tenant = Prefs.tenantId
        if (tenant.isNullOrBlank()) {
            _state.update { it.copy(loading = false, error = "未选择工作空间") }
            return@launch
        }
        _state.update { it.copy(loading = true, error = null) }
        when (val r = apiCall { groups(tenant) }) {
            is ApiResult.Success -> _state.update { it.copy(loading = false, groups = r.data) }
            is ApiResult.Failure -> _state.update { it.copy(loading = false, error = r.message) }
        }
    }

    fun delete(group: MailGroup) = viewModelScope.launch {
        if (Prefs.apiKeyMode) return@launch
        val tenant = Prefs.tenantId ?: return@launch
        _state.update { it.copy(acting = true) }
        when (val r = apiCallUnit { deleteGroup(tenant, group.id) }) {
            is ApiResult.Success -> _message.tryEmit("已删除「${group.name}」")
            is ApiResult.Failure -> _message.tryEmit(r.message)
        }
        _state.update { it.copy(acting = false) }
        load()
    }

    fun move(from: Int, to: Int) {
        if (Prefs.apiKeyMode) return
        val list = _state.value.groups.toMutableList()
        if (to !in list.indices || from !in list.indices) return
        val item = list.removeAt(from)
        list.add(to, item)
        _state.update { it.copy(groups = list) }
        syncOrder(list)
    }

    private fun syncOrder(list: List<MailGroup>) = viewModelScope.launch {
        if (Prefs.apiKeyMode) return@launch
        val tenant = Prefs.tenantId ?: return@launch
        _state.update { it.copy(acting = true) }
        when (val r = apiCallUnit { reorderGroups(tenant, ReorderGroupsRequest(list.map { it.id })) }) {
            is ApiResult.Success -> Unit
            is ApiResult.Failure -> _message.tryEmit(r.message)
        }
        _state.update { it.copy(acting = false) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupsScreen(onNavigate: (String) -> Unit) {
    val vm: GroupsViewModel = viewModel()
    val state by vm.state.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    val readOnly = Prefs.apiKeyMode
    var pendingDelete by remember { mutableStateOf<MailGroup?>(null) }

    LaunchedEffect(vm) {
        vm.message.collect { snackbar.showSnackbar(it) }
    }
    LaunchedEffect(vm) {
        vm.load()
        RefreshBus.events.collect { if (it == RefreshBus.Kind.Groups) vm.load() }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            AppTopBar(
                title = "分组",
                actions = {
                    IconButton(onClick = { vm.load() }) {
                        Icon(Ym1rIcons.RefreshCw, contentDescription = "刷新")
                    }
                },
            )
        },
        floatingActionButton = {
            if (!readOnly) {
                FloatingActionButton(
                    onClick = { onNavigate(com.masteralanlab.emailbox.ui.nav.Route.groupEdit()) },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ) {
                    Icon(Ym1rIcons.Plus, contentDescription = "新建分组")
                }
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding)) {
            when {
                state.loading -> LoadingBox()
                state.error != null && state.groups.isEmpty() -> ErrorBox(state.error!!) { vm.load() }
                state.groups.isEmpty() -> EmptyBox("还没有分组", Ym1rIcons.Folder) {
                    if (!readOnly) {
                        TextButton(onClick = { onNavigate(com.masteralanlab.emailbox.ui.nav.Route.groupEdit()) }) {
                            Text("新建分组")
                        }
                    }
                }

                else -> PullToRefreshBox(isRefreshing = false, onRefresh = { vm.load() }) {
                    LazyColumn(
                        Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 96.dp),
                    ) {
                        if (state.acting) {
                            item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
                        }
                        itemsIndexed(state.groups, key = { _, g -> g.id }) { index, group ->
                            GroupRow(
                                group = group,
                                readOnly = readOnly,
                                canUp = index > 0,
                                canDown = index < state.groups.lastIndex,
                                onUp = { vm.move(index, index - 1) },
                                onDown = { vm.move(index, index + 1) },
                                onEdit = { onNavigate(com.masteralanlab.emailbox.ui.nav.Route.groupEdit(group.id)) },
                                onDelete = { pendingDelete = group },
                            )
                        }
                    }
                }
            }
        }
    }

    pendingDelete?.let { g ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("删除分组「${g.name}」？") },
            text = {
                Text(
                    if (g.is_system) "默认分组不能删除。"
                    else "组内的 ${g.account_count} 个账号会先回落到默认分组，再删除这个分组。"
                )
            },
            confirmButton = {
                if (!g.is_system) {
                    TextButton(onClick = { pendingDelete = null; vm.delete(g) }) { Text("删除") }
                }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text(if (g.is_system) "知道了" else "取消") } },
        )
    }
}

@Composable
private fun GroupRow(
    group: MailGroup,
    readOnly: Boolean,
    canUp: Boolean,
    canDown: Boolean,
    onUp: () -> Unit,
    onDown: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    ListItem(
        leadingContent = {
            Box(
                Modifier
                    .size(32.dp)
                    .background(groupTint(group.color), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Ym1rIcons.Folder,
                    null,
                    Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.surface,
                )
            }
        },
        headlineContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(group.name, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, false))
                if (group.is_system) {
                    Spacer(Modifier.width(6.dp))
                    Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.secondaryContainer) {
                        Text(
                            "默认",
                            Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                    }
                }
            }
        },
        supportingContent = {
            Column {
                Text("${group.account_count} 个账号", style = MaterialTheme.typography.bodySmall)
                if (!group.proxy_url_masked.isNullOrBlank()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Ym1rIcons.Shield,
                            null,
                            Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            group.proxy_url_masked!!,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                if (!group.description.isNullOrBlank()) {
                    Text(
                        group.description!!,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        },
        trailingContent = {
            if (!readOnly) {
                Row {
                    IconButton(onClick = onUp, enabled = canUp) {
                        Icon(Ym1rIcons.ArrowUp, contentDescription = "上移")
                    }
                    IconButton(onClick = onDown, enabled = canDown) {
                        Icon(Ym1rIcons.ArrowDown, contentDescription = "下移")
                    }
                    IconButton(onClick = onEdit) { Icon(Ym1rIcons.Pencil, contentDescription = "编辑") }
                    IconButton(onClick = onDelete, enabled = !group.is_system) {
                        Icon(Ym1rIcons.Trash2, contentDescription = "删除")
                    }
                }
            }
        },
    )
}

// ------------------------------------------------------------------ 新建 / 编辑

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupEditScreen(groupId: String?, onBack: () -> Unit, onSaved: () -> Unit) {
    val vm: GroupsViewModel = viewModel()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var color by remember { mutableStateOf<String?>("gray") }
    var proxy by remember { mutableStateOf("") }
    var fb1 by remember { mutableStateOf("") }
    var fb2 by remember { mutableStateOf("") }
    var clearProxy by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(groupId != null) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var masked by remember { mutableStateOf("") }
    var isSystem by remember { mutableStateOf(false) }

    LaunchedEffect(groupId) {
        if (groupId == null) return@LaunchedEffect
        val tenant = Prefs.tenantId ?: return@LaunchedEffect
        when (val r = apiCall { groups(tenant) }) {
            is ApiResult.Success -> {
                val g = r.data.firstOrNull { it.id == groupId }
                if (g == null) {
                    error = "分组不存在"
                } else {
                    name = g.name
                    description = g.description.orEmpty()
                    color = g.color
                    masked = g.proxy_url_masked.orEmpty()
                    isSystem = g.is_system
                }
            }

            is ApiResult.Failure -> error = r.message
        }
        loading = false
    }

    LaunchedEffect(vm) { vm.message.collect { snackbar.showSnackbar(it) } }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = { AppTopBar(title = if (groupId == null) "新建分组" else "编辑分组", onBack = onBack) },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .padding(16.dp)
                .fillMaxSize(),
        ) {
            if (loading) {
                LoadingBox()
                return@Scaffold
            }

            LabeledField(name, { name = it }, "分组名称", supporting = "必填，最长 100 字符")
            Spacer(Modifier.height(12.dp))
            LabeledField(description, { description = it }, "说明（可留空）")
            Spacer(Modifier.height(12.dp))
            DropdownField(
                label = "颜色",
                options = GROUP_COLORS.map { (k, v) -> k to v },
                selected = color,
                onSelect = { color = it },
            )
            Spacer(Modifier.height(16.dp))
            Text("分组代理", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            if (masked.isNotBlank()) {
                Text(
                    "当前已设置：$masked",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                "留空表示不修改。支持 {mail} 模板，例如 socks5h://user:pass@host:1080",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            ProductField(proxy, { proxy = it }, label = "主代理", modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            ProductField(fb1, { fb1 = it }, label = "备用代理 1", modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            ProductField(fb2, { fb2 = it }, label = "备用代理 2", modifier = Modifier.fillMaxWidth())
            if (groupId != null) {
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.material3.Checkbox(checked = clearProxy, onCheckedChange = { clearProxy = it })
                    Spacer(Modifier.width(8.dp))
                    Text("清空分组代理（三个字段一起清空）", style = MaterialTheme.typography.bodyMedium)
                }
            }

            if (error != null) {
                Spacer(Modifier.height(12.dp))
                Text(error!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }

            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TextButton(onClick = onBack, modifier = Modifier.weight(1f)) { Text("取消") }
                androidx.compose.material3.Button(
                    onClick = {
                        scope.launch {
                            saving = true
                            error = null
                            val tenant = Prefs.tenantId
                            if (tenant.isNullOrBlank()) {
                                error = "未选择工作空间"
                                saving = false
                                return@launch
                            }
                            val result = if (groupId == null) {
                                apiCall {
                                    createGroup(
                                        tenant,
                                        CreateGroupRequest(
                                            name = name.trim(),
                                            description = description.trim().ifBlank { null },
                                            color = color,
                                            proxy_url = proxy.trim().ifBlank { null },
                                            fallback_proxy_url_1 = fb1.trim().ifBlank { null },
                                            fallback_proxy_url_2 = fb2.trim().ifBlank { null },
                                        ),
                                    )
                                }
                            } else {
                                apiCall {
                                    updateGroup(
                                        tenant,
                                        groupId,
                                        UpdateGroupRequest(
                                            name = name.trim().ifBlank { null },
                                            description = description.trim().ifBlank { null },
                                            color = color,
                                            proxy_url = if (clearProxy) "" else proxy.trim().ifBlank { null },
                                            fallback_proxy_url_1 = if (clearProxy) "" else fb1.trim().ifBlank { null },
                                            fallback_proxy_url_2 = if (clearProxy) "" else fb2.trim().ifBlank { null },
                                        ),
                                    )
                                }
                            }
                            when (result) {
                                is ApiResult.Success -> onSaved()
                                is ApiResult.Failure -> error = result.message
                            }
                            saving = false
                        }
                    },
                    enabled = !saving && name.isNotBlank() && !isSystem,
                    modifier = Modifier.weight(1f),
                ) {
                    if (saving) {
                        androidx.compose.material3.CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Text("保存")
                    }
                }
            }
            if (isSystem) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "默认分组只能修改名称、说明和颜色。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
