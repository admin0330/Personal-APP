package com.masteralanlab.emailbox.update

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import java.io.File

object UpdateNotifications {

    const val CHANNEL_ID = "emailbox_update"
    private const val ID_DOWNLOADED = 9001

    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "应用更新",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply { description = "Emailbox 新版本下载完成提醒" }
        context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
    }

    private fun allowed(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    /** 下载完成后发一条通知：即使用户离开了对话框，也能点回安装界面。 */
    fun notifyDownloaded(context: Context, apk: File, versionName: String) {
        if (!allowed(context)) return
        val intent = UpdateManager.installIntent(context, apk)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        val pending = PendingIntent.getActivity(context, ID_DOWNLOADED, intent, flags)

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("Emailbox $versionName 已下载")
            .setContentText("点击安装更新")
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        // allowed() 已做过权限前置检查；这里再显式兜住并发场景下的 SecurityException：
        // 通知发不出去不影响更新本身，安装入口仍在应用内对话框里
        try {
            NotificationManagerCompat.from(context).notify(ID_DOWNLOADED, notification)
        } catch (_: SecurityException) {
        }
    }
}
