package com.staninna.aufmachen.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TutaPdfParserTest {
    private val text = """
        Tuta Team
        to: me.other@example.org
        Thu, Nov 5, 2026 • 22:14
        To
        me.other@example.org
        Reply to
        no-reply@tutanota.de
        Welcome to your mailbox. Read more in our guide.
        Second line of the body.
    """.trimIndent()
    private val links = listOf("https://tuta.com/support")

    @Test fun parsesExport() {
        val f = TutaPdfParser.parse("me@example.com - Tuta", text, links)!!
        assertEquals("me@example.com - Tuta", f.title)
        assertEquals("me@example.com", f.account)
        assertEquals("Tuta Team", f.displayName)
        assertEquals("me.other@example.org", f.to)
        assertEquals("Thu, Nov 5, 2026 • 22:14", f.dateLine)
        assertEquals("no-reply@tutanota.de", f.replyTo)
        assertEquals("Welcome to your mailbox. Read more in our guide.\nSecond line of the body.", f.bodyText)
        assertEquals(links, f.links)
    }

    @Test fun toleratesSameLineAndDisplayNameAndShuffledHeader() {
        val t = "Thu, Nov 5, 2026 • 22:14\nTuta Team\nTO: Me Example <me.other@example.org>\nReply to no-reply@tutanota.de\nBody here"
        val f = TutaPdfParser.parse(null, t, emptyList())!!
        assertEquals("Tuta Team", f.displayName)
        assertEquals("me.other@example.org", f.to)
        assertEquals("no-reply@tutanota.de", f.replyTo)
        assertEquals("Body here", f.bodyText)
        assertNull(f.account)
    }

    @Test fun noReplyToBlock() {
        val f = TutaPdfParser.parse("me@example.com - Tuta", "Shop\nto: me.other@example.org\nTo\nme.other@example.org\nHello", emptyList())!!
        assertNull(f.replyTo)
        assertEquals("Hello", f.bodyText)
    }

    @Test fun notATutaExport() {
        assertNull(TutaPdfParser.parse("Invoice", "Some PDF\nwith text", emptyList()))
        assertNotNull(TutaPdfParser.parse("x@example.com - Tuta", "Some PDF", emptyList()))
    }

    @Test fun matcherRanksTutaFirst() {
        val f = TutaPdfParser.parse("me@example.com - Tuta", text, links)!!
        val ranked = ProofMatcher.rank(
            f,
            listOf(
                ProofCandidate("decoy", setOf("example.net"), listOf("Decoy Bank")),
                ProofCandidate("tuta", setOf("tutanota.de", "tuta.com"), listOf("Tuta Team")),
            ),
        )
        assertEquals("tuta", ranked[0].groupKey)
        assertTrue(ranked[0].score > ProofMatcher.HIGH_CONFIDENCE)
        assertTrue(ranked[1].score < ProofMatcher.HIGH_CONFIDENCE)
        assertTrue(ranked[0].reasons.isNotEmpty())
    }
}
