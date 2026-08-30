package com.masteralanlab.emailbox.ui.screens.me

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.masteralanlab.emailbox.data.Prefs
import com.masteralanlab.emailbox.data.remote.ApiKeyView
import com.masteralanlab.emailbox.data.remote.ApiResult
import com.masteralanlab.emailbox.data.remote.apiCall
import com.masteralanlab.emailbox.ui.components.AppTopBar
import com.masteralanlab.emailbox.ui.components.ErrorBox
import com.masteralanlab.emailbox.ui.components.InfoRow
import com.masteralanlab.emailbox.ui.components.LoadingBox
import com.masteralanlab.emailbox.util.formatFullTime
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

// ---------------------------------------------------------------- 数据层

class ApiKeyViewModel : ViewModel() {

    /** null 表示「尚未生成」，非 null 表示已生成。 */
    private val _keyView = MutableStateFlow<ApiKeyView?>(null)
    val keyView: StateFlow<ApiKeyView?> = _keyView.asStateFlow()

    private val _loading = MutableStateFlow(true)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _actionError = MutableStateFlow<String?>(null)
    val actionError: StateFlow<String?> = _actionError.asStateFlow()

    private val _notice = MutableStateFlow<String?>(null)
    val notice: StateFlow<String?> = _notice.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _loading.value = true
            reload()
            _loading.value = false
        }
    }

    private suspend fun reload() {
        val tenantId = Prefs.tenantId
        if (tenantId.isNullOrBlank()) {
            _keyView.value = null
            _error.value = "尚未选择工作空间"
            return
        }
        when (val r = apiCall { apiKey(tenantId) }) {
            is ApiResult.Success -> {
                // 已生成时 token 非空；未生成时后端返回空对象，token 为 null
                _keyView.value = r.data.takeIf { !it.token.isNullOrBlank() }
                _error.value = null
            }

            is ApiResult.Failure -> {
                // 未生成时 data 为 null 但 code 仍是 0，apiCall 会把它收敛成
                // httpStatus=200 / code=0 的 Failure，这里按「还没生成」处理
                if (r.httpStatus == 200 && (r.code == null || r.code == 0)) {
                    _keyView.value = null
                    _error.value = null
                } else {
                    _keyView.value = null
                    _error.value = r.message
                }
            }
        }
    }

    fun reset() {
        val tenantId = Prefs.tenantId ?: return
        viewModelScope.launch {
            _busy.value = true
            _actionError.value = null
            when (val r = apiCall { resetApiKey(tenantId) }) {
                is ApiResult.Success -> {
                    _keyView.value = r.data.takeIf { !it.token.isNullOrBlank() }
                    _error.value = null
                    _notice.value = if (_keyView.value == null) {
                        "重置成功，但服务端未返回新的 Key"
                    } else {
                        "API Key 已重置，请同步更新所有调用方"
                    }
                }

                is ApiResult.Failure -> _actionError.value = r.message
            }
            _busy.value = false
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
fun ApiKeyScreen(onBack: () -> Unit) {
    val vm: ApiKeyViewModel = viewModel()
    val context = LocalContext.current

    val keyView by vm.keyView.collectAsState()
    val loading by vm.loading.collectAsState()
    val error by vm.error.collectAsState()
    val busy by vm.busy.collectAsState()
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

    var confirmReset by remember { mutableStateOf(false) }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = { AppTopBar(title = "API Key", onBack = onBack) },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when {
                loading -> LoadingBox(text = "正在读取 API Key…")

                error != null -> ErrorBox(message = error ?: "加载失败", onRetry = vm::load)

                else -> Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    if (keyView == null) {
                        NotGeneratedCard(busy = busy, onGenerate = { confirmReset = true })
                    } else {
                        KeyCard(
                            view = keyView,
                            busy = busy,
                            onCopy = { copyToClipboard(context, it) },
                            onReset = { confirmReset = true },
                        )
                    }
                    UsageCard()
                }
            }
        }
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            icon = { Icon(Icons.Outlined.Refresh, contentDescription = null) },
            title = { Text(if (keyView == null) "生成 API Key" else "重置 API Key") },
            text = {
                Text(
                    if (keyView == null) {
                        "将为本工作空间生成一枚新的 API Key。"
                    } else {
                        "旧 Key 会立即失效，请同步更新所有调用方。"
                    }
                )
            },
            confirmButton = {
                TextButton(
                    enabled = !busy,
                    onClick = {
                        confirmReset = false
                        vm.reset()
                    },
                ) { Text(if (keyView == null) "生成" else "重置") }
            },
            dismissButton = {
                TextButton(onClick = { confirmReset = false }) { Text("取消") }
            },
        )
    }
}

// ---------------------------------------------------------------- 卡片

@Composable
private fun NotGeneratedCard(busy: Boolean, onGenerate: () -> Unit) {
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            Modifier.fillMaxWidth().padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                Icons.Outlined.Key,
                contentDescription = null,
                modifier = Modifier.height(48.dp),
                tint = MaterialTheme.colorScheme.outline,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                "尚未生成 API Key",
                style = MaterialTheme.typography.titleSmall,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "生成后即可用它读取本工作空间下的分组、账号与邮件。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(14.dp))
            Button(onClick = onGenerate, enabled = !busy) {
                Text(if (busy) "生成中…" else "生成 API Key")
            }
        }
    }
}

@Composable
private fun KeyCard(
    view: ApiKeyView?,
    busy: Boolean,
    onCopy: (String) -> Unit,
    onReset: () -> Unit,
) {
    val token = view?.token.orEmpty()
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text(
                "当前 API Key",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(12.dp))
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    token,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
                )
            }
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth()) {
                TextButton(onClick = { onCopy(token) }) {
                    Icon(Icons.Outlined.ContentCopy, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("复制")
                }
                Spacer(Modifier.weight(1f))
                TextButton(
                    enabled = !busy,
                    onClick = onReset,
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                ) {
                    Icon(Icons.Outlined.Refresh, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text(if (busy) "处理中…" else "重置")
                }
            }
            HorizontalDivider(Modifier.padding(vertical = 4.dp))
            InfoRow("创建时间", formatFullTime(view?.created_at).ifBlank { "—" })
            InfoRow("更新时间", formatFullTime(view?.updated_at).ifBlank { "—" })
        }
    }
}

@Composable
private fun UsageCard() {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text(
                "使用说明",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(8.dp))
            BulletText("API Key 仅有分组、账号与邮件的读取权限，不能做任何写操作。")
            BulletText("只能访问 /api/v1/tenants/** 下的接口，管理员接口一律拒绝。")
            BulletText("API Key 无法读写它自己，重置只能通过登录会话完成。")
            BulletText("请以 Bearer <key> 的形式放在 Authorization 请求头里。")
        }
    }
}

@Composable
private fun BulletText(text: String) {
    Row(Modifier.padding(vertical = 3.dp)) {
        Text(
            "·",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
    }
}

/**
 * 用系统剪贴板而不是 Compose 的 LocalClipboardManager，
 * 避免不同 Compose 版本间 API 差异带来的兼容问题。
 */
private fun copyToClipboard(context: Context, text: String) {
    val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    if (manager == null) {
        Toast.makeText(context, "复制失败", Toast.LENGTH_SHORT).show()
        return
    }
    manager.setPrimaryClip(ClipData.newPlainText("Emailbox API Key", text))
    Toast.makeText(context, "已复制到剪贴板", Toast.LENGTH_SHORT).show()
}
