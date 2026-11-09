package com.staninna.aufmachen.ui.detail

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.staninna.aufmachen.AppContainer
import com.staninna.aufmachen.R
import com.staninna.aufmachen.container
import com.staninna.aufmachen.data.TimelineKind
import com.staninna.aufmachen.engine.Templates
import com.staninna.aufmachen.mail.MailAccess
import com.staninna.aufmachen.model.Status
import com.staninna.aufmachen.ui.actions.copyText
import com.staninna.aufmachen.ui.common.BackTopBar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class GdprForm(
    val loaded: Boolean = false,
    val found: Boolean = true,
    val serviceName: String = "",
    val oldAddress: String? = null,
    val canSend: Boolean = false,
    val recipient: String = "",
    val recipientFixed: Boolean = false,
    val dutch: Boolean = false,
    val subject: String = "",
    val body: String = "",
    val preview: Boolean = false,
    val sending: Boolean = false,
    val error: String? = null,
)

class GdprViewModel(private val c: AppContainer, private val domain: String) : ViewModel() {
    val form = MutableStateFlow(GdprForm())

    init {
        viewModelScope.launch {
            val v = c.repo.service(domain).first()
            val s = c.settings.get()
            if (v == null) {
                form.value = GdprForm(loaded = true, found = false)
                return@launch
            }
            val name = v.displayNames.firstOrNull() ?: domain
            val old = s.oldAddress
            val dutch = Templates.isDutchDomain(domain)
            val mail = Templates.gdprErasure(name, old.orEmpty(), dutch)
            val contact = v.state.privacyContact?.trim()?.takeIf { it.isNotEmpty() }
            form.value = GdprForm(
                loaded = true, serviceName = name, oldAddress = old,
                canSend = s.imap != null && old != null,
                recipient = contact.orEmpty(), recipientFixed = contact != null && looksLikeAddress(contact),
                dutch = dutch, subject = mail.subject, body = mail.body,
            )
        }
    }

    fun edit(block: (GdprForm) -> GdprForm) { form.value = block(form.value) }

    /** Switches language. Regenerates the text only when the user has not edited it. */
    fun setDutch(dutch: Boolean) {
        val f = form.value
        if (f.dutch == dutch) return
        val old = Templates.gdprErasure(f.serviceName, f.oldAddress.orEmpty(), f.dutch)
        val new = Templates.gdprErasure(f.serviceName, f.oldAddress.orEmpty(), dutch)
        form.value = f.copy(
            dutch = dutch,
            subject = if (f.subject == old.subject) new.subject else f.subject,
            body = if (f.body == old.body) new.body else f.body,
        )
    }

    fun send(ctx: Context, onDone: () -> Unit) {
        val f = form.value
        val to = f.recipient.trim()
        form.value = f.copy(sending = true, error = null)
        viewModelScope.launch {
            val res = withContext(Dispatchers.IO) {
                runCatching { MailAccess.sendFromOldAccount(ctx.applicationContext, to, f.subject, f.body).getOrThrow() }
            }
            if (res.isSuccess) {
                c.repo.setStatus(domain, Status.DELETION_REQUESTED)
                c.repo.timeline(domain, TimelineKind.SENT, ctx.getString(R.string.detail_gdpr_sent_note, to))
                form.value = form.value.copy(sending = false)
                onDone()
            } else {
                val msg = res.exceptionOrNull()?.message?.takeIf { it.isNotBlank() } ?: ctx.getString(R.string.detail_gdpr_unknown_error)
                form.value = form.value.copy(sending = false, error = msg)
            }
        }
    }
}

@Composable
fun GdprScreen(nav: NavHostController, domain: String) {
    val ctx = LocalContext.current
    val vm: GdprViewModel = viewModel(key = "gdpr:$domain") { GdprViewModel(ctx.container, domain) }
    val f by vm.form.collectAsState()

    Scaffold(topBar = { BackTopBar(stringResource(R.string.detail_gdpr_title), nav) }) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            when {
                !f.loaded -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                !f.found -> Text(stringResource(R.string.detail_not_found, domain), Modifier.align(Alignment.Center).padding(24.dp))
                else -> Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (f.preview) Preview(f, vm, ctx) { nav.popBackStack() } else Editor(f, vm, ctx)
                }
            }
        }
    }
}

@Composable
private fun Editor(f: GdprForm, vm: GdprViewModel, ctx: Context) {
    val recipientOk = looksLikeAddress(f.recipient)
    var touched by remember { mutableStateOf(false) }
    Text(stringResource(R.string.detail_gdpr_intro, f.serviceName), style = MaterialTheme.typography.bodyMedium)
    if (!f.canSend) {
        Card(Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.detail_gdpr_no_account), Modifier.padding(12.dp), style = MaterialTheme.typography.bodyMedium)
        }
    }
    OutlinedTextField(
        f.recipient, { touched = true; vm.edit { s -> s.copy(recipient = it) } }, Modifier.fillMaxWidth(),
        label = { Text(stringResource(R.string.detail_gdpr_to)) }, singleLine = true,
        readOnly = f.recipientFixed,
        isError = touched && !recipientOk,
        supportingText = {
            Text(stringResource(
                if (touched && !recipientOk) R.string.detail_gdpr_to_invalid
                else if (f.recipientFixed) R.string.detail_gdpr_to_known else R.string.detail_gdpr_to_needed,
            ))
        },
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.detail_gdpr_language))
        FilterChip(!f.dutch, { vm.setDutch(false) }, { Text(stringResource(R.string.detail_gdpr_en)) })
        FilterChip(f.dutch, { vm.setDutch(true) }, { Text(stringResource(R.string.detail_gdpr_nl)) })
    }
    OutlinedTextField(
        f.subject, { vm.edit { s -> s.copy(subject = it) } }, Modifier.fillMaxWidth(),
        label = { Text(stringResource(R.string.detail_gdpr_subject)) },
    )
    OutlinedTextField(
        f.body, { vm.edit { s -> s.copy(body = it) } }, Modifier.fillMaxWidth(),
        label = { Text(stringResource(R.string.detail_gdpr_body)) }, minLines = 8,
    )
    val full = "${f.subject}\n\n${f.body}"
    if (f.canSend) {
        Button(
            onClick = { vm.edit { s -> s.copy(preview = true, error = null) } },
            enabled = recipientOk && f.subject.isNotBlank() && f.body.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(R.string.detail_gdpr_preview)) }
    }
    OutlinedButton(onClick = { copyText(ctx, "Erasure request", full) }, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.detail_gdpr_copy))
    }
}

@Composable
private fun Preview(f: GdprForm, vm: GdprViewModel, ctx: Context, onDone: () -> Unit) {
    val from = f.oldAddress.orEmpty()
    Text(stringResource(R.string.detail_gdpr_preview_title), style = MaterialTheme.typography.titleMedium)
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.detail_gdpr_from_line, from))
            Text(stringResource(R.string.detail_gdpr_to_line, f.recipient.trim()))
            Text(stringResource(R.string.detail_gdpr_subject_line, f.subject))
            Text(f.body, style = MaterialTheme.typography.bodyMedium)
        }
    }
    Text(stringResource(R.string.detail_gdpr_confirm_note, from, f.recipient.trim()), style = MaterialTheme.typography.bodySmall)
    f.error?.let {
        Text(stringResource(R.string.detail_gdpr_failed, it), color = MaterialTheme.colorScheme.error)
    }
    if (f.sending) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            CircularProgressIndicator(Modifier.size(24.dp))
            Text(stringResource(R.string.detail_gdpr_sending))
        }
    }
    Button(onClick = { vm.send(ctx, onDone) }, enabled = !f.sending, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.detail_gdpr_send, from))
    }
    OutlinedButton(
        onClick = { vm.edit { s -> s.copy(preview = false) } }, enabled = !f.sending, modifier = Modifier.fillMaxWidth(),
    ) { Text(stringResource(R.string.detail_gdpr_back)) }
}
