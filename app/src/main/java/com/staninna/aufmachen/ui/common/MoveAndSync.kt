package com.staninna.aufmachen.ui.common

import android.content.Context
import android.net.ConnectivityManager
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.staninna.aufmachen.R
import com.staninna.aufmachen.container
import com.staninna.aufmachen.data.HeaderEntity
import com.staninna.aufmachen.mail.SyncScheduler
import com.staninna.aufmachen.model.Placement
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun placementLabel(p: Placement): String = stringResource(
    when (p) {
        Placement.MIGRATE -> R.string.nav_migrate
        Placement.UNSUBSCRIBE -> R.string.nav_unsubscribe
        Placement.DORMANT -> R.string.nav_dormant
        Placement.DELETE -> R.string.nav_delete
        Placement.PEOPLE -> R.string.nav_people
    },
)

/**
 * "Move to…" picker. [current] is the user's earlier choice (null: the app's guess).
 * [onPick] gets the chosen tab, or null for "Let the app decide".
 */
@Composable
fun MoveToDialog(count: Int, current: Placement?, onPick: (Placement?) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (count == 1) stringResource(R.string.move_title_one) else stringResource(R.string.move_title_many, count)) },
        text = {
            Column {
                Placement.entries.forEach { p ->
                    MoveOption(placementLabel(p), selected = current == p) { onPick(p) }
                }
                MoveOption(stringResource(R.string.move_automatic), selected = current == null) { onPick(null) }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.move_cancel)) } },
    )
}

@Composable
private fun MoveOption(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Text(label, Modifier.padding(start = 8.dp))
    }
}

/**
 * Returns a "sync now" action for buttons and pull-to-refresh. Before the first scan has run, on
 * mobile data, it first asks whether to start now or wait for Wi-Fi, because the first scan reads
 * the headers of every mail. Shows a short confirmation toast either way.
 */
@Composable
fun rememberSyncNow(): () -> Unit {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var ask by remember { mutableStateOf(false) }

    if (ask) {
        AlertDialog(
            onDismissRequest = { ask = false },
            title = { Text(stringResource(R.string.sync_metered_title)) },
            text = { Text(stringResource(R.string.sync_metered_text)) },
            confirmButton = {
                TextButton(onClick = {
                    ask = false
                    SyncScheduler.syncNow(ctx, allowMetered = true)
                    toast(ctx, R.string.sync_started)
                }) { Text(stringResource(R.string.sync_metered_start)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    ask = false
                    SyncScheduler.syncNow(ctx, allowMetered = false)
                    toast(ctx, R.string.sync_waiting_wifi)
                }) { Text(stringResource(R.string.sync_metered_wait)) }
            },
        )
    }

    return {
        scope.launch {
            val c = ctx.container
            val needsAsk = withContext(Dispatchers.IO) {
                c.settings.get().imap != null &&
                    c.db.sync().get(HeaderEntity.SOURCE_IMAP)?.initialDone != true &&
                    ctx.getSystemService(ConnectivityManager::class.java).isActiveNetworkMetered
            }
            if (needsAsk) {
                ask = true
            } else {
                SyncScheduler.syncNow(ctx)
                toast(ctx, R.string.sync_started)
            }
        }
    }
}

private fun toast(ctx: Context, id: Int) = Toast.makeText(ctx, id, Toast.LENGTH_SHORT).show()
