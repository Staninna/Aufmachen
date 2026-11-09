package com.staninna.aufmachen.engine

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TemplatesTest {
    private fun noDashes(m: Templates.Mail) {
        val all = m.subject + m.body
        assertFalse(all.contains(0x2014.toChar()))
        assertFalse(all.contains(0x2013.toChar()))
    }

    @Test fun erasureEnglish() {
        val m = Templates.gdprErasure("Example Shop", "old@example.com", dutch = false)
        assertTrue(m.subject.contains("Article 17") && m.subject.contains("Example Shop"))
        assertTrue(m.body.contains("old@example.com") && m.body.contains("one month"))
        noDashes(m)
    }

    @Test fun erasureDutch() {
        val m = Templates.gdprErasure("Voorbeeld", "old@example.com", dutch = true)
        assertTrue(m.subject.contains("artikel 17") && m.subject.contains("Voorbeeld"))
        assertTrue(m.body.contains("old@example.com") && m.body.contains("één maand"))
        noDashes(m)
    }

    @Test fun moved() {
        val en = Templates.moved("me@example.org", dutch = false)
        val nl = Templates.moved("me@example.org", dutch = true)
        assertTrue(en.body.contains("me@example.org") && en.body.contains("new email"))
        assertTrue(nl.body.contains("me@example.org") && nl.body.contains("nieuw e-mailadres"))
        noDashes(en); noDashes(nl)
    }

    @Test fun dutchDomain() {
        assertTrue(Templates.isDutchDomain("shop.nl"))
        assertFalse(Templates.isDutchDomain("shop.com"))
    }
}
