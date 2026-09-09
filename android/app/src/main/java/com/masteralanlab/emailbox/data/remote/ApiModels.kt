package com.masteralanlab.emailbox.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 全部字段与后端 Go 结构体的 json tag 一一对应，属性名即 JSON 名，不做驼峰转换。
 * 时间统一用 String 承载 RFC3339，展示层再解析——后端会输出纳秒精度，直接反序列化成
 * Instant 在部分机型上有兼容风险。
 */

// ---------- 统一包装 ----------

@Serializable
data class ApiResp<T>(
    val code: Int? = null,
    val data: T? = null,
    val message: String? = null,
)

/** 中间件/路由层错误只有 message，没有 code 与 data。 */
@Serializable
data class RawError(val message: String? = null, val code: Int? = null)

/** code=1005 上游邮件错误时 data 的结构。 */
@Serializable
data class UpstreamError(
    val error_kind: String? = null,
    val channel: String? = null,
)

@Serializable
data class Pagination(
    val page: Int = 0,
    val limit: Int = 0,
    val total: Int = 0,
    val pages: Int = 0,
)

@Serializable
data class Paged<T>(
    val items: List<T> = emptyList(),
    val pagination: Pagination? = null,
)

// ---------- 鉴权与用户 ----------

@Serializable
data class LoginRequest(val username: String, val password: String)

@Serializable
data class RegisterRequest(
    val username: String,
    val email: String? = null,
    val password: String,
)

@Serializable
data class InviteRegisterRequest(
    val code: String,
    val username: String,
    val email: String? = null,
    val password: String,
)

@Serializable
data class CreateInviteRequest(val valid_hours: Int = 24)

/** 明文 code 只在创建邀请码的响应中返回一次。 */
@Serializable
data class SignupInviteCreated(
    val id: String = "",
    val created_by: String = "",
    val expires_at: String = "",
    val redeemed_at: String? = null,
    val created_at: String = "",
    val code: String = "",
)

@Serializable
data class UserResp(
    val id: String = "",
    val username: String = "",
    val email: String? = null,
    val status: String = "active",
    val platform_role: String = "user",
)

@Serializable
data class Tenant(
    val id: String = "",
    val name: String = "",
    val slug: String = "",
    val kind: String = "personal",
    val created_by: String = "",
    val created_at: String = "",
    val updated_at: String = "",
)

@Serializable
data class AuthResponse(
    val user: UserResp,
    val tenants: List<Tenant> = emptyList(),
    val active_tenant_id: String? = null,
)

@Serializable
data class KeySessionResponse(
    val tenant_id: String = "",
)

// ---------- 个人记账 ----------

@Serializable
data class LedgerTransaction(
    val id: String = "",
    val tenant_id: String = "",
    val type: String = "expense",
    val amount_minor: Long = 0,
    val currency: String = "CNY",
    val category: String = "其他",
    val occurred_at: String = "",
    val merchant: String = "",
    val note: String = "",
    val source: String = "manual",
    val source_message_key: String? = null,
    val client_id: String = "",
    val posted: Boolean = true,
    val created_at: String = "",
    val updated_at: String = "",
)

@Serializable
data class CreateLedgerTransactionRequest(
    val type: String,
    val amount_minor: Long,
    val currency: String,
    val category: String,
    val occurred_at: String,
    val merchant: String = "",
    val note: String = "",
    val source: String = "manual",
    val source_message_key: String? = null,
    val client_id: String,
    val posted: Boolean? = null,
)

@Serializable
data class UpdateLedgerTransactionRequest(
    val type: String? = null,
    val amount_minor: Long? = null,
    val currency: String? = null,
    val category: String? = null,
    val occurred_at: String? = null,
    val merchant: String? = null,
    val note: String? = null,
    val posted: Boolean? = null,
)

// ---------- 笔记 ----------

@Serializable
data class Note(
    val id: String = "",
    val tenant_id: String = "",
    val title: String = "",
    val content: String = "",
    val is_pinned: Boolean = false,
    val created_at: String = "",
    val updated_at: String = "",
    val is_completed: Boolean = false,
)

@Serializable
data class CreateNoteRequest(
    val title: String,
    val content: String,
    val is_pinned: Boolean = false,
    val is_completed: Boolean = false,
)

@Serializable
data class UpdateNoteRequest(
    val title: String? = null,
    val content: String? = null,
    val is_pinned: Boolean? = null,
    val is_completed: Boolean? = null,
)

// ---------- 同步健康 ----------

@Serializable
data class ProxyGroupStatus(
    val name: String = "",
    val type: String = "",
    val current: String = "",
    val choices: List<String> = emptyList(),
)

@Serializable
data class SyncHealth(
    val healthy: Boolean = false,
    val checked_at: String = "",
    val groups: List<ProxyGroupStatus> = emptyList(),
)

@Serializable
data class SwitchProxyNodeRequest(val group: String, val node: String)

@Serializable
data class LedgerSummaryItem(
    val type: String = "expense",
    val currency: String = "CNY",
    val category: String = "其他",
    val amount_minor: Long = 0,
)

@Serializable
data class LedgerSummary(
    val month: String = "",
    val items: List<LedgerSummaryItem> = emptyList(),
)

@Serializable
data class UpdateProfileRequest(
    val username: String? = null,
    val email: String? = null,
)

@Serializable
data class ChangePasswordRequest(
    val old_password: String,
    val new_password: String,
)

@Serializable
data class CreateTenantRequest(val name: String, val slug: String? = null)

@Serializable
data class UpdateTenantRequest(
    val name: String? = null,
    val slug: String? = null,
)

// ---------- 成员 ----------

@Serializable
data class TenantMember(
    val id: String = "",
    val tenant_id: String = "",
    val user_id: String = "",
    val role: String = "member",
    val created_at: String = "",
    val updated_at: String = "",
    val username: String = "",
    val email: String? = null,
)

@Serializable
data class AddMemberRequest(val username: String, val role: String = "member")

@Serializable
data class UpdateMemberRoleRequest(val role: String)

// ---------- 配额与 API Key ----------

@Serializable
data class QuotaLimits(
    val plan_code: String = "",
    val plan_name: String = "",
    val max_accounts: Int = -1,
    val max_groups: Int = -1,
    val daily_mail_fetch: Int = -1,
)

@Serializable
data class QuotaUsed(
    val accounts: Int = 0,
    val groups: Int = 0,
    val mail_fetch: Int = 0,
    val token_refresh: Int = 0,
)

@Serializable
data class QuotaUsage(
    val limits: QuotaLimits,
    val usage: QuotaUsed,
    val day: String = "",
)

@Serializable
data class UpdateQuotaRequest(
    val plan_id: String? = null,
    val note: String = "",
    val max_accounts: Int? = null,
    val max_groups: Int? = null,
    val daily_mail_fetch: Int? = null,
)

@Serializable
data class ApiKeyView(
    val token: String? = null,
    val created_at: String? = null,
    val updated_at: String? = null,
)

// ---------- 分组 ----------

@Serializable
data class MailGroup(
    val id: String = "",
    val name: String = "",
    val description: String? = null,
    val color: String = "gray",
    val sort_order: Int = 0,
    val is_system: Boolean = false,
    val created_at: String = "",
    val updated_at: String = "",
    val proxy_url_masked: String? = null,
    val fallback_proxy_url_1_masked: String? = null,
    val fallback_proxy_url_2_masked: String? = null,
    val account_count: Int = 0,
)

@Serializable
data class CreateGroupRequest(
    val name: String,
    val description: String? = null,
    val color: String? = null,
    val proxy_url: String? = null,
    val fallback_proxy_url_1: String? = null,
    val fallback_proxy_url_2: String? = null,
)

/** 全字段可空：null（省略）= 保持原值，"" = 显式清空。 */
@Serializable
data class UpdateGroupRequest(
    val name: String? = null,
    val description: String? = null,
    val color: String? = null,
    val proxy_url: String? = null,
    val fallback_proxy_url_1: String? = null,
    val fallback_proxy_url_2: String? = null,
)

@Serializable
data class ReorderGroupsRequest(val group_ids: List<String> = emptyList())

// ---------- 账号 ----------

@Serializable
data class MailAccount(
    val id: String = "",
    val group_id: String? = null,
    val email: String = "",
    val provider: String = "",
    val account_type: String = "",
    val auth_channel: String = "",
    val client_id: String? = null,
    val imap_host: String? = null,
    val imap_port: Int? = null,
    val status: String = "active",
    val remark: String? = null,
    val sort_order: Int = 0,
    val last_refresh_at: String? = null,
    val last_refresh_status: String? = null,
    val last_refresh_error: String? = null,
    val last_refresh_error_kind: String? = null,
    val refresh_token_updated_at: String? = null,
    val created_at: String = "",
    val updated_at: String = "",
    val has_password: Boolean = false,
    val has_refresh_token: Boolean = false,
    val has_imap_password: Boolean = false,
    val proxy_url_masked: String? = null,
    val fallback_proxy_url_1_masked: String? = null,
    val fallback_proxy_url_2_masked: String? = null,
    val aliases: List<String> = emptyList(),
)

@Serializable
data class CreateAccountRequest(
    val group_id: String? = null,
    val email: String,
    val provider: String? = null,
    val account_type: String? = null,
    val password: String? = null,
    val client_id: String? = null,
    val refresh_token: String? = null,
    val imap_host: String? = null,
    val imap_port: Int? = null,
    val imap_password: String? = null,
    val status: String? = null,
    val remark: String? = null,
    val aliases: List<String>? = null,
    val proxy_url: String? = null,
    val fallback_proxy_url_1: String? = null,
    val fallback_proxy_url_2: String? = null,
)

/** 无 email 字段——邮箱不可修改。null = 保持原值，"" = 清空。 */
@Serializable
data class UpdateAccountRequest(
    val group_id: String? = null,
    val provider: String? = null,
    val password: String? = null,
    val client_id: String? = null,
    val refresh_token: String? = null,
    val imap_host: String? = null,
    val imap_port: Int? = null,
    val imap_password: String? = null,
    val status: String? = null,
    val remark: String? = null,
    val aliases: List<String>? = null,
    val proxy_url: String? = null,
    val fallback_proxy_url_1: String? = null,
    val fallback_proxy_url_2: String? = null,
)

@Serializable
data class ImportDefaults(
    val remark: String? = null,
    val status: String? = null,
)

@Serializable
data class ImportAccountsRequest(
    val group_id: String? = null,
    val format: String = "auto",
    val content: String = "",
    val on_conflict: String = "skip",
    val client_id_first: Boolean? = null,
    val imap_host: String? = null,
    val imap_port: Int? = null,
    val defaults: ImportDefaults? = null,
)

@Serializable
data class ImportError(val line: Int = 0, val email: String = "", val reason: String = "")

@Serializable
data class ImportResult(
    val total: Int = 0,
    val created: Int = 0,
    val updated: Int = 0,
    val skipped: Int = 0,
    val failed: Int = 0,
    val errors: List<ImportError> = emptyList(),
    val truncated: Boolean = false,
)

@Serializable
data class ExportAccountsRequest(
    val scope: String = "all",
    val group_ids: List<String> = emptyList(),
    val account_ids: List<String> = emptyList(),
)

@Serializable
data class BatchIdsRequest(val account_ids: List<String> = emptyList())

@Serializable
data class BatchMoveRequest(
    val account_ids: List<String> = emptyList(),
    val group_id: String = "",
)

@Serializable
data class BatchStatusRequest(
    val account_ids: List<String> = emptyList(),
    val status: String = "active",
)

@Serializable
data class BatchProxyRequest(
    val account_ids: List<String> = emptyList(),
    val proxy_url: String = "",
    val fallback_proxy_url_1: String? = null,
    val fallback_proxy_url_2: String? = null,
)

@Serializable
data class BatchError(val account_id: String = "", val reason: String = "")

@Serializable
data class BatchResult(
    val requested: Int = 0,
    val succeeded: Int = 0,
    val failed: Int = 0,
    val errors: List<BatchError> = emptyList(),
)

// ---------- 邮件 ----------

@Serializable
data class MessageItem(
    val id: String = "",
    val id_mode: String = "",
    val folder: String = "inbox",
    val subject: String = "",
    val from: String = "",
    val to: String = "",
    val cc: String = "",
    val received_at: String = "",
    val is_read: Boolean = false,
    val has_attachments: Boolean = false,
    val body_preview: String = "",
)

@Serializable
data class MessageListResult(
    val items: List<MessageItem> = emptyList(),
    val channel: String = "",
)

@Serializable
data class Attachment(
    val id: String = "",
    val name: String = "",
    val content_type: String = "",
    val size: Long = 0,
    val is_inline: Boolean = false,
)

@Serializable
data class MessageDetail(
    val id: String = "",
    val id_mode: String = "",
    val folder: String = "inbox",
    val subject: String = "",
    val from: String = "",
    val to: String = "",
    val cc: String = "",
    val received_at: String = "",
    val is_read: Boolean = false,
    val has_attachments: Boolean = false,
    val body_preview: String = "",
    val body: String = "",
    val body_type: String = "text",
    val attachments: List<Attachment> = emptyList(),
)

@Serializable
data class MessageRef(
    val id: String = "",
    val id_mode: String = "",
    val folder: String = "inbox",
)

@Serializable
data class MessageBatchRequest(val items: List<MessageRef> = emptyList())

@Serializable
data class MailBatchItem(
    val ref: MessageRef,
    val ok: Boolean = false,
    val error: String? = null,
)

@Serializable
data class MailBatchResult(
    val succeeded: Int = 0,
    val failed: Int = 0,
    val items: List<MailBatchItem> = emptyList(),
)

// ---------- 令牌刷新与任务 ----------

@Serializable
data class Job(
    val id: String = "",
    val tenant_id: String = "",
    val type: String = "",
    val trigger: String = "",
    val status: String = "",
    val created_by: String = "",
    val total_count: Int = 0,
    val success_count: Int = 0,
    val failed_count: Int = 0,
    val params: String = "",
    val error_summary: String = "",
    val started_at: String? = null,
    val finished_at: String? = null,
    val heartbeat_at: String? = null,
    val created_at: String = "",
)

@Serializable
data class JobItem(
    val id: String = "",
    val job_id: String = "",
    val account_id: String = "",
    val email: String = "",
    val position: Int = 0,
    val status: String = "",
    val error_kind: String = "",
    val error: String = "",
    val started_at: String? = null,
    val finished_at: String? = null,
)

@Serializable
data class SubmitRefreshRequest(
    val scope: String = "all",
    val account_ids: List<String> = emptyList(),
    val group_ids: List<String> = emptyList(),
)

@Serializable
data class RefreshStats(
    val total: Int = 0,
    val success: Int = 0,
    val failed: Int = 0,
    val never: Int = 0,
    val by_error_kind: Map<String, Int> = emptyMap(),
    val last_job: Job? = null,
)

@Serializable
data class RefreshLog(
    val id: String = "",
    val tenant_id: String = "",
    val account_id: String = "",
    val account_email: String = "",
    val job_id: String = "",
    val refresh_type: String = "",
    val status: String = "",
    val error_kind: String = "",
    val error_message: String = "",
    val created_at: String = "",
)

// ---------- SSE ----------

@Serializable
data class SseStarted(val total: Int = 0, val type: String = "")

@Serializable
data class SseProgress(
    val total: Int = 0,
    val success: Int = 0,
    val failed: Int = 0,
    val done: Int = 0,
    val current: String = "",
)

@Serializable
data class SseItem(
    val account_id: String = "",
    val email: String = "",
    val status: String = "",
    val error_kind: String = "",
    val error: String = "",
)

@Serializable
data class SseFinished(
    val status: String = "",
    val total: Int = 0,
    val success: Int = 0,
    val failed: Int = 0,
    val skipped: Int = 0,
    val error_summary: String = "",
)

@Serializable
data class SseErrorPayload(val error: String = "")

@Serializable
data class MailNotificationPayload(
    val account_id: String = "",
    val new_count: Int = 0,
)

// ---------- OAuth ----------

@Serializable
data class OAuthStartResult(
    val flow_id: String = "",
    val authorization_url: String = "",
    val expires_at: String = "",
)

@Serializable
data class OAuthCompleteRequest(
    val flow_id: String = "",
    val redirected_url: String = "",
)

@Serializable
data class OAuthCompleteResult(
    val account_id: String = "",
    val email: String = "",
    val status: String = "",
)

// ---------- 管理员 ----------

@Serializable
data class PlatformStats(
    val user_count: Int = 0,
    val disabled_user_count: Int = 0,
    val admin_count: Int = 0,
    val tenant_count: Int = 0,
    val account_count: Int = 0,
    val banned_account_count: Int = 0,
    val mail_fetch_today: Int = 0,
    val token_refresh_today: Int = 0,
)

@Serializable
data class AuditLog(
    val id: String = "",
    val tenant_id: String = "",
    val actor_user_id: String = "",
    val actor_name: String = "",
    val actor_kind: String = "",
    val action: String = "",
    val resource_type: String = "",
    val resource_id: String = "",
    val ip: String = "",
    val details: String = "{}",
    val created_at: String = "",
)

@Serializable
data class AdminUser(
    val id: String = "",
    val username: String = "",
    val email: String? = null,
    val status: String = "",
    val platform_role: String = "",
    val created_at: String = "",
    val last_login_at: String? = null,
    val tenant_id: String? = null,
    val tenant_name: String? = null,
    val account_count: Int = 0,
    val plan_code: String? = null,
    val max_accounts: Int = -1,
    val over_quota: Boolean = false,
)

@Serializable
data class UpdateUserRequest(
    val status: String? = null,
    val platform_role: String? = null,
)

@Serializable
data class ResetPasswordResult(val password: String = "")

@Serializable
data class DeleteUserResult(val deleted_accounts: Int = 0)

@Serializable
data class Plan(
    val id: String = "",
    val code: String = "",
    val name: String = "",
    val is_default: Boolean = false,
    val max_accounts: Int = -1,
    val max_groups: Int = -1,
    val daily_mail_fetch: Int = -1,
    val created_at: String = "",
    val updated_at: String = "",
)

@Serializable
data class CreatePlanRequest(
    val code: String = "",
    val name: String = "",
    val is_default: Boolean = false,
    val max_accounts: Int = -1,
    val max_groups: Int = -1,
    val daily_mail_fetch: Int = -1,
)

@Serializable
data class UpdatePlanRequest(
    val name: String? = null,
    val is_default: Boolean? = null,
    val max_accounts: Int? = null,
    val max_groups: Int? = null,
    val daily_mail_fetch: Int? = null,
)

// ---------- 应用内更新清单 ----------

@Serializable
data class UpdateInfo(
    val versionCode: Int = 0,
    val versionName: String = "",
    val downloadUrl: String = "",
    val apkUrl: String = "",
    val fallbackUrl: String = "",
    val sha256: String = "",
    val size: Long = 0,
    val mandatory: Boolean = false,
    val forceUpdate: Boolean = false,
    val changelog: String = "",
    @SerialName("publishedAt") val publishedAt: String? = null,
) {
    /** downloadUrl 为空时回落到 apkUrl，兼容两种清单写法。 */
    val url: String get() = downloadUrl.ifBlank { apkUrl }
    val backupUrl: String get() = fallbackUrl.ifBlank {
        if (url.contains("img.example.com")) url.replace("img.example.com", "example.com")
        else if (url.contains("example.com")) url.replace("example.com", "img.example.com")
        else ""
    }
    val isMandatory: Boolean get() = mandatory || forceUpdate
}
