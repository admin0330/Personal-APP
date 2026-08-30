package com.masteralanlab.emailbox.data

import android.content.Context
import com.masteralanlab.emailbox.data.remote.AppJson
import com.masteralanlab.emailbox.data.remote.MailAccount
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import java.io.File

/**
 * 主页邮箱账号列表的磁盘缓存。
 *
 * 打开主页先直显缓存（秒开），后台再向服务器刷新——只读数据、不含任何凭据，
 * 只按租户隔离：登录态切到别的租户时旧缓存自然失效。存 cacheDir，
 * 系统清理与应用内「清理更新缓存」之外的逻辑互不影响。
 */
object AccountsCache {

    private const val FILE = "accounts_cache.json"

    @Serializable
    private data class Wrapper(
        val tenant: String,
        val accounts: List<MailAccount>,
    )

    fun load(context: Context, tenant: String): List<MailAccount>? = runCatching {
        val file = File(context.cacheDir, FILE)
        if (!file.exists()) return null
        val wrapper = AppJson.decodeFromString<Wrapper>(file.readText())
        if (wrapper.tenant != tenant) null else wrapper.accounts
    }.getOrNull()

    /** 原子写入：先写临时文件再改名，进程中途被杀不会留下半个 JSON。 */
    fun save(context: Context, tenant: String, accounts: List<MailAccount>) {
        runCatching {
            val file = File(context.cacheDir, FILE)
            val tmp = File(context.cacheDir, "$FILE.tmp")
            tmp.writeText(AppJson.encodeToString(Wrapper(tenant, accounts)))
            if (!tmp.renameTo(file)) {
                tmp.copyTo(file, overwrite = true)
                tmp.delete()
            }
        }
    }
}
