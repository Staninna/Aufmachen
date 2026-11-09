package com.staninna.aufmachen.engine

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ClaudePromptTest {
    private val facts = PromptFacts(
        name = "Example Shop", domains = listOf("example.com", "mail.example.org"),
        displayNames = listOf("Example Shop Team"), category = "shopping",
        hasSecurityMails = true, hasBilling = true, hasOrders = false,
        googleLoginSuspected = true, dormantYears = 3,
    )

    @Test fun migrateHasFormatAndFacts() {
        val p = ClaudePrompt.migrate(facts)
        for (s in listOf("SETTINGS_URL:", "PRIVACY_CONTACT:", "STEPS:", "GOTCHAS:", "Example Shop", "example.com",
            "Example Shop Team", "shopping", "Tuta", "switching the login method", "3 years")) {
            assertTrue(s, p.contains(s))
        }
        assertFalse(p.contains('@'))
    }

    @Test fun deleteHasFormatAndNoAt() {
        val p = ClaudePrompt.delete(facts.copy(googleLoginSuspected = false, dormantYears = null))
        assertTrue(p.contains("Delete my account"))
        assertTrue(p.contains("STEPS:"))
        assertFalse(p.contains("switching the login method"))
        assertFalse(p.contains('@'))
    }

    @Test fun stripsAtFromFacts() {
        assertFalse(ClaudePrompt.migrate(facts.copy(name = "a@b")).contains('@'))
    }
}
