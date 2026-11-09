package com.staninna.aufmachen.engine

import com.staninna.aufmachen.model.Status
import java.time.Instant
import java.time.ZoneId

internal fun watchdogFailures(
    services: List<WatchedService>,
    newMail: List<NewMail>,
    oldAddress: String?,
    isNewAddress: (String) -> Boolean,
    nowMillis: Long,
): List<WatchdogFailure> {
    val byDomain = HashMap<String, WatchedService>()
    for (s in services) {
        if (s.status == Status.TODO || s.status == Status.IGNORED) continue
        for (d in s.domains) byDomain.putIfAbsent(d, s)
    }
    val zone = ZoneId.systemDefault()
    val old = oldAddress?.lowercase()
    val out = ArrayList<WatchdogFailure>()
    for (m in newMail) {
        if (m.sent) continue
        val from = m.fromAddr.lowercase()
        if (from == old || isNewAddress(from)) continue
        if (m.toAddrs.isNotEmpty() && m.toAddrs.all { isNewAddress(it.lowercase()) }) continue
        val s = byDomain[m.fromDomain] ?: continue
        if (!isFailing(s, nowMillis)) continue
        val changed = s.statusChangedAt
        val day = m.dateEpochDay
        if (changed != null && day != null) {
            val changedDay = Instant.ofEpochMilli(changed).atZone(zone).toLocalDate().toEpochDay()
            if (day < changedDay) continue
        }
        out.add(WatchdogFailure(s.groupKey, m))
    }
    return out
}

private fun isFailing(s: WatchedService, nowMillis: Long): Boolean = when (s.status) {
    Status.CHANGED, Status.VERIFIED, Status.DELETED, Status.DELETION_REQUESTED -> true
    Status.UNSUBSCRIBED -> {
        // Without a timestamp the grace period cannot be proven over, so no failure.
        val since = s.unsubscribedAt ?: s.statusChangedAt
        since != null && nowMillis - since >= Watchdog.UNSUBSCRIBE_GRACE_DAYS * 86_400_000L
    }
    Status.TODO, Status.IGNORED -> false
}
