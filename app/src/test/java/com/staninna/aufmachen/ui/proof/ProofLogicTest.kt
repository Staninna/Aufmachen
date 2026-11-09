package com.staninna.aufmachen.ui.proof

import com.staninna.aufmachen.engine.NameMatch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProofLogicTest {
    private val isNew = { a: String -> a.equals("me.other@example.org", true) }

    @Test fun addressVerdicts() {
        assertEquals(AddressVerdict.NOT_NEW, addressVerdict(null, isNew, null))
        assertEquals(AddressVerdict.NOT_NEW, addressVerdict("old@example.com", isNew, null))
        assertEquals(AddressVerdict.MATCHES, addressVerdict("ME.other@example.org", isNew, "me.other@example.org"))
        assertEquals(AddressVerdict.DIFFERS, addressVerdict("me.other@example.org", isNew, "me@example.org"))
        assertEquals(AddressVerdict.DIFFERS, addressVerdict("me.other@example.org", isNew, null))
    }

    @Test fun fileNameIsSafe() {
        assertEquals("shop.example.com-5.pdf", proofFileName("shop.example.com", 5))
        assertEquals("a_b-7.pdf", proofFileName("a/b", 7))
    }

    @Test fun failedClearsOnlyWithoutOpenAlerts() {
        assertFalse(failedAfterProof(true, 0))
        assertTrue(failedAfterProof(true, 2))
        assertFalse(failedAfterProof(false, 2))
    }

    @Test fun reviewRowsCheckMatched() {
        val rows = reviewRows(
            listOf("Shop", "Unknown"),
            listOf(NameMatch("Shop", "shop.example.com", 0.9)),
        )
        assertEquals(ReviewRow("Shop", "shop.example.com", true), rows[0])
        assertEquals(ReviewRow("Unknown", null, false), rows[1])
    }
}
