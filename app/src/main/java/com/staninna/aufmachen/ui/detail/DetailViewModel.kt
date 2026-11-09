package com.staninna.aufmachen.ui.detail

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.staninna.aufmachen.AppContainer
import com.staninna.aufmachen.data.MergeEntity
import com.staninna.aufmachen.data.ProofEntity
import com.staninna.aufmachen.data.ServiceView
import com.staninna.aufmachen.data.TimelineEntity
import com.staninna.aufmachen.data.intsToJson
import com.staninna.aufmachen.data.jsonInts
import com.staninna.aufmachen.data.toJson
import com.staninna.aufmachen.engine.ClaudeAnswer
import com.staninna.aufmachen.model.AddressBucket
import com.staninna.aufmachen.model.Status
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

sealed interface DetailState {
    data object Loading : DetailState
    data object NotFound : DetailState
    data class Ready(
        val service: ServiceView,
        val timeline: List<TimelineEntity>,
        val proofs: List<ProofEntity>,
        /** Domains in this group that were merged in (have a merge row). */
        val mergedDomains: Set<String>,
        val buckets: List<Pair<AddressBucket, String?>>,
        val myDomain: String,
    ) : DetailState
}

@OptIn(ExperimentalCoroutinesApi::class)
class DetailViewModel(private val c: AppContainer, val domain: String) : ViewModel() {
    private val repo = c.repo

    val state: StateFlow<DetailState> = repo.service(domain).flatMapLatest { v ->
        if (v == null) flowOf<DetailState>(DetailState.NotFound)
        else combine(
            c.db.timeline().forDomains(v.domains),
            c.db.proofs().forDomains(v.domains),
            c.db.services().merges(),
            c.settings.flow,
        ) { tl, pr, merges: List<MergeEntity>, s ->
            DetailState.Ready(
                service = v,
                timeline = tl,
                proofs = pr,
                mergedDomains = merges.filter { it.intoDomain == v.domain }.map { it.domain }.toSet(),
                buckets = s.buckets.map { it to s.addressOf(it) },
                myDomain = s.domain,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DetailState.Loading)

    /** Other services for the merge picker. */
    val others: StateFlow<List<ServiceView>> = repo.services
        .map { all -> all.filter { it.domain != domain } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val merging = MutableStateFlow(false)

    fun setStatus(status: Status) = viewModelScope.launch { repo.setStatus(domain, status) }

    fun pinTier(level: Int?) = viewModelScope.launch { repo.pinTier(domain, level) }

    fun assignAddress(address: String?) = viewModelScope.launch {
        repo.updateState(domain) { it.copy(assignedAddress = address?.trim()?.ifEmpty { null }) }
    }

    fun setGoogleLogin(on: Boolean) = viewModelScope.launch {
        repo.updateState(domain) { it.copy(googleLogin = on, loginSwitched = if (on) it.loginSwitched else false) }
    }

    fun setLoginSwitched(v: Boolean) = viewModelScope.launch { repo.updateState(domain) { it.copy(loginSwitched = v) } }

    fun setStepDone(index: Int, checked: Boolean) = viewModelScope.launch {
        repo.updateState(domain) { it.copy(stepsDoneJson = toggleStep(it.stepsDoneJson.toIntSet(), index, checked).intsToJson()) }
    }

    fun saveAnswer(a: ClaudeAnswer) = viewModelScope.launch {
        repo.updateState(domain) {
            it.copy(
                settingsUrl = a.settingsUrl, privacyContact = a.privacyContact,
                stepsJson = a.steps.toJson(), gotchasJson = a.gotchas.toJson(),
                stepsDoneJson = emptyList<Int>().intsToJson(), claudeNotes = null,
            )
        }
    }

    fun saveNotes(text: String) = viewModelScope.launch { repo.updateState(domain) { it.copy(claudeNotes = text) } }

    fun merge(other: String) = viewModelScope.launch {
        merging.value = true
        try {
            withContext(Dispatchers.Default) { repo.merge(other, into = domain) }
        } finally {
            merging.value = false
        }
    }

    fun unmerge(other: String) = viewModelScope.launch {
        merging.value = true
        try {
            withContext(Dispatchers.Default) { repo.unmerge(other) }
        } finally {
            merging.value = false
        }
    }

    /** Copies the picked PDF into filesDir/incoming and returns its path, or null on failure. */
    suspend fun importPdf(ctx: Context, uri: Uri): String? = withContext(Dispatchers.IO) {
        runCatching {
            val dir = File(ctx.filesDir, "incoming").apply { mkdirs() }
            val out = File(dir, "proof-${System.currentTimeMillis()}.pdf")
            ctx.contentResolver.openInputStream(uri)!!.use { i -> out.outputStream().use { i.copyTo(it) } }
            out.absolutePath
        }.getOrNull()
    }
}
private fun String.toIntSet(): Set<Int> = jsonInts().toSet()
