package com.staninna.aufmachen.ui.readiness

import com.staninna.aufmachen.data.AlertEntity
import com.staninna.aufmachen.data.AppSettings
import com.staninna.aufmachen.data.PersonStatsEntity
import com.staninna.aufmachen.data.ServiceView
import com.staninna.aufmachen.model.Status
import com.staninna.aufmachen.model.Tier
import com.staninna.aufmachen.model.TellNewAddress

/** Blocker kinds, declared in severity order (most severe first). */
enum class BlockerKind { CRITICAL_SERVICE, ALERT, GOOGLE_LOGIN, PERSON, TAKEOUT }

/** One thing standing between the user and closing Gmail. [key] is a domain, an alert id or an address. */
data class Blocker(val kind: BlockerKind, val key: String, val label: String)

data class BlockerGroup(val kind: BlockerKind, val items: List<Blocker>) {
    val count: Int get() = items.size
}

data class Readiness(val percent: Int, val cleared: Int, val total: Int, val groups: List<BlockerGroup>) {
    val ready: Boolean get() = groups.isEmpty()
    /** The three most severe blockers: critical services first. */
    val top: List<Blocker> get() = groups.flatMap { it.items }.take(3)
}

object ReadinessCalc {
    const val ALERT_WINDOW_DAYS = 60L

    /**
     * [services] should be the services with an account, best score first. [alerts] are the alerts to weigh
     * (open ones normally). [told] holds the addresses of people already told.
     * Every service, alert, person and the takeout tick counts as one item.
     */
    fun compute(
        services: List<ServiceView>,
        alerts: List<AlertEntity>,
        people: List<PersonStatsEntity>,
        told: Set<String>,
        settings: AppSettings,
        now: Long = System.currentTimeMillis(),
    ): Readiness {
        val cleared = setOf(Status.VERIFIED, Status.DELETED, Status.IGNORED)
        val critical = services
            .filter { it.tier == Tier.CRITICAL && it.status !in cleared }
            .map { Blocker(BlockerKind.CRITICAL_SERVICE, it.domain, it.name) }
        val login = services
            .filter { it.state.googleLogin && !it.state.loginSwitched && it.status != Status.DELETED && it.status != Status.IGNORED }
            .map { Blocker(BlockerKind.GOOGLE_LOGIN, it.domain, it.name) }
        val since = now - ALERT_WINDOW_DAYS * 86_400_000L
        val alertItems = alerts
            .filter { !it.dismissed && it.createdAt >= since }
            .map { Blocker(BlockerKind.ALERT, it.id.toString(), it.domain) }
        val persons = people
            .filter { it.tell == TellNewAddress.YES.name && it.address !in told }
            .map { Blocker(BlockerKind.PERSON, it.address, it.name.ifBlank { it.address }) }
        val takeout = if (settings.takeoutDone) emptyList() else listOf(Blocker(BlockerKind.TAKEOUT, "takeout", ""))

        val groups = listOf(
            BlockerKind.CRITICAL_SERVICE to critical,
            BlockerKind.ALERT to alertItems,
            BlockerKind.GOOGLE_LOGIN to login,
            BlockerKind.PERSON to persons,
            BlockerKind.TAKEOUT to takeout,
        ).filter { it.second.isNotEmpty() }.map { BlockerGroup(it.first, it.second) }

        val total = services.size + alerts.size + people.size + 1
        val open = groups.sumOf { it.count }
        val done = (total - open).coerceIn(0, total)
        return Readiness(done * 100 / total, done, total, groups)
    }
}
