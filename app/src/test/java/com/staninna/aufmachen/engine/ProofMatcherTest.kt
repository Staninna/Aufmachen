package com.staninna.aufmachen.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProofMatcherTest {
    private fun fields(name: String = "Someone", reply: String? = null, body: String = "", links: List<String> = emptyList()) =
        TutaPdfFields(null, null, name, null, null, reply, body, links)

    @Test fun replyToPlusNameIsHigh() {
        val r = ProofMatcher.rank(
            fields("Example Bank", "noreply@mail.example.co.uk"),
            listOf(ProofCandidate("b", setOf("example.co.uk"), listOf("Example Bank"))),
        )
        assertTrue(r[0].score > ProofMatcher.HIGH_CONFIDENCE)
    }

    @Test fun fuzzyNameCounts() {
        val r = ProofMatcher.rank(
            fields("Example Bank Support"),
            listOf(ProofCandidate("b", setOf("other.org"), listOf("Example Bank"))),
        )
        assertTrue(r[0].score > 0.4)
    }

    @Test fun loneBrandMentionIsWeak() {
        val r = ProofMatcher.rank(
            fields(body = "I like shopalot a lot"),
            listOf(ProofCandidate("s", setOf("shopalot.com"), emptyList())),
        )
        assertTrue(r[0].score in 0.01..0.3)
    }

    @Test fun socialLinksIgnoredAndFrequencyWeighs() {
        val cands = listOf(ProofCandidate("s", setOf("shop.example.com"), emptyList()), ProofCandidate("f", setOf("facebook.com"), emptyList()))
        val one = ProofMatcher.rank(fields(links = listOf("https://www.example.com/a", "https://facebook.com/x")), cands)
        assertEquals("s", one[0].groupKey)
        assertEquals(0.0, one.first { it.groupKey == "f" }.score, 0.0)
        val three = ProofMatcher.rank(fields(links = listOf("https://example.com/a", "https://example.com/b", "https://example.com/c")), cands)
        assertTrue(three[0].score > one[0].score)
    }
}
