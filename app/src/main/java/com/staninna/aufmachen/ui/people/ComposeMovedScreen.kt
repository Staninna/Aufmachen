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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.staninna.aufmachen.R
import com.staninna.aufmachen.ui.common.BackTopBar

@Composable
fun ComposeMovedScreen(nav: NavHostController) {
    val ctx = LocalContext.current
    val vm: ComposeMovedViewModel = viewModel { ComposeMovedViewModel(ctx.applicationContext) }
    val to = vm.recipients

    Scaffold(
        topBar = { BackTopBar(stringResource(R.string.lists_moved_title), nav) },
        bottomBar = {
            if (to.isNotEmpty()) Surface(tonalElevation = 3.dp) {
                Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    when {
                        vm.finished -> {
                            Text(stringResource(R.string.lists_moved_finished))
                            Button({ nav.popBackStack() }, Modifier.fillMaxWidth()) { Text(stringResource(R.string.lists_confirm_close)) }
                        }
                        vm.bccMode -> {
                            Button(
                                onClick = { vm.opened = true; openMailDraft(ctx, emptyList(), to, vm.subject, vm.body) },
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text(stringResource(R.string.lists_moved_open_bcc, to.size)) }
                            if (vm.opened) OutlinedButton(vm::confirmBccSent, Modifier.fillMaxWidth()) {
                                Text(stringResource(R.string.lists_moved_i_sent_it))
                            }
                        }
                        else -> {
                            val who = to[vm.index]
                            Text(stringResource(R.string.lists_moved_person, vm.index + 1, to.size, who),
                                style = MaterialTheme.typography.titleSmall)
                            Button(
                                onClick = { vm.opened = true; openMailDraft(ctx, listOf(who), emptyList(), vm.subject, vm.body) },
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text(stringResource(R.string.lists_moved_open_one)) }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton({ vm.next(false) }, Modifier.weight(1f)) { Text(stringResource(R.string.lists_web_skip)) }
                                OutlinedButton({ vm.next(true) }, Modifier.weight(1f), enabled = vm.opened) {
                                    Text(stringResource(R.string.lists_moved_sent_next))
                                }
                            }
                        }
                    }
                }
            }
        },
    ) { pad ->
        if (to.isEmpty()) {
            Box(Modifier.padding(pad).fillMaxSize().padding(24.dp), Alignment.Center) {
                Text(stringResource(R.string.lists_moved_empty))
            }
        } else LazyColumn(Modifier.padding(pad).fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item(key = "info") {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(stringResource(R.string.lists_moved_explain))
                        if (vm.mainAddress == null) {
                            Text(stringResource(R.string.lists_moved_no_address), color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
            item(key = "lang") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.lists_moved_language))
                    FilterChip(!vm.dutch, { vm.chooseDutch(false) }, label = { Text(stringResource(R.string.lists_moved_en)) })
                    FilterChip(vm.dutch, { vm.chooseDutch(true) }, label = { Text(stringResource(R.string.lists_moved_nl)) })
                }
            }
            item(key = "subject") {
                OutlinedTextField(vm.subject, { vm.subject = it }, Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.lists_moved_subject)) }, singleLine = true)
            }
            item(key = "body") {
                OutlinedTextField(vm.body, { vm.body = it }, Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.lists_moved_body)) }, minLines = 6)
            }
            item(key = "mode") {
                Column {
                    ModeRow(vm.bccMode, stringResource(R.string.lists_moved_mode_bcc)) { vm.setMode(true) }
                    ModeRow(!vm.bccMode, stringResource(R.string.lists_moved_mode_each)) { vm.setMode(false) }
                }
            }
            item(key = "head") {
                Text(stringResource(R.string.lists_moved_recipients, to.size), style = MaterialTheme.typography.titleSmall)
            }
            itemsIndexed(to, key = { _, a -> a }) { _, a -> Text(a, style = MaterialTheme.typography.bodyMedium) }
        }
    }
}

@Composable
private fun ModeRow(selected: Boolean, label: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick), verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected, onClick)
        Text(label)
    }
}
