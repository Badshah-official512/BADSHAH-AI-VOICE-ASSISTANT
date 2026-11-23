package dev.pages.badshah222ai.features.call

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.pages.badshah222ai.data.live.CallPhase
import dev.pages.badshah222ai.data.live.LiveCallManager
import dev.pages.badshah222ai.ui.components.HeroCallStage
import dev.pages.badshah222ai.ui.theme.Bad
import dev.pages.badshah222ai.ui.theme.Gold
import dev.pages.badshah222ai.ui.theme.Ok

/**
 * Call screen — approved hero design (mock-call-fit.jpg).
 * HeroCallStage: full-body character inside the platform circle.
 * Controls: End (red — the ONLY way to end, besides the AI ending it itself),
 * Minimize (2e — chat screen, call keeps running, floating circle returns),
 * mic mute, front/back camera switch (camera auto-ON at call start, 2d).
 * Phase 2/3 wires LiveCallManager to the audited LiveSession + CallService.
 */
@Composable
fun CallScreen(
    call: LiveCallManager,
    onMinimized: () -> Unit,
    onEnded: () -> Unit,
) {
    val state by call.state.collectAsState()
    // Entering the Call screen starts the call (fresh call after ENDED too).
    androidx.compose.runtime.LaunchedEffect(Unit) {
        if (state.phase == CallPhase.IDLE || state.phase == CallPhase.ENDED) call.startCall()
    }
    Box(Modifier.fillMaxSize()) {
        HeroCallStage(Modifier.fillMaxSize())
        // Camera PiP: front camera auto-on at call start (tour asked once).
        if (state.phase == CallPhase.LIVE || state.phase == CallPhase.CONNECTING) {
            dev.pages.badshah222ai.ui.components.CameraPiP(
                front = state.frontCamera,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 56.dp, end = 16.dp)
                    .size(width = 96.dp, height = 136.dp)
                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(16.dp)),
            )
        }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxSize().padding(16.dp),
        ) {
            // Status chip (English, like the web call screen).
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("●", color = if (state.phase == CallPhase.LIVE) Ok else Gold, fontSize = 14.sp)
                Text(
                    when (state.phase) {
                        CallPhase.CONNECTING -> "Connecting…"
                        CallPhase.LIVE -> "LIVE"
                        CallPhase.MINIMIZED -> "Call running in background"
                        CallPhase.ENDED -> "Call ended"
                        CallPhase.IDLE -> "Ready"
                    },
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                )
                if (state.statusText.isNotBlank() && state.phase != CallPhase.LIVE) {
                    Text(state.statusText, color = Color.White.copy(alpha = 0.8f))
                }
            }
            Spacer(Modifier.weight(1f))
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Button(onClick = { call.toggleMute() }, modifier = Modifier.weight(1f)) {
                    Text(if (state.isMuted) "Unmute" else "Mute")
                }
                Button(onClick = { call.switchCamera() }, modifier = Modifier.weight(1f)) {
                    Text(if (state.frontCamera) "Back cam" else "Front cam")
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Button(onClick = onMinimized, modifier = Modifier.weight(1f)) {
                    Text("Minimize")
                }
                Button(
                    onClick = onEnded,
                    colors = ButtonDefaults.buttonColors(containerColor = Bad),
                    modifier = Modifier.weight(1f),
                ) { Text("End call", color = Color.White) }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}
