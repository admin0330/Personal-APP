package com.masteralanlab.emailbox.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.masteralanlab.emailbox.MainActivity
import com.masteralanlab.emailbox.R
import com.masteralanlab.emailbox.data.Prefs
import com.masteralanlab.emailbox.data.SecureMailCache
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MailStatusWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { manager.updateAppWidget(it, views(context)) }
    }

    companion object {
        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val component = ComponentName(context, MailStatusWidget::class.java)
            val ids = manager.getAppWidgetIds(component)
            ids.forEach { manager.updateAppWidget(it, views(context)) }
        }

        private fun views(context: Context): RemoteViews {
            val stats = Prefs.tenantId?.let { SecureMailCache.stats(it) }
            val stale = stats == null || stats.lastSyncAt == 0L || System.currentTimeMillis() - stats.lastSyncAt > 24 * 60 * 60 * 1000L
            val open = PendingIntent.getActivity(
                context, 0, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            return RemoteViews(context.packageName, R.layout.widget_mail_status).apply {
                setTextViewText(R.id.widget_unread, if (stale) "—" else (stats?.unread ?: 0).toString())
                setTextViewText(R.id.widget_accounts, "${stats?.accounts ?: 0} 个缓存邮箱")
                setTextViewText(
                    R.id.widget_sync,
                    if (stale) "待同步" else "更新于 " + SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(stats!!.lastSyncAt)),
                )
                setOnClickPendingIntent(R.id.widget_root, open)
                setOnClickPendingIntent(R.id.widget_refresh, open)
            }
        }
    }
}
