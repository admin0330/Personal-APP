package com.masteralanlab.emailbox.ui.screens.account

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import com.masteralanlab.emailbox.ui.components.Ym1rIcons
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.layout.ContentScale
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.masteralanlab.emailbox.data.Prefs
import com.masteralanlab.emailbox.data.remote.ApiResult
import com.masteralanlab.emailbox.data.remote.CreateAccountRequest
import com.masteralanlab.emailbox.data.remote.MailAccount
import com.masteralanlab.emailbox.data.remote.MailGroup
import com.masteralanlab.emailbox.data.remote.UpdateAccountRequest
import com.masteralanlab.emailbox.data.remote.apiCall
import com.masteralanlab.emailbox.ui.components.AppTopBar
import com.masteralanlab.emailbox.ui.components.DropdownField
import com.masteralanlab.emailbox.ui.components.ErrorBox
import com.masteralanlab.emailbox.ui.components.GroupPicker
import com.masteralanlab.emailbox.ui.components.Labels
import com.masteralanlab.emailbox.ui.components.LoadingBox
import com.masteralanlab.emailbox.ui.components.ProductField
import com.masteralanlab.emailbox.ui.components.SectionTitle
import com.masteralanlab.emailbox.ui.components.InitialAvatar
import com.masteralanlab.emailbox.util.initialOf
import coil.compose.AsyncImage
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// ---------------------------------------------------------------- 常量

private const val MAX_ALIASES = 20
private const val MAX_REMARK = 500
private const val PROXY_TEMPLATE_HINT = "支持 {mail} 模板，例如 socks5h://user:pass@host:port"

/** 服务商下拉：null 表示交给后端按邮箱域名推断，未知域名归为 custom。 */
private val PROVIDER_OPTIONS: List<Pair<String?, String>> =
    listOf(Pair<String?, String>(null, "自动识别（按邮箱域名）")) +
        listOf("outlook", "gmail", "qq", "163", "126", "yahoo", "aliyun", "2925", "custom")
            .map { Pair<String?, String>(it, Labels.provider(it)) }

private val ACCOUNT_TYPE_OPTIONS: List<Pair<String?, String>> = listOf(
    Pair<String?, String>(null, "自动"),
    Pair<String?, String>("outlook", Labels.accountType("outlook")),
    Pair<String?, String>("imap", Labels.accountType("imap")),
)

private val STATUS_OPTIONS: List<Pair<String, String>> = listOf(
    Pair("active", Labels.accountStatus("active")),
    Pair("disabled", Labels.accountStatus("disabled")),
    Pair("banned", Labels.accountStatus("banned")),
)

// ---------------------------------------------------------------- 表单草稿

/**
 * 表单草稿。凭据类字段一律用 String 承载：
 * - 新建：空串视为「不设置」，提交 null；
 * - 编辑：空串视为「保持不变」提交 null，只有勾选对应的 clear* 才提交 ""。
 */
data class AccountDraft(
    val email: String = "",
    val groupId: String? = null,
    val provider: String? = null,
    val accountType: String? = null,
    val status: String = "active",
    val remark: String = "",
    val password: String = "",
    val clientId: String = "",
    val refreshToken: String = "",
    val imapHost: String = "",
    val imapPort: String = "",
    val imapPassword: String = "",
    val clearPassword: Boolean = false,
    val clearClientId: Boolean = false,
    val clearRefreshToken: Boolean = false,
    val clearImapHost: Boolean = false,
    val clearImapPort: Boolean = false,
    val clearImapPassword: Boolean = false,
    val proxyUrl: String = "",
    val fallbackProxy1: String = "",
    val fallbackProxy2: String = "",
    val clearProxy: Boolean = false,
    val clearFallback1: Boolean = false,
    val clearFallback2: Boolean = false,
    val aliasesText: String = "",
    val clearAliases: Boolean = false,
) {
    companion object {
        /** 凭据与代理明文服务端不返回，这里只能回填明文可见的字段。 */
        fun of(account: MailAccount): AccountDraft = AccountDraft(
            email = account.email,
            groupId = account.group_id?.takeIf { it.isNotBlank() },
            provider = account.provider.takeIf { it.isNotBlank() },
            accountType = account.account_type.takeIf { it.isNotBlank() },
            status = account.status.ifBlank { "active" },
            remark = account.remark ?: "",
            clientId = account.client_id ?: "",
            imapHost = account.imap_host ?: "",
            imapPort = account.imap_port?.toString().orEmpty(),
            aliasesText = account.aliases.joinToString("\n"),
        )
    }
}

data class AccountEditUiState(
    val loading: Boolean = true,
    val loadError: String? = null,
    val groups: List<MailGroup> = emptyList(),
    /** 编辑态的原始数据；新建时为 null。凭据是否已保存、代理脱敏值都从这里取。 */
    val account: MailAccount? = null,
    val draft: AccountDraft = AccountDraft(),
    val saving: Boolean = false,
    val saveError: String? = null,
    val saved: Boolean = false,
)

// ---------------------------------------------------------------- 数据层

class AccountEditViewModel : ViewModel() {

    private val _state = MutableStateFlow(AccountEditUiState())
    val state: StateFlow<AccountEditUiState> = _state.asStateFlow()

    fun load(accountId: String?, groupId: String?) {
        val tenant = Prefs.tenantId
        if (tenant.isNullOrBlank()) {
            _state.update { it.copy(loading = false, loadError = "未选择工作空间") }
            return
        }
        _state.update {
            it.copy(
                loading = accountId != null,
                loadError = null,
                draft = if (accountId == null) it.draft.copy(groupId = groupId) else it.draft,
            )
        }
        viewModelScope.launch {
            // 分组列表新建与编辑都要用，先拉回来；失败直接终止，表单没有分组可选没有意义。
            when (val r = apiCall { groups(tenant) }) {
                is ApiResult.Success -> _state.update { it.copy(groups = r.data) }
                is ApiResult.Failure -> {
                    _state.update { it.copy(loading = false, loadError = r.message) }
                    return@launch
                }
            }
            if (accountId.isNullOrBlank()) {
                _state.update { it.copy(loading = false) }
                return@launch
            }
            when (val r = apiCall { account(tenant, accountId) }) {
                is ApiResult.Success -> _state.update {
                    it.copy(
                        loading = false,
                        loadError = null,
                        account = r.data,
                        draft = AccountDraft.of(r.data),
                    )
                }

                is ApiResult.Failure -> _state.update {
                    it.copy(loading = false, loadError = r.message)
                }
            }
        }
    }

    fun updateDraft(mutator: (AccountDraft) -> AccountDraft) {
        _state.update { it.copy(draft = mutator(it.draft), saveError = null) }
    }

    fun consumeSaveError() = _state.update { it.copy(saveError = null) }

    fun consumeSaved() = _state.update { it.copy(saved = false) }

    fun save() {
        val s = _state.value
        if (s.saving) return
        val tenant = Prefs.tenantId
        if (tenant.isNullOrBlank()) {
            _state.update { it.copy(saveError = "未选择工作空间") }
            return
        }
        val d = s.draft
        val existing = s.account
        val isEdit = existing != null

        val email = d.email.trim()
        if (!isEdit) {
            if (email.isBlank()) {
                _state.update { it.copy(saveError = "请填写邮箱") }
                return
            }
            if (!email.contains("@")) {
                _state.update { it.copy(saveError = "邮箱格式不正确，必须包含 @") }
                return
            }
        }

        val portRaw = d.imapPort.trim()
        val port = if (portRaw.isBlank()) {
            null
        } else {
            val v = portRaw.toIntOrNull()
            if (v == null || v !in 1..65535) {
                _state.update { it.copy(saveError = "IMAP 端口必须是 1–65535 的数字") }
                return
            }
            v
        }

        val aliases = parseAliases(d.aliasesText)
        if (aliases.size > MAX_ALIASES) {
            _state.update {
                it.copy(saveError = "别名最多 $MAX_ALIASES 个，当前已解析出 ${aliases.size} 个")
            }
            return
        }
        if (d.remark.length > MAX_REMARK) {
            _state.update { it.copy(saveError = "备注最多 $MAX_REMARK 个字符") }
            return
        }

        viewModelScope.launch {
            _state.update { it.copy(saving = true, saveError = null) }
            val result = if (existing != null) {
                apiCall { updateAccount(tenant, existing.id, buildUpdateBody(d, port, aliases)) }
            } else {
                apiCall { createAccount(tenant, buildCreateBody(d, email, port, aliases)) }
            }
            _state.update {
                when (result) {
                    is ApiResult.Success -> it.copy(saving = false, saved = true)
                    is ApiResult.Failure -> it.copy(saving = false, saveError = result.message)
                }
            }
        }
    }

    // ---- 请求体 ----

    /** 编辑态：null = 保持原值，"" = 显式清空。 */
    private fun upd(value: String, clear: Boolean): String? = when {
        clear -> ""
        value.isBlank() -> null
        else -> value.trim()
    }

    /** 新建态：空白一律当作「不设置」。 */
    private fun cre(value: String): String? = value.trim().ifBlank { null }

    private fun buildUpdateBody(
        d: AccountDraft,
        port: Int?,
        aliases: List<String>,
    ): UpdateAccountRequest = UpdateAccountRequest(
        group_id = d.groupId,
        provider = d.provider,
        password = upd(d.password, d.clearPassword),
        client_id = upd(d.clientId, d.clearClientId),
        refresh_token = upd(d.refreshToken, d.clearRefreshToken),
        imap_host = upd(d.imapHost, d.clearImapHost),
        // 端口是 Int，无法表达 ""，用 0 表示「清空」
        imap_port = if (d.clearImapPort) 0 else port,
        imap_password = upd(d.imapPassword, d.clearImapPassword),
        status = d.status,
        remark = d.remark.trim(),
        aliases = if (d.clearAliases) emptyList() else aliases.ifEmpty { null },
        proxy_url = upd(d.proxyUrl, d.clearProxy),
        fallback_proxy_url_1 = upd(d.fallbackProxy1, d.clearFallback1),
        fallback_proxy_url_2 = upd(d.fallbackProxy2, d.clearFallback2),
    )

    private fun buildCreateBody(
        d: AccountDraft,
        email: String,
        port: Int?,
        aliases: List<String>,
    ): CreateAccountRequest = CreateAccountRequest(
        group_id = d.groupId,
        email = email,
        provider = d.provider,
        account_type = d.accountType,
        password = cre(d.password),
        client_id = cre(d.clientId),
        refresh_token = cre(d.refreshToken),
        imap_host = cre(d.imapHost),
        imap_port = port,
        imap_password = cre(d.imapPassword),
        status = d.status,
        remark = d.remark.trim().ifBlank { null },
        aliases = aliases.ifEmpty { null },
        proxy_url = cre(d.proxyUrl),
        fallback_proxy_url_1 = cre(d.fallbackProxy1),
        fallback_proxy_url_2 = cre(d.fallbackProxy2),
    )
}

/** 别名支持换行、逗号、分号分隔（中英文标点都收）。 */
private fun parseAliases(raw: String): List<String> =
    raw.split('\n', ',', '，', ';', '；')
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .distinct()

// ---------------------------------------------------------------- 页面

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountEditScreen(
    accountId: String?,
    groupId: String?,
    onBack: () -> Unit,
    onSaved: () -> Unit,
) {
    val vm: AccountEditViewModel = viewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    val isEdit = accountId != null

    LaunchedEffect(accountId, groupId) { vm.load(accountId, groupId) }
    LaunchedEffect(state.saved) {
        if (state.saved) {
            vm.consumeSaved()
            onSaved()
        }
    }

    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(state.saveError) {
        state.saveError?.let {
            snackbar.showSnackbar(it)
            vm.consumeSaveError()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = { AppTopBar(title = if (isEdit) "编辑账号" else "新建账号", onBack = onBack) },
        bottomBar = {
            if (!state.loading && state.loadError == null) {
                SaveBar(
                    saving = state.saving,
                    isEdit = isEdit,
                    onSave = vm::save,
                )
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when {
                state.loading -> LoadingBox(text = "正在加载…")

                state.loadError != null -> ErrorBox(
                    message = state.loadError ?: "加载失败",
                    onRetry = { vm.load(accountId, groupId) },
                )

                else -> AccountForm(
                    state = state,
                    isEdit = isEdit,
                    onDraftChange = { draft -> vm.updateDraft { draft } },
                )
            }
        }
    }
}

@Composable
private fun SaveBar(saving: Boolean, isEdit: Boolean, onSave: () -> Unit) {
    Surface(tonalElevation = 3.dp) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Button(
                onClick = onSave,
                enabled = !saving,
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                if (saving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp,
                    )
                    Spacer(Modifier.width(10.dp))
                }
                Text(if (saving) "保存中…" else if (isEdit) "保存修改" else "创建账号")
            }
        }
    }
}

// ---------------------------------------------------------------- 表单

@Composable
private fun AccountForm(
    state: AccountEditUiState,
    isEdit: Boolean,
    onDraftChange: (AccountDraft) -> Unit,
) {
    val d = state.draft
    val account = state.account

    fun mutate(block: AccountDraft.() -> AccountDraft) = onDraftChange(d.block())

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 24.dp),
    ) {
        if (isEdit && account != null) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    InitialAvatar(
                        text = initialOf(account.email),
                        modifier = Modifier.size(52.dp),
                    )
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            account.email,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            "账号标识：${initialOf(account.email)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
        }

        SectionTitle("基本信息")
        Column(Modifier.padding(horizontal = 16.dp)) {
            ProductField(
                value = d.email,
                onValueChange = { mutate { copy(email = it) } },
                label = "邮箱",
                placeholder = "name@example.com",
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !isEdit,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                supporting = if (isEdit) "邮箱创建后不可修改" else "必填；服务商与 IMAP 服务器留空时按域名自动推断",
            )
            Spacer(Modifier.height(12.dp))

            GroupPicker(
                groups = state.groups,
                selectedId = d.groupId,
                onSelect = { mutate { copy(groupId = it) } },
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                if (isEdit) "选择「不指定」表示保持原分组不变" else "不指定时落到系统默认分组",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, top = 4.dp),
            )
            Spacer(Modifier.height(12.dp))

            DropdownField(
                label = "服务商",
                options = PROVIDER_OPTIONS,
                selected = d.provider,
                onSelect = { mutate { copy(provider = it) } },
                modifier = Modifier.fillMaxWidth(),
                supporting = "留空按邮箱域名自动识别，未知域名归为自定义",
            )
            Spacer(Modifier.height(12.dp))

            DropdownField(
                label = "账号类型",
                options = ACCOUNT_TYPE_OPTIONS,
                selected = d.accountType,
                onSelect = { mutate { copy(accountType = it) } },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isEdit,
                supporting = if (isEdit) "账号类型创建后不可修改" else "留空按服务商自动选择",
            )
            Spacer(Modifier.height(12.dp))

            DropdownField(
                label = "状态",
                options = STATUS_OPTIONS,
                selected = d.status,
                onSelect = { value -> mutate { copy(status = value ?: "active") } },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))

            ProductField(
                value = d.remark,
                onValueChange = { if (it.length <= MAX_REMARK) mutate { copy(remark = it) } },
                label = "备注",
                placeholder = "可选",
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                singleLine = false,
                supporting = "${d.remark.length}/$MAX_REMARK",
            )
        }

        SectionTitle("凭据")
        Column(Modifier.padding(horizontal = 16.dp)) {
            Text(
                if (isEdit) {
                    "服务端永不返回明文凭据。留空表示保持不变，只有勾选「清除」才会删除已保存的内容。"
                } else {
                    "凭据可稍后补充；留空时后端按服务商默认值处理。"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))

            CredentialField(
                label = "登录密码",
                value = d.password,
                onValueChange = { mutate { copy(password = it) } },
                isEdit = isEdit,
                saved = account?.has_password ?: false,
                clearChecked = d.clearPassword,
                onClearChange = { mutate { copy(clearPassword = it) } },
            )
            CredentialField(
                label = "client_id",
                value = d.clientId,
                onValueChange = { mutate { copy(clientId = it) } },
                isEdit = isEdit,
                saved = !account?.client_id.isNullOrBlank(),
                secret = false,
                keyboardType = KeyboardType.Text,
                clearChecked = d.clearClientId,
                onClearChange = { mutate { copy(clearClientId = it) } },
            )
            CredentialField(
                label = "refresh_token",
                value = d.refreshToken,
                onValueChange = { mutate { copy(refreshToken = it) } },
                isEdit = isEdit,
                saved = account?.has_refresh_token ?: false,
                clearChecked = d.clearRefreshToken,
                onClearChange = { mutate { copy(clearRefreshToken = it) } },
            )
            CredentialField(
                label = "IMAP 服务器",
                value = d.imapHost,
                onValueChange = { mutate { copy(imapHost = it) } },
                isEdit = isEdit,
                saved = !account?.imap_host.isNullOrBlank(),
                secret = false,
                keyboardType = KeyboardType.Text,
                clearChecked = d.clearImapHost,
                onClearChange = { mutate { copy(clearImapHost = it) } },
            )
            CredentialField(
                label = "IMAP 端口",
                value = d.imapPort,
                onValueChange = { mutate { copy(imapPort = it) } },
                isEdit = isEdit,
                saved = account?.imap_port != null,
                secret = false,
                keyboardType = KeyboardType.Number,
                supporting = if (isEdit) "留空保持不变；勾选「清除」后置为 0" else "留空使用服务商默认值（993）",
                clearChecked = d.clearImapPort,
                onClearChange = { mutate { copy(clearImapPort = it) } },
            )
            CredentialField(
                label = "IMAP 授权码",
                value = d.imapPassword,
                onValueChange = { mutate { copy(imapPassword = it) } },
                isEdit = isEdit,
                saved = account?.has_imap_password ?: false,
                clearChecked = d.clearImapPassword,
                onClearChange = { mutate { copy(clearImapPassword = it) } },
            )
        }

        SectionTitle("别名")
        Column(Modifier.padding(horizontal = 16.dp)) {
            ProductField(
                value = d.aliasesText,
                onValueChange = { mutate { copy(aliasesText = it) } },
                label = "别名列表",
                placeholder = "一行一个别名",
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                singleLine = false,
                supporting = "一行一个，也可用逗号或分号分隔；最多 $MAX_ALIASES 个（当前 ${parseAliases(d.aliasesText).size} 个）",
            )
            ClearToggle(
                text = "清空别名",
                checked = d.clearAliases,
                onCheckedChange = { mutate { copy(clearAliases = it) } },
            )
        }

        SectionTitle("代理")
        Column(Modifier.padding(horizontal = 16.dp)) {
            Text(
                PROXY_TEMPLATE_HINT,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))

            ProxyField(
                label = "主代理",
                value = d.proxyUrl,
                onValueChange = { mutate { copy(proxyUrl = it) } },
                isEdit = isEdit,
                masked = account?.proxy_url_masked,
                clearChecked = d.clearProxy,
                onClearChange = { mutate { copy(clearProxy = it) } },
            )
            ProxyField(
                label = "备用代理 1",
                value = d.fallbackProxy1,
                onValueChange = { mutate { copy(fallbackProxy1 = it) } },
                isEdit = isEdit,
                masked = account?.fallback_proxy_url_1_masked,
                clearChecked = d.clearFallback1,
                onClearChange = { mutate { copy(clearFallback1 = it) } },
            )
            ProxyField(
                label = "备用代理 2",
                value = d.fallbackProxy2,
                onValueChange = { mutate { copy(fallbackProxy2 = it) } },
                isEdit = isEdit,
                masked = account?.fallback_proxy_url_2_masked,
                clearChecked = d.clearFallback2,
                onClearChange = { mutate { copy(clearFallback2 = it) } },
            )
        }
    }
}

/**
 * 凭据输入框。编辑态下 saved=true 时提示「已保存」，占位符提示「留空保持不变」。
 * 勾选「清除」后输入框禁用，提交时发 ""。
 */
@Composable
private fun CredentialField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    isEdit: Boolean,
    saved: Boolean,
    clearChecked: Boolean,
    onClearChange: (Boolean) -> Unit,
    secret: Boolean = true,
    keyboardType: KeyboardType = KeyboardType.Password,
    supporting: String? = null,
) {
    var visible by remember { mutableStateOf(false) }
    val hint = supporting ?: if (isEdit) (if (saved) "已保存" else "未设置") else null

    Column(Modifier.fillMaxWidth()) {
        ProductField(
            value = value,
            onValueChange = onValueChange,
            label = label,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            enabled = !clearChecked,
            placeholder = if (isEdit && saved) "留空保持不变" else null,
            visualTransformation = if (secret && !visible) {
                PasswordVisualTransformation()
            } else {
                VisualTransformation.None
            },
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            trailing = if (secret) {
                {
                    IconButton(onClick = { visible = !visible }) {
                        Icon(
                            imageVector = if (visible) {
                                Ym1rIcons.Eye
                            } else {
                                Ym1rIcons.EyeOff
                            },
                            contentDescription = if (visible) "隐藏" else "显示",
                        )
                    }
                }
            } else {
                null
            },
            supporting = hint,
        )
        if (isEdit) {
            ClearToggle(text = "清除", checked = clearChecked, onCheckedChange = onClearChange)
        }
    }
    Spacer(Modifier.height(8.dp))
}

/** 代理输入框：服务端只回传脱敏值，编辑态把它作为提示展示，留空表示不改。 */
@Composable
private fun ProxyField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    isEdit: Boolean,
    masked: String?,
    clearChecked: Boolean,
    onClearChange: (Boolean) -> Unit,
) {
    val supporting = if (!isEdit) {
        null
    } else if (masked.isNullOrBlank()) {
        "当前未设置，留空保持不变"
    } else {
        "当前：$masked，留空保持不变"
    }
    Column(Modifier.fillMaxWidth()) {
        ProductField(
            value = value,
            onValueChange = onValueChange,
            label = label,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            enabled = !clearChecked,
            placeholder = if (isEdit) "留空保持不变" else null,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            supporting = supporting,
        )
        if (isEdit) {
            ClearToggle(text = "清除", checked = clearChecked, onCheckedChange = onClearChange)
        }
    }
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun ClearToggle(
    text: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 4.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start,
    ) {
        Checkbox(checked = checked, onCheckedChange = onCheckedChange)
        Spacer(Modifier.width(4.dp))
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
