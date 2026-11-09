package com.staninna.aufmachen.ui.migrate

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import com.staninna.aufmachen.model.Status
import com.staninna.aufmachen.model.Tier
import com.staninna.aufmachen.ui.actions.relativeDay
import com.staninna.aufmachen.ui.common.StatusChip
import com.staninna.aufmachen.ui.common.TabTopBar
import com.staninna.aufmachen.ui.common.TierStripe
import com.staninna.aufmachen.ui.common.statusLabel
import com.staninna.aufmachen.ui.common.tierLabel
import com.staninna.aufmachen.ui.nav.Routes

@Composable
fun MigrateScreen(nav: NavHostController) {
    val ctx = LocalContext.current
    val vm = viewModel { MigrateViewModel(ctx.container) }
    val ui by vm.ui.collectAsState()
    val f by vm.filter.collectAsState()

    Scaffold(topBar = { TabTopBar(stringResource(R.string.nav_migrate), nav) }) { pad ->
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
            FilterRow(f, ui.categories, vm)
            if (ui.loaded && ui.totalWithAccount > 0) {
                Text(
                    stringResource(R.string.home_migrate_count, ui.rows.size, ui.totalWithAccount),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
            when {
                !ui.loaded -> Unit
                ui.totalWithAccount == 0 -> Empty(R.string.home_migrate_empty_title, R.string.home_migrate_empty_body)
                ui.rows.isEmpty() -> Empty(R.string.home_migrate_nomatch_title, R.string.home_migrate_nomatch_body)
                else -> LazyColumn(Modifier.fillMaxSize()) {
                    items(ui.rows, key = { it.domain }) { s ->
                        ServiceRow(s) { nav.navigate(Routes.service(s.domain)) }
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

@Composable
private fun Empty(title: Int, body: Int) {
    Column(Modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text(stringResource(title), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(body), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
    }
}

@Composable
private fun FilterRow(f: MigrateFilter, categories: List<String>, vm: MigrateViewModel) {
    Row(
        Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Tier.entries.forEach { t ->
            FilterChip(
                selected = t in f.tiers,
                onClick = { vm.update { it.copy(tiers = it.tiers.toggle(t)) } },
                label = { Text(tierLabel(t)) },
            )
        }
        var menu by remember { mutableStateOf(false) }
        Box {
            FilterChip(
                selected = f.category != null,
                onClick = { menu = true },
                label = { Text(f.category ?: stringResource(R.string.home_migrate_category)) },
                trailingIcon = { Icon(Icons.Default.ArrowDropDown, null) },
            )
            DropdownMenu(menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text(stringResource(R.string.home_migrate_all_categories)) },
                    onClick = { menu = false; vm.update { it.copy(category = null) } })
                categories.forEach { c ->
                    DropdownMenuItem(text = { Text(c) }, onClick = { menu = false; vm.update { it.copy(category = c) } })
                }
            }
        }
        Status.entries.forEach { st ->
            FilterChip(
                selected = st in f.statuses,
                onClick = { vm.update { it.copy(statuses = it.statuses.toggle(st)) } },
                label = { Text(statusLabel(st)) },
            )
        }
        FilterChip(
            selected = f.failed,
            onClick = { vm.update { it.copy(failed = !it.failed) } },
            label = { Text(stringResource(R.string.home_migrate_failed)) },
        )
        FilterChip(
            selected = f.googleLogin,
            onClick = { vm.update { it.copy(googleLogin = !it.googleLogin) } },
            label = { Text(stringResource(R.string.home_migrate_google)) },
        )
        FilterChip(
            selected = f.hasSteps,
            onClick = { vm.update { it.copy(hasSteps = !it.hasSteps) } },
            label = { Text(stringResource(R.string.home_migrate_steps)) },
        )
    }
}

private fun <T> Set<T>.toggle(x: T): Set<T> = if (x in this) this - x else this + x

@Composable
private fun ServiceRow(s: ServiceView, onClick: () -> Unit) {
    val ctx = LocalContext.current
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).clickable(onClick = onClick)) {
        TierStripe(s.tier)
        Column(Modifier.weight(1f).padding(horizontal = 12.dp, vertical = 8.dp)) {
            Text(s.name, style = MaterialTheme.typography.titleMedium, maxLines = 1)
            Text(
                listOf(s.stats.category, stringResource(R.string.home_migrate_score, s.stats.score)).filter { it.isNotBlank() }.joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
            Text(
                stringResource(R.string.home_migrate_row_stats, s.stats.mails, relativeDay(ctx, s.stats.lastEpochDay)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
        Box(Modifier.padding(end = 8.dp).align(Alignment.CenterVertically)) { StatusChip(s, onClick) }
    }
}
