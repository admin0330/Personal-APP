package com.masteralanlab.emailbox.ui.screens.oauth

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.masteralanlab.emailbox.data.Prefs
import com.masteralanlab.emailbox.data.remote.ApiResult
import com.masteralanlab.emailbox.data.remote.OAuthCompleteRequest
import com.masteralanlab.emailbox.data.remote.apiCall
import kotlinx.coroutines.launch

/**
 * 微软 OAuth 重新授权。
 *
 * 默认部署下回调地址是参考应用的 http://localhost:8080，手机打不开，
 * 所以流程是：App 打开系统浏览器 → 用户在微软完成授权 → 把地址栏里的完整 URL 粘回这里 → 完成交换。
 */
class ReauthorizeViewModel : ViewModel() {

    var flowId: String = ""
        private set

    private var _busy = mutableStateOf(false)
    val busy: Boolean get() = _busy.value

    private var _error = mutableStateOf<String?>(null)
    val error: String? get() = _error.value

    private var _url = mutableStateOf<String?>(null)
    val authorizationUrl: String? get() = _url.value

    fun start(accountId: String) {
        viewModelScope.launch {
            _busy.value = true
            _error.value = null
            val tenant = Prefs.tenantId
            if (tenant.isNullOrBlank()) {
                _error.value = "未选择工作空间"
                _busy.value = false
                return@launch
            }
            when (val r = apiCall { oauthStart(tenant, accountId) }) {
                is ApiResult.Success -> {
                    flowId = r.data.flow_id
                    _url.value = r.data.authorization_url
                }

                is ApiResult.Failure -> _error.value = r.message
            }
            _busy.value = false
        }
    }

    fun complete(accountId: String, redirectedUrl: String, onDone: (String) -> Unit) {
        viewModelScope.launch {
            _busy.value = true
            _error.value = null
            val tenant = Prefs.tenantId ?: return@launch
            val body = OAuthCompleteRequest(flow_id = flowId, redirected_url = redirectedUrl.trim())
            when (val r = apiCall { oauthComplete(tenant, accountId, body) }) {
                is ApiResult.Success -> onDone(r.data.email.ifBlank { "授权完成" })
                is ApiResult.Failure -> _error.value = r.message
            }
            _busy.value = false
        }
    }
}

@Composable
fun ReauthorizeDialog(
    accountId: String,
    email: String,
    onDismiss: () -> Unit,
    onDone: (String) -> Unit,
) {
    val vm: ReauthorizeViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pasted by remember { mutableStateOf("") }

    val url = vm.authorizationUrl
    val busy = vm.busy
    val error = vm.error

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("重新授权 $email") },
        text = {
            Column {
                when {
                    url == null -> {
                        Text(
                            "将为这个 Outlook 账号重新发起微软授权。授权后 refresh_token 会被替换。",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }

                    else -> {
                        Text(
                            "第 1 步：已在浏览器打开微软登录页，完成登录与授权。",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "第 2 步：授权完成后浏览器会跳到一个打不开的地址（形如 http://localhost:8080/?code=…）。把地址栏里的完整网址复制，粘贴到下面。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = pasted,
                            onValueChange = { pasted = it },
                            label = { Text("粘贴浏览器地址") },
                            modifier = Modifier.fillMaxWidth(),
                            maxLines = 3,
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = { openInBrowser(context, url) },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("重新打开浏览器") }
                    }
                }

                if (busy) {
                    Spacer(Modifier.height(12.dp))
                    CircularProgressIndicator()
                }
                if (error != null) {
                    Spacer(Modifier.height(12.dp))
                    Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                }
            }
        },
        confirmButton = {
            if (url == null) {
                Button(onClick = { vm.start(accountId) }, enabled = !busy) { Text("开始授权") }
            } else {
                Button(
                    onClick = { vm.complete(accountId, pasted) { onDone(it) } },
                    enabled = !busy && pasted.contains("code="),
                ) { Text("完成授权") }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !busy) { Text("取消") }
        },
    )
}

private fun openInBrowser(context: Context, url: String) {
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
