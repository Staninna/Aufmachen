package com.staninna.aufmachen.ui.unsubscribe

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.staninna.aufmachen.data.Repo
import com.staninna.aufmachen.data.ServiceView
import com.staninna.aufmachen.engine.UnsubMethod
import com.staninna.aufmachen.engine.unsubMethod
import com.staninna.aufmachen.model.Status
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn

data class UnsubRowUi(val sv: ServiceView, val method: UnsubMethod)

class UnsubscribeViewModel(repo: Repo) : ViewModel() {
    val showDone = MutableStateFlow(false)
    private val selected = MutableStateFlow<Set<String>>(emptySet())

    /** Null while loading. */
    val rows: StateFlow<List<UnsubRowUi>?> = combine(repo.services, showDone) { all, done ->
        all.asSequence()
            .filter { it.stats.newsletterLike }
            .filter { done || (it.status != Status.UNSUBSCRIBED && it.status != Status.IGNORED) }
            .sortedByDescending { it.stats.last12 }
            .map { UnsubRowUi(it, unsubMethod(it.unsubLinks, it.stats.unsubOneClick)) }
            .toList()
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /** Selected domains that are still visible and selectable. */
    val selectedVisible: StateFlow<Set<String>> = combine(rows, selected) { list, sel ->
        val ok = list.orEmpty().filter { it.method != UnsubMethod.NONE }.map { it.sv.domain }.toSet()
        sel.intersect(ok)
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    fun toggle(domain: String) {
        selected.value = selected.value.let { if (domain in it) it - domain else it + domain }
    }

    fun selectAll(on: Boolean) {
        selected.value = if (on) {
            rows.value.orEmpty().filter { it.method != UnsubMethod.NONE }.map { it.sv.domain }.toSet()
        } else emptySet()
    }
}
