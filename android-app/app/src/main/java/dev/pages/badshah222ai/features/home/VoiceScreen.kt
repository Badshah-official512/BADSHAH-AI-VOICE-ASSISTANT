package dev.pages.badshah222ai.features.home

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import dev.pages.badshah222ai.ui.character.CharState
import dev.pages.badshah222ai.ui.orb.VoiceOrb
import dev.pages.badshah222ai.ui.theme.*
import kotlin.random.Random

/**
 * The full-screen voice conversation, modelled on the reference app's call screen:
 * a black-hole orb, "BADSHAH is listening / thinking / speaking", and
 * Mute / End / Minimize controls. Listening genuinely continues if you minimize,
 * because the speech engine lives in the shared HomeViewModel.
 */
@Composable
fun VoiceScreen(vm: HomeViewModel, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val state by vm.charState.collectAsState(); val level by vm.micLevel.collectAsState()
    val partial by vm.partial.collectAsState()

    val micPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok -> if (ok) vm.startListening() else vm.status.value = "Microphone permission denied. Open Permissions to enable it." }
    LaunchedEffect(Unit) {
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            if (state == CharState.Idle) vm.startListening()
        } else micPerm.launch(Manifest.permission.RECORD_AUDIO)
    }

    val stars = remember { List(70) { Offset(Random.nextFloat(), Random.nextFloat()) to Random.nextFloat() } }
    val driftT = rememberInfiniteTransition(label = "drift")
    val drift by driftT.animateFloat(0f, 1f, infiniteRepeatable(tween(14000, easing = LinearEasing), RepeatMode.Restart), label = "drift")
    val floatY by driftT.animateFloat(-10f, 10f, infiniteRepeatable(tween(3000, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "floatY")
    val rgb = rememberRgbColor()
    var burst by remember { mutableStateOf(0) }
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Ink, Panel2, Ink))).statusBarsPadding().navigationBarsPadding()) {
        AmbientLights(Modifier.fillMaxSize())
        Canvas(Modifier.fillMaxSize()) {
            stars.forEach { (p, a) ->
                val y = (p.y + drift * (0.03f + a * 0.06f)) % 1f
                drawCircle(Color.White.copy(alpha = 0.10f + a * 0.15f), radius = 1.2f + a * 1.6f, center = Offset(p.x * size.width, y * size.height))
            }
        }
        Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("BADSHAH", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                Spacer(Modifier.weight(1f))
                IconButton(onBack) { Icon(Icons.Default.KeyboardArrowDown, "Minimize") }
            }
            Spacer(Modifier.weight(0.7f))
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                TapLightBurst(burst, rgb, Modifier.size(300.dp))
                VoiceOrb(state, level, vm.lowPower, Modifier.size(280.dp).offset(y = floatY.dp).clip(CircleShape).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { burst++ })
            }
            Spacer(Modifier.height(18.dp))
            Text(
                when (state) {
                    CharState.Listening -> "BADSHAH is listening…"
                    CharState.Thinking -> "BADSHAH is thinking…"
                    CharState.Speaking -> "BADSHAH is speaking…"
                    CharState.Working -> "BADSHAH is working…"
                    CharState.Error -> "Something went wrong"
                    CharState.Idle -> "BADSHAH is ready"
                },
                Modifier.fillMaxWidth(), textAlign = TextAlign.Center, fontStyle = FontStyle.Italic,
                style = MaterialTheme.typography.titleMedium,
            )
            Text("Keeps listening if you leave this screen.", Modifier.fillMaxWidth().padding(top = 4.dp), textAlign = TextAlign.Center, color = Mute, style = MaterialTheme.typography.bodySmall)
            if (partial.isNotEmpty()) Text(partial, Modifier.fillMaxWidth().padding(top = 10.dp), textAlign = TextAlign.Center, color = VioletSoft, style = MaterialTheme.typography.bodyMedium, maxLines = 2)
            Spacer(Modifier.weight(1f))
            Row(Modifier.fillMaxWidth().padding(bottom = 18.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.Top) {
                ControlButton(Icons.Default.MicOff, "Mute", Panel2, Color.White) { vm.stopListening() }
                ControlButton(Icons.Default.CallEnd, "End", Bad, Color.White, big = true) { vm.stopListening(); vm.stopSpeaking(); onBack() }
                ControlButton(Icons.Default.KeyboardArrowDown, "Minimize", Panel2, Color.White) { onBack() }
            }
        }
    }
    ActionDialogs(vm)
}

@Composable
private fun ControlButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, bg: Color, tint: Color, big: Boolean = false, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        FilledIconButton(onClick, Modifier.size(if (big) 72.dp else 60.dp), colors = IconButtonDefaults.filledIconButtonColors(containerColor = bg, contentColor = tint), shape = CircleShape) {
            Icon(icon, label, Modifier.size(if (big) 30.dp else 24.dp))
        }
        Text(label, style = MaterialTheme.typography.bodySmall, color = Mute)
    }
}
