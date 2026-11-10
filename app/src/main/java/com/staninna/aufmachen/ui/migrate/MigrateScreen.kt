package com.staninna.aufmachen.ui.migrate

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.staninna.aufmachen.R
import com.staninna.aufmachen.container
import com.staninna.aufmachen.data.ServiceView
import com.staninna.aufmachen.model.Placement
import com.staninna.aufmachen.model.Status
import com.staninna.aufmachen.model.Tier
import com.staninna.aufmachen.ui.actions.relativeDay
import com.staninna.aufmachen.ui.common.MoveToDialog
import com.staninna.aufmachen.ui.common.StatusChip
import com.staninna.aufmachen.ui.common.TabTopBar
import com.staninna.aufmachen.ui.common.TierStripe
import com.staninna.aufmachen.ui.common.statusLabel
import com.staninna.aufmachen.ui.common.tierLabel
import com.staninna.aufmachen.ui.lists.MoveRequest
import com.staninna.aufmachen.ui.lists.rememberMoveActions
import com.staninna.aufmachen.ui.lists.sharedPlacement
import com.staninna.aufmachen.ui.nav.Routes

@Composable
fun MigrateScreen(nav: NavHostController) {
    val ctx = LocalContext.current
    val vm = viewModel { MigrateViewModel(ctx.container) }
    val ui by vm.ui.collectAsState()
    val f by vm.filter.collectAsState()
    val selected by vm.selectedVisible.collectAsState()
    val mover = rememberMoveActions()
    var request by remember { mutableStateOf<MoveRequest?>(null) }
    var sheet by remember { mutableStateOf(false) }

    Scaffold(
        topBar = { TabTopBar(stringResource(R.string.nav_migrate), nav) },
        snackbarHost = { SnackbarHost(mover.snackbar) },
        bottomBar = {
            if (selected.isNotEmpty()) Surface(tonalElevation = 3.dp) {
                Row(
                    Modifier.fillMaxWidth().padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(stringResource(R.string.lists_selected_count, selected.size), Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
                    Button(onClick = {
                        val now = ui.rows.filter { it.domain in selected }
                        request = MoveRequest(now.map { it.domain }, sharedPlacement(now.map { it.placement }))
                    }) { Text(stringResource(R.string.move_action)) }
                }
            }
        },
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {
            OutlinedTextField(
                value = f.query,
                onValueChange = { q -> vm.update { it.copy(query = q) } },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                singleLine = true,
                placeholder = { Text(stringResource(R.string.home_migrate_search)) },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                trailingIcon = if (f.query.isNotEmpty()) {
                    { IconButton(onClick = { vm.update { it.copy(query = "") } }) { Icon(Icons.Default.Clear, stringResource(R.string.home_migrate_clear)) } }
                } else null,
            )
            Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                if (selected.isNotEmpty()) {
                    Checkbox(checked = selected.size == ui.rows.size, onCheckedChange = { vm.selectAll(it) })
                    Text(stringResource(R.string.lists_select_all), Modifier.weight(1f))
                } else {
                    if (ui.loaded && ui.totalWithAccount > 0) {
                        Text(
                            stringResource(R.string.home_migrate_count, ui.rows.size, ui.totalWithAccount),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                    } else Box(Modifier.weight(1f))
                }
                OutlinedButton(onClick = { sheet = true }) {
                    BadgedBox(badge = { if (f.activeCount > 0) Badge { Text("${f.activeCount}") } }) {
                        Icon(Icons.Default.FilterList, null)
                    }
                    Text(stringResource(R.string.lists_filters), Modifier.padding(start = 8.dp))
                }
            }
            when {
                !ui.loaded -> Unit
                ui.totalWithAccount == 0 -> Empty(R.string.home_migrate_empty_title, R.string.home_migrate_empty_body)
                ui.rows.isEmpty() -> Empty(R.string.home_migrate_nomatch_title, R.string.home_migrate_nomatch_body)
                else -> LazyColumn(Modifier.fillMaxSize()) {
                    items(ui.rows, key = { it.domain }) { s ->
                        ServiceRow(
                            s,
                            selecting = selected.isNotEmpty(),
                            checked = s.domain in selected,
                            onClick = { if (selected.isNotEmpty()) vm.toggle(s.domain) else nav.navigate(Routes.service(s.domain)) },
                            onLongClick = { if (selected.isEmpty()) vm.toggle(s.domain) },
                            onMove = { request = MoveRequest(listOf(s.domain), s.placement) },
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }

    if (sheet) FiltersSheet(f, ui.categories, vm) { sheet = false }

    request?.let { r ->
        MoveToDialog(r.ids.size, r.current, onPick = { to ->
            request = null
            mover.move(domains = r.ids, to = to) { vm.selectAll(false) }
        }, onDismiss = { request = null })
    }
}

@Composable
private fun Empty(title: Int, body: Int) {
    Column(Modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text(stringResource(title), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(body), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun FiltersSheet(f: MigrateFilter, categories: List<String>, vm: MigrateViewModel, onClose: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onClose) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.lists_filters), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                TextButton(
                    onClick = { vm.update { it.copy(tiers = emptySet(), category = null, statuses = emptySet(), failed = false, googleLogin = false, hasSteps = false) } },
                    enabled = f.activeCount > 0,
                ) { Text(stringResource(R.string.lists_filters_clear)) }
            }
            SheetTitle(R.string.lists_filters_tier)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Tier.entries.forEach { t ->
                    FilterChip(t in f.tiers, { vm.update { it.copy(tiers = it.tiers.toggle(t)) } }, label = { Text(tierLabel(t)) })
                }
            }
            SheetTitle(R.string.lists_filters_status)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Status.entries.forEach { st ->
                    FilterChip(st in f.statuses, { vm.update { it.copy(statuses = it.statuses.toggle(st)) } }, label = { Text(statusLabel(st)) })
                }
                FilterChip(f.failed, { vm.update { it.copy(failed = !it.failed) } }, label = { Text(stringResource(R.string.home_migrate_failed)) })
            }
            if (categories.isNotEmpty()) {
                SheetTitle(R.string.lists_filters_category)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    categories.forEach { c ->
                        FilterChip(f.category == c, { vm.update { it.copy(category = if (it.category == c) null else c) } }, label = { Text(c) })
                    }
                }
            }
            SheetTitle(R.string.lists_filters_other)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(f.googleLogin, { vm.update { it.copy(googleLogin = !it.googleLogin) } }, label = { Text(stringResource(R.string.home_migrate_google)) })
                FilterChip(f.hasSteps, { vm.update { it.copy(hasSteps = !it.hasSteps) } }, label = { Text(stringResource(R.string.home_migrate_steps)) })
            }
        }
    }
}

@Composable
private fun SheetTitle(res: Int) {
    Text(stringResource(res), Modifier.padding(top = 16.dp, bottom = 4.dp), style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
}

private fun <T> Set<T>.toggle(x: T): Set<T> = if (x in this) this - x else this + x

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ServiceRow(
    s: ServiceView, selecting: Boolean, checked: Boolean,
    onClick: () -> Unit, onLongClick: () -> Unit, onMove: () -> Unit,
) {
    val ctx = LocalContext.current
    var menu by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().height(IntrinsicSize.Min).combinedClickable(onClick = onClick, onLongClick = onLongClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TierStripe(s.tier)
        if (selecting) Checkbox(checked, { onClick() })
        Column(Modifier.weight(1f).padding(horizontal = 12.dp, vertical = 8.dp)) {
            Text(s.name, style = MaterialTheme.typography.titleMedium, maxLines = 1)
            Text(
                listOf(s.stats.category, stringResource(R.string.home_migrate_row_stats, s.stats.mails, relativeDay(ctx, s.stats.lastEpochDay)))
                    .filter { it.isNotBlank() }.joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
        StatusChip(s, onClick)
        Box {
            IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, stringResource(R.string.lists_row_menu)) }
            DropdownMenu(menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text(stringResource(R.string.move_action)) }, onClick = { menu = false; onMove() })
            }
        }
    }
}
