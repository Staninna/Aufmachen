package com.staninna.aufmachen.mail

import android.content.Context
import com.staninna.aufmachen.data.ImapConfig
import com.staninna.aufmachen.work.SchedulerImpl

// Contracts of the mail/sync area. Signatures are fixed; bodies are filled by the mail area.

/** Owner: mail area. */
object MailAccess {
    /** Logs in read-only and logs out. Failure message is user-presentable. */
    suspend fun testLogin(config: ImapConfig, password: String): Result<Unit> =
        MailAccessImpl.testLogin(config, password)

    /**
     * Sends one plain-text mail from the old account over SMTP (same app password).
     * Only ever called after the user confirmed exactly this send.
     */
    suspend fun sendFromOldAccount(context: Context, to: String, subject: String, body: String): Result<Unit> =
        MailAccessImpl.send(context, to, subject, body)

    /** RFC 8058 one-click unsubscribe: POST "List-Unsubscribe=One-Click" to [url]. */
    suspend fun oneClickUnsubscribe(url: String): Result<Unit> = MailAccessImpl.oneClick(url)
}

/** Owner: mail area. Background jobs. */
object SyncScheduler {
    /** First full scan (or resume), foreground job with progress notification. */
    fun startInitialScan(context: Context): Unit = SchedulerImpl.startInitialScan(context)

    /** Incremental sync now (pull-to-refresh). */
    fun syncNow(context: Context): Unit = SchedulerImpl.syncNow(context)

    /** (Re)schedules the periodic incremental sync from current settings (interval, Wi-Fi only). */
    fun schedulePeriodic(context: Context): Unit = SchedulerImpl.schedulePeriodic(context)

    /** Drops stored headers of all sources and scans again from scratch. */
    fun rescanAll(context: Context): Unit = SchedulerImpl.rescanAll(context)

    /** Imports the Takeout .mbox in settings.mboxUri (background job). */
    fun importMbox(context: Context): Unit = SchedulerImpl.importMbox(context)

    /** Cancels every job (wipe). */
    fun cancelAll(context: Context): Unit = SchedulerImpl.cancelAll(context)
}
