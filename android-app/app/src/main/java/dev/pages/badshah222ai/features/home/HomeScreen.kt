package dev.pages.badshah222ai.features.home

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.pages.badshah222ai.BadshahApp
import dev.pages.badshah222ai.R
import dev.pages.badshah222ai.data.phone.PhoneResult
import dev.pages.badshah222ai.data.ai.*
import dev.pages.badshah222ai.domain.*
import dev.pages.badshah222ai.features.auth.SessionViewModel
import dev.pages.badshah222ai.ui.character.CharState
import dev.pages.badshah222ai.ui.orb.VoiceOrb
import dev.pages.badshah222ai.ui.theme.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class HomeViewModel(app: Application) : AndroidViewModel(app) {
    private val c = (app as BadshahApp).container
    private val uid get() = c.auth.current?.uid.orEmpty()

    val messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val busy = MutableStateFlow(false)
    val error = MutableStateFlow<String?>(null)
    val partial = MutableStateFlow("")
    val status = MutableStateFlow("Tap the orb and speak, or type below.")
    val pending = MutableStateFlow<PhoneAction?>(null)        // waiting for user confirmation (calls / messages)
    val needPermission = MutableStateFlow<Pair<String, String>?>(null)
    val pickContact = MutableStateFlow(false)
    val working = MutableStateFlow(false)
    val feeling = MutableStateFlow(Feelings.fromId("warm"))
    val lowPower get() = c.store.lowPower
    /** GF mode: a girls voice mode or the Girlfriend personality swaps the hero to the girl assistant. */
    val girlMode: Boolean get() = c.store.personalityId == "girlfriend" || VoiceModes.fromId(c.store.voiceModeId)?.female == true
    val listening = c.speech.listening; val micLevel = c.speech.level; val speaking = c.tts.speaking
    private var convoId: String? = null
    private var lastAction: PhoneAction? = null

    val charState: StateFlow<CharState> = combine(listening, speaking, busy, working, error) { l, s, b, w, e ->
        when { e != null -> CharState.Error; l -> CharState.Listening; b -> CharState.Thinking; w -> CharState.Working; s -> CharState.Speaking; else -> CharState.Idle }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), CharState.Idle)

    init { if (uid.isNotEmpty()) messages.value = c.chats.load(uid) }

    fun startListening() {
        error.value = null; c.tts.stop(); partial.value = ""; status.value = "Listening..."
        c.speech.start(c.store.language, { partial.value = it }, { partial.value = ""; send(it) }, { error.value = it; status.value = it })
    }
    fun stopListening() = c.speech.stop()
    fun dismissError() { error.value = null }

    fun send(text: String) {
        val t = text.trim(); if (t.isEmpty() || busy.value) return
        error.value = null
        add(ChatMessage("user", t))
        val action = CommandParser.parse(t)
        if (action != null) { handleAction(action); return }
        viewModelScope.launch {
            busy.value = true; status.value = "Thinking..."
            try {
                val feel = c.store.feelingId?.let { Feelings.fromId(it) } ?: Feelings.detect(t)
                feeling.value = feel
                val reply = c.ai.chat(
                    messages.value,
                    Personalities.fromId(c.store.personalityId).prompt,
                    Brains.fromId(c.store.brainId).prompt,
                    feel.prompt,
                )
                add(ChatMessage("assistant", reply.text)); status.value = "Speaking..."
                syncToCloud(t, reply)
                busy.value = false
                speak(reply.text)
            } catch (e: AiException) { error.value = e.userMessage; status.value = e.userMessage }
            catch (e: Exception) { error.value = "Something went wrong. Please try again."; status.value = error.value!! }
            finally { busy.value = false }
        }
    }

    private fun handleAction(a: PhoneAction) {
        if (a.needsConfirmation) { pending.value = a; status.value = "Please confirm."; return }
        runAction(a)
    }
    fun confirm() { val a = pending.value ?: return; pending.value = null; runAction(a) }
    fun cancelPending() { pending.value = null; reply("Cancelled. Nothing was sent.") }

    private fun runAction(a: PhoneAction) {
        lastAction = a; working.value = true
        when (val r = c.phone.execute(a)) {
            is PhoneResult.Done -> reply(r.message)
            is PhoneResult.Failed -> { error.value = r.message; add(ChatMessage("assistant", r.message)); status.value = r.message }
            is PhoneResult.NeedPermission -> { needPermission.value = r.permission to r.why; reply(r.why) }
            PhoneResult.LaunchContactPicker -> { pickContact.value = true; status.value = "Pick a contact." }
        }
        viewModelScope.launch { kotlinx.coroutines.delay(900); working.value = false }
    }
    fun retryLastAction() { lastAction?.let { runAction(it) } }
    fun contactPicked(name: String) = reply("Selected contact: $name. Say \"call $name\" or \"send WhatsApp to $name saying ...\".")

    private fun reply(text: String) { add(ChatMessage("assistant", text)); status.value = text; viewModelScope.launch { speak(text) } }
    private suspend fun speak(text: String) {
        if (!c.store.speakReplies) return
        val f = feeling.value
        c.tts.speak(text, null, f.pitchMul, f.speedMul)?.let { warn -> status.value = warn }
    }
    /** Speak one message aloud on demand (the speaker button on a chat bubble). */
    fun replay(text: String) { c.tts.stop(); viewModelScope.launch { c.tts.speak(text) } }
    private fun add(m: ChatMessage) { messages.value = messages.value + m; if (uid.isNotEmpty()) c.chats.save(uid, messages.value) }

    private suspend fun syncToCloud(userText: String, r: AiReply) {
        if (uid.isEmpty()) return
        runCatching {
            val id = convoId ?: c.cloud.createConversation(uid, userText, r.provider.id, r.model).also { convoId = it }
            c.cloud.addMessage(uid, id, "user", userText, null, null)
            c.cloud.addMessage(uid, id, "assistant", r.text, r.provider.id, r.model)
        } // best effort: local history is always kept even when cloud sync is unavailable
    }
    fun newChat() { convoId = null; messages.value = emptyList(); if (uid.isNotEmpty()) c.chats.clear(uid); c.tts.stop() }
    fun stopSpeaking() = c.tts.stop()
    override fun onCleared() { c.speech.stop() }
}

/** Short human label for the assistant's current state, LIA-style. */
fun stateLabel(state: CharState, status: String, partial: String): String = when {
    partial.isNotEmpty() -> partial
    state == CharState.Listening -> "I'm listening…"
    state == CharState.Speaking -> "Speaking…"
    state == CharState.Thinking -> "Thinking…"
    state == CharState.Working -> "Working…"
    else -> status
}

/**
 * Everything a phone action can pop up: the confirmation dialog for calls/messages,
 * the contacts-permission request and the contact picker. Include this once in any
 * screen that can trigger actions (Home, Chat, Voice, Quick actions).
 */
@Composable
fun ActionDialogs(vm: HomeViewModel) {
    val ctx = LocalContext.current
    val pending by vm.pending.collectAsState(); val needPerm by vm.needPermission.collectAsState(); val pick by vm.pickContact.collectAsState()
    val contactsPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok -> if (ok) vm.retryLastAction() else vm.status.value = "Without contacts access, say the phone number instead." }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickContact()) { uri ->
        vm.pickContact.value = false
        if (uri != null) ctx.contentResolver.query(uri, arrayOf(android.provider.ContactsContract.Contacts.DISPLAY_NAME), null, null, null)?.use { if (it.moveToFirst()) vm.contactPicked(it.getString(0)) }
    }
    LaunchedEffect(pick) { if (pick) picker.launch(null) }
    LaunchedEffect(needPerm) { needPerm?.let { vm.needPermission.value = null; contactsPerm.launch(it.first) } }
    pending?.let { a ->
        AlertDialog(onDismissRequest = vm::cancelPending, title = { Text("Confirm") },
            text = { Text(when (a) { is PhoneAction.Call -> "Open the dialer for ${a.target}? You still press the call button."
                is PhoneAction.Sms -> "Prepare this SMS to ${a.target}?\n\n\"${a.message}\"\n\nYou press send yourself."
                is PhoneAction.WhatsAppMessage -> "Prepare this WhatsApp message to ${a.target}?\n\n\"${a.message}\"\n\nYou press send yourself."
                else -> "Continue?" }) },
            confirmButton = { TextButton(vm::confirm) { Text("Yes, continue") } }, dismissButton = { TextButton(vm::cancelPending) { Text("Cancel") } })
    }
}

@Composable
fun HomeScreen(session: SessionViewModel, vm: HomeViewModel, onNav: (String) -> Unit) {
    val state by vm.charState.collectAsState(); val level by vm.micLevel.collectAsState()
    val status by vm.status.collectAsState(); val partial by vm.partial.collectAsState()
    val feeling by vm.feeling.collectAsState()
    val maintenance by session.maintenance.collectAsState(); val announcements by session.announcements.collectAsState(); val cloud by session.cloudReady.collectAsState()
    var text by remember { mutableStateOf("") }

    Column(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Ink, Panel, Ink))).statusBarsPadding().verticalScroll(rememberScrollState())) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton({ onNav("about") }) { Icon(Icons.Default.Menu, "About") }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("BADSHAH AI", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                Text("YOUR AI ASSISTANT", style = MaterialTheme.typography.labelSmall, color = Mute, letterSpacing = 3.sp)
            }
            IconButton({ onNav("settings") }) { Icon(Icons.Default.Settings, "Settings") }
        }
        maintenance?.let { Banner(it, Bad) }
        announcements.firstOrNull()?.let { Banner("${it.first}: ${it.second}", Blue) }
        if (!cloud) Banner("Cloud sync is off (chat is saved on this phone). See Help > Troubleshooting.", Mute)

        HeroPortrait(vm.girlMode, feeling.composeColor, vm.lowPower)

        Text(stateLabel(state, status, partial), Modifier.fillMaxWidth().padding(horizontal = 24.dp), textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium, color = if (state == CharState.Error) Bad else Mute, maxLines = 2)

        Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.Center) {
            Row(
                Modifier.clip(RoundedCornerShape(50)).background(Panel.copy(alpha = 0.85f)).border(1.dp, feeling.composeColor.copy(alpha = 0.55f), RoundedCornerShape(50)).padding(horizontal = 10.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(feeling.composeColor))
                Text("Feeling · ${feeling.label}", style = MaterialTheme.typography.labelMedium, color = feeling.composeColor)
            }
        }

        Box(Modifier.fillMaxWidth().padding(vertical = 6.dp), contentAlignment = Alignment.Center) {
            VoiceOrb(state, level, vm.lowPower, Modifier.size(118.dp).clip(CircleShape).clickable { onNav("voice") })
        }

        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(text, { text = it }, Modifier.weight(1f), placeholder = { Text("Ask me anything…") }, maxLines = 3, shape = RoundedCornerShape(28.dp))
            FilledIconButton({ if (text.isNotBlank()) { vm.send(text); text = ""; onNav("chat") } }, colors = IconButtonDefaults.filledIconButtonColors(containerColor = Violet, contentColor = Color.White)) {
                Icon(Icons.Default.Send, "Send")
            }
        }

        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            QuickCard(Modifier.weight(1f), Icons.Default.Chat, "Chat", "Get instant answers") { onNav("chat") }
            QuickCard(Modifier.weight(1f), Icons.Default.Call, "Voice Call", "Talk to BADSHAH") { onNav("voice") }
            QuickCard(Modifier.weight(1f), Icons.Default.Bolt, "Quick Actions", "Phone control") { onNav("quick") }
        }
        Spacer(Modifier.height(12.dp))
    }
    ActionDialogs(vm)
}

@Composable
private fun HeroPortrait(girl: Boolean, accent: Color, lowPower: Boolean) {
    val transition = rememberInfiniteTransition(label = "hero")
    val floatY by transition.animateFloat(-6f, 6f, infiniteRepeatable(tween(2600, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "float")
    Box(Modifier.fillMaxWidth().height(248.dp), contentAlignment = Alignment.Center) {
        Box(Modifier.size(240.dp).clip(CircleShape).background(Brush.radialGradient(listOf(accent.copy(alpha = 0.35f), Color.Transparent))))
        Box(Modifier.size(230.dp).border(1.5.dp, accent.copy(alpha = 0.5f), CircleShape))
        Box(Modifier.size(254.dp).border(1.dp, CyanAcc.copy(alpha = 0.22f), CircleShape))
        Image(painterResource(if (girl) R.drawable.girl_assistant else R.drawable.badshah_cutout), if (girl) "AI assistant" else "BADSHAH", Modifier.size(214.dp).offset(y = if (lowPower) 0.dp else floatY.dp).clip(CircleShape), contentScale = ContentScale.Crop)
        Text(if (girl) "Hi, I'm\nAISHA" else "Hi, I'm\nBADSHAH", Modifier.align(Alignment.CenterStart).padding(start = 10.dp), color = VioletSoft, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        Text("Always\nhere for you", Modifier.align(Alignment.CenterEnd).padding(end = 10.dp), color = VioletSoft, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, textAlign = TextAlign.End)
    }
}

@Composable
private fun QuickCard(modifier: Modifier, icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, sub: String, onClick: () -> Unit) {
    Card(modifier.clickable { onClick() }, colors = CardDefaults.cardColors(containerColor = Panel.copy(alpha = 0.88f)), shape = RoundedCornerShape(18.dp), border = BorderStroke(1.dp, Violet.copy(alpha = 0.28f))) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Icon(icon, title, tint = VioletSoft)
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(sub, style = MaterialTheme.typography.bodySmall, color = Mute)
        }
    }
}

@Composable
fun Banner(text: String, color: Color) {
    Text(text, Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp).background(color.copy(alpha = 0.18f), RoundedCornerShape(10.dp)).padding(8.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onBackground)
}
