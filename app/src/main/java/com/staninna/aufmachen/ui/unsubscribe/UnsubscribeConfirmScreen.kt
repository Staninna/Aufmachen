package com.staninna.aufmachen.ui.unsubscribe

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.staninna.aufmachen.R
import com.staninna.aufmachen.ui.actions.openUrl
import com.staninna.aufmachen.ui.common.BackTopBar

@Composable
fun UnsubscribeConfirmScreen(nav: NavHostController) {
    val ctx = LocalContext.current
    val vm: UnsubscribeConfirmViewModel = viewModel { UnsubscribeConfirmViewModel(ctx.applicationContext) }
    val items by vm.items.collectAsState()
    val phase by vm.phase.collectAsState()
    val loaded by vm.loaded.collectAsState()
    val from by vm.oldAddress.collectAsState()

    val runnable = items.count { it.plan != null }
    val gdprCount = items.count { it.gdprReady && it.gdprState != ItemState.DONE }
    val failed = items.count { it.state == ItemState.FAILED || it.gdprState == ItemState.FAILED }
    val canMail = from.isNotBlank()
    val editable = phase == ConfirmPhase.REVIEW

    Scaffold(
        topBar = { BackTopBar(stringResource(R.string.lists_confirm_title), nav) },
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    when (phase) {
                        ConfirmPhase.REVIEW -> Button(
                            onClick = vm::confirm, enabled = loaded && (runnable > 0 || gdprCount > 0),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                if (gdprCount > 0) stringResource(R.string.lists_confirm_go_gdpr, runnable, gdprCount)
                                else stringResource(R.string.lists_confirm_go, runnable),
                            )
                        }
                        ConfirmPhase.RUNNING -> Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                            Text(stringResource(R.string.lists_confirm_running), Modifier.padding(start = 12.dp))
                        }
                        ConfirmPhase.WEB -> vm.currentWeb()?.let { cur ->
                            val link = cur.plan?.link.orEmpty()
                            LaunchedEffect(cur.domain) { openUrl(ctx, link) }
                            val pos = items.count { it.plan?.step == UnsubStep.WEB && it.state != ItemState.PENDING } + 1
                            val total = items.count { it.plan?.step == UnsubStep.WEB }
                            Text(stringResource(R.string.lists_web_step, pos, total, cur.name),
                                style = MaterialTheme.typography.titleSmall)
                            Text(stringResource(R.string.lists_web_hint, hostOf(link)),
                                style = MaterialTheme.typography.bodySmall)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = { openUrl(ctx, link) }) { Text(stringResource(R.string.lists_web_open_again)) }
                                OutlinedButton(onClick = { vm.webSkip(cur.domain) }) { Text(stringResource(R.string.lists_web_skip)) }
                                Button(onClick = { vm.webDone(cur.domain) }) { Text(stringResource(R.string.lists_web_done)) }
                            }
                        }
                        ConfirmPhase.DONE -> {
                            if (failed > 0) OutlinedButton(onClick = vm::confirm, modifier = Modifier.fillMaxWidth()) {
                                Text(stringResource(R.string.lists_confirm_retry, failed))
                            }
                            Button(onClick = { nav.popBackStack() }, modifier = Modifier.fillMaxWidth()) {
                                Text(stringResource(R.string.lists_confirm_close))
                            }
                        }
                    }
                }
            }
        },
    ) { pad ->
        if (!loaded) {
            Box(Modifier.padding(pad).fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
        } else if (items.isEmpty()) {
            Box(Modifier.padding(pad).fillMaxSize().padding(24.dp), Alignment.Center) {
                Text(stringResource(R.string.lists_confirm_empty))
            }
        } else LazyColumn(Modifier.padding(pad).fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 8.dp)) {
            item(key = "summary") {
                Card(Modifier.fillMaxWidth().padding(12.dp)) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        fun n(s: UnsubStep) = items.count { it.plan?.step == s }
                        Text(stringResource(R.string.lists_confirm_counts,
                            n(UnsubStep.ONE_CLICK), n(UnsubStep.EMAIL), n(UnsubStep.WEB)),
                            style = MaterialTheme.typography.titleSmall)
                        val skipped = items.count { it.plan == null }
                        if (skipped > 0) Text(stringResource(R.string.lists_confirm_cannot, skipped))
                        if (from.isBlank() && items.any { it.plan?.fellBack == true || it.plan == null }) {
                            Text(stringResource(R.string.lists_confirm_no_imap), style = MaterialTheme.typography.bodySmall)
                        }
                        if (canMail && editable) {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(stringResource(R.string.lists_gdpr_all), style = MaterialTheme.typography.bodyMedium)
                                    Text(stringResource(R.string.lists_gdpr_all_hint, from), style = MaterialTheme.typography.bodySmall)
                                }
                                Switch(checked = items.isNotEmpty() && items.all { it.gdpr }, onCheckedChange = vm::setGdprAll)
                            }
                        } else if (!canMail) {
                            Text(stringResource(R.string.lists_gdpr_no_imap), style = MaterialTheme.typography.bodySmall)
                        }
                        if (phase == ConfirmPhase.DONE) {
                            val sent = items.count { it.gdprState == ItemState.DONE }
                            if (sent > 0) Text(stringResource(R.string.lists_gdpr_summary, sent))
                            Text(stringResource(R.string.lists_confirm_summary,
                                items.count { it.state == ItemState.DONE }, failed,
                                items.count { it.state == ItemState.SKIPPED }))
                            Text(stringResource(R.string.lists_confirm_watchdog), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
            items(items, key = { it.domain }) { it ->
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Text(it.name, style = MaterialTheme.typography.titleSmall)
                    Text(describe(it, from), style = MaterialTheme.typography.bodySmall)
                    when (it.state) {
                        ItemState.PENDING -> Unit
                        ItemState.RUNNING -> Text(stringResource(R.string.lists_state_running), color = MaterialTheme.colorScheme.primary)
                        ItemState.DONE -> Text(stringResource(R.string.lists_state_done), color = MaterialTheme.colorScheme.primary)
                        ItemState.FAILED -> Text(stringResource(R.string.lists_state_failed, it.message.orEmpty()), color = MaterialTheme.colorScheme.error)
                        ItemState.SKIPPED -> Text(stringResource(R.string.lists_state_skipped, it.message.orEmpty()))
                    }
                    if (canMail) GdprRow(it, editable, vm)
                }
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun describe(item: ConfirmItem, from: String): String {
    val p = item.plan ?: return stringResource(R.string.lists_confirm_no_method)
    val base = when (p.step) {
        UnsubStep.ONE_CLICK -> stringResource(R.string.lists_will_oneclick, hostOf(p.link))
        UnsubStep.EMAIL -> stringResource(R.string.lists_will_email, from, p.mailto?.address.orEmpty(), p.mailto?.subject.orEmpty())
        UnsubStep.WEB -> stringResource(R.string.lists_will_web, hostOf(p.link))
    }
    return if (p.fellBack) base + " " + stringResource(R.string.lists_will_fallback) else base
}

@Composable
private fun GdprRow(item: ConfirmItem, editable: Boolean, vm: UnsubscribeConfirmViewModel) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(item.gdpr, { on -> vm.setGdpr(item.domain, on) }, enabled = editable)
        Text(stringResource(R.string.lists_gdpr_item), style = MaterialTheme.typography.bodyMedium)
    }
    if (!item.gdpr) return
    if (editable) {
        OutlinedTextField(
            value = item.gdprTo,
            onValueChange = { vm.setGdprTo(item.domain, it) },
            label = { Text(stringResource(R.string.lists_gdpr_to)) },
            singleLine = true,
            isError = !item.gdprReady,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            supportingText = {
                when {
                    !item.gdprReady -> Text(stringResource(R.string.lists_gdpr_bad_address))
                    item.gdprGuessed -> Text(stringResource(R.string.lists_gdpr_guessed))
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )
    } else {
        Text(stringResource(R.string.lists_gdpr_to_line, item.gdprTo.trim()), style = MaterialTheme.typography.bodySmall)
    }
    when (item.gdprState) {
        ItemState.RUNNING -> Text(stringResource(R.string.lists_state_running), color = MaterialTheme.colorScheme.primary)
        ItemState.DONE -> Text(stringResource(R.string.lists_gdpr_sent), color = MaterialTheme.colorScheme.primary)
        ItemState.FAILED -> Text(stringResource(R.string.lists_state_failed, item.gdprMessage.orEmpty()), color = MaterialTheme.colorScheme.error)
        else -> Unit
    }
}
