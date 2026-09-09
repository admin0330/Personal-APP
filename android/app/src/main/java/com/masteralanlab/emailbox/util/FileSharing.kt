package com.masteralanlab.emailbox.util

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.ResponseBody
import java.io.File

/**
 * 把服务端返回的二进制流（账号导出、附件、附件压缩包）落到私有缓存，
 * 再通过 FileProvider 交给系统：用其他应用打开，或分享出去。
 */
object FileSharing {

    suspend fun save(
        context: Context,
        body: ResponseBody,
        fileName: String,
        subDir: String = "shared",
    ): File = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, subDir).apply { mkdirs() }
        val safeName = fileName.replace(Regex("[\\\\/:*?\"<>|]"), "_").ifBlank { "download" }
        val target = File(dir, safeName)
        body.byteStream().use { input ->
            target.outputStream().use { output -> input.copyTo(output) }
        }
        target
    }

    private fun uri(context: Context, file: File) =
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

    fun view(context: Context, file: File, mimeType: String) {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri(context, file), mimeType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(Intent.createChooser(intent, "打开").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    fun share(context: Context, file: File, mimeType: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri(context, file))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching {
            context.startActivity(Intent.createChooser(intent, "分享或保存").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    fun shareText(
        context: Context,
        content: String,
        fileName: String,
        mimeType: String = guessMime(fileName),
    ) {
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val safeName = fileName.replace(Regex("[\\\\/:*?\"<>|]"), "_").ifBlank { "export.txt" }
        val target = File(dir, safeName)
        target.writeText(content, Charsets.UTF_8)
        share(context, target, mimeType)
    }

    fun guessMime(name: String): String {
        val lower = name.lowercase()
        return when {
            lower.endsWith(".pdf") -> "application/pdf"
            lower.endsWith(".png") -> "image/png"
            lower.endsWith(".jpg") || lower.endsWith(".jpeg") -> "image/jpeg"
            lower.endsWith(".gif") -> "image/gif"
            lower.endsWith(".webp") -> "image/webp"
            lower.endsWith(".zip") -> "application/zip"
            lower.endsWith(".txt") || lower.endsWith(".log") -> "text/plain"
            lower.endsWith(".csv") -> "text/csv"
            lower.endsWith(".md") -> "text/markdown"
            lower.endsWith(".eml") -> "message/rfc822"
            else -> "*/*"
        }
    }
}
