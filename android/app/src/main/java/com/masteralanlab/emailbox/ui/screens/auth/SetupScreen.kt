package com.masteralanlab.emailbox.ui.screens.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MailOutline
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.masteralanlab.emailbox.data.Prefs
import com.masteralanlab.emailbox.data.remote.ApiClient
import com.masteralanlab.emailbox.data.remote.ApiResult
import com.masteralanlab.emailbox.data.remote.LoginRequest
import com.masteralanlab.emailbox.data.remote.InviteRegisterRequest
import com.masteralanlab.emailbox.data.remote.RegisterRequest
import com.masteralanlab.emailbox.data.remote.apiCall
import com.masteralanlab.emailbox.data.remote.apiCallUnit
import com.masteralanlab.emailbox.data.remote.isValidBearerValue
import com.masteralanlab.emailbox.data.remote.normalizeServerUrl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Request

enum class ConnectMode { LoginKey, Session, ApiKey }

class SetupViewModel : ViewModel() {

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun consumeMessage() = _message.value.let { _message.value = null }

    private fun normalizedServer(raw: String): String? = runCatching {
        normalizeServerUrl(raw)
    }.getOrElse {
        _message.value = it.message ?: "服务器地址无效"
        null
    }

    /** 探测服务器是否可达：未登录时 /auth/session 返回 401 即说明后端在正常工作。 */
    suspend fun probe(server: String): String = withContext(Dispatchers.IO) {
        val base = normalizeServerUrl(server)
        val request = Request.Builder().url("$base/api/v1/auth/session").get().build()
        ApiClient.plainClient().newCall(request).execute().use { resp ->
            when (resp.code) {
                200, 401 -> "服务器可达"
                else -> "服务器返回 HTTP ${resp.code}"
            }
        }
    }

    fun connect(
        server: String,
        username: String,
        password: String,
        onDone: () -> Unit,
    ) {
        viewModelScope.launch {
            val normalized = normalizedServer(server) ?: return@launch
            _busy.value = true
            Prefs.serverUrl = normalized
            Prefs.apiKeyMode = false
            Prefs.apiKey = null
            Prefs.sessionToken = null
            ApiClient.invalidate()
            // 用户名不区分大小写：管理账户「Ym1r」任意大小写输入都归一化后提交
            val normalizedUsername = username.trim().let {
                if (it.equals("Ym1r", ignoreCase = true)) "Ym1r" else it
            }
            when (val r = apiCall { login(LoginRequest(normalizedUsername, password)) }) {
                is ApiResult.Success -> {
                    val auth = r.data
                    Prefs.rememberSession(auth)
                    val tenant = auth.active_tenant_id ?: auth.tenants.firstOrNull()?.id
                    if (tenant.isNullOrBlank()) {
                        _message.value = "该账号还没有可用的工作空间"
                    } else {
                        Prefs.tenantId = tenant
                        Prefs.tenantName = auth.tenants.firstOrNull { it.id == tenant }?.name
                        onDone()
                    }
                }

                is ApiResult.Failure -> _message.value = r.message
            }
            _busy.value = false
        }
    }

    fun register(
        server: String,
        username: String,
        email: String,
        password: String,
        onDone: () -> Unit,
    ) {
        viewModelScope.launch {
            val normalized = normalizedServer(server) ?: return@launch
            _busy.value = true
            Prefs.serverUrl = normalized
            Prefs.apiKeyMode = false
            Prefs.apiKey = null
            Prefs.sessionToken = null
            ApiClient.invalidate()
            val body = RegisterRequest(username.trim(), email.trim().ifBlank { null }, password)
            when (val r = apiCall { register(body) }) {
                is ApiResult.Success -> {
                    val auth = r.data
                    Prefs.rememberSession(auth)
                    val tenant = auth.active_tenant_id ?: auth.tenants.firstOrNull()?.id
                    if (tenant.isNullOrBlank()) {
                        _message.value = "注册成功，但没有可用工作空间"
                    } else {
                        Prefs.tenantId = tenant
                        Prefs.tenantName = auth.tenants.firstOrNull { it.id == tenant }?.name
                        onDone()
                    }
                }

                is ApiResult.Failure -> _message.value = r.message
            }
            _busy.value = false
        }
    }

    fun redeemInvite(
        username: String,
        password: String,
        code: String,
        onDone: () -> Unit,
    ) {
        viewModelScope.launch {
            if (username.trim().length < 3) {
                _message.value = "用户名至少需要 3 个字符"
                return@launch
            }
            if (password.length < 6) {
                _message.value = "密码至少需要 6 个字符"
                return@launch
            }
            if (code.isBlank()) {
                _message.value = "请输入邀请码"
                return@launch
            }
            _busy.value = true
            Prefs.clearSession()
            Prefs.serverUrl = Prefs.DEFAULT_SERVER
            ApiClient.invalidate()
            when (val r = apiCall {
                redeemInvite(InviteRegisterRequest(code.trim(), username.trim(), password = password))
            }) {
                is ApiResult.Success -> {
                    val auth = r.data
                    Prefs.rememberSession(auth)
                    val tenant = auth.active_tenant_id ?: auth.tenants.firstOrNull()?.id
                    if (tenant.isNullOrBlank()) {
                        _message.value = "账号已创建，但没有可用空间"
                    } else {
                        Prefs.tenantId = tenant
                        Prefs.tenantName = auth.tenants.firstOrNull { it.id == tenant }?.name
                        onDone()
                    }
                }
                is ApiResult.Failure -> _message.value = r.message
            }
            _busy.value = false
        }
    }

    fun connectWithApiKey(
        server: String,
        apiKey: String,
        tenantId: String,
        onDone: () -> Unit,
    ) {
        viewModelScope.launch {
            val key = apiKey.trim()
            val tenant = tenantId.trim()
            if (key.isBlank() || tenant.isBlank()) {
                _message.value = "请输入 API Key 和工作空间 ID"
                return@launch
            }
            // 从网页复制 Key 时可能混进排版字符（em-dash、全角、零宽空格）：
            // 这种值存进 Keystore 后，每次请求都会在 okhttp 的 header 校验上炸掉进程，
            // 必须在绑定这一步就拦下，让用户回网页端重新复制。
            if (!isValidBearerValue(key)) {
                _message.value = "API Key 含非法字符，请回到网页端「设置 → API」重新复制"
                return@launch
            }
            val normalized = normalizedServer(server) ?: return@launch
            _busy.value = true
            Prefs.clearSession()
            Prefs.serverUrl = normalized
            Prefs.apiKeyMode = true
            runCatching { Prefs.apiKey = key }.getOrElse {
                Prefs.apiKeyMode = false
                _message.value = "无法安全保存 API Key"
                _busy.value = false
                return@launch
            }
            Prefs.sessionToken = null
            ApiClient.invalidate()
            // 用分组接口验证 Key 与工作空间是否匹配（API Key 只有读权限）
            when (val r = apiCall { groups(tenant) }) {
                is ApiResult.Success -> {
                    Prefs.tenantId = tenant
                    Prefs.tenantName = null
                    onDone()
                }

                is ApiResult.Failure -> {
                    Prefs.clearSession()
                    ApiClient.invalidate()
                    _message.value = r.message
                }
            }
            _busy.value = false
        }
    }

    /** 使用单一登录密钥换取服务端绑定的工作空间；密钥只在成功后写入 Keystore。 */
    fun connectWithLoginKey(
        loginKey: String,
        onDone: () -> Unit,
    ) {
        viewModelScope.launch {
            val key = loginKey.trim()
            if (key.isBlank()) {
                _message.value = "请输入登录密钥"
                return@launch
            }
            if (!isValidBearerValue(key)) {
                _message.value = "登录密钥含非法字符，请重新复制"
                return@launch
            }

            _busy.value = true
            Prefs.clearSession()
            Prefs.serverUrl = Prefs.DEFAULT_SERVER
            Prefs.apiKeyMode = true
            Prefs.sessionToken = null
            ApiClient.invalidate()

            when (val r = apiCall { keySession("Bearer $key") }) {
                is ApiResult.Success -> {
                    val tenant = r.data.tenant_id.trim()
                    if (tenant.isBlank()) {
                        Prefs.clearSession()
                        ApiClient.invalidate()
                        _message.value = "服务器未返回可用工作空间"
                    } else {
                        runCatching { Prefs.apiKey = key }
                            .onSuccess {
                                Prefs.apiKeyMode = true
                                Prefs.tenantId = tenant
                                Prefs.tenantName = null
                                onDone()
                            }
                            .onFailure {
                                Prefs.clearSession()
                                ApiClient.invalidate()
                                _message.value = "无法安全保存登录密钥"
                            }
                    }
                }

                is ApiResult.Failure -> {
                    Prefs.clearSession()
                    ApiClient.invalidate()
                    _message.value = r.message
                }
            }
            _busy.value = false
        }
    }

    /** 修改服务器地址后清理旧凭据。 */
    fun forget() {
        viewModelScope.launch { apiCallUnit { logout() } }
        Prefs.clearSession()
        ApiClient.invalidate()
    }
}

@Composable
fun SetupScreen(onLoggedIn: () -> Unit) {
    val vm: SetupViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    // 服务器地址固定在客户端配置中且不展示；受邀用户只处理自己的账号凭据。
    var inviteMode by remember { mutableStateOf(false) }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var inviteCode by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }

    val busy by vm.busy.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        vm.message.collect { msg ->
            if (msg != null) snackbar.showSnackbar(msg)
        }
    }

    // 显式铺主题背景：本页没有 Scaffold，不设背景会透出「系统明暗」的窗口底色，
    // 与应用内主题（可独立于系统）不一致时就是用户看到的深浅乱配
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(28.dp))
            Surface(
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ) {
                Icon(
                    Icons.Outlined.MailOutline,
                    contentDescription = null,
                    modifier = Modifier.size(72.dp).padding(16.dp),
                )
            }
            Spacer(Modifier.height(10.dp))
            Text(
                "Ym1r",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                if (inviteMode) "使用一次性邀请码创建账号" else "登录你的账号",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(28.dp))

            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            ) {
                Column(Modifier.padding(16.dp)) {
                    OutlinedTextField(
                        value = username,
                        onValueChange = { username = it },
                        label = { Text("用户名") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (inviteMode) {
                        Spacer(Modifier.height(10.dp))
                        OutlinedTextField(
                            value = inviteCode,
                            onValueChange = { inviteCode = it.uppercase() },
                            label = { Text("一次性邀请码") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("密码") },
                        singleLine = true,
                        supportingText = { Text("密码不会保存在设备中") },
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                Text(if (passwordVisible) "隐藏" else "显示", style = MaterialTheme.typography.labelMedium)
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (inviteMode) {
                        Spacer(Modifier.height(10.dp))
                        OutlinedTextField(
                            value = confirmPassword,
                            onValueChange = { confirmPassword = it },
                            label = { Text("确认密码") },
                            singleLine = true,
                            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            isError = confirmPassword.isNotEmpty() && confirmPassword != password,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            Button(
                onClick = {
                    if (inviteMode) {
                        if (password != confirmPassword) {
                            scope.launch { snackbar.showSnackbar("两次输入的密码不一致") }
                        } else {
                            vm.redeemInvite(username, password, inviteCode, onLoggedIn)
                        }
                    } else {
                        vm.connect(Prefs.DEFAULT_SERVER, username, password, onLoggedIn)
                    }
                },
                enabled = !busy && username.isNotBlank() && password.isNotBlank() &&
                    (!inviteMode || inviteCode.isNotBlank() && confirmPassword.isNotBlank()),
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                if (busy) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                } else {
                    Text(if (inviteMode) "创建账号并登录" else "登录")
                }
            }

            Spacer(Modifier.height(4.dp))
            TextButton(onClick = {
                inviteMode = !inviteMode
                password = ""
                confirmPassword = ""
            }) {
                Text(if (inviteMode) "已有账号，返回登录" else "有邀请码？创建账号")
            }
            OutlinedButton(
                onClick = {
                    scope.launch {
                        runCatching { vm.probe(Prefs.DEFAULT_SERVER) }
                            .onSuccess { snackbar.showSnackbar(it) }
                            .onFailure { snackbar.showSnackbar("连接失败：${it.message ?: "未知错误"}") }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("检查服务状态") }

            Spacer(Modifier.height(24.dp))
        }

        SnackbarHost(
            hostState = snackbar,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .safeDrawingPadding()
                .padding(16.dp),
        )
    }
}
