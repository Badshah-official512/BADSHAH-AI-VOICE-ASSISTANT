package dev.pages.badshah222ai.features.help

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Help / About — Phase 1 scaffold. Privacy policy lives here in-app (Uptodown). */
@Composable
fun HelpScreen() {
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Help")
        Text("BADSHAH AI — Your Personal AI Assistant.\n\nVoice calls, chat, tools, memory — all native, no website.")
    }
}
