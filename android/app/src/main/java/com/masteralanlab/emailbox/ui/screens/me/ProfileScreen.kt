package com.masteralanlab.emailbox.ui.screens.me

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import com.masteralanlab.emailbox.ui.components.Ym1rIcons
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.masteralanlab.emailbox.data.Prefs
import com.masteralanlab.emailbox.data.SessionBus
import com.masteralanlab.emailbox.data.SessionManager
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
import com.masteralanlab.emailbox.ui.components.ProductField
import com.masteralanlab.emailbox.ui.components.Ym1rCard
import com.masteralanlab.emailbox.util.initialOf
import coil.compose.AsyncImage
import java.io.File
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
    val context = LocalContext.current

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
            SessionManager.clearLocal(context)
            SessionBus.emitExpired("请重新登录")
        }
    }

    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var avatarPath by remember { mutableStateOf(Prefs.avatarPath) }
    val avatarPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            runCatching {
                context.contentResolver.openInputStream(it)?.use { input ->
                    val target = File(context.filesDir, "avatar_${System.currentTimeMillis()}.png")
                    target.outputStream().use { out -> input.copyTo(out) }
                    Prefs.avatarPath = target.absolutePath
                    avatarPath = target.absolutePath
                    scope.launch { snackbar.showSnackbar("头像已更新") }
                }
            }.onFailure { err ->
                scope.launch { snackbar.showSnackbar("头像保存失败：${err.message}") }
            }
        }
    }

    var username by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var initialized by remember { mutableStateOf(false) }
    var editProfileOpen by remember { mutableStateOf(false) }
    var changePasswordOpen by remember { mutableStateOf(false) }

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
                    UsernameCard(
                        name = user?.username ?: Prefs.username.orEmpty(),
                        avatarPath = avatarPath,
                        onAvatarClick = { avatarPicker.launch("image/*") },
                    )
                    BasicInfoCard(user = user)

                    EditProfileCard(onClick = { editProfileOpen = true })
                    ChangePasswordCard(onClick = { changePasswordOpen = true })

                    Spacer(Modifier.height(12.dp))
                }
            }
        }
    }

    if (editProfileOpen) {
        EditProfileDialog(
            username = username,
            email = email,
            saving = saving,
            onDismiss = { if (!saving) editProfileOpen = false },
            onSave = { nextUsername, nextEmail ->
                username = nextUsername.trim()
                email = nextEmail.trim()
                editProfileOpen = false
                vm.saveProfile(nextUsername, nextEmail)
            },
            onClearEmail = {
                editProfileOpen = false
                vm.clearEmail()
            },
        )
    }

    if (changePasswordOpen) {
        ChangePasswordDialog(
            saving = saving,
            onDismiss = { if (!saving) changePasswordOpen = false },
            onSubmit = { oldPwd, newPwd ->
                changePasswordOpen = false
                vm.changePassword(oldPwd, newPwd)
            },
        )
    }
}

// ---------------------------------------------------------------- 基本信息

@Composable
private fun BasicInfoCard(user: UserResp?) {
    val u = user ?: return
    Ym1rCard(modifier = Modifier.fillMaxWidth()) {
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

// ---------------------------------------------------------------- 头像

@Composable
private fun UsernameCard(name: String, avatarPath: String?, onAvatarClick: () -> Unit) {
    Ym1rCard(modifier = Modifier.fillMaxWidth()) {
        ListItem(
            headlineContent = {
                Text(
                    name.ifBlank { "未设置用户名" },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            },
            supportingContent = {
                Text(
                    "点击头像更换自定义头像",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            leadingContent = {
                Box(
                    modifier = Modifier.clickable(onClick = onAvatarClick),
                ) {
                    UserAvatar(name = name, avatarPath = avatarPath, modifier = Modifier.size(64.dp))
                    Surface(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .size(20.dp),
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Ym1rIcons.Pencil, contentDescription = "更换头像", modifier = Modifier.size(12.dp))
                        }
                    }
                }
            },
            trailingContent = {
                Text(
                    "已登录",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            },
            colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
        )
    }
}

/** 个人头像的统一渲染，个人页和「我的」页共用。 */
@Composable
internal fun UserAvatar(name: String, avatarPath: String?, modifier: Modifier) {
    Box(
        modifier = modifier
            .clip(androidx.compose.foundation.shape.CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        val file = avatarPath?.let(::File)?.takeIf { it.isFile }
        if (file != null) {
            AsyncImage(
                model = file,
                contentDescription = "头像",
                modifier = Modifier.fillMaxSize().clip(androidx.compose.foundation.shape.CircleShape),
                contentScale = ContentScale.Crop,
            )
        } else {
            Text(
                initialOf(name.ifBlank { "Y" }),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

// ---------------------------------------------------------------- 修改资料：卡片点击后才展示表单

@Composable
private fun EditProfileCard(onClick: () -> Unit) {
    Ym1rCard(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .clickable(onClick = onClick),
    ) {
        ListItem(
            headlineContent = { Text("修改资料") },
            supportingContent = { Text("修改用户名和联系邮箱") },
            leadingContent = { Icon(Ym1rIcons.Pencil, contentDescription = null) },
            trailingContent = {
                Icon(Ym1rIcons.ChevronRight, contentDescription = "打开修改资料")
            },
            colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
        )
    }
}

@Composable
private fun EditProfileDialog(
    username: String,
    email: String,
    saving: Boolean,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit,
    onClearEmail: () -> Unit,
) {
    var nextUsername by remember(username) { mutableStateOf(username) }
    var nextEmail by remember(email) { mutableStateOf(email) }
    var localError by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text("修改资料") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                ProductField(
                    value = nextUsername,
                    onValueChange = { nextUsername = it; localError = null },
                    label = "用户名",
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                ProductField(
                    value = nextEmail,
                    onValueChange = { nextEmail = it; localError = null },
                    label = "邮箱",
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    supporting = "留空保存不会清空；需要清空请点下方按钮",
                )
                if (localError != null) {
                    Text(
                        localError.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onClearEmail, enabled = !saving) { Text("清空邮箱") }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (nextUsername.trim().isBlank()) localError = "用户名不能为空"
                    else onSave(nextUsername, nextEmail)
                },
                enabled = !saving,
            ) {
                if (saving) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Text("保存")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !saving) { Text("取消") }
        },
    )
}

// ---------------------------------------------------------------- 修改密码：卡片点击后才展示表单

@Composable
private fun ChangePasswordCard(onClick: () -> Unit) {
    Ym1rCard(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .clickable(onClick = onClick),
    ) {
        ListItem(
            headlineContent = { Text("修改密码") },
            supportingContent = { Text("更新登录密码，修改后需要重新登录") },
            leadingContent = { Icon(Ym1rIcons.Lock, contentDescription = null) },
            trailingContent = {
                Icon(Ym1rIcons.ChevronRight, contentDescription = "打开修改密码")
            },
            colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
        )
    }
}

@Composable
private fun ChangePasswordDialog(
    saving: Boolean,
    onDismiss: () -> Unit,
    onSubmit: (String, String) -> Unit,
) {
    var oldPwd by remember { mutableStateOf("") }
    var newPwd by remember { mutableStateOf("") }
    var confirmPwd by remember { mutableStateOf("") }
    var oldVisible by remember { mutableStateOf(false) }
    var newVisible by remember { mutableStateOf(false) }
    var localError by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text("修改密码") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                ProductField(
                    value = oldPwd,
                    onValueChange = { oldPwd = it; localError = null },
                    label = "当前密码",
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    visualTransformation = if (oldVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailing = {
                        IconButton(onClick = { oldVisible = !oldVisible }) {
                            Icon(
                                if (oldVisible) Ym1rIcons.EyeOff else Ym1rIcons.Eye,
                                contentDescription = if (oldVisible) "隐藏密码" else "显示密码",
                            )
                        }
                    },
                )
                ProductField(
                    value = newPwd,
                    onValueChange = { newPwd = it; localError = null },
                    label = "新密码",
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    visualTransformation = if (newVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    supporting = "至少 8 位",
                    trailing = {
                        IconButton(onClick = { newVisible = !newVisible }) {
                            Icon(
                                if (newVisible) Ym1rIcons.EyeOff else Ym1rIcons.Eye,
                                contentDescription = if (newVisible) "隐藏密码" else "显示密码",
                            )
                        }
                    },
                )
                ProductField(
                    value = confirmPwd,
                    onValueChange = { confirmPwd = it; localError = null },
                    label = "确认新密码",
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                )
                Ym1rCard(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        "修改成功后服务端会清空该账号的全部会话，当前登录会立即失效，需要重新登录。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    )
                }
                if (localError != null) {
                    Text(
                        localError.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            Button(
                enabled = !saving,
                onClick = {
                    when {
                        oldPwd.isBlank() -> localError = "请填写当前密码"
                        newPwd.length < 8 -> localError = "新密码至少 8 位"
                        newPwd != confirmPwd -> localError = "两次输入的新密码不一致"
                        else -> onSubmit(oldPwd, newPwd)
                    }
                },
            ) { Text(if (saving) "提交中…" else "修改密码") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !saving) { Text("取消") }
        },
    )
}
