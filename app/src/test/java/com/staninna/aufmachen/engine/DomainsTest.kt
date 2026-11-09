package com.staninna.aufmachen.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DomainsTest {
    @Test fun stripsSubdomains() {
        assertEquals("example.nl", baseDomain("mail.example.nl"))
        assertEquals("example.com", baseDomain("a.b.example.com"))
    }

    @Test fun multiPartTlds() {
        assertEquals("amazon.co.uk", baseDomain("x.amazon.co.uk"))
        assertEquals("example.com.au", baseDomain("a.b.example.com.au"))
        assertEquals("example.co.uk", baseDomain("example.co.uk"))
    }

    @Test fun trailingDotsSpacesAndBracket() {
        assertEquals("example.org", baseDomain("mail.example.org."))
        assertEquals("example.org", baseDomain("mail.example.org>"))
        assertEquals("example.org", baseDomain(" Mail.Example.ORG.> "))
    }

    @Test fun singleLabelHosts() {
        assertEquals("localhost", baseDomain("localhost"))
        assertEquals("", baseDomain(""))
    }

    @Test fun freemail() {
        assertTrue("gmail.com" in FREEMAIL)
        assertTrue("ziggo.nl" in FREEMAIL)
        assertFalse("example.com" in FREEMAIL)
    }

    @Test fun noreplyPattern() {
        assertTrue(NOREPLY_RE.containsMatchIn("No-Reply@example.com"))
        assertTrue(NOREPLY_RE.containsMatchIn("news@example.com"))
        assertFalse(NOREPLY_RE.containsMatchIn("alex@example.com"))
        assertFalse(NOREPLY_RE.containsMatchIn("x.news@example.com"))
    }
}
