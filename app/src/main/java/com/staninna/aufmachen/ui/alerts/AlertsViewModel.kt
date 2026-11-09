package com.staninna.aufmachen.ui.alerts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.staninna.aufmachen.AppContainer
import com.staninna.aufmachen.data.AlertEntity
import com.staninna.aufmachen.data.ServiceView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class AlertsUi(
    val loaded: Boolean = false,
    val open: List<AlertEntity> = emptyList(),
    val dismissed: List<AlertEntity> = emptyList(),
    val services: Map<String, ServiceView> = emptyMap(),
)

class AlertsViewModel(private val c: AppContainer) : ViewModel() {
    val ui: StateFlow<AlertsUi> = combine(c.db.alerts().all(), c.repo.services) { alerts, services ->
        AlertsUi(
            loaded = true,
            open = alerts.filter { !it.dismissed },
            dismissed = alerts.filter { it.dismissed },
            services = services.associateBy { it.domain },
        )
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AlertsUi())

    /** Dismisses with a reason. Clears the service's failed flag once no other alert is open. */
    fun dismiss(alert: AlertEntity, reason: String) = viewModelScope.launch {
        c.db.alerts().dismiss(alert.id, reason)
        if (c.db.alerts().openCountFor(alert.domain) == 0) {
            c.repo.updateState(alert.domain) { it.copy(failed = false) }
        }
    }
}
