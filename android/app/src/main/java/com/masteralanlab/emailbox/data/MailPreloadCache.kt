package com.masteralanlab.emailbox.data

import com.masteralanlab.emailbox.data.remote.ApiResult
import com.masteralanlab.emailbox.data.remote.MailAccount
import com.masteralanlab.emailbox.data.remote.MessageItem
import com.masteralanlab.emailbox.data.remote.apiCall
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

/** 进程内首屏缓存；不落盘，也不把邮件内容写入日志。 */
data class MailPreload(
    val items: List<MessageItem>,
    val channel: String,
    val insights: Map<String, MailInsight> = emptyMap(),
)

object MailPreloadCache {

    private data class Key(val tenant: String, val account: String, val folder: String)

    private val lock = Any()
    private val values = mutableMapOf<Key, MailPreload>()
    private val requestPermits = Semaphore(2)
    private var runGeneration = 0L
    private var prefetchJob: kotlinx.coroutines.Job? = null
    private val fetchedAt = mutableMapOf<Key, Long>()

    suspend fun get(tenant: String, account: String, folder: String): MailPreload? {
        synchronized(lock) { values[Key(tenant, account, folder)] }?.let { return it }
        val cached = withContext(Dispatchers.IO) { SecureMailCache.cached(tenant, account, folder, 25) }
        return cached.takeIf { it.isNotEmpty() }?.let {
            MailPreload(
                items = it.map(CachedMail::item),
                channel = "offline-cache",
                insights = it.associate { row ->
                    MailIntelligence.insightKey(row.item) to MailInsight(row.category, row.otp)
                },
            )
        }
    }

    suspend fun put(
        tenant: String,
        account: String,
        folder: String,
        preload: MailPreload,
    ) {
        val insights = if (preload.insights.isNotEmpty()) preload.insights else {
            withContext(Dispatchers.Default) {
                preload.items.associateBy(MailIntelligence::insightKey) {
                    MailIntelligence.analyze(it, overrideCategory = Prefs.categoryOverride(it.from))
                }
            }
        }
        withContext(Dispatchers.IO) {
            SecureMailCache.putMessages(tenant, account, preload.items, insights)
        }
        synchronized(lock) {
            values[Key(tenant, account, folder)] = preload.copy(insights = insights)
            fetchedAt[Key(tenant, account, folder)] = android.os.SystemClock.elapsedRealtime()
        }
    }

    /** 两个请求槽连续补位；慢账号不再阻塞整批后续账号。 */
    fun prefetch(
        scope: CoroutineScope,
        tenant: String,
        accounts: List<MailAccount>,
    ) {
        val generation = synchronized(lock) { runGeneration }
        prefetchJob?.cancel()
        val preferCache = pullModeUsesServerCache()
        prefetchJob = scope.launch(Dispatchers.IO) {
                coroutineScope {
                    accounts.distinctBy { it.id }.map { account ->
                        launch {
                            val result = requestPermits.withPermit {
                                val fresh = synchronized(lock) {
                                    fetchedAt[Key(tenant, account.id, "inbox")]?.let {
                                        android.os.SystemClock.elapsedRealtime() - it < 30_000
                                    } == true
                                }
                                if (fresh) return@launch
                                apiCall {
                                    messages(
                                        tenantId = tenant,
                                        accountId = account.id,
                                        folder = "inbox",
                                        skip = 0,
                                        top = 25,
                                        preferCache = preferCache,
                                    )
                                }
                            }
                            if (result is ApiResult.Success && isCurrent(generation)) {
                                put(
                                    tenant = tenant,
                                    account = account.id,
                                    folder = "inbox",
                                    preload = MailPreload(result.data.items, result.data.channel),
                                )
                            }
                        }
                    }.joinAll()
                }
        }
    }

    /** SSE 收到单账号事件时只预取该账号，仍使用同一代际守卫。 */
    fun prefetchAccount(
        scope: CoroutineScope,
        tenant: String,
        accountId: String,
    ) {
        // 单账号事件不应使其他账号的在途预取失效。
        val generation = synchronized(lock) { runGeneration }
        val preferCache = pullModeUsesServerCache()
        scope.launch(Dispatchers.IO) {
            val result = requestPermits.withPermit {
                apiCall {
                    messages(
                        tenantId = tenant,
                        accountId = accountId,
                        folder = "inbox",
                        skip = 0,
                        top = 25,
                        preferCache = preferCache,
                    )
                }
            }
            if (result is ApiResult.Success && isCurrent(generation)) {
                put(
                    tenant = tenant,
                    account = accountId,
                    folder = "inbox",
                    preload = MailPreload(result.data.items, result.data.channel),
                )
            }
        }
    }

    private fun isCurrent(generation: Long): Boolean = synchronized(lock) {
        generation == runGeneration
    }

    /** 退出或切换账号时清掉进程内预加载，且让已经在途的请求结果失效。 */
    fun clear() {
        prefetchJob?.cancel()
        synchronized(lock) {
            values.clear()
            fetchedAt.clear()
            runGeneration += 1
        }
    }

    private fun pullModeUsesServerCache(): Boolean = Prefs.pullMode == Prefs.PULL_MODE_SERVER
}
