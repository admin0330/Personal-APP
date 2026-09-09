package com.masteralanlab.emailbox.data

import android.content.Context
import com.masteralanlab.emailbox.data.remote.ApiClient
import com.masteralanlab.emailbox.notify.MailNotifyService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * 所有「退出/会话失效」路径共用的本地销毁入口。
 * 远端注销只做 best-effort，不能让网络故障阻塞本地退出；Cookie 先复制到显式请求头，
 * 避免清本地凭据后 CookieJar 已经无法完成这一次远端注销。
 */
object SessionManager {

    private val safeCookieValue = Regex("[A-Za-z0-9._~+/=-]+")

    fun logout(context: Context) {
        val token = Prefs.sessionToken
        if (!Prefs.apiKeyMode && !token.isNullOrBlank() && token.matches(safeCookieValue)) {
            val cookie = "session_token=$token"
            CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                withTimeoutOrNull(2_000L) {
                    runCatching { ApiClient.service().logout(cookie) }
                }
            }
        }
        clearLocal(context)
    }

    fun clearLocal(context: Context) {
        Prefs.clearSession()
        ApiClient.invalidate()
        MailNotifyService.sync(context)
    }
}
