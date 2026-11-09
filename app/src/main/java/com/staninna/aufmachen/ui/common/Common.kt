package com.staninna.aufmachen.ui.common

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
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
import androidx.navigation.NavHostController
import com.staninna.aufmachen.R
import com.staninna.aufmachen.container
import com.staninna.aufmachen.ui.nav.Routes

/** Alerts bell with the open-alert badge. */
@Composable
fun AlertsBell(nav: NavHostController) {
    val ctx = LocalContext.current
    val count by remember { ctx.container.db.alerts().openCount() }.collectAsState(initial = 0)
    IconButton(onClick = { nav.navigate(Routes.ALERTS) }) {
        BadgedBox(badge = { if (count > 0) Badge { Text("$count") } }) {
            Icon(Icons.Default.Notifications, stringResource(R.string.alerts_title))
        }
    }
}

/** Top bar for the five tab screens: title, alerts bell, overflow with Settings and Readiness. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TabTopBar(title: String, nav: NavHostController) {
    var menu by remember { mutableStateOf(false) }
    TopAppBar(
        title = { Text(title) },
        actions = {
            AlertsBell(nav)
            IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, null) }
            DropdownMenu(menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text(stringResource(R.string.readiness_title)) },
                    onClick = { menu = false; nav.navigate(Routes.READINESS) })
                DropdownMenuItem(text = { Text(stringResource(R.string.settings_title)) },
                    onClick = { menu = false; nav.navigate(Routes.SETTINGS) })
            }
        },
    )
}

/** Top bar with a back arrow, for pushed screens. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackTopBar(title: String, nav: NavHostController, actions: @Composable () -> Unit = {}) {
    TopAppBar(
        title = { Text(title) },
        navigationIcon = {
            IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) }
        },
        actions = { actions() },
    )
}

/** Placeholder used by stub screens until their area is built. */
@Composable
fun Placeholder(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(text) }
}
