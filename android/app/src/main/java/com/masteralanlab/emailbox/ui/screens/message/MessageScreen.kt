package com.masteralanlab.emailbox.ui.screens.message

import android.annotation.SuppressLint
import android.content.Context
import android.view.ViewGroup
import android.webkit.WebView
import java.io.File
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Attachment
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Drafts
import androidx.compose.material.icons.outlined.FolderZip
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.MarkEmailRead
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.masteralanlab.emailbox.data.Prefs
import com.masteralanlab.emailbox.data.SecureMailCache
import com.masteralanlab.emailbox.data.MailIntelligence
import com.masteralanlab.emailbox.data.MailCategory
import com.masteralanlab.emailbox.data.remote.ApiClient
import com.masteralanlab.emailbox.data.remote.ApiResult
import com.masteralanlab.emailbox.data.remote.Attachment
import com.masteralanlab.emailbox.data.remote.MessageBatchRequest
import com.masteralanlab.emailbox.data.remote.MessageDetail
import com.masteralanlab.emailbox.data.remote.MessageRef
import com.masteralanlab.emailbox.data.remote.apiCall
import com.masteralanlab.emailbox.data.remote.requireSuccessful
import com.masteralanlab.emailbox.ui.components.AppTopBar
import com.masteralanlab.emailbox.ui.components.ErrorBox
import com.masteralanlab.emailbox.ui.components.Labels
import com.masteralanlab.emailbox.ui.components.LoadingBox
import com.masteralanlab.emailbox.util.FileSharing
import com.masteralanlab.emailbox.util.formatBytes
import com.masteralanlab.emailbox.util.formatFullTime
import com.masteralanlab.emailbox.util.displayName
import com.masteralanlab.emailbox.ui.screens.ledger.LedgerEditorDialog
import com.masteralanlab.emailbox.ui.screens.ledger.LedgerViewModel
import java.math.BigDecimal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class MessageState(
    val loading: Boolean = true,
    val detail: MessageDetail? = null,
    val error: String? = null,
    val acting: Boolean = false,
    val allowImages: Boolean = false,
)

class MessageViewModel : ViewModel() {

    private val _state = MutableStateFlow(MessageState())
    val state: MutableStateFlow<MessageState> get() = _state

    private val _message = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val message = _message.asSharedFlow()

    var allowImages by mutableStateOf(!Prefs.blockRemoteImages)
        private set

    fun updateAllowImages(v: Boolean) {
        allowImages = v
        Prefs.blockRemoteImages = !v
    }

    fun load(accountId: String, messageId: String, folder: String, idMode: String) {
        viewModelScope.launch {
            val tenant = Prefs.tenantId
            if (tenant.isNullOrBlank()) {
                _state.update { it.copy(loading = false, error = "未选择工作空间") }
                return@launch
            }
            val resolvedFolder = folder.ifBlank { "inbox" }
            val cached = SecureMailCache.detail(tenant, accountId, resolvedFolder, idMode, messageId)
            _state.update { it.copy(loading = cached == null, detail = cached, error = null) }
            when (val r = apiCall {
                messageDetail(
                    tenant,
                    accountId,
                    messageId,
                    folder = resolvedFolder,
                    idMode = idMode.ifBlank { null },
                )
            }) {
                is ApiResult.Success -> {
                    SecureMailCache.putDetail(tenant, accountId, r.data)
                    _state.update { it.copy(loading = false, detail = r.data) }
                }
                is ApiResult.Failure -> _state.update {
                    it.copy(loading = false, error = if (cached == null) r.message else null)
                }
            }
        }
    }

    private fun refs(detail: MessageDetail) =
        listOf(MessageRef(detail.id, detail.id_mode, detail.folder))

    fun markRead(accountId: String) = viewModelScope.launch {
        if (Prefs.apiKeyMode) return@launch
        val tenant = Prefs.tenantId ?: return@launch
        val d = _state.value.detail ?: return@launch
        _state.update { it.copy(acting = true) }
        when (val r = apiCall { markRead(tenant, accountId, MessageBatchRequest(refs(d))) }) {
            is ApiResult.Success -> _message.tryEmit("已标记为已读")
            is ApiResult.Failure -> _message.tryEmit(r.message)
        }
        _state.update { it.copy(acting = false) }
    }

    fun delete(accountId: String, onDeleted: () -> Unit) = viewModelScope.launch {
        if (Prefs.apiKeyMode) return@launch
        val tenant = Prefs.tenantId ?: return@launch
        val d = _state.value.detail ?: return@launch
        _state.update { it.copy(acting = true) }
        when (val r = apiCall { deleteMessages(tenant, accountId, MessageBatchRequest(refs(d))) }) {
            is ApiResult.Success -> {
                _message.tryEmit("邮件已删除")
                onDeleted()
            }

            is ApiResult.Failure -> _message.tryEmit(r.message)
        }
        _state.update { it.copy(acting = false) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessageScreen(
    accountId: String,
    messageId: String,
    folder: String,
    idMode: String,
    subject: String,
    onBack: () -> Unit,
) {
    val vm: MessageViewModel = viewModel()
    val ledgerVm: LedgerViewModel = viewModel(key = "message-ledger")
    val state by vm.state.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val readOnly = Prefs.apiKeyMode
    var confirmDelete by remember { mutableStateOf(false) }
    var downloading by remember { mutableStateOf<String?>(null) }
    var showLedgerDraft by remember { mutableStateOf(false) }
    var showCategoryPicker by remember { mutableStateOf(false) }
    var localCategory by remember(messageId) { mutableStateOf<String?>(null) }

    LaunchedEffect(messageId) { vm.load(accountId, messageId, folder, idMode) }
    LaunchedEffect(vm) { vm.message.collect { snackbar.showSnackbar(it) } }
    LaunchedEffect(ledgerVm) { ledgerVm.message.collect { snackbar.showSnackbar(it) } }

    fun downloadAttachment(att: Attachment) {
        scope.launch {
            downloading = att.name
            runCatching {
                withContext(Dispatchers.IO) {
                    fetchAttachment(context, accountId, messageId, folder, idMode, att.id, att.name)
                }
            }.onSuccess { file ->
                FileSharing.view(context, file, att.content_type.ifBlank { FileSharing.guessMime(att.name) })
            }.onFailure { e ->
                snackbar.showSnackbar(e.message ?: "下载失败")
            }
            downloading = null
        }
    }

    fun downloadZip() {
        scope.launch {
            downloading = "全部附件"
            runCatching {
                withContext(Dispatchers.IO) {
                    fetchAttachmentZip(context, accountId, messageId, folder, idMode)
                }
            }.onSuccess { file ->
                FileSharing.share(context, file, "application/zip")
            }.onFailure { e ->
                snackbar.showSnackbar(e.message ?: "下载失败")
            }
            downloading = null
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            AppTopBar(
                title = subject.ifBlank { "（无主题）" },
                onBack = onBack,
                actions = {
                    state.detail?.let {
                        if (!readOnly && !it.is_read) {
                            IconButton(onClick = { vm.markRead(accountId) }, enabled = !state.acting) {
                                Icon(Icons.Outlined.MarkEmailRead, contentDescription = "标记已读")
                            }
                        }
                        if (!readOnly) {
                            IconButton(onClick = { confirmDelete = true }, enabled = !state.acting) {
                                Icon(Icons.Outlined.Delete, contentDescription = "删除")
                            }
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            when {
                state.loading -> LoadingBox(text = "正在读取邮件…")
                state.error != null -> ErrorBox(state.error!!) { vm.load(accountId, messageId, folder, idMode) }
                state.detail == null -> ErrorBox("邮件不存在或已被移动") { vm.load(accountId, messageId, folder, idMode) }
                else -> {
                    val detail = state.detail!!
                    val item = com.masteralanlab.emailbox.data.remote.MessageItem(
                        detail.id, detail.id_mode, detail.folder, detail.subject, detail.from, detail.to, detail.cc,
                        detail.received_at, detail.is_read, detail.has_attachments, detail.body_preview,
                    )
                    val insight = remember(detail, localCategory) { MailIntelligence.analyze(item, detail.body, localCategory ?: Prefs.categoryOverride(detail.from)) }
                    MessageHeader(detail, insight.category) { showCategoryPicker = true }

                    if (detail.attachments.isNotEmpty()) {
                        AttachmentBar(
                            attachments = detail.attachments,
                            downloading = downloading,
                            onOpen = { downloadAttachment(it) },
                            onZip = { downloadZip() },
                        )
                    }

                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        FilterChip(
                            selected = vm.allowImages,
                            onClick = { vm.updateAllowImages(!vm.allowImages) },
                            label = { Text("远程图片") },
                            leadingIcon = {
                                Icon(Icons.Outlined.Image, null, Modifier.size(16.dp))
                            },
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (vm.allowImages) "已允许加载远程图片" else "默认阻断，防止追踪像素",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        insight.money?.let {
                            Spacer(Modifier.width(8.dp))
                            FilterChip(
                                selected = false,
                                onClick = { showLedgerDraft = true },
                                label = { Text("记入账本") },
                            )
                        }
                    }

                    MessageBody(
                        html = detail.body,
                        isHtml = detail.body_type == "html",
                        allowImages = vm.allowImages,
                        modifier = Modifier.fillMaxSize().weight(1f),
                    )
                }
            }
        }
    }

    if (confirmDelete && !readOnly) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("删除这封邮件？") },
            text = { Text("会直接从上游服务器删除，无法恢复。") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    vm.delete(accountId, onBack)
                }) { Text("删除") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("取消") } },
        )
    }

    val detail = state.detail
    if (showLedgerDraft && detail != null) {
        val item = com.masteralanlab.emailbox.data.remote.MessageItem(
            detail.id, detail.id_mode, detail.folder, detail.subject, detail.from, detail.to, detail.cc,
            detail.received_at, detail.is_read, detail.has_attachments, detail.body_preview,
        )
        val insight = MailIntelligence.analyze(item, detail.body, Prefs.categoryOverride(detail.from))
        val money = insight.money
        LedgerEditorDialog(
            item = null,
            onDismiss = { showLedgerDraft = false },
            initialAmount = money?.let { BigDecimal(it.amountMinor).movePointLeft(2).setScale(2).toPlainString() }.orEmpty(),
            initialCurrency = money?.currency ?: "CNY",
            initialCategory = if (insight.category == MailCategory.SUBSCRIPTION) "订阅" else "其他",
            initialMerchant = displayName(detail.from),
            onSave = { type, amount, currency, category, merchant, note, occurredAt, posted ->
                showLedgerDraft = false
                val sourceKey = listOf(Prefs.tenantId, accountId, detail.folder, detail.id_mode, detail.id).joinToString(":")
                // 邮件识别的账单默认未入账，用户确认后手动切换
                ledgerVm.create(type, amount, currency, category, merchant, note, "email", sourceKey, occurredAt, posted)
            },
        )
    }

    if (showCategoryPicker && detail != null) {
        AlertDialog(
            onDismissRequest = { showCategoryPicker = false },
            title = { Text("设置本地分类") },
            text = {
                Column {
                    Text("仅改变 Android 本地标签，可选择以后将同一发件人的邮件都这样分类。", style = MaterialTheme.typography.bodySmall)
                    MailCategory.all.forEach { category ->
                        TextButton(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = {
                                localCategory = category
                                Prefs.setSenderCategoryRule(detail.from, category)
                                SecureMailCache.putDetail(Prefs.tenantId.orEmpty(), accountId, detail)
                                showCategoryPicker = false
                            },
                        ) { Text(category, Modifier.fillMaxWidth()) }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { showCategoryPicker = false }) { Text("取消") } },
        )
    }
}

@Composable
private fun MessageHeader(d: MessageDetail, category: String, onCategory: () -> Unit) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(
            d.subject.ifBlank { "（无主题）" },
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(d.from, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Text(
                formatFullTime(d.received_at),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (d.to.isNotBlank()) {
            Text(
                "收件人：${d.to}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (d.cc.isNotBlank()) {
            Text(
                "抄送：${d.cc}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            FilterChip(
                                selected = false,
                                onClick = {},
                                enabled = false,
                                label = { Text(folderLabel(d.folder)) },
                            )
             if (!d.is_read) {
                FilterChip(selected = true, onClick = {}, label = { Text("未读") })
            }
            FilterChip(selected = false, onClick = onCategory, label = { Text(category) })
        }
        Spacer(Modifier.height(4.dp))
        androidx.compose.material3.HorizontalDivider()
    }
}

private fun folderLabel(f: String) = when (f) {
    "inbox" -> "收件箱"
    "junkemail" -> "垃圾邮件"
    "deleteditems" -> "已删除"
    "all" -> "全部"
    else -> f
}

@Composable
private fun AttachmentBar(
    attachments: List<Attachment>,
    downloading: String?,
    onOpen: (Attachment) -> Unit,
    onZip: () -> Unit,
) {
    Column(Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Attachment, null, Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(
                "附件 ${attachments.size} 个",
                style = MaterialTheme.typography.labelLarge,
            )
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onZip, enabled = downloading == null) {
                Icon(Icons.Outlined.FolderZip, null, Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("打包下载")
            }
        }
        Spacer(Modifier.height(4.dp))
        attachments.forEach { att ->
            Surface(
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                onClick = { if (downloading == null) onOpen(att) },
            ) {
                Row(
                    Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Outlined.Attachment,
                        null,
                        Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(att.name, maxLines = 1, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            formatBytes(att.size),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (downloading == att.name) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    }
                }
            }
        }
    }
}

/**
 * 邮件正文渲染。
 * 与网页端一致：默认阻断远程图片与脚本，只允许白名单内的基础样式。
 * 明暗适配：WebView 底色与应用主题同步；纯文本邮件注入主题的前景/背景色，
 * HTML 邮件保留自身样式（绝大多数邮件自带白底卡片，深色下以卡片形式呈现）。
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun MessageBody(
    html: String,
    isHtml: Boolean,
    allowImages: Boolean,
    modifier: Modifier = Modifier,
) {
    if (html.isBlank()) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                "这封邮件没有正文",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    val dark = com.masteralanlab.emailbox.ui.theme.rememberDarkTheme()
    val surfaceArgb = MaterialTheme.colorScheme.background.toArgb()
    val content = remember(html, isHtml, dark) {
        if (isHtml) html else plainToHtml(html, dark)
    }

    AndroidView(
        modifier = modifier.fillMaxWidth(),
        factory = { ctx ->
            WebView(ctx).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
                settings.apply {
                    javaScriptEnabled = false
                    domStorageEnabled = false
                    allowFileAccess = false
                    allowContentAccess = false
                    setSupportZoom(true)
                    builtInZoomControls = true
                    displayZoomControls = false
                    loadWithOverviewMode = true
                    useWideViewPort = true
                }
            }
        },
        update = { web ->
            web.setBackgroundColor(surfaceArgb)
            web.settings.loadsImagesAutomatically = allowImages
            web.settings.blockNetworkLoads = !allowImages
            web.loadDataWithBaseURL(null, content, "text/html", "UTF-8", null)
        },
    )
}

private suspend fun fetchAttachment(
    context: Context,
    accountId: String,
    messageId: String,
    folder: String,
    idMode: String,
    attachmentId: String,
    fileName: String,
): File {
    val tenant = Prefs.tenantId ?: error("未选择工作空间")
    val resp = ApiClient.service().attachment(
        tenant,
        accountId,
        messageId,
        attachmentId,
        folder = folder.ifBlank { "inbox" },
        idMode = idMode.ifBlank { null },
    )
    requireSuccessful(resp)
    val body = resp.body() ?: error("下载失败：空响应")
    return FileSharing.save(context, body, fileName)
}

private suspend fun fetchAttachmentZip(
    context: Context,
    accountId: String,
    messageId: String,
    folder: String,
    idMode: String,
): File {
    val tenant = Prefs.tenantId ?: error("未选择工作空间")
    val resp = ApiClient.service().attachmentsZip(
        tenant,
        accountId,
        messageId,
        folder = folder.ifBlank { "inbox" },
        idMode = idMode.ifBlank { null },
    )
    requireSuccessful(resp)
    val body = resp.body() ?: error("下载失败：空响应")
    val name = resp.headers()["Content-Disposition"]
        ?.substringAfter("filename*=UTF-8''", "")
        ?.takeIf { it.isNotBlank() }
        ?.let { runCatching { java.net.URLDecoder.decode(it, "UTF-8") }.getOrNull() }
        ?: "attachments.zip"
    return FileSharing.save(context, body, name)
}

private fun plainToHtml(text: String, dark: Boolean): String {
    val escaped = text
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
    // 与 Material 3 深浅色基线一致，避免系统 WebView 默认白底在深色主题下刺眼
    val bg = if (dark) "#1C1B1F" else "#FFFFFF"
    val fg = if (dark) "#E6E1E5" else "#1C1B1F"
    return """<html><head><meta name="viewport" content="width=device-width, initial-scale=1"/>
        |<style>
        | body { font-family: sans-serif; font-size: 15px; line-height: 1.6; margin: 12px;
        |        word-wrap: break-word; background: $bg; color: $fg; }
        |</style></head><body><pre style="white-space:pre-wrap;font-family:sans-serif;">$escaped</pre></body></html>"""
        .trimMargin()
}
