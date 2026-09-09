package com.masteralanlab.emailbox.ui.screens.tokens

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.masteralanlab.emailbox.data.Prefs
import com.masteralanlab.emailbox.data.remote.ApiResult
import com.masteralanlab.emailbox.data.remote.Job
import com.masteralanlab.emailbox.data.remote.JobItem
import com.masteralanlab.emailbox.data.remote.SseClient
import com.masteralanlab.emailbox.data.remote.SseEvent
import com.masteralanlab.emailbox.data.remote.apiCall
import com.masteralanlab.emailbox.data.remote.apiCallUnit
import com.masteralanlab.emailbox.data.remote.presentableErrorMessage
import com.masteralanlab.emailbox.ui.components.AppTopBar
import com.masteralanlab.emailbox.ui.components.DropdownField
import com.masteralanlab.emailbox.ui.components.EmptyBox
import com.masteralanlab.emailbox.ui.components.ErrorBox
import com.masteralanlab.emailbox.ui.components.Labels
import com.masteralanlab.emailbox.ui.components.LoadingBox
import com.masteralanlab.emailbox.ui.components.ProductSurface
import com.masteralanlab.emailbox.ui.components.SectionTitle
import com.masteralanlab.emailbox.ui.components.StatusChip
import com.masteralanlab.emailbox.ui.components.statusContainer
import com.masteralanlab.emailbox.ui.components.statusContent
import com.masteralanlab.emailbox.util.formatFullTime
import com.masteralanlab.emailbox.util.formatShortTime
import com.masteralanlab.emailbox.util.parseInstant
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Locale

private const val MAX_LIVE_EVENTS = 200
private const val ITEMS_PAGE_SIZE = 30

data class JobDetailUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val job: Job? = null,
    /** SSE 实时计数，finished 之前以它为准。 */
    val liveTotal: Int = 0,
    val liveSuccess: Int = 0,
    val liveFailed: Int = 0,
    val liveDone: Int = 0,
    val current: String? = null,
    /** 最近 200 条 item 事件，最新的在前面。 */
    val events: List<SseEvent.Item> = emptyList(),
    /** SSE finished 事件带来的最终状态；为空表示还没收到。 */
    val streamStatus: String? = null,
    val streamError: String? = null,
    val finishedSummary: String? = null,
    val items: List<JobItem> = emptyList(),
    val itemPage: Int = 1,
    val itemPages: Int = 1,
    val loadingItems: Boolean = false,
    val itemFilter: String? = null,
    val stopping: Boolean = false,
    val actionMessage: String? = null,
)

class JobDetailViewModel : ViewModel() {

    private val _state = MutableStateFlow(JobDetailUiState())
    val state: StateFlow<JobDetailUiState> = _state.asStateFlow()

    private var tenantId: String? = null
    private var jobId: String? = null
    private var streamHandle: kotlinx.coroutines.Job? = null

    /** 明细请求代际号：筛选切换/加载更多/终态刷新并发时，过期响应不允许写回明细列表。 */
    private var itemsSeq = 0

    fun load(jobId: String) {
        val tenant = Prefs.tenantId
        this.tenantId = tenant
        this.jobId = jobId
        streamHandle?.cancel()
        if (tenant.isNullOrBlank()) {
            _state.update { JobDetailUiState(loading = false, error = "未选择工作空间") }
            return
        }
        _state.update { JobDetailUiState(loading = true) }
        loadDetail()
    }

    private fun loadDetail() {
        val tenant = tenantId ?: return
        val jid = jobId ?: return
        viewModelScope.launch {
            when (val r = apiCall { job(tenant, jid) }) {
                is ApiResult.Success -> {
                    val j = r.data
                    _state.update {
                        it.copy(
                            loading = false,
                            error = null,
                            job = j,
                            liveTotal = j.total_count,
                            liveSuccess = j.success_count,
                            liveFailed = j.failed_count,
                            liveDone = j.success_count + j.failed_count,
                        )
                    }
                    loadItems(1)
                    if (isJobRunning(j.status)) startStream(tenant, jid)
                }

                is ApiResult.Failure -> _state.update {
                    it.copy(loading = false, error = r.message)
                }
            }
        }
    }

    private fun startStream(tenant: String, jid: String) {
        streamHandle?.cancel()
        streamHandle = viewModelScope.launch {
            SseClient.jobStream(tenant, jid).collect { ev ->
                when (ev) {
                    is SseEvent.Started -> _state.update {
                        it.copy(liveTotal = maxOf(it.liveTotal, ev.total))
                    }

                    is SseEvent.Progress -> _state.update {
                        it.copy(
                            liveTotal = maxOf(it.liveTotal, ev.total),
                            liveSuccess = maxOf(it.liveSuccess, ev.success),
                            liveFailed = maxOf(it.liveFailed, ev.failed),
                            liveDone = maxOf(it.liveDone, ev.done),
                            current = ev.current.ifBlank { null },
                        )
                    }

                    is SseEvent.Item -> _state.update {
                        it.copy(
                            events = (listOf(ev) + it.events).take(MAX_LIVE_EVENTS),
                            liveSuccess = it.liveSuccess + if (ev.status == "success") 1 else 0,
                            liveFailed = it.liveFailed + if (ev.status == "failed") 1 else 0,
                            liveDone = it.liveDone + 1,
                        )
                    }

                    is SseEvent.Finished -> {
                        _state.update {
                            it.copy(
                                job = it.job?.copy(status = ev.status),
                                streamStatus = ev.status,
                                liveTotal = maxOf(it.liveTotal, ev.total),
                                liveSuccess = maxOf(it.liveSuccess, ev.success),
                                liveFailed = maxOf(it.liveFailed, ev.failed),
                                liveDone = maxOf(it.liveDone, ev.success + ev.failed + ev.skipped),
                                finishedSummary = ev.errorSummary.ifBlank { null },
                                current = null,
                            )
                        }
                        // 终态已到，重新拉一次详情并加载明细
                        loadDetail()
                    }

                    is SseEvent.Error -> _state.update {
                        it.copy(streamError = ev.msg, current = null)
                    }
                }
            }
        }
    }

    fun loadItems(page: Int) {
        val tenant = tenantId ?: return
        val jid = jobId ?: return
        val seq = ++itemsSeq
        if (page > 1) _state.update { it.copy(loadingItems = true) }
        viewModelScope.launch {
            val filter = _state.value.itemFilter
            when (val r = apiCall {
                jobItems(tenant, jid, status = filter, page = page, limit = ITEMS_PAGE_SIZE)
            }) {
                is ApiResult.Success -> if (seq == itemsSeq) _state.update {
                    it.copy(
                        items = if (page <= 1) r.data.items else it.items + r.data.items,
                        itemPage = page,
                        itemPages = (r.data.pagination?.pages ?: 1).coerceAtLeast(1),
                        loadingItems = false,
                    )
                }

                is ApiResult.Failure -> if (seq == itemsSeq) _state.update {
                    it.copy(loadingItems = false, actionMessage = r.message)
                }
            }
        }
    }

    fun loadMoreItems() {
        val s = _state.value
        if (s.loadingItems || s.itemPage >= s.itemPages) return
        loadItems(s.itemPage + 1)
    }

    fun setItemFilter(filter: String?) {
        _state.update { it.copy(itemFilter = filter, items = emptyList(), itemPage = 1, itemPages = 1) }
        loadItems(1)
    }

    fun stop() {
        val tenant = tenantId ?: return
        val jid = jobId ?: return
        _state.update { it.copy(stopping = true, actionMessage = null) }
        viewModelScope.launch {
            when (val r = apiCallUnit { stopJob(tenant, jid) }) {
                is ApiResult.Success -> {
                    _state.update {
                        it.copy(
                            stopping = false,
                            actionMessage = "已请求停止，正在处理中的账号不会被打断",
                            job = it.job?.copy(status = "stopping"),
                        )
                    }
                    loadDetail()
                }

                is ApiResult.Failure -> _state.update {
                    it.copy(stopping = false, actionMessage = r.message)
                }
            }
        }
    }

    fun dismissMessage() = _state.update { it.copy(actionMessage = null) }

    override fun onCleared() {
        streamHandle?.cancel()
        streamHandle = null
        super.onCleared()
    }
}

@Composable
fun JobDetailScreen(jobId: String, onBack: () -> Unit) {
    val vm: JobDetailViewModel = viewModel()
    val state by vm.state.collectAsStateWithLifecycle()

    LaunchedEffect(jobId) { vm.load(jobId) }

    Scaffold(topBar = { AppTopBar("任务详情", onBack = onBack) }) { padding ->
        when {
            state.loading -> LoadingBox(Modifier.padding(padding))
            state.error == "未选择工作空间" ->
                EmptyBox("未选择工作空间", modifier = Modifier.padding(padding))
            state.job == null && state.error != null ->
                ErrorBox(
                    message = state.error ?: "",
                    onRetry = { vm.load(jobId) },
                    modifier = Modifier.padding(padding),
                )
            else -> JobDetailContent(state, vm, Modifier.padding(padding))
        }
    }
}

@Composable
private fun JobDetailContent(
    state: JobDetailUiState,
    vm: JobDetailViewModel,
    modifier: Modifier = Modifier,
) {
    var tab by remember { mutableIntStateOf(0) }

    Column(modifier.fillMaxSize()) {
        SummaryCard(state, vm)

        TabRow(selectedTabIndex = tab) {
            Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("实时进度") })
            Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("全部明细") })
        }

        Box(Modifier.weight(1f)) {
            when (tab) {
                0 -> LivePane(state)
                1 -> ItemsPane(state, vm)
            }
        }
    }
}

@Composable
private fun SummaryCard(state: JobDetailUiState, vm: JobDetailViewModel) {
    val job = state.job ?: return
    val status = state.streamStatus ?: job.status
    val total = state.liveTotal
    val done = state.liveDone.coerceAtMost(total.takeIf { it > 0 } ?: state.liveDone)
    val progress = jobProgressOf(state.liveSuccess, state.liveFailed, total)

    ProductSurface(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    jobTypeText(job.type),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                StatusChip(
                    text = Labels.jobStatus(status),
                    container = statusContainer(status),
                    content = statusContent(status),
                )
            }

            Spacer(Modifier.height(12.dp))

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                CountCell("总数", total, MaterialTheme.colorScheme.onSurface)
                CountCell("已完成", done, MaterialTheme.colorScheme.onSurfaceVariant)
                CountCell("成功", state.liveSuccess, MaterialTheme.colorScheme.primary)
                CountCell("失败", state.liveFailed, MaterialTheme.colorScheme.error)
            }

            Spacer(Modifier.height(12.dp))

            if (total > 0) {
                LinearProgressIndicator(progress = progress, modifier = Modifier.fillMaxWidth())
            } else {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            if (state.current != null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "正在处理：${state.current}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            if (state.finishedSummary != null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "错误汇总：${presentableErrorMessage(state.finishedSummary)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            if (state.streamError != null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    presentableErrorMessage(state.streamError),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            if (state.actionMessage != null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    state.actionMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(8.dp))
            Text(
                "触发方式：${jobTriggerText(job.trigger)}｜创建于 ${formatFullTime(job.created_at)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (isJobRunning(status)) {
                Spacer(Modifier.height(12.dp))
                OutlinedButton(
                    onClick = vm::stop,
                    enabled = !state.stopping,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (state.stopping) "停止中…" else "停止任务（不会打断正在处理的账号）")
                }
            }
        }
    }
}

@Composable
private fun CountCell(label: String, value: Int, color: androidx.compose.ui.graphics.Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(72.dp)) {
        Text("$value", style = MaterialTheme.typography.titleMedium, color = color)
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun LivePane(state: JobDetailUiState) {
    if (state.events.isEmpty()) {
        EmptyBox(
            text = if (state.streamStatus == null) "等待任务事件…" else "本次没有实时事件记录",
            modifier = Modifier.fillMaxSize(),
        )
        return
    }
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item { SectionTitle("实时事件（最新在上，最多 $MAX_LIVE_EVENTS 条）") }
        items(state.events) { ev -> LiveEventRow(ev) }
    }
}

@Composable
private fun LiveEventRow(ev: SseEvent.Item) {
    val kind = Labels.errorKind(ev.errorKind)
    Column {
        ListItem(
            headlineContent = {
                Text(
                    ev.email.ifBlank { ev.accountId },
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            supportingContent = {
                val detail = buildList {
                    if (kind.isNotBlank()) add(kind)
                    if (ev.error.isNotBlank()) add(presentableErrorMessage(ev.error))
                }.joinToString("：")
                if (detail.isNotBlank()) {
                    Text(
                        detail,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            trailingContent = {
                StatusChip(
                    text = Labels.jobItemStatus(ev.status),
                    container = statusContainer(ev.status),
                    content = statusContent(ev.status),
                )
            },
        )
        HorizontalDivider()
    }
}

@Composable
private fun ItemsPane(state: JobDetailUiState, vm: JobDetailViewModel) {
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            DropdownField(
                label = "结果筛选",
                options = listOf(
                    null to "全部",
                    "success" to "成功",
                    "failed" to "失败",
                    "skipped" to "已跳过",
                    "pending" to "排队中",
                    "running" to "进行中",
                ),
                selected = state.itemFilter,
                onSelect = vm::setItemFilter,
            )
        }

        if (state.items.isEmpty() && !state.loadingItems) {
            EmptyBox("没有符合条件的明细", modifier = Modifier.fillMaxWidth().weight(1f))
            return
        }

        LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f)) {
            items(state.items) { item -> JobItemRow(item) }
            if (state.itemPage < state.itemPages) {
                item {
                    LoadMoreRow(
                        loading = state.loadingItems,
                        text = "加载更多明细",
                        onClick = vm::loadMoreItems,
                    )
                }
            }
        }
    }
}

@Composable
private fun JobItemRow(item: JobItem) {
    val kind = Labels.errorKind(item.error_kind)
    val duration = durationText(item.started_at, item.finished_at)
    Column {
        ListItem(
            overlineContent = {
                Text("#${item.position}", style = MaterialTheme.typography.labelSmall)
            },
            headlineContent = {
                Text(
                    item.email.ifBlank { item.account_id },
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            supportingContent = {
                val parts = buildList {
                    if (kind.isNotBlank()) add(kind)
                    if (item.error.isNotBlank()) add(presentableErrorMessage(item.error))
                    if (duration.isNotBlank()) add("耗时 $duration")
                    val t = formatShortTime(item.finished_at ?: item.started_at)
                    if (t.isNotBlank()) add(t)
                }
                if (parts.isNotEmpty()) {
                    Text(
                        parts.joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            trailingContent = {
                StatusChip(
                    text = Labels.jobItemStatus(item.status),
                    container = statusContainer(item.status),
                    content = statusContent(item.status),
                )
            },
        )
        HorizontalDivider()
    }
}

private fun durationText(started: String?, finished: String?): String {
    val s = parseInstant(started) ?: return ""
    val f = parseInstant(finished) ?: return ""
    val ms = f.toEpochMilli() - s.toEpochMilli()
    if (ms < 0) return ""
    return if (ms < 1000) "${ms} 毫秒" else String.format(Locale.CHINA, "%.1f 秒", ms / 1000.0)
}
