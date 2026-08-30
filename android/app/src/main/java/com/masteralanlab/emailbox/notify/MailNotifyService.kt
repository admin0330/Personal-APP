package com.masteralanlab.emailbox.notify

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.masteralanlab.emailbox.MainActivity
import com.masteralanlab.emailbox.data.AccountsCache
import com.masteralanlab.emailbox.data.MailPreloadCache
import com.masteralanlab.emailbox.data.Prefs
import com.masteralanlab.emailbox.data.SessionBus
import com.masteralanlab.emailbox.data.remote.ApiClient
import com.masteralanlab.emailbox.data.remote.MailNotificationEvent
import com.masteralanlab.emailbox.data.remote.SseClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 服务器拉取模式的后台通知服务。
 *
 * 职责只有两件，都由阿里云侧的 5 分钟轮询驱动：
 * 1. 保持一条 `notifications/stream` SSE 长连接，收到 mail 事件时发一条
 *    「有新邮件、几封新信」的系统通知（不带邮箱地址或邮件内容）；
 * 2. 同一事件触发该账号的首屏邮件预取，用户点进收件箱时列表已经在本地缓存里。
 *
 * 生命周期跟随登录态与「拉取设置」：仅登录态 + 服务器拉取模式时运行；
 * 本地拉取模式下不启动（App 打开时现拉现取，不占后台电）。SSE 断线由
 * SseClient 内部有限退避重连；401/403/404 视为凭据或服务器状态变化，停服并把
 * 401 交给 SessionBus 走统一的重新绑定流程。
 */
class MailNotifyService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var streamJob: Job? = null
    private val accountEmails = mutableMapOf<String, String>()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannels(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val tenant = Prefs.tenantId
        if (!hasActiveSession() || tenant.isNullOrBlank() || !runningAllowed()) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (streamJob?.isActive != true) {
            streamJob = scope.launch { runStream(tenant) }
            scope.launch { refreshAccountEmails(tenant) }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun runningAllowed(): Boolean = Prefs.pullMode == Prefs.PULL_MODE_SERVER

    private suspend fun runStream(tenant: String) {
        var restarts = 0
        while (scope.isActive && runningAllowed()) {
            var endedNormally = true
            SseClient.notificationStream(tenant).collect { event ->
                when (event) {
                    MailNotificationEvent.Ready -> Unit

                    is MailNotificationEvent.Mail -> onMailEvent(tenant, event)

                    is MailNotificationEvent.Error -> {
                        if (event.status == 401) {
                            // 会话/Key 已失效：交给统一的重新绑定流程并停服
                            SessionBus.emitExpired(
                                if (Prefs.apiKeyMode) "API Key 无效或已重置，请重新绑定"
                                else "登录已失效，请重新登录",
                            )
                            stopSelf()
                        } else if (event.status in setOf(403, 404)) {
                            stopSelf()
                        } else {
                            endedNormally = false
                        }
                    }
                }
            }
            if (!scope.isActive || !runningAllowed()) break

            // 流被服务端/网络正常关闭（非 4xx 硬错误）：有限次重启，指数退避封顶 5 分钟
            restarts += 1
            if (restarts > MAX_STREAM_RESTARTS) {
                stopSelf()
                break
            }
            if (endedNormally) {
                // ready→正常关闭多为服务器发布/重启，退避短一些
                delay(30_000L * restarts)
            } else {
                delay(60_000L * restarts)
            }
        }
    }

    private suspend fun onMailEvent(tenant: String, event: MailNotificationEvent.Mail) {
        // 先预取首屏，通知只是入口；两者都不携带邮件内容
        MailPreloadCache.prefetchAccount(scope, tenant, event.accountId)
        // 标题优先取内存 → 磁盘缓存（毫秒级）；都没有才走网络补齐——通知绝不等网络
        var accountEmail = accountEmails[event.accountId]
            ?: AccountsCache.load(applicationContext, tenant)
                ?.firstOrNull { it.id == event.accountId }?.email
        if (accountEmail == null) {
            refreshAccountEmails(tenant)
            accountEmail = accountEmails[event.accountId] ?: "邮箱账号"
        }
        postMailNotification(
            event.accountId,
            accountEmail,
            event.newCount,
        )
    }

    private suspend fun refreshAccountEmails(tenant: String) {
        withContext(Dispatchers.IO) {
            runCatching {
                ApiClient.service().accounts(tenantId = tenant, page = 1, limit = 100)
            }.getOrNull()
        }?.data?.items?.forEach { accountEmails[it.id] = it.email }
    }

    private fun postMailNotification(accountId: String, email: String, newCount: Int) {
        val manager = getSystemService(NotificationManager::class.java) ?: return
        if (!notificationsAllowed(this)) return
        val text = if (newCount > 0) "收到 $newCount 封新邮件" else "邮箱有更新"
        val tap = PendingIntent.getActivity(
            this,
            accountId.hashCode(),
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra("open_account_id", accountId)
                .putExtra("open_email", email),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(this, CHANNEL_MAIL)
            .setSmallIcon(android.R.drawable.ic_dialog_email)
            .setContentTitle(email)
            .setContentText(text)
            .setContentIntent(tap)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_EMAIL)
            .build()
        runCatching {
            // 通知 ID 按账号隔离：同一邮箱连续几轮有新信时覆盖更新，不刷屏
            manager.notify(accountId.hashCode(), notification)
        }
    }

    companion object {

        private const val CHANNEL_MAIL = "new_mail"
        private const val MAX_STREAM_RESTARTS = 5

        fun createChannels(context: Context) {
            val manager = context.getSystemService(NotificationManager::class.java) ?: return
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_MAIL,
                    "新邮件提醒",
                    NotificationManager.IMPORTANCE_DEFAULT,
                ).apply { description = "服务器检测到新邮件时的提醒" },
            )
        }

        private fun notificationsAllowed(context: Context): Boolean {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
            return androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.POST_NOTIFICATIONS,
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        }

        /** 登录态 + 服务器拉取模式才允许启动；其余情况是幂等停服。 */
        fun sync(context: Context) {
            val shouldRun = hasActiveSession() && Prefs.pullMode == Prefs.PULL_MODE_SERVER &&
                !Prefs.tenantId.isNullOrBlank()
            val intent = Intent(context, MailNotifyService::class.java)
            if (shouldRun) {
                runCatching { context.startService(intent) }
            } else {
                context.stopService(intent)
            }
        }

        private fun hasActiveSession(): Boolean = runCatching { Prefs.hasSession }.getOrDefault(false)
    }
}
