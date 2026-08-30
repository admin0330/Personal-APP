package com.masteralanlab.emailbox.ui.screens.tokens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.masteralanlab.emailbox.data.Prefs
import com.masteralanlab.emailbox.data.remote.Job
import com.masteralanlab.emailbox.data.remote.MailAccount
import com.masteralanlab.emailbox.data.remote.MailGroup
import com.masteralanlab.emailbox.data.remote.RefreshLog
import com.masteralanlab.emailbox.data.remote.RefreshStats
import com.masteralanlab.emailbox.data.remote.SubmitRefreshRequest
import com.masteralanlab.emailbox.data.remote.apiCall
import com.masteralanlab.emailbox.data.remote.ApiResult
import com.masteralanlab.emailbox.ui.components.AppTopBar
import com.masteralanlab.emailbox.ui.components.MainTopBar
import com.masteralanlab.emailbox.ui.components.EmptyBox
import com.masteralanlab.emailbox.ui.components.ErrorBox
import com.masteralanlab.emailbox.ui.components.Labels
import com.masteralanlab.emailbox.ui.components.LoadingBox
import com.masteralanlab.emailbox.ui.components.RadioOptionList
import com.masteralanlab.emailbox.ui.components.SectionTitle
import com.masteralanlab.emailbox.ui.components.StatusChip
import com.masteralanlab.emailbox.ui.components.statusContainer
import com.masteralanlab.emailbox.ui.components.statusContent
import com.masteralanlab.emailbox.util.formatShortTime
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// ---------- 展示用文案助手（JobDetailScreen / RefreshLogsScreen 也会复用） ----------

internal fun jobTypeText(type: String?): String = when (type) {
    "token_refresh" -> "令牌刷新"
    else -> type?.takeIf { it.isNotBlank() } ?: "未知任务"
}

internal fun jobTriggerText(trigger: String?): String = when (trigger) {
    "manual" -> "手动"
    "scheduled" -> "定时"
    "api" -> "API 调用"
    "system" -> "系统"
    else -> trigger?.takeIf { it.isNotBlank() } ?: "手动"
}

internal fun refreshTypeText(type: String?): String = when (type) {
    "manual" -> "手动"
    "job" -> "任务"
    "scheduled" -> "定时"
    else -> type?.takeIf { it.isNotBlank() } ?: "手动"
}

/** pending / running / stopping 都算未结束，需要展示进度并允许停止。 */
internal fun isJobRunning(status: String?): Boolean =
    status == "pending" || status == "running" || status == "stopping"

internal fun jobProgressOf(success: Int, failed: Int, total: Int): Float =
    if (total <= 0) 0f else ((success + failed).toFloat() / total.toFloat()).coerceIn(0f, 1f)

// ---------- UI 状态 ----------

private const val JOBS_PAGE_SIZE = 20
private const val LOGS_PREVIEW_SIZE = 10
private const val ACCOUNT_PAGE_SIZE = 200

data class TokensUiState(
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val error: String? = null,
    val stats: RefreshStats? = null,
    val jobs: List<Job> = emptyList(),
    val jobPage: Int = 1,
    val jobPages: Int = 1,
    val loadingMoreJobs: Boolean = false,
    val logs: List<RefreshLog> = emptyList(),
    val groups: List<MailGroup> = emptyList(),
    val accounts: List<MailAccount> = emptyList(),
    val loadingAccounts: Boolean = false,
    val scope: String = "all",
    val accountQuery: String = "",
    val selectedAccountIds: Set<String> = emptySet(),
    val selectedGroupIds: Set<String> = emptySet(),
    val submitting: Boolean = false,
    val submitError: String? = null,
)

class TokensViewModel(app: android.app.Application) : androidx.lifecycle.AndroidViewModel(app) {

    private val _state = MutableStateFlow(TokensUiState())
    val state: StateFlow<TokensUiState> = _state.asStateFlow()

    /** 任务创建成功后用它把 jobId 交给导航层，避免重组时重复触发。 */
    private val _openJob = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val openJob: SharedFlow<String> = _openJob.asSharedFlow()

    init {
        loadCache()
        refresh(silent = true)
    }

    /** 进页秒开：直显磁盘缓存的统计/任务/日志，随后 refresh(silent) 静默更新。 */
    private fun loadCache() {
        val tenant = Prefs.tenantId ?: return
        val snapshot = com.masteralanlab.emailbox.data.TokensCache.load(getApplication(), tenant) ?: return
        _state.value = _state.value.copy(
            loading = false,
            stats = snapshot.stats,
            jobs = snapshot.jobs,
            jobPages = snapshot.jobPages,
            logs = snapshot.logs,
        )
    }

    fun refresh(silent: Boolean = false) {
        val tenant = Prefs.tenantId
        if (tenant.isNullOrBlank()) {
            _state.update { TokensUiState(loading = false, error = "未选择工作空间") }
            return
        }
        _state.update { it.copy(refreshing = !silent, error = null) }
        loadStats(tenant)
        loadJobs(tenant, 1)
        loadLogs(tenant)
        if (_state.value.scope == "group") ensureGroups()
    }

    fun retry() {
        _state.update { it.copy(loading = true) }
        refresh()
    }

    // ---- 统计 / 任务 / 日志 ----

    private fun loadStats(tenant: String) {
        viewModelScope.launch {
            when (val r = apiCall { refreshStats(tenant) }) {
                is ApiResult.Success -> {
                    _state.update { it.copy(stats = r.data) }
                    // 快照落盘，下次进页秒开
                    val s = _state.value
                    com.masteralanlab.emailbox.data.TokensCache.save(getApplication(), tenant, s.stats, s.jobs, s.jobPages, s.logs)
                }
                is ApiResult.Failure -> _state.update { it.copy(error = r.message) }
            }
        }
    }

    private fun loadJobs(tenant: String, page: Int) {
        viewModelScope.launch {
            if (page > 1) _state.update { it.copy(loadingMoreJobs = true) }
            when (val r = apiCall { jobs(tenant, page = page, limit = JOBS_PAGE_SIZE) }) {
                is ApiResult.Success -> {
                    val paged = r.data
                    _state.update {
                        it.copy(
                            loading = false,
                            refreshing = false,
                            jobs = if (page <= 1) paged.items else it.jobs + paged.items,
                            jobPage = page,
                            jobPages = (paged.pagination?.pages ?: 1).coerceAtLeast(1),
                            loadingMoreJobs = false,
                        )
                    }
                }

                is ApiResult.Failure -> _state.update {
                    it.copy(
                        loading = false,
                        refreshing = false,
                        loadingMoreJobs = false,
                        error = r.message,
                    )
                }
            }
        }
    }

    private fun loadLogs(tenant: String) {
        viewModelScope.launch {
            when (val r = apiCall { refreshLogs(tenant, page = 1, limit = LOGS_PREVIEW_SIZE) }) {
                is ApiResult.Success -> _state.update { it.copy(logs = r.data.items) }
                is ApiResult.Failure -> _state.update { it.copy(error = r.message) }
            }
        }
    }

    fun loadMoreJobs() {
        val s = _state.value
        val tenant = Prefs.tenantId ?: return
        if (s.loadingMoreJobs || s.jobPage >= s.jobPages) return
        loadJobs(tenant, s.jobPage + 1)
    }

    // ---- 刷新范围 ----

    fun setScope(scope: String) {
        _state.update { it.copy(scope = scope, submitError = null) }
        when (scope) {
            "group" -> ensureGroups()
            "selected" -> ensureAccounts()
        }
    }

    fun setAccountQuery(q: String) = _state.update { it.copy(accountQuery = q) }

    private fun ensureGroups() {
        val tenant = Prefs.tenantId ?: return
        if (_state.value.groups.isNotEmpty()) return
        viewModelScope.launch {
            when (val r = apiCall { groups(tenant) }) {
                is ApiResult.Success -> _state.update { it.copy(groups = r.data) }
                is ApiResult.Failure -> _state.update { it.copy(submitError = r.message) }
            }
        }
    }

    private fun ensureAccounts() {
        if (_state.value.accounts.isNotEmpty() || _state.value.loadingAccounts) return
        searchAccounts()
    }

    fun searchAccounts() {
        val tenant = Prefs.tenantId ?: return
        val q = _state.value.accountQuery.trim().ifBlank { null }
        _state.update { it.copy(loadingAccounts = true) }
        viewModelScope.launch {
            when (val r = apiCall { accounts(tenant, q = q, limit = ACCOUNT_PAGE_SIZE) }) {
                is ApiResult.Success -> _state.update {
                    it.copy(accounts = r.data.items, loadingAccounts = false)
                }

                is ApiResult.Failure -> _state.update {
                    it.copy(accounts = emptyList(), loadingAccounts = false, submitError = r.message)
                }
            }
        }
    }

    fun toggleGroup(id: String) = _state.update {
        val next = it.selectedGroupIds.toMutableSet()
        if (!next.add(id)) next.remove(id)
        it.copy(selectedGroupIds = next, submitError = null)
    }

    fun toggleAccount(id: String) = _state.update {
        val next = it.selectedAccountIds.toMutableSet()
        if (!next.add(id)) next.remove(id)
        it.copy(selectedAccountIds = next, submitError = null)
    }

    fun selectAllAccounts() = _state.update {
        it.copy(selectedAccountIds = it.accounts.map { a -> a.id }.toSet(), submitError = null)
    }

    fun selectAllGroups() = _state.update {
        it.copy(selectedGroupIds = it.groups.map { g -> g.id }.toSet(), submitError = null)
    }

    fun clearSelection() = _state.update {
        it.copy(selectedAccountIds = emptySet(), selectedGroupIds = emptySet(), submitError = null)
    }

    fun submit() {
        val tenant = Prefs.tenantId
        if (tenant.isNullOrBlank()) {
            _state.update { it.copy(submitError = "未选择工作空间") }
            return
        }
        val s = _state.value
        val request = when (s.scope) {
            "selected" -> {
                if (s.selectedAccountIds.isEmpty()) {
                    _state.update { it.copy(submitError = "请先选择要刷新的账号") }
                    return
                }
                SubmitRefreshRequest("selected", s.selectedAccountIds.toList(), emptyList())
            }

            "group" -> {
                if (s.selectedGroupIds.isEmpty()) {
                    _state.update { it.copy(submitError = "请先选择要刷新的分组") }
                    return
                }
                SubmitRefreshRequest("group", emptyList(), s.selectedGroupIds.toList())
            }

            else -> SubmitRefreshRequest(s.scope)
        }

        _state.update { it.copy(submitting = true, submitError = null) }
        viewModelScope.launch {
            when (val r = apiCall { submitRefreshJob(tenant, request) }) {
                is ApiResult.Success -> {
                    _state.update { it.copy(submitting = false) }
                    _openJob.tryEmit(r.data.id)
                    refresh()
                }

                is ApiResult.Failure -> _state.update {
                    it.copy(submitting = false, submitError = r.message)
                }
            }
        }
    }
}

// ---------- 页面 ----------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TokensScreen(
    onOpenJob: (String) -> Unit,
    onOpenLogs: () -> Unit,
) {
    val vm: TokensViewModel = viewModel()
    val state by vm.state.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        vm.openJob.collect { onOpenJob(it) }
    }

    Scaffold(
        topBar = {
            MainTopBar(
                title = "令牌",
                actions = {
                    IconButton(onClick = vm::refresh) {
                        Icon(Icons.Outlined.Refresh, contentDescription = "刷新")
                    }
                },
            )
        },
    ) { padding ->
        when {
            state.loading -> LoadingBox(Modifier.padding(padding))
            state.error == "未选择工作空间" ->
                EmptyBox("未选择工作空间", modifier = Modifier.padding(padding))
            state.stats == null && state.error != null ->
                ErrorBox(state.error ?: "", onRetry = vm::retry, modifier = Modifier.padding(padding))
            else -> PullToRefreshBox(
                isRefreshing = state.refreshing,
                onRefresh = { vm.refresh(silent = false) },
                modifier = Modifier.padding(padding).fillMaxSize(),
            ) {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    item(key = "stats") { StatsCard(state.stats, onOpenJob) }

                    item(key = "submit") { SubmitCard(state, vm) }

                    if (state.jobs.isNotEmpty()) item(key = "jobs_title") { SectionTitle("最近任务") }
                    if (state.jobs.isEmpty()) {
                        item(key = "jobs_empty") { HintText("还没有刷新任务") }
                    } else {
                        items(state.jobs, key = { "job_${it.id}" }) { job ->
                            JobRow(job) { onOpenJob(job.id) }
                        }
                        if (state.jobPage < state.jobPages) {
                            item(key = "jobs_more") {
                                LoadMoreRow(
                                    loading = state.loadingMoreJobs,
                                    text = "加载更多任务",
                                    onClick = vm::loadMoreJobs,
                                )
                            }
                        }
                    }

                    if (state.logs.isNotEmpty()) item(key = "logs_title") { SectionTitle("最近刷新日志") }
                    if (state.logs.isNotEmpty()) {
                        items(state.logs, key = { "log_${it.id}" }) { log -> LogRow(log) }
                        item(key = "logs_all") {
                            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                TextButton(onClick = onOpenLogs) { Text("查看全部") }
                            }
                        }
                    }

                    item(key = "bottom") {
                        Box(
                            Modifier.fillMaxWidth().padding(vertical = 20.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            TextButton(onClick = vm::refresh) { Text("刷新数据") }
                        }
                    }
                }
            }
        }
    }
}

// ---------- 统计 ----------

@Composable
private fun StatsCard(stats: RefreshStats?, onOpenJob: (String) -> Unit) {
    if (stats == null) return
    Column(Modifier.fillMaxWidth()) {
        StatsMainCard(stats)
        stats.last_job?.let { job -> LastJobCard(job) { onOpenJob(job.id) } }
    }
}

@Composable
private fun StatsMainCard(stats: RefreshStats) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        ),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth()) {
                StatCell("总数", stats.total, MaterialTheme.colorScheme.onPrimary)
                StatCell("成功", stats.success, MaterialTheme.colorScheme.onPrimary)
                StatCell("失败", stats.failed, MaterialTheme.colorScheme.onPrimary)
                StatCell("未刷新", stats.never, MaterialTheme.colorScheme.onPrimary)
            }

            val kinds = stats.by_error_kind.entries.sortedByDescending { it.value }
            if (kinds.isNotEmpty()) {
                HorizontalDivider(Modifier.padding(vertical = 12.dp))
                Text(
                    "失败原因",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
                Spacer(Modifier.height(6.dp))
                kinds.forEach { (kind, count) ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 3.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            Labels.errorKind(kind).ifBlank { kind },
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            "$count",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LastJobCard(job: Job, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        ListItem(
            headlineContent = {
                Text("最近一次：${jobTypeText(job.type)}", style = MaterialTheme.typography.bodyLarge)
            },
            supportingContent = {
                Text(
                    "成功 ${job.success_count} · 失败 ${job.failed_count} · 共 ${job.total_count}" +
                        "｜${jobTriggerText(job.trigger)}｜${formatShortTime(job.created_at)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            trailingContent = {
                StatusChip(
                    text = Labels.jobStatus(job.status),
                    container = statusContainer(job.status),
                    content = statusContent(job.status),
                )
            },
        )
    }
}

@Composable
private fun RowScope.StatCell(label: String, value: Int, color: Color) {
    Column(
        Modifier.weight(1f),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("$value", style = MaterialTheme.typography.titleLarge, color = color)
        Spacer(Modifier.height(2.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = color.copy(alpha = .78f),
        )
    }
}

// ---------- 提交任务 ----------

@Composable
private fun SubmitCard(state: TokensUiState, vm: TokensViewModel) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.padding(vertical = 8.dp)) {
            SectionTitle("提交刷新任务")

            RadioOptionList(
                options = listOf(
                    "all" to "全部账号",
                    "failed" to "仅刷新最近失败的",
                    "group" to "按分组",
                    "selected" to "按账号",
                ),
                selected = state.scope,
                onSelect = vm::setScope,
            )

            when (state.scope) {
                "group" -> GroupSelector(state, vm)
                "selected" -> AccountSelector(state, vm)
            }

            if (state.submitError != null) {
                Text(
                    state.submitError,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }

            Button(
                onClick = vm::submit,
                enabled = !state.submitting,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                if (state.submitting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                    Spacer(Modifier.width(10.dp))
                }
                Text(if (state.submitting) "提交中…" else "提交刷新任务")
            }
        }
    }
}

@Composable
private fun GroupSelector(state: TokensUiState, vm: TokensViewModel) {
    if (state.groups.isEmpty()) {
        HintText("暂无分组，请先在账号页创建分组")
        return
    }
    Column(Modifier.padding(horizontal = 8.dp)) {
        state.groups.forEach { g ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { vm.toggleGroup(g.id) }
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(
                    checked = state.selectedGroupIds.contains(g.id),
                    onCheckedChange = { vm.toggleGroup(g.id) },
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    g.name,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "${g.account_count} 个账号",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        SelectionActions(
            selected = state.selectedGroupIds.size,
            onSelectAll = vm::selectAllGroups,
            onClear = vm::clearSelection,
        )
    }
}

@Composable
private fun AccountSelector(state: TokensUiState, vm: TokensViewModel) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        OutlinedTextField(
            value = state.accountQuery,
            onValueChange = vm::setAccountQuery,
            label = { Text("搜索邮箱") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            trailingIcon = {
                IconButton(onClick = vm::searchAccounts) {
                    Icon(Icons.Outlined.Search, contentDescription = "搜索")
                }
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { vm.searchAccounts() }),
        )

        if (state.loadingAccounts) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp))
        }

        if (!state.loadingAccounts && state.accounts.isEmpty()) {
            Text(
                "没有匹配的账号",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 8.dp),
            )
        }

        state.accounts.forEach { a ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { vm.toggleAccount(a.id) }
                    .padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(
                    checked = state.selectedAccountIds.contains(a.id),
                    onCheckedChange = { vm.toggleAccount(a.id) },
                )
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        a.email,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        "最近刷新：${Labels.refreshStatus(a.last_refresh_status)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        if (state.accounts.isNotEmpty()) {
            SelectionActions(
                selected = state.selectedAccountIds.size,
                onSelectAll = vm::selectAllAccounts,
                onClear = vm::clearSelection,
            )
        }
    }
}

@Composable
private fun SelectionActions(selected: Int, onSelectAll: () -> Unit, onClear: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = onSelectAll) { Text("全选") }
        TextButton(onClick = onClear) { Text("清空") }
        Spacer(Modifier.weight(1f))
        Text(
            "已选 $selected",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ---------- 列表项 ----------

@Composable
private fun JobRow(job: Job, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        ListItem(
            headlineContent = {
                Text(jobTypeText(job.type), style = MaterialTheme.typography.bodyLarge)
            },
            supportingContent = {
                Column {
                    Text(
                        "成功 ${job.success_count} · 失败 ${job.failed_count} · 共 ${job.total_count}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        "${jobTriggerText(job.trigger)} · ${formatShortTime(job.created_at)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            trailingContent = {
                StatusChip(
                    text = Labels.jobStatus(job.status),
                    container = statusContainer(job.status),
                    content = statusContent(job.status),
                )
            },
        )
        if (isJobRunning(job.status)) {
            LinearProgressIndicator(
                progress = jobProgressOf(job.success_count, job.failed_count, job.total_count),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 8.dp),
            )
        }
        HorizontalDivider()
    }
}

@Composable
private fun LogRow(log: RefreshLog) {
    val kind = Labels.errorKind(log.error_kind)
    Column {
        ListItem(
            headlineContent = {
                Text(
                    log.account_email.ifBlank { log.account_id },
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            supportingContent = {
                Text(
                    buildString {
                        append(refreshTypeText(log.refresh_type))
                        if (kind.isNotBlank()) append(" · ").append(kind)
                        append(" · ").append(formatShortTime(log.created_at))
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            trailingContent = {
                StatusChip(
                    text = Labels.refreshStatus(log.status),
                    container = statusContainer(log.status),
                    content = statusContent(log.status),
                )
            },
        )
        HorizontalDivider()
    }
}

@Composable
private fun HintText(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
    )
}

@Composable
internal fun LoadMoreRow(loading: Boolean, text: String, onClick: () -> Unit) {
    Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
        if (loading) {
            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
        } else {
            TextButton(onClick = onClick) { Text(text) }
        }
    }
}
