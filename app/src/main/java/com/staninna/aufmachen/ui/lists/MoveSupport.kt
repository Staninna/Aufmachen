package com.staninna.aufmachen.ui.lists

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import com.staninna.aufmachen.R
import com.staninna.aufmachen.container
import com.staninna.aufmachen.data.PersonStateEntity
import com.staninna.aufmachen.model.Placement
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Second step of moving people: offers their companies' accounts, all unchecked. [onMove] gets the checked ones. */
@Composable
fun AlsoMoveDialog(to: Placement, domains: List<String>, onMove: (List<String>) -> Unit, onCancel: () -> Unit) {
    val checked = remember { mutableStateListOf<String>() }
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(R.string.lists_also_title, stringResource(placementName(to)))) },
        text = {
            Column {
                domains.forEach { d ->
                    Row(
                        Modifier.fillMaxWidth().clickable { if (d in checked) checked.remove(d) else checked.add(d) },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(d in checked, { if (d in checked) checked.remove(d) else checked.add(d) })
                        Text(d)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onMove(checked.toList()) }) { Text(stringResource(R.string.lists_also_move)) } },
        dismissButton = { TextButton(onClick = onCancel) { Text(stringResource(R.string.lists_cancel)) } },
    )
}

/** What a "Move to..." dialog is open for: the ids to move and their shared current tab, if any. */
data class MoveRequest(val ids: List<String>, val current: Placement?)

/**
 * Groups the placements to put back by target tab (null: the app's guess). Only entries whose
 * current placement differs from the remembered one are included. A domain missing from a map has no placement.
 */
fun restoreGroups(before: Map<String, Placement?>, now: Map<String, Placement?>): Map<Placement?, List<String>> =
    (before.keys + now.keys).filter { before[it] != now[it] }.groupBy { before[it] }

/** Recipients for "I've moved": chosen people first, then the sender addresses of chosen services, no repeats. */
fun movedRecipients(people: Collection<String>, serviceAddrs: Collection<Collection<String>>): List<String> =
    (people + serviceAddrs.flatten()).distinct()

private data class Snapshot(val services: Map<String, Placement?>, val people: Map<String, Placement?>)

private fun placementName(p: Placement): Int = when (p) {
    Placement.MIGRATE -> R.string.nav_migrate
    Placement.UNSUBSCRIBE -> R.string.nav_unsubscribe
    Placement.DORMANT -> R.string.nav_dormant
    Placement.DELETE -> R.string.nav_delete
    Placement.PEOPLE -> R.string.nav_people
}

/** Moves services and people between tabs, off the main thread, and offers Undo in a snackbar. */
class MoveActions(ctx: Context, private val scope: CoroutineScope, val snackbar: SnackbarHostState) {
    private val ctx = ctx.applicationContext
    private val c = this.ctx.container

    private suspend fun servicePlacements(): Map<String, Placement?> =
        c.db.services().allStates().first().associate { s ->
            s.domain to s.placement?.let { runCatching { Placement.valueOf(it) }.getOrNull() }
        }

    private suspend fun peoplePlacements(): Map<String, Placement?> =
        c.db.people().statesOnce().associate { s ->
            s.address to s.placement?.let { runCatching { Placement.valueOf(it) }.getOrNull() }
        }

    /** Services behind [addresses] that the user may want to move along. */
    suspend fun servicesOfPeople(addresses: Collection<String>): List<String> =
        withContext(Dispatchers.IO) { c.repo.servicesOfPeople(addresses) }

    /** [onDone] runs on the main thread once the move is stored. */
    fun move(
        domains: Collection<String> = emptyList(),
        addresses: Collection<String> = emptyList(),
        to: Placement?,
        alsoServices: Collection<String> = emptyList(),
        onDone: () -> Unit = {},
    ) {
        if (domains.isEmpty() && addresses.isEmpty()) return
        scope.launch {
            val snap = withContext(Dispatchers.IO + NonCancellable) {
                val snap = Snapshot(servicePlacements(), peoplePlacements())
                if (domains.isNotEmpty()) c.repo.moveServices(domains, to)
                if (addresses.isNotEmpty()) c.repo.movePeople(addresses, to, alsoServices)
                snap
            }
            onDone()
            val count = domains.size + addresses.size
            val msg = if (to == null) ctx.getString(R.string.lists_moved_snack_auto, count)
            else ctx.getString(R.string.lists_moved_snack, count, ctx.getString(placementName(to)))
            snackbar.currentSnackbarData?.dismiss()
            val r = snackbar.showSnackbar(msg, ctx.getString(R.string.lists_undo), duration = SnackbarDuration.Long)
            if (r == SnackbarResult.ActionPerformed) undo(snap)
        }
    }

    private suspend fun undo(snap: Snapshot) = withContext(Dispatchers.IO + NonCancellable) {
        restoreGroups(snap.services, servicePlacements()).forEach { (p, ds) -> c.repo.moveServices(ds, p) }
        val states = c.db.people().statesOnce().associateBy { it.address }
        val changed = restoreGroups(snap.people, peoplePlacements()).flatMap { (p, addrs) ->
            addrs.map { a -> (states[a] ?: PersonStateEntity(a)).copy(placement = p?.name) }
        }
        if (changed.isNotEmpty()) c.db.people().upsertStates(changed)
    }
}

@Composable
fun rememberMoveActions(): MoveActions {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    return remember { MoveActions(ctx, scope, SnackbarHostState()) }
}

/** The tab all of [current] share, or null (the app decides) when they differ. */
fun sharedPlacement(current: Collection<Placement?>): Placement? = current.distinct().singleOrNull()
