package com.staninna.aufmachen.ui.readiness

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
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
import com.staninna.aufmachen.ui.actions.openUrl
import com.staninna.aufmachen.ui.common.BackTopBar
import com.staninna.aufmachen.ui.nav.Routes

const val TAKEOUT_URL = "https://takeout.google.com"

/** One-line text for a blocker, shared by Home and the Readiness screen. */
@Composable
fun blockerText(b: Blocker): String = when (b.kind) {
    BlockerKind.CRITICAL_SERVICE -> stringResource(R.string.home_blocker_critical, b.label)
    BlockerKind.ALERT -> stringResource(R.string.home_blocker_alert, b.label)
    BlockerKind.GOOGLE_LOGIN -> stringResource(R.string.home_blocker_login, b.label)
    BlockerKind.PERSON -> stringResource(R.string.home_blocker_person, b.label)
    BlockerKind.TAKEOUT -> stringResource(R.string.home_blocker_takeout)
}

private fun BlockerKind.titleRes(): Int = when (this) {
    BlockerKind.CRITICAL_SERVICE -> R.string.home_group_critical
    BlockerKind.ALERT -> R.string.home_group_alerts
    BlockerKind.GOOGLE_LOGIN -> R.string.home_group_login
    BlockerKind.PERSON -> R.string.home_group_people
    BlockerKind.TAKEOUT -> R.string.home_group_takeout
}

@Composable
fun ReadinessScreen(nav: NavHostController) {
    val ctx = LocalContext.current
    val vm = viewModel { ReadinessViewModel(ctx.container) }
    val r by vm.readiness.collectAsState()
    val settings by vm.settings.collectAsState()
    val logins by vm.loginServices.collectAsState()
    val expanded = remember { mutableStateListOf<BlockerKind>() }
    var confirm by remember { mutableStateOf(false) }

    Scaffold(topBar = { BackTopBar(stringResource(R.string.readiness_title), nav) }) { pad ->
        val rd = r
        if (rd == null) return@Scaffold
        LazyColumn(
            Modifier.fillMaxSize().padding(pad),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "head") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.home_readiness_percent, rd.percent), style = MaterialTheme.typography.displaySmall)
                    LinearProgressIndicator(progress = { rd.percent / 100f }, Modifier.fillMaxWidth())
                    Text(
                        stringResource(R.string.home_readiness_summary, rd.cleared, rd.total),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            BlockerKind.entries.forEach { kind ->
                val items = rd.groups.firstOrNull { it.kind == kind }?.items.orEmpty()
                val isLogin = kind == BlockerKind.GOOGLE_LOGIN
                val isTakeout = kind == BlockerKind.TAKEOUT
                // Fixed groups (login, takeout) always show so they can be ticked or unticked.
                if (items.isEmpty() && !(isLogin && logins.isNotEmpty()) && !isTakeout) return@forEach
                item(key = kind.name) {
                    val open = kind in expanded
                    val canExpand = !isTakeout && (items.isNotEmpty() || isLogin)
                    Card(Modifier.fillMaxWidth()) {
                        Column {
                            Row(
                                Modifier.fillMaxWidth().clickable(enabled = canExpand) {
                                    if (open) expanded.remove(kind) else expanded.add(kind)
                                }.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                if (items.isEmpty()) Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
                                Column(Modifier.weight(1f)) {
                                    Text(stringResource(kind.titleRes()), style = MaterialTheme.typography.titleMedium)
                                    Text(
                                        if (items.isEmpty()) stringResource(R.string.home_group_clear) else stringResource(R.string.home_group_left, items.size),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                if (canExpand) Icon(if (open) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null)
                            }
                            if (isTakeout) {
                                Row(Modifier.padding(start = 8.dp, end = 16.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(settings?.takeoutDone == true, onCheckedChange = { vm.setTakeout(it) })
                                    Text(stringResource(R.string.home_takeout_done), Modifier.weight(1f))
                                    TextButton(onClick = { openUrl(ctx, TAKEOUT_URL) }) { Text(stringResource(R.string.home_takeout_open)) }
                                }
                            }
                            if (open && isLogin) {
                                logins.forEach { s ->
                                    Row(Modifier.fillMaxWidth().padding(start = 8.dp, end = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Checkbox(s.state.loginSwitched, onCheckedChange = { vm.setLoginSwitched(s.domain, it) })
                                        Column(Modifier.weight(1f).clickable { nav.navigate(Routes.service(s.domain)) }.padding(vertical = 8.dp)) {
                                            Text(s.name)
                                            Text(stringResource(R.string.home_login_switched), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                }
                            } else if (open) {
                                items.forEach { b ->
                                    Text(
                                        blockerText(b),
                                        Modifier.fillMaxWidth().clickable {
                                            nav.navigate(
                                                when (kind) {
                                                    BlockerKind.CRITICAL_SERVICE -> Routes.service(b.key)
                                                    BlockerKind.PERSON -> Routes.PEOPLE
                                                    else -> Routes.ALERTS
                                                },
                                            )
                                        }.padding(horizontal = 16.dp, vertical = 12.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
            item(key = "close") {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Button(
                        onClick = { if (rd.ready) nav.navigate(Routes.CLOSE_GMAIL) else confirm = true },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(R.string.home_close_button)) }
                    if (!rd.ready) {
                        Text(
                            stringResource(R.string.home_close_locked),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        if (confirm) {
            AlertDialog(
                onDismissRequest = { confirm = false },
                title = { Text(stringResource(R.string.home_override_title)) },
                text = { Text(stringResource(R.string.home_override_body, rd.groups.sumOf { it.count })) },
                confirmButton = {
                    TextButton(onClick = {
                        confirm = false
                        vm.overrideGate()
                        nav.navigate(Routes.CLOSE_GMAIL)
                    }) { Text(stringResource(R.string.home_override_confirm)) }
                },
                dismissButton = { TextButton(onClick = { confirm = false }) { Text(stringResource(R.string.home_cancel)) } },
            )
        }
    }
}
