package com.staninna.aufmachen.work

import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.ListenableWorker.Result
import androidx.work.WorkerParameters
import com.staninna.aufmachen.R
import com.staninna.aufmachen.container
import com.staninna.aufmachen.mail.ImapScanner
import com.staninna.aufmachen.mail.MailFailure
import com.staninna.aufmachen.mail.MboxImporter
import com.staninna.aufmachen.mail.ScanProgress
import com.staninna.aufmachen.mail.SyncScheduler
import com.staninna.aufmachen.mail.WatchdogPipeline
import com.staninna.aufmachen.model.Status
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first

private const val MAX_ATTEMPTS = 5

private fun foregroundInfo(n: android.app.Notification) =
    if (Build.VERSION.SDK_INT >= 29) ForegroundInfo(Notifier.ID_SCAN, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
    else ForegroundInfo(Notifier.ID_SCAN, n)

private fun failed(e: MailFailure, attempt: Int): Result =
    if (e.retryable && attempt < MAX_ATTEMPTS) Result.retry() else Result.failure()

/** First full scan (or resume). Runs as a foreground dataSync job with a progress notification. */
class ScanWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun getForegroundInfo(): ForegroundInfo = foregroundInfo(
        Notifier.progress(applicationContext, applicationContext.getString(R.string.mail_scan_title), "", 0, 0),
    )

    override suspend fun doWork(): Result {
        val c = applicationContext.container
        val t0 = System.currentTimeMillis()
        var p0 = -1
        // Show the notification right away, before login and the UID listing, which can take a while.
        runCatching {
            setForeground(foregroundInfo(Notifier.progress(
                applicationContext, applicationContext.getString(R.string.mail_scan_title),
                applicationContext.getString(R.string.mail_scan_connecting), 0, 0)))
        }
        return try {
            ImapScanner.run(c) { p: ScanProgress ->
                if (p0 < 0) p0 = p.processed
                val text = when {
                    p.readingCategories -> applicationContext.getString(R.string.mail_scan_categories)
                    else -> progressText(p, t0, p0)
                }
                // Foreground start can be refused in rare background states. The scan still runs.
                runCatching {
                    setForeground(foregroundInfo(Notifier.progress(
                        applicationContext, applicationContext.getString(R.string.mail_scan_title),
                        text, p.processed, p.total)))
                }
            }
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: MailFailure) {
            failed(e, runAttemptCount)
        }
    }

    private fun progressText(p: ScanProgress, t0: Long, p0: Int): String {
        val ctx = applicationContext
        val doneNow = p.processed - p0
        val elapsedMs = System.currentTimeMillis() - t0
        if (doneNow < BATCH_FOR_ETA || elapsedMs < 5_000) {
            return ctx.getString(R.string.mail_scan_progress_unknown, p.processed, p.total)
        }
        val minLeft = ((p.total - p.processed).toDouble() / doneNow * elapsedMs / 60_000).toInt().coerceAtLeast(1)
        return ctx.getString(R.string.mail_scan_progress, p.processed, p.total, minLeft)
    }

    private companion object { const val BATCH_FOR_ETA = 500 }
}

/** Incremental sync plus the watchdog. Periodic and on demand. */
class SyncWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val c = applicationContext.container
        val s = c.settings.get()
        if (s.imap == null || !s.onboardingDone) return Result.success()
        // The first scan is not done yet: hand over to it instead of syncing a half-filled table.
        if (c.db.sync().get(ImapScanner.SOURCE)?.initialDone != true) {
            SyncScheduler.startInitialScan(applicationContext)
            return Result.success()
        }
        return try {
            val out = ImapScanner.run(c)
            val failures = WatchdogPipeline.run(c, out.startedAt)
            if (failures > 0) Notifier.alert(applicationContext, failures)
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: MailFailure) {
            failed(e, runAttemptCount)
        }
    }
}

/** Imports the Takeout mbox from the uri in settings. */
class MboxWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun getForegroundInfo(): ForegroundInfo = foregroundInfo(
        Notifier.progress(applicationContext, applicationContext.getString(R.string.mail_mbox_title), "", 0, 0),
    )

    override suspend fun doWork(): Result {
        val c = applicationContext.container
        return try {
            MboxImporter.run(c, applicationContext) { count, total ->
                runCatching {
                    setForeground(foregroundInfo(Notifier.progress(
                        applicationContext, applicationContext.getString(R.string.mail_mbox_title),
                        applicationContext.getString(R.string.mail_mbox_progress, count), count, total)))
                }
            }
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: MailFailure) {
            Result.failure()
        }
    }
}

/** Weekly digest: open alerts and progress counts. Off unless settings.weeklyDigest. */
class DigestWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val c = applicationContext.container
        if (!c.settings.get().weeklyDigest) return Result.success()
        val open = c.db.alerts().open().first().size
        val states = c.db.services().allStatesOnce().mapNotNull { runCatching { Status.valueOf(it.status) }.getOrNull() }
        val done = states.count { it != Status.TODO && it != Status.CHANGED }
        val todo = c.db.services().allStatsOnce().size - states.count { it != Status.TODO }
        Notifier.digest(applicationContext, open, done, todo.coerceAtLeast(0))
        return Result.success()
    }
}
