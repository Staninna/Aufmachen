package com.staninna.aufmachen.ui.people

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.staninna.aufmachen.container
import com.staninna.aufmachen.engine.Templates
import com.staninna.aufmachen.ui.nav.Selection
import kotlinx.coroutines.launch
import java.util.Locale

class ComposeMovedViewModel(app: Context) : ViewModel() {
    private val container = app.applicationContext.container
    val recipients: List<String> = Selection.people.toList()

    var mainAddress by mutableStateOf<String?>(null)
        private set
    var dutch by mutableStateOf(Locale.getDefault().language == "nl")
        private set
    var subject by mutableStateOf("")
    var body by mutableStateOf("")
    var bccMode by mutableStateOf(true)

    /** Index of the person whose mail is up in one-per-person mode. */
    var index by mutableIntStateOf(0)
        private set
    var finished by mutableStateOf(false)
        private set
    var opened by mutableStateOf(false)

    init {
        viewModelScope.launch {
            mainAddress = container.settings.get().mainAddress
            applyTemplate()
        }
    }

    fun chooseDutch(v: Boolean) {
        dutch = v
        applyTemplate()
    }

    private fun applyTemplate() {
        val m = Templates.moved(mainAddress.orEmpty(), dutch)
        subject = m.subject
        body = m.body
    }

    fun setMode(bcc: Boolean) {
        bccMode = bcc
        opened = false
        index = 0
    }

    /** All in one mail: the user confirms it was sent. */
    fun confirmBccSent() {
        viewModelScope.launch {
            setTold(container.db, recipients, true)
            finished = true
        }
    }

    /** One per person: the current person was mailed, or skipped when [sent] is false. */
    fun next(sent: Boolean) {
        val who = recipients.getOrNull(index) ?: return
        viewModelScope.launch {
            if (sent) setTold(container.db, listOf(who), true)
            opened = false
            if (index + 1 < recipients.size) index++ else finished = true
        }
    }
}
