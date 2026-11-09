package com.staninna.aufmachen

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import com.staninna.aufmachen.data.AppSettings
import com.staninna.aufmachen.data.LockMode
import com.staninna.aufmachen.ui.lock.LockScreen
import com.staninna.aufmachen.ui.nav.AppNav
import com.staninna.aufmachen.ui.theme.AufmachenTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Single activity. Also the share target for application/pdf (Tuta proof PDFs): the shared PDF
 * is copied to app-private storage and the app navigates to the proof screen once unlocked.
 */
class MainActivity : AppCompatActivity() {
    private var sharedPdf by mutableStateOf<String?>(null)
    private var unlocked by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        unlocked = savedInstanceState?.getBoolean("unlocked") ?: false
        handleShare(intent)
        setContent {
            val settings by container.settings.flow.collectAsState(initial = null)
            val s: AppSettings = settings ?: return@setContent
            // A lock switched on while the app is open (end of onboarding, settings) must not
            // lock the user out of the session that just set it. It applies from the next start.
            val firstLockMode = remember { s.lockMode }
            if (firstLockMode == LockMode.NONE && s.lockMode != LockMode.NONE) unlocked = true
            AufmachenTheme(s.theme) {
                if (s.lockMode != LockMode.NONE && !unlocked) {
                    LockScreen(s.lockMode) { unlocked = true }
                } else {
                    AppNav(s.onboardingDone, sharedPdf) { sharedPdf = null }
                }
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean("unlocked", unlocked)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleShare(intent)
    }

    /** Re-lock when the app goes to the background. */
    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations) unlocked = false
    }

    @Suppress("DEPRECATION")
    private fun handleShare(intent: Intent?) {
        if (intent?.action != Intent.ACTION_SEND) return
        val uri = (intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)) ?: return
        lifecycleScope.launch {
            val path = withContext(Dispatchers.IO) {
                runCatching {
                    val dir = File(filesDir, "incoming").apply { mkdirs() }
                    val out = File(dir, "shared-${System.currentTimeMillis()}.pdf")
                    contentResolver.openInputStream(uri)!!.use { i -> out.outputStream().use { i.copyTo(it) } }
                    out.absolutePath
                }.getOrNull()
            }
            sharedPdf = path
        }
    }
}
