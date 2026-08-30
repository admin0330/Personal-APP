package com.masteralanlab.emailbox.data

import android.content.Context
import com.masteralanlab.emailbox.data.remote.AppJson
import com.masteralanlab.emailbox.data.remote.Job
import com.masteralanlab.emailbox.data.remote.RefreshLog
import com.masteralanlab.emailbox.data.remote.RefreshStats
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import java.io.File

/** 令牌页快照缓存：进页先直显，后台静默刷新。按租户隔离，只存展示数据。 */
object TokensCache {

    @Serializable
    internal data class Snapshot(
        val tenant: String,
        val stats: RefreshStats?,
        val jobs: List<Job>,
        val jobPages: Int,
        val logs: List<RefreshLog>,
    )

    private fun file(context: Context) = File(context.cacheDir, "tokens_cache.json")

    internal fun load(context: Context, tenant: String): Snapshot? = runCatching {
        val f = file(context)
        if (!f.exists()) return null
        val s = AppJson.decodeFromString<Snapshot>(f.readText())
        if (s.tenant != tenant) null else s
    }.getOrNull()

    fun save(context: Context, tenant: String, stats: RefreshStats?, jobs: List<Job>, jobPages: Int, logs: List<RefreshLog>) {
        runCatching {
            val f = file(context)
            val tmp = File(context.cacheDir, f.name + ".tmp")
            tmp.writeText(AppJson.encodeToString(Snapshot(tenant, stats, jobs, jobPages, logs)))
            if (!tmp.renameTo(f)) {
                tmp.copyTo(f, overwrite = true)
                tmp.delete()
            }
        }
    }
}
