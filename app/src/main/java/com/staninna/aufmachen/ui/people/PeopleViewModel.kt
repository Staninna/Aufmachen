package com.staninna.aufmachen.ui.people

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.staninna.aufmachen.data.AppDatabase
import com.staninna.aufmachen.data.PersonStateEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class PeopleViewModel(private val db: AppDatabase) : ViewModel() {
    val filters = MutableStateFlow<Set<PeopleFilter>>(emptySet())
    private val selected = MutableStateFlow<Set<String>>(emptySet())

    /** Null while loading. */
    val rows: StateFlow<List<PersonRow>?> =
        combine(db.people().all(), db.people().states(), filters) { people, states, f ->
            val told = states.filter { it.told }.map { it.address }.toSet()
            filterPeople(people.map { PersonRow(it, it.address in told) }, f)
        }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val selectedVisible: StateFlow<Set<String>> = combine(rows, selected) { list, sel ->
        sel.intersect(list.orEmpty().map { it.address }.toSet())
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    fun toggleFilter(f: PeopleFilter) {
        filters.value = filters.value.let { if (f in it) it - f else it + f }
    }

    fun toggle(address: String) {
        selected.value = selected.value.let { if (address in it) it - address else it + address }
    }

    fun selectAll(on: Boolean) {
        selected.value = if (on) rows.value.orEmpty().map { it.address }.toSet() else emptySet()
    }

    fun markTold(addresses: Collection<String>, told: Boolean) {
        viewModelScope.launch { setTold(db, addresses, told) }
    }
}

suspend fun setTold(db: AppDatabase, addresses: Collection<String>, told: Boolean) {
    val now = System.currentTimeMillis()
    db.people().upsertStates(addresses.map { PersonStateEntity(it, told, if (told) now else null) })
}
