package com.staninna.aufmachen.mail

import com.staninna.aufmachen.AppContainer
import com.staninna.aufmachen.data.AlertEntity
import com.staninna.aufmachen.data.TimelineEntity
import com.staninna.aufmachen.data.TimelineKind
import com.staninna.aufmachen.data.jsonStrings
import com.staninna.aufmachen.engine.NewMail
import com.staninna.aufmachen.engine.WatchedService
import com.staninna.aufmachen.engine.Watchdog
import com.staninna.aufmachen.model.Status

/** Runs after every incremental sync (§6 of the brief). The failure rules live in [Watchdog]. */
object WatchdogPipeline {
    /**
     * Looks at mail synced since [since] (never first-scan rows), records alerts and timeline
     * entries, then rescores. Returns the number of new alerts.
     */
    suspend fun run(c: AppContainer, since: Long, now: Long = System.currentTimeMillis()): Int {
        val fresh = c.db.headers().syncedSince(since)
        if (fresh.isEmpty()) return 0
        val settings = c.settings.get()

        val states = c.db.services().allStatesOnce().associateBy { it.domain }
        val services = c.db.services().allStatsOnce().map { st ->
            val state = states[st.domain]
            WatchedService(
                groupKey = st.domain,
                domains = st.domainsJson.jsonStrings().toSet() + st.domain,
                status = state?.let { runCatching { Status.valueOf(it.status) }.getOrNull() } ?: Status.TODO,
                statusChangedAt = state?.statusChangedAt,
                unsubscribedAt = state?.unsubscribedAt,
            )
        }
        val newMail = fresh.map { h ->
            NewMail(
                headerId = h.id, fromAddr = h.fromAddr, fromDomain = h.fromDomain,
                toAddrs = h.toAddrs.split(',').filter { it.isNotBlank() },
                subject = h.subject, dateEpochDay = h.dateEpochDay, sent = h.sent,
            )
        }
        val failures = Watchdog.failures(services, newMail, settings.oldAddress, settings::isNewAddress, now)
        val statusOf = services.associate { it.groupKey to it.status }

        var created = 0
        if (failures.isNotEmpty()) {
            val alerts = failures.map { f ->
                AlertEntity(
                    domain = f.groupKey, headerId = f.mail.headerId, dateEpochDay = f.mail.dateEpochDay,
                    senderAddr = f.mail.fromAddr, subject = f.mail.subject, createdAt = now,
                )
            }
            // headerId is unique, so a re-run inserts nothing twice. -1 marks a skipped row.
            val ids = c.db.alerts().insertAll(alerts)
            val inserted = failures.filterIndexed { i, _ -> ids[i] != -1L }
            created = inserted.size
            for (f in inserted) {
                c.repo.updateState(f.groupKey) { it.copy(failed = true) }
                c.db.timeline().insert(TimelineEntity(domain = f.groupKey, at = now, kind = TimelineKind.WATCHDOG,
                    text = "Still mailing the old address: ${f.mail.subject}"))
                if (statusOf[f.groupKey] == Status.DELETION_REQUESTED) {
                    c.db.timeline().insert(TimelineEntity(domain = f.groupKey, at = now, kind = TimelineKind.REPLY,
                        text = "Reply from the service: ${f.mail.subject}"))
                }
            }
        }
        c.repo.rescore()
        return created
    }
}
