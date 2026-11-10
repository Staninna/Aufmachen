package com.staninna.aufmachen.ui.migrate

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.staninna.aufmachen.AppContainer
import com.staninna.aufmachen.data.ServiceView
import com.staninna.aufmachen.model.Placement
import com.staninna.aufmachen.model.Status
import com.staninna.aufmachen.model.Tier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

data class MigrateFilter(
    val query: String = "",
    val tiers: Set<Tier> = emptySet(),
    val category: String? = null,
    val statuses: Set<Status> = emptySet(),
    val failed: Boolean = false,
    val googleLogin: Boolean = false,
    val hasSteps: Boolean = false,
) {
    val active: Boolean
        get() = query.isNotBlank() || activeCount > 0

    /** How many filters the Filters sheet holds are on. The search text does not count. */
    val activeCount: Int
        get() = tiers.size + statuses.size + (if (category != null) 1 else 0) +
            listOf(failed, googleLogin, hasSteps).count { it }
}

/** A service plus its lowercase search text, built once per data change. */
class MigrateRow(val view: ServiceView, val haystack: String)

data class MigrateUi(
    val loaded: Boolean = false,
    val totalWithAccount: Int = 0,
    val categories: List<String> = emptyList(),
    val rows: List<ServiceView> = emptyList(),
)

internal fun matches(r: MigrateRow, f: MigrateFilter): Boolean {
    val s = r.view
    if (f.tiers.isNotEmpty() && s.tier !in f.tiers) return false
    if (f.category != null && s.stats.category != f.category) return false
    if (f.statuses.isNotEmpty() || f.failed) {
        if (!((s.status in f.statuses) || (f.failed && s.state.failed))) return false
    }
    if (f.googleLogin && !s.state.googleLogin) return false
    if (f.hasSteps && s.state.stepsJson.length <= 2) return false
    val q = f.query.trim().lowercase()
    if (q.isNotEmpty() && !r.haystack.contains(q)) return false
    return true
}

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class MigrateViewModel(c: AppContainer) : ViewModel() {
    val filter = MutableStateFlow(MigrateFilter())

    private val all = c.repo.services.map { list ->
        list.filter { it.inTab(Placement.MIGRATE) }.sortedByDescending { it.stats.score }
            .map { MigrateRow(it, (listOf(it.domain) + it.displayNames).joinToString(" ").lowercase()) }
    }.flowOn(Dispatchers.Default)

    val ui: StateFlow<MigrateUi> = combine(all, filter.debounce(80)) { rows, f ->
        MigrateUi(
            loaded = true,
            totalWithAccount = rows.size,
            categories = rows.map { it.view.stats.category }.filter { it.isNotBlank() }.distinct().sorted(),
            rows = rows.filter { matches(it, f) }.map { it.view },
        )
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), MigrateUi())

    fun update(block: (MigrateFilter) -> MigrateFilter) { filter.update(block) }

    private val selected = MutableStateFlow<Set<String>>(emptySet())

    /** Selected domains that the current filters still show. */
    val selectedVisible: StateFlow<Set<String>> = combine(ui, selected) { u, sel ->
        sel.intersect(u.rows.map { it.domain }.toSet())
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    fun toggle(domain: String) {
        selected.value = selected.value.let { if (domain in it) it - domain else it + domain }
    }

    fun selectAll(on: Boolean) {
        selected.value = if (on) ui.value.rows.map { it.domain }.toSet() else emptySet()
    }
}
