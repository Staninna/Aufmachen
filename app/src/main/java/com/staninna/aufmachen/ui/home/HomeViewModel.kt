package com.staninna.aufmachen.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.staninna.aufmachen.AppContainer
import com.staninna.aufmachen.data.AlertEntity
import com.staninna.aufmachen.data.ServiceView
import com.staninna.aufmachen.data.SyncStateEntity
import com.staninna.aufmachen.model.Placement
import com.staninna.aufmachen.model.Status
import com.staninna.aufmachen.model.Tier
import com.staninna.aufmachen.ui.readiness.Readiness
import com.staninna.aufmachen.ui.readiness.readinessFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class TierSlice(val tier: Tier, val done: Int, val open: Int) {
    val total: Int get() = done + open
}

data class HomeUi(
    val loaded: Boolean = false,
    val total: Int = 0,
    val slices: List<TierSlice> = emptyList(),
    val statusCounts: Map<Status, Int> = emptyMap(),
    val failed: Int = 0,
    val next: ServiceView? = null,
    val queue: Int = 0,
) {
    val done: Int get() = slices.sumOf { it.done }
}

/** Failed services first (they need attention), then open services by score. Snoozed ones wait. */
internal fun buildHomeUi(all: List<ServiceView>): HomeUi {
    val withAccount = all.filter { it.inTab(Placement.MIGRATE) }
    val slices = Tier.entries.map { t ->
        val inTier = withAccount.filter { it.tier == t }
        TierSlice(t, inTier.count { it.status != Status.TODO }, inTier.count { it.status == Status.TODO })
    }
    val counts = withAccount.groupingBy { it.status }.eachCount()
    val sorted = withAccount.sortedByDescending { it.stats.score }
    val queue = sorted.filter { !it.snoozed && (it.state.failed || it.status == Status.TODO) }
    val ordered = queue.filter { it.state.failed } + queue.filter { !it.state.failed }
    return HomeUi(
        loaded = true,
        total = withAccount.size,
        slices = slices,
        statusCounts = counts,
        failed = withAccount.count { it.state.failed },
        next = ordered.firstOrNull(),
        queue = ordered.size,
    )
}

class HomeViewModel(private val c: AppContainer) : ViewModel() {
    val ui: StateFlow<HomeUi> = c.repo.services.map { buildHomeUi(it) }.flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HomeUi())
    val sync: StateFlow<List<SyncStateEntity>?> = c.db.sync().all()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val openAlerts: StateFlow<List<AlertEntity>> = c.db.alerts().open()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val readiness: StateFlow<Readiness?> = c.readinessFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /** Marks changed. A failed service also gets its failure flag cleared, so the card advances. */
    fun markChanged(s: ServiceView) = viewModelScope.launch {
        c.repo.setStatus(s.domain, Status.CHANGED)
        if (s.state.failed) c.repo.updateState(s.domain) { it.copy(failed = false) }
    }

    fun skip(s: ServiceView) = viewModelScope.launch { c.repo.snooze(s.domain) }
    fun ignore(s: ServiceView) = viewModelScope.launch { c.repo.setStatus(s.domain, Status.IGNORED) }
}
