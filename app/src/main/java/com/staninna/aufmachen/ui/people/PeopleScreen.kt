package com.staninna.aufmachen.ui.people

import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.staninna.aufmachen.R
import com.staninna.aufmachen.container
import com.staninna.aufmachen.model.TellNewAddress
import com.staninna.aufmachen.ui.actions.relativeDay
import com.staninna.aufmachen.ui.common.TabTopBar
import com.staninna.aufmachen.ui.nav.Routes
import com.staninna.aufmachen.ui.nav.Selection

@Composable
fun PeopleScreen(nav: NavHostController) {
    val ctx = LocalContext.current
    val vm: PeopleViewModel = viewModel { PeopleViewModel(ctx.container.db) }
    val rows by vm.rows.collectAsState()
    val filters by vm.filters.collectAsState()
    val selected by vm.selectedVisible.collectAsState()

    Scaffold(
        topBar = { TabTopBar(stringResource(R.string.nav_people), nav) },
        bottomBar = {
            if (selected.isNotEmpty()) Surface(tonalElevation = 3.dp) {
                Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.lists_selected_count, selected.size), style = MaterialTheme.typography.labelLarge)
                    Button(
                        onClick = {
                            Selection.people = rows.orEmpty().map { it.address }.filter { it in selected }
                            nav.navigate(Routes.COMPOSE_MOVED)
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(R.string.lists_people_compose)) }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton({ vm.markTold(selected, true) }, Modifier.weight(1f)) {
                            Text(stringResource(R.string.lists_people_mark_told))
                        }
                        OutlinedButton({ vm.markTold(selected, false) }, Modifier.weight(1f)) {
                            Text(stringResource(R.string.lists_people_mark_not_told))
                        }
                    }
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
                    checked = !list.isNullOrEmpty() && selected.size == list.size,
                    onCheckedChange = { vm.selectAll(it) },
                    enabled = !list.isNullOrEmpty(),
                )
                Text(stringResource(R.string.lists_select_all))
            }
            HorizontalDivider()
            when {
                list == null -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
                list.isEmpty() -> Box(Modifier.fillMaxSize().padding(24.dp), Alignment.Center) {
                    Text(stringResource(R.string.lists_people_empty), style = MaterialTheme.typography.bodyLarge)
                }
                else -> LazyColumn(Modifier.fillMaxSize()) {
                    items(list, key = { it.address }) { p ->
                        PersonItem(p, p.address in selected) { vm.toggle(p.address) }
                        HorizontalDivider()
                    }
                }
            }
        }
    }
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

@Composable
private fun PersonItem(p: PersonRow, checked: Boolean, onToggle: () -> Unit) {
    val ctx = LocalContext.current
    Row(Modifier.fillMaxWidth().clickable(onClick = onToggle), verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked, { onToggle() })
        Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
            Text(p.stats.name.ifBlank { p.address }, style = MaterialTheme.typography.titleSmall)
            if (p.stats.name.isNotBlank()) {
                Text(p.address, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(
                stringResource(
                    R.string.lists_people_row, p.stats.youSent, p.stats.theySent,
                    stringResource(if (p.stats.twoWay) R.string.lists_yes else R.string.lists_no),
                    relativeDay(ctx, p.stats.lastEpochDay),
                ),
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Column(Modifier.padding(end = 8.dp), horizontalAlignment = Alignment.End) {
            SuggestionChip(onClick = {}, label = {
                Text(stringResource(when (p.tell) {
                    TellNewAddress.YES -> R.string.lists_tell_yes
                    TellNewAddress.MAYBE -> R.string.lists_tell_maybe
                    TellNewAddress.NO -> R.string.lists_tell_no
                }))
            })
            if (p.told) Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Check, null, Modifier.padding(end = 4.dp))
                Text(stringResource(R.string.lists_people_told), style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}
