package com.staninna.aufmachen.ui.onboarding

import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.staninna.aufmachen.R
import com.staninna.aufmachen.data.ImapConfig
import com.staninna.aufmachen.data.ImapProvider
import com.staninna.aufmachen.data.LockMode
import com.staninna.aufmachen.model.AddressBucket
import com.staninna.aufmachen.model.Categories
import com.staninna.aufmachen.model.Tier
import com.staninna.aufmachen.ui.lock.BiometricSupport

// ---------------------------------------------------------------- validation

private val DOMAIN_RE = Regex("^[a-z0-9]([a-z0-9-]*[a-z0-9])?(\\.[a-z0-9]([a-z0-9-]*[a-z0-9])?)*\\.[a-z]{2,}$")
private val LOCAL_RE = Regex("^[a-z0-9._+-]+$")
private val ADDR_RE = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")

fun isValidDomain(d: String) = DOMAIN_RE.matches(d.trim().lowercase())
fun isValidAddress(a: String) = ADDR_RE.matches(a.trim())

/** String resource id of the first problem with the buckets, or null when they are fine. */
@StringRes
fun bucketsError(buckets: List<AddressBucket>): Int? = when {
    buckets.isEmpty() -> R.string.setup_buckets_error_empty
    buckets.any { !LOCAL_RE.matches(it.localPart.lowercase()) } -> R.string.setup_buckets_error_local
    buckets.map { it.localPart.lowercase() }.toSet().size != buckets.size -> R.string.setup_buckets_error_dup
    buckets.count { it.isMain } != 1 -> R.string.setup_buckets_error_main
    else -> null
}

@StringRes
fun pinError(pin: String, again: String): Int? = when {
    pin.length !in 4..8 || !pin.all { it.isDigit() } -> R.string.setup_pin_error_length
    pin != again -> R.string.setup_pin_error_match
    else -> null
}

fun imapComplete(c: ImapConfig, password: String) =
    c.user.isNotBlank() && c.host.isNotBlank() && password.isNotBlank()

// ------------------------------------------------------------------- layout

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.titleMedium, modifier = modifier)
}

@Composable
fun SetupCard(content: @Composable () -> Unit) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) { content() } }
}

// ------------------------------------------------------------------ buckets

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BucketsEditor(
    domain: String,
    buckets: List<AddressBucket>,
    onChange: (List<AddressBucket>) -> Unit,
) {
    val newName = stringResource(R.string.setup_bucket_new_name)
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        buckets.forEachIndexed { i, b ->
            fun set(nb: AddressBucket) = onChange(buckets.toMutableList().also { it[i] = nb })
            SetupCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        b.name, { set(b.copy(name = it)) }, singleLine = true,
                        label = { Text(stringResource(R.string.setup_bucket_name)) },
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = { onChange(buckets.filterIndexed { j, _ -> j != i }) }) {
                        Icon(Icons.Default.Delete, stringResource(R.string.setup_bucket_remove))
                    }
                }
                OutlinedTextField(
                    b.localPart, { set(b.copy(localPart = it.trim().lowercase())) }, singleLine = true,
                    label = { Text(stringResource(R.string.setup_bucket_local)) },
                    suffix = { Text("@" + domain.ifBlank { stringResource(R.string.setup_domain_hint) }) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(b.isMain, { on ->
                        onChange(buckets.mapIndexed { j, x -> if (j == i) x.copy(isMain = on) else if (on) x.copy(isMain = false) else x })
                    })
                    Text(stringResource(R.string.setup_bucket_main), Modifier.padding(start = 12.dp))
                }
                Text(stringResource(R.string.setup_bucket_tiers), style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Tier.entries.forEach { t ->
                        FilterChip(
                            selected = t.level in b.tiers,
                            onClick = {
                                set(b.copy(tiers = if (t.level in b.tiers) b.tiers - t.level else (b.tiers + t.level).sorted()))
                            },
                            label = { Text(stringResource(tierLabel(t))) },
                        )
                    }
                }
                Text(stringResource(R.string.setup_bucket_categories), style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Categories.ALL.forEach { c ->
                        FilterChip(
                            selected = c in b.categories,
                            onClick = { set(b.copy(categories = if (c in b.categories) b.categories - c else b.categories + c)) },
                            label = { Text(c) },
                        )
                    }
                }
            }
        }
        OutlinedButton(onClick = { onChange(buckets + AddressBucket(newName, "new${buckets.size + 1}")) }) {
            Text(stringResource(R.string.setup_bucket_add))
        }
    }
}

@StringRes
fun tierLabel(t: Tier): Int = when (t) {
    Tier.CRITICAL -> R.string.setup_tier_1
    Tier.IMPORTANT -> R.string.setup_tier_2
    Tier.ACCOUNT -> R.string.setup_tier_3
    Tier.LOW -> R.string.setup_tier_4
    Tier.TRASH -> R.string.setup_tier_5
}

@Composable
fun OwnAddressesEditor(addresses: List<String>, onChange: (List<String>) -> Unit) {
    var draft by remember { mutableStateOf("") }
    val invalid = draft.isNotBlank() && !isValidAddress(draft)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        addresses.forEach { a ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(a, Modifier.weight(1f))
                IconButton(onClick = { onChange(addresses - a) }) {
                    Icon(Icons.Default.Close, stringResource(R.string.setup_remove))
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                draft, { draft = it }, singleLine = true, isError = invalid,
                placeholder = { Text(stringResource(R.string.setup_own_hint)) },
                supportingText = { if (invalid) Text(stringResource(R.string.setup_own_invalid)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                modifier = Modifier.weight(1f),
            )
            TextButton(
                onClick = {
                    val a = draft.trim().lowercase()
                    if (a !in addresses) onChange(addresses + a)
                    draft = ""
                },
                enabled = draft.isNotBlank() && !invalid,
            ) { Text(stringResource(R.string.setup_add)) }
        }
    }
}

// --------------------------------------------------------------------- IMAP

sealed interface TestState {
    data object Idle : TestState
    data object Running : TestState
    data object Ok : TestState
    data class Failed(val message: String) : TestState
}

private val AUTH_WORDS = Regex("auth|credential|password|login|invalid|denied|username", RegexOption.IGNORE_CASE)

@Composable
fun ImapSection(
    config: ImapConfig,
    password: String,
    keepSavedPassword: Boolean,
    test: TestState,
    onConfig: (ImapConfig) -> Unit,
    onPassword: (String) -> Unit,
    onTest: () -> Unit,
) {
    val uri = LocalUriHandler.current
    @StringRes fun providerLabel(p: ImapProvider) = when (p) {
        ImapProvider.GMAIL -> R.string.setup_provider_gmail
        ImapProvider.OUTLOOK -> R.string.setup_provider_outlook
        ImapProvider.YAHOO -> R.string.setup_provider_yahoo
        ImapProvider.CUSTOM -> R.string.setup_provider_custom
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        @OptIn(ExperimentalLayoutApi::class)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ImapProvider.entries.forEach { p ->
                FilterChip(
                    selected = config.provider == p,
                    onClick = {
                        onConfig(
                            if (p == ImapProvider.CUSTOM) config.copy(provider = p)
                            else config.copy(provider = p, host = p.host, port = p.port, smtpHost = p.smtpHost, smtpPort = p.smtpPort),
                        )
                    },
                    label = { Text(stringResource(providerLabel(p))) },
                )
            }
        }
        OutlinedTextField(
            config.user, { onConfig(config.copy(user = it.trim())) }, singleLine = true,
            label = { Text(stringResource(R.string.setup_imap_address)) },
            placeholder = { Text(stringResource(R.string.setup_imap_address_hint)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            password, onPassword, singleLine = true,
            label = {
                Text(stringResource(if (keepSavedPassword) R.string.setup_imap_password_keep else R.string.setup_imap_password))
            },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth(),
        )
        if (config.provider == ImapProvider.CUSTOM) {
            OutlinedTextField(
                config.host, { onConfig(config.copy(host = it.trim())) }, singleLine = true,
                label = { Text(stringResource(R.string.setup_imap_host)) }, modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                config.port.toString(), { onConfig(config.copy(port = it.filter(Char::isDigit).take(5).toIntOrNull() ?: 0)) },
                singleLine = true, label = { Text(stringResource(R.string.setup_imap_port)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                config.smtpHost, { onConfig(config.copy(smtpHost = it.trim())) }, singleLine = true,
                label = { Text(stringResource(R.string.setup_smtp_host)) }, modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                config.smtpPort.toString(), { onConfig(config.copy(smtpPort = it.filter(Char::isDigit).take(5).toIntOrNull() ?: 0)) },
                singleLine = true, label = { Text(stringResource(R.string.setup_smtp_port)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth(),
            )
        } else {
            Text(
                stringResource(R.string.setup_imap_server_line, config.host, config.port),
                style = MaterialTheme.typography.bodySmall,
            )
        }
        if (config.provider == ImapProvider.GMAIL) {
            Text(stringResource(R.string.setup_gmail_help), style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = { uri.openUri("https://myaccount.google.com/apppasswords") }) {
                Text(stringResource(R.string.setup_gmail_link))
            }
        }
        Button(onClick = onTest, enabled = test !is TestState.Running) {
            Text(stringResource(if (test is TestState.Running) R.string.setup_testing else R.string.setup_test))
        }
        when (test) {
            TestState.Ok -> Text(stringResource(R.string.setup_test_ok), color = MaterialTheme.colorScheme.primary)
            is TestState.Failed -> Column {
                Text(stringResource(R.string.setup_test_failed, test.message), color = MaterialTheme.colorScheme.error)
                if (config.isGmail || AUTH_WORDS.containsMatchIn(test.message)) {
                    Text(stringResource(R.string.setup_test_auth_hint), style = MaterialTheme.typography.bodySmall)
                }
            }
            else -> {}
        }
    }
}

// --------------------------------------------------------------------- mbox

@Composable
fun MboxSection(name: String?, onPicked: (uri: String, name: String) -> Unit, onFailed: () -> Unit) {
    val ctx = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        val ok = runCatching {
            ctx.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }.isSuccess
        if (!ok) { onFailed(); return@rememberLauncherForActivityResult }
        val display = runCatching {
            ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                if (it.moveToFirst()) it.getString(0) else null
            }
        }.getOrNull() ?: uri.lastPathSegment.orEmpty()
        onPicked(uri.toString(), display)
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.setup_mbox_help), style = MaterialTheme.typography.bodySmall)
        if (name != null) Text(stringResource(R.string.setup_mbox_picked, name))
        OutlinedButton(onClick = { launcher.launch(arrayOf("*/*")) }) {
            Text(stringResource(if (name == null) R.string.setup_mbox_pick else R.string.setup_mbox_change))
        }
    }
}

// --------------------------------------------------------------------- lock

@Composable
fun LockChoice(
    mode: LockMode,
    onMode: (LockMode) -> Unit,
    pin: String,
    pinAgain: String,
    onPin: (String) -> Unit,
    onPinAgain: (String) -> Unit,
) {
    val ctx = LocalContext.current
    val bio = remember { BiometricSupport.available(ctx) }
    @Composable fun option(m: LockMode, @StringRes label: Int, enabled: Boolean = true) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            RadioButton(mode == m, { onMode(m) }, enabled = enabled)
            Text(stringResource(label), Modifier.padding(start = 4.dp))
        }
    }
    Column {
        option(LockMode.NONE, R.string.setup_lock_none)
        option(LockMode.BIOMETRIC, R.string.setup_lock_biometric, bio)
        if (!bio) {
            Text(
                stringResource(R.string.setup_lock_biometric_missing),
                style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = 48.dp),
            )
        }
        option(LockMode.PIN, R.string.setup_lock_pin)
        if (mode == LockMode.PIN) {
            val err = if (pin.isEmpty() && pinAgain.isEmpty()) null else pinError(pin, pinAgain)
            OutlinedTextField(
                pin, { onPin(it.filter(Char::isDigit).take(8)) }, singleLine = true,
                label = { Text(stringResource(R.string.setup_pin_label)) },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
            OutlinedTextField(
                pinAgain, { onPinAgain(it.filter(Char::isDigit).take(8)) }, singleLine = true,
                label = { Text(stringResource(R.string.setup_pin_confirm)) },
                isError = err != null,
                supportingText = { if (err != null) Text(stringResource(err)) },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
        }
    }
}

/** True when the chosen lock mode can be applied. */
fun lockReady(ctx: android.content.Context, mode: LockMode, pin: String, again: String) = when (mode) {
    LockMode.NONE -> true
    LockMode.BIOMETRIC -> BiometricSupport.available(ctx)
    LockMode.PIN -> pinError(pin, again) == null
}
