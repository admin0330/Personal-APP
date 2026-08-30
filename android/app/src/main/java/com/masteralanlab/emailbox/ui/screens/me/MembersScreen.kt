package com.masteralanlab.emailbox.ui.screens.me

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.masteralanlab.emailbox.data.Prefs
import com.masteralanlab.emailbox.data.remote.AddMemberRequest
import com.masteralanlab.emailbox.data.remote.ApiResult
import com.masteralanlab.emailbox.data.remote.TenantMember
import com.masteralanlab.emailbox.data.remote.UpdateMemberRoleRequest
import com.masteralanlab.emailbox.data.remote.apiCall
import com.masteralanlab.emailbox.data.remote.apiCallUnit
import com.masteralanlab.emailbox.ui.components.AppTopBar
import com.masteralanlab.emailbox.ui.components.DropdownField
import com.masteralanlab.emailbox.ui.components.EmptyBox
import com.masteralanlab.emailbox.ui.components.ErrorBox
import com.masteralanlab.emailbox.ui.components.Labels
import com.masteralanlab.emailbox.ui.components.LoadingBox
import com.masteralanlab.emailbox.ui.components.RadioOptionList
import com.masteralanlab.emailbox.ui.components.StatusChip
import com.masteralanlab.emailbox.util.formatFullTime
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private val ROLE_OPTIONS = listOf(
    "owner" to "所有者",
    "admin" to "管理员",
    "member" to "成员",
)

// ---------------------------------------------------------------- 数据层

class MembersViewModel : ViewModel() {

    private val _memberList = MutableStateFlow<List<TenantMember>>(emptyList())
    val memberList: StateFlow<List<TenantMember>> = _memberList.asStateFlow()

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
        val tenantId = Prefs.tenantId
        if (tenantId.isNullOrBlank()) {
            _memberList.value = emptyList()
            _error.value = "尚未选择工作空间"
            return
        }
        when (val r = apiCall { members(tenantId) }) {
            is ApiResult.Success -> {
                _memberList.value = r.data
                _error.value = null
            }

            is ApiResult.Failure -> {
                _memberList.value = emptyList()
                _error.value = r.message
            }
        }
    }

    fun add(username: String, role: String) {
        val tenantId = Prefs.tenantId ?: return
        val trimmed = username.trim()
        if (trimmed.isBlank()) {
            _actionError.value = "请填写用户名"
            return
        }
        viewModelScope.launch {
            _saving.value = true
            _actionError.value = null
            when (val r = apiCall { addMember(tenantId, AddMemberRequest(trimmed, role)) }) {
                is ApiResult.Success -> {
                    _notice.value = "已添加成员「${r.data.username}」"
                    load()
                }

                is ApiResult.Failure -> _actionError.value = r.message
            }
            _saving.value = false
        }
    }

    fun changeRole(member: TenantMember, role: String) {
        val tenantId = Prefs.tenantId ?: return
        viewModelScope.launch {
            _saving.value = true
            _actionError.value = null
            when (val r = apiCallUnit {
                updateMember(tenantId, member.user_id, UpdateMemberRoleRequest(role))
            }) {
                is ApiResult.Success -> {
                    _notice.value = "已把「${member.username}」的角色改为${Labels.tenantRole(role)}"
                    load()
                }

                is ApiResult.Failure -> _actionError.value = r.message
            }
            _saving.value = false
        }
    }

    fun remove(member: TenantMember) {
        val tenantId = Prefs.tenantId ?: return
        viewModelScope.launch {
            _saving.value = true
            _actionError.value = null
            when (val r = apiCallUnit { deleteMember(tenantId, member.user_id) }) {
                is ApiResult.Success -> {
                    _notice.value = "已移除成员「${member.username}」"
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
fun MembersScreen(onBack: () -> Unit) {
    val vm: MembersViewModel = viewModel()

    val memberList by vm.memberList.collectAsState()
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

    var showAdd by remember { mutableStateOf(false) }
    var roleTarget by remember { mutableStateOf<TenantMember?>(null) }
    var removeTarget by remember { mutableStateOf<TenantMember?>(null) }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = { AppTopBar(title = "成员管理", onBack = onBack) },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAdd = true }) {
                Icon(Icons.Outlined.PersonAdd, contentDescription = "添加成员")
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when {
                loading -> LoadingBox(text = "正在加载成员…")

                error != null -> ErrorBox(message = error ?: "加载失败", onRetry = vm::refresh)

                memberList.isEmpty() -> EmptyBox(
                    text = "该工作空间还没有其他成员",
                    icon = Icons.Outlined.Group,
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
                        items(memberList, key = { it.id.ifBlank { it.user_id } }) { member ->
                            MemberCard(
                                member = member,
                                onChangeRole = { roleTarget = member },
                                onRemove = { removeTarget = member },
                            )
                        }
                        item {
                            Text(
                                "所有者可以管理成员与工作空间，管理员可以管理邮箱账号，成员只有读取权限。",
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

    if (showAdd) {
        AddMemberSheet(
            saving = saving,
            onDismiss = { showAdd = false },
            onConfirm = { name, role ->
                showAdd = false
                vm.add(name, role)
            },
        )
    }

    roleTarget?.let { member ->
        var role by remember(member.id) { mutableStateOf(member.role) }
        AlertDialog(
            onDismissRequest = { roleTarget = null },
            title = { Text("修改角色") },
            text = {
                Column {
                    Text("为「${member.username}」选择新的角色：")
                    Spacer(Modifier.height(8.dp))
                    RadioOptionList(
                        options = ROLE_OPTIONS,
                        selected = role,
                        onSelect = { role = it },
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !saving,
                    onClick = {
                        roleTarget = null
                        if (role != member.role) vm.changeRole(member, role)
                    },
                ) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { roleTarget = null }) { Text("取消") }
            },
        )
    }

    removeTarget?.let { member ->
        AlertDialog(
            onDismissRequest = { removeTarget = null },
            title = { Text("移除成员") },
            text = {
                Text(
                    "确定把「${member.username}」从当前工作空间移除吗？\n\n" +
                        "移除后该用户将失去对本工作空间下所有邮箱账号的访问权限。",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        removeTarget = null
                        vm.remove(member)
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                ) { Text("移除") }
            },
            dismissButton = {
                TextButton(onClick = { removeTarget = null }) { Text("取消") }
            },
        )
    }
}

// ---------------------------------------------------------------- 列表项

@Composable
private fun MemberCard(
    member: TenantMember,
    onChangeRole: () -> Unit,
    onRemove: () -> Unit,
) {
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    member.username,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f, fill = false),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.width(8.dp))
                StatusChip(
                    text = Labels.tenantRole(member.role),
                    container = MaterialTheme.colorScheme.secondaryContainer,
                    content = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
            if (!member.email.isNullOrBlank()) {
                Spacer(Modifier.height(3.dp))
                Text(
                    member.email ?: "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "加入时间：${formatFullTime(member.created_at).ifBlank { "—" }}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            HorizontalDivider(Modifier.padding(vertical = 6.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onChangeRole) { Text("修改角色") }
                Spacer(Modifier.width(4.dp))
                TextButton(
                    onClick = onRemove,
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                ) {
                    Icon(Icons.Outlined.Delete, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("移除")
                }
            }
        }
    }
}

// ---------------------------------------------------------------- 添加成员

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddMemberSheet(
    saving: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit,
) {
    var username by remember { mutableStateOf("") }
    var role by remember { mutableStateOf("member") }
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
            Text("添加成员", style = MaterialTheme.typography.titleMedium)

            OutlinedTextField(
                value = username,
                onValueChange = { username = it; localError = null },
                label = { Text("用户名") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                supportingText = { Text("该用户必须已在平台上注册") },
            )

            DropdownField(
                label = "角色",
                options = ROLE_OPTIONS.map { (value, text) -> value to text },
                selected = role,
                onSelect = { role = it ?: "member" },
                modifier = Modifier.fillMaxWidth(),
            )

            if (localError != null) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        localError ?: "",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer,
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
                        if (username.trim().isBlank()) {
                            localError = "请填写用户名"
                        } else {
                            localError = null
                            onConfirm(username, role)
                        }
                    },
                ) { Text(if (saving) "添加中…" else "添加") }
            }
        }
    }
}
