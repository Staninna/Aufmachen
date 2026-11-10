package com.staninna.aufmachen.ui.migrate

import com.staninna.aufmachen.model.Status
import com.staninna.aufmachen.model.Tier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MigrateFilterTest {
    @Test fun searchTextIsNotACountedFilter() {
        val f = MigrateFilter(query = "shop")
        assertEquals(0, f.activeCount)
        assertTrue(f.active)
    }

    @Test fun everyChosenOptionCounts() {
        val f = MigrateFilter(
            tiers = setOf(Tier.CRITICAL, Tier.LOW), category = "Finance",
            statuses = setOf(Status.TODO), failed = true, googleLogin = true, hasSteps = true,
        )
        assertEquals(7, f.activeCount)
    }

    @Test fun emptyFilterIsInactive() {
        assertEquals(0, MigrateFilter().activeCount)
        assertFalse(MigrateFilter().active)
    }
}
