package com.staninna.aufmachen

import android.app.Application
import com.staninna.aufmachen.mail.MailText
import com.staninna.aufmachen.mail.SyncScheduler
import com.staninna.aufmachen.work.Notifier

/**
 * Process start hooks: notification channels and periodic sync scheduling.
 * Owned by the sync worker area; keep it fast. Settings and WorkManager are touched on a
 * background scope inside [SyncScheduler.schedulePeriodic], never on the main thread.
 */
object AppInit {
    fun onCreate(app: Application) {
        MailText.app = app
        Notifier.createChannels(app)
        SyncScheduler.schedulePeriodic(app)
    }
}
