package com.masteralanlab.emailbox.data.remote

import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Streaming

/**
 * Emailbox 服务端接口。baseUrl 形如 https://host/emailbox/api/v1/ （必须以 / 结尾）。
 * 管理员侧的跨租户邮箱路由与用户侧完全同构，复用同一批方法。
 */
interface ApiService {

    // ---------------- 平台同步健康 ----------------

    @GET("admin/sync-health")
    suspend fun syncHealth(): ApiResp<SyncHealth>

    @PUT("admin/sync-health/proxy")
    suspend fun switchProxyNode(@Body body: SwitchProxyNodeRequest): ApiResp<Unit>

    // ---------------- 鉴权 ----------------

    @POST("auth/register")
    suspend fun register(@Body body: RegisterRequest): ApiResp<AuthResponse>

    @POST("auth/invite/redeem")
    suspend fun redeemInvite(@Body body: InviteRegisterRequest): ApiResp<AuthResponse>

    @POST("auth/login")
    suspend fun login(@Body body: LoginRequest): ApiResp<AuthResponse>

    @POST("auth/logout")
    suspend fun logout(@Header("Cookie") cookie: String? = null): ApiResp<Unit>

    @GET("auth/session")
    suspend fun session(): ApiResp<AuthResponse>

    @GET("auth/key-session")
    suspend fun keySession(@Header("Authorization") authorization: String): ApiResp<KeySessionResponse>

    // ---------------- 个人记账 ----------------

    @GET("tenants/{tenantID}/ledger/transactions")
    suspend fun ledgerTransactions(
        @Path("tenantID") tenantId: String,
        @Query("month") month: String,
        @Query("limit") limit: Int = 100,
        @Query("offset") offset: Int = 0,
    ): ApiResp<List<LedgerTransaction>>

    @POST("tenants/{tenantID}/ledger/transactions")
    suspend fun createLedgerTransaction(
        @Path("tenantID") tenantId: String,
        @Body body: CreateLedgerTransactionRequest,
    ): ApiResp<LedgerTransaction>

    @PATCH("tenants/{tenantID}/ledger/transactions/{id}")
    suspend fun updateLedgerTransaction(
        @Path("tenantID") tenantId: String,
        @Path("id") id: String,
        @Body body: UpdateLedgerTransactionRequest,
    ): ApiResp<LedgerTransaction>

    @DELETE("tenants/{tenantID}/ledger/transactions/{id}")
    suspend fun deleteLedgerTransaction(
        @Path("tenantID") tenantId: String,
        @Path("id") id: String,
    ): ApiResp<Unit>

    @GET("tenants/{tenantID}/ledger/summary")
    suspend fun ledgerSummary(
        @Path("tenantID") tenantId: String,
        @Query("month") month: String,
    ): ApiResp<LedgerSummary>

    // ---------------- 笔记 ----------------

    @GET("tenants/{tenantID}/notes")
    suspend fun notes(@Path("tenantID") tenantId: String): ApiResp<List<Note>>

    @POST("tenants/{tenantID}/notes")
    suspend fun createNote(
        @Path("tenantID") tenantId: String,
        @Body body: CreateNoteRequest,
    ): ApiResp<Note>

    @PATCH("tenants/{tenantID}/notes/{noteID}")
    suspend fun updateNote(
        @Path("tenantID") tenantId: String,
        @Path("noteID") noteId: String,
        @Body body: UpdateNoteRequest,
    ): ApiResp<Note>

    @DELETE("tenants/{tenantID}/notes/{noteID}")
    suspend fun deleteNote(
        @Path("tenantID") tenantId: String,
        @Path("noteID") noteId: String,
    ): ApiResp<Unit>

    // ---------------- 用户 ----------------

    @GET("user/profile")
    suspend fun profile(): ApiResp<UserResp>

    @PATCH("user/profile")
    suspend fun updateProfile(@Body body: UpdateProfileRequest): ApiResp<UserResp>

    @POST("user/change-password")
    suspend fun changePassword(@Body body: ChangePasswordRequest): ApiResp<Unit>

    // ---------------- 租户 ----------------

    @GET("tenants")
    suspend fun tenants(): ApiResp<List<Tenant>>

    @POST("tenants")
    suspend fun createTenant(@Body body: CreateTenantRequest): ApiResp<Tenant>

    @GET("tenants/{tenantID}")
    suspend fun tenant(@Path("tenantID") tenantId: String): ApiResp<Tenant>

    @PATCH("tenants/{tenantID}")
    suspend fun updateTenant(
        @Path("tenantID") tenantId: String,
        @Body body: UpdateTenantRequest,
    ): ApiResp<Tenant>

    @DELETE("tenants/{tenantID}")
    suspend fun deleteTenant(@Path("tenantID") tenantId: String): ApiResp<Unit>

    @POST("tenants/{tenantID}/select")
    suspend fun selectTenant(@Path("tenantID") tenantId: String): ApiResp<Unit>

    @GET("tenants/{tenantID}/quota")
    suspend fun quota(@Path("tenantID") tenantId: String): ApiResp<QuotaUsage>

    @GET("tenants/{tenantID}/api-key")
    suspend fun apiKey(@Path("tenantID") tenantId: String): ApiResp<ApiKeyView>

    @POST("tenants/{tenantID}/api-key/reset")
    suspend fun resetApiKey(@Path("tenantID") tenantId: String): ApiResp<ApiKeyView>

    // ---------------- 成员 ----------------

    @GET("tenants/{tenantID}/members")
    suspend fun members(@Path("tenantID") tenantId: String): ApiResp<List<TenantMember>>

    @POST("tenants/{tenantID}/members")
    suspend fun addMember(
        @Path("tenantID") tenantId: String,
        @Body body: AddMemberRequest,
    ): ApiResp<TenantMember>

    @PATCH("tenants/{tenantID}/members/{userID}")
    suspend fun updateMember(
        @Path("tenantID") tenantId: String,
        @Path("userID") userId: String,
        @Body body: UpdateMemberRoleRequest,
    ): ApiResp<Unit>

    @DELETE("tenants/{tenantID}/members/{userID}")
    suspend fun deleteMember(
        @Path("tenantID") tenantId: String,
        @Path("userID") userId: String,
    ): ApiResp<Unit>

    // ---------------- 平台邀请码 ----------------

    @POST("admin/invites")
    suspend fun createInvite(@Body body: CreateInviteRequest): ApiResp<SignupInviteCreated>

    // ---------------- 分组 ----------------

    @GET("tenants/{tenantID}/mail/groups")
    suspend fun groups(@Path("tenantID") tenantId: String): ApiResp<List<MailGroup>>

    @POST("tenants/{tenantID}/mail/groups")
    suspend fun createGroup(
        @Path("tenantID") tenantId: String,
        @Body body: CreateGroupRequest,
    ): ApiResp<MailGroup>

    @PATCH("tenants/{tenantID}/mail/groups/{groupID}")
    suspend fun updateGroup(
        @Path("tenantID") tenantId: String,
        @Path("groupID") groupId: String,
        @Body body: UpdateGroupRequest,
    ): ApiResp<MailGroup>

    @DELETE("tenants/{tenantID}/mail/groups/{groupID}")
    suspend fun deleteGroup(
        @Path("tenantID") tenantId: String,
        @Path("groupID") groupId: String,
    ): ApiResp<Unit>

    @POST("tenants/{tenantID}/mail/groups/reorder")
    suspend fun reorderGroups(
        @Path("tenantID") tenantId: String,
        @Body body: ReorderGroupsRequest,
    ): ApiResp<Unit>

    // ---------------- 账号 ----------------

    @GET("tenants/{tenantID}/mail/accounts")
    suspend fun accounts(
        @Path("tenantID") tenantId: String,
        @Query("q") q: String? = null,
        @Query("group_id") groupId: String? = null,
        @Query("status") status: String? = null,
        @Query("refresh_status") refreshStatus: String? = null,
        @Query("provider") provider: String? = null,
        @Query("sort") sort: String? = null,
        @Query("order") order: String? = null,
        @Query("page") page: Int? = null,
        @Query("limit") limit: Int? = null,
    ): ApiResp<Paged<MailAccount>>

    @GET("tenants/{tenantID}/mail/accounts/{accountID}")
    suspend fun account(
        @Path("tenantID") tenantId: String,
        @Path("accountID") accountId: String,
    ): ApiResp<MailAccount>

    @POST("tenants/{tenantID}/mail/accounts")
    suspend fun createAccount(
        @Path("tenantID") tenantId: String,
        @Body body: CreateAccountRequest,
    ): ApiResp<MailAccount>

    @PATCH("tenants/{tenantID}/mail/accounts/{accountID}")
    suspend fun updateAccount(
        @Path("tenantID") tenantId: String,
        @Path("accountID") accountId: String,
        @Body body: UpdateAccountRequest,
    ): ApiResp<MailAccount>

    @DELETE("tenants/{tenantID}/mail/accounts/{accountID}")
    suspend fun deleteAccount(
        @Path("tenantID") tenantId: String,
        @Path("accountID") accountId: String,
    ): ApiResp<Unit>

    @POST("tenants/{tenantID}/mail/accounts/import")
    suspend fun importAccounts(
        @Path("tenantID") tenantId: String,
        @Body body: ImportAccountsRequest,
    ): ApiResp<ImportResult>

    @Streaming
    @POST("tenants/{tenantID}/mail/accounts/export")
    suspend fun exportAccounts(
        @Path("tenantID") tenantId: String,
        @Body body: ExportAccountsRequest,
    ): Response<ResponseBody>

    @POST("tenants/{tenantID}/mail/accounts/batch/move")
    suspend fun batchMove(
        @Path("tenantID") tenantId: String,
        @Body body: BatchMoveRequest,
    ): ApiResp<BatchResult>

    @POST("tenants/{tenantID}/mail/accounts/batch/status")
    suspend fun batchStatus(
        @Path("tenantID") tenantId: String,
        @Body body: BatchStatusRequest,
    ): ApiResp<BatchResult>

    @POST("tenants/{tenantID}/mail/accounts/batch/proxy")
    suspend fun batchProxy(
        @Path("tenantID") tenantId: String,
        @Body body: BatchProxyRequest,
    ): ApiResp<BatchResult>

    @POST("tenants/{tenantID}/mail/accounts/batch/delete")
    suspend fun batchDelete(
        @Path("tenantID") tenantId: String,
        @Body body: BatchIdsRequest,
    ): ApiResp<BatchResult>

    // ---------------- 邮件 ----------------

    @GET("tenants/{tenantID}/mail/accounts/{accountID}/messages")
    suspend fun messages(
        @Path("tenantID") tenantId: String,
        @Path("accountID") accountId: String,
        @Query("folder") folder: String? = null,
        @Query("skip") skip: Int? = null,
        @Query("top") top: Int? = null,
        @Query("prefer_cache") preferCache: Boolean? = null,
    ): ApiResp<MessageListResult>

    @GET("tenants/{tenantID}/mail/accounts/{accountID}/messages/{messageID}")
    suspend fun messageDetail(
        @Path("tenantID") tenantId: String,
        @Path("accountID") accountId: String,
        @Path("messageID") messageId: String,
        @Query("folder") folder: String? = null,
        @Query("id_mode") idMode: String? = null,
    ): ApiResp<MessageDetail>

    @Streaming
    @GET("tenants/{tenantID}/mail/accounts/{accountID}/messages/{messageID}/attachments/{attachmentID}")
    suspend fun attachment(
        @Path("tenantID") tenantId: String,
        @Path("accountID") accountId: String,
        @Path("messageID") messageId: String,
        @Path("attachmentID") attachmentId: String,
        @Query("folder") folder: String? = null,
        @Query("id_mode") idMode: String? = null,
    ): Response<ResponseBody>

    @Streaming
    @GET("tenants/{tenantID}/mail/accounts/{accountID}/messages/{messageID}/attachments.zip")
    suspend fun attachmentsZip(
        @Path("tenantID") tenantId: String,
        @Path("accountID") accountId: String,
        @Path("messageID") messageId: String,
        @Query("folder") folder: String? = null,
        @Query("id_mode") idMode: String? = null,
    ): Response<ResponseBody>

    @POST("tenants/{tenantID}/mail/accounts/{accountID}/messages/read")
    suspend fun markRead(
        @Path("tenantID") tenantId: String,
        @Path("accountID") accountId: String,
        @Body body: MessageBatchRequest,
    ): ApiResp<MailBatchResult>

    @POST("tenants/{tenantID}/mail/accounts/{accountID}/messages/delete")
    suspend fun deleteMessages(
        @Path("tenantID") tenantId: String,
        @Path("accountID") accountId: String,
        @Body body: MessageBatchRequest,
    ): ApiResp<MailBatchResult>

    // ---------------- 令牌刷新与任务 ----------------

    @POST("tenants/{tenantID}/mail/accounts/{accountID}/token/refresh")
    suspend fun refreshToken(
        @Path("tenantID") tenantId: String,
        @Path("accountID") accountId: String,
    ): ApiResp<Unit>

    @POST("tenants/{tenantID}/mail/jobs/token-refresh")
    suspend fun submitRefreshJob(
        @Path("tenantID") tenantId: String,
        @Body body: SubmitRefreshRequest,
    ): ApiResp<Job>

    @GET("tenants/{tenantID}/mail/jobs")
    suspend fun jobs(
        @Path("tenantID") tenantId: String,
        @Query("type") type: String? = null,
        @Query("status") status: String? = null,
        @Query("page") page: Int? = null,
        @Query("limit") limit: Int? = null,
    ): ApiResp<Paged<Job>>

    @GET("tenants/{tenantID}/mail/jobs/{jobID}")
    suspend fun job(
        @Path("tenantID") tenantId: String,
        @Path("jobID") jobId: String,
    ): ApiResp<Job>

    @GET("tenants/{tenantID}/mail/jobs/{jobID}/items")
    suspend fun jobItems(
        @Path("tenantID") tenantId: String,
        @Path("jobID") jobId: String,
        @Query("status") status: String? = null,
        @Query("page") page: Int? = null,
        @Query("limit") limit: Int? = null,
    ): ApiResp<Paged<JobItem>>

    @POST("tenants/{tenantID}/mail/jobs/{jobID}/stop")
    suspend fun stopJob(
        @Path("tenantID") tenantId: String,
        @Path("jobID") jobId: String,
    ): ApiResp<Unit>

    @GET("tenants/{tenantID}/mail/refresh/stats")
    suspend fun refreshStats(@Path("tenantID") tenantId: String): ApiResp<RefreshStats>

    @GET("tenants/{tenantID}/mail/refresh/logs")
    suspend fun refreshLogs(
        @Path("tenantID") tenantId: String,
        @Query("status") status: String? = null,
        @Query("account_id") accountId: String? = null,
        @Query("job_id") jobId: String? = null,
        @Query("page") page: Int? = null,
        @Query("limit") limit: Int? = null,
    ): ApiResp<Paged<RefreshLog>>

    // ---------------- OAuth ----------------

    @POST("tenants/{tenantID}/mail/accounts/{accountID}/oauth/start")
    suspend fun oauthStart(
        @Path("tenantID") tenantId: String,
        @Path("accountID") accountId: String,
    ): ApiResp<OAuthStartResult>

    @POST("tenants/{tenantID}/mail/accounts/{accountID}/oauth/complete")
    suspend fun oauthComplete(
        @Path("tenantID") tenantId: String,
        @Path("accountID") accountId: String,
        @Body body: OAuthCompleteRequest,
    ): ApiResp<OAuthCompleteResult>

    // ---------------- 管理员 ----------------

    @GET("admin/stats")
    suspend fun adminStats(): ApiResp<PlatformStats>

    @GET("admin/audit")
    suspend fun adminAudit(
        @Query("tenant_id") tenantId: String? = null,
        @Query("actor_user_id") actorUserId: String? = null,
        @Query("actor_kind") actorKind: String? = null,
        @Query("action") action: String? = null,
        @Query("page") page: Int? = null,
        @Query("limit") limit: Int? = null,
    ): ApiResp<Paged<AuditLog>>

    @GET("admin/users")
    suspend fun adminUsers(
        @Query("q") q: String? = null,
        @Query("status") status: String? = null,
        @Query("platform_role") platformRole: String? = null,
        @Query("page") page: Int? = null,
        @Query("limit") limit: Int? = null,
    ): ApiResp<Paged<AdminUser>>

    @GET("admin/users/{userID}")
    suspend fun adminUser(@Path("userID") userId: String): ApiResp<AdminUser>

    @PATCH("admin/users/{userID}")
    suspend fun updateAdminUser(
        @Path("userID") userId: String,
        @Body body: UpdateUserRequest,
    ): ApiResp<AdminUser>

    @POST("admin/users/{userID}/reset-password")
    suspend fun resetUserPassword(@Path("userID") userId: String): ApiResp<ResetPasswordResult>

    @DELETE("admin/users/{userID}")
    suspend fun deleteAdminUser(@Path("userID") userId: String): ApiResp<DeleteUserResult>

    @GET("admin/plans")
    suspend fun plans(): ApiResp<List<Plan>>

    @POST("admin/plans")
    suspend fun createPlan(@Body body: CreatePlanRequest): ApiResp<Plan>

    @PATCH("admin/plans/{planID}")
    suspend fun updatePlan(
        @Path("planID") planId: String,
        @Body body: UpdatePlanRequest,
    ): ApiResp<Plan>

    @DELETE("admin/plans/{planID}")
    suspend fun deletePlan(@Path("planID") planId: String): ApiResp<Unit>

    @GET("admin/tenants/{tenantID}/quota")
    suspend fun adminQuota(@Path("tenantID") tenantId: String): ApiResp<QuotaUsage>

    @PATCH("admin/tenants/{tenantID}/quota")
    suspend fun updateAdminQuota(
        @Path("tenantID") tenantId: String,
        @Body body: UpdateQuotaRequest,
    ): ApiResp<QuotaUsage>

    // 跨租户邮箱管理：路径不同但在同一个 Retrofit 实例下，用 path 前缀区分
    @GET("admin/tenants/{tenantID}/mail/groups")
    suspend fun adminGroups(@Path("tenantID") tenantId: String): ApiResp<List<MailGroup>>

    @GET("admin/tenants/{tenantID}/mail/accounts")
    suspend fun adminAccounts(
        @Path("tenantID") tenantId: String,
        @Query("q") q: String? = null,
        @Query("group_id") groupId: String? = null,
        @Query("status") status: String? = null,
        @Query("refresh_status") refreshStatus: String? = null,
        @Query("provider") provider: String? = null,
        @Query("sort") sort: String? = null,
        @Query("order") order: String? = null,
        @Query("page") page: Int? = null,
        @Query("limit") limit: Int? = null,
    ): ApiResp<Paged<MailAccount>>

    @GET("admin/tenants/{tenantID}/mail/accounts/{accountID}/messages")
    suspend fun adminMessages(
        @Path("tenantID") tenantId: String,
        @Path("accountID") accountId: String,
        @Query("folder") folder: String? = null,
        @Query("skip") skip: Int? = null,
        @Query("top") top: Int? = null,
        @Query("prefer_cache") preferCache: Boolean? = null,
    ): ApiResp<MessageListResult>

    @GET("admin/tenants/{tenantID}/mail/accounts/{accountID}/messages/{messageID}")
    suspend fun adminMessageDetail(
        @Path("tenantID") tenantId: String,
        @Path("accountID") accountId: String,
        @Path("messageID") messageId: String,
        @Query("folder") folder: String? = null,
        @Query("id_mode") idMode: String? = null,
    ): ApiResp<MessageDetail>

    @GET("admin/tenants/{tenantID}/mail/refresh/stats")
    suspend fun adminRefreshStats(@Path("tenantID") tenantId: String): ApiResp<RefreshStats>
}
