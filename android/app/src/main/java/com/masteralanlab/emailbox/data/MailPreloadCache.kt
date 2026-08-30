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

/** 进程内首屏缓存；不落盘，也不把邮件内容写入日志。 */
data class MailPreload(
    val items: List<MessageItem>,
    val channel: String,
)

object MailPreloadCache {

    private data class Key(val tenant: String, val account: String, val folder: String)

    private val lock = Any()
    private val values = mutableMapOf<Key, MailPreload>()
    private val requestPermits = Semaphore(2)
    private var runGeneration = 0L

    fun get(tenant: String, account: String, folder: String): MailPreload? {
        synchronized(lock) { values[Key(tenant, account, folder)] }?.let { return it }
        val cached = SecureMailCache.messages(tenant, account, folder, 25)
        return cached.takeIf { it.isNotEmpty() }?.let { MailPreload(it, "offline-cache") }
    }

    fun put(
        tenant: String,
        account: String,
        folder: String,
        preload: MailPreload,
    ) {
        synchronized(lock) { values[Key(tenant, account, folder)] = preload }
        SecureMailCache.putMessages(tenant, account, preload.items)
    }

    /** 账号列表成功后调用。分批启动两个请求，保证并发上限为 2。 */
    fun prefetch(
        scope: CoroutineScope,
        tenant: String,
        accounts: List<MailAccount>,
    ) {
        val generation = synchronized(lock) {
            runGeneration += 1
            runGeneration
        }
        val preferCache = pullModeUsesServerCache()
        scope.launch(Dispatchers.IO) {
            accounts.distinctBy { it.id }.chunked(2).forEach { batch ->
                coroutineScope {
                    batch.map { account ->
                        launch {
                            val result = requestPermits.withPermit {
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
    }

    /** SSE 收到单账号事件时只预取该账号，仍使用同一代际守卫。 */
    fun prefetchAccount(
        scope: CoroutineScope,
        tenant: String,
        accountId: String,
    ) {
        val generation = synchronized(lock) {
            runGeneration += 1
            runGeneration
        }
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

    private fun pullModeUsesServerCache(): Boolean = Prefs.pullMode == Prefs.PULL_MODE_SERVER
}
