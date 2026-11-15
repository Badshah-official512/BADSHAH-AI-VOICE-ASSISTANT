package dev.pages.badshah222ai.features.home

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.pages.badshah222ai.BadshahApp
import dev.pages.badshah222ai.data.phone.PhoneResult
import dev.pages.badshah222ai.data.ai.*
import dev.pages.badshah222ai.domain.*
import dev.pages.badshah222ai.features.auth.SessionViewModel
import dev.pages.badshah222ai.ui.character.BadshahCharacter
import dev.pages.badshah222ai.ui.character.CharState
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
    val status = MutableStateFlow("Tap the mic and speak, or type below.")
    val pending = MutableStateFlow<PhoneAction?>(null)        // waiting for user confirmation (calls / messages)
    val needPermission = MutableStateFlow<Pair<String, String>?>(null)
    val pickContact = MutableStateFlow(false)
    val working = MutableStateFlow(false)
    val lowPower get() = c.store.lowPower
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
                val reply = c.ai.chat(messages.value)
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
        c.tts.speak(text)?.let { warn -> status.value = warn }
    }
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

@Composable
fun HomeScreen(session: SessionViewModel, vm: HomeViewModel, onNav: (String) -> Unit) {
    val ctx = LocalContext.current
    val state by vm.charState.collectAsState(); val level by vm.micLevel.collectAsState()
    val messages by vm.messages.collectAsState(); val status by vm.status.collectAsState(); val partial by vm.partial.collectAsState()
    val pending by vm.pending.collectAsState(); val needPerm by vm.needPermission.collectAsState(); val pick by vm.pickContact.collectAsState()
    val maintenance by session.maintenance.collectAsState(); val announcements by session.announcements.collectAsState(); val cloud by session.cloudReady.collectAsState()
    var text by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    LaunchedEffect(messages.size) { if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex) }

    val micPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok -> if (ok) vm.startListening() else vm.status.value = "Microphone permission denied. Open Permissions to enable it." }
    val contactsPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok -> if (ok) vm.retryLastAction() else vm.status.value = "Without contacts access, say the phone number instead." }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickContact()) { uri ->
        vm.pickContact.value = false
        if (uri != null) ctx.contentResolver.query(uri, arrayOf(android.provider.ContactsContract.Contacts.DISPLAY_NAME), null, null, null)?.use { if (it.moveToFirst()) vm.contactPicked(it.getString(0)) }
    }
    LaunchedEffect(pick) { if (pick) picker.launch(null) }
    LaunchedEffect(needPerm) { needPerm?.let { vm.needPermission.value = null; contactsPerm.launch(it.first) } }
    fun mic() { if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) vm.startListening() else micPerm.launch(Manifest.permission.RECORD_AUDIO) }

    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("BADSHAH AI", style = MaterialTheme.typography.titleLarge, color = Gold, modifier = Modifier.padding(start = 8.dp).weight(1f))
            IconButton({ onNav("history") }) { Icon(Icons.Default.History, "History") }
            IconButton({ onNav("help") }) { Icon(Icons.Default.Help, "Help") }
            IconButton({ onNav("about") }) { Icon(Icons.Default.Info, "About") }
            IconButton({ onNav("settings") }) { Icon(Icons.Default.Settings, "Settings") }
        }
        maintenance?.let { Banner(it, Bad) }
        announcements.firstOrNull()?.let { Banner("${it.first}: ${it.second}", Blue) }
        if (!cloud) Banner("Cloud sync is off (chat is saved on this phone). See Help > Troubleshooting.", Mute)

        BadshahCharacter(state, level, vm.lowPower, Modifier.fillMaxWidth().weight(0.9f, fill = false).heightIn(min = 220.dp, max = 330.dp).align(Alignment.CenterHorizontally))
        Text(if (partial.isNotEmpty()) partial else status, Modifier.fillMaxWidth().padding(horizontal = 24.dp), textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium, color = if (state == CharState.Error) Bad else Mute, maxLines = 3)

        LazyColumn(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp), state = listState, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(messages.takeLast(40)) { m ->
                val mine = m.role == "user"
                Row(Modifier.fillMaxWidth(), horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start) {
                    Text(m.content, Modifier.widthIn(max = 300.dp).background(if (mine) Panel2 else Color(0xFF3A2C0A), RoundedCornerShape(14.dp)).padding(10.dp), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(text, { text = it }, Modifier.weight(1f), placeholder = { Text("Ask or command...") }, maxLines = 3)
            IconButton({ if (text.isNotBlank()) { vm.send(text); text = "" } }) { Icon(Icons.Default.Send, "Send") }
            FilledIconButton({ if (state == CharState.Listening) vm.stopListening() else mic() }, colors = IconButtonDefaults.filledIconButtonColors(containerColor = if (state == CharState.Listening) Ok else Gold, contentColor = Ink)) {
                Icon(if (state == CharState.Listening) Icons.Default.Stop else Icons.Default.Mic, "Microphone")
            }
        }
    }

    pending?.let { a ->
        AlertDialog(onDismissRequest = vm::cancelPending, title = { Text("Confirm") },
            text = { Text(when (a) { is PhoneAction.Call -> "Open the dialer for ${a.target}? You still press the call button."
                is PhoneAction.Sms -> "Prepare this SMS to ${a.target}?\n\n\"${a.message}\"\n\nYou press send yourself."
                is PhoneAction.WhatsAppMessage -> "Prepare this WhatsApp message to ${a.target}?\n\n\"${a.message}\"\n\nYou press send yourself."
                else -> "Continue?" }) },
            confirmButton = { TextButton(vm::confirm) { Text("Yes, continue") } }, dismissButton = { TextButton(vm::cancelPending) { Text("Cancel") } })
    }
}

@Composable private fun Banner(text: String, color: Color) {
    Text(text, Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp).background(color.copy(alpha = 0.18f), RoundedCornerShape(10.dp)).padding(8.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onBackground)
}

