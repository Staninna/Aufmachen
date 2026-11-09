package com.staninna.aufmachen.ui.people

import com.staninna.aufmachen.data.PersonStatsEntity
import com.staninna.aufmachen.model.TellNewAddress

data class PersonRow(val stats: PersonStatsEntity, val told: Boolean) {
    val address: String get() = stats.address
    val tell: TellNewAddress get() = runCatching { TellNewAddress.valueOf(stats.tell) }.getOrDefault(TellNewAddress.NO)
}

enum class PeopleFilter { YES, MAYBE, NO, NOT_TOLD }

/**
 * Tell-flag filters (YES, MAYBE, NO) combine with OR, NOT_TOLD narrows further. No filter keeps everyone.
 * Sorted by weight, heaviest first.
 */
fun filterPeople(all: List<PersonRow>, filters: Set<PeopleFilter>): List<PersonRow> {
    val tells = buildSet {
        if (PeopleFilter.YES in filters) add(TellNewAddress.YES)
        if (PeopleFilter.MAYBE in filters) add(TellNewAddress.MAYBE)
        if (PeopleFilter.NO in filters) add(TellNewAddress.NO)
    }
    val notTold = PeopleFilter.NOT_TOLD in filters
    return all.filter { (tells.isEmpty() || it.tell in tells) && (!notTold || !it.told) }
        .sortedByDescending { it.stats.weight }
}
