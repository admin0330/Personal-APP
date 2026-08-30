package com.masteralanlab.emailbox.ui.screens.me

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.masteralanlab.emailbox.data.Prefs
import com.masteralanlab.emailbox.data.SessionBus
import com.masteralanlab.emailbox.data.remote.ApiClient
import com.masteralanlab.emailbox.data.remote.ApiResult
import com.masteralanlab.emailbox.data.remote.ChangePasswordRequest
import com.masteralanlab.emailbox.data.remote.UpdateProfileRequest
import com.masteralanlab.emailbox.data.remote.UserResp
import com.masteralanlab.emailbox.data.remote.apiCall
import com.masteralanlab.emailbox.ui.components.AppTopBar
import com.masteralanlab.emailbox.ui.components.ErrorBox
import com.masteralanlab.emailbox.ui.components.InfoRow
import com.masteralanlab.emailbox.ui.components.Labels
import com.masteralanlab.emailbox.ui.components.LoadingBox
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

// ---------------------------------------------------------------- 数据层

class ProfileViewModel : ViewModel() {

    private val _user = MutableStateFlow<UserResp?>(null)
    val user: StateFlow<UserResp?> = _user.asStateFlow()

    private val _loading = MutableStateFlow(true)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _saving = MutableStateFlow(false)
    val saving: StateFlow<Boolean> = _saving.asStateFlow()

    private val _actionError = MutableStateFlow<String?>(null)
    val actionError: StateFlow<String?> = _actionError.asStateFlow()

    private val _notice = MutableStateFlow<String?>(null)
    val notice: StateFlow<String?> = _notice.asStateFlow()

    /** 改密成功后后端清空全部会话，置 true 让界面执行登出流程。 */
    private val _needRelogin = MutableStateFlow(false)
    val needRelogin: StateFlow<Boolean> = _needRelogin.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _loading.value = true
            when (val r = apiCall { profile() }) {
                is ApiResult.Success -> {
                    _user.value = r.data
                    _error.value = null
                    Prefs.username = r.data.username
                    Prefs.userEmail = r.data.email
                    Prefs.platformRole = r.data.platform_role
                }

                is ApiResult.Failure -> {
                    _user.value = null
                    _error.value = r.message
                }
            }
            _loading.value = false
        }
    }

    /**
     * @param email 后端语义：null 保持原值，"" 显式清空。
     */
    fun saveProfile(username: String, email: String) {
        val trimmedName = username.trim()
        if (trimmedName.isBlank()) {
            _actionError.value = "用户名不能为空"
            return
        }
        viewModelScope.launch {
            _saving.value = true
            _actionError.value = null
            val body = UpdateProfileRequest(
                username = trimmedName,
                email = email.trim().ifEmpty { null },
            )
            when (val r = apiCall { updateProfile(body) }) {
                is ApiResult.Success -> {
                    _user.value = r.data
                    Prefs.username = r.data.username
                    Prefs.userEmail = r.data.email
                    _notice.value = "资料已保存"
                }

                is ApiResult.Failure -> _actionError.value = r.message
            }
            _saving.value = false
        }
    }

    /** 清空邮箱：显式传空串。 */
    fun clearEmail() {
        viewModelScope.launch {
            _saving.value = true
            _actionError.value = null
            when (val r = apiCall { updateProfile(UpdateProfileRequest(email = "")) }) {
                is ApiResult.Success -> {
                    _user.value = r.data
                    Prefs.username = r.data.username
                    Prefs.userEmail = r.data.email
                    _notice.value = "邮箱已清空"
                }

                is ApiResult.Failure -> _actionError.value = r.message
            }
            _saving.value = false
        }
    }

    fun changePassword(oldPassword: String, newPassword: String) {
        if (oldPassword.isBlank()) {
            _actionError.value = "请填写当前密码"
            return
        }
        if (newPassword.length < 8) {
            _actionError.value = "新密码至少 8 位"
            return
        }
        viewModelScope.launch {
            _saving.value = true
            _actionError.value = null
            val body = ChangePasswordRequest(
                old_password = oldPassword,
                new_password = newPassword,
            )
            when (val r = apiCall { changePassword(body) }) {
                is ApiResult.Success -> {
                    _notice.value = "密码已修改，请重新登录"
                    _needRelogin.value = true
                }

                is ApiResult.Failure -> _actionError.value = r.message
            }
            _saving.value = false
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
fun ProfileScreen(onBack: () -> Unit) {
    val vm: ProfileViewModel = viewModel()

    val user by vm.user.collectAsState()
    val loading by vm.loading.collectAsState()
    val error by vm.error.collectAsState()
    val saving by vm.saving.collectAsState()
    val actionError by vm.actionError.collectAsState()
    val notice by vm.notice.collectAsState()
    val needRelogin by vm.needRelogin.collectAsState()

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

    LaunchedEffect(needRelogin) {
        if (needRelogin) {
            Prefs.clearSession()
            ApiClient.invalidate()
            SessionBus.emitExpired("请重新登录")
        }
    }

    var username by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var initialized by remember { mutableStateOf(false) }

    // 资料拉取完成后回填一次表单，之后以用户编辑为准
    LaunchedEffect(user) {
        val u = user ?: return@LaunchedEffect
        if (!initialized) {
            username = u.username
            email = u.email.orEmpty()
            initialized = true
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = { AppTopBar(title = "个人资料", onBack = onBack) },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when {
                loading -> LoadingBox(text = "正在加载资料…")

                error != null -> ErrorBox(message = error ?: "加载失败", onRetry = vm::load)

                else -> Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .imePadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    BasicInfoCard(user = user)

                    EditProfileCard(
                        username = username,
                        email = email,
                        saving = saving,
                        onUsernameChange = { username = it },
                        onEmailChange = { email = it },
                        onSave = { vm.saveProfile(username, email) },
                        onClearEmail = vm::clearEmail,
                    )

                    ChangePasswordCard(
                        saving = saving,
                        onSubmit = { oldPwd, newPwd -> vm.changePassword(oldPwd, newPwd) },
                    )

                    Spacer(Modifier.height(12.dp))
                }
            }
        }
    }
}

// ---------------------------------------------------------------- 基本信息

@Composable
private fun BasicInfoCard(user: UserResp?) {
    val u = user ?: return
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text(
                "基本信息",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(vertical = 8.dp),
            )
            InfoRow("用户 ID", u.id)
            InfoRow("用户名", u.username)
            InfoRow("邮箱", u.email?.takeIf { it.isNotBlank() } ?: "未设置")
            InfoRow("状态", Labels.accountStatus(u.status))
            InfoRow(
                "平台角色",
                if (u.platform_role == "admin") "平台管理员" else "普通用户",
            )
        }
    }
}

// ---------------------------------------------------------------- 修改资料

@Composable
private fun EditProfileCard(
    username: String,
    email: String,
    saving: Boolean,
    onUsernameChange: (String) -> Unit,
    onEmailChange: (String) -> Unit,
    onSave: () -> Unit,
    onClearEmail: () -> Unit,
) {
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text(
                "修改资料",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = username,
                onValueChange = onUsernameChange,
                label = { Text("用户名") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = email,
                onValueChange = onEmailChange,
                label = { Text("邮箱") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                supportingText = { Text("留空表示保持原值；如需清空请使用下方按钮") },
            )
            Spacer(Modifier.height(6.dp))
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    "服务端约定：邮箱字段不传或传 null 时保持原值，传空字符串才是清空。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onClearEmail, enabled = !saving) { Text("清空邮箱") }
                Spacer(Modifier.width(8.dp))
                Button(onClick = onSave, enabled = !saving) {
                    if (saving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Text("保存")
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- 修改密码

@Composable
private fun ChangePasswordCard(
    saving: Boolean,
    onSubmit: (String, String) -> Unit,
) {
    var oldPwd by remember { mutableStateOf("") }
    var newPwd by remember { mutableStateOf("") }
    var confirmPwd by remember { mutableStateOf("") }
    var oldVisible by remember { mutableStateOf(false) }
    var newVisible by remember { mutableStateOf(false) }
    var localError by remember { mutableStateOf<String?>(null) }

    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text(
                "修改密码",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = oldPwd,
                onValueChange = { oldPwd = it; localError = null },
                label = { Text("当前密码") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                visualTransformation = if (oldVisible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                trailingIcon = {
                    IconButton(onClick = { oldVisible = !oldVisible }) {
                        Icon(
                            if (oldVisible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                            contentDescription = if (oldVisible) "隐藏密码" else "显示密码",
                        )
                    }
                },
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = newPwd,
                onValueChange = { newPwd = it; localError = null },
                label = { Text("新密码") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                visualTransformation = if (newVisible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                supportingText = { Text("至少 8 位") },
                trailingIcon = {
                    IconButton(onClick = { newVisible = !newVisible }) {
                        Icon(
                            if (newVisible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                            contentDescription = if (newVisible) "隐藏密码" else "显示密码",
                        )
                    }
                },
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = confirmPwd,
                onValueChange = { confirmPwd = it; localError = null },
                label = { Text("确认新密码") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            )
            Spacer(Modifier.height(8.dp))
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    "修改成功后服务端会清空该账号的全部会话，当前登录会立即失效，需要重新登录。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }
            if (localError != null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    localError ?: "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Spacer(Modifier.height(8.dp))
            HorizontalDivider()
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Button(
                    enabled = !saving,
                    onClick = {
                        when {
                            oldPwd.isBlank() -> localError = "请填写当前密码"
                            newPwd.length < 8 -> localError = "新密码至少 8 位"
                            newPwd != confirmPwd -> localError = "两次输入的新密码不一致"
                            else -> {
                                localError = null
                                onSubmit(oldPwd, newPwd)
                                oldPwd = ""
                                newPwd = ""
                                confirmPwd = ""
                            }
                        }
                    },
                ) {
                    Text(if (saving) "提交中…" else "修改密码")
                }
            }
        }
    }
}
