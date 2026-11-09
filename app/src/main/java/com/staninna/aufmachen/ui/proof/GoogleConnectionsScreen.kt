package com.staninna.aufmachen.ui.proof

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
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
import com.staninna.aufmachen.data.TimelineKind
import com.staninna.aufmachen.engine.GoogleConnections
import com.staninna.aufmachen.ui.actions.openUrl
import com.staninna.aufmachen.ui.actions.readClipboard
import com.staninna.aufmachen.ui.common.BackTopBar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val CONNECTIONS_URL = "https://myaccount.google.com/connections"

private const val SUGGESTION_QUERY =
    "SELECT fromAddr, subject FROM headers WHERE fromDomain = 'google.com' ORDER BY dateEpochDay DESC LIMIT 2000"

/** Null on a read error. */
private fun readGoogleSubjects(ctx: android.content.Context): List<Pair<String, String>>? = runCatching {
    val out = ArrayList<Pair<String, String>>()
    ctx.container.db.openHelper.readableDatabase.query(SUGGESTION_QUERY).use { c ->
        while (c.moveToNext()) out += c.getString(0).orEmpty() to c.getString(1).orEmpty()
    }
    out as List<Pair<String, String>>
}.getOrNull()

private sealed interface Paste {
    data object None : Paste
    data object Empty : Paste
    data object NothingParsed : Paste
    data class Rows(val rows: List<ReviewRow>) : Paste
}

@Composable
fun GoogleConnectionsScreen(nav: NavHostController) {
    val ctx = LocalContext.current
    val c = ctx.container
    val scope = rememberCoroutineScope()
    val services by c.repo.services.collectAsState(initial = emptyList())
    val byKey = remember(services) { services.associateBy { it.domain } }
    val candidates = remember(services) { services.map { it.toCandidate() } }

    var paste by remember { mutableStateOf<Paste>(Paste.None) }
    // Per parsed name: chosen group key (null = none) and checked flag.
    val assigned = remember { mutableStateMapOf<String, String?>() }
    val checked = remember { mutableStateMapOf<String, Boolean>() }
    var pickFor by remember { mutableStateOf<String?>(null) }
    val suggestChecked = remember { mutableStateMapOf<String, Boolean>() }
    var done by remember { mutableStateOf<Int?>(null) }

    val subjects by produceState<List<Pair<String, String>>?>(null) {
        value = withContext(Dispatchers.IO) { readGoogleSubjects(ctx) }
    }
    val alreadyKeys = remember(services) { services.filter { it.state.googleLogin }.map { it.domain }.toSet() }
    val suggestions = remember(subjects, candidates, alreadyKeys) {
        subjects?.let { suggestionKeys(it, candidates, alreadyKeys) }
    }

    Scaffold(topBar = { BackTopBar(stringResource(R.string.proof_google_title), nav) }) { pad ->
        Column(
            Modifier.padding(pad).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.proof_google_step1_title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.proof_google_step1_body))
            OutlinedButton(onClick = { openUrl(ctx, CONNECTIONS_URL) }) {
                Text(stringResource(R.string.proof_google_open))
            }
            HorizontalDivider()
            Text(stringResource(R.string.proof_google_step2_title), style = MaterialTheme.typography.titleMedium)
            Button(onClick = {
                done = null
                val text = readClipboard(ctx)?.takeIf { it.isNotBlank() }
                if (text == null) {
                    paste = Paste.Empty
                    return@Button
                }
                val names = GoogleConnections.parse(text)
                if (names.isEmpty()) {
                    paste = Paste.NothingParsed
                    return@Button
                }
                val rows = reviewRows(names, GoogleConnections.match(names, candidates))
                assigned.clear(); checked.clear()
                rows.forEach { assigned[it.name] = it.groupKey; checked[it.name] = it.checked }
                paste = Paste.Rows(rows)
            }) { Text(stringResource(R.string.proof_google_paste)) }

            when (val p = paste) {
                Paste.None -> {}
                Paste.Empty -> Text(stringResource(R.string.proof_google_clipboard_empty), color = MaterialTheme.colorScheme.error)
                Paste.NothingParsed -> Text(stringResource(R.string.proof_google_nothing_parsed), color = MaterialTheme.colorScheme.error)
                is Paste.Rows -> {
                    Text(stringResource(R.string.proof_google_found), style = MaterialTheme.typography.titleSmall)
                    val matched = p.rows.filter { assigned[it.name] != null }
                    val unmatched = p.rows.filter { assigned[it.name] == null }
                    matched.forEach { r ->
                        val key = assigned[r.name]!!
                        Row(
                            Modifier.fillMaxWidth().clickable { checked[r.name] = checked[r.name] != true },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(checked[r.name] == true, { checked[r.name] = it })
                            Column(Modifier.weight(1f)) {
                                Text(r.name)
                                Text(byKey[key]?.label() ?: key, style = MaterialTheme.typography.bodySmall)
                            }
                            TextButton(onClick = { pickFor = r.name }) { Text(stringResource(R.string.proof_google_pick_service)) }
                        }
                    }
                    if (unmatched.isNotEmpty()) {
                        Text(stringResource(R.string.proof_google_unmatched), style = MaterialTheme.typography.titleSmall)
                        unmatched.forEach { r ->
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(r.name)
                                    Text(stringResource(R.string.proof_google_no_match), style = MaterialTheme.typography.bodySmall)
                                }
                                TextButton(onClick = { pickFor = r.name }) { Text(stringResource(R.string.proof_google_pick_service)) }
                            }
                        }
                    }
                }
            }

            HorizontalDivider()
            Text(stringResource(R.string.proof_google_suggestions), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.proof_google_suggestions_body), style = MaterialTheme.typography.bodySmall)
            when {
                subjects == null && suggestions == null -> {}
                suggestions == null -> Text(stringResource(R.string.proof_google_suggestions_error), color = MaterialTheme.colorScheme.error)
                suggestions.isEmpty() -> Text(stringResource(R.string.proof_google_suggestions_empty))
                else -> suggestions.forEach { key ->
                    Row(
                        Modifier.fillMaxWidth().clickable { suggestChecked[key] = suggestChecked[key] != true },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(suggestChecked[key] == true, { suggestChecked[key] = it })
                        Text(byKey[key]?.label() ?: key)
                    }
                }
            }

            val selected = remember(paste, assigned.toMap(), checked.toMap(), suggestChecked.toMap(), suggestions) {
                val fromPaste = (paste as? Paste.Rows)?.rows.orEmpty()
                    .filter { checked[it.name] == true }.mapNotNull { assigned[it.name] }
                val fromMail = suggestions.orEmpty().filter { suggestChecked[it] == true }
                (fromPaste + fromMail).distinct()
            }
            Button(
                enabled = selected.isNotEmpty(),
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    scope.launch {
                        val note = ctx.getString(R.string.proof_google_note)
                        selected.forEach { key ->
                            c.repo.updateState(key) { it.copy(googleLogin = true) }
                            c.repo.timeline(key, TimelineKind.NOTE, note)
                        }
                        done = selected.size
                        paste = Paste.None
                        assigned.clear(); checked.clear(); suggestChecked.clear()
                    }
                },
            ) { Text(stringResource(R.string.proof_google_confirm, selected.size)) }
            done?.let { Text(stringResource(R.string.proof_google_done, it)) }
        }
    }

    pickFor?.let { name ->
        ServicePickerDialog(services, onDismiss = { pickFor = null }, onPick = {
            assigned[name] = it.domain
            checked[name] = true
            pickFor = null
        })
    }
}
