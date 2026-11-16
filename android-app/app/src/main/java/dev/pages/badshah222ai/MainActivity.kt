package dev.pages.badshah222ai

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import dev.pages.badshah222ai.core.WakeSignal
import dev.pages.badshah222ai.core.WakeWordService
import dev.pages.badshah222ai.ui.AppNav
import dev.pages.badshah222ai.ui.theme.BadshahTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleWake(intent)
        startWakeIfEnabled()
        setContent { BadshahTheme { AppNav(this) } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleWake(intent)
    }

    private fun handleWake(intent: Intent?) {
        if (intent?.getStringExtra(WakeWordService.EXTRA_WAKE) == "1") WakeSignal.ping()
    }

    /** If the user left the wake word ON, restart its listener when the app opens. */
    private fun startWakeIfEnabled() {
        val store = (application as BadshahApp).container.store
        if (store.wakeWord &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        ) WakeWordService.start(this)
    }
}
