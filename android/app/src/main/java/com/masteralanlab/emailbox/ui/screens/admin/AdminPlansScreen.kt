package com.masteralanlab.emailbox.ui.screens.admin

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.masteralanlab.emailbox.data.Prefs
import com.masteralanlab.emailbox.data.remote.ApiResult
import com.masteralanlab.emailbox.data.remote.CreatePlanRequest
import com.masteralanlab.emailbox.data.remote.Plan
import com.masteralanlab.emailbox.data.remote.UpdatePlanRequest
import com.masteralanlab.emailbox.data.remote.apiCall
import com.masteralanlab.emailbox.data.remote.apiCallUnit
import com.masteralanlab.emailbox.ui.components.AppTopBar
import com.masteralanlab.emailbox.ui.components.EmptyBox
import com.masteralanlab.emailbox.ui.components.ErrorBox
import com.masteralanlab.emailbox.ui.components.LoadingBox
import com.masteralanlab.emailbox.ui.components.StatusChip
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** -1 在后端表示「不限」。 */
private const val UNLIMITED = -1

private fun limitText(value: Int): String = if (value < 0) "不限" else value.toString()

private fun parseLimit(raw: String): Int = raw.trim().toIntOrNull() ?: UNLIMITED

private val CODE_PATTERN = Regex("[a-z0-9_-]+")

/** 套餐编辑表单的草稿，三个上限用字符串承载，便于原样回显。 */
data class PlanDraft(
    val code: String = "",
    val name: String = "",
    val isDefault: Boolean = false,
    val maxAccounts: String = UNLIMITED.toString(),
    val maxGroups: String = UNLIMITED.toString(),
    val dailyFetch: String = UNLIMITED.toString(),
) {
    companion object {
        fun of(plan: Plan?): PlanDraft = if (plan == null) {
            PlanDraft()
        } else {
            PlanDraft(
                code = plan.code,
                name = plan.name,
                isDefault = plan.is_default,
                maxAccounts = plan.max_accounts.toString(),
                maxGroups = plan.max_groups.toString(),
                dailyFetch = plan.daily_mail_fetch.toString(),
            )
        }
    }
}

// ---------------------------------------------------------------- 数据层

class AdminPlansViewModel : ViewModel() {

    private companion object {
        const val NO_PERMISSION = "需要平台管理员权限"
    }

    private val _plans = MutableStateFlow<List<Plan>>(emptyList())
    val plans: StateFlow<List<Plan>> = _plans.asStateFlow()

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
        if (!Prefs.isPlatformAdmin) {
            _plans.value = emptyList()
            _error.value = NO_PERMISSION
            return
        }
        when (val r = apiCall { plans() }) {
            is ApiResult.Success -> {
                _plans.value = r.data
                _error.value = null
            }

            is ApiResult.Failure -> {
                _plans.value = emptyList()
                _error.value = if (r.httpStatus == 403) NO_PERMISSION else r.message
            }
        }
    }

    fun create(draft: PlanDraft) {
        viewModelScope.launch {
            _saving.value = true
            _actionError.value = null
            val body = CreatePlanRequest(
                code = draft.code.trim().lowercase(),
                name = draft.name.trim(),
                is_default = draft.isDefault,
                max_accounts = parseLimit(draft.maxAccounts),
                max_groups = parseLimit(draft.maxGroups),
                daily_mail_fetch = parseLimit(draft.dailyFetch),
            )
            when (val r = apiCall { createPlan(body) }) {
                is ApiResult.Success -> {
                    _notice.value = "套餐「${r.data.name}」已创建"
                    load()
                }

                is ApiResult.Failure -> _actionError.value = r.message
            }
            _saving.value = false
        }
    }

    fun update(planId: String, draft: PlanDraft) {
        viewModelScope.launch {
            _saving.value = true
            _actionError.value = null
            // code 不可修改，UpdatePlanRequest 里也刻意不带该字段
            val body = UpdatePlanRequest(
                name = draft.name.trim(),
                is_default = draft.isDefault,
                max_accounts = parseLimit(draft.maxAccounts),
                max_groups = parseLimit(draft.maxGroups),
                daily_mail_fetch = parseLimit(draft.dailyFetch),
            )
            when (val r = apiCall { updatePlan(planId, body) }) {
                is ApiResult.Success -> {
                    _notice.value = "套餐「${r.data.name}」已保存"
                    load()
                }

                is ApiResult.Failure -> _actionError.value = r.message
            }
            _saving.value = false
        }
    }

    fun remove(plan: Plan) {
        viewModelScope.launch {
            _saving.value = true
            _actionError.value = null
            when (val r = apiCallUnit { deletePlan(plan.id) }) {
                is ApiResult.Success -> {
                    _notice.value = "套餐「${plan.name}」已删除"
                    load()
                }

                is ApiResult.Failure -> _actionError.value = r.message
            }
            _saving.value = false
        }
    }

    // ---- 一次性状态 ----

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
fun AdminPlansScreen(onBack: () -> Unit) {
    val vm: AdminPlansViewModel = viewModel()

    val plans by vm.plans.collectAsState()
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

    val isAdmin = remember { Prefs.isPlatformAdmin }

    var editing by remember { mutableStateOf<Plan?>(null) }
    var creating by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<Plan?>(null) }
    var draft by remember { mutableStateOf(PlanDraft()) }
    var formError by remember { mutableStateOf<String?>(null) }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = { AppTopBar(title = "套餐管理", onBack = onBack) },
        floatingActionButton = {
            if (isAdmin) {
                FloatingActionButton(onClick = {
                    draft = PlanDraft.of(null)
                    formError = null
                    creating = true
                }) {
                    Icon(Icons.Outlined.Add, contentDescription = "新建套餐")
                }
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when {
                !isAdmin -> EmptyBox(
                    text = "需要平台管理员权限\n当前账号无权查看套餐管理",
                    icon = Icons.Outlined.Lock,
                )

                loading -> LoadingBox(text = "正在加载套餐…")

                error != null -> ErrorBox(message = error ?: "加载失败", onRetry = vm::refresh)

                plans.isEmpty() -> EmptyBox(text = "还没有任何套餐")

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
                        items(plans, key = { it.id }) { plan ->
                            PlanCard(
                                plan = plan,
                                onEdit = {
                                    draft = PlanDraft.of(plan)
                                    formError = null
                                    editing = plan
                                },
                                onDelete = { deleting = plan },
                            )
                        }
                        item {
                            Text(
                                "上限为「不限」时表示该维度不做限制。",
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

    if (creating || editing != null) {
        val isEdit = editing != null
        PlanEditorSheet(
            draft = draft,
            isEdit = isEdit,
            saving = saving,
            error = formError,
            onDraftChange = { draft = it },
            onDismiss = {
                creating = false
                editing = null
                formError = null
            },
            onSave = {
                val trimmedCode = draft.code.trim()
                val trimmedName = draft.name.trim()
                when {
                    !isEdit && trimmedCode.isBlank() -> formError = "请填写套餐代码"
                    !isEdit && !CODE_PATTERN.matches(trimmedCode.lowercase()) ->
                        formError = "套餐代码只能包含小写字母、数字、下划线和连字符"
                    trimmedName.isBlank() -> formError = "请填写套餐名称"
                    else -> {
                        formError = null
                        val target = editing
                        if (target != null) {
                            vm.update(target.id, draft)
                        } else {
                            vm.create(draft)
                        }
                        creating = false
                        editing = null
                    }
                }
            },
        )
    }

    deleting?.let { plan ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("删除套餐") },
            text = {
                Text(
                    "确定删除套餐「${plan.name}」吗？\n\n" +
                        "默认套餐不能删除；若仍有工作空间在使用该套餐，服务端也会拒绝该操作。"
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        deleting = null
                        vm.remove(plan)
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

// ---------------------------------------------------------------- 列表

@Composable
private fun PlanCard(plan: Plan, onEdit: () -> Unit, onDelete: () -> Unit) {
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    plan.name,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f, fill = false),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.width(8.dp))
                if (plan.is_default) {
                    StatusChip(
                        text = "默认",
                        container = MaterialTheme.colorScheme.primaryContainer,
                        content = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
            Spacer(Modifier.height(2.dp))
            Text(
                plan.code,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth()) {
                LimitItem("账号上限", limitText(plan.max_accounts), Modifier.weight(1f))
                LimitItem("分组上限", limitText(plan.max_groups), Modifier.weight(1f))
                LimitItem("每日取件", limitText(plan.daily_mail_fetch), Modifier.weight(1f))
            }
            HorizontalDivider(Modifier.padding(vertical = 6.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onEdit) {
                    Icon(Icons.Outlined.Edit, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("编辑")
                }
                TextButton(
                    onClick = onDelete,
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                ) {
                    Icon(Icons.Outlined.Delete, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("删除")
                }
            }
        }
    }
}

@Composable
private fun LimitItem(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            value,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

// ---------------------------------------------------------------- 编辑器

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlanEditorSheet(
    draft: PlanDraft,
    isEdit: Boolean,
    saving: Boolean,
    error: String?,
    onDraftChange: (PlanDraft) -> Unit,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState()) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                if (isEdit) "编辑套餐" else "新建套餐",
                style = MaterialTheme.typography.titleMedium,
            )

            OutlinedTextField(
                value = draft.code,
                onValueChange = { onDraftChange(draft.copy(code = it)) },
                label = { Text("套餐代码") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !isEdit,
                supportingText = {
                    Text(if (isEdit) "套餐代码创建后不可修改" else "仅小写字母、数字、下划线与连字符")
                },
            )
            OutlinedTextField(
                value = draft.name,
                onValueChange = { onDraftChange(draft.copy(name = it)) },
                label = { Text("套餐名称") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )

            ListItem(
                headlineContent = { Text("设为默认套餐") },
                supportingContent = { Text("新工作空间默认使用；至少保留一个默认套餐") },
                trailingContent = {
                    Switch(
                        checked = draft.isDefault,
                        onCheckedChange = { onDraftChange(draft.copy(isDefault = it)) },
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            )

            LimitField(
                label = "账号上限",
                value = draft.maxAccounts,
                onValueChange = { onDraftChange(draft.copy(maxAccounts = it)) },
            )
            LimitField(
                label = "分组上限",
                value = draft.maxGroups,
                onValueChange = { onDraftChange(draft.copy(maxGroups = it)) },
            )
            LimitField(
                label = "每日取件上限",
                value = draft.dailyFetch,
                onValueChange = { onDraftChange(draft.copy(dailyFetch = it)) },
            )

            if (error != null) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        error,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    )
                }
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss, enabled = !saving) { Text("取消") }
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = onSave, enabled = !saving) {
                    Text(if (saving) "保存中…" else "保存")
                }
            }
        }
    }
}

@Composable
private fun LimitField(label: String, value: String, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        supportingText = { Text("填 -1 或留空表示不限") },
    )
}
