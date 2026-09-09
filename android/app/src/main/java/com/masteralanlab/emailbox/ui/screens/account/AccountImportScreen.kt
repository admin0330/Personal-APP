package com.masteralanlab.emailbox.ui.screens.account

import android.content.ClipboardManager
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.masteralanlab.emailbox.ui.components.Ym1rIcons
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.masteralanlab.emailbox.data.Prefs
import com.masteralanlab.emailbox.data.remote.ApiResult
import com.masteralanlab.emailbox.data.remote.ImportAccountsRequest
import com.masteralanlab.emailbox.data.remote.ImportDefaults
import com.masteralanlab.emailbox.data.remote.ImportError
import com.masteralanlab.emailbox.data.remote.ImportResult
import com.masteralanlab.emailbox.data.remote.MailGroup
import com.masteralanlab.emailbox.data.remote.apiCall
import com.masteralanlab.emailbox.ui.components.AppTopBar
import com.masteralanlab.emailbox.ui.components.ErrorBox
import com.masteralanlab.emailbox.ui.components.GroupPicker
import com.masteralanlab.emailbox.ui.components.Labels
import com.masteralanlab.emailbox.ui.components.LoadingBox
import com.masteralanlab.emailbox.ui.components.ProductField
import com.masteralanlab.emailbox.ui.components.RadioOptionList
import com.masteralanlab.emailbox.ui.components.SectionTitle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// ---------------------------------------------------------------- 常量

/** 分隔符恒为四个连字符。 */
private const val SEP = "----"

private val FORMAT_OPTIONS: List<Pair<String, String>> = listOf(
    Pair("auto", "自动识别\n按每行的段数判断格式"),
    Pair("outlook_oauth", "Outlook OAuth\n邮箱${SEP}密码${SEP}client_id${SEP}refresh_token"),
    Pair("imap", "IMAP 授权码\n邮箱${SEP}授权码"),
    Pair("custom_imap", "自定义 IMAP\n邮箱${SEP}密码${SEP}imap_host${SEP}imap_port"),
)

private val CONFLICT_OPTIONS: List<Pair<String, String>> = listOf(
    Pair("skip", "跳过\n邮箱已存在时保留原有账号"),
    Pair("update", "覆盖更新\n邮箱已存在时用新数据覆盖"),
)

private val DEFAULT_STATUS_OPTIONS: List<Pair<String, String>> = listOf(
    Pair("active", Labels.accountStatus("active")),
    Pair("disabled", Labels.accountStatus("disabled")),
)

// ---------------------------------------------------------------- 草稿与状态

data class ImportDraft(
    val groupId: String? = null,
    val format: String = "auto",
    val onConflict: String = "skip",
    val content: String = "",
    val clientIdFirst: Boolean = true,
    val advancedExpanded: Boolean = false,
    val imapHost: String = "",
    val imapPort: String = "",
    val defaultRemark: String = "",
    val defaultStatus: String = "active",
)

data class AccountImportUiState(
    val loading: Boolean = true,
    val loadError: String? = null,
    val groups: List<MailGroup> = emptyList(),
    val draft: ImportDraft = ImportDraft(),
    val importing: Boolean = false,
    val error: String? = null,
    val result: ImportResult? = null,
)

// ---------------------------------------------------------------- 数据层

class AccountImportViewModel : ViewModel() {

    private val _state = MutableStateFlow(AccountImportUiState())
    val state: StateFlow<AccountImportUiState> = _state.asStateFlow()

    fun load(groupId: String?) {
        val tenant = Prefs.tenantId
        if (tenant.isNullOrBlank()) {
            _state.update { it.copy(loading = false, loadError = "未选择工作空间") }
            return
        }
        _state.update {
            it.copy(
                loading = true,
                loadError = null,
                draft = it.draft.copy(groupId = groupId),
            )
        }
        viewModelScope.launch {
            when (val r = apiCall { groups(tenant) }) {
                is ApiResult.Success -> _state.update {
                    it.copy(loading = false, groups = r.data)
                }

                is ApiResult.Failure -> _state.update {
                    it.copy(loading = false, loadError = r.message)
                }
            }
        }
    }

    fun updateDraft(mutator: (ImportDraft) -> ImportDraft) {
        _state.update { it.copy(draft = mutator(it.draft), error = null) }
    }

    fun consumeError() = _state.update { it.copy(error = null) }

    /** 剪贴板为空这类本地提示，走和接口错误一样的 snackbar 通道。 */
    fun reportError(message: String) = _state.update { it.copy(error = message) }

    fun dismissResult() = _state.update { it.copy(result = null) }

    /** 追加一行示例；内容为空时直接写入，否则换行追加。 */
    fun appendSample() {
        _state.update { s ->
            val d = s.draft
            val line = sampleLine(d.format)
            val prefix = if (d.content.isBlank() || d.content.endsWith("\n")) "" else "\n"
            s.copy(draft = d.copy(content = d.content + prefix + line))
        }
    }

    fun setContent(text: String) {
        _state.update { s ->
            val d = s.draft
            val prefix = if (d.content.isBlank() || d.content.endsWith("\n")) "" else "\n"
            s.copy(draft = d.copy(content = d.content + prefix + text))
        }
    }

    fun runImport() {
        val s = _state.value
        if (s.importing) return
        val tenant = Prefs.tenantId
        if (tenant.isNullOrBlank()) {
            _state.update { it.copy(error = "未选择工作空间") }
            return
        }
        val d = s.draft
        if (d.content.isBlank()) {
            _state.update { it.copy(error = "请先粘贴或输入要导入的内容") }
            return
        }

        val portRaw = d.imapPort.trim()
        val port = if (portRaw.isBlank()) {
            null
        } else {
            val v = portRaw.toIntOrNull()
            if (v == null || v !in 1..65535) {
                _state.update { it.copy(error = "IMAP 端口必须是 1–65535 的数字") }
                return
            }
            v
        }

        val body = ImportAccountsRequest(
            group_id = d.groupId,
            format = d.format,
            content = d.content,
            on_conflict = d.onConflict,
            client_id_first = d.clientIdFirst,
            imap_host = d.imapHost.trim().ifBlank { null },
            imap_port = port,
            defaults = ImportDefaults(
                remark = d.defaultRemark.trim().ifBlank { null },
                status = d.defaultStatus,
            ),
        )

        viewModelScope.launch {
            _state.update { it.copy(importing = true, error = null) }
            // 超出账号配额的行计入 skipped，HTTP 仍是 200，结果里看 skipped 即可。
            when (val r = apiCall { importAccounts(tenant, body) }) {
                is ApiResult.Success -> _state.update {
                    it.copy(importing = false, result = r.data)
                }

                is ApiResult.Failure -> _state.update {
                    it.copy(importing = false, error = r.message)
                }
            }
        }
    }
}

private fun sampleLine(format: String): String = when (format) {
    "imap" -> "user@qq.com${SEP}授权码"
    "custom_imap" -> "user@example.com${SEP}password${SEP}imap.example.com${SEP}993"
    else -> "user@outlook.com${SEP}password${SEP}client-id${SEP}refresh-token"
}

/** 按换行统计非空行。 */
private fun countLines(content: String): Int =
    content.lineSequence().count { it.isNotBlank() }

// ---------------------------------------------------------------- 页面

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountImportScreen(
    groupId: String?,
    onBack: () -> Unit,
    onDone: () -> Unit,
) {
    val vm: AccountImportViewModel = viewModel()
    val state by vm.state.collectAsStateWithLifecycle()

    LaunchedEffect(groupId) { vm.load(groupId) }

    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(state.error) {
        state.error?.let {
            snackbar.showSnackbar(it)
            vm.consumeError()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = { AppTopBar(title = "批量导入账号", onBack = onBack) },
        bottomBar = {
            if (!state.loading && state.loadError == null) {
                ImportBar(
                    importing = state.importing,
                    lineCount = countLines(state.draft.content),
                    onImport = vm::runImport,
                )
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when {
                state.loading -> LoadingBox(text = "正在加载分组…")

                state.loadError != null -> ErrorBox(
                    message = state.loadError ?: "加载失败",
                    onRetry = { vm.load(groupId) },
                )

                else -> ImportForm(
                    state = state,
                    onDraftChange = { draft -> vm.updateDraft { draft } },
                    onAppendSample = vm::appendSample,
                    onPasteContent = vm::setContent,
                    onNotice = vm::reportError,
                )
            }
        }
    }

    state.result?.let { result ->
        ImportResultDialog(
            result = result,
            onDone = {
                vm.dismissResult()
                onDone()
            },
        )
    }
}

@Composable
private fun ImportBar(
    importing: Boolean,
    lineCount: Int,
    onImport: () -> Unit,
) {
    Surface(tonalElevation = 3.dp) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("已解析 $lineCount 行", style = MaterialTheme.typography.titleSmall)
                Text(
                    "空行不计入，接口上限 8MB",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(12.dp))
            Button(
                onClick = onImport,
                enabled = !importing && lineCount > 0,
                modifier = Modifier.height(52.dp),
            ) {
                if (importing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp,
                    )
                    Spacer(Modifier.width(10.dp))
                }
                Text(if (importing) "导入中…" else "开始导入")
            }
        }
    }
}

// ---------------------------------------------------------------- 表单

@Composable
private fun ImportForm(
    state: AccountImportUiState,
    onDraftChange: (ImportDraft) -> Unit,
    onAppendSample: () -> Unit,
    onPasteContent: (String) -> Unit,
    onNotice: (String) -> Unit,
) {
    val d = state.draft
    val context = LocalContext.current

    fun mutate(block: ImportDraft.() -> ImportDraft) = onDraftChange(d.block())

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 24.dp),
    ) {
        SectionTitle("导入目标")
        Column(Modifier.padding(horizontal = 16.dp)) {
            GroupPicker(
                groups = state.groups,
                selectedId = d.groupId,
                onSelect = { mutate { copy(groupId = it) } },
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                "不指定时落到系统默认分组",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, top = 4.dp),
            )
        }

        SectionTitle("内容格式")
        RadioOptionList(
            options = FORMAT_OPTIONS,
            selected = d.format,
            onSelect = { mutate { copy(format = it) } },
        )
        Text(
            "分隔符恒为四个连字符「$SEP」。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
        )

        SectionTitle("冲突处理")
        RadioOptionList(
            options = CONFLICT_OPTIONS,
            selected = d.onConflict,
            onSelect = { mutate { copy(onConflict = it) } },
        )

        SectionTitle("高级选项")
        ListItem(
            headlineContent = { Text("展开高级选项") },
            supportingContent = {
                Text("client_id 顺序、IMAP 默认值、默认备注与状态")
            },
            trailingContent = {
                Icon(
                    imageVector = if (d.advancedExpanded) {
                        Ym1rIcons.ChevronUp
                    } else {
                        Ym1rIcons.ChevronDown
                    },
                    contentDescription = if (d.advancedExpanded) "收起" else "展开",
                )
            },
            modifier = Modifier.clickable { mutate { copy(advancedExpanded = !advancedExpanded) } },
        )
        if (d.advancedExpanded) {
            Column(Modifier.padding(horizontal = 16.dp)) {
                ListItem(
                    headlineContent = { Text("client_id 在前") },
                    supportingContent = {
                        Text("仅在四段格式中两段都像 UUID（或都不像）时决定顺序")
                    },
                    trailingContent = {
                        Switch(
                            checked = d.clientIdFirst,
                            onCheckedChange = { mutate { copy(clientIdFirst = it) } },
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                HorizontalDivider()
                ProductField(
                    value = d.imapHost,
                    onValueChange = { mutate { copy(imapHost = it) } },
                    label = "IMAP 服务器",
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    supporting = "留空则按服务商默认值",
                )
                Spacer(Modifier.height(12.dp))
                ProductField(
                    value = d.imapPort,
                    onValueChange = { mutate { copy(imapPort = it) } },
                    label = "IMAP 端口",
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    supporting = "留空则按服务商默认值（993）",
                )
                Spacer(Modifier.height(12.dp))
                ProductField(
                    value = d.defaultRemark,
                    onValueChange = { mutate { copy(defaultRemark = it) } },
                    label = "默认备注",
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    supporting = "写入这批导入账号的 remark",
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    "默认状态",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                RadioOptionList(
                    options = DEFAULT_STATUS_OPTIONS,
                    selected = d.defaultStatus,
                    onSelect = { mutate { copy(defaultStatus = it) } },
                )
            }
        }

        SectionTitle("导入内容")
        Column(Modifier.padding(horizontal = 16.dp)) {
            Row(Modifier.fillMaxWidth()) {
                TextButton(onClick = {
                    val manager = context.getSystemService(ClipboardManager::class.java)
                    val text = manager?.primaryClip
                        ?.takeIf { it.itemCount > 0 }
                        ?.getItemAt(0)
                        ?.text
                        ?.toString()
                        ?.trim()
                        .orEmpty()
                    if (text.isBlank()) {
                        onNotice("剪贴板里没有文本内容")
                    } else {
                        onPasteContent(text)
                    }
                }) {
                    Icon(Ym1rIcons.Copy, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("从剪贴板粘贴")
                }
                TextButton(onClick = onAppendSample) {
                    Text("插入示例行")
                }
            }
            ProductField(
                value = d.content,
                onValueChange = { mutate { copy(content = it) } },
                label = "账号内容",
                placeholder = "每行一个账号",
                modifier = Modifier.fillMaxWidth().heightIn(min = 200.dp),
                minLines = 8,
                singleLine = false,
                supporting = "每行一个账号，当前格式：${sampleLine(d.format)}",
            )
        }
    }
}

// ---------------------------------------------------------------- 结果对话框

@Composable
private fun ImportResultDialog(result: ImportResult, onDone: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDone,
        title = { Text("导入完成") },
        text = {
            Column {
                Row(Modifier.fillMaxWidth()) {
                    StatCell("总数", result.total, Modifier.weight(1f))
                    StatCell("新建", result.created, Modifier.weight(1f))
                    StatCell("更新", result.updated, Modifier.weight(1f))
                    StatCell("跳过", result.skipped, Modifier.weight(1f))
                    StatCell("失败", result.failed, Modifier.weight(1f))
                }
                Text(
                    "跳过包含邮箱重复与超出账号配额的行。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp),
                )

                if (result.errors.isNotEmpty()) {
                    HorizontalDivider(Modifier.padding(vertical = 10.dp))
                    Text(
                        "失败明细",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    if (result.truncated) {
                        Text(
                            "错误较多，仅显示前 200 条",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    LazyColumn(Modifier.heightIn(max = 260.dp)) {
                        itemsIndexed(result.errors) { _, e ->
                            ErrorLine(e)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDone) { Text("完成") }
        },
    )
}

@Composable
private fun StatCell(label: String, value: Int, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            value.toString(),
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
        )
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

@Composable
private fun ErrorLine(e: ImportError) {
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(
            "第 ${e.line} 行 · ${e.email.ifBlank { "—" }}",
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            e.reason.ifBlank { "未知原因" },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
    }
}
