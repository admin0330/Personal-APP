package com.masteralanlab.emailbox.ui.screens.message

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import java.io.File
import androidx.compose.animation.AnimatedContent
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import com.masteralanlab.emailbox.ui.components.Ym1rIcons
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.AnnotatedString
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
import com.masteralanlab.emailbox.data.MailInsight
import com.masteralanlab.emailbox.data.MailTranslation
import com.masteralanlab.emailbox.data.remote.ApiClient
import com.masteralanlab.emailbox.data.remote.ApiResult
import com.masteralanlab.emailbox.data.remote.Attachment
import com.masteralanlab.emailbox.data.remote.MessageBatchRequest
import com.masteralanlab.emailbox.data.remote.MessageDetail
import com.masteralanlab.emailbox.data.remote.MessageItem
import com.masteralanlab.emailbox.data.remote.MessageRef
import com.masteralanlab.emailbox.data.remote.apiCall
import com.masteralanlab.emailbox.data.remote.presentableErrorMessage
import com.masteralanlab.emailbox.data.remote.requireSuccessful
import com.masteralanlab.emailbox.ui.components.ErrorBox
import com.masteralanlab.emailbox.ui.components.InitialAvatar
import com.masteralanlab.emailbox.ui.components.InfoRow
import com.masteralanlab.emailbox.ui.components.LoadingBox
import com.masteralanlab.emailbox.ui.components.StatusChip
import com.masteralanlab.emailbox.ui.components.Ym1rCard
import com.masteralanlab.emailbox.util.FileSharing
import com.masteralanlab.emailbox.util.formatBytes
import com.masteralanlab.emailbox.util.formatFullTime
import com.masteralanlab.emailbox.util.formatShortTime
import com.masteralanlab.emailbox.util.emailAddress
import com.masteralanlab.emailbox.util.displayName
import com.masteralanlab.emailbox.util.initialOf
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal fun isOtpCopied(code: String, copiedCode: String?): Boolean =
    code.isNotBlank() && code == copiedCode

data class MessageState(
    val loading: Boolean = true,
    val detail: MessageDetail? = null,
    val insight: MailInsight? = null,
    val error: String? = null,
    val acting: Boolean = false,
)

private fun MessageDetail.toMessageItem() = MessageItem(
    id = id,
    id_mode = id_mode,
    folder = folder,
    subject = subject,
    from = from,
    to = to,
    cc = cc,
    received_at = received_at,
    is_read = is_read,
    has_attachments = has_attachments,
    body_preview = body_preview,
)

class MessageViewModel : ViewModel() {

    private val _state = MutableStateFlow(MessageState())
    val state: MutableStateFlow<MessageState> get() = _state

    private val _message = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val message = _message.asSharedFlow()

    private var loadJob: Job? = null

    fun load(accountId: String, messageId: String, folder: String, idMode: String) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            val tenant = Prefs.tenantId
            if (tenant.isNullOrBlank()) {
                _state.update { it.copy(loading = false, error = "未选择工作空间") }
                return@launch
            }
            val resolvedFolder = folder.ifBlank { "inbox" }
            val cached = withContext(Dispatchers.IO) {
                SecureMailCache.detail(tenant, accountId, resolvedFolder, idMode, messageId)
            }
            // 正文先显示；分类与验证码分析不应延迟已缓存邮件的首屏。
            _state.update { it.copy(loading = cached == null, detail = cached, insight = null, error = null) }
            val cachedInsight = cached?.let { detail ->
                withContext(Dispatchers.Default) {
                    MailIntelligence.analyze(
                        detail.toMessageItem(),
                        detail.body,
                        Prefs.categoryOverride(detail.from),
                    )
                }
            }
            _state.update { it.copy(insight = cachedInsight) }
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
                    _state.update { it.copy(loading = false, detail = r.data, insight = null, error = null) }
                    val insight = withContext(Dispatchers.Default) {
                        MailIntelligence.analyze(
                            r.data.toMessageItem(),
                            r.data.body,
                            Prefs.categoryOverride(r.data.from),
                        )
                    }
                    _state.update { it.copy(loading = false, detail = r.data, insight = insight) }
                    withContext(Dispatchers.IO) {
                        SecureMailCache.putDetail(tenant, accountId, r.data, insight)
                    }
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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class)
@Composable
fun MessageScreen(
    accountId: String,
    messageId: String,
    folder: String,
    idMode: String,
    subject: String,
    onBack: () -> Unit,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
) {
    val vm: MessageViewModel = viewModel()
    val state by vm.state.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val readOnly = Prefs.apiKeyMode
    var confirmDelete by remember { mutableStateOf(false) }
    var downloading by remember { mutableStateOf<String?>(null) }
    var showCategoryPicker by remember { mutableStateOf(false) }
    var showMoreMenu by remember { mutableStateOf(false) }
    var localCategory by remember(messageId) { mutableStateOf<String?>(null) }
    var allowImages by remember(messageId) { mutableStateOf(!Prefs.blockRemoteImages) }
    var originalLayout by remember(messageId) { mutableStateOf(false) }
    var englishIdentification by remember(messageId) { mutableStateOf<com.masteralanlab.emailbox.data.EnglishIdentification?>(null) }
    var translatedBody by remember(messageId) { mutableStateOf<String?>(null) }
    var showTranslated by remember(messageId) { mutableStateOf(false) }
    var translationBusy by remember(messageId) { mutableStateOf(false) }
    var translationError by remember(messageId) { mutableStateOf<String?>(null) }
    var showTranslationPrompt by remember(messageId) { mutableStateOf(false) }
    var showOriginalMail by remember(messageId) { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current

    LaunchedEffect(accountId, messageId, folder, idMode) { vm.load(accountId, messageId, folder, idMode) }
    LaunchedEffect(vm) { vm.message.collect { snackbar.showSnackbar(it) } }
    LaunchedEffect(state.detail?.id, state.detail?.body) {
        englishIdentification = null
        translatedBody = null
        showTranslated = false
        translationError = null
        val body = state.detail?.body ?: return@LaunchedEffect
        val current = state.detail
        if (current != null) {
            translatedBody = SecureMailCache.translation(
                Prefs.tenantId.orEmpty(), accountId, current.folder, current.id_mode, current.id, body,
            )?.text
        }
        englishIdentification = runCatching {
            withContext(Dispatchers.Default) { MailTranslation.identifyEnglish(body) }
        }.getOrNull()
    }

    fun startTranslation() {
        val source = englishIdentification?.candidate?.text
            ?: state.detail?.body?.let(MailIntelligence::cleanVisibleText).orEmpty()
        if (source.isBlank()) return
        translationBusy = true
        translationError = null
        scope.launch {
            try {
                val result = withContext(Dispatchers.Default) {
                    MailTranslation.translateEnglishToChinese(source)
                }
                translatedBody = result
                state.detail?.let { current ->
                    Prefs.tenantId?.let { tenant ->
                        withContext(Dispatchers.IO) {
                            SecureMailCache.putTranslation(
                                tenant, accountId, current.folder, current.id_mode, current.id,
                                current.body, result,
                            )
                        }
                    }
                }
                showTranslated = true
                showOriginalMail = false
                originalLayout = false
                snackbar.showSnackbar("译文已生成")
            } catch (e: Exception) {
                translationError = presentableErrorMessage(e.message).ifBlank { "翻译失败，请稍后重试" }
            } finally {
                translationBusy = false
            }
        }
    }

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
                snackbar.showSnackbar(presentableErrorMessage(e.message).ifBlank { "下载失败" })
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
                snackbar.showSnackbar(presentableErrorMessage(e.message).ifBlank { "下载失败" })
            }
            downloading = null
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("邮件") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Ym1rIcons.ArrowLeft, contentDescription = "返回")
                    }
                },
                actions = {
                    state.detail?.let {
                        if (!readOnly && !it.is_read) {
                            IconButton(onClick = { vm.markRead(accountId) }, enabled = !state.acting) {
                                Icon(Ym1rIcons.Check, contentDescription = "标记已读")
                            }
                        }
                        if (!readOnly) {
                            IconButton(onClick = { confirmDelete = true }, enabled = !state.acting) {
                                Icon(Ym1rIcons.Trash2, contentDescription = "删除")
                            }
                        }
                    }
                    Box {
                        IconButton(onClick = { showMoreMenu = true }) {
                            Icon(Ym1rIcons.MoreVertical, contentDescription = "更多")
                        }
                        DropdownMenu(
                            expanded = showMoreMenu,
                            onDismissRequest = { showMoreMenu = false },
                        ) {
                            if (state.detail != null) {
                                DropdownMenuItem(
                                    text = { Text("适应屏幕") },
                                    onClick = {
                                        originalLayout = false
                                        showMoreMenu = false
                                    },
                                    trailingIcon = if (!originalLayout) {
                                        { Text("✓", color = MaterialTheme.colorScheme.primary) }
                                    } else null,
                                )
                                DropdownMenuItem(
                                    text = { Text("原始排版") },
                                    onClick = {
                                        originalLayout = true
                                        showMoreMenu = false
                                    },
                                    trailingIcon = if (originalLayout) {
                                        { Text("✓", color = MaterialTheme.colorScheme.primary) }
                                    } else null,
                                )
                                DropdownMenuItem(
                                    text = { Text("设置本地分类") },
                                    onClick = {
                                        showCategoryPicker = true
                                        showMoreMenu = false
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("翻译正文") },
                                    onClick = {
                                        showTranslationPrompt = true
                                        showMoreMenu = false
                                    },
                                )
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
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
                    val insight = state.insight?.let { stored ->
                        localCategory?.let { category -> stored.copy(category = category) } ?: stored
                    }
                    MessageHeader(
                        d = detail,
                        category = insight?.category ?: MailCategory.OTHER,
                        expanded = showOriginalMail,
                        onToggleExpanded = {
                            showOriginalMail = !showOriginalMail
                            originalLayout = showOriginalMail
                            if (showOriginalMail) showTranslated = false
                        },
                        sharedTransitionScope = sharedTransitionScope,
                        animatedVisibilityScope = animatedVisibilityScope,
                    )

                    TextButton(
                        onClick = {
                            showOriginalMail = !showOriginalMail
                            originalLayout = showOriginalMail
                            if (showOriginalMail) showTranslated = false
                        },
                        modifier = Modifier.padding(horizontal = 16.dp),
                    ) {
                        Text(if (showOriginalMail) "收起原始邮件" else "查看原始邮件")
                    }

                    insight?.otp?.let { code ->
                        OtpCard(code) {
                            val copied = runCatching {
                                clipboard.setText(AnnotatedString(code.filter(Char::isLetterOrDigit)))
                            }.isSuccess
                            scope.launch {
                                snackbar.showSnackbar(if (copied) "验证码已复制" else "复制失败，请重试")
                            }
                            copied
                        }
                    }

                    if (detail.attachments.isNotEmpty()) {
                        AttachmentBar(
                            attachments = detail.attachments,
                            downloading = downloading,
                            onOpen = { downloadAttachment(it) },
                            onZip = { downloadZip() },
                        )
                    }

                    if (!allowImages) {
                        PrivacyNotice { allowImages = true }
                    }

                    if (englishIdentification != null || translatedBody != null || translationBusy || translationError != null) {
                        TranslationBanner(
                            loading = translationBusy,
                            translated = translatedBody != null,
                            showingTranslation = showTranslated,
                            error = translationError,
                            onTranslate = { showTranslationPrompt = true },
                            onToggle = {
                                showOriginalMail = false
                                originalLayout = false
                                showTranslated = it
                            },
                        )
                    }

                    if (showTranslated && translatedBody != null && !showOriginalMail) {
                        TranslatedBody(translatedBody!!, Modifier.fillMaxWidth().weight(1f))
                    } else {
                        MessageBody(
                            html = detail.body,
                            isHtml = detail.body_type.equals("html", ignoreCase = true),
                            allowImages = allowImages,
                            originalLayout = originalLayout,
                            modifier = Modifier.fillMaxWidth().weight(1f),
                        )
                    }
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
    if (showTranslationPrompt && detail != null) {
        AlertDialog(
            onDismissRequest = { if (!translationBusy) showTranslationPrompt = false },
            title = { Text("翻译正文") },
            text = {
                Text(
                    "使用设备上的英语→中文模型翻译可见正文。首次使用需要下载模型，默认仅在 Wi‑Fi 下进行；原始邮件始终保留。",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showTranslationPrompt = false
                        startTranslation()
                    },
                    enabled = !translationBusy,
                ) { Text("下载并翻译") }
            },
            dismissButton = {
                TextButton(onClick = { showTranslationPrompt = false }, enabled = !translationBusy) {
                    Text("取消")
                }
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
                                scope.launch(Dispatchers.IO) {
                                    SecureMailCache.putDetail(Prefs.tenantId.orEmpty(), accountId, detail)
                                }
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

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun MessageHeader(
    d: MessageDetail,
    category: String,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    sharedTransitionScope: SharedTransitionScope?,
    animatedVisibilityScope: AnimatedVisibilityScope?,
) {
    val address = emailAddress(d.from).ifBlank { d.from }
    val shortTime = formatShortTime(d.received_at)
    val sharedModifier = if (sharedTransitionScope != null && animatedVisibilityScope != null) {
        with(sharedTransitionScope) {
            Modifier.sharedBounds(
                rememberSharedContentState(key = "mail-message:${d.id}:${d.id_mode}"),
                animatedVisibilityScope = animatedVisibilityScope,
            )
        }
    } else {
        Modifier
    }

    Column(
        sharedModifier
            .fillMaxWidth()
            .animateContentSize()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        StatusChip(
            text = category,
            container = MaterialTheme.colorScheme.primaryContainer,
            content = MaterialTheme.colorScheme.onPrimaryContainer,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            d.subject.ifBlank { "（无主题）" },
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Medium,
        )
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            InitialAvatar(initialOf(d.from), modifier = Modifier.size(40.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    displayName(d.from).ifBlank { "未知发件人" },
                    maxLines = 1,
                    style = MaterialTheme.typography.titleSmall,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
                Text(
                    listOf(address, shortTime).filter { it.isNotBlank() }.joinToString(" · "),
                    maxLines = 1,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = onToggleExpanded) {
                Icon(
                    if (expanded) Ym1rIcons.ChevronUp else Ym1rIcons.ChevronDown,
                    contentDescription = if (expanded) "收起邮件详情" else "展开邮件详情",
                )
            }
        }
        AnimatedVisibility(visible = expanded) {
            Column(Modifier.padding(start = 52.dp, top = 4.dp)) {
                if (d.from.isNotBlank()) InfoRow("发件人", d.from)
                if (d.to.isNotBlank()) InfoRow("收件人", d.to)
                if (d.cc.isNotBlank()) InfoRow("抄送", d.cc)
                formatFullTime(d.received_at).takeIf { it.isNotBlank() }?.let { InfoRow("完整时间", it) }
                InfoRow("邮件夹", folderLabel(d.folder))
                if (!d.is_read) {
                    Text(
                        "未读",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(vertical = 4.dp),
                    )
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        androidx.compose.material3.HorizontalDivider()
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun OtpCard(code: String, onCopy: () -> Boolean) {
    var copiedCode by remember(code) { mutableStateOf<String?>(null) }
    var resetJob by remember(code) { mutableStateOf<Job?>(null) }
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    DisposableEffect(code) {
        onDispose { resetJob?.cancel() }
    }
    val copied = isOtpCopied(code, copiedCode)
    val motionScheme = MaterialTheme.motionScheme

    Ym1rCard(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        containerColor = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    ) {
        Row(
            Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                modifier = Modifier.size(40.dp),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.12f),
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Ym1rIcons.Lock, contentDescription = null)
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("验证码", style = MaterialTheme.typography.labelLarge)
                Text(
                    code,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
            }
            TextButton(onClick = {
                resetJob?.cancel()
                copiedCode = null
                if (onCopy()) {
                    copiedCode = code
                    haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.Confirm)
                    resetJob = scope.launch {
                        delay(1500)
                        if (isOtpCopied(code, copiedCode)) copiedCode = null
                    }
                }
            }) {
                AnimatedContent(
                    targetState = copied,
                    transitionSpec = {
                        (fadeIn(animationSpec = motionScheme.fastEffectsSpec()) +
                            scaleIn(initialScale = 0.92f, animationSpec = motionScheme.fastSpatialSpec())) togetherWith
                            (fadeOut(animationSpec = motionScheme.fastEffectsSpec()) +
                                scaleOut(targetScale = 1.08f, animationSpec = motionScheme.fastSpatialSpec()))
                    },
                    label = "otp-copy-state",
                ) { isCopied ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (isCopied) Ym1rIcons.Check else Ym1rIcons.Copy,
                            contentDescription = null,
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(if (isCopied) "已复制" else "复制验证码")
                    }
                }
            }
        }
    }
}

@Composable
private fun TranslationBanner(
    loading: Boolean,
    translated: Boolean,
    showingTranslation: Boolean,
    error: String?,
    onTranslate: () -> Unit,
    onToggle: (Boolean) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (translated) "邮件正文翻译" else "检测到英文正文",
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.weight(1f),
                )
                if (loading) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            }
            when {
                error != null -> Text(
                    error,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
                translated -> Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = { onToggle(false) }) { Text("原文") }
                    TextButton(onClick = { onToggle(true) }) { Text("译文") }
                    if (showingTranslation) {
                        Text("当前显示译文", style = MaterialTheme.typography.labelSmall)
                    }
                }
                else -> TextButton(onClick = onTranslate, enabled = !loading) { Text("下载模型并翻译") }
            }
        }
    }
}

@Composable
private fun TranslatedBody(text: String, modifier: Modifier = Modifier) {
    SelectionContainer {
        Column(
            modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            text.split('\n').forEach { paragraph ->
                if (paragraph.isNotBlank()) {
                    Text(paragraph.trim(), style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
}

@Composable
private fun PrivacyNotice(onAllowImages: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Row(
            Modifier.padding(start = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Ym1rIcons.Image, contentDescription = null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                "为保护隐私，已阻止远程图片",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onAllowImages) { Text("显示图片") }
        }
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
    var expanded by remember(attachments) { mutableStateOf(false) }
    val totalSize = attachments.sumOf { it.size.coerceAtLeast(0L) }
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(Modifier.animateContentSize()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Ym1rIcons.Paperclip, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text("附件 ${attachments.size} 个", style = MaterialTheme.typography.labelLarge)
                    if (totalSize > 0) {
                        Text(
                            formatBytes(totalSize),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                IconButton(onClick = { expanded = !expanded }) {
                    Icon(
                        if (expanded) Ym1rIcons.ChevronUp else Ym1rIcons.ChevronDown,
                        contentDescription = if (expanded) "收起附件" else "展开附件",
                    )
                }
                TextButton(onClick = onZip, enabled = downloading == null) {
                    Icon(Ym1rIcons.Archive, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("打包下载")
                }
            }
            AnimatedVisibility(expanded) {
                Column(Modifier.padding(start = 12.dp, end = 12.dp, bottom = 8.dp)) {
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
                                    Ym1rIcons.Paperclip,
                                    null,
                                    Modifier.size(20.dp),
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        att.name.ifBlank { "未命名附件" },
                                        maxLines = 1,
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
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
        }
    }
}

/**
 * 邮件正文渲染。WebView 保持只读安全沙箱，正文内容只在真正变化时重新加载，
 * 避免 Snackbar、复制和展开元数据把长邮件滚动位置重置。
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun MessageBody(
    html: String,
    isHtml: Boolean,
    allowImages: Boolean,
    originalLayout: Boolean,
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

    val background = MaterialTheme.colorScheme.background
    val foreground = MaterialTheme.colorScheme.onBackground
    val content = remember(html, isHtml, originalLayout, background, foreground) {
        if (isHtml) htmlDocument(html, originalLayout, background, foreground)
        else plainToHtml(html, background, foreground)
    }
    val loadKey = BodyLoadKey(content, originalLayout, allowImages)

    AndroidView(
        modifier = modifier.fillMaxWidth(),
        factory = { ctx ->
            WebView(ctx).apply {
                settings.apply {
                    javaScriptEnabled = false
                    domStorageEnabled = false
                    allowFileAccess = false
                    allowContentAccess = false
                    setSupportZoom(true)
                    builtInZoomControls = true
                    displayZoomControls = false
                }
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
                        openExternalLink(ctx, request.url)
                }
            }
        },
        update = { web ->
            web.setBackgroundColor(background.toArgb())
            web.settings.loadsImagesAutomatically = allowImages
            web.settings.blockNetworkLoads = !allowImages
            web.settings.loadWithOverviewMode = originalLayout
            web.settings.useWideViewPort = originalLayout
            if (web.tag != loadKey) {
                web.tag = loadKey
                web.loadDataWithBaseURL(null, content, "text/html", "UTF-8", null)
            }
        },
    )
}

private data class BodyLoadKey(
    val content: String,
    val originalLayout: Boolean,
    val allowImages: Boolean,
)

private fun openExternalLink(context: Context, uri: Uri): Boolean {
    return when (uri.scheme?.lowercase(Locale.ROOT)) {
        "http", "https" -> {
            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, uri)) }
            true
        }
        else -> true
    }
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

private fun htmlDocument(
    raw: String,
    originalLayout: Boolean,
    background: Color,
    foreground: Color,
): String {
    val safe = stripActiveHtml(raw)
    val viewport = Regex("(?i)<meta\\b[^>]*\\bname\\s*=\\s*['\"]viewport['\"]").let {
        if (it.containsMatchIn(safe)) "" else "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\"/>"
    }
    val isDark = (0.299 * background.red + 0.587 * background.green + 0.114 * background.blue) < 0.5
    val darkSelfHealingCss = if (isDark) """
        |table, td, tr, th, div, p, section, article {
        |   background-color: transparent !important;
        |}
        |img {
        |   filter: brightness(0.9) contrast(1.02);
        |}
    """.trimMargin() else ""

    val themeCss = """
        |<style id="emailbox-theme-style">
        |html, body { background-color: ${cssColor(background)} !important; color: ${cssColor(foreground)} !important; }
        |body, body * { color: ${cssColor(foreground)} !important; -webkit-text-fill-color: ${cssColor(foreground)} !important; }
        |$darkSelfHealingCss
        |</style>
    """.trimMargin()
    val responsiveCss = if (originalLayout) "" else """
        |<style id="emailbox-responsive-style">
        |html, body { width: 100%; min-width: 0; }
        |body { margin: 0 !important; padding: 16px !important; overflow-x: auto !important;
        |       overflow-wrap: anywhere !important; word-break: break-word !important;
        |       border-radius: 24px; }
        |*, *::before, *::after { box-sizing: border-box; }
        |img, video, svg, canvas { max-width: 100% !important; height: auto !important; }
        |table { max-width: 100% !important; }
        |td, th { max-width: 100% !important; overflow-wrap: anywhere !important; word-break: break-word !important; }
        |pre { white-space: pre-wrap !important; overflow-wrap: anywhere !important; }
        |a { overflow-wrap: anywhere !important; word-break: break-word !important; }
        |</style>
    """.trimMargin()
    return injectIntoHead(safe, viewport + themeCss + responsiveCss)
}

private fun stripActiveHtml(input: String): String {
    var result = input
    listOf("script", "iframe", "object", "embed", "form").forEach { tag ->
        result = result
            .replace(Regex("(?is)<$tag\\b[^>]*>.*?</$tag\\s*>"), "")
            .replace(Regex("(?is)<$tag\\b[^>]*/>"), "")
    }
    return result.replace(
        Regex("(?is)\\s+on[a-z][a-z0-9_-]*\\s*=\\s*(?:\"[^\"]*\"|'[^']*'|[^\\s>]+)"),
        "",
    )
}

private fun injectIntoHead(html: String, addition: String): String {
    if (addition.isBlank()) return html
    val headOpen = Regex("(?i)<head\\b[^>]*>").find(html)
    if (headOpen != null) {
        val insertAt = headOpen.range.last + 1
        return html.substring(0, insertAt) + addition + html.substring(insertAt)
    }
    val htmlOpen = Regex("(?i)<html\\b[^>]*>").find(html)
    if (htmlOpen != null) {
        val insertAt = htmlOpen.range.last + 1
        return html.substring(0, insertAt) + "<head>$addition</head>" + html.substring(insertAt)
    }
    return "<html><head>$addition</head><body>$html</body></html>"
}

private fun plainToHtml(text: String, background: Color, foreground: Color): String {
    val escaped = text
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
    val bg = cssColor(background)
    val fg = cssColor(foreground)
    return """<html><head><meta name="viewport" content="width=device-width, initial-scale=1"/>
        |<style>
        | body { font-family: sans-serif; font-size: 15px; line-height: 1.6; margin: 0;
        |        padding: 16px; word-wrap: break-word; overflow-wrap: anywhere;
        |        background: $bg; color: $fg; }
        | pre { white-space: pre-wrap; font-family: sans-serif; margin: 0; overflow-wrap: anywhere; }
        |</style></head><body><pre style="white-space:pre-wrap;font-family:sans-serif;">$escaped</pre></body></html>"""
        .trimMargin()
}

private fun cssColor(color: Color): String {
    val argb = color.toArgb()
    return String.format(
        Locale.US,
        "#%02X%02X%02X",
        (argb shr 16) and 0xFF,
        (argb shr 8) and 0xFF,
        argb and 0xFF,
    )
}
