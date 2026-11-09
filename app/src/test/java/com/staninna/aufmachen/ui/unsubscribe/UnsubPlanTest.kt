package com.staninna.aufmachen.ui.unsubscribe

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UnsubPlanTest {
    @Test fun parsesAddressSubjectAndBody() {
        val m = parseMailto("mailto:leave@example.com?subject=Please%20unsubscribe&body=remove+me%21")!!
        assertEquals("leave@example.com", m.address)
        assertEquals("Please unsubscribe", m.subject)
        assertEquals("remove me!", m.body)
    }

    @Test fun defaultsWhenNoQuery() {
        val m = parseMailto("MAILTO:list+tag@example.org")!!
        assertEquals("list+tag@example.org", m.address)
        assertEquals("unsubscribe", m.subject)
    }

    @Test fun rejectsNonMailtoAndEmptyAddress() {
        assertNull(parseMailto("https://example.com/u"))
        assertNull(parseMailto("mailto:?subject=x"))
        assertNull(parseMailto("mailto:nobody"))
    }

    @Test fun badPercentEscapeDoesNotThrow() {
        assertEquals("100%", parseMailto("mailto:a@example.com?subject=100%")!!.subject)
    }

    @Test fun hostOfUrl() {
        assertEquals("news.example.com", hostOf("https://news.example.com/u?id=1"))
        assertEquals("junk", hostOf("junk"))
    }

    @Test fun oneClickNeedsHttps() {
        val p = planUnsub(listOf("mailto:a@example.com", "https://example.com/u"), true, false)!!
        assertEquals(UnsubStep.ONE_CLICK, p.step)
        assertEquals("https://example.com/u", p.link)
    }

    @Test fun emailFallsBackToWebWithoutMailSettings() {
        val links = listOf("mailto:a@example.com", "https://example.com/u")
        assertEquals(UnsubStep.EMAIL, planUnsub(links, false, true)!!.step)
        val p = planUnsub(links, false, false)!!
        assertEquals(UnsubStep.WEB, p.step)
        assertTrue(p.fellBack)
    }

    @Test fun emailOnlyWithoutMailSettingsIsSkipped() {
        assertNull(planUnsub(listOf("mailto:a@example.com"), false, false))
        assertNull(planUnsub(emptyList(), false, true))
    }
}
