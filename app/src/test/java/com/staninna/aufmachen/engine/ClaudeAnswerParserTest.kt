package com.staninna.aufmachen.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ClaudeAnswerParserTest {
    private val clean = """
        SETTINGS_URL: https://example.com/settings/email
        PRIVACY_CONTACT: privacy@example.com
        STEPS:
        1. Open settings
        2. Change the email
        GOTCHAS:
        - Needs a confirmation mail
    """.trimIndent()

    @Test fun cleanBlock() {
        val a = ClaudeAnswerParser.parse(clean)!!
        assertEquals("https://example.com/settings/email", a.settingsUrl)
        assertEquals("privacy@example.com", a.privacyContact)
        assertEquals(listOf("Open settings", "Change the email"), a.steps)
        assertEquals(listOf("Needs a confirmation mail"), a.gotchas)
    }

    @Test fun fencedBlockWithChatterAround() {
        val a = ClaudeAnswerParser.parse("Sure, here you go:\n\n```\n$clean\n```\nHope this helps!")!!
        assertEquals(2, a.steps.size)
        assertEquals(listOf("Needs a confirmation mail"), a.gotchas)
    }

    @Test fun boldKeysAndLowercase() {
        val a = ClaudeAnswerParser.parse(
            "**SETTINGS_URL:** https://example.com/a\n**privacy_contact:** privacy@example.com\n**STEPS:**\n1. One\n**GOTCHAS:**\n* Two",
        )!!
        assertEquals("https://example.com/a", a.settingsUrl)
        assertEquals("privacy@example.com", a.privacyContact)
        assertEquals(listOf("One"), a.steps)
        assertEquals(listOf("Two"), a.gotchas)
    }

    @Test fun parenNumberingAndBullets() {
        val a = ClaudeAnswerParser.parse("Steps:\n  1)  First\n  2)  Second\ngotchas:\n • Careful")!!
        assertEquals(listOf("First", "Second"), a.steps)
        assertEquals(listOf("Careful"), a.gotchas)
    }

    @Test fun multiLineSteps() {
        val a = ClaudeAnswerParser.parse("STEPS:\n1. Open the menu\n   and pick Account\n2. Save")!!
        assertEquals(listOf("Open the menu and pick Account", "Save"), a.steps)
    }

    @Test fun noneValues() {
        val a = ClaudeAnswerParser.parse("SETTINGS_URL: NONE\nPRIVACY_CONTACT: n/a\nSTEPS:\n1. Ask support\nGOTCHAS:\n- none")!!
        assertNull(a.settingsUrl)
        assertNull(a.privacyContact)
        assertEquals(emptyList<String>(), a.gotchas)
        val b = ClaudeAnswerParser.parse("SETTINGS_URL:\nPRIVACY_CONTACT: none\nSTEPS:\n1. x")!!
        assertNull(b.settingsUrl)
        assertNull(b.privacyContact)
    }

    @Test fun markdownLinkAndAngleBracketsAndMailto() {
        val a = ClaudeAnswerParser.parse("SETTINGS_URL: [settings](https://example.com/s)\nPRIVACY_CONTACT: mailto:dpo@example.org\nSTEPS:\n1. x")!!
        assertEquals("https://example.com/s", a.settingsUrl)
        assertEquals("dpo@example.org", a.privacyContact)
        val b = ClaudeAnswerParser.parse("SETTINGS_URL: <https://example.com/t>\nPRIVACY_CONTACT: <dpo@example.org>\nSTEPS:\n1. x")!!
        assertEquals("https://example.com/t", b.settingsUrl)
        assertEquals("dpo@example.org", b.privacyContact)
    }

    @Test fun garbageReturnsNull() {
        assertNull(ClaudeAnswerParser.parse("I could not find anything about that service, sorry."))
        assertNull(ClaudeAnswerParser.parse(""))
    }
}
