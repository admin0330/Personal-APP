package com.masteralanlab.emailbox.ui.screens.ledger

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import com.masteralanlab.emailbox.ui.components.appleClickable
import com.masteralanlab.emailbox.ui.components.appleCombinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.BorderStroke
import com.masteralanlab.emailbox.ui.components.Ym1rIcons
import com.masteralanlab.emailbox.ui.components.RollingMoney
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.ui.platform.LocalContext
import com.masteralanlab.emailbox.util.FileSharing
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.masteralanlab.emailbox.ui.components.UserAvatarButton
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.masteralanlab.emailbox.data.LedgerLocalStore
import com.masteralanlab.emailbox.data.Prefs
import com.masteralanlab.emailbox.data.remote.ApiResult
import com.masteralanlab.emailbox.data.remote.CreateLedgerTransactionRequest
import com.masteralanlab.emailbox.data.remote.LedgerSummary
import com.masteralanlab.emailbox.data.remote.LedgerTransaction
import com.masteralanlab.emailbox.data.remote.UpdateLedgerTransactionRequest
import com.masteralanlab.emailbox.data.remote.apiCall
import com.masteralanlab.emailbox.data.remote.apiCallUnit
import com.masteralanlab.emailbox.ui.components.AppTopBar
import com.masteralanlab.emailbox.ui.components.MainTopBar
import com.masteralanlab.emailbox.ui.components.AppleColors
import com.masteralanlab.emailbox.ui.components.AppleIconSquircle
import com.masteralanlab.emailbox.ui.components.AppleSegmentedControl
import com.masteralanlab.emailbox.ui.components.EmptyBox
import com.masteralanlab.emailbox.ui.components.ProductField
import com.masteralanlab.emailbox.ui.components.ProductSurface
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.OffsetDateTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.UUID
import kotlin.math.roundToInt

private val LEDGER_CATEGORIES = listOf("餐饮", "交通", "购物", "订阅", "住房", "医疗", "数码服务", "工资", "其他")

data class LedgerState(
    val month: YearMonth = YearMonth.now(),
    val transactions: List<LedgerTransaction> = emptyList(),
    val summary: LedgerSummary? = null,
    val loading: Boolean = true,
    val pending: Int = 0,
    val error: String? = null,
)

class LedgerViewModel : ViewModel() {
    val state = MutableStateFlow(LedgerState())
    private val _message = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val message = _message.asSharedFlow()
    private var initialized = false
    private var loadGeneration = 0L
    private var flushingPending = false

    fun init() {
        if (initialized) return
        initialized = true
        load()
    }

    fun changeMonth(delta: Long) {
        state.update { it.copy(month = it.month.plusMonths(delta), loading = true) }
        load()
    }

    fun load() = viewModelScope.launch {
        val generation = ++loadGeneration
        load(generation)
    }

    private suspend fun load(generation: Long) {
        val tenant = Prefs.tenantId.orEmpty()
        if (tenant.isBlank()) { state.update { it.copy(loading = false, error = "未选择工作空间") }; return }
        val month = state.value.month.toString()
        val local = LedgerLocalStore.load(tenant)
        if (local.month == month && generation == loadGeneration) {
            // 缓存直显：立即退出加载态，网络结果回来后静默覆盖
            state.update {
                it.copy(
                    transactions = local.transactions,
                    summary = local.summary,
                    pending = pendingCount(local),
                    loading = false,
                )
            }
        }
        if (!flushingPending) {
            flushingPending = true
            viewModelScope.launch {
                try {
                    flushPending(tenant)
                } finally {
                    flushingPending = false
                }
            }
        }
        val list = apiCall { ledgerTransactions(tenant, month) }
        val summary = apiCall { ledgerSummary(tenant, month) }
        if (generation != loadGeneration) return
        if (list is ApiResult.Success && summary is ApiResult.Success) {
            val pendingData = LedgerLocalStore.load(tenant)
            val data = com.masteralanlab.emailbox.data.LedgerLocalData(
                tenant = tenant,
                month = month,
                transactions = list.data,
                summary = summary.data,
                pendingCreates = pendingData.pendingCreates,
                pendingUpdates = pendingData.pendingUpdates,
                pendingDeletes = pendingData.pendingDeletes,
            )
            LedgerLocalStore.save(data)
            if (generation == loadGeneration) {
                state.update {
                    if (it.month.toString() != month) it
                    else it.copy(
                        transactions = list.data,
                        summary = summary.data,
                        loading = false,
                        pending = pendingCount(pendingData),
                        error = null,
                    )
                }
            }
        } else {
            val error = (list as? ApiResult.Failure)?.message ?: (summary as? ApiResult.Failure)?.message
            if (generation == loadGeneration) {
                state.update {
                    it.copy(loading = false, pending = pendingCount(LedgerLocalStore.load(tenant)), error = error)
                }
            }
        }
    }

    private fun pendingCount(data: com.masteralanlab.emailbox.data.LedgerLocalData): Int =
        data.pendingCreates.size + data.pendingUpdates.size + data.pendingDeletes.size

    fun create(
        type: String,
        amount: String,
        currency: String,
        category: String,
        merchant: String,
        note: String,
        source: String = "manual",
        sourceMessageKey: String? = null,
        occurredAt: String = OffsetDateTime.now().toString(),
        posted: Boolean = true,
    ) {
        val minor = parseMinor(amount) ?: run { _message.tryEmit("请输入正确金额"); return }
        val tenant = Prefs.tenantId.orEmpty()
        if (tenant.isBlank()) return
        val request = CreateLedgerTransactionRequest(
            type, minor, currency, category, occurredAt, merchant.trim(), note.trim(),
            source = source, source_message_key = sourceMessageKey, client_id = UUID.randomUUID().toString(),
            posted = posted,
        )
        LedgerLocalStore.enqueue(tenant, request)
        state.update { it.copy(pending = pendingCount(LedgerLocalStore.load(tenant))) }
        viewModelScope.launch {
            when (val result = apiCall { createLedgerTransaction(tenant, request) }) {
                is ApiResult.Success -> {
                    LedgerLocalStore.complete(tenant, request.client_id, result.data)
                    _message.tryEmit("已记账")
                    load()
                }
                is ApiResult.Failure -> {
                    if (result.httpStatus == 409 && reconcileConflict(tenant, request)) {
                        _message.tryEmit("这封邮件已在账本中")
                        load()
                    } else if (result.httpStatus == 0 || result.httpStatus == 429 || result.httpStatus >= 500) {
                        _message.tryEmit("已离线保存，联网后自动同步")
                    } else _message.tryEmit(result.message)
                    state.update { it.copy(pending = pendingCount(LedgerLocalStore.load(tenant))) }
                }
            }
        }
    }

    fun update(
        item: LedgerTransaction,
        type: String,
        amount: String,
        currency: String,
        category: String,
        merchant: String,
        note: String,
        occurredAt: String? = null,
        posted: Boolean? = null,
    ) {
        val minor = parseMinor(amount) ?: run { _message.tryEmit("请输入正确金额"); return }
        val tenant = Prefs.tenantId.orEmpty()
        // 本地优先：立即写本地缓存并刷新界面（无等待），后台再同步服务器
        val updated = item.copy(
            type = type, amount_minor = minor, currency = currency, category = category,
            occurred_at = occurredAt ?: item.occurred_at, merchant = merchant.trim(),
            note = note.trim(), posted = posted ?: item.posted,
        )
        LedgerLocalStore.putTransaction(tenant, updated)
        state.update { current -> current.copy(transactions = current.transactions.map { if (it.id == item.id) updated else it }) }
        val op = com.masteralanlab.emailbox.data.PendingLedgerUpdate(
            serverId = item.id, posted = posted, occurredAt = occurredAt,
            type = type, amountMinor = minor, currency = currency, category = category,
            merchant = merchant.trim(), note = note.trim(),
        )
        LedgerLocalStore.enqueueUpdate(tenant, op)
        viewModelScope.launch {
            when (val result = apiCall {
                updateLedgerTransaction(
                    tenant, item.id,
                    UpdateLedgerTransactionRequest(
                        type, minor, currency, category,
                        occurred_at = occurredAt, merchant = merchant.trim(), note = note.trim(),
                        posted = posted,
                    ),
                )
            }) {
                is ApiResult.Success -> { LedgerLocalStore.popPendingUpdate(tenant, op); load() }
                is ApiResult.Failure -> {
                    // 回滚：恢复原记录并提示
                    LedgerLocalStore.popPendingUpdate(tenant, op)
                    LedgerLocalStore.putTransaction(tenant, item)
                    state.update { current -> current.copy(transactions = current.transactions.map { if (it.id == item.id) item else it }) }
                    _message.tryEmit(result.message)
                }
            }
        }
    }

    fun delete(item: LedgerTransaction) {
        val tenant = Prefs.tenantId.orEmpty()
        // 本地优先：立即从列表与缓存移除
        LedgerLocalStore.removeTransaction(tenant, item.id)
        state.update { current -> current.copy(transactions = current.transactions.filterNot { it.id == item.id }) }
        viewModelScope.launch {
            when (val result = apiCallUnit { deleteLedgerTransaction(tenant, item.id) }) {
                is ApiResult.Success -> { _message.tryEmit("已删除"); load() }
                is ApiResult.Failure -> {
                    when {
                        result.httpStatus == 404 -> {
                            _message.tryEmit("已删除")
                            load()
                        }
                        result.httpStatus == 0 || result.httpStatus == 429 || result.httpStatus >= 500 -> {
                            LedgerLocalStore.enqueueDelete(tenant, item.id)
                            state.update { current -> current.copy(pending = pendingCount(LedgerLocalStore.load(tenant))) }
                            _message.tryEmit("已离线删除，联网后自动同步")
                        }
                        else -> {
                            LedgerLocalStore.putTransaction(tenant, item)
                            state.update { current -> current.copy(transactions = current.transactions.map { if (it.id == item.id) item else it }) }
                            _message.tryEmit(result.message)
                            load()
                        }
                    }
                }
            }
        }
    }

    private suspend fun flushPending(tenant: String) {
        for (request in LedgerLocalStore.load(tenant).pendingCreates) {
            val delays = listOf(5_000L, 30_000L, 120_000L, 600_000L)
            for (attempt in 0 until 5) {
                when (val result = apiCall { createLedgerTransaction(tenant, request) }) {
                    is ApiResult.Success -> {
                        LedgerLocalStore.complete(tenant, request.client_id, result.data)
                        state.update { it.copy(pending = pendingCount(LedgerLocalStore.load(tenant))) }
                        break
                    }
                    is ApiResult.Failure -> {
                        if (result.httpStatus in listOf(401, 403, 404)) return
                        if (result.httpStatus == 409 && reconcileConflict(tenant, request)) break
                        if (result.httpStatus !in listOf(0, 429, 502, 503, 504) || attempt == 4) break
                        delay(delays[attempt])
                    }
                }
            }
        }

        // 编辑/入账切换类更新
        for (op in LedgerLocalStore.load(tenant).pendingUpdates.toList()) {
            val delays = listOf(5_000L, 30_000L, 120_000L, 600_000L)
            for (attempt in 0 until 5) {
                when (val result = apiCall {
                    updateLedgerTransaction(
                        tenant, op.serverId,
                        UpdateLedgerTransactionRequest(
                            type = op.type, amount_minor = op.amountMinor, currency = op.currency,
                            category = op.category, occurred_at = op.occurredAt,
                            merchant = op.merchant, note = op.note, posted = op.posted,
                        ),
                    )
                }) {
                    is ApiResult.Success -> { LedgerLocalStore.popPendingUpdate(tenant, op); break }
                    is ApiResult.Failure -> {
                        if (result.httpStatus in listOf(401, 403, 404)) { LedgerLocalStore.popPendingUpdate(tenant, op); break }
                        if (result.httpStatus !in listOf(0, 429, 502, 503, 504) || attempt == 4) break
                        delay(delays[attempt])
                    }
                }
            }
        }

        // 删除
        for (serverId in LedgerLocalStore.load(tenant).pendingDeletes.toList()) {
            val delays = listOf(5_000L, 30_000L, 120_000L, 600_000L)
            for (attempt in 0 until 5) {
                when (val result = apiCallUnit { deleteLedgerTransaction(tenant, serverId) }) {
                    is ApiResult.Success -> { LedgerLocalStore.popPendingDelete(tenant, serverId); break }
                    is ApiResult.Failure -> {
                        if (result.httpStatus in listOf(401, 403, 404)) { LedgerLocalStore.popPendingDelete(tenant, serverId); break }
                        if (result.httpStatus !in listOf(0, 429, 502, 503, 504) || attempt == 4) break
                        delay(delays[attempt])
                    }
                }
            }
        }
    }

    private suspend fun reconcileConflict(tenant: String, request: CreateLedgerTransactionRequest): Boolean {
        val month = runCatching { YearMonth.from(OffsetDateTime.parse(request.occurred_at)).toString() }.getOrDefault(state.value.month.toString())
        val list = apiCall { ledgerTransactions(tenant, month) }
        val match = (list as? ApiResult.Success)?.data?.firstOrNull {
            it.client_id == request.client_id || (request.source_message_key != null && it.source_message_key == request.source_message_key)
        } ?: return false
        LedgerLocalStore.complete(tenant, request.client_id, match)
        state.update { it.copy(pending = pendingCount(LedgerLocalStore.load(tenant))) }
        return true
    }

    private fun parseMinor(value: String): Long? = runCatching {
        BigDecimal(value.trim()).movePointRight(2).setScale(0, RoundingMode.HALF_UP).longValueExact()
    }.getOrNull()?.takeIf { it > 0 }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LedgerScreen(onOpenDrawer: (() -> Unit)? = null) {
    val vm: LedgerViewModel = viewModel()
    val state by vm.state.collectAsState()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    var editor by remember { mutableStateOf<LedgerTransaction?>(null) }
    var adding by remember { mutableStateOf(false) }
    val topBarScrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    LaunchedEffect(Unit) { vm.init(); vm.message.collect { snackbar.showSnackbar(it) } }

    Scaffold(
        modifier = Modifier.nestedScroll(topBarScrollBehavior.nestedScrollConnection),
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            MainTopBar(
                "记账",
                navigationIcon = onOpenDrawer?.let { open -> { UserAvatarButton(onClick = open) } },
                scrollBehavior = topBarScrollBehavior,
                actions = {
                    if (state.transactions.isNotEmpty()) {
                        IconButton(onClick = {
                            val sb = StringBuilder()
                            sb.append("时间,类型,金额,币种,分类,商家/对象,状态,备注\n")
                            state.transactions.forEach { t ->
                                val typeStr = if (t.type == "income") "收入" else "支出"
                                val amtStr = String.format(java.util.Locale.US, "%.2f", t.amount_minor / 100.0)
                                val postedStr = if (t.posted) "已入账" else "未入账"
                                val timeStr = t.occurred_at.take(19).replace('T', ' ')
                                val safeNote = t.note.replace(",", "，").replace("\n", " ")
                                val safeMerchant = t.merchant.replace(",", "，")
                                val safeCategory = t.category.replace(",", "，")
                                sb.append("$timeStr,$typeStr,$amtStr,${t.currency},$safeCategory,$safeMerchant,$postedStr,$safeNote\n")
                            }
                            val fileName = "Emailbox-Ledger-${state.month}.csv"
                            FileSharing.shareText(context, sb.toString(), fileName, "text/csv")
                        }) {
                            Icon(Ym1rIcons.Download, contentDescription = "导出账目")
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { adding = true },
                shape = CircleShape,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier
                    .padding(bottom = 80.dp)
                    .size(56.dp),
            ) {
                Icon(Ym1rIcons.Plus, contentDescription = "记一笔", modifier = Modifier.size(28.dp))
            }
        },
    ) { padding ->
        LazyColumn(
            Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 2.dp,
                bottom = 140.dp
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 2.dp, bottom = 2.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                        modifier = Modifier.height(32.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 4.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .appleClickable(pressedScale = 0.82f) { vm.changeMonth(-1) },
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    Ym1rIcons.ChevronLeft,
                                    contentDescription = "上个月",
                                    modifier = Modifier.size(17.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Text(
                                text = state.month.format(DateTimeFormatter.ofPattern("yyyy 年 M 月")),
                                style = MaterialTheme.typography.labelLarge.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    letterSpacing = (-0.2).sp,
                                ),
                                modifier = Modifier.padding(horizontal = 6.dp),
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .appleClickable(pressedScale = 0.82f) { vm.changeMonth(1) },
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    Ym1rIcons.ChevronRight,
                                    contentDescription = "下个月",
                                    modifier = Modifier.size(17.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
            if (state.loading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            item { SummaryCard(state.summary, state.transactions) }
            if (state.pending > 0) item {
                ProductSurface(
                    shape = MaterialTheme.shapes.medium,
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                ) {
                    Text("${state.pending} 笔离线记录等待同步", Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onTertiaryContainer)
                }
            }
            item { Text("最近记录", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 4.dp)) }
            if (state.transactions.isEmpty()) item { EmptyBox("本月还没有账目", Ym1rIcons.FileText) }
            items(state.transactions, key = { it.id }) { item ->
                TransactionRow(
                    item = item,
                    onEdit = { editor = item },
                )
            }
            item { Spacer(Modifier.height(80.dp)) }
        }
    }

    // 时间统一转成带秒的 RFC3339：LocalDateTime.toString() 在整分时会省略秒（…T19:49+08:00），
    // 后端按 RFC3339 解析会直接 400
    if (adding) LedgerEditorDialog(null, onDismiss = { adding = false }, onSave = { type, amount, currency, category, merchant, note, occurredAt, posted ->
        adding = false
        val occurred = runCatching {
            java.time.LocalDateTime.parse(occurredAt, java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
                .atOffset(java.time.OffsetDateTime.now().offset)
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX"))
        }.getOrDefault(occurredAt)
        vm.create(type, amount, currency, category, merchant, note, occurredAt = occurred, posted = posted)
    })
    editor?.let { item ->
        LedgerEditorDialog(item, onDismiss = { editor = null }, onDelete = { editor = null; vm.delete(item) }, onSave = { type, amount, currency, category, merchant, note, occurredAt, posted ->
            editor = null
            val occurred = runCatching {
                java.time.LocalDateTime.parse(occurredAt, java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
                    .atOffset(java.time.OffsetDateTime.now().offset)
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX"))
            }.getOrDefault(item.occurred_at)
            vm.update(item, type, amount, currency, category, merchant, note, occurredAt = occurred, posted = posted)
        })
    }
}

@Composable
private fun SummaryCard(summary: LedgerSummary?, transactions: List<LedgerTransaction> = emptyList()) {
    val currencies = summary?.items?.map { it.currency }?.distinct().orEmpty().ifEmpty { listOf("CNY") }
    ProductSurface(
        shape = MaterialTheme.shapes.large,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(Modifier.fillMaxWidth().padding(20.dp)) {
            currencies.forEachIndexed { index, currency ->
                if (index > 0) HorizontalDivider(Modifier.padding(vertical = 14.dp), color = MaterialTheme.colorScheme.outlineVariant)
                val rows = summary?.items.orEmpty().filter { it.currency == currency }
                val income = rows.filter { it.type == "income" }.sumOf { it.amount_minor }
                val expenseRows = rows.filter { it.type == "expense" }
                val expense = expenseRows.sumOf { it.amount_minor }
                Text("本月结余 · $currency", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                RollingMoney(
                    amountMinor = income - expense,
                    currency = currency,
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth()) {
                    Column(Modifier.weight(1f)) {
                        Text("收入", style = MaterialTheme.typography.labelMedium)
                        RollingMoney(
                            amountMinor = income,
                            currency = currency,
                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    Column(Modifier.weight(1f)) {
                        Text("支出", style = MaterialTheme.typography.labelMedium)
                        RollingMoney(
                            amountMinor = expense,
                            currency = currency,
                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
                // 未入账：邮件识别的建议账单等，不计入上面的结余。
                // 正负口径与结余一致（收入 +、支出 −），排版与字体同「收入」行。
                val pendingRows = transactions.filter { !it.posted && it.currency == currency }
                if (pendingRows.isNotEmpty()) {
                    val pendingNet = pendingRows.sumOf { if (it.type == "income") it.amount_minor else -it.amount_minor }
                    Spacer(Modifier.height(12.dp))
                    Row(Modifier.fillMaxWidth()) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                "未入账",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            RollingMoney(
                                amountMinor = pendingNet,
                                currency = currency,
                                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                if (expense > 0) {
                    Spacer(Modifier.height(14.dp))
                    Text("支出构成", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(6.dp))
                    val topCategories = expenseRows.sortedByDescending { it.amount_minor }.take(5)
                    val segmentPalette = listOf(
                        MaterialTheme.colorScheme.primary,
                        MaterialTheme.colorScheme.secondary,
                        MaterialTheme.colorScheme.tertiary,
                        MaterialTheme.colorScheme.error,
                        MaterialTheme.colorScheme.outline,
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                    ) {
                        topCategories.forEachIndexed { catIndex, catRow ->
                            val weight = (catRow.amount_minor.toFloat() / expense.toFloat()).coerceAtLeast(0.01f)
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .weight(weight)
                                    .background(segmentPalette[catIndex % segmentPalette.size])
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    topCategories.forEachIndexed { catIndex, row ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(segmentPalette[catIndex % segmentPalette.size])
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(row.category, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                            Text(
                                "${formatMoney(row.amount_minor)} · ${(row.amount_minor.toDouble() / expense * 100).roundToInt()}%",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TransactionRow(item: LedgerTransaction, onEdit: () -> Unit) {
    ProductSurface(
        modifier = Modifier
            .fillMaxWidth()
            .appleCombinedClickable(
                pressedScale = 0.97f,
                pressedAlpha = 0.92f,
                onClick = {},
                onLongClick = onEdit,
            ),
    ) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            val iconBg = if (item.type == "income") {
                AppleColors.Green
            } else {
                when (item.category) {
                    "餐饮" -> AppleColors.Orange
                    "交通" -> AppleColors.Blue
                    "购物" -> AppleColors.Pink
                    "订阅" -> AppleColors.Purple
                    "住房" -> AppleColors.Indigo
                    "医疗" -> AppleColors.Red
                    "数码服务" -> AppleColors.Teal
                    "工资" -> AppleColors.Green
                    else -> AppleColors.Gray
                }
            }
            AppleIconSquircle(
                icon = Ym1rIcons.FileText,
                backgroundColor = iconBg,
                size = 36.dp,
                iconSize = 20.dp,
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(item.merchant.ifBlank { item.category }, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                Text("${item.category} · ${item.occurred_at.take(10)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Column(horizontalAlignment = androidx.compose.ui.Alignment.End) {
                Text(
                    (if (item.type == "income") "+" else "−") + formatMoney(item.amount_minor) + " ${item.currency}",
                    fontWeight = FontWeight.SemiBold,
                    color = if (item.type == "income") MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurface,
                )
                if (!item.posted) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "未入账",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
fun LedgerEditorDialog(
    item: LedgerTransaction?,
    onDismiss: () -> Unit,
    onDelete: (() -> Unit)? = null,
    initialAmount: String = "",
    initialCurrency: String = "CNY",
    initialCategory: String = "餐饮",
    initialMerchant: String = "",
    onSave: (String, String, String, String, String, String, String, Boolean) -> Unit,
) {
    val categoryEnabled = Prefs.ledgerCategoryEnabled
    var type by remember { mutableStateOf(item?.type ?: "expense") }
    var amount by remember { mutableStateOf(item?.let { formatMoney(it.amount_minor) } ?: initialAmount) }
    var currency by remember { mutableStateOf(item?.currency ?: initialCurrency) }
    var category by remember {
        mutableStateOf(if (item == null && !categoryEnabled) "其他" else item?.category ?: initialCategory)
    }
    var merchant by remember { mutableStateOf(item?.merchant ?: initialMerchant) }
    var note by remember { mutableStateOf(item?.note.orEmpty()) }
    var posted by remember {
        mutableStateOf(item?.posted ?: true)
    }
    var occurredAt by remember {
        mutableStateOf(
            item?.occurred_at?.take(16)?.replace('T', ' ')
                ?: java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")),
        )
    }
    val timeValid = runCatching {
        java.time.LocalDateTime.parse(occurredAt.trim(), java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
    }.isSuccess
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (item == null) "记一笔" else "编辑账目") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                ) {
                    AppleSegmentedControl(
                        options = listOf("支出", "收入"),
                        selectedIndex = if (type == "expense") 0 else 1,
                        onSelect = { type = if (it == 0) "expense" else "income" },
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(8.dp))
                    // 入账状态按钮：默认已入账，点击可手动切换为未入账
                    FilterChip(
                        selected = posted,
                        onClick = { posted = !posted },
                        leadingIcon = {
                            Icon(
                                if (posted) Ym1rIcons.Check else Ym1rIcons.X,
                                null,
                                Modifier.size(16.dp),
                                tint = if (posted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                            )
                        },
                        label = {
                            Text(
                                if (posted) "已入账" else "未入账",
                                color = if (posted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                            )
                        },
                    )
                }
                ProductField(amount, { amount = it }, label = "金额", modifier = Modifier.fillMaxWidth(), placeholder = "例如 38.00")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf("CNY", "USD", "HKD").forEach { value -> FilterChip(currency == value, { currency = value }, label = { Text(value) }) } }
                if (categoryEnabled) {
                    Row(Modifier.fillMaxWidth().horizontalScroll(androidx.compose.foundation.rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        LEDGER_CATEGORIES.forEach { value -> FilterChip(category == value, { category = value }, label = { Text(value) }) }
                    }
                }
                ProductField(merchant, { merchant = it }, label = "商户 / 来源", modifier = Modifier.fillMaxWidth(), placeholder = "可选")
                ProductField(note, { note = it }, label = "备注", modifier = Modifier.fillMaxWidth(), singleLine = false, placeholder = "可选")
                ProductField(
                    occurredAt,
                    { occurredAt = it },
                    label = "记录时间",
                    modifier = Modifier.fillMaxWidth(),
                    supporting = "格式 yyyy-MM-dd HH:mm，可自定义记账时间",
                    isError = occurredAt.isNotBlank() && !timeValid,
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(type, amount, currency, category, merchant, note, occurredAt.trim(), posted) },
                enabled = amount.isNotBlank() && timeValid,
            ) { Text("保存") }
        },
        dismissButton = {
            Row {
                if (onDelete != null) TextButton(onClick = onDelete) { Icon(Ym1rIcons.Trash2, null); Spacer(Modifier.width(4.dp)); Text("删除") }
                TextButton(onClick = onDismiss) { Text("取消") }
            }
        },
    )
}

private fun formatMoney(minor: Long): String = BigDecimal(minor).movePointLeft(2).setScale(2).toPlainString()
