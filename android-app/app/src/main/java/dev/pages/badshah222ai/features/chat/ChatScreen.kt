package dev.pages.badshah222ai.features.chat

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import dev.pages.badshah222ai.R
import dev.pages.badshah222ai.domain.ChatMessage
import dev.pages.badshah222ai.features.home.ActionDialogs
import dev.pages.badshah222ai.features.home.HomeViewModel
import dev.pages.badshah222ai.ui.character.CharState
import dev.pages.badshah222ai.ui.orb.VoiceOrb
import dev.pages.badshah222ai.ui.theme.*

/** The text conversation, LIA-style: orb avatar header, violet user bubbles, panel assistant bubbles. */
@Composable
fun ChatScreen(vm: HomeViewModel, onBack: () -> Unit, onVoice: () -> Unit) {
    val ctx = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val state by vm.charState.collectAsState(); val level by vm.micLevel.collectAsState()
    val messages by vm.messages.collectAsState(); val error by vm.error.collectAsState()
    var text by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    LaunchedEffect(messages.size) { if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex) }

    val micPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok -> if (ok) vm.startListening() else vm.status.value = "Microphone permission denied. Open Permissions to enable it." }
    fun mic() { if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) vm.startListening() else micPerm.launch(Manifest.permission.RECORD_AUDIO) }

    Column(Modifier.fillMaxSize().statusBarsPadding().imePadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
            Image(painterResource(R.drawable.badshah_cutout), "BADSHAH", Modifier.size(42.dp).clip(CircleShape), contentScale = ContentScale.Crop)
            Column(Modifier.padding(start = 10.dp).weight(1f)) {
                Text("BADSHAH", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("Here to help", style = MaterialTheme.typography.bodySmall, color = Mute)
            }
            IconButton({ vm.newChat() }) { Icon(Icons.Default.Add, "New chat") }
        }
        HorizontalDivider(color = Panel2)

        error?.let { e ->
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp).background(Bad.copy(alpha = 0.15f), RoundedCornerShape(10.dp)).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(e, Modifier.weight(1f), color = Bad, style = MaterialTheme.typography.bodySmall)
                TextButton(vm::dismissError) { Text("Dismiss") }
            }
        }

        LazyColumn(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp), state = listState, verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(vertical = 10.dp)) {
            if (messages.isEmpty()) {
                item {
                    Column(Modifier.fillMaxWidth().padding(top = 48.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        VoiceOrb(CharState.Idle, 0f, vm.lowPower, Modifier.size(90.dp))
                        Text("Ask me anything, or tap the mic and speak.", color = Mute, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            items(messages.takeLast(60)) { m -> MessageBubble(m, vm, clipboard) }
        }

        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(text, { text = it }, Modifier.weight(1f), placeholder = { Text("Message BADSHAH…") }, maxLines = 4, shape = RoundedCornerShape(26.dp))
            IconButton({ if (state == CharState.Listening) vm.stopListening() else mic() }) {
                Icon(if (state == CharState.Listening) Icons.Default.Stop else Icons.Default.Mic, "Voice input", tint = if (state == CharState.Listening) Ok else VioletSoft)
            }
            FilledIconButton({ if (text.isNotBlank()) { vm.send(text); text = "" } }, colors = IconButtonDefaults.filledIconButtonColors(containerColor = Violet, contentColor = Color.White)) {
                Icon(Icons.Default.Send, "Send")
            }
        }
    }
    ActionDialogs(vm)
}

@Composable
private fun MessageBubble(m: ChatMessage, vm: HomeViewModel, clipboard: androidx.compose.ui.platform.ClipboardManager) {
    val mine = m.role == "user"
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start, verticalAlignment = Alignment.Top) {
        if (!mine) VoiceOrb(CharState.Idle, 0f, vm.lowPower, Modifier.padding(end = 6.dp, top = 2.dp).size(26.dp))
        Column(horizontalAlignment = if (mine) Alignment.End else Alignment.Start) {
            Box(
                Modifier.widthIn(max = 300.dp)
                    .background(
                        if (mine) Brush.horizontalGradient(listOf(VioletDeep, Violet)) else Brush.horizontalGradient(listOf(Panel, Panel)),
                        RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = if (mine) 18.dp else 4.dp, bottomEnd = if (mine) 4.dp else 18.dp),
                    )
                    .padding(horizontal = 12.dp, vertical = 9.dp),
            ) { Text(m.content, style = MaterialTheme.typography.bodyMedium, color = Color.White) }
            if (!mine) {
                Row {
                    IconButton({ clipboard.setText(AnnotatedString(m.content)) }, Modifier.size(32.dp)) { Icon(Icons.Default.ContentCopy, "Copy", Modifier.size(16.dp), tint = Mute) }
                    IconButton({ vm.replay(m.content) }, Modifier.size(32.dp)) { Icon(Icons.Default.VolumeUp, "Listen", Modifier.size(16.dp), tint = Mute) }
                }
            }
        }
    }
}
