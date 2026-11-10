package com.staninna.aufmachen.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GoogleConnectionsTest {
    private val clipboard = """
        Skip to main content
        Google Account
        Third-party apps & services
        Your connections to third-party apps & services
        Sign in with Google
        Example Shop
        Has some account access
        Since May 3, 2021
        Manage
        Learn more
        Alex Tools
        Sinds 3 mei 2021
        Beheren
        Inloggen met Google
        Heeft toegang tot een deel van het account
        example.org
        me@example.com
    """.trimIndent()

    private val cands = listOf(
        ProofCandidate("shop", setOf("shop.example.com"), listOf("Example Shop")),
        ProofCandidate("tools", setOf("alextools.io"), listOf("Alex Tools Team")),
    )

    @Test fun parseRemovesChrome() {
        assertEquals(listOf("Example Shop", "Alex Tools", "example.org"), GoogleConnections.parse(clipboard))
    }

    @Test fun parseDeduplicates() {
        assertEquals(listOf("Foo App"), GoogleConnections.parse("Foo App\nfoo app\nManage"))
    }

    @Test fun matchBestCandidate() {
        val m = GoogleConnections.match(listOf("Example Shop", "Alex Tools", "Unknown Thing"), cands)
        assertEquals(listOf("shop", "tools"), m.map { it.groupKey })
        assertTrue(m.all { it.score >= 0.8 })
    }

    @Test fun accessHint() {
        assertTrue(GoogleConnections.isThirdPartyAccessHint("no-reply@accounts.google.com", "Example Shop now has access to your Google Account"))
        assertTrue(GoogleConnections.isThirdPartyAccessHint("no-reply@accounts.google.com", "Nieuwe toegang tot je Google-account"))
        assertFalse(GoogleConnections.isThirdPartyAccessHint("news@example.com", "Access granted"))
        assertFalse(GoogleConnections.isThirdPartyAccessHint("no-reply@accounts.google.com", "Your storage is almost full"))
    }

    @Test fun candidatesFromSubject() {
        val m = GoogleConnections.candidatesFromSubject("Example Shop has access to your Google Account", cands)
        assertEquals(listOf("shop"), m.map { it.groupKey })
        val q = GoogleConnections.candidatesFromSubject("Signed in with Google: \"Alex Tool\"", cands)
        assertEquals(listOf("tools"), q.map { it.groupKey })
    }

    @Test fun matchHandlesAWholeCopiedPageQuickly() {
        // A select-all copy of the connections page against a big mailbox froze the app (ANR).
        val page = (1..400).joinToString("\n") { "Connected App Number $it\nHas some account access\nSince May 3, 2021" }
        val many = (1..3000).map { ProofCandidate("svc$it", setOf("service$it.example.com"), listOf("Service Name $it", "Other Brand $it")) }
        val names = GoogleConnections.parse(page)
        val start = System.nanoTime()
        GoogleConnections.match(names, many)
        val ms = (System.nanoTime() - start) / 1_000_000
        assertTrue("match took $ms ms", ms < 5_000)
    }

    @Test fun fastSimilarityAgreesAboveThreshold() {
        val words = listOf("shop", "alex", "tools", "app", "bank", "bnak", "exmple", "example", "the", "team", "mail", "pay", "pal", "paypal")
        val rnd = java.util.Random(7)
        fun name() = (1..(1 + rnd.nextInt(3))).joinToString(" ") { words[rnd.nextInt(words.size)] }
        repeat(5000) {
            val a = ParserText.prepare(name())
            val b = ParserText.prepare(name())
            val slow = ParserText.nameSimilarity(a, b)
            val fast = ParserText.nameSimilarityAtLeast(a, b, 0.82)
            if (slow >= 0.82) assertEquals(slow, fast, 1e-9) else assertTrue(fast < 0.82)
        }
    }

    @Test fun parsesTheLinkedAppsPage() {
        val page = """
            Linked apps
            Manage data sharing
            Review your linked apps
            See the data that you're sharing with your linked apps

            Learn about linked apps

            search
            Search linked apps by name
            View by:

            done



            1001sms.com

            Coinbase: Buy Crypto & Stocks

            GitHub

            Lime - #RideGreen

            Splitser - WieBetaaltWat

            PrivacyTermsHelpAbout
        """.trimIndent()
        val names = GoogleConnections.parse(page)
        assertEquals(
            listOf("1001sms.com", "Coinbase: Buy Crypto & Stocks", "GitHub", "Lime - #RideGreen", "Splitser - WieBetaaltWat"),
            names,
        )
        val c = listOf(
            ProofCandidate("coinbase.com", setOf("coinbase.com"), listOf("Coinbase")),
            ProofCandidate("li.me", setOf("li.me"), listOf("Lime")),
            ProofCandidate("github.com", setOf("github.com"), listOf("GitHub")),
            ProofCandidate("1001sms.com", setOf("1001sms.com"), emptyList()),
            ProofCandidate("splitser.com", setOf("splitser.com"), listOf("Splitser")),
        )
        val m = GoogleConnections.match(names, c).associate { it.name to it.groupKey }
        assertEquals("coinbase.com", m["Coinbase: Buy Crypto & Stocks"])
        assertEquals("li.me", m["Lime - #RideGreen"])
        assertEquals("github.com", m["GitHub"])
        assertEquals("1001sms.com", m["1001sms.com"])
        assertEquals("splitser.com", m["Splitser - WieBetaaltWat"])
    }
}
