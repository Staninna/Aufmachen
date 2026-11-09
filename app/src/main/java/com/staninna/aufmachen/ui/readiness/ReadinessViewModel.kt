package com.staninna.aufmachen.ui.readiness

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.staninna.aufmachen.AppContainer
import com.staninna.aufmachen.data.AppSettings
import com.staninna.aufmachen.data.ServiceView
import com.staninna.aufmachen.model.Status
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ReadinessViewModel(private val c: AppContainer) : ViewModel() {
    val readiness: StateFlow<Readiness?> = c.readinessFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /** Every service that logs in with Google, switched or not, so a tick can be undone. */
    val loginServices: StateFlow<List<ServiceView>> = c.repo.services
        .map { l -> l.filter { it.stats.hasAccount && it.state.googleLogin && it.status != Status.DELETED && it.status != Status.IGNORED } }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val settings: StateFlow<AppSettings?> = c.settings.flow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun setLoginSwitched(domain: String, v: Boolean) = viewModelScope.launch {
        c.repo.updateState(domain) { it.copy(loginSwitched = v) }
    }

    fun setTakeout(v: Boolean) = viewModelScope.launch { c.settings.update { it.copy(takeoutDone = v) } }

    fun overrideGate() = viewModelScope.launch { c.settings.update { it.copy(readinessOverride = true) } }

    fun setEndgameTick(id: String, v: Boolean) = viewModelScope.launch {
        c.settings.update { s -> s.copy(endgameTicks = if (v) (s.endgameTicks + id).distinct() else s.endgameTicks - id) }
    }
}
