package com.masteralanlab.emailbox.ui.screens.me

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AdminPanelSettings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
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
import com.masteralanlab.emailbox.data.remote.Plan
import com.masteralanlab.emailbox.data.remote.QuotaUsage
import com.masteralanlab.emailbox.data.remote.UpdateQuotaRequest
import com.masteralanlab.emailbox.data.remote.apiCall
import com.masteralanlab.emailbox.ui.components.AppTopBar
import com.masteralanlab.emailbox.ui.components.DropdownField
import com.masteralanlab.emailbox.ui.components.ErrorBox
import com.masteralanlab.emailbox.ui.components.LoadingBox
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** 后端约定：-1 表示不限。 */
private const val UNLIMITED = -1

private fun limitText(value: Int): String = if (value < 0) "不限" else value.toString()

/** 留空 = 沿用套餐值（null），填 -1 = 不限，填其他数字 = 覆盖。 */
private fun parseLimit(raw: String): Int? {
    val s = raw.trim()
    if (s.isEmpty()) return null
    return s.toIntOrNull() ?: UNLIMITED
}

// ---------------------------------------------------------------- 数据层

class QuotaViewModel : ViewModel() {

    private val _usage = MutableStateFlow<QuotaUsage?>(null)
    val usage: StateFlow<QuotaUsage?> = _usage.asStateFlow()

    private val _planList = MutableStateFlow<List<Plan>>(emptyList())
    val planList: StateFlow<List<Plan>> = _planList.asStateFlow()

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
            _usage.value = null
            _error.value = "尚未选择工作空间"
            return
        }
        when (val r = apiCall { quota(tenantId) }) {
            is ApiResult.Success -> {
                _usage.value = r.data
                _error.value = null
            }

            is ApiResult.Failure -> {
                _usage.value = null
                _error.value = r.message
            }
        }
        if (Prefs.isPlatformAdmin) loadPlans()
    }

    private suspend fun loadPlans() {
        when (val r = apiCall { plans() }) {
            is ApiResult.Success -> _planList.value = r.data
            is ApiResult.Failure -> _planList.value = emptyList()
        }
    }

    fun updateQuota(
        planId: String?,
        note: String,
        maxAccounts: String,
        maxGroups: String,
        dailyFetch: String,
    ) {
        val tenantId = Prefs.tenantId ?: return
        val trimmedNote = note.trim()
        if (trimmedNote.isBlank()) {
            _actionError.value = "调整配额必须填写原因"
            return
        }
        viewModelScope.launch {
            _saving.value = true
            _actionError.value = null
            val body = UpdateQuotaRequest(
                plan_id = planId,
                note = trimmedNote,
                max_accounts = parseLimit(maxAccounts),
                max_groups = parseLimit(maxGroups),
                daily_mail_fetch = parseLimit(dailyFetch),
            )
            when (val r = apiCall { updateAdminQuota(tenantId, body) }) {
                is ApiResult.Success -> {
                    _usage.value = r.data
                    _notice.value = "配额已调整"
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
fun QuotaScreen(onBack: () -> Unit) {
    val vm: QuotaViewModel = viewModel()

    val usage by vm.usage.collectAsState()
    val planList by vm.planList.collectAsState()
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

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = { AppTopBar(title = "用量配额", onBack = onBack) },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when {
                loading -> LoadingBox(text = "正在读取用量…")

                error != null -> ErrorBox(message = error ?: "加载失败", onRetry = vm::refresh)

                else -> PullToRefreshBox(
                    isRefreshing = refreshing,
                    onRefresh = vm::refresh,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        usage?.let { u ->
                            PlanCard(usage = u)
                            UsageCard(usage = u)
                        }
                        if (Prefs.isPlatformAdmin) {
                            AdjustQuotaCard(
                                plans = planList,
                                saving = saving,
                                onSubmit = { planId, note, a, g, d ->
                                    vm.updateQuota(planId, note, a, g, d)
                                },
                            )
                        }
                        Spacer(Modifier.height(12.dp))
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- 展示卡片

@Composable
private fun PlanCard(usage: QuotaUsage) {
    val limits = usage.limits
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.width(4.dp))
                Text(
                    "当前套餐",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    "统计日期 ${usage.day.ifBlank { "—" }}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    limits.plan_name.ifBlank { "未分配套餐" },
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f, fill = false),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (limits.plan_code.isNotBlank()) {
                    Spacer(Modifier.width(8.dp))
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    ) {
                        Text(
                            limits.plan_code,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun UsageCard(usage: QuotaUsage) {
    val limits = usage.limits
    val used = usage.usage
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text(
                "用量",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(10.dp))
            UsageBar(
                label = "邮箱账号",
                used = used.accounts,
                limit = limits.max_accounts,
            )
            Spacer(Modifier.height(14.dp))
            UsageBar(
                label = "分组",
                used = used.groups,
                limit = limits.max_groups,
            )
            Spacer(Modifier.height(14.dp))
            UsageBar(
                label = "每日取件",
                used = used.mail_fetch,
                limit = limits.daily_mail_fetch,
            )
            Spacer(Modifier.height(14.dp))
            UsageBar(
                label = "令牌刷新（今日）",
                used = used.token_refresh,
                limit = UNLIMITED,
            )
        }
    }
}

@Composable
private fun UsageBar(label: String, used: Int, limit: Int) {
    val unlimited = limit < 0
    val ratio = if (unlimited) 0.06f else {
        if (limit <= 0) 0f else (used.toFloat() / limit.toFloat()).coerceIn(0f, 1f)
    }
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                label,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f, fill = false),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.width(10.dp))
            Text(
                "$used / ${limitText(limit)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(6.dp))
        LinearProgressIndicator(
            progress = { ratio },
            modifier = Modifier.fillMaxWidth(),
        )
        if (unlimited) {
            Spacer(Modifier.height(4.dp))
            Text(
                "该维度不限制用量",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ---------------------------------------------------------------- 调整配额

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AdjustQuotaCard(
    plans: List<Plan>,
    saving: Boolean,
    onSubmit: (String?, String, String, String, String) -> Unit,
) {
    var planId by remember { mutableStateOf<String?>(null) }
    var note by remember { mutableStateOf("") }
    var maxAccounts by remember { mutableStateOf("") }
    var maxGroups by remember { mutableStateOf("") }
    var dailyFetch by remember { mutableStateOf("") }
    var formError by remember { mutableStateOf<String?>(null) }
    var confirm by remember { mutableStateOf(false) }

    val planOptions: List<Pair<String?, String>> = buildList {
        add(null to "不更换套餐")
        plans.forEach { add(it.id to "${it.name}（${it.code}）") }
    }

    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Outlined.AdminPanelSettings,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.height(20.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "调整配额",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Spacer(Modifier.height(12.dp))

            DropdownField(
                label = "套餐",
                options = planOptions,
                selected = planId,
                onSelect = { planId = it },
                modifier = Modifier.fillMaxWidth(),
                supporting = "不更换时留空即可",
            )
            Spacer(Modifier.height(10.dp))

            LimitOverrideField(
                label = "账号上限",
                value = maxAccounts,
                onValueChange = { maxAccounts = it; formError = null },
            )
            Spacer(Modifier.height(10.dp))
            LimitOverrideField(
                label = "分组上限",
                value = maxGroups,
                onValueChange = { maxGroups = it; formError = null },
            )
            Spacer(Modifier.height(10.dp))
            LimitOverrideField(
                label = "每日取件上限",
                value = dailyFetch,
                onValueChange = { dailyFetch = it; formError = null },
            )
            Spacer(Modifier.height(10.dp))

            OutlinedTextField(
                value = note,
                onValueChange = { note = it; formError = null },
                label = { Text("调整原因（必填）") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = false,
                minLines = 2,
                maxLines = 4,
                supportingText = { Text("会写入审计日志，不能为空") },
            )

            if (formError != null) {
                Spacer(Modifier.height(10.dp))
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        formError ?: "",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    )
                }
            }

            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(
                    enabled = !saving,
                    onClick = {
                        if (note.trim().isBlank()) {
                            formError = "调整配额必须填写原因"
                        } else {
                            formError = null
                            confirm = true
                        }
                    },
                ) { Text(if (saving) "保存中…" else "保存调整") }
            }
        }
    }

    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("调整配额") },
            text = { Text("确定按当前设置调整本工作空间的配额吗？该操作会记录到审计日志。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirm = false
                        val payload = listOf(note, maxAccounts, maxGroups, dailyFetch)
                        note = ""
                        maxAccounts = ""
                        maxGroups = ""
                        dailyFetch = ""
                        onSubmit(planId, payload[0], payload[1], payload[2], payload[3])
                    },
                ) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { confirm = false }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun LimitOverrideField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        supportingText = { Text("留空沿用套餐值，-1 表示不限") },
    )
}
