package com.staninna.aufmachen.ui.proof

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.staninna.aufmachen.R
import com.staninna.aufmachen.container
import com.staninna.aufmachen.data.ProofEntity
import com.staninna.aufmachen.data.ServiceView
import com.staninna.aufmachen.data.TimelineKind
import com.staninna.aufmachen.data.toJson
import com.staninna.aufmachen.engine.ProofMatch
import com.staninna.aufmachen.engine.ProofMatcher
import com.staninna.aufmachen.engine.TutaPdfFields
import com.staninna.aufmachen.engine.TutaPdfParser
import com.staninna.aufmachen.model.Status
import com.staninna.aufmachen.pdf.PdfBoxTextSource
import com.staninna.aufmachen.ui.common.BackTopBar
import com.staninna.aufmachen.ui.nav.Routes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private sealed interface ProofLoad {
    data object Loading : ProofLoad
    data class Error(@StringRes val message: Int) : ProofLoad
    /** [fields] is null when the PDF is not a Tuta export. */
    data class Ready(val fields: TutaPdfFields?, val title: String?, val links: List<String>) : ProofLoad
}

private fun loadPdf(ctx: Context, path: String): ProofLoad {
    val ex = PdfBoxTextSource(ctx).extract(path) ?: return ProofLoad.Error(R.string.proof_err_unreadable)
    if (ex.text.isBlank()) return ProofLoad.Error(R.string.proof_err_no_text)
    return ProofLoad.Ready(TutaPdfParser.parse(ex.title, ex.text, ex.links), ex.title, ex.links)
}

private fun copyToIncoming(ctx: Context, uri: Uri): String? = runCatching {
    val dir = File(ctx.filesDir, "incoming").apply { mkdirs() }
    val out = File(dir, "picked-${System.currentTimeMillis()}.pdf")
    ctx.contentResolver.openInputStream(uri)!!.use { i -> out.outputStream().use { i.copyTo(it) } }
    out.absolutePath
}.getOrNull()

private fun isIncoming(ctx: Context, path: String) =
    File(path).parentFile?.canonicalPath == File(ctx.filesDir, "incoming").canonicalPath

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProofScreen(nav: NavHostController, pdfPath: String?) {
    val ctx = LocalContext.current
    val container = ctx.container
    val scope = rememberCoroutineScope()
    var path by remember { mutableStateOf(pdfPath) }
    var pickFailed by remember { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val p = withContext(Dispatchers.IO) { copyToIncoming(ctx, uri) }
            pickFailed = p == null
            if (p != null) path = p
        }
    }

    Scaffold(topBar = { BackTopBar(stringResource(R.string.proof_title), nav) }) { pad ->
        val current = path
        if (current == null) {
            Column(
                Modifier.padding(pad).padding(16.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(stringResource(R.string.proof_how_title), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.proof_how_body))
                Button(onClick = { picker.launch(arrayOf("application/pdf")) }) {
                    Text(stringResource(R.string.proof_pick_pdf))
                }
                if (pickFailed) {
                    Text(stringResource(R.string.proof_copy_failed), color = MaterialTheme.colorScheme.error)
                }
            }
            return@Scaffold
        }

        val load by produceState<ProofLoad>(ProofLoad.Loading, current) {
            value = ProofLoad.Loading
            value = withContext(Dispatchers.IO) { loadPdf(ctx, current) }
        }
        val services by container.repo.services.collectAsState(initial = null)
        val settings by container.settings.flow.collectAsState(initial = null)

        fun discard() {
            scope.launch {
                withContext(Dispatchers.IO) { if (isIncoming(ctx, current)) File(current).delete() }
                nav.popBackStack()
            }
        }

        when (val l = load) {
            ProofLoad.Loading -> Column(
                Modifier.padding(pad).fillMaxSize(),
                verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                CircularProgressIndicator()
                Spacer(Modifier.height(12.dp))
                Text(stringResource(R.string.proof_loading))
            }
            is ProofLoad.Error -> Column(
                Modifier.padding(pad).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(stringResource(l.message), color = MaterialTheme.colorScheme.error)
                OutlinedButton(onClick = { discard() }) { Text(stringResource(R.string.proof_discard)) }
            }
            is ProofLoad.Ready -> {
                val svcs = services
                val s = settings
                if (svcs == null || s == null) return@Scaffold
                ProofReady(
                    modifier = Modifier.padding(pad),
                    load = l,
                    services = svcs,
                    isNewAddress = s::isNewAddress,
                    onDiscard = { discard() },
                    onConfirm = { svc, updateAddress ->
                        saveProof(ctx, current, l, svc, updateAddress)
                        nav.navigate(Routes.service(svc.domain)) {
                            popUpTo(Routes.PROOF) { inclusive = true }
                        }
                    },
                )
            }
        }
    }
}

private suspend fun saveProof(
    ctx: Context,
    path: String,
    load: ProofLoad.Ready,
    svc: ServiceView,
    updateAddress: Boolean,
) {
    val c = ctx.container
    val repo = c.repo
    val key = svc.domain
    val f = load.fields
    val now = System.currentTimeMillis()
    val stored = withContext(Dispatchers.IO) {
        val dir = File(ctx.filesDir, "proofs").apply { mkdirs() }
        val dest = File(dir, proofFileName(key, now))
        val src = File(path)
        if (!src.renameTo(dest)) {
            src.copyTo(dest, overwrite = true)
            src.delete()
        }
        dest.absolutePath
    }
    val to = f?.to.orEmpty()
    val name = f?.displayName.orEmpty()
    c.db.proofs().insert(
        ProofEntity(
            domain = key, receivedDate = f?.dateLine, recipient = to, displayName = name,
            replyTo = f?.replyTo, title = f?.title ?: load.title.orEmpty(), pdfPath = stored,
            linksJson = (f?.links ?: load.links).toJson(), createdAt = now,
        )
    )
    if (updateAddress && to.isNotBlank()) repo.updateState(key) { it.copy(assignedAddress = to) }
    repo.setStatus(key, Status.VERIFIED, ctx.getString(R.string.proof_status_note))
    val open = c.db.alerts().openCountFor(key)
    repo.updateState(key) { it.copy(failed = failedAfterProof(it.failed, open)) }
    val text = if (f != null) ctx.getString(R.string.proof_timeline, name, to)
    else ctx.getString(R.string.proof_timeline_manual, svc.label())
    repo.timeline(key, TimelineKind.PROOF, text)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProofReady(
    modifier: Modifier,
    load: ProofLoad.Ready,
    services: List<ServiceView>,
    isNewAddress: (String) -> Boolean,
    onDiscard: () -> Unit,
    onConfirm: suspend (ServiceView, Boolean) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val fields = load.fields
    val ranked: List<ProofMatch> = remember(fields, services) {
        if (fields == null) emptyList() else ProofMatcher.rank(fields, services.map { it.toCandidate() })
    }
    val byKey = remember(services) { services.associateBy { it.domain } }
    var chosen by remember { mutableStateOf<String?>(null) }
    var showSearch by remember { mutableStateOf(false) }
    var useAnyway by remember { mutableStateOf(false) }
    var updateAddress by remember { mutableStateOf(true) }
    var saving by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }

    LaunchedEffect(ranked) {
        val top = ranked.firstOrNull()
        if (top != null && top.score >= ProofMatcher.HIGH_CONFIDENCE) chosen = top.groupKey
    }

    // Without parsed fields there is no recipient to check.
    val addressOk = fields == null || isNewAddress(fields.to.orEmpty()) && !fields.to.isNullOrBlank() || useAnyway

    Column(
        modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (fields == null) {
            Text(stringResource(R.string.proof_not_tuta), color = MaterialTheme.colorScheme.error)
        } else {
            FieldsCard(fields)
            if (!addressOk) {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(stringResource(R.string.proof_not_new_title), style = MaterialTheme.typography.titleSmall)
                        Text(stringResource(R.string.proof_not_new_body))
                        TextButton(onClick = { useAnyway = true }) { Text(stringResource(R.string.proof_use_anyway)) }
                    }
                }
            }
        }

        if (services.isEmpty()) {
            Text(stringResource(R.string.proof_no_services))
        } else {
            Text(stringResource(R.string.proof_picker_title), style = MaterialTheme.typography.titleMedium)
            val top = ranked.filter { it.score > 0 }.take(5)
            if (top.isEmpty()) {
                Text(stringResource(R.string.proof_no_matches))
            } else {
                Text(stringResource(R.string.proof_top_matches), style = MaterialTheme.typography.labelLarge)
                top.forEach { m ->
                    val svc = byKey[m.groupKey] ?: return@forEach
                    Card(Modifier.fillMaxWidth().clickable { chosen = m.groupKey }) {
                        ListItem(
                            headlineContent = { Text(svc.label()) },
                            supportingContent = { Text(m.reasons.joinToString(", ")) },
                            trailingContent = { Text(stringResource(R.string.proof_score, (m.score * 100).toInt())) },
                        )
                    }
                }
            }
            OutlinedButton(onClick = { showSearch = true }) { Text(stringResource(R.string.proof_search_all)) }
        }
        TextButton(onClick = onDiscard) { Text(stringResource(R.string.proof_discard)) }
    }

    if (showSearch) {
        ServicePickerDialog(services, onDismiss = { showSearch = false }, onPick = {
            showSearch = false
            chosen = it.domain
        })
    }

    val svc = chosen?.let { byKey[it] }
    if (svc != null) {
        ModalBottomSheet(onDismissRequest = { chosen = null }) {
            val to = fields?.to
            val verdict = if (fields == null) AddressVerdict.MATCHES
            else addressVerdict(to, isNewAddress, svc.newAddress)
            Column(
                Modifier.padding(horizontal = 16.dp).padding(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(stringResource(R.string.proof_confirm_title, svc.label()), style = MaterialTheme.typography.titleLarge)
                Text(
                    if (fields != null) stringResource(
                        R.string.proof_confirm_body, to ?: "?",
                        fields.dateLine ?: stringResource(R.string.proof_date_unknown),
                    ) else stringResource(R.string.proof_confirm_body_manual)
                )
                val reasons = ranked.firstOrNull { it.groupKey == svc.domain }?.reasons.orEmpty()
                if (reasons.isNotEmpty()) {
                    Text(stringResource(R.string.proof_reasons), style = MaterialTheme.typography.labelLarge)
                    reasons.forEach { Text("• $it") }
                }
                if (verdict == AddressVerdict.DIFFERS && to != null) {
                    Text(
                        if (svc.newAddress != null) stringResource(R.string.proof_address_differs, to, svc.newAddress!!)
                        else stringResource(R.string.proof_address_differs_none, to)
                    )
                    Row(
                        Modifier.clickable { updateAddress = !updateAddress },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(updateAddress, { updateAddress = it })
                        Text(stringResource(R.string.proof_update_address, to))
                    }
                }
                if (!addressOk) {
                    Text(stringResource(R.string.proof_not_new_body), color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = { useAnyway = true }) { Text(stringResource(R.string.proof_use_anyway)) }
                }
                if (failed) Text(stringResource(R.string.proof_saving_failed), color = MaterialTheme.colorScheme.error)
                Button(
                    enabled = addressOk && !saving,
                    onClick = {
                        saving = true
                        failed = false
                        scope.launch {
                            try {
                                onConfirm(svc, verdict == AddressVerdict.DIFFERS && updateAddress)
                            } catch (e: Exception) {
                                failed = true
                                saving = false
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.proof_confirm)) }
                OutlinedButton(
                    onClick = { chosen = null; showSearch = ranked.isEmpty() },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.proof_pick_another)) }
            }
        }
    }
}

@Composable
private fun FieldsCard(f: TutaPdfFields) {
    var open by remember { mutableStateOf(false) }
    val none = stringResource(R.string.proof_none)
    Card(Modifier.fillMaxWidth().clickable { open = !open }) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.proof_fields_title), style = MaterialTheme.typography.titleSmall)
            if (open) {
                FieldRow(R.string.proof_field_name, f.displayName)
                FieldRow(R.string.proof_field_to, f.to ?: none)
                FieldRow(R.string.proof_field_date, f.dateLine ?: none)
                FieldRow(R.string.proof_field_reply_to, f.replyTo ?: none)
                FieldRow(R.string.proof_field_links, f.links.size.toString())
            } else {
                Text(f.to ?: none, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun FieldRow(@StringRes label: Int, value: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(label), style = MaterialTheme.typography.labelMedium, modifier = Modifier.fillMaxWidth(0.3f))
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}
