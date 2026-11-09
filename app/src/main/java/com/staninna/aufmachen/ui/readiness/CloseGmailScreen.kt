package com.staninna.aufmachen.ui.readiness

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.staninna.aufmachen.R
import com.staninna.aufmachen.container
import com.staninna.aufmachen.ui.actions.openUrl
import com.staninna.aufmachen.ui.common.BackTopBar

private class EndgameStep(val id: String, val title: Int, val body: Int, val url: String? = null, val link: Int = 0)

private val steps = listOf(
    EndgameStep("takeout", R.string.home_end_takeout, R.string.home_end_takeout_body),
    EndgameStep("payments", R.string.home_end_payments, R.string.home_end_payments_body, "https://pay.google.com", R.string.home_end_payments_link),
    EndgameStep("play", R.string.home_end_play, R.string.home_end_play_body),
    EndgameStep("recovery", R.string.home_end_recovery, R.string.home_end_recovery_body),
    EndgameStep(
        "delete", R.string.home_end_delete, R.string.home_end_delete_body,
        "https://myaccount.google.com/delete-services-or-account", R.string.home_end_delete_link,
    ),
)

@Composable
fun CloseGmailScreen(nav: NavHostController) {
    val ctx = LocalContext.current
    val vm = viewModel { ReadinessViewModel(ctx.container) }
    val settings by vm.settings.collectAsState()
    val ticks = settings?.endgameTicks.orEmpty()

    Scaffold(topBar = { BackTopBar(stringResource(R.string.home_close_title), nav) }) { pad ->
        LazyColumn(
            Modifier.fillMaxSize().padding(pad),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "intro") {
                Text(stringResource(R.string.home_close_intro), style = MaterialTheme.typography.bodyMedium)
            }
            items(steps, key = { it.id }) { st ->
                Card(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(8.dp), verticalAlignment = Alignment.Top) {
                        Checkbox(st.id in ticks, onCheckedChange = { vm.setEndgameTick(st.id, it) })
                        Column(Modifier.weight(1f).padding(top = 12.dp, end = 8.dp)) {
                            Text(stringResource(st.title), style = MaterialTheme.typography.titleMedium)
                            Text(
                                stringResource(st.body),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (st.url != null) {
                                TextButton(onClick = { openUrl(ctx, st.url) }) { Text(stringResource(st.link)) }
                            }
                        }
                    }
                }
            }
        }
    }
}
