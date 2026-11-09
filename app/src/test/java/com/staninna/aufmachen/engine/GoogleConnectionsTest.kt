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
}
