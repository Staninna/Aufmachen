package com.staninna.aufmachen.ui.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.os.LocaleListCompat
import androidx.navigation.NavHostController
import com.staninna.aufmachen.MainActivity
import com.staninna.aufmachen.R
import com.staninna.aufmachen.backup.Backup
import com.staninna.aufmachen.backup.BackupException
import com.staninna.aufmachen.backup.NotABackupException
import com.staninna.aufmachen.backup.ServiceExport
import com.staninna.aufmachen.backup.WrongPassphraseException
import com.staninna.aufmachen.container
import com.staninna.aufmachen.data.AppDatabase
import com.staninna.aufmachen.data.AppSettings
import com.staninna.aufmachen.data.ImapConfig
import com.staninna.aufmachen.data.LockMode
import com.staninna.aufmachen.data.ThemeMode
import com.staninna.aufmachen.mail.MailAccess
import com.staninna.aufmachen.mail.SyncScheduler
import com.staninna.aufmachen.ui.common.rememberSyncNow
import com.staninna.aufmachen.ui.common.BackTopBar
import com.staninna.aufmachen.ui.onboarding.BucketsEditor
import com.staninna.aufmachen.ui.onboarding.CatchAllTip
import com.staninna.aufmachen.ui.onboarding.DomainField
import com.staninna.aufmachen.ui.onboarding.ImapSection
import com.staninna.aufmachen.ui.onboarding.LockChoice
import com.staninna.aufmachen.ui.onboarding.MboxSection
import com.staninna.aufmachen.ui.onboarding.OwnAddressesEditor
import com.staninna.aufmachen.ui.onboarding.SectionTitle
import com.staninna.aufmachen.ui.onboarding.SetupCard
import com.staninna.aufmachen.ui.onboarding.TestState
import com.staninna.aufmachen.ui.onboarding.bucketsError
import com.staninna.aufmachen.ui.onboarding.imapComplete
import com.staninna.aufmachen.ui.onboarding.isValidAddress
import com.staninna.aufmachen.ui.onboarding.isValidDomain
import com.staninna.aufmachen.ui.onboarding.lockReady
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.DateFormat
import java.time.LocalDate
import java.util.Date

private val INTERVALS = listOf(1, 3, 6, 12, 24)
private const val MIME_XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"

private enum class PassMode { EXPORT, IMPORT }

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(nav: NavHostController) {
    val ctx = LocalContext.current
    val c = ctx.container
    val scope = rememberCoroutineScope()
    val snack = remember { SnackbarHostState() }
    val settings: AppSettings? by c.settings.flow.collectAsState(initial = null)
    val s = settings
    val syncStates by remember { c.db.sync().all() }.collectAsState(initial = emptyList())

    fun say(msg: String) { scope.launch { snack.showSnackbar(msg) } }

    Scaffold(
        topBar = { BackTopBar(stringResource(R.string.settings_title), nav) },
        snackbarHost = { SnackbarHost(snack) },
    ) { pad ->
        if (s == null) return@Scaffold
        Column(
            Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            MailboxSection(s, syncStates, ::say)
            AddressesSection(s, ::say)
            SyncSection(s)
            LockSection(s, ::say)
            AppearanceSection(s)
            BackupSection(::say)
            ExportSection(::say)
            WipeSection()
        }
    }
}

// ------------------------------------------------------------------ mailbox

@Composable
private fun MailboxSection(
    s: AppSettings,
    syncStates: List<com.staninna.aufmachen.data.SyncStateEntity>,
    say: (String) -> Unit,
) {
    val ctx = LocalContext.current
    val c = ctx.container
    val scope = rememberCoroutineScope()
    var imap by remember(s.imap) { mutableStateOf(s.imap ?: ImapConfig()) }
    var password by remember { mutableStateOf("") }
    var test by remember { mutableStateOf<TestState>(TestState.Idle) }
    var confirmRescan by remember { mutableStateOf(false) }
    val hasSaved = s.imap != null
    val needFields = stringResource(R.string.setup_test_need_fields)
    val needPassword = stringResource(R.string.setup_settings_need_password)
    val saved = stringResource(R.string.setup_saved)
    val syncNow = rememberSyncNow()
    val rescanStarted = stringResource(R.string.setup_settings_rescan_started)
    val mboxFailed = stringResource(R.string.setup_mbox_failed)

    SetupCard {
        SectionTitle(stringResource(R.string.setup_settings_mailbox))
        ImapSection(
            config = imap, password = password, keepSavedPassword = hasSaved, test = test,
            onConfig = { imap = it; test = TestState.Idle },
            onPassword = { password = it; test = TestState.Idle },
            onTest = {
                scope.launch {
                    val pw = password.ifBlank { withContext(Dispatchers.IO) { c.secrets.imapPassword.orEmpty() } }
                    if (imap.user.isBlank() || imap.host.isBlank() || pw.isBlank()) {
                        test = TestState.Failed(needFields); return@launch
                    }
                    test = TestState.Running
                    test = runCatching { MailAccess.testLogin(imap, pw.replace(" ", "")) }
                        .map { r -> r.fold({ TestState.Ok }, { TestState.Failed(it.message.orEmpty()) }) }
                        .getOrElse { TestState.Failed(it.message.orEmpty()) }
                }
            },
        )
        Button(onClick = {
            scope.launch {
                if (!hasSaved && !imapComplete(imap, password)) { say(needPassword); return@launch }
                withContext(Dispatchers.IO) {
                    if (password.isNotBlank()) c.secrets.imapPassword = password
                    c.settings.update { it.copy(imap = imap) }
                }
                password = ""
                if (!hasSaved) SyncScheduler.startInitialScan(ctx)
                SyncScheduler.schedulePeriodic(ctx)
                say(saved)
            }
        }) { Text(stringResource(R.string.setup_settings_save_connection)) }

        SectionTitle(stringResource(R.string.setup_mbox_title))
        MboxSection(
            s.mboxName,
            onPicked = { uri, name ->
                scope.launch {
                    c.settings.update { it.copy(mboxUri = uri, mboxName = name) }
                    SyncScheduler.importMbox(ctx)
                    say(saved)
                }
            },
            onFailed = { say(mboxFailed) },
        )

        val unknown = stringResource(R.string.setup_unknown_error)
        syncStates.forEach { st ->
            val label = if (st.source == "imap") R.string.setup_settings_sync_status_imap else R.string.setup_settings_sync_status_mbox
            Column {
                Text(stringResource(label), style = MaterialTheme.typography.labelLarge)
                val last = st.lastSyncAt?.let { DateFormat.getDateTimeInstance().format(Date(it)) }
                    ?: stringResource(R.string.setup_settings_never_synced)
                Text(stringResource(R.string.setup_settings_last_sync, last), style = MaterialTheme.typography.bodySmall)
                st.lastError?.let {
                    Text(
                        stringResource(R.string.setup_settings_last_error, it.ifBlank { unknown }),
                        color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = syncNow,
                enabled = hasSaved,
            ) { Text(stringResource(R.string.setup_settings_sync_now)) }
            OutlinedButton(onClick = { confirmRescan = true }) {
                Text(stringResource(R.string.setup_settings_rescan))
            }
        }
    }
    if (confirmRescan) {
        AlertDialog(
            onDismissRequest = { confirmRescan = false },
            title = { Text(stringResource(R.string.setup_settings_rescan_title)) },
            text = { Text(stringResource(R.string.setup_settings_rescan_body)) },
            confirmButton = {
                TextButton(onClick = { confirmRescan = false; SyncScheduler.rescanAll(ctx); say(rescanStarted) }) {
                    Text(stringResource(R.string.setup_settings_rescan_confirm))
                }
            },
            dismissButton = { TextButton(onClick = { confirmRescan = false }) { Text(stringResource(R.string.setup_cancel)) } },
        )
    }
}

// ---------------------------------------------------------------- addresses

@Composable
private fun AddressesSection(s: AppSettings, say: (String) -> Unit) {
    val ctx = LocalContext.current
    val c = ctx.container
    val scope = rememberCoroutineScope()
    var domain by remember(s.domain) { mutableStateOf(s.domain) }
    var buckets by remember(s.buckets) { mutableStateOf(s.buckets) }
    var own by remember(s.otherOwnAddresses) { mutableStateOf(s.otherOwnAddresses) }
    val err = bucketsError(buckets)
    val ok = isValidDomain(domain) && err == null && own.all(::isValidAddress)
    val saved = stringResource(R.string.setup_saved)

    SetupCard {
        SectionTitle(stringResource(R.string.setup_settings_addresses))
        DomainField(domain) { domain = it }
        BucketsEditor(domain, buckets) { buckets = it }
        err?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error) }
        CatchAllTip()
        SectionTitle(stringResource(R.string.setup_own_title))
        OwnAddressesEditor(own) { own = it }
        Button(
            enabled = ok,
            onClick = {
                scope.launch {
                    c.settings.update { it.copy(domain = domain, buckets = buckets, otherOwnAddresses = own) }
                    say(saved)
                    runCatching { c.repo.rescore() }
                }
            },
        ) { Text(stringResource(R.string.setup_save)) }
    }
}

// --------------------------------------------------------------------- sync

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SyncSection(s: AppSettings) {
    val ctx = LocalContext.current
    val c = ctx.container
    val scope = rememberCoroutineScope()
    fun change(block: (AppSettings) -> AppSettings) {
        scope.launch {
            c.settings.update(block)
            runCatching { SyncScheduler.schedulePeriodic(ctx) }
        }
    }
    SetupCard {
        SectionTitle(stringResource(R.string.setup_settings_sync))
        Text(stringResource(R.string.setup_settings_interval), style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            INTERVALS.forEach { h ->
                FilterChip(
                    selected = s.syncIntervalHours == h,
                    onClick = { change { it.copy(syncIntervalHours = h) } },
                    label = { Text(stringResource(R.string.setup_settings_hours, h)) },
                )
            }
        }
        ToggleRow(R.string.setup_settings_wifi, s.wifiOnly) { v -> change { it.copy(wifiOnly = v) } }
        ToggleRow(R.string.setup_settings_digest, s.weeklyDigest) { v -> change { it.copy(weeklyDigest = v) } }
    }
}

@Composable
private fun ToggleRow(label: Int, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        Text(stringResource(label), Modifier.weight(1f))
        Switch(value, onChange)
    }
}

// --------------------------------------------------------------------- lock

@Composable
private fun LockSection(s: AppSettings, say: (String) -> Unit) {
    val ctx = LocalContext.current
    val c = ctx.container
    val scope = rememberCoroutineScope()
    var mode by remember(s.lockMode) { mutableStateOf(s.lockMode) }
    var pin by remember { mutableStateOf("") }
    var again by remember { mutableStateOf("") }
    val done = stringResource(R.string.setup_settings_lock_done)
    val changed = mode != s.lockMode || (mode == LockMode.PIN && pin.isNotEmpty())
    SetupCard {
        SectionTitle(stringResource(R.string.setup_settings_lock))
        LockChoice(mode, { mode = it }, pin, again, { pin = it }, { again = it })
        Button(
            enabled = changed && lockReady(ctx, mode, pin, again),
            onClick = {
                scope.launch {
                    withContext(Dispatchers.IO) {
                        c.secrets.setPin(if (mode == LockMode.PIN) pin else null)
                        c.settings.update { it.copy(lockMode = mode) }
                    }
                    pin = ""; again = ""
                    say(done)
                }
            },
        ) { Text(stringResource(R.string.setup_settings_lock_apply)) }
    }
}

// --------------------------------------------------------------- appearance

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AppearanceSection(s: AppSettings) {
    val c = LocalContext.current.container
    val scope = rememberCoroutineScope()
    val langs = listOf("" to R.string.setup_lang_system, "en" to R.string.setup_lang_en, "nl" to R.string.setup_lang_nl)
    val themes = listOf(
        ThemeMode.SYSTEM to R.string.setup_theme_system,
        ThemeMode.LIGHT to R.string.setup_theme_light,
        ThemeMode.DARK to R.string.setup_theme_dark,
    )
    SetupCard {
        SectionTitle(stringResource(R.string.setup_settings_appearance))
        Text(stringResource(R.string.setup_settings_language), style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            langs.forEach { (tag, label) ->
                FilterChip(
                    selected = s.language == tag,
                    onClick = {
                        scope.launch { c.settings.update { it.copy(language = tag) } }
                        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag))
                    },
                    label = { Text(stringResource(label)) },
                )
            }
        }
        Text(stringResource(R.string.setup_settings_theme), style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            themes.forEach { (t, label) ->
                FilterChip(
                    selected = s.theme == t,
                    onClick = { scope.launch { c.settings.update { it.copy(theme = t) } } },
                    label = { Text(stringResource(label)) },
                )
            }
        }
    }
}

// ------------------------------------------------------------------- backup

@Composable
private fun BackupSection(say: (String) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var passMode by remember { mutableStateOf<PassMode?>(null) }
    var pass by remember { mutableStateOf<CharArray?>(null) }
    var importUri by remember { mutableStateOf<Uri?>(null) }
    var busy by remember { mutableStateOf(false) }
    val exported = stringResource(R.string.setup_backup_exported)
    val imported = stringResource(R.string.setup_backup_imported)
    val wrong = stringResource(R.string.setup_backup_wrong)
    val notBackup = stringResource(R.string.setup_backup_not_backup)

    fun fail(e: Throwable) = say(
        when (e) {
            is WrongPassphraseException -> wrong
            is NotABackupException -> notBackup
            else -> ctx.getString(R.string.setup_backup_failed, e.message ?: ctx.getString(R.string.setup_unknown_error))
        },
    )

    val create = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        val p = pass
        pass = null
        if (uri == null || p == null) return@rememberLauncherForActivityResult
        busy = true
        scope.launch {
            try {
                val bytes = Backup.export(ctx, p)
                withContext(Dispatchers.IO) {
                    ctx.contentResolver.openOutputStream(uri, "wt")!!.use { it.write(bytes) }
                }
                say(exported)
            } catch (e: Throwable) { fail(e) } finally { busy = false }
        }
    }
    val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) { importUri = uri; passMode = PassMode.IMPORT }
    }

    SetupCard {
        SectionTitle(stringResource(R.string.setup_backup_title))
        Text(stringResource(R.string.setup_backup_help), style = MaterialTheme.typography.bodySmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { passMode = PassMode.EXPORT }, enabled = !busy) {
                Text(stringResource(R.string.setup_backup_export))
            }
            OutlinedButton(onClick = { open.launch(arrayOf("*/*")) }, enabled = !busy) {
                Text(stringResource(R.string.setup_backup_import))
            }
        }
    }

    passMode?.let { mode ->
        PassphraseDialog(
            export = mode == PassMode.EXPORT,
            onDismiss = { passMode = null; importUri = null },
            onConfirm = { p ->
                passMode = null
                if (mode == PassMode.EXPORT) {
                    pass = p
                    create.launch("aufmachen-backup-${LocalDate.now()}.aufm")
                } else {
                    val uri = importUri
                    importUri = null
                    if (uri != null) {
                        busy = true
                        scope.launch {
                            try {
                                val bytes = withContext(Dispatchers.IO) {
                                    ctx.contentResolver.openInputStream(uri)!!.use { it.readBytes() }
                                }
                                Backup.import(ctx, bytes, p)
                                say(imported)
                            } catch (e: Throwable) { fail(e) } finally { busy = false }
                        }
                    }
                }
            },
        )
    }
}

@Composable
private fun PassphraseDialog(export: Boolean, onDismiss: () -> Unit, onConfirm: (CharArray) -> Unit) {
    var p1 by remember { mutableStateOf("") }
    var p2 by remember { mutableStateOf("") }
    val error = when {
        !export -> null
        p1.isNotEmpty() && p1.length < 8 -> R.string.setup_backup_pass_short
        p2.isNotEmpty() && p1 != p2 -> R.string.setup_backup_pass_mismatch
        else -> null
    }
    val valid = if (export) p1.length >= 8 && p1 == p2 else p1.isNotEmpty()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (export) R.string.setup_backup_pass_title_export else R.string.setup_backup_pass_title_import)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(if (export) R.string.setup_backup_pass_warning else R.string.setup_backup_import_warning))
                OutlinedTextField(
                    p1, { p1 = it }, singleLine = true, label = { Text(stringResource(R.string.setup_backup_pass_label)) },
                    visualTransformation = PasswordVisualTransformation(),
                )
                if (export) OutlinedTextField(
                    p2, { p2 = it }, singleLine = true, label = { Text(stringResource(R.string.setup_backup_pass_repeat)) },
                    isError = error != null, supportingText = { error?.let { Text(stringResource(it)) } },
                    visualTransformation = PasswordVisualTransformation(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(p1.toCharArray()) }, enabled = valid) {
                Text(stringResource(R.string.setup_ok))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.setup_cancel)) } },
    )
}

// ------------------------------------------------------------------- export

@Composable
private fun ExportSection(say: (String) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    fun write(uri: Uri?, xlsx: Boolean) {
        if (uri == null) return
        busy = true
        scope.launch {
            try {
                val rows = ServiceExport.load(ctx)
                withContext(Dispatchers.IO) {
                    ctx.contentResolver.openOutputStream(uri, "wt")!!.use {
                        if (xlsx) ServiceExport.writeXlsx(it, ServiceExport.HEADER, rows)
                        else ServiceExport.writeCsv(it, ServiceExport.HEADER, rows)
                    }
                }
                say(ctx.getString(R.string.setup_export_done, rows.size))
            } catch (e: Throwable) {
                say(ctx.getString(R.string.setup_export_failed, e.message ?: ctx.getString(R.string.setup_unknown_error)))
            } finally { busy = false }
        }
    }
    val csv = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { write(it, false) }
    val xlsx = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(MIME_XLSX)) { write(it, true) }
    SetupCard {
        SectionTitle(stringResource(R.string.setup_export_title))
        Text(stringResource(R.string.setup_export_help), style = MaterialTheme.typography.bodySmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { csv.launch("aufmachen-services-${LocalDate.now()}.csv") }, enabled = !busy) {
                Text(stringResource(R.string.setup_export_csv))
            }
            OutlinedButton(onClick = { xlsx.launch("aufmachen-services-${LocalDate.now()}.xlsx") }, enabled = !busy) {
                Text(stringResource(R.string.setup_export_xlsx))
            }
        }
    }
}

// --------------------------------------------------------------------- wipe

@Composable
private fun WipeSection() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var ask by remember { mutableStateOf(false) }
    var typed by remember { mutableStateOf("") }
    val word = stringResource(R.string.setup_wipe_word)
    SetupCard {
        SectionTitle(stringResource(R.string.setup_wipe_title))
        Text(stringResource(R.string.setup_wipe_help), style = MaterialTheme.typography.bodySmall)
        Button(
            onClick = { typed = ""; ask = true },
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
        ) { Text(stringResource(R.string.setup_wipe_button)) }
    }
    if (ask) {
        AlertDialog(
            onDismissRequest = { ask = false },
            title = { Text(stringResource(R.string.setup_wipe_dialog_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.setup_wipe_dialog_body, word))
                    OutlinedTextField(typed, { typed = it }, singleLine = true)
                }
            },
            confirmButton = {
                TextButton(
                    enabled = typed.trim().equals(word, ignoreCase = true),
                    onClick = { ask = false; scope.launch { wipeEverything(ctx) } },
                ) { Text(stringResource(R.string.setup_wipe_confirm), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { ask = false }) { Text(stringResource(R.string.setup_cancel)) } },
        )
    }
}

/**
 * Cancels jobs, empties the database and deletes it (its key is wiped with the secrets), removes
 * proofs and shared files, clears settings and secrets, then restarts the app in a fresh process.
 */
private suspend fun wipeEverything(context: Context) {
    val c = context.container
    SyncScheduler.cancelAll(context)
    withContext(Dispatchers.IO) {
        runCatching { c.db.clearAllTables() }
        runCatching { c.db.close() }
        context.deleteDatabase(AppDatabase.NAME)
        File(context.filesDir, "proofs").deleteRecursively()
        File(context.filesDir, "incoming").deleteRecursively()
        c.settings.clear()
        c.secrets.wipe()
    }
    AppCompatDelegate.setApplicationLocales(LocaleListCompat.getEmptyLocaleList())
    val intent = Intent(context, MainActivity::class.java)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
    context.startActivity(intent)
    Runtime.getRuntime().exit(0)
}
