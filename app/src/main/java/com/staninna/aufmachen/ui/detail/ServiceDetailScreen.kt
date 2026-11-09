package com.staninna.aufmachen.ui.detail

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.staninna.aufmachen.R
import com.staninna.aufmachen.container
import com.staninna.aufmachen.data.ServiceView
import com.staninna.aufmachen.data.ProofEntity
import com.staninna.aufmachen.engine.ClaudeAnswer
import com.staninna.aufmachen.engine.ClaudeAnswerParser
import com.staninna.aufmachen.model.Status
import com.staninna.aufmachen.model.Tier
import com.staninna.aufmachen.ui.actions.ClaudeGoal
import com.staninna.aufmachen.ui.actions.askClaude
import com.staninna.aufmachen.ui.actions.copyText
import com.staninna.aufmachen.ui.actions.gmailSearchUrl
import com.staninna.aufmachen.ui.actions.openUrl
import com.staninna.aufmachen.ui.actions.readClipboard
import com.staninna.aufmachen.ui.common.BackTopBar
import com.staninna.aufmachen.ui.common.StatusChip
import com.staninna.aufmachen.ui.common.TierChip
import com.staninna.aufmachen.ui.common.tierLabel
import com.staninna.aufmachen.ui.nav.Routes
import com.staninna.aufmachen.ui.nav.Selection
import kotlinx.coroutines.launch
import java.io.File

/** A pending confirmation: what will happen, and what to run on OK. */
private class Confirm(val title: String, val text: String, val ok: String, val run: () -> Unit)

private sealed interface Sheet {
    data object None : Sheet
    data object Tier : Sheet
    data object Address : Sheet
    data object MergePick : Sheet
    data class MergeConfirm(val other: ServiceView) : Sheet
    data class Paste(val raw: String, val parsed: ClaudeAnswer?) : Sheet
}

@Composable
fun ServiceDetailScreen(nav: NavHostController, domain: String) {
    val ctx = LocalContext.current
    val vm: DetailViewModel = viewModel(key = "detail:$domain") { DetailViewModel(ctx.container, domain) }
    val state by vm.state.collectAsState()
    val merging by vm.merging.collectAsState()
    val scope = rememberCoroutineScope()
    var sheet by remember { mutableStateOf<Sheet>(Sheet.None) }
    var confirm by remember { mutableStateOf<Confirm?>(null) }
    var menu by remember { mutableStateOf(false) }
    var unmergeTarget by remember { mutableStateOf<String?>(null) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            val path = vm.importPdf(ctx, uri)
            if (path != null) nav.navigate(Routes.proof(path))
            else Toast.makeText(ctx, R.string.detail_pdf_failed, Toast.LENGTH_LONG).show()
        }
    }

    val ready = state as? DetailState.Ready
    val s = ready?.service

    fun statusConfirm(status: Status, title: Int, text: Int, ok: Int) {
        confirm = Confirm(ctx.getString(title), ctx.getString(text, domain), ctx.getString(ok)) { vm.setStatus(status) }
    }

    val actions: List<DetailAction> = if (s == null) emptyList() else listOf(
        DetailAction(R.string.detail_act_ask_migrate) {
            confirm = Confirm(ctx.getString(R.string.detail_ask_title), ctx.getString(R.string.detail_ask_migrate_text, domain),
                ctx.getString(R.string.detail_ask_ok)) { askClaude(ctx, s, ClaudeGoal.MIGRATE) }
        },
        DetailAction(R.string.detail_act_ask_delete) {
            confirm = Confirm(ctx.getString(R.string.detail_ask_title), ctx.getString(R.string.detail_ask_delete_text, domain),
                ctx.getString(R.string.detail_ask_ok)) { askClaude(ctx, s, ClaudeGoal.DELETE) }
        },
        DetailAction(R.string.detail_act_paste) {
            val raw = readClipboard(ctx)?.trim()
            if (raw.isNullOrEmpty()) Toast.makeText(ctx, R.string.detail_clipboard_empty, Toast.LENGTH_SHORT).show()
            else sheet = Sheet.Paste(raw, ClaudeAnswerParser.parse(raw))
        },
        DetailAction(R.string.detail_act_changed) {
            statusConfirm(Status.CHANGED, R.string.detail_changed_title, R.string.detail_changed_text, R.string.detail_changed_ok)
        },
        DetailAction(R.string.detail_act_deleted) {
            statusConfirm(Status.DELETED, R.string.detail_deleted_title, R.string.detail_deleted_text, R.string.detail_deleted_ok)
        },
        DetailAction(R.string.detail_act_ignore) {
            statusConfirm(Status.IGNORED, R.string.detail_ignore_title, R.string.detail_ignore_text, R.string.detail_ignore_ok)
        },
        DetailAction(R.string.detail_act_reset) {
            statusConfirm(Status.TODO, R.string.detail_reset_title, R.string.detail_reset_text, R.string.detail_reset_ok)
        },
        DetailAction(R.string.detail_act_add_proof) { picker.launch(arrayOf("application/pdf")) },
        DetailAction(R.string.detail_act_unsubscribe) {
            Selection.unsubscribe = listOf(domain)
            nav.navigate(Routes.UNSUBSCRIBE_CONFIRM)
        },
        DetailAction(R.string.detail_act_gdpr) { nav.navigate(Routes.gdpr(domain)) },
        DetailAction(R.string.detail_act_merge) { sheet = Sheet.MergePick },
        DetailAction(R.string.detail_act_gmail) {
            confirm = Confirm(ctx.getString(R.string.detail_gmail_title), ctx.getString(R.string.detail_gmail_text, domain),
                ctx.getString(R.string.detail_gmail_ok)) { openUrl(ctx, gmailSearchUrl(s.domains)) }
        },
    )

    Scaffold(topBar = {
        BackTopBar(s?.name ?: domain, nav) {
            if (s != null) {
                IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, stringResource(R.string.detail_more)) }
                DropdownMenu(menu, onDismissRequest = { menu = false }) {
                    actions.forEach { a ->
                        DropdownMenuItem(text = { Text(stringResource(a.label)) }, onClick = { menu = false; a.onClick() })
                    }
                }
            }
        }
    }) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            when (val st = state) {
                DetailState.Loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                DetailState.NotFound -> Text(
                    stringResource(R.string.detail_not_found, domain), Modifier.align(Alignment.Center).padding(24.dp),
                )
                is DetailState.Ready -> LazyColumn(
                    Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    val v = st.service
                    item("header") { HeaderSection(st, { sheet = Sheet.Tier }, { unmergeTarget = it }) }
                    item("status") {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(R.string.detail_status), Modifier.padding(end = 8.dp))
                            StatusChip(v)
                        }
                    }
                    item("stats") { StatsSection(v) }
                    item("address") {
                        AddressSection(v, { copyText(ctx, "Address", it) }, { sheet = Sheet.Address })
                    }
                    item("login") { LoginSection(v, { vm.setGoogleLogin(it) }, { vm.setLoginSwitched(it) }) }
                    item("timeline") { TimelineSection(st.timeline, st.proofs) { openProof(ctx, it) } }
                    item("steps") { StepsSection(v, { i, c -> vm.setStepDone(i, c) }) { openUrl(ctx, it) } }
                    item("actions") { ActionsSection(actions) }
                }
            }
        }
    }

    // Dialogs and sheets
    confirm?.let { c ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text(c.title) },
            text = { Text(c.text) },
            confirmButton = { TextButton(onClick = { confirm = null; c.run() }) { Text(c.ok) } },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text(stringResource(R.string.detail_cancel)) } },
        )
    }
    unmergeTarget?.let { d ->
        AlertDialog(
            onDismissRequest = { unmergeTarget = null },
            title = { Text(stringResource(R.string.detail_unmerge_title, d)) },
            text = { Text(stringResource(R.string.detail_unmerge_text, d, domain)) },
            confirmButton = {
                TextButton(onClick = { unmergeTarget = null; vm.unmerge(d) }) { Text(stringResource(R.string.detail_unmerge)) }
            },
            dismissButton = { TextButton(onClick = { unmergeTarget = null }) { Text(stringResource(R.string.detail_cancel)) } },
        )
    }
    if (merging) {
        AlertDialog(
            onDismissRequest = {},
            confirmButton = {},
            title = { Text(stringResource(R.string.detail_merging)) },
            text = { CircularProgressIndicator() },
        )
    }
    val close = { sheet = Sheet.None }
    when (val sh = sheet) {
        Sheet.None -> {}
        Sheet.Tier -> if (ready != null) TierSheet(ready.service, vm, close)
        Sheet.Address -> if (ready != null) AddressSheet(ready, vm, close)
        Sheet.MergePick -> MergePicker(vm, { sheet = Sheet.MergeConfirm(it) }, close)
        is Sheet.MergeConfirm -> AlertDialog(
            onDismissRequest = close,
            title = { Text(stringResource(R.string.detail_merge_title, sh.other.domain, domain)) },
            text = { Text(stringResource(R.string.detail_merge_text, sh.other.domain, domain)) },
            confirmButton = {
                TextButton(onClick = { close(); vm.merge(sh.other.domain) }) { Text(stringResource(R.string.detail_merge_ok)) }
            },
            dismissButton = { TextButton(onClick = close) { Text(stringResource(R.string.detail_cancel)) } },
        )
        is Sheet.Paste -> PasteDialog(sh, vm, close)
    }
}

private fun openProof(ctx: Context, p: ProofEntity) {
    try {
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", File(p.pdfPath))
        ctx.startActivity(
            Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/pdf")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(ctx, R.string.detail_no_pdf_viewer, Toast.LENGTH_LONG).show()
    } catch (_: IllegalArgumentException) {
        Toast.makeText(ctx, R.string.detail_pdf_missing, Toast.LENGTH_LONG).show()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TierSheet(s: ServiceView, vm: DetailViewModel, close: () -> Unit) {
    ModalBottomSheet(onDismissRequest = close) {
        Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.detail_pin_tier), style = MaterialTheme.typography.titleMedium)
            Tier.entries.forEach { t ->
                Row(Modifier.fillMaxWidth().clickable { vm.pinTier(t.level); close() }.padding(vertical = 6.dp)) {
                    TierChip(t, pinned = s.tierPinned && s.tier == t, onClick = { vm.pinTier(t.level); close() })
                }
            }
            if (s.tierPinned) {
                OutlinedButton(onClick = { vm.pinTier(null); close() }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.detail_use_computed, tierLabel(Tier.ofLevel(s.stats.tier))))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddressSheet(r: DetailState.Ready, vm: DetailViewModel, close: () -> Unit) {
    val s = r.service
    var own by remember { mutableStateOf(s.state.assignedAddress ?: ownAddressSuggestion(s.domain, r.myDomain)) }
    val valid = looksLikeAddress(own)
    ModalBottomSheet(onDismissRequest = close) {
        Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.detail_pick_bucket), style = MaterialTheme.typography.titleMedium)
            r.buckets.forEach { (b, addr) ->
                if (addr != null) Row(
                    Modifier.fillMaxWidth().clickable { vm.assignAddress(addr); close() }.padding(vertical = 8.dp),
                ) {
                    Text(b.name, Modifier.weight(1f))
                    Text(addr, style = MaterialTheme.typography.bodyMedium)
                }
            }
            if (r.buckets.all { it.second == null }) Text(stringResource(R.string.detail_no_domain), style = MaterialTheme.typography.bodySmall)
            Text(stringResource(R.string.detail_own_address), style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                own, { own = it }, Modifier.fillMaxWidth(), singleLine = true, isError = !valid,
                supportingText = if (!valid) ({ Text(stringResource(R.string.detail_address_invalid)) }) else null,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { vm.assignAddress(own.trim()); close() }, enabled = valid) {
                    Text(stringResource(R.string.detail_save))
                }
                if (s.state.assignedAddress != null) {
                    TextButton(onClick = { vm.assignAddress(null); close() }) { Text(stringResource(R.string.detail_reset_bucket)) }
                }
            }
        }
    }
}

@Composable
private fun MergePicker(vm: DetailViewModel, onPick: (ServiceView) -> Unit, close: () -> Unit) {
    val all by vm.others.collectAsState()
    var q by remember { mutableStateOf("") }
    val shown = remember(all, q) {
        val n = q.trim().lowercase()
        if (n.isEmpty()) all else all.filter { v -> v.domain.contains(n) || v.displayNames.any { it.lowercase().contains(n) } }
    }
    AlertDialog(
        onDismissRequest = close,
        title = { Text(stringResource(R.string.detail_merge_pick)) },
        text = {
            Column {
                OutlinedTextField(q, { q = it }, Modifier.fillMaxWidth(), singleLine = true,
                    label = { Text(stringResource(R.string.detail_search)) })
                if (shown.isEmpty()) Text(stringResource(R.string.detail_no_matches), Modifier.padding(top = 12.dp))
                LazyColumn(Modifier.padding(top = 8.dp)) {
                    items(shown, key = { it.domain }) { v ->
                        Column(Modifier.fillMaxWidth().clickable { onPick(v) }.padding(vertical = 8.dp)) {
                            Text(v.domain, style = MaterialTheme.typography.bodyLarge)
                            v.displayNames.firstOrNull()?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = close) { Text(stringResource(R.string.detail_cancel)) } },
    )
}

@Composable
private fun PasteDialog(p: Sheet.Paste, vm: DetailViewModel, close: () -> Unit) {
    val a = p.parsed
    if (a != null) {
        AlertDialog(
            onDismissRequest = close,
            title = { Text(stringResource(R.string.detail_paste_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(R.string.detail_paste_url, a.settingsUrl ?: stringResource(R.string.detail_none)))
                    Text(stringResource(R.string.detail_paste_contact, a.privacyContact ?: stringResource(R.string.detail_none)))
                    Text(stringResource(R.string.detail_paste_steps, a.steps.size))
                    Text(stringResource(R.string.detail_paste_gotchas, a.gotchas.size))
                    Text(stringResource(R.string.detail_paste_replace), style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = { TextButton(onClick = { vm.saveAnswer(a); close() }) { Text(stringResource(R.string.detail_save)) } },
            dismissButton = { TextButton(onClick = close) { Text(stringResource(R.string.detail_cancel)) } },
        )
    } else {
        AlertDialog(
            onDismissRequest = close,
            title = { Text(stringResource(R.string.detail_paste_unparsed_title)) },
            text = { Text(stringResource(R.string.detail_paste_unparsed_text)) },
            confirmButton = {
                TextButton(onClick = { vm.saveNotes(p.raw); close() }) { Text(stringResource(R.string.detail_save_notes)) }
            },
            dismissButton = { TextButton(onClick = close) { Text(stringResource(R.string.detail_cancel)) } },
        )
    }
}
