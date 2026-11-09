package com.staninna.aufmachen.ui.dormant

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.staninna.aufmachen.model.Status
import com.staninna.aufmachen.ui.actions.ClaudeGoal
import com.staninna.aufmachen.ui.actions.askClaude
import com.staninna.aufmachen.ui.actions.relativeDay
import com.staninna.aufmachen.ui.common.StatusChip
import com.staninna.aufmachen.ui.common.TabTopBar
import com.staninna.aufmachen.ui.common.TierStripe
import com.staninna.aufmachen.ui.nav.Routes

private enum class Confirm { DELETED, IGNORE }

@Composable
fun DormantScreen(nav: NavHostController) {
    val ctx = LocalContext.current
    val vm: DormantViewModel = viewModel { DormantViewModel(ctx.container.repo) }
    val rows by vm.rows.collectAsState()
    val showHandled by vm.showHandled.collectAsState()
    val selected by vm.selectedVisible.collectAsState()

    var confirm by remember { mutableStateOf<Confirm?>(null) }
    // Ask Claude walk-through: domains still to open, and the position of the one open now.
    var queue by remember { mutableStateOf<List<ServiceView>>(emptyList()) }
    var pos by remember { mutableStateOf(0) }

    Scaffold(
        topBar = { TabTopBar(stringResource(R.string.nav_dormant), nav) },
        bottomBar = {
            if (queue.isNotEmpty()) Surface(tonalElevation = 3.dp) {
                Row(
                    Modifier.fillMaxWidth().padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        stringResource(R.string.lists_dormant_walk, pos + 1, queue.size, queue[pos].name),
                        Modifier.weight(1f),
                    )
                    if (pos + 1 < queue.size) {
                        Button(onClick = { pos++; askClaude(ctx, queue[pos], ClaudeGoal.DELETE) }) {
                            Text(stringResource(R.string.lists_dormant_next))
                        }
                    } else Button(onClick = { queue = emptyList() }) { Text(stringResource(R.string.lists_dormant_finish)) }
                    TextButton(onClick = { queue = emptyList() }) { Text(stringResource(R.string.lists_dormant_stop)) }
                }
            } else if (selected.isNotEmpty()) Surface(tonalElevation = 3.dp) {
                Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.lists_selected_count, selected.size), style = MaterialTheme.typography.labelLarge)
                    Button(
                        onClick = {
                            queue = vm.selectedInOrder(); pos = 0
                            queue.firstOrNull()?.let { askClaude(ctx, it, ClaudeGoal.DELETE) }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(R.string.lists_dormant_ask)) }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { confirm = Confirm.DELETED }, Modifier.weight(1f)) {
                            Text(stringResource(R.string.lists_dormant_mark_deleted))
                        }
                        OutlinedButton(onClick = { confirm = Confirm.IGNORE }, Modifier.weight(1f)) {
                            Text(stringResource(R.string.lists_dormant_ignore))
                        }
                    }
                }
            }
        },
    ) { pad ->
        val list = rows
        Column(Modifier.padding(pad).fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = !list.isNullOrEmpty() && selected.size == list.size,
                    onCheckedChange = { vm.selectAll(it) },
                    enabled = !list.isNullOrEmpty(),
                )
                Text(stringResource(R.string.lists_select_all), Modifier.weight(1f))
                Text(stringResource(R.string.lists_dormant_show_handled), style = MaterialTheme.typography.bodySmall)
                Switch(showHandled, { vm.showHandled.value = it }, Modifier.padding(start = 8.dp))
            }
            HorizontalDivider()
            when {
                list == null -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
                list.isEmpty() -> Box(Modifier.fillMaxSize().padding(24.dp), Alignment.Center) {
                    Text(stringResource(R.string.lists_dormant_empty), style = MaterialTheme.typography.bodyLarge)
                }
                else -> LazyColumn(Modifier.fillMaxSize()) {
                    items(list, key = { it.domain }) { sv ->
                        DormantRow(
                            sv, sv.domain in selected,
                            onToggle = { vm.toggle(sv.domain) },
                            onOpen = { nav.navigate(Routes.service(sv.domain)) },
                            onGdpr = { nav.navigate(Routes.gdpr(sv.domain)) },
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }

    confirm?.let { kind ->
        val chosen = vm.selectedInOrder()
        val deleted = kind == Confirm.DELETED
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text(stringResource(if (deleted) R.string.lists_dormant_confirm_deleted_title else R.string.lists_dormant_confirm_ignore_title, chosen.size)) },
            text = {
                Column {
                    Text(stringResource(if (deleted) R.string.lists_dormant_confirm_deleted_body else R.string.lists_dormant_confirm_ignore_body))
                    Text(chosen.joinToString("\n") { it.name }, Modifier.padding(top = 8.dp))
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.setStatus(chosen.map { it.domain }, if (deleted) Status.DELETED else Status.IGNORED)
                    confirm = null
                }) { Text(stringResource(R.string.lists_confirm_button)) }
            },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text(stringResource(R.string.lists_cancel)) } },
        )
    }
}

@Composable
private fun DormantRow(sv: ServiceView, checked: Boolean, onToggle: () -> Unit, onOpen: () -> Unit, onGdpr: () -> Unit) {
    val ctx = LocalContext.current
    var menu by remember { mutableStateOf(false) }
    val years = ((sv.stats.daysSince ?: 0L) / 365).toInt()
    Row(
        Modifier.fillMaxWidth().height(IntrinsicSize.Min).clickable(onClick = onOpen),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TierStripe(sv.tier)
        Checkbox(checked, { onToggle() })
        Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
            Text(sv.name, style = MaterialTheme.typography.titleSmall)
            Text(
                listOf(sv.stats.category, stringResource(R.string.lists_dormant_row, years, relativeDay(ctx, sv.stats.lastEpochDay)))
                    .filter { it.isNotBlank() }.joinToString(", "),
                style = MaterialTheme.typography.bodySmall,
            )
        }
        StatusChip(sv, onClick = onOpen)
        Box {
            IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, stringResource(R.string.lists_row_menu)) }
            DropdownMenu(menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.lists_dormant_gdpr)) },
                    onClick = { menu = false; onGdpr() },
                )
            }
        }
    }
}
