package com.masteralanlab.emailbox.ui.nav

object Route {
    const val Splash = "splash"
    const val Setup = "setup"
    const val Login = "login"

    /** 主页容器，内部按底部导航切换。 */
    const val Home = "home"

    // 详情页（挂在主导航栈上，覆盖底部栏）
    const val Mailbox = "mailbox/{accountId}/{email}"
    const val MessageDetail = "message/{accountId}/{messageId}?folder={folder}&idMode={idMode}&subject={subject}"
    const val AccountEdit = "account_edit?accountId={accountId}&groupId={groupId}"
    const val AccountImport = "account_import?groupId={groupId}"
    const val GroupEdit = "group_edit?groupId={groupId}"
    const val JobDetail = "job/{jobId}"
    const val AdminUsers = "admin_users"
    const val AdminPlans = "admin_plans"
    const val AdminAudit = "admin_audit"
    const val Settings = "settings"
    const val Profile = "profile"
    const val Members = "members"
    const val ApiKey = "api_key"
    const val Quota = "quota"
    const val Workspaces = "workspaces"
    const val RefreshLogs = "refresh_logs"
    const val MailSearch = "mail_search"
    const val Tokens = "tokens"
    const val SyncHealth = "sync_health"

    fun mailbox(accountId: String, email: String) =
        "mailbox/$accountId/${java.net.URLEncoder.encode(email, "UTF-8")}"

    fun messageDetail(accountId: String, messageId: String, folder: String, idMode: String, subject: String) =
        "message/$accountId/${enc(messageId)}?folder=${enc(folder)}&idMode=${enc(idMode)}&subject=${enc(subject)}"

    fun accountEdit(accountId: String? = null, groupId: String? = null): String {
        val parts = mutableListOf<String>()
        if (!accountId.isNullOrBlank()) parts += "accountId=$accountId"
        if (!groupId.isNullOrBlank()) parts += "groupId=$groupId"
        return if (parts.isEmpty()) "account_edit" else "account_edit?" + parts.joinToString("&")
    }

    fun accountImport(groupId: String? = null) =
        if (groupId.isNullOrBlank()) "account_import" else "account_import?groupId=$groupId"

    fun groupEdit(groupId: String? = null) =
        if (groupId.isNullOrBlank()) "group_edit" else "group_edit?groupId=$groupId"

    fun job(jobId: String) = "job/$jobId"

    private fun enc(v: String) = java.net.URLEncoder.encode(v, "UTF-8")
}
