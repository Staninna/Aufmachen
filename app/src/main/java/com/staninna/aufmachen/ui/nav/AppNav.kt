package com.staninna.aufmachen.ui.nav

import android.net.Uri
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Hotel
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Unsubscribe
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.staninna.aufmachen.R
import com.staninna.aufmachen.ui.alerts.AlertsScreen
import com.staninna.aufmachen.ui.delete.DeleteScreen
import com.staninna.aufmachen.ui.detail.GdprScreen
import com.staninna.aufmachen.ui.detail.ServiceDetailScreen
import com.staninna.aufmachen.ui.dormant.DormantScreen
import com.staninna.aufmachen.ui.home.HomeScreen
import com.staninna.aufmachen.ui.migrate.MigrateScreen
import com.staninna.aufmachen.ui.onboarding.OnboardingScreen
import com.staninna.aufmachen.ui.people.ComposeMovedScreen
import com.staninna.aufmachen.ui.people.PeopleScreen
import com.staninna.aufmachen.ui.proof.GoogleConnectionsScreen
import com.staninna.aufmachen.ui.proof.ProofScreen
import com.staninna.aufmachen.ui.readiness.CloseGmailScreen
import com.staninna.aufmachen.ui.readiness.ReadinessScreen
import com.staninna.aufmachen.ui.settings.SettingsScreen
import com.staninna.aufmachen.ui.unsubscribe.UnsubscribeConfirmScreen
import com.staninna.aufmachen.ui.unsubscribe.UnsubscribeScreen

/**
 * Routes. Screens navigate with `nav.navigate(Routes.service(domain))` etc.
 * Multi-select flows hand their selection over through [Selection].
 */
object Routes {
    const val ONBOARDING = "onboarding"
    const val HOME = "home"
    const val MIGRATE = "migrate"
    const val UNSUBSCRIBE = "unsubscribe"
    const val UNSUBSCRIBE_CONFIRM = "unsubscribe/confirm"
    const val DORMANT = "dormant"
    const val DELETE = "delete"
    const val PEOPLE = "people"
    const val COMPOSE_MOVED = "people/compose"
    const val ALERTS = "alerts"
    const val SETTINGS = "settings"
    const val READINESS = "readiness"
    const val CLOSE_GMAIL = "readiness/close"
    const val GOOGLE_CONNECTIONS = "google/connections"
    const val SERVICE = "service/{domain}"
    const val GDPR = "gdpr/{domain}"
    const val PROOF = "proof?pdf={pdf}"

    fun service(domain: String) = "service/${Uri.encode(domain)}"
    fun gdpr(domain: String) = "gdpr/${Uri.encode(domain)}"
    /** [pdfPath] is a file path in app-private storage. */
    fun proof(pdfPath: String) = "proof?pdf=${Uri.encode(pdfPath)}"
}

/** In-memory hand-over of multi-select results between screens (domains or addresses). */
object Selection {
    var unsubscribe: List<String> = emptyList()
    var dormantDelete: List<String> = emptyList()
    var delete: List<String> = emptyList()
    var people: List<String> = emptyList()
}

private data class Tab(val route: String, val label: Int, val icon: ImageVector)

private val tabs = listOf(
    Tab(Routes.HOME, R.string.nav_home, Icons.Default.Home),
    Tab(Routes.MIGRATE, R.string.nav_migrate, Icons.Default.SwapHoriz),
    Tab(Routes.UNSUBSCRIBE, R.string.nav_unsubscribe, Icons.Default.Unsubscribe),
    Tab(Routes.DORMANT, R.string.nav_dormant, Icons.Default.Hotel),
    Tab(Routes.PEOPLE, R.string.nav_people, Icons.Default.People),
    Tab(Routes.DELETE, R.string.nav_delete, Icons.Default.DeleteOutline),
)

/**
 * Root navigation. Each tab screen draws its own TopAppBar (title, Alerts bell via
 * [com.staninna.aufmachen.ui.common.AlertsBell], overflow with Settings).
 */
@Composable
fun AppNav(onboardingDone: Boolean, sharedPdf: String?, onSharedPdfConsumed: () -> Unit) {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    val showBar = tabs.any { it.route == route }

    LaunchedEffect(sharedPdf, onboardingDone) {
        if (sharedPdf != null && onboardingDone) {
            nav.navigate(Routes.proof(sharedPdf))
            onSharedPdfConsumed()
        }
    }

    Scaffold(
        bottomBar = {
            if (showBar) NavigationBar {
                tabs.forEach { t ->
                    NavigationBarItem(
                        selected = route == t.route,
                        onClick = { nav.navigateTab(t.route) },
                        icon = { Icon(t.icon, null) },
                        // Six tabs: one line, small type, so long labels like "Unsubscribe" fit.
                        label = {
                            Text(stringResource(t.label), maxLines = 1, overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.labelSmall)
                        },
                    )
                }
            }
        },
    ) { pad ->
        NavHost(
            nav,
            startDestination = if (onboardingDone) Routes.HOME else Routes.ONBOARDING,
            modifier = Modifier.padding(bottom = pad.calculateBottomPadding()),
        ) {
            composable(Routes.ONBOARDING) { OnboardingScreen(nav) }
            composable(Routes.HOME) { HomeScreen(nav) }
            composable(Routes.MIGRATE) { MigrateScreen(nav) }
            composable(Routes.UNSUBSCRIBE) { UnsubscribeScreen(nav) }
            composable(Routes.UNSUBSCRIBE_CONFIRM) { UnsubscribeConfirmScreen(nav) }
            composable(Routes.DORMANT) { DormantScreen(nav) }
            composable(Routes.PEOPLE) { PeopleScreen(nav) }
            composable(Routes.DELETE) { DeleteScreen(nav) }
            composable(Routes.COMPOSE_MOVED) { ComposeMovedScreen(nav) }
            composable(Routes.ALERTS) { AlertsScreen(nav) }
            composable(Routes.SETTINGS) { SettingsScreen(nav) }
            composable(Routes.READINESS) { ReadinessScreen(nav) }
            composable(Routes.CLOSE_GMAIL) { CloseGmailScreen(nav) }
            composable(Routes.GOOGLE_CONNECTIONS) { GoogleConnectionsScreen(nav) }
            composable(Routes.SERVICE, listOf(navArgument("domain") { type = NavType.StringType })) {
                ServiceDetailScreen(nav, it.arguments?.getString("domain").orEmpty())
            }
            composable(Routes.GDPR, listOf(navArgument("domain") { type = NavType.StringType })) {
                GdprScreen(nav, it.arguments?.getString("domain").orEmpty())
            }
            composable(Routes.PROOF, listOf(navArgument("pdf") { type = NavType.StringType; nullable = true })) {
                ProofScreen(nav, it.arguments?.getString("pdf"))
            }
        }
    }
}

fun NavHostController.navigateTab(route: String) = navigate(route) {
    popUpTo(graph.findStartDestination().id) { saveState = true }
    launchSingleTop = true
    restoreState = true
}
