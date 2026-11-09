package com.staninna.aufmachen.work

import android.app.NotificationManager
import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.await
import com.staninna.aufmachen.container
import com.staninna.aufmachen.data.HeaderEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

/** Implementations behind [com.staninna.aufmachen.mail.SyncScheduler]. Settings reads happen off the main thread. */
internal object SchedulerImpl {
    const val WORK_SCAN = "aufmachen_scan"
    const val WORK_SYNC_NOW = "aufmachen_sync_now"
    const val WORK_SYNC_PERIODIC = "aufmachen_sync_periodic"
    const val WORK_MBOX = "aufmachen_mbox"
    const val WORK_DIGEST = "aufmachen_digest"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private fun net(wifiOnly: Boolean) = Constraints.Builder()
        .setRequiredNetworkType(if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
        .build()

    fun startInitialScan(context: Context) {
        val app = context.applicationContext
        scope.launch { enqueueScan(app, ExistingWorkPolicy.KEEP) }
    }

    private suspend fun enqueueScan(app: Context, policy: ExistingWorkPolicy) {
        val wifiOnly = app.container.settings.get().wifiOnly
        val req = OneTimeWorkRequestBuilder<ScanWorker>()
            .setConstraints(net(wifiOnly))
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(app).enqueueUniqueWork(WORK_SCAN, policy, req)
    }

    fun syncNow(context: Context) {
        val app = context.applicationContext
        scope.launch {
            val c = app.container
            if (c.settings.get().imap == null) return@launch
            val wm = WorkManager.getInstance(app)
            if (c.db.sync().get(HeaderEntity.SOURCE_IMAP)?.initialDone != true) {
                // First scan not done yet. A manual tap starts it now on any network, instead of
                // leaving it queued behind the Wi-Fi-only constraint. A scan already running is kept.
                val running = wm.getWorkInfosForUniqueWork(WORK_SCAN).get()
                    .any { it.state == WorkInfo.State.RUNNING }
                if (!running) {
                    val req = OneTimeWorkRequestBuilder<ScanWorker>()
                        .setConstraints(net(false))
                        .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
                        .build()
                    wm.enqueueUniqueWork(WORK_SCAN, ExistingWorkPolicy.REPLACE, req)
                }
                return@launch
            }
            // Manual refresh runs on any network: the user asked for it right now.
            val req = OneTimeWorkRequestBuilder<SyncWorker>().setConstraints(net(false)).build()
            wm.enqueueUniqueWork(WORK_SYNC_NOW, ExistingWorkPolicy.KEEP, req)
        }
    }

    fun schedulePeriodic(context: Context) {
        val app = context.applicationContext
        scope.launch {
            val s = app.container.settings.get()
            val wm = WorkManager.getInstance(app)
            if (!s.onboardingDone || s.imap == null) {
                wm.cancelUniqueWork(WORK_SYNC_PERIODIC)
            } else {
                val req = PeriodicWorkRequestBuilder<SyncWorker>(s.syncIntervalHours.coerceAtLeast(1).toLong(), TimeUnit.HOURS)
                    .setConstraints(net(s.wifiOnly))
                    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.MINUTES)
                    .build()
                wm.enqueueUniquePeriodicWork(WORK_SYNC_PERIODIC, ExistingPeriodicWorkPolicy.UPDATE, req)
            }
            if (s.onboardingDone && s.weeklyDigest) {
                val digest = PeriodicWorkRequestBuilder<DigestWorker>(7, TimeUnit.DAYS).build()
                wm.enqueueUniquePeriodicWork(WORK_DIGEST, ExistingPeriodicWorkPolicy.KEEP, digest)
            } else {
                wm.cancelUniqueWork(WORK_DIGEST)
            }
        }
    }

    fun rescanAll(context: Context) {
        val app = context.applicationContext
        scope.launch {
            val c = app.container
            val wm = WorkManager.getInstance(app)
            wm.cancelUniqueWork(WORK_SCAN).await()
            wm.cancelUniqueWork(WORK_MBOX).await()
            c.db.headers().deleteSource(HeaderEntity.SOURCE_IMAP)
            c.db.headers().deleteSource(HeaderEntity.SOURCE_MBOX)
            c.db.sync().delete(HeaderEntity.SOURCE_IMAP)
            c.db.sync().delete(HeaderEntity.SOURCE_MBOX)
            c.repo.rescore()
            val s = c.settings.get()
            if (s.imap != null) enqueueScan(app, ExistingWorkPolicy.REPLACE)
            if (s.mboxUri != null) importMbox(app)
        }
    }

    fun importMbox(context: Context) {
        val app = context.applicationContext
        val req = OneTimeWorkRequestBuilder<MboxWorker>().build()
        WorkManager.getInstance(app).enqueueUniqueWork(WORK_MBOX, ExistingWorkPolicy.REPLACE, req)
    }

    fun cancelAll(context: Context) {
        val app = context.applicationContext
        WorkManager.getInstance(app).cancelAllWork()
        app.getSystemService(NotificationManager::class.java).cancelAll()
    }
}
