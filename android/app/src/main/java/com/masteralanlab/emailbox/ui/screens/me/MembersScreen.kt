package com.masteralanlab.emailbox.ui.screens.me

import com.masteralanlab.emailbox.ui.components.Ym1rIcons
import android.content.ClipData
import android.content.Context
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.masteralanlab.emailbox.data.Prefs
import com.masteralanlab.emailbox.data.remote.ApiResult
import com.masteralanlab.emailbox.data.remote.CreateInviteRequest
import com.masteralanlab.emailbox.data.remote.SignupInviteCreated
import com.masteralanlab.emailbox.data.remote.TenantMember
import com.masteralanlab.emailbox.data.remote.UpdateMemberRoleRequest
import com.masteralanlab.emailbox.data.remote.apiCall
import com.masteralanlab.emailbox.data.remote.apiCallUnit
import com.masteralanlab.emailbox.ui.components.AppTopBar
import com.masteralanlab.emailbox.ui.components.EmptyBox
import com.masteralanlab.emailbox.ui.components.ErrorBox
import com.masteralanlab.emailbox.ui.components.Labels
import com.masteralanlab.emailbox.ui.components.LoadingBox
import com.masteralanlab.emailbox.ui.components.ProductSurface
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

    private val _invite = MutableStateFlow<SignupInviteCreated?>(null)
    val invite: StateFlow<SignupInviteCreated?> = _invite.asStateFlow()

    fun refresh(silent: Boolean = false) {
        viewModelScope.launch {
            if (!silent) _refreshing.value = true
            load()
            if (!silent) _refreshing.value = false
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

    fun createInvite() {
        viewModelScope.launch {
            _saving.value = true
            _actionError.value = null
            when (val r = apiCall { createInvite(CreateInviteRequest(valid_hours = 24)) }) {
                is ApiResult.Success -> _invite.value = r.data

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

    fun consumeInvite() {
        _invite.value = null
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
    val invite by vm.invite.collectAsState()
    val context = LocalContext.current

    // 每次重新进入成员管理都静默拉取一次；不复用上次页面停留期间的旧列表。
    LaunchedEffect(Unit) { vm.refresh(silent = true) }

    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
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

    var roleTarget by remember { mutableStateOf<TenantMember?>(null) }
    var removeTarget by remember { mutableStateOf<TenantMember?>(null) }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = { AppTopBar(title = "成员管理", onBack = onBack) },
        floatingActionButton = {
            if (Prefs.isPlatformAdmin) {
                FloatingActionButton(
                    onClick = { if (!saving) vm.createInvite() },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ) {
                    Icon(Ym1rIcons.Key, contentDescription = "生成邀请码")
                }
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when {
                loading -> LoadingBox(text = "正在加载成员…")

                error != null -> ErrorBox(message = error ?: "加载失败", onRetry = { vm.refresh() })

                memberList.isEmpty() -> EmptyBox(
                    text = "该工作空间还没有其他成员",
                    icon = Ym1rIcons.Users,
                )

                else -> PullToRefreshBox(
                    isRefreshing = refreshing,
                    onRefresh = { vm.refresh() },
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

    invite?.let { generated ->
        InviteDialog(
            invite = generated,
            onCopy = {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                if (clipboard == null) {
                    scope.launch { snackbar.showSnackbar("无法访问剪贴板，请手动记录邀请码") }
                } else {
                    clipboard.setPrimaryClip(ClipData.newPlainText("Emailbox 邀请码", generated.code))
                    scope.launch { snackbar.showSnackbar("邀请码已复制") }
                }
            },
            onDismiss = vm::consumeInvite,
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
    ProductSurface(modifier = Modifier.fillMaxWidth()) {
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
                    Icon(Ym1rIcons.Trash2, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("移除")
                }
            }
        }
    }
}

// ---------------------------------------------------------------- 邀请码

@Composable
private fun InviteDialog(
    invite: SignupInviteCreated,
    onCopy: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Ym1rIcons.Key, contentDescription = null) },
        title = { Text("邀请码已生成") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("邀请码 24 小时内有效，只能使用一次；关闭后不会再次显示。")
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                ) {
                    Text(
                        invite.code.ifBlank { "生成失败：服务端未返回邀请码" },
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
                    )
                }
                Text(
                    "过期时间：${formatFullTime(invite.expires_at).ifBlank { "24 小时后" }}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onCopy, enabled = invite.code.isNotBlank()) {
                Icon(Ym1rIcons.Copy, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("复制邀请码")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("关闭") } },
    )
}
