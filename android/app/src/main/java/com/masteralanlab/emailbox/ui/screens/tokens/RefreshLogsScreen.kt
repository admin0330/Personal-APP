package com.masteralanlab.emailbox.ui.screens.tokens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.masteralanlab.emailbox.data.Prefs
import com.masteralanlab.emailbox.data.remote.ApiResult
import com.masteralanlab.emailbox.data.remote.MailAccount
import com.masteralanlab.emailbox.data.remote.RefreshLog
import com.masteralanlab.emailbox.data.remote.apiCall
import com.masteralanlab.emailbox.ui.components.AppTopBar
import com.masteralanlab.emailbox.ui.components.DropdownField
import com.masteralanlab.emailbox.ui.components.EmptyBox
import com.masteralanlab.emailbox.ui.components.ErrorBox
import com.masteralanlab.emailbox.ui.components.Labels
import com.masteralanlab.emailbox.ui.components.LoadingBox
import com.masteralanlab.emailbox.ui.components.StatusChip
import com.masteralanlab.emailbox.ui.components.statusContainer
import com.masteralanlab.emailbox.ui.components.statusContent
import com.masteralanlab.emailbox.util.formatFullTime
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val LOGS_PAGE_SIZE = 30
private const val ACCOUNT_SEARCH_LIMIT = 50

data class RefreshLogsUiState(
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val error: String? = null,
    val logs: List<RefreshLog> = emptyList(),
    val page: Int = 1,
    val pages: Int = 1,
    val loadingMore: Boolean = false,
    val status: String? = null,
    /** 接口只接受 account_id，邮箱关键词只用于本地搜索出候选账号。 */
    val accountId: String? = null,
    val accountLabel: String? = null,
    val query: String = "",
    val candidates: List<MailAccount> = emptyList(),
    val searching: Boolean = false,
)

class RefreshLogsViewModel : ViewModel() {

    private val _state = MutableStateFlow(RefreshLogsUiState())
    val state: StateFlow<RefreshLogsUiState> = _state.asStateFlow()

    init {
        load(1)
    }

    fun refresh() {
        _state.update { it.copy(refreshing = true, error = null) }
        load(1)
    }

    fun loadMore() {
        val s = _state.value
        if (s.loadingMore || s.page >= s.pages) return
        load(s.page + 1)
    }

    private fun load(page: Int) {
        val tenant = Prefs.tenantId
        if (tenant.isNullOrBlank()) {
            _state.update { RefreshLogsUiState(loading = false, error = "未选择工作空间") }
            return
        }
        if (page > 1) _state.update { it.copy(loadingMore = true) }
        viewModelScope.launch {
            val status = _state.value.status
            val accountId = _state.value.accountId
            when (val r = apiCall {
                refreshLogs(
                    tenant,
                    status = status,
                    accountId = accountId,
                    page = page,
                    limit = LOGS_PAGE_SIZE,
                )
            }) {
                is ApiResult.Success -> _state.update {
                    it.copy(
                        loading = false,
                        refreshing = false,
                        error = null,
                        logs = if (page <= 1) r.data.items else it.logs + r.data.items,
                        page = page,
                        pages = (r.data.pagination?.pages ?: 1).coerceAtLeast(1),
                        loadingMore = false,
                    )
                }

                is ApiResult.Failure -> _state.update {
                    it.copy(
                        loading = false,
                        refreshing = false,
                        loadingMore = false,
                        error = r.message,
                    )
                }
            }
        }
    }

    fun setStatus(status: String?) {
        _state.update { it.copy(status = status, logs = emptyList(), page = 1, pages = 1) }
        load(1)
    }

    fun setQuery(q: String) = _state.update { it.copy(query = q) }

    fun searchAccounts() {
        val tenant = Prefs.tenantId ?: return
        val q = _state.value.query.trim().ifBlank { null }
        _state.update { it.copy(searching = true) }
        viewModelScope.launch {
            when (val r = apiCall { accounts(tenant, q = q, limit = ACCOUNT_SEARCH_LIMIT) }) {
                is ApiResult.Success -> _state.update {
                    it.copy(candidates = r.data.items, searching = false)
                }

                is ApiResult.Failure -> _state.update {
                    it.copy(candidates = emptyList(), searching = false, error = r.message)
                }
            }
        }
    }

    fun pickAccount(account: MailAccount) {
        _state.update {
            it.copy(
                accountId = account.id,
                accountLabel = account.email,
                candidates = emptyList(),
                logs = emptyList(),
                page = 1,
                pages = 1,
            )
        }
        load(1)
    }

    fun clearAccount() {
        _state.update {
            it.copy(accountId = null, accountLabel = null, logs = emptyList(), page = 1, pages = 1)
        }
        load(1)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RefreshLogsScreen(onBack: () -> Unit) {
    val vm: RefreshLogsViewModel = viewModel()
    val state by vm.state.collectAsStateWithLifecycle()

    Scaffold(topBar = { AppTopBar("刷新日志", onBack = onBack) }) { padding ->
        when {
            state.loading -> LoadingBox(Modifier.padding(padding))
            state.error == "未选择工作空间" ->
                EmptyBox("未选择工作空间", modifier = Modifier.padding(padding))
            state.error != null && state.logs.isEmpty() ->
                ErrorBox(
                    message = state.error ?: "",
                    onRetry = vm::refresh,
                    modifier = Modifier.padding(padding),
                )
            else -> PullToRefreshBox(
                isRefreshing = state.refreshing,
                onRefresh = vm::refresh,
                modifier = Modifier.padding(padding).fillMaxSize(),
            ) {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    item(key = "filters") { FilterPanel(state, vm) }

                    if (state.logs.isEmpty()) {
                        item(key = "empty") { LogsEmptyHint() }
                    } else {
                        items(state.logs, key = { "log_${it.id}" }) { log -> RefreshLogRow(log) }
                        if (state.page < state.pages) {
                            item(key = "more") {
                                LoadMoreRow(
                                    loading = state.loadingMore,
                                    text = "加载更多",
                                    onClick = vm::loadMore,
                                )
                            }
                        }
                    }

                    item(key = "bottom") { Spacer(Modifier.height(24.dp)) }
                }
            }
        }
    }
}

@Composable
private fun FilterPanel(state: RefreshLogsUiState, vm: RefreshLogsViewModel) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.padding(16.dp)) {
            DropdownField(
                label = "刷新结果",
                options = listOf(
                    null to "不限",
                    "success" to "成功",
                    "failed" to "失败",
                ),
                selected = state.status,
                onSelect = vm::setStatus,
            )

            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = state.query,
                onValueChange = vm::setQuery,
                label = { Text("按邮箱筛选账号") },
                placeholder = { Text("输入邮箱关键词后搜索") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                trailingIcon = {
                    IconButton(onClick = vm::searchAccounts) {
                        Icon(Icons.Outlined.Search, contentDescription = "搜索账号")
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { vm.searchAccounts() }),
            )

            if (state.searching) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp))
            }

            if (state.accountId != null) {
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AssistChip(
                        onClick = vm::clearAccount,
                        label = { Text(state.accountLabel ?: state.accountId ?: "") },
                        trailingIcon = {
                            Icon(Icons.Outlined.Close, contentDescription = "清除账号筛选")
                        },
                    )
                }
            }

            if (state.candidates.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                HorizontalDivider()
                state.candidates.forEach { a ->
                    ListItem(
                        headlineContent = {
                            Text(
                                a.email,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                        modifier = Modifier.clickable { vm.pickAccount(a) },
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun LogsEmptyHint() {
    Box(Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) {
        Text(
            "没有符合条件的刷新记录",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun RefreshLogRow(log: RefreshLog) {
    val kind = Labels.errorKind(log.error_kind)
    Column {
        ListItem(
            headlineContent = {
                Text(
                    log.account_email.ifBlank { log.account_id },
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            supportingContent = {
                Column {
                    Text(
                        "${refreshTypeText(log.refresh_type)} · ${formatFullTime(log.created_at)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (kind.isNotBlank()) {
                        Text(
                            kind,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    if (log.error_message.isNotBlank()) {
                        Text(
                            log.error_message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
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
