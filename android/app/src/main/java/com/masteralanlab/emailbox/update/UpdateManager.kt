package com.masteralanlab.emailbox.update

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.masteralanlab.emailbox.BuildConfig
import com.masteralanlab.emailbox.data.Prefs
import com.masteralanlab.emailbox.data.remote.ApiClient
import com.masteralanlab.emailbox.data.remote.AppJson
import com.masteralanlab.emailbox.data.remote.UpdateInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.CacheControl
import okhttp3.Request
import java.io.File
import java.security.MessageDigest

/**
 * 应用内更新：拉取更新清单 → 比对 versionCode → 下载 APK → 校验 SHA-256 → 交给系统安装器。
 *
 * 清单地址固定在 ym3861.cn，与登录的 Emailbox 服务器无关；请求禁用一切缓存，
 * 否则运营商或 OkHttp 的缓存会让用户拿不到刚发布的版本。
 */
object UpdateManager {

    const val STABLE_MANIFEST_URL = "https://ym3861.cn/emailbox-updates/latest.json"
    const val TEST_MANIFEST_URL = "https://ym3861.cn/emailbox-updates/beta.json"

    /**
     * 两个渠道都由同一台服务器托管，普通用户不能填写任意清单地址。
     */
    fun manifestUrl(): String =
        if (Prefs.updateChannel == Prefs.UPDATE_CHANNEL_TEST) TEST_MANIFEST_URL else STABLE_MANIFEST_URL

    /** 当前生效渠道的展示名。 */
    fun channelLabel(): String =
        if (Prefs.updateChannel == Prefs.UPDATE_CHANNEL_TEST) "测试版" else "稳定版"

    /**
     * 清单有效性判定：只有远端 versionCode 严格更大且下载地址齐全时才提示更新。
     * 清单不可用、版本相同或更低都不打扰用户；这里故意不校验 sha256——
     * 校验失败要发生在「下载完成之后、交给安装器之前」（见 download），提前判掉反而
     * 会让一个发布时写错了摘要的正常版本完全无法提示。
     */
    internal fun evaluateManifest(info: UpdateInfo, currentVersionCode: Int): UpdateInfo? {
        if (info.versionCode <= currentVersionCode) return null
        if (!info.url.startsWith("https://", ignoreCase = true)) return null
        if (info.sha256.trim().length != 64) return null
        return info
    }

    /** 64 位十六进制 SHA-256，清单字段不合格式时在下载前就中止。 */
    internal fun isValidSha256(value: String): Boolean =
        value.trim().matches(Regex("[0-9a-fA-F]{64}"))

    /** 拉取最新清单。返回 null 表示当前已是最新或清单不可用。 */
    suspend fun check(): UpdateInfo? = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(manifestUrl())
            .cacheControl(CacheControl.Builder().noCache().noStore().build())
            .header("Cache-Control", "no-cache")
            .header("Accept", "application/json")
            .build()

        runCatching {
            ApiClient.plainClient().newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext null
                val body = resp.body?.string()?.takeIf { it.isNotBlank() } ?: return@withContext null
                val info = AppJson.decodeFromString<UpdateInfo>(body)
                evaluateManifest(info, BuildConfig.VERSION_CODE)
            }
        }.getOrNull()
    }

    /** 更新缓存目录（成品 APK 与断点文件）。 */
    fun cacheDir(context: Context): File = File(context.cacheDir, "updates")

    /** 清理更新缓存，返回释放的字节数。 */
    fun clearUpdateCache(context: Context): Long = runCatching {
        var freed = 0L
        cacheDir(context).listFiles()?.forEach {
            freed += it.length()
            it.delete()
        }
        freed
    }.getOrDefault(0L)

    /** 更新缓存占用的字节数。 */
    fun updateCacheSize(context: Context): Long =
        cacheDir(context).listFiles()?.sumOf { it.length() } ?: 0L

    /** 下载 APK 到私有缓存目录，回调 0f~1f 进度。支持断点续传与成品复用。 */
    @Throws(Exception::class)
    suspend fun download(
        context: Context,
        info: UpdateInfo,
        onProgress: (Float) -> Unit,
    ): File = withContext(Dispatchers.IO) {
        val expectedSha256 = info.sha256.trim()
        require(isValidSha256(expectedSha256)) {
            "更新清单缺少有效 SHA-256，已中止安装"
        }
        require(info.url.startsWith("https://", ignoreCase = true)) {
            "更新下载地址必须使用 HTTPS，已中止安装"
        }
        val dir = cacheDir(context).apply { mkdirs() }
        val target = File(dir, "emailbox-${info.versionName}-${info.versionCode}.apk")
        val part = File(dir, target.name + ".part")

        // 只清理其它版本的残留，当前版本的成品与断点文件都保留
        dir.listFiles()?.forEach { if (it != target && it != part) it.delete() }

        // 成品已在且校验通过：直接复用，不重新下载（误点关闭后再检查更新时走这里）
        if (target.exists() && sha256(target).equals(expectedSha256, ignoreCase = true)) {
            onProgress(1f)
            return@withContext target
        }
        if (target.exists()) target.delete()

        // 断点续传：从已有 .part 的长度继续；文件异常超长时丢弃重来
        var start = if (part.exists()) part.length() else 0L
        if (start > 0 && info.size > 0 && start >= info.size) {
            part.delete()
            start = 0L
        }

        val request = Request.Builder().url(info.url).header("Accept-Encoding", "identity")
            .apply { if (start > 0) header("Range", "bytes=$start-") }
            .build()
        ApiClient.plainClient().newCall(request).execute().use { resp ->
            when {
                // 206 = 服务器支持续传，追加写入
                resp.code == 206 && start > 0 -> Unit
                // 200 = 服务器不支持 Range 或断点已失效，从头开始
                resp.code == 200 -> {
                    start = 0L
                    if (part.exists()) part.delete()
                }

                else -> error("下载失败：HTTP ${resp.code}")
            }

            val body = resp.body ?: error("下载失败：空响应")
            val total = (start + body.contentLength().coerceAtLeast(0))
                .takeIf { it > 0 } ?: info.size.takeIf { it > 0 } ?: 0L

            body.byteStream().use { input ->
                java.io.FileOutputStream(part, start > 0).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var read: Int
                    var done = start
                    while (input.read(buffer).also { read = it } != -1) {
                        output.write(buffer, 0, read)
                        done += read
                        if (total > 0) onProgress((done.toFloat() / total).coerceIn(0f, 1f))
                    }
                    output.flush()
                }
            }
        }
        onProgress(1f)

        val actual = sha256(part)
        if (!actual.equals(expectedSha256, ignoreCase = true)) {
            part.delete()
            error("APK 校验失败，已中止安装")
        }
        if (!part.renameTo(target)) {
            part.copyTo(target, overwrite = true)
            part.delete()
        }
        target
    }

    fun sha256(file: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(128 * 1024)
            var read: Int
            while (input.read(buffer).also { read = it } != -1) md.update(buffer, 0, read)
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    /** 判断本 App 是否已被授予「安装未知应用」权限。 */
    fun canInstall(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return true
        return context.packageManager.canRequestPackageInstalls()
    }

    /** 跳到系统设置页，让用户给本 App 开启安装权限。 */
    fun openInstallPermissionSetting(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val intent = Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:${context.packageName}"),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }

    /** 构造安装 Intent。交给系统安装器，App 自身不需要任何安装权限以外的能力。 */
    fun installIntent(context: Context, apk: File): Intent {
        val uri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apk,
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true)
        }
        // Android 6+ 上部分安装器不会自动继承 URI 授权，显式授予给所有候选接收者
        val candidates = context.packageManager.queryIntentActivities(
            intent,
            PackageManager.MATCH_DEFAULT_ONLY,
        )
        for (info in candidates) {
            runCatching {
                context.grantUriPermission(
                    info.activityInfo.packageName,
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
        }
        return intent
    }
}
