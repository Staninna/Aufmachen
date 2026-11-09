package com.staninna.aufmachen.ui.people

import com.staninna.aufmachen.data.PersonStatsEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class PeopleFilterTest {
    private fun row(addr: String, tell: String, told: Boolean, weight: Int) =
        PersonRow(PersonStatsEntity(addr, "N", 1, 1, true, null, null, tell, weight), told)

    private val all = listOf(
        row("a@example.com", "YES", false, 5),
        row("b@example.com", "MAYBE", true, 9),
        row("c@example.com", "NO", false, 1),
        row("d@example.com", "YES", true, 7),
    )

    @Test fun noFilterSortsByWeight() {
        assertEquals(listOf("b", "d", "a", "c").map { "$it@example.com" }, filterPeople(all, emptySet()).map { it.address })
    }

    @Test fun tellFiltersAreUnioned() {
        val out = filterPeople(all, setOf(PeopleFilter.YES, PeopleFilter.MAYBE)).map { it.address }
        assertEquals(listOf("b@example.com", "d@example.com", "a@example.com"), out)
    }

    @Test fun notToldCombinesWithTell() {
        val out = filterPeople(all, setOf(PeopleFilter.YES, PeopleFilter.NOT_TOLD)).map { it.address }
        assertEquals(listOf("a@example.com"), out)
    }
}
