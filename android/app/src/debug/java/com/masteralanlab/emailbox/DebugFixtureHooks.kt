package com.masteralanlab.emailbox

import android.content.Context
import com.masteralanlab.emailbox.data.MailPreload
import com.masteralanlab.emailbox.data.MailPreloadCache
import com.masteralanlab.emailbox.data.Prefs
import com.masteralanlab.emailbox.data.SecureMailCache
import com.masteralanlab.emailbox.data.remote.Attachment
import com.masteralanlab.emailbox.data.remote.MessageDetail
import com.masteralanlab.emailbox.data.remote.MessageItem
import kotlinx.coroutines.runBlocking

/** 仅 debug APK 可调用的本地验收数据；不包含真实凭据，也不进入 release。 */
class DebugFixtureHooks private constructor() {
    companion object {
        @JvmStatic
        fun install(context: Context) {
            Prefs.init(context)
            Prefs.apiKeyMode = false
            Prefs.sessionToken = "debug-fixture-only"
            Prefs.serverUrl = "https://example.invalid/emailbox"
            Prefs.tenantId = "debug-tenant"
            Prefs.tenantName = "本地验收空间"
            Prefs.pullMode = Prefs.PULL_MODE_LOCAL
            Prefs.offlineCacheEnabled = true
            Prefs.blockRemoteImages = true

            SecureMailCache.clear()
            MailPreloadCache.clear()

            val item = MessageItem(
                id = "debug-message-otp",
                id_mode = "uid",
                folder = "inbox",
                subject = "登录验证码与安全提醒",
                from = "Ym1r 验证服务 <no-reply@example.invalid>",
                to = "tester@example.invalid",
                received_at = "2026-09-02T08:00:00Z",
                is_read = false,
                has_attachments = true,
                body_preview = "你的验证码为 482913，5 分钟内有效。",
            )
            val detail = MessageDetail(
                id = item.id,
                id_mode = item.id_mode,
                folder = item.folder,
                subject = item.subject,
                from = item.from,
                to = item.to,
                received_at = item.received_at,
                is_read = item.is_read,
                has_attachments = true,
                body_preview = item.body_preview,
                body = """
                    <h2>登录验证码</h2>
                    <p>你的验证码为 <strong>482913</strong>，5 分钟内有效。</p>
                    <p>本次登录设备：Pixel 9。若非本人操作，请立即修改密码。</p>
                """.trimIndent(),
                body_type = "html",
                attachments = listOf(
                    Attachment(
                        id = "debug-attachment",
                        name = "安全说明.pdf",
                        content_type = "application/pdf",
                        size = 64 * 1024,
                    ),
                ),
            )
            runBlocking {
                MailPreloadCache.put(
                    tenant = "debug-tenant",
                    account = "debug-account",
                    folder = "inbox",
                    preload = MailPreload(listOf(item), "offline-cache"),
                )
            }
            SecureMailCache.putDetail("debug-tenant", "debug-account", detail)
        }
    }
}
