package com.staninna.aufmachen.ui.alerts

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
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
import com.staninna.aufmachen.data.AlertEntity
import com.staninna.aufmachen.data.ServiceView
import com.staninna.aufmachen.ui.actions.ClaudeGoal
import com.staninna.aufmachen.ui.actions.askClaude
import com.staninna.aufmachen.ui.common.BackTopBar
import com.staninna.aufmachen.ui.nav.Routes
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

private fun alertDate(a: AlertEntity): String {
    val day = a.dateEpochDay?.let { LocalDate.ofEpochDay(it) }
        ?: Instant.ofEpochMilli(a.createdAt).atZone(ZoneId.systemDefault()).toLocalDate()
    return day.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
}

@Composable
fun AlertsScreen(nav: NavHostController) {
    val ctx = LocalContext.current
    val vm = viewModel { AlertsViewModel(ctx.container) }
    val ui by vm.ui.collectAsState()
    var showDismissed by remember { mutableStateOf(false) }
    var dismissing by remember { mutableStateOf<AlertEntity?>(null) }

    Scaffold(topBar = { BackTopBar(stringResource(R.string.alerts_title), nav) }) { pad ->
        if (ui.loaded && ui.open.isEmpty() && ui.dismissed.isEmpty()) {
            Column(Modifier.fillMaxSize().padding(pad).padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Text(stringResource(R.string.home_alerts_empty_title), style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(R.string.home_alerts_empty_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            return@Scaffold
        }
        LazyColumn(
            Modifier.fillMaxSize().padding(pad),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (ui.open.isEmpty() && ui.loaded) item(key = "none") {
                Text(stringResource(R.string.home_alerts_none_open), style = MaterialTheme.typography.bodyMedium)
            }
            items(ui.open, key = { "o" + it.id }) { a ->
                AlertCard(a, ui.services[a.domain], false, nav, onDismiss = { dismissing = a })
            }
            if (ui.dismissed.isNotEmpty()) {
                item(key = "dhead") {
                    Row(
                        Modifier.fillMaxWidth().clickable { showDismissed = !showDismissed }.padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            stringResource(R.string.home_alerts_dismissed, ui.dismissed.size),
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.weight(1f),
                        )
                        Icon(if (showDismissed) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null)
                    }
                }
                if (showDismissed) items(ui.dismissed, key = { "d" + it.id }) { a ->
                    AlertCard(a, ui.services[a.domain], true, nav, onDismiss = {})
                }
            }
        }
    }

    dismissing?.let { a ->
        DismissDialog(
            onCancel = { dismissing = null },
            onConfirm = { reason -> vm.dismiss(a, reason); dismissing = null },
        )
    }
}

@Composable
private fun AlertCard(a: AlertEntity, service: ServiceView?, dismissed: Boolean, nav: NavHostController, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(Modifier.fillMaxWidth()) {
                Text(a.domain, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Text(alertDate(a), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(stringResource(R.string.home_alerts_sender, a.senderAddr), style = MaterialTheme.typography.bodyMedium)
            if (a.subject.isNotBlank()) Text(a.subject, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (dismissed && !a.dismissReason.isNullOrBlank()) {
                Text(stringResource(R.string.home_alerts_reason, a.dismissReason), style = MaterialTheme.typography.bodySmall)
            }
            Row {
                TextButton(onClick = { nav.navigate(Routes.service(a.domain)) }) { Text(stringResource(R.string.home_alerts_open_service)) }
                if (service != null) {
                    TextButton(onClick = { askClaude(ctx, service, ClaudeGoal.MIGRATE) }) { Text(stringResource(R.string.home_alerts_ask)) }
                }
                if (!dismissed) TextButton(onClick = onDismiss) { Text(stringResource(R.string.home_alerts_dismiss)) }
            }
        }
    }
}

@Composable
private fun DismissDialog(onCancel: () -> Unit, onConfirm: (String) -> Unit) {
    val options = listOf(
        stringResource(R.string.home_dismiss_added),
        stringResource(R.string.home_dismiss_oneoff),
        stringResource(R.string.home_dismiss_not_this),
        stringResource(R.string.home_dismiss_other),
    )
    var choice by remember { mutableStateOf(0) }
    var text by remember { mutableStateOf("") }
    val other = choice == options.lastIndex
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(R.string.home_dismiss_title)) },
        text = {
            Column {
                options.forEachIndexed { i, label ->
                    Row(Modifier.fillMaxWidth().clickable { choice = i }, verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(choice == i, onClick = { choice = i })
                        Text(label)
                    }
                }
                if (other) {
                    OutlinedTextField(
                        text, { text = it }, Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.home_dismiss_reason)) },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onConfirm(if (other && text.isNotBlank()) text.trim() else options[choice])
            }) { Text(stringResource(R.string.home_alerts_dismiss)) }
        },
        dismissButton = { TextButton(onClick = onCancel) { Text(stringResource(R.string.home_cancel)) } },
    )
}
