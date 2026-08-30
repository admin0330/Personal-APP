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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.masteralanlab.emailbox.data.Prefs
import com.masteralanlab.emailbox.data.remote.ApiResult
import com.masteralanlab.emailbox.data.remote.AppJson
import com.masteralanlab.emailbox.data.remote.AuditLog
import com.masteralanlab.emailbox.data.remote.apiCall
import com.masteralanlab.emailbox.ui.components.AppTopBar
import com.masteralanlab.emailbox.ui.components.DropdownField
import com.masteralanlab.emailbox.ui.components.EmptyBox
import com.masteralanlab.emailbox.ui.components.ErrorBox
import com.masteralanlab.emailbox.ui.components.Labels
import com.masteralanlab.emailbox.ui.components.LoadingBox
import com.masteralanlab.emailbox.util.formatFullTime
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** Labels.auditAction() 覆盖的全部 action，顺序与后端约定一致。 */
private val AUDIT_ACTIONS = listOf(
    "account.list",
    "account.read",
    "account.create",
    "account.update",
    "account.delete",
    "account.import",
    "account.batch",
    "account.export",
    "message.read",
    "message.write",
    "group.write",
    "api_key.reset",
    "token.refresh",
    "token.reauthorize",
    "job.submit",
    "job.stop",
    "user.update",
    "user.delete",
    "user.reset_password",
    "plan.create",
    "plan.update",
    "plan.delete",
    "quota.update",
)

private val ACTION_OPTIONS: List<Pair<String?, String>> =
    listOf(null to "全部操作") + AUDIT_ACTIONS.map { it to Labels.auditAction(it) }

private val ACTOR_OPTIONS: List<Pair<String?, String>> = listOf(
    null to "不限",
    "user" to Labels.actorKind("user"),
    "admin" to Labels.actorKind("admin"),
    "api_key" to Labels.actorKind("api_key"),
    "system" to Labels.actorKind("system"),
)

private const val DETAILS_INDENT = "  "

/**
 * details 是 JSON 字符串而非对象。能解析就缩进展示，解析失败或为空对象则原样/隐藏处理。
 * 这里自己走一遍 JsonElement 而不依赖 Json.encodeToString，避免序列化版本差异。
 */
private fun formatDetails(raw: String): String {
    val text = raw.trim()
    if (text.isBlank() || text == "{}" || text == "[]" || text == "null") return ""
    val element = runCatching { AppJson.parseToJsonElement(text) }.getOrNull() ?: return text
    return prettyJsonElement(element, 0)
}

private fun prettyJsonElement(element: JsonElement, depth: Int): String {
    val pad = DETAILS_INDENT.repeat(depth)
    val inner = DETAILS_INDENT.repeat(depth + 1)
    return when (element) {
        is JsonObject -> if (element.isEmpty()) {
            "{}"
        } else {
            element.entries.joinToString(",\n", "{\n", "\n$pad}") { (key, value) ->
                "$inner${quoteJson(key)}: ${prettyJsonElement(value, depth + 1)}"
            }
        }

        is JsonArray -> if (element.isEmpty()) {
            "[]"
        } else {
            element.joinToString(",\n", "[\n", "\n$pad]") { inner + prettyJsonElement(it, depth + 1) }
        }

        is JsonPrimitive -> if (element.isString) quoteJson(element.content) else element.content

        else -> element.toString()
    }
}

private fun quoteJson(value: String): String {
    val escaped = value
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")
        .replace("\r", "\\r")
        .replace("\t", "\\t")
    return "\"$escaped\""
}

// ---------------------------------------------------------------- 数据层

class AdminAuditViewModel : ViewModel() {

    private companion object {
        const val PAGE_SIZE = 30
        const val NO_PERMISSION = "需要平台管理员权限"
    }

    private val _items = MutableStateFlow<List<AuditLog>>(emptyList())
    val items: StateFlow<List<AuditLog>> = _items.asStateFlow()

    private val _action = MutableStateFlow<String?>(null)
    val action: StateFlow<String?> = _action.asStateFlow()

    private val _actorKind = MutableStateFlow<String?>(null)
    val actorKind: StateFlow<String?> = _actorKind.asStateFlow()

    private val _onlyCurrentTenant = MutableStateFlow(false)
    val onlyCurrentTenant: StateFlow<Boolean> = _onlyCurrentTenant.asStateFlow()

    private val _loading = MutableStateFlow(true)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    private val _loadingMore = MutableStateFlow(false)
    val loadingMore: StateFlow<Boolean> = _loadingMore.asStateFlow()

    private val _hasMore = MutableStateFlow(false)
    val hasMore: StateFlow<Boolean> = _hasMore.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _moreError = MutableStateFlow<String?>(null)
    val moreError: StateFlow<String?> = _moreError.asStateFlow()

    private var page = 1
    private var pages = 1

    init {
        refresh()
    }

    fun onActionChange(value: String?) {
        if (_action.value == value) return
        _action.value = value
        reload()
    }

    fun onActorKindChange(value: String?) {
        if (_actorKind.value == value) return
        _actorKind.value = value
        reload()
    }

    fun onOnlyCurrentTenantChange(value: Boolean) {
        if (_onlyCurrentTenant.value == value) return
        _onlyCurrentTenant.value = value
        reload()
    }

    fun refresh() {
        viewModelScope.launch {
            _refreshing.value = true
            page = 1
            loadPage(reset = true)
            _refreshing.value = false
            _loading.value = false
        }
    }

    fun reload() {
        viewModelScope.launch {
            _loading.value = true
            _error.value = null
            page = 1
            loadPage(reset = true)
            _loading.value = false
        }
    }

    fun loadMore() {
        if (_loadingMore.value || _loading.value || !_hasMore.value) return
        viewModelScope.launch {
            _loadingMore.value = true
            _moreError.value = null
            loadPage(reset = false)
            _loadingMore.value = false
        }
    }

    private suspend fun loadPage(reset: Boolean) {
        if (!Prefs.isPlatformAdmin) {
            if (reset) {
                _items.value = emptyList()
                _hasMore.value = false
                _error.value = NO_PERMISSION
            } else {
                _moreError.value = NO_PERMISSION
            }
            return
        }
        val tenantId = if (_onlyCurrentTenant.value) Prefs.tenantId else null
        when (val r = apiCall {
            adminAudit(
                tenantId = tenantId,
                actorUserId = null,
                actorKind = _actorKind.value,
                action = _action.value,
                page = page,
                limit = PAGE_SIZE,
            )
        }) {
            is ApiResult.Success -> {
                val paged = r.data
                val p = paged.pagination
                page = p?.page ?: page
                pages = p?.pages ?: 1
                _items.value = if (reset) paged.items else _items.value + paged.items
                _hasMore.value = page < pages && paged.items.isNotEmpty()
                _error.value = null
                _moreError.value = null
            }

            is ApiResult.Failure -> {
                if (reset) {
                    _items.value = emptyList()
                    _hasMore.value = false
                    _error.value = if (r.httpStatus == 403) NO_PERMISSION else r.message
                } else {
                    _moreError.value = r.message
                }
            }
        }
    }
}

// ---------------------------------------------------------------- 页面

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminAuditScreen(onBack: () -> Unit) {
    val vm: AdminAuditViewModel = viewModel()

    val items by vm.items.collectAsState()
    val action by vm.action.collectAsState()
    val actorKind by vm.actorKind.collectAsState()
    val onlyCurrent by vm.onlyCurrentTenant.collectAsState()
    val loading by vm.loading.collectAsState()
    val refreshing by vm.refreshing.collectAsState()
    val loadingMore by vm.loadingMore.collectAsState()
    val hasMore by vm.hasMore.collectAsState()
    val error by vm.error.collectAsState()
    val moreError by vm.moreError.collectAsState()

    val isAdmin = remember { Prefs.isPlatformAdmin }
    val currentTenantId = remember { Prefs.tenantId }

    Scaffold(topBar = { AppTopBar(title = "审计日志", onBack = onBack) }) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            if (!isAdmin) {
                Box(Modifier.fillMaxSize()) {
                    EmptyBox(
                        text = "需要平台管理员权限\n当前账号无权查看审计日志",
                        icon = Icons.Outlined.Lock,
                    )
                }
            } else {
                AuditFilterRow(
                    action = action,
                    onActionChange = vm::onActionChange,
                    actorKind = actorKind,
                    onActorKindChange = vm::onActorKindChange,
                    onlyCurrentTenant = onlyCurrent,
                    onOnlyCurrentTenantChange = vm::onOnlyCurrentTenantChange,
                    hasTenant = !currentTenantId.isNullOrBlank(),
                )
                HorizontalDivider()

                Box(Modifier.weight(1f).fillMaxWidth()) {
                    when {
                        loading && items.isEmpty() -> LoadingBox(text = "正在加载审计日志…")
                        error != null && items.isEmpty() ->
                            ErrorBox(message = error ?: "加载失败", onRetry = vm::reload)
                        items.isEmpty() -> EmptyBox(text = "没有符合条件的审计记录")
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
                                itemsIndexed(items, key = { _, log -> log.id }) { index, log ->
                                    AuditLogCard(log)
                                    if (index == items.lastIndex && hasMore) {
                                        LaunchedEffect(items.size) { vm.loadMore() }
                                    }
                                }
                                if (loadingMore || moreError != null) {
                                    item {
                                        AuditLoadMoreFooter(
                                            error = moreError,
                                            loading = loadingMore,
                                            onRetry = vm::loadMore,
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
}

@Composable
private fun AuditFilterRow(
    action: String?,
    onActionChange: (String?) -> Unit,
    actorKind: String?,
    onActorKindChange: (String?) -> Unit,
    onlyCurrentTenant: Boolean,
    onOnlyCurrentTenantChange: (Boolean) -> Unit,
    hasTenant: Boolean,
) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            DropdownField(
                label = "操作类型",
                options = ACTION_OPTIONS,
                selected = action,
                onSelect = onActionChange,
                modifier = Modifier.weight(1f),
            )
            DropdownField(
                label = "操作者类型",
                options = ACTOR_OPTIONS,
                selected = actorKind,
                onSelect = onActorKindChange,
                modifier = Modifier.weight(1f),
            )
        }
        ListItem(
            headlineContent = { Text("仅看当前工作空间") },
            supportingContent = {
                Text(if (hasTenant) "不勾选则查看全部工作空间" else "尚未选择工作空间")
            },
            trailingContent = {
                Switch(
                    checked = onlyCurrentTenant,
                    onCheckedChange = onOnlyCurrentTenantChange,
                    enabled = hasTenant,
                )
            },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

// ---------------------------------------------------------------- 列表

@Composable
private fun AuditLogCard(log: AuditLog) {
    val details = remember(log.details) { formatDetails(log.details) }
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.fillMaxWidth().padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    Labels.auditAction(log.action),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.width(8.dp))
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                ) {
                    Text(
                        Labels.actorKind(log.actor_kind),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                buildList {
                    add(log.actor_name.ifBlank { "未知操作者" })
                    if (log.ip.isNotBlank()) add(log.ip)
                }.joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth()) {
                Text(
                    "资源：${log.resource_type.ifBlank { "—" }}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    formatFullTime(log.created_at).ifBlank { log.created_at },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (log.resource_id.isNotBlank()) {
                Text(
                    log.resource_id,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (details.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        details,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(10.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun AuditLoadMoreFooter(error: String?, loading: Boolean, onRetry: () -> Unit) {
    Box(
        Modifier.fillMaxWidth().padding(vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (error != null) {
            TextButton(onClick = onRetry) {
                Text("$error · 点击重试", color = MaterialTheme.colorScheme.error)
            }
        } else if (loading) {
            CircularProgressIndicator(Modifier.size(24.dp))
        }
    }
}
