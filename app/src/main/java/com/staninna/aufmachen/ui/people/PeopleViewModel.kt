package com.staninna.aufmachen.ui.people

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.staninna.aufmachen.AppContainer
import com.staninna.aufmachen.data.AppDatabase
import com.staninna.aufmachen.data.ServiceView
import com.staninna.aufmachen.data.inPeople
import com.staninna.aufmachen.model.Placement
import com.staninna.aufmachen.ui.lists.movedRecipients
import com.staninna.aufmachen.data.PersonStateEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class PeopleViewModel(c: AppContainer) : ViewModel() {
    private val db = c.db
    val filters = MutableStateFlow<Set<PeopleFilter>>(emptySet())
    private val selected = MutableStateFlow<Set<String>>(emptySet())

    /** Null while loading. People the user moved to another tab are left out. */
    val rows: StateFlow<List<PersonRow>?> =
        combine(db.people().all(), db.people().states(), filters) { people, states, f ->
            val byAddr = states.associateBy { it.address }
            val told = states.filter { it.told }.map { it.address }.toSet()
            filterPeople(people.filter { byAddr[it.address].inPeople() }.map { PersonRow(it, it.address in told) }, f)
        }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /** Services the user moved to People, heaviest first. */
    val moved: StateFlow<List<ServiceView>> = c.repo.services.map { all ->
        all.filter { it.placement == Placement.PEOPLE }.sortedByDescending { it.stats.score }
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Selected ids still on screen: person addresses and moved-service domains. */
    val selectedVisible: StateFlow<Set<String>> = combine(rows, moved, selected) { list, svc, sel ->
        sel.intersect(list.orEmpty().map { it.address }.toSet() + svc.map { it.domain }.toSet())
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    fun toggleFilter(f: PeopleFilter) {
        filters.value = filters.value.let { if (f in it) it - f else it + f }
    }

    fun toggle(id: String) {
        selected.value = selected.value.let { if (id in it) it - id else it + id }
    }

    fun selectAll(on: Boolean) {
        selected.value = if (on) rows.value.orEmpty().map { it.address }.toSet() + moved.value.map { it.domain } else emptySet()
    }

    /** Addresses of the chosen people, in list order. */
    fun selectedPeople(): List<String> = rows.value.orEmpty().map { it.address }.filter { it in selectedVisible.value }

    fun selectedServices(): List<ServiceView> = moved.value.filter { it.domain in selectedVisible.value }

    /** Who "I've moved" goes to: chosen people, then the sender addresses of chosen moved services. */
    fun recipients(): List<String> = movedRecipients(selectedPeople(), selectedServices().map { it.senderAddrs })

    fun markTold(told: Boolean) {
        val who = recipients()
        viewModelScope.launch { setTold(db, who, told) }
    }
}

/** Sets the told flag, keeping everything else on each person's state (such as their tab). */
suspend fun setTold(db: AppDatabase, addresses: Collection<String>, told: Boolean) {
    val now = System.currentTimeMillis()
    val existing = db.people().statesOnce().associateBy { it.address }
    db.people().upsertStates(addresses.map {
        (existing[it] ?: PersonStateEntity(it)).copy(told = told, toldAt = if (told) now else null)
    })
}
