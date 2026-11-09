package com.staninna.aufmachen.ui.readiness

import com.staninna.aufmachen.data.AlertEntity
import com.staninna.aufmachen.data.AppSettings
import com.staninna.aufmachen.data.PersonStatsEntity
import com.staninna.aufmachen.data.ServiceStateEntity
import com.staninna.aufmachen.data.ServiceStatsEntity
import com.staninna.aufmachen.data.ServiceView
import com.staninna.aufmachen.model.Status
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadinessTest {
    private val now = 1_000_000_000_000L
    private val day = 86_400_000L

    private fun svc(
        domain: String, tier: Int = 1, status: Status = Status.TODO,
        googleLogin: Boolean = false, switched: Boolean = false,
    ) = ServiceView(
        ServiceStatsEntity(
            domain = domain, domainsJson = "[]", displayNamesJson = "[]", senderAddrsJson = "[]", category = "",
            score = 70, tier = tier, whyJson = "[]", componentsJson = "[]", action = "CHANGE", mails = 1, last12 = 1,
            perYearJson = "{}", perYearRate = 0.0, firstEpochDay = null, lastEpochDay = null, daysSince = null,
            activeYears = 1, readPct = 0.0, importantPct = 0.0, starred = 0, youSent = 0, security = 0, welcome = 0,
            orders = 0, billing = 0, marketing = 0, billMonths = 0, regularity = null, newsletterPct = 0.0,
            promoPct = 0.0, updatesPct = 0.0, socialPct = 0.0, attachments = 0, unsubLinksJson = "[]",
            unsubOneClick = false, exampleSubject = "", hasAccount = true, dormant = false, newsletterLike = false,
        ),
        ServiceStateEntity(domain, status = status.name, googleLogin = googleLogin, loginSwitched = switched),
        null,
    )

    private fun alert(id: Long, ageDays: Long, dismissed: Boolean = false) =
        AlertEntity(id, "a.example.com", id, null, "x@a.example.com", "s", now - ageDays * day, dismissed)

    private fun person(addr: String, tell: String) = PersonStatsEntity(addr, "Alex Example", 1, 1, true, null, null, tell, 1)

    private val done = AppSettings(takeoutDone = true)

    @Test fun everythingClearIsHundredPercent() {
        val r = ReadinessCalc.compute(listOf(svc("a.example.com", status = Status.VERIFIED)), emptyList(), emptyList(), emptySet(), done, now)
        assertEquals(100, r.percent)
        assertTrue(r.ready)
        assertTrue(r.top.isEmpty())
    }

    @Test fun criticalNotVerifiedBlocks_deletedAndIgnoredClear() {
        val s = listOf(
            svc("a.example.com"),
            svc("b.example.com", status = Status.DELETED),
            svc("c.example.com", status = Status.IGNORED),
            svc("d.example.com", tier = 2),
        )
        val r = ReadinessCalc.compute(s, emptyList(), emptyList(), emptySet(), done, now)
        assertEquals(listOf("a.example.com"), r.groups.single().items.map { it.key })
        assertEquals(80, r.percent)
    }

    @Test fun googleLoginNeedsSwitch() {
        val s = listOf(
            svc("a.example.com", tier = 3, googleLogin = true),
            svc("b.example.com", tier = 3, googleLogin = true, switched = true),
        )
        val r = ReadinessCalc.compute(s, emptyList(), emptyList(), emptySet(), done, now)
        assertEquals(BlockerKind.GOOGLE_LOGIN, r.groups.single().kind)
        assertEquals(1, r.groups.single().count)
    }

    @Test fun alertsOlderThan60DaysOrDismissedDoNotBlock() {
        val a = listOf(alert(1, 10), alert(2, 61), alert(3, 5, dismissed = true))
        val r = ReadinessCalc.compute(emptyList(), a, emptyList(), emptySet(), done, now)
        assertEquals(listOf("1"), r.groups.single().items.map { it.key })
        assertEquals(75, r.percent)
    }

    @Test fun onlyYesPeopleNotToldBlock() {
        val p = listOf(person("a@example.org", "YES"), person("b@example.org", "YES"), person("c@example.org", "MAYBE"))
        val r = ReadinessCalc.compute(emptyList(), emptyList(), p, setOf("b@example.org"), done, now)
        assertEquals(listOf("a@example.org"), r.groups.single().items.map { it.key })
    }

    @Test fun takeoutBlocksUntilTicked() {
        val r = ReadinessCalc.compute(emptyList(), emptyList(), emptyList(), emptySet(), AppSettings(), now)
        assertEquals(0, r.percent)
        assertEquals(BlockerKind.TAKEOUT, r.groups.single().kind)
    }

    @Test fun topThreeOrderedBySeverity() {
        val s = listOf(
            svc("g.example.com", tier = 3, googleLogin = true),
            svc("c1.example.com"), svc("c2.example.com"), svc("c3.example.com"),
        )
        val r = ReadinessCalc.compute(s, listOf(alert(1, 1)), listOf(person("a@example.org", "YES")), emptySet(), AppSettings(), now)
        assertEquals(
            listOf(BlockerKind.CRITICAL_SERVICE, BlockerKind.CRITICAL_SERVICE, BlockerKind.CRITICAL_SERVICE),
            r.top.map { it.kind },
        )
        assertEquals(listOf("c1.example.com", "c2.example.com", "c3.example.com"), r.top.map { it.key })
        assertEquals(listOf(BlockerKind.CRITICAL_SERVICE, BlockerKind.ALERT, BlockerKind.GOOGLE_LOGIN, BlockerKind.PERSON, BlockerKind.TAKEOUT), r.groups.map { it.kind })
        assertEquals(0, r.percent)
    }
}
