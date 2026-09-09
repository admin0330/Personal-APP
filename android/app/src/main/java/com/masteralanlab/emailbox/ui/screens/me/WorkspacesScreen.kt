package com.masteralanlab.emailbox.ui.screens.me

import com.masteralanlab.emailbox.ui.components.Ym1rIcons
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.masteralanlab.emailbox.data.Prefs
import com.masteralanlab.emailbox.data.remote.ApiResult
import com.masteralanlab.emailbox.data.remote.CreateTenantRequest
import com.masteralanlab.emailbox.data.remote.Tenant
import com.masteralanlab.emailbox.data.remote.UpdateTenantRequest
import com.masteralanlab.emailbox.data.remote.apiCall
import com.masteralanlab.emailbox.data.remote.apiCallUnit
import com.masteralanlab.emailbox.ui.components.AppTopBar
import com.masteralanlab.emailbox.ui.components.EmptyBox
import com.masteralanlab.emailbox.ui.components.ErrorBox
import com.masteralanlab.emailbox.ui.components.LoadingBox
import com.masteralanlab.emailbox.ui.components.ProductField
import com.masteralanlab.emailbox.ui.components.ProductSurface
import com.masteralanlab.emailbox.ui.components.StatusChip
import com.masteralanlab.emailbox.util.formatFullTime
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private const val KIND_PERSONAL = "personal"

private fun kindText(kind: String?): String = when (kind) {
    KIND_PERSONAL -> "个人"
    "team" -> "团队"
    else -> kind?.takeIf { it.isNotBlank() } ?: "—"
}

// ---------------------------------------------------------------- 数据层

class WorkspacesViewModel : ViewModel() {

    private val _tenantList = MutableStateFlow<List<Tenant>>(emptyList())
    val tenantList: StateFlow<List<Tenant>> = _tenantList.asStateFlow()

    private val _loading = MutableStateFlow(true)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _saving = MutableStateFlow(false)
    val saving: StateFlow<Boolean> = _saving.asStateFlow()

    private val _actionError = MutableStateFlow<String?>(null)
    val actionError: StateFlow<String?> = _actionError.asStateFlow()

    private val _notice = MutableStateFlow<String?>(null)
    val notice: StateFlow<String?> = _notice.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _refreshing.value = true
            load()
            _refreshing.value = false
            _loading.value = false
        }
    }

    private suspend fun load() {
        when (val r = apiCall { tenants() }) {
            is ApiResult.Success -> {
                _tenantList.value = r.data
                _error.value = null
            }

            is ApiResult.Failure -> {
                _tenantList.value = emptyList()
                _error.value = r.message
            }
        }
    }

    fun select(tenant: Tenant) {
        if (tenant.id == Prefs.tenantId) {
            _notice.value = "当前已在该工作空间"
            return
        }
        viewModelScope.launch {
            _saving.value = true
            _actionError.value = null
            when (val r = apiCallUnit { selectTenant(tenant.id) }) {
                is ApiResult.Success -> {
                    // tenantId 只影响路径参数，不必重建 Retrofit
                    Prefs.tenantId = tenant.id
                    Prefs.tenantName = tenant.name
                    _notice.value = "已切换到「${tenant.name}」"
                }

                is ApiResult.Failure -> _actionError.value = r.message
            }
            _saving.value = false
        }
    }

    fun create(name: String, slug: String) {
        val trimmed = name.trim()
        if (trimmed.isBlank()) {
            _actionError.value = "请填写工作空间名称"
            return
        }
        viewModelScope.launch {
            _saving.value = true
            _actionError.value = null
            val body = CreateTenantRequest(
                name = trimmed,
                slug = slug.trim().ifEmpty { null },
            )
            when (val r = apiCall { createTenant(body) }) {
                is ApiResult.Success -> {
                    _notice.value = "工作空间「${r.data.name}」已创建"
                    load()
                }

                is ApiResult.Failure -> _actionError.value = r.message
            }
            _saving.value = false
        }
    }

    fun rename(tenant: Tenant, name: String, slug: String) {
        viewModelScope.launch {
            _saving.value = true
            _actionError.value = null
            // 两个字段均可空：空串表示不修改
            val body = UpdateTenantRequest(
                name = name.trim().ifEmpty { null },
                slug = slug.trim().ifEmpty { null },
            )
            when (val r = apiCall { updateTenant(tenant.id, body) }) {
                is ApiResult.Success -> {
                    _notice.value = "已保存"
                    if (tenant.id == Prefs.tenantId) Prefs.tenantName = r.data.name
                    load()
                }

                is ApiResult.Failure -> _actionError.value = r.message
            }
            _saving.value = false
        }
    }

    fun remove(tenant: Tenant) {
        viewModelScope.launch {
            _saving.value = true
            _actionError.value = null
            when (val r = apiCallUnit { deleteTenant(tenant.id) }) {
                is ApiResult.Success -> {
                    _notice.value = "工作空间「${tenant.name}」已删除"
                    if (tenant.id == Prefs.tenantId) {
                        Prefs.tenantId = null
                        Prefs.tenantName = null
                    }
                    load()
                }

                is ApiResult.Failure -> _actionError.value = r.message
            }
            _saving.value = false
        }
    }

    fun consumeNotice() {
        _notice.value = null
    }

    fun consumeActionError() {
        _actionError.value = null
    }
}

// ---------------------------------------------------------------- 页面

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkspacesScreen(onBack: () -> Unit) {
    val vm: WorkspacesViewModel = viewModel()

    val tenantList by vm.tenantList.collectAsState()
    val loading by vm.loading.collectAsState()
    val refreshing by vm.refreshing.collectAsState()
    val error by vm.error.collectAsState()
    val saving by vm.saving.collectAsState()
    val actionError by vm.actionError.collectAsState()
    val notice by vm.notice.collectAsState()

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

    var creating by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Tenant?>(null) }
    var deleting by remember { mutableStateOf<Tenant?>(null) }
    var currentId by remember { mutableStateOf(Prefs.tenantId) }

    // 切换成功後重新读取本地记录，让选中态跟着变
    LaunchedEffect(tenantList, notice) {
        currentId = Prefs.tenantId
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = { AppTopBar(title = "工作空间", onBack = onBack) },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { creating = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ) {
                Icon(Ym1rIcons.Plus, contentDescription = "新建工作空间")
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when {
                loading -> LoadingBox(text = "正在加载工作空间…")

                error != null -> ErrorBox(message = error ?: "加载失败", onRetry = vm::refresh)

                tenantList.isEmpty() -> EmptyBox(
                    text = "还没有任何工作空间",
                    icon = Ym1rIcons.Folder,
                )

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
                        items(tenantList, key = { it.id }) { tenant ->
                            TenantCard(
                                tenant = tenant,
                                selected = tenant.id == currentId,
                                onClick = { vm.select(tenant) },
                                onRename = { editing = tenant },
                                onDelete = { deleting = tenant },
                            )
                        }
                        item {
                            Text(
                                "点击卡片即可切换工作空间。个人工作空间不能删除，也不能修改标识（slug）。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 8.dp),
                            )
                        }
                    }
                }
            }
        }
    }

    if (creating) {
        TenantEditorSheet(
            tenant = null,
            saving = saving,
            onDismiss = { creating = false },
            onSave = { name, slug ->
                creating = false
                vm.create(name, slug)
            },
        )
    }

    editing?.let { tenant ->
        TenantEditorSheet(
            tenant = tenant,
            saving = saving,
            onDismiss = { editing = null },
            onSave = { name, slug ->
                editing = null
                vm.rename(tenant, name, slug)
            },
        )
    }

    deleting?.let { tenant ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("删除工作空间") },
            text = {
                Text(
                    "确定删除「${tenant.name}」吗？\n\n" +
                        "该工作空间下的分组、邮箱账号与全部配置都会被移除，且无法恢复。" +
                        "个人工作空间不允许删除，服务端会直接拒绝。",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        deleting = null
                        vm.remove(tenant)
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                ) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { deleting = null }) { Text("取消") }
            },
        )
    }
}

// ---------------------------------------------------------------- 列表项

@Composable
private fun TenantCard(
    tenant: Tenant,
    selected: Boolean,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    val isPersonal = tenant.kind == KIND_PERSONAL
    ProductSurface(modifier = Modifier.fillMaxWidth(), onClick = onClick) {
        Column(Modifier.fillMaxWidth().padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    tenant.name,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f, fill = false),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.width(8.dp))
                StatusChip(
                    text = kindText(tenant.kind),
                    container = MaterialTheme.colorScheme.surfaceVariant,
                    content = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (selected) {
                    Spacer(Modifier.width(6.dp))
                    StatusChip(
                        text = "当前",
                        container = MaterialTheme.colorScheme.primaryContainer,
                        content = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "标识：${tenant.slug.ifBlank { "—" }}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "创建时间：${formatFullTime(tenant.created_at).ifBlank { "—" }}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            HorizontalDivider(Modifier.padding(vertical = 6.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onRename) {
                    Icon(Ym1rIcons.Pencil, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("重命名")
                }
                if (!isPersonal) {
                    Spacer(Modifier.width(4.dp))
                    TextButton(
                        onClick = onDelete,
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.error,
                        ),
                    ) {
                        Icon(Ym1rIcons.Trash2, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text("删除")
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- 编辑表单

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TenantEditorSheet(
    tenant: Tenant?,
    saving: Boolean,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit,
) {
    val isEdit = tenant != null
    val isPersonal = tenant?.kind == KIND_PERSONAL

    var name by remember { mutableStateOf(tenant?.name.orEmpty()) }
    var slug by remember { mutableStateOf("") }
    var localError by remember { mutableStateOf<String?>(null) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = 16.dp)
                .padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                if (isEdit) "重命名工作空间" else "新建工作空间",
                style = MaterialTheme.typography.titleMedium,
            )

            ProductField(
                value = name,
                onValueChange = { name = it; localError = null },
                label = "名称",
                placeholder = "例如：家庭邮箱",
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )

            ProductField(
                value = slug,
                onValueChange = { slug = it; localError = null },
                label = "标识（slug）",
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !isPersonal,
                supporting = if (isPersonal) {
                    "个人工作空间的标识不可修改"
                } else if (isEdit) {
                    "留空表示不修改"
                } else {
                    "留空时由服务端自动生成"
                },
            )

            if (localError != null) {
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        localError ?: "",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    )
                }
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss, enabled = !saving) { Text("取消") }
                Spacer(Modifier.width(8.dp))
                TextButton(
                    enabled = !saving,
                    onClick = {
                        if (!isEdit && name.trim().isBlank()) {
                            localError = "请填写工作空间名称"
                        } else {
                            localError = null
                            onSave(name, slug)
                        }
                    },
                ) { Text(if (saving) "保存中…" else "保存") }
            }
        }
    }
}
