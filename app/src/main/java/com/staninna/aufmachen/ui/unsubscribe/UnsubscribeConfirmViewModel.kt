package com.staninna.aufmachen.ui.unsubscribe

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.staninna.aufmachen.R
import com.staninna.aufmachen.container
import com.staninna.aufmachen.data.TimelineKind
import com.staninna.aufmachen.mail.MailAccess
import com.staninna.aufmachen.model.Status
import com.staninna.aufmachen.ui.nav.Selection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

enum class ItemState { PENDING, RUNNING, DONE, FAILED, SKIPPED }

enum class ConfirmPhase { REVIEW, RUNNING, WEB, DONE }

data class ConfirmItem(
    val domain: String,
    val name: String,
    val plan: UnsubPlan?,
    val state: ItemState,
    val message: String? = null,
)

class UnsubscribeConfirmViewModel(app: Context) : ViewModel() {
    private val ctx = app.applicationContext
    private val repo = ctx.container.repo

    val items = MutableStateFlow<List<ConfirmItem>>(emptyList())
    val oldAddress = MutableStateFlow("")
    val phase = MutableStateFlow(ConfirmPhase.REVIEW)
    val loaded = MutableStateFlow(false)

    init {
        viewModelScope.launch(Dispatchers.Default) {
            val s = repo.settings.get()
            val from = s.imap?.user?.takeIf { it.isNotBlank() }
            oldAddress.value = from.orEmpty()
            val byDomain = repo.services.first().associateBy { it.domain }
            items.value = Selection.unsubscribe.mapNotNull { d ->
                val sv = byDomain[d] ?: return@mapNotNull null
                val plan = planUnsub(sv.unsubLinks, sv.stats.unsubOneClick, canSendMail = from != null)
                if (plan == null) {
                    ConfirmItem(d, sv.name, null, ItemState.SKIPPED, ctx.getString(R.string.lists_confirm_skip_nomethod))
                } else ConfirmItem(d, sv.name, plan, ItemState.PENDING)
            }
            loaded.value = true
        }
    }

    private fun update(domain: String, f: (ConfirmItem) -> ConfirmItem) {
        items.value = items.value.map { if (it.domain == domain) f(it) else it }
    }

    /** Runs one-click and email items one after another, then hands over to the web flow. */
    fun confirm() {
        if (phase.value == ConfirmPhase.RUNNING) return
        phase.value = ConfirmPhase.RUNNING
        viewModelScope.launch(Dispatchers.IO) {
            val todo = items.value.filter {
                it.plan != null && it.plan.step != UnsubStep.WEB &&
                    (it.state == ItemState.PENDING || it.state == ItemState.FAILED)
            }
            for (item in todo) {
                update(item.domain) { it.copy(state = ItemState.RUNNING, message = null) }
                val plan = item.plan!!
                val result = runCatching {
                    when (plan.step) {
                        UnsubStep.ONE_CLICK -> MailAccess.oneClickUnsubscribe(plan.link).getOrThrow()
                        UnsubStep.EMAIL -> plan.mailto!!.let {
                            MailAccess.sendFromOldAccount(ctx, it.address, it.subject, it.body).getOrThrow()
                        }
                        UnsubStep.WEB -> Unit
                    }
                }
                val err = result.exceptionOrNull()
                if (err == null) {
                    markDone(item)
                    update(item.domain) { it.copy(state = ItemState.DONE) }
                } else {
                    val msg = err.message?.takeIf { it.isNotBlank() && err !is NotImplementedError }
                        ?: ctx.getString(R.string.lists_confirm_failed_generic)
                    update(item.domain) { it.copy(state = ItemState.FAILED, message = msg) }
                }
            }
            phase.value = nextPhase()
        }
    }

    fun currentWeb(): ConfirmItem? =
        items.value.firstOrNull { it.plan?.step == UnsubStep.WEB && it.state == ItemState.PENDING }

    private fun nextPhase() = if (currentWeb() != null) ConfirmPhase.WEB else ConfirmPhase.DONE

    fun webDone(domain: String) {
        val item = items.value.firstOrNull { it.domain == domain } ?: return
        viewModelScope.launch(Dispatchers.IO) {
            markDone(item)
            update(domain) { it.copy(state = ItemState.DONE) }
            phase.value = nextPhase()
        }
    }

    fun webSkip(domain: String) {
        update(domain) { it.copy(state = ItemState.SKIPPED, message = ctx.getString(R.string.lists_confirm_skipped_by_you)) }
        phase.value = nextPhase()
    }

    private suspend fun markDone(item: ConfirmItem) {
        val plan = item.plan ?: return
        val method = when (plan.step) {
            UnsubStep.ONE_CLICK -> "oneclick"
            UnsubStep.EMAIL -> "mailto"
            UnsubStep.WEB -> "web"
        }
        repo.setStatus(item.domain, Status.UNSUBSCRIBED, method)
        repo.updateState(item.domain) { it.copy(unsubMethod = method) }
        when (plan.step) {
            UnsubStep.ONE_CLICK -> repo.timeline(item.domain, TimelineKind.SENT,
                ctx.getString(R.string.lists_timeline_oneclick, hostOf(plan.link)))
            UnsubStep.EMAIL -> repo.timeline(item.domain, TimelineKind.SENT,
                ctx.getString(R.string.lists_timeline_email, plan.mailto?.address.orEmpty()))
            UnsubStep.WEB -> Unit
        }
    }
}
