package com.staninna.aufmachen.ui.dormant

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.staninna.aufmachen.data.Repo
import com.staninna.aufmachen.data.ServiceView
import com.staninna.aufmachen.model.Placement
import com.staninna.aufmachen.model.Status
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private val HANDLED = setOf(Status.DELETED, Status.DELETION_REQUESTED, Status.IGNORED)

class DormantViewModel(private val repo: Repo) : ViewModel() {
    val showHandled = MutableStateFlow(false)
    private val selected = MutableStateFlow<Set<String>>(emptySet())

    /** Null while loading. */
    val rows: StateFlow<List<ServiceView>?> = combine(repo.services, showHandled) { all, handled ->
        all.filter { it.inTab(Placement.DORMANT) && (handled || it.status !in HANDLED) }
            .sortedByDescending { it.stats.score }
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val selectedVisible: StateFlow<Set<String>> = combine(rows, selected) { list, sel ->
        sel.intersect(list.orEmpty().map { it.domain }.toSet())
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    fun toggle(domain: String) {
        selected.value = selected.value.let { if (domain in it) it - domain else it + domain }
    }

    fun selectAll(on: Boolean) {
        selected.value = if (on) rows.value.orEmpty().map { it.domain }.toSet() else emptySet()
    }

    /** Selected services in list order. */
    fun selectedInOrder(): List<ServiceView> =
        rows.value.orEmpty().filter { it.domain in selectedVisible.value }

    fun setStatus(domains: List<String>, status: Status) {
        viewModelScope.launch {
            domains.forEach { repo.setStatus(it, status) }
            selected.value = selected.value - domains.toSet()
        }
    }
}
