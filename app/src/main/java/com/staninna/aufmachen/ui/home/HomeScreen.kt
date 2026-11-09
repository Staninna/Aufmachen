package com.staninna.aufmachen.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
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
import com.staninna.aufmachen.data.SyncStateEntity
import com.staninna.aufmachen.mail.SyncScheduler
import com.staninna.aufmachen.model.Status
import com.staninna.aufmachen.ui.actions.ClaudeGoal
import com.staninna.aufmachen.ui.actions.askClaude
import com.staninna.aufmachen.ui.actions.copyText
import com.staninna.aufmachen.ui.actions.openUrl
import com.staninna.aufmachen.ui.actions.relativeDay
import com.staninna.aufmachen.ui.common.TabTopBar
import com.staninna.aufmachen.ui.common.TierChip
import com.staninna.aufmachen.ui.common.statusLabel
import com.staninna.aufmachen.ui.nav.Routes
import com.staninna.aufmachen.ui.readiness.Readiness
import com.staninna.aufmachen.ui.readiness.blockerText
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(nav: NavHostController) {
    val ctx = LocalContext.current
    val vm = viewModel { HomeViewModel(ctx.container) }
    val ui by vm.ui.collectAsState()
    val sync by vm.sync.collectAsState()
    val alerts by vm.openAlerts.collectAsState()
    val readiness by vm.readiness.collectAsState()
    val scope = rememberCoroutineScope()
    var refreshing by remember { mutableStateOf(false) }

    Scaffold(topBar = { TabTopBar(stringResource(R.string.nav_home), nav) }) { pad ->
        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = {
                scope.launch {
                    refreshing = true
                    try {
                        SyncScheduler.syncNow(ctx)
                        delay(800)
                    } finally {
                        refreshing = false
                    }
                }
            },
            modifier = Modifier.fillMaxSize().padding(pad),
        ) {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                sync?.let { rows -> item(key = "sync") { SyncCard(rows) } }
                if (ui.loaded && ui.total == 0) {
                    item(key = "empty") {
                        Column(Modifier.fillMaxWidth().padding(vertical = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(stringResource(R.string.home_empty_title), style = MaterialTheme.typography.titleMedium)
                            Text(
                                stringResource(R.string.home_empty_body),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 8.dp),
                            )
                        }
                    }
                } else if (ui.loaded) {
                    item(key = "ring") { RingCard(ui) }
                    readiness?.let { r -> item(key = "readiness") { ReadinessCard(r) { nav.navigate(Routes.READINESS) } } }
                    item(key = "next") {
                        val next = ui.next
                        if (next != null) {
                            NextUpCard(
                                s = next,
                                queue = ui.queue,
                                onOpen = { nav.navigate(Routes.service(next.domain)) },
                                onAsk = { askClaude(ctx, next, ClaudeGoal.MIGRATE) },
                                onSettingsPage = { next.state.settingsUrl?.let { openUrl(ctx, it) } },
                                onCopy = { next.newAddress?.let { copyText(ctx, "New address", it) } },
                                onChanged = { vm.markChanged(next) },
                                onSkip = { vm.skip(next) },
                                onDelete = { nav.navigate(Routes.service(next.domain)) },
                                onIgnore = { vm.ignore(next) },
                            )
                        } else {
                            Card(Modifier.fillMaxWidth()) {
                                Text(stringResource(R.string.home_queue_done), Modifier.padding(16.dp))
                            }
                        }
                    }
                    if (alerts.isNotEmpty()) item(key = "alerts") {
                        Card(Modifier.fillMaxWidth().clickable { nav.navigate(Routes.ALERTS) }) {
                            Column(Modifier.padding(16.dp)) {
                                Text(
                                    pluralStringResource(R.plurals.home_alerts_open, alerts.size, alerts.size),
                                    style = MaterialTheme.typography.titleMedium,
                                )
                                Text(
                                    stringResource(R.string.home_alerts_hint),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SyncCard(rows: List<SyncStateEntity>) {
    val ctx = LocalContext.current
    val initialRunning = rows.isNotEmpty() && rows.any { !it.initialDone }
    val error = rows.firstNotNullOfOrNull { it.lastError }
    val lastSync = rows.mapNotNull { it.lastSyncAt }.maxOrNull()
    if (!initialRunning && error == null && lastSync == null) return
    Card(
        Modifier.fillMaxWidth(),
        colors = if (error != null) CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer) else CardDefaults.cardColors(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (initialRunning) {
                val processed = rows.sumOf { it.processed }
                val total = rows.sumOf { it.total }
                Text(stringResource(R.string.home_sync_scanning), style = MaterialTheme.typography.titleMedium)
                if (total > 0) {
                    LinearProgressIndicator(progress = { (processed.toFloat() / total).coerceIn(0f, 1f) }, Modifier.fillMaxWidth())
                    Text(stringResource(R.string.home_sync_progress, processed, total), style = MaterialTheme.typography.bodyMedium)
                } else {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                }
            }
            if (lastSync != null) {
                val day = Instant.ofEpochMilli(lastSync).atZone(ZoneId.systemDefault()).toLocalDate().toEpochDay()
                Text(
                    stringResource(R.string.home_sync_last, relativeDay(ctx, day)),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            if (error != null) {
                Text(stringResource(R.string.home_sync_error, error), style = MaterialTheme.typography.bodyMedium)
                Text(stringResource(R.string.home_sync_error_hint), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RingCard(ui: HomeUi) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            ProgressRing(ui.slices, "${ui.done}/${ui.total}")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Status.entries.forEach { st ->
                    val n = ui.statusCounts[st] ?: 0
                    if (n > 0) Text("${statusLabel(st)} $n", style = MaterialTheme.typography.bodyMedium)
                }
                if (ui.failed > 0) {
                    Text(
                        stringResource(R.string.home_failed_count, ui.failed),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}

@Composable
private fun ReadinessCard(r: Readiness, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.home_readiness_title, r.percent), style = MaterialTheme.typography.titleMedium)
            LinearProgressIndicator(progress = { r.percent / 100f }, Modifier.fillMaxWidth())
            if (r.ready) {
                Text(stringResource(R.string.home_readiness_ready), style = MaterialTheme.typography.bodyMedium)
            } else {
                r.top.forEach { Text("- " + blockerText(it), style = MaterialTheme.typography.bodyMedium) }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NextUpCard(
    s: ServiceView,
    queue: Int,
    onOpen: () -> Unit,
    onAsk: () -> Unit,
    onSettingsPage: () -> Unit,
    onCopy: () -> Unit,
    onChanged: () -> Unit,
    onSkip: () -> Unit,
    onDelete: () -> Unit,
    onIgnore: () -> Unit,
) {
    val ctx = LocalContext.current
    val why = s.why.joinToString(" · ") { "${it.key} ${if (it.points >= 0) "+" else ""}${it.points.toInt()}" }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.clickable(onClick = onOpen), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    pluralStringResource(R.plurals.home_next_up, queue, queue),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (s.state.failed) {
                    Text(
                        stringResource(R.string.status_failed),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.weight(1f)) {
                        Text(s.name, style = MaterialTheme.typography.titleLarge)
                        if (s.stats.category.isNotBlank()) {
                            Text(s.stats.category, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    TierChip(s.tier, s.tierPinned, onClick = onOpen)
                }
                Text(
                    stringResource(
                        R.string.home_next_stats,
                        s.stats.mails,
                        relativeDay(ctx, s.stats.lastEpochDay),
                        s.stats.security,
                        s.stats.billing,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (why.isNotEmpty()) {
                    Text(stringResource(R.string.home_next_why, why), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                s.newAddress?.let {
                    Text(stringResource(R.string.home_next_address, it), style = MaterialTheme.typography.bodySmall)
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onAsk) { Text(stringResource(R.string.home_action_ask)) }
                if (!s.state.settingsUrl.isNullOrBlank()) {
                    OutlinedButton(onClick = onSettingsPage) { Text(stringResource(R.string.home_action_settings_page)) }
                }
                if (s.newAddress != null) {
                    OutlinedButton(onClick = onCopy) { Text(stringResource(R.string.home_action_copy)) }
                }
                OutlinedButton(onClick = onChanged) { Text(stringResource(R.string.home_action_changed)) }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = onSkip) { Text(stringResource(R.string.home_action_skip)) }
                TextButton(onClick = onDelete) { Text(stringResource(R.string.home_action_delete)) }
                TextButton(onClick = onIgnore) { Text(stringResource(R.string.home_action_ignore)) }
            }
        }
    }
}
