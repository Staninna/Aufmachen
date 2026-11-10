package com.staninna.aufmachen.ui.unsubscribe

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
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SuggestionChip
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
import com.staninna.aufmachen.engine.UnsubMethod
import com.staninna.aufmachen.ui.actions.gmailSearchUrl
import com.staninna.aufmachen.ui.actions.openUrl
import com.staninna.aufmachen.ui.actions.relativeDay
import com.staninna.aufmachen.ui.common.MoveToDialog
import com.staninna.aufmachen.ui.common.TabTopBar
import com.staninna.aufmachen.ui.common.TierStripe
import com.staninna.aufmachen.ui.lists.MoveRequest
import com.staninna.aufmachen.ui.lists.rememberMoveActions
import com.staninna.aufmachen.ui.lists.sharedPlacement
import com.staninna.aufmachen.ui.nav.Routes
import com.staninna.aufmachen.ui.nav.Selection

@Composable
fun UnsubscribeScreen(nav: NavHostController) {
    val ctx = LocalContext.current
    val vm: UnsubscribeViewModel = viewModel { UnsubscribeViewModel(ctx.container.repo) }
    val rows by vm.rows.collectAsState()
    val showDone by vm.showDone.collectAsState()
    val selected by vm.selectedVisible.collectAsState()
    val mover = rememberMoveActions()
    var request by remember { mutableStateOf<MoveRequest?>(null) }

    Scaffold(
        topBar = { TabTopBar(stringResource(R.string.nav_unsubscribe), nav) },
        snackbarHost = { SnackbarHost(mover.snackbar) },
        bottomBar = {
            if (selected.isNotEmpty()) Surface(tonalElevation = 3.dp) {
                Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            Selection.unsubscribe = selected.toList()
                            nav.navigate(Routes.UNSUBSCRIBE_CONFIRM)
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(R.string.lists_unsub_bar, selected.size)) }
                    OutlinedButton(
                        onClick = {
                            val now = rows.orEmpty().filter { it.sv.domain in selected }
                            request = MoveRequest(now.map { it.sv.domain }, sharedPlacement(now.map { it.sv.placement }))
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(R.string.move_action)) }
                }
            }
        },
    ) { pad ->
        val list = rows
        Column(Modifier.padding(pad).fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val selectable = list.orEmpty().filter { it.method != UnsubMethod.NONE }
                Checkbox(
                    checked = selectable.isNotEmpty() && selected.size == selectable.size,
                    onCheckedChange = { vm.selectAll(it) },
                    enabled = selectable.isNotEmpty(),
                )
                Text(stringResource(R.string.lists_select_all), Modifier.weight(1f))
                Text(stringResource(R.string.lists_show_done), style = MaterialTheme.typography.bodySmall)
                Switch(showDone, { vm.showDone.value = it }, Modifier.padding(start = 8.dp))
            }
            HorizontalDivider()
            when {
                list == null -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
                list.isEmpty() -> Box(Modifier.fillMaxSize().padding(24.dp), Alignment.Center) {
                    Text(stringResource(R.string.lists_unsub_empty), style = MaterialTheme.typography.bodyLarge)
                }
                else -> LazyColumn(Modifier.fillMaxSize()) {
                    items(list, key = { it.sv.domain }) { r ->
                        UnsubRow(
                            r, r.sv.domain in selected,
                            onToggle = { vm.toggle(r.sv.domain) },
                            onOpenGmail = { openUrl(ctx, gmailSearchUrl(r.sv.domains)) },
                            onMove = { request = MoveRequest(listOf(r.sv.domain), r.sv.placement) },
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
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun UnsubRow(r: UnsubRowUi, checked: Boolean, onToggle: () -> Unit, onOpenGmail: () -> Unit, onMove: () -> Unit) {
    val ctx = LocalContext.current
    val sv = r.sv
    val selectable = r.method != UnsubMethod.NONE
    var menu by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().height(IntrinsicSize.Min)
            .combinedClickable(enabled = true, onClick = { if (selectable) onToggle() }, onLongClick = onMove),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TierStripe(sv.tier)
        Checkbox(checked, { onToggle() }, enabled = selectable)
        Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
            Text(sv.name, style = MaterialTheme.typography.titleSmall, maxLines = 1)
            Text(
                stringResource(
                    R.string.lists_unsub_row_stats, sv.stats.last12, (sv.stats.readPct * 100).toInt(),
                    relativeDay(ctx, sv.stats.lastEpochDay),
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
        SuggestionChip(onClick = {}, label = { Text(methodLabel(r.method)) })
        Box {
            IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, stringResource(R.string.lists_row_menu)) }
            DropdownMenu(menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text(stringResource(R.string.move_action)) }, onClick = { menu = false; onMove() })
                DropdownMenuItem(text = { Text(stringResource(R.string.lists_open_gmail)) }, onClick = { menu = false; onOpenGmail() })
            }
        }
    }
}

@Composable
fun methodLabel(m: UnsubMethod): String = stringResource(
    when (m) {
        UnsubMethod.ONE_CLICK -> R.string.lists_method_oneclick
        UnsubMethod.EMAIL -> R.string.lists_method_email
        UnsubMethod.WEB -> R.string.lists_method_web
        UnsubMethod.NONE -> R.string.lists_method_none
    },
)
