package com.staninna.aufmachen.ui.onboarding

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.staninna.aufmachen.AppContainer
import com.staninna.aufmachen.R
import com.staninna.aufmachen.container
import com.staninna.aufmachen.data.AppSettings
import com.staninna.aufmachen.data.ImapConfig
import com.staninna.aufmachen.data.LockMode
import com.staninna.aufmachen.mail.MailAccess
import com.staninna.aufmachen.mail.SyncScheduler
import com.staninna.aufmachen.ui.nav.Routes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val STEPS = 5

class OnboardingViewModel : ViewModel() {
    var step by mutableStateOf(0)
    var settings by mutableStateOf(AppSettings())
    var imap by mutableStateOf(ImapConfig())
    var password by mutableStateOf("")
    var test by mutableStateOf<TestState>(TestState.Idle)
    var pin by mutableStateOf("")
    var pinAgain by mutableStateOf("")
    var starting by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    var mboxError by mutableStateOf(false)

    val hasImap get() = imapComplete(imap, password)
    val hasMbox get() = settings.mboxUri != null

    fun runTest(scope: CoroutineScope, needFields: String) {
        if (!hasImap) {
            test = TestState.Failed(needFields); return
        }
        test = TestState.Running
        val cfg = imap
        val pw = password.replace(" ", "")
        scope.launch {
            test = runCatching { MailAccess.testLogin(cfg, pw) }
                .map { r -> r.fold({ TestState.Ok }, { TestState.Failed(it.message ?: "") }) }
                .getOrElse { TestState.Failed(it.message ?: "") }
        }
    }

    /** Saves everything and kicks off the background work. Call [afterStart] on the main thread when done. */
    suspend fun start(context: Context) {
        val c: AppContainer = context.container
        val imapOn = hasImap
        withContext(Dispatchers.IO) {
            if (imapOn) c.secrets.imapPassword = password
            c.secrets.setPin(if (settings.lockMode == LockMode.PIN) pin else null)
            c.settings.replace(
                settings.copy(
                    domain = settings.domain.trim().lowercase(),
                    imap = if (imapOn) imap else null,
                    onboardingDone = true,
                ),
            )
        }
        if (imapOn) SyncScheduler.startInitialScan(context)
        if (hasMbox) SyncScheduler.importMbox(context)
        SyncScheduler.schedulePeriodic(context)
    }
}

@Composable
fun OnboardingScreen(nav: NavHostController) {
    val vm: OnboardingViewModel = viewModel()
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    fun finish() {
        vm.starting = true
        scope.launch {
            try {
                vm.start(ctx)
                nav.navigate(Routes.HOME) { popUpTo(Routes.ONBOARDING) { inclusive = true } }
            } catch (e: Throwable) {
                vm.starting = false
                vm.error = e.message ?: ctx.getString(R.string.setup_unknown_error)
            }
        }
    }

    val notif = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { finish() }
    fun onStart() {
        val need = Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        if (need) notif.launch(Manifest.permission.POST_NOTIFICATIONS) else finish()
    }

    val s = vm.settings
    val canNext = when (vm.step) {
        1 -> isValidDomain(s.domain) && bucketsError(s.buckets) == null && s.otherOwnAddresses.all(::isValidAddress)
        2 -> vm.hasImap || vm.hasMbox
        3 -> lockReady(ctx, s.lockMode, vm.pin, vm.pinAgain)
        else -> true
    }

    Scaffold(
        topBar = {
            Column(Modifier.statusBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp)) {
                Text(
                    stringResource(R.string.setup_step_of, vm.step + 1, STEPS),
                    style = MaterialTheme.typography.labelMedium,
                )
                LinearProgressIndicator(
                    progress = { (vm.step + 1) / STEPS.toFloat() },
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                )
            }
        },
        bottomBar = {
            Row(Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                if (vm.step > 0) OutlinedButton(onClick = { vm.step-- }, enabled = !vm.starting) {
                    Text(stringResource(R.string.setup_back))
                } else Row {}
                if (vm.step < STEPS - 1) {
                    Button(onClick = { vm.step++ }, enabled = canNext) { Text(stringResource(R.string.setup_next)) }
                } else {
                    Button(onClick = ::onStart, enabled = !vm.starting) {
                        Text(stringResource(if (vm.starting) R.string.setup_starting else R.string.setup_start_button))
                    }
                }
            }
        },
    ) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when (vm.step) {
                0 -> IntroStep()
                1 -> AddressesStep(vm)
                2 -> ConnectStep(vm, scope)
                3 -> LockStep(vm)
                else -> StartStep(vm)
            }
        }
    }
}

@Composable
private fun IntroStep() {
    Text(stringResource(R.string.setup_intro_title), style = MaterialTheme.typography.headlineSmall)
    Text(stringResource(R.string.setup_intro_body))
    SetupCard {
        SectionTitle(stringResource(R.string.setup_intro_does))
        listOf(R.string.setup_intro_does_1, R.string.setup_intro_does_2, R.string.setup_intro_does_3)
            .forEach { Text(stringResource(it)) }
    }
    SetupCard {
        SectionTitle(stringResource(R.string.setup_intro_never))
        listOf(R.string.setup_intro_never_1, R.string.setup_intro_never_2, R.string.setup_intro_never_3)
            .forEach { Text(stringResource(it)) }
    }
}

@Composable
fun CatchAllTip() {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.setup_catchall_title), style = MaterialTheme.typography.titleSmall)
            Text(stringResource(R.string.setup_catchall_body), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun AddressesStep(vm: OnboardingViewModel) {
    val s = vm.settings
    Text(stringResource(R.string.setup_addresses_title), style = MaterialTheme.typography.headlineSmall)
    DomainField(s.domain) { vm.settings = s.copy(domain = it) }
    SectionTitle(stringResource(R.string.setup_buckets_title))
    Text(stringResource(R.string.setup_buckets_help), style = MaterialTheme.typography.bodySmall)
    BucketsEditor(s.domain, s.buckets) { vm.settings = s.copy(buckets = it) }
    bucketsError(s.buckets)?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error) }
    CatchAllTip()
    SectionTitle(stringResource(R.string.setup_own_title))
    Text(stringResource(R.string.setup_own_help), style = MaterialTheme.typography.bodySmall)
    OwnAddressesEditor(s.otherOwnAddresses) { vm.settings = s.copy(otherOwnAddresses = it) }
}

@Composable
fun DomainField(domain: String, onChange: (String) -> Unit) {
    val bad = domain.isNotBlank() && !isValidDomain(domain)
    OutlinedTextField(
        domain, { onChange(it.trim().lowercase()) }, singleLine = true, isError = bad,
        label = { Text(stringResource(R.string.setup_domain_label)) },
        placeholder = { Text(stringResource(R.string.setup_domain_hint)) },
        supportingText = { if (bad) Text(stringResource(R.string.setup_domain_invalid)) },
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun ConnectStep(vm: OnboardingViewModel, scope: CoroutineScope) {
    val needFields = stringResource(R.string.setup_test_need_fields)
    Text(stringResource(R.string.setup_connect_title), style = MaterialTheme.typography.headlineSmall)
    Text(stringResource(R.string.setup_connect_help))
    SetupCard {
        SectionTitle(stringResource(R.string.setup_imap_title))
        ImapSection(
            config = vm.imap, password = vm.password, keepSavedPassword = false, test = vm.test,
            onConfig = { vm.imap = it; vm.test = TestState.Idle },
            onPassword = { vm.password = it; vm.test = TestState.Idle },
            onTest = { vm.runTest(scope, needFields) },
        )
    }
    SetupCard {
        SectionTitle(stringResource(R.string.setup_mbox_title))
        MboxSection(
            vm.settings.mboxName,
            onPicked = { uri, name -> vm.mboxError = false; vm.settings = vm.settings.copy(mboxUri = uri, mboxName = name) },
            onFailed = { vm.mboxError = true },
        )
        if (vm.mboxError) Text(stringResource(R.string.setup_mbox_failed), color = MaterialTheme.colorScheme.error)
    }
    if (!vm.hasImap && !vm.hasMbox) {
        Text(stringResource(R.string.setup_source_required), color = MaterialTheme.colorScheme.error)
    }
}

@Composable
private fun LockStep(vm: OnboardingViewModel) {
    Text(stringResource(R.string.setup_lock_title), style = MaterialTheme.typography.headlineSmall)
    Text(stringResource(R.string.setup_lock_help))
    LockChoice(
        mode = vm.settings.lockMode,
        onMode = { vm.settings = vm.settings.copy(lockMode = it) },
        pin = vm.pin, pinAgain = vm.pinAgain,
        onPin = { vm.pin = it }, onPinAgain = { vm.pinAgain = it },
    )
}

@Composable
private fun StartStep(vm: OnboardingViewModel) {
    val s = vm.settings
    Text(stringResource(R.string.setup_start_title), style = MaterialTheme.typography.headlineSmall)
    Text(stringResource(R.string.setup_start_body))
    SetupCard {
        Text(stringResource(R.string.setup_start_domain, s.domain))
        s.mainBucket?.let { Text(stringResource(R.string.setup_start_main, "${it.localPart}@${s.domain}")) }
        if (vm.hasImap) Text(stringResource(R.string.setup_start_imap, vm.imap.user))
        s.mboxName?.let { Text(stringResource(R.string.setup_start_mbox, it)) }
        val lock = stringResource(
            when (s.lockMode) {
                LockMode.NONE -> R.string.setup_lock_none
                LockMode.BIOMETRIC -> R.string.setup_lock_biometric
                LockMode.PIN -> R.string.setup_lock_pin
            },
        )
        Text(stringResource(R.string.setup_start_lock, lock))
    }
    vm.error?.let { Text(stringResource(R.string.setup_start_error, it), color = MaterialTheme.colorScheme.error) }
}
