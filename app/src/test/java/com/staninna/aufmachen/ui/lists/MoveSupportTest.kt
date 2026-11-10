package com.staninna.aufmachen.ui.lists

import com.staninna.aufmachen.model.Placement
import org.junit.Assert.assertEquals
import org.junit.Test

class MoveSupportTest {
    @Test fun restoreOnlyWhatChanged() {
        val before = mapOf("a.example.com" to null, "b.example.com" to Placement.DORMANT, "c.example.com" to Placement.DELETE)
        val now = mapOf("a.example.com" to Placement.DELETE, "b.example.com" to Placement.DELETE, "c.example.com" to Placement.DELETE)
        val out = restoreGroups(before, now)
        assertEquals(mapOf<Placement?, List<String>>(null to listOf("a.example.com"), Placement.DORMANT to listOf("b.example.com")), out)
    }

    @Test fun domainWithoutStateRestoresToAutomatic() {
        val out = restoreGroups(emptyMap(), mapOf("new.example.com" to Placement.MIGRATE))
        assertEquals(mapOf<Placement?, List<String>>(null to listOf("new.example.com")), out)
    }

    @Test fun nothingChangedNothingRestored() {
        val m = mapOf("a.example.com" to Placement.PEOPLE)
        assertEquals(emptyMap<Placement?, List<String>>(), restoreGroups(m, m))
    }

    @Test fun recipientsKeepOrderAndDropRepeats() {
        val out = movedRecipients(listOf("a@example.com"), listOf(listOf("b@example.org", "a@example.com"), listOf("c@example.org")))
        assertEquals(listOf("a@example.com", "b@example.org", "c@example.org"), out)
    }

    @Test fun sharedPlacementNeedsAgreement() {
        assertEquals(Placement.DELETE, sharedPlacement(listOf(Placement.DELETE, Placement.DELETE)))
        assertEquals(null, sharedPlacement(listOf(Placement.DELETE, null)))
    }
}
