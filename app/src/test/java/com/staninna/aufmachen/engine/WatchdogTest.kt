package com.staninna.aufmachen.engine

import com.staninna.aufmachen.model.Status
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class WatchdogTest {
    private val zone = ZoneId.systemDefault()
    private val today = LocalDate.of(2026, 11, 9)
    private val now = today.atTime(12, 0).atZone(zone).toInstant().toEpochMilli()
    private val dayMs = 86_400_000L
    private val changedDay = today.minusDays(5)
    private val changedAt = changedDay.atTime(12, 0).atZone(zone).toInstant().toEpochMilli()

    private fun isNew(a: String) = a == "me@example.org"

    private fun svc(
        status: Status,
        domains: Set<String> = setOf("shop.example.com"),
        changed: Long? = changedAt,
        unsub: Long? = null,
    ) = WatchedService("shop.example.com", domains, status, changed, unsub)

    private fun mail(
        id: Long = 1,
        from: String = "news@shop.example.com",
        domain: String = "shop.example.com",
        to: List<String> = listOf("me.old@example.com"),
        day: Long? = today.minusDays(1).toEpochDay(),
        sent: Boolean = false,
    ) = NewMail(id, from, domain, to, "Hi", day, sent)

    private fun run(s: WatchedService, vararg m: NewMail) =
        Watchdog.failures(listOf(s), m.toList(), "me.old@example.com", ::isNew, now)

    @Test fun changedVerifiedDeletedAndRequestedFail() {
        for (st in listOf(Status.CHANGED, Status.VERIFIED, Status.DELETED, Status.DELETION_REQUESTED)) {
            val f = run(svc(st), mail())
            assertEquals(st.name, 1, f.size)
            assertEquals("shop.example.com", f[0].groupKey)
        }
    }

    @Test fun todoAndIgnoredNeverFail() {
        assertTrue(run(svc(Status.TODO), mail()).isEmpty())
        assertTrue(run(svc(Status.IGNORED), mail()).isEmpty())
    }

    @Test fun unsubscribedHonoursGrace() {
        val inGrace = svc(Status.UNSUBSCRIBED, unsub = now - 13 * dayMs)
        val exactly = svc(Status.UNSUBSCRIBED, unsub = now - 14 * dayMs)
        val past = svc(Status.UNSUBSCRIBED, unsub = now - 30 * dayMs, changed = now - 30 * dayMs)
        assertTrue(run(inGrace, mail()).isEmpty())
        assertEquals(1, run(exactly, mail()).size)
        assertEquals(1, run(past, mail(day = today.toEpochDay())).size)
    }

    @Test fun mergedDomainsMatch() {
        val s = svc(Status.CHANGED, domains = setOf("shop.example.com", "shop.example.org"))
        val f = run(s, mail(from = "a@shop.example.org", domain = "shop.example.org"))
        assertEquals(1, f.size)
        assertTrue(run(s, mail(from = "a@other.example.com", domain = "other.example.com")).isEmpty())
    }

    @Test fun sentMailAndOwnSendersSkipped() {
        val s = svc(Status.CHANGED)
        assertTrue(run(s, mail(sent = true)).isEmpty())
        assertTrue(run(s, mail(from = "me@example.org")).isEmpty())
        assertTrue(run(s, mail(from = "me.old@example.com")).isEmpty())
    }

    @Test fun mailOnlyToNewAddressesSkipped() {
        val s = svc(Status.VERIFIED)
        assertTrue(run(s, mail(to = listOf("me@example.org"))).isEmpty())
        assertEquals(1, run(s, mail(to = listOf("me@example.org", "me.old@example.com"))).size)
        assertEquals(1, run(s, mail(to = emptyList())).size)
    }

    @Test fun mailBeforeStatusChangeIgnored() {
        val s = svc(Status.CHANGED)
        assertTrue(run(s, mail(day = changedDay.minusDays(1).toEpochDay())).isEmpty())
        assertEquals(1, run(s, mail(day = changedDay.toEpochDay())).size)
        assertEquals(1, run(s, mail(day = null)).size)
    }

    @Test fun oneFailurePerMail() {
        val f = run(svc(Status.CHANGED), mail(id = 1), mail(id = 2))
        assertEquals(listOf(1L, 2L), f.map { it.mail.headerId })
    }
}
