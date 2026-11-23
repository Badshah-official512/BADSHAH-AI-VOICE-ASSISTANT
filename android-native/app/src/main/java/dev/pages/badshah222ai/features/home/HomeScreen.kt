package dev.pages.badshah222ai.features.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.pages.badshah222ai.R
import dev.pages.badshah222ai.ui.theme.Gold
import dev.pages.badshah222ai.ui.theme.Mute

/** Home — Phase 1 scaffold. Hero half character + quick actions. */
@Composable
fun HomeScreen(
    onChat: () -> Unit,
    onCall: () -> Unit,
    onTools: () -> Unit,
    onMemory: () -> Unit,
    onSettings: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxSize().padding(24.dp),
    ) {
        Spacer(Modifier.height(16.dp))
        Image(
            painter = painterResource(R.drawable.char_half),
            contentDescription = "Badshah",
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(200.dp),
        )
        Text("BADSHAH AI", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Gold)
        Text("Your Personal AI Assistant", color = Mute)
        Spacer(Modifier.height(32.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            Button(onClick = onCall, modifier = Modifier.weight(1f)) { Text("Voice Call") }
            Button(onClick = onChat, modifier = Modifier.weight(1f)) { Text("Chat") }
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(onClick = onTools, modifier = Modifier.weight(1f)) { Text("Tools") }
            OutlinedButton(onClick = onMemory, modifier = Modifier.weight(1f)) { Text("Memory") }
        }
        Spacer(Modifier.height(12.dp))
        OutlinedButton(onClick = onSettings, modifier = Modifier.fillMaxWidth()) { Text("Settings") }
    }
}
