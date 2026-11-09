package com.staninna.aufmachen.work

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.staninna.aufmachen.MainActivity
import com.staninna.aufmachen.R

/** Channels and notifications for scans, alerts and the weekly digest. Never carries mail content. */
object Notifier {
    const val CHANNEL_SCAN = "scan"
    const val CHANNEL_ALERTS = "alerts"
    const val CHANNEL_DIGEST = "digest"
    const val ID_SCAN = 1001
    const val ID_ALERT = 1002
    const val ID_DIGEST = 1003

    fun createChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        fun ch(id: String, name: Int, desc: Int, importance: Int) = NotificationChannel(id, context.getString(name), importance)
            .apply { description = context.getString(desc) }
        nm.createNotificationChannels(listOf(
            ch(CHANNEL_SCAN, R.string.mail_channel_scan, R.string.mail_channel_scan_desc, NotificationManager.IMPORTANCE_LOW),
            ch(CHANNEL_ALERTS, R.string.mail_channel_alerts, R.string.mail_channel_alerts_desc, NotificationManager.IMPORTANCE_DEFAULT),
            ch(CHANNEL_DIGEST, R.string.mail_channel_digest, R.string.mail_channel_digest_desc, NotificationManager.IMPORTANCE_LOW),
        ))
    }

    private fun openApp(context: Context): PendingIntent = PendingIntent.getActivity(
        context, 0, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    fun progress(context: Context, title: String, text: String, processed: Int, total: Int): Notification =
        NotificationCompat.Builder(context, CHANNEL_SCAN)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(title)
            .setContentText(text)
            .setProgress(total, processed.coerceAtMost(total), total <= 0)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openApp(context))
            .build()

    fun alert(context: Context, count: Int) = post(
        context, ID_ALERT,
        NotificationCompat.Builder(context, CHANNEL_ALERTS)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle(context.getString(R.string.mail_alert_title))
            .setContentText(context.resources.getQuantityString(R.plurals.mail_alert_text, count, count))
            .setContentIntent(openApp(context))
            .setAutoCancel(true)
            .build(),
    )

    fun digest(context: Context, openAlerts: Int, done: Int, todo: Int) = post(
        context, ID_DIGEST,
        NotificationCompat.Builder(context, CHANNEL_DIGEST)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle(context.getString(R.string.mail_digest_title))
            .setContentText(context.getString(R.string.mail_digest_text, openAlerts, done, todo))
            .setContentIntent(openApp(context))
            .setAutoCancel(true)
            .build(),
    )

    private fun post(context: Context, id: Int, n: Notification) {
        val nm = NotificationManagerCompat.from(context)
        if (!nm.areNotificationsEnabled()) return
        try { nm.notify(id, n) } catch (_: SecurityException) { /* permission revoked */ }
    }
}
