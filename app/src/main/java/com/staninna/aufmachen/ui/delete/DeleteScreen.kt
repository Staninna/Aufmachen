package com.staninna.aufmachen.ui.delete

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import com.staninna.aufmachen.ui.actions.ClaudeGoal
import com.staninna.aufmachen.ui.actions.askClaude
import com.staninna.aufmachen.ui.actions.relativeDay
import com.staninna.aufmachen.ui.common.MoveToDialog
import com.staninna.aufmachen.ui.common.StatusChip
import com.staninna.aufmachen.ui.common.TabTopBar
import com.staninna.aufmachen.ui.common.TierStripe
import com.staninna.aufmachen.ui.lists.MoveRequest
import com.staninna.aufmachen.ui.lists.rememberMoveActions
import com.staninna.aufmachen.ui.lists.sharedPlacement
import com.staninna.aufmachen.ui.nav.Routes

@Composable
fun DeleteScreen(nav: NavHostController) {
    val ctx = LocalContext.current
    val vm: DeleteViewModel = viewModel { DeleteViewModel(ctx.container.repo) }
    val rows by vm.rows.collectAsState()
    val showDone by vm.showDone.collectAsState()
    val selected by vm.selectedVisible.collectAsState()
    val mover = rememberMoveActions()

    var request by remember { mutableStateOf<MoveRequest?>(null) }
    var confirm by remember { mutableStateOf(false) }
    // Ask Claude walk-through: services still to open, and the position of the one open now.
    var queue by remember { mutableStateOf<List<ServiceView>>(emptyList()) }
    var pos by remember { mutableIntStateOf(0) }

    Scaffold(
        topBar = { TabTopBar(stringResource(R.string.nav_delete), nav) },
        snackbarHost = { SnackbarHost(mover.snackbar) },
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
                        OutlinedButton(
                            onClick = { vm.selectedInOrder().singleOrNull()?.let { nav.navigate(Routes.gdpr(it.domain)) } },
                            enabled = selected.size == 1,
                            modifier = Modifier.weight(1f),
                        ) { Text(stringResource(R.string.lists_dormant_gdpr)) }
                        OutlinedButton(onClick = { confirm = true }, Modifier.weight(1f)) {
                            Text(stringResource(R.string.lists_dormant_mark_deleted))
                        }
                    }
                    OutlinedButton(
                        onClick = {
                            val now = vm.selectedInOrder()
                            request = MoveRequest(now.map { it.domain }, sharedPlacement(now.map { it.placement }))
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(R.string.move_action)) }
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
                Text(stringResource(R.string.lists_show_done), style = MaterialTheme.typography.bodySmall)
                Switch(showDone, { vm.showDone.value = it }, Modifier.padding(start = 8.dp))
            }
            HorizontalDivider()
            when {
                list == null -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
                list.isEmpty() -> Box(Modifier.fillMaxSize().padding(24.dp), Alignment.Center) {
                    Text(stringResource(R.string.lists_delete_empty), style = MaterialTheme.typography.bodyLarge)
                }
                else -> LazyColumn(Modifier.fillMaxSize()) {
                    items(list, key = { it.domain }) { sv ->
                        DeleteRow(
                            sv, sv.domain in selected,
                            onToggle = { vm.toggle(sv.domain) },
                            onOpen = { nav.navigate(Routes.service(sv.domain)) },
                            onMove = { request = MoveRequest(listOf(sv.domain), sv.placement) },
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
            mover.move(domains = r.ids, to = to) { vm.selectAll(false) }
        }, onDismiss = { request = null })
    }

    if (confirm) {
        val chosen = vm.selectedInOrder()
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text(stringResource(R.string.lists_dormant_confirm_deleted_title, chosen.size)) },
            text = {
                Column {
                    Text(stringResource(R.string.lists_dormant_confirm_deleted_body))
                    Text(chosen.joinToString("\n") { it.name }, Modifier.padding(top = 8.dp))
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.markDeleted(chosen.map { it.domain })
                    confirm = false
                }) { Text(stringResource(R.string.lists_confirm_button)) }
            },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text(stringResource(R.string.lists_cancel)) } },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DeleteRow(sv: ServiceView, checked: Boolean, onToggle: () -> Unit, onOpen: () -> Unit, onMove: () -> Unit) {
    val ctx = LocalContext.current
    var menu by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().height(IntrinsicSize.Min).combinedClickable(onClick = onOpen, onLongClick = onMove),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TierStripe(sv.tier)
        Checkbox(checked, { onToggle() })
        Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
            Text(sv.name, style = MaterialTheme.typography.titleSmall, maxLines = 1)
            Text(
                listOf(sv.stats.category, stringResource(R.string.lists_last_seen, relativeDay(ctx, sv.stats.lastEpochDay)))
                    .filter { it.isNotBlank() }.joinToString(", "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
        StatusChip(sv, onClick = onOpen)
        Box {
            IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, stringResource(R.string.lists_row_menu)) }
            DropdownMenu(menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text(stringResource(R.string.move_action)) }, onClick = { menu = false; onMove() })
            }
        }
    }
}
