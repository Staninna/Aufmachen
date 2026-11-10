package com.staninna.aufmachen.ui.people

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.staninna.aufmachen.R
import com.staninna.aufmachen.container
import com.staninna.aufmachen.data.ServiceView
import com.staninna.aufmachen.model.Placement
import com.staninna.aufmachen.model.TellNewAddress
import com.staninna.aufmachen.ui.actions.relativeDay
import com.staninna.aufmachen.ui.common.MoveToDialog
import com.staninna.aufmachen.ui.common.TabTopBar
import com.staninna.aufmachen.ui.lists.AlsoMoveDialog
import com.staninna.aufmachen.ui.lists.MoveRequest
import com.staninna.aufmachen.ui.lists.rememberMoveActions
import com.staninna.aufmachen.ui.lists.sharedPlacement
import com.staninna.aufmachen.ui.nav.Routes
import com.staninna.aufmachen.ui.nav.Selection
import kotlinx.coroutines.launch

/** A pending move of people whose companies the user may also want to move. */
private class AlsoRequest(val addresses: List<String>, val to: Placement, val domains: List<String>)

@Composable
fun PeopleScreen(nav: NavHostController) {
    val ctx = LocalContext.current
    val vm: PeopleViewModel = viewModel { PeopleViewModel(ctx.container) }
    val rows by vm.rows.collectAsState()
    val moved by vm.moved.collectAsState()
    val filters by vm.filters.collectAsState()
    val selected by vm.selectedVisible.collectAsState()
    val mover = rememberMoveActions()
    val scope = rememberCoroutineScope()
    var request by remember { mutableStateOf<MoveRequest?>(null) }
    var also by remember { mutableStateOf<AlsoRequest?>(null) }

    Scaffold(
        topBar = { TabTopBar(stringResource(R.string.nav_people), nav) },
        snackbarHost = { SnackbarHost(mover.snackbar) },
        bottomBar = {
            if (selected.isNotEmpty()) Surface(tonalElevation = 3.dp) {
                Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.lists_selected_count, selected.size), style = MaterialTheme.typography.labelLarge)
                    Button(
                        onClick = {
                            Selection.people = vm.recipients()
                            nav.navigate(Routes.COMPOSE_MOVED)
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(R.string.lists_people_compose)) }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton({ vm.markTold(true) }, Modifier.weight(1f)) {
                            Text(stringResource(R.string.lists_people_mark_told))
                        }
                        OutlinedButton({ vm.markTold(false) }, Modifier.weight(1f)) {
                            Text(stringResource(R.string.lists_people_mark_not_told))
                        }
                    }
                    OutlinedButton(
                        onClick = {
                            val p = vm.selectedPeople()
                            val s = vm.selectedServices()
                            val cur = rows.orEmpty().filter { it.address in p }.map { null } + s.map { it.placement }
                            request = MoveRequest(p + s.map { it.domain }, sharedPlacement(cur))
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(R.string.move_action)) }
                }
            }
        },
    ) { pad ->
        val list = rows
        Column(Modifier.padding(pad).fillMaxSize()) {
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PeopleFilter.entries.forEach { f ->
                    FilterChip(f in filters, { vm.toggleFilter(f) }, label = { Text(filterLabel(f)) })
                }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = selected.isNotEmpty() && selected.size == list.orEmpty().size + moved.size,
                    onCheckedChange = { vm.selectAll(it) },
                    enabled = !list.isNullOrEmpty() || moved.isNotEmpty(),
                )
                Text(stringResource(R.string.lists_select_all))
            }
            HorizontalDivider()
            when {
                list == null -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
                list.isEmpty() && moved.isEmpty() -> Box(Modifier.fillMaxSize().padding(24.dp), Alignment.Center) {
                    Text(stringResource(R.string.lists_people_empty), style = MaterialTheme.typography.bodyLarge)
                }
                else -> LazyColumn(Modifier.fillMaxSize()) {
                    if (moved.isNotEmpty()) {
                        item(key = "h-moved") { SectionHeader(R.string.lists_people_moved_here) }
                        items(moved, key = { "s-" + it.domain }) { sv ->
                            MovedRow(
                                sv, sv.domain in selected,
                                onToggle = { vm.toggle(sv.domain) },
                                onOpen = { nav.navigate(Routes.service(sv.domain)) },
                                onMove = { request = MoveRequest(listOf(sv.domain), sv.placement) },
                            )
                            HorizontalDivider()
                        }
                        if (list.isNotEmpty()) item(key = "h-people") { SectionHeader(R.string.nav_people) }
                    }
                    items(list, key = { it.address }) { p ->
                        PersonItem(
                            p, p.address in selected,
                            onToggle = { vm.toggle(p.address) },
                            onMove = { request = MoveRequest(listOf(p.address), null) },
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }

    request?.let { r ->
        MoveToDialog(r.ids.size, r.current, onPick = { to ->
            request = null
            val people = r.ids.filter { '@' in it }
            val domains = r.ids - people.toSet()
            scope.launch {
                val companies = if (people.isNotEmpty() && to != null && to != Placement.PEOPLE) {
                    mover.servicesOfPeople(people) - domains.toSet()
                } else emptyList()
                if (companies.isEmpty()) {
                    mover.move(domains = domains, addresses = people, to = to) { vm.selectAll(false) }
                } else also = AlsoRequest(r.ids, to!!, companies)
            }
        }, onDismiss = { request = null })
    }

    also?.let { a ->
        val people = a.addresses.filter { '@' in it }
        val domains = a.addresses - people.toSet()
        AlsoMoveDialog(a.to, a.domains, onMove = { checked ->
            also = null
            mover.move(domains = domains, addresses = people, to = a.to, alsoServices = checked) { vm.selectAll(false) }
        }, onCancel = { also = null })
    }
}

@Composable
private fun SectionHeader(res: Int) {
    Text(
        stringResource(res),
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun filterLabel(f: PeopleFilter) = stringResource(
    when (f) {
        PeopleFilter.YES -> R.string.lists_tell_yes
        PeopleFilter.MAYBE -> R.string.lists_tell_maybe
        PeopleFilter.NO -> R.string.lists_tell_no
        PeopleFilter.NOT_TOLD -> R.string.lists_people_not_told
    },
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MovedRow(sv: ServiceView, checked: Boolean, onToggle: () -> Unit, onOpen: () -> Unit, onMove: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().combinedClickable(onClick = onOpen, onLongClick = onMove), verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked, { onToggle() })
        Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
            Text(sv.name, style = MaterialTheme.typography.titleSmall, maxLines = 1)
            Text(
                listOfNotNull(
                    sv.displayNames.firstOrNull(),
                    pluralStringResource(R.plurals.lists_people_addresses, sv.senderAddrs.size, sv.senderAddrs.size),
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
        Box {
            IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, stringResource(R.string.lists_row_menu)) }
            DropdownMenu(menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text(stringResource(R.string.move_action)) }, onClick = { menu = false; onMove() })
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PersonItem(p: PersonRow, checked: Boolean, onToggle: () -> Unit, onMove: () -> Unit) {
    val ctx = LocalContext.current
    var menu by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().combinedClickable(onClick = onToggle, onLongClick = onMove), verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked, { onToggle() })
        Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
            Text(p.stats.name.ifBlank { p.address }, style = MaterialTheme.typography.titleSmall, maxLines = 1)
            Text(
                listOfNotNull(
                    p.address.takeIf { p.stats.name.isNotBlank() },
                    stringResource(R.string.lists_people_last_contact, relativeDay(ctx, p.stats.lastEpochDay)),
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
        SuggestionChip(onClick = {}, label = {
            Text(
                if (p.told) stringResource(R.string.lists_people_told)
                else stringResource(when (p.tell) {
                    TellNewAddress.YES -> R.string.lists_tell_yes
                    TellNewAddress.MAYBE -> R.string.lists_tell_maybe
                    TellNewAddress.NO -> R.string.lists_tell_no
                }),
            )
        })
        Box {
            IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, stringResource(R.string.lists_row_menu)) }
            DropdownMenu(menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text(stringResource(R.string.move_action)) }, onClick = { menu = false; onMove() })
            }
        }
    }
}
