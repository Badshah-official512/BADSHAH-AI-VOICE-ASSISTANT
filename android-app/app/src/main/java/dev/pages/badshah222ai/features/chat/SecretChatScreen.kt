package dev.pages.badshah222ai.features.chat

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.ScreenShare
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.pages.badshah222ai.BadshahApp
import dev.pages.badshah222ai.data.ai.FtFusion
import dev.pages.badshah222ai.data.ai.FtFusionClient
import dev.pages.badshah222ai.data.phone.PhoneResult
import dev.pages.badshah222ai.domain.ChatMessage
import dev.pages.badshah222ai.domain.CommandParser
import dev.pages.badshah222ai.domain.PhoneAction
import dev.pages.badshah222ai.features.settings.TopBar
import dev.pages.badshah222ai.ui.theme.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/**
 * SECRET CHAT — the heavy-work room (owner's design, 2026-11-17). Locked behind
 * the creator's password. Inside: the full FT Fusion model set does the heavy
 * lifting — complete coding projects (delivered as ZIP), deep information, and
 * phone/computer control commands executed on the spot. The front chat stays
 * light; this room is where the heavy work happens.
 */
class SecretChatViewModel(app: Application) : AndroidViewModel(app) {
    private val c = (app as BadshahApp).container
    val unlocked = MutableStateFlow(false)
    val wrongPw = MutableStateFlow(false)
    val messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val model = MutableStateFlow("fusion-flex")
    val busy = MutableStateFlow(false)
    val status = MutableStateFlow<String?>(null)

    fun tryUnlock(pw: String) {
        if (pw == SECRET_PASSWORD) { unlocked.value = true; wrongPw.value = false }
        else wrongPw.value = true
    }

    fun setModel(m: String) { model.value = m }

    private fun post(s: String) { messages.value = messages.value + ChatMessage("assistant", s) }

    fun send(text: String) {
        val t = text.trim(); if (t.isEmpty() || busy.value) return
        messages.value = messages.value + ChatMessage("user", t)
        // Computer/phone control: commands run right here, on the spot.
        val action = CommandParser.parse(t)
        if (action != null) {
            when {
                action.needsConfirmation -> post("Ye action (call / message) safety ke liye normal chat se confirm karke hoga, Boss.")
                action is PhoneAction.OpenBrowser || action is PhoneAction.OpenScreen ->
                    post("Us ke liye neeche wala Browser / Screen button hai, Boss — wahan full control milega.")
                else -> post(
                    when (val r = c.phone.execute(action)) {
                        is PhoneResult.Done -> r.message
                        is PhoneResult.Failed -> r.message
                        else -> "Ho gaya, Boss."
                    }
                )
            }
            return
        }
        busy.value = true
        viewModelScope.launch {
            try {
                val reply = FtFusionClient().chat("", model.value, SECRET_SYSTEM, messages.value.takeLast(14))
                post(reply.text)
            } catch (e: Exception) {
                post("⚠️ " + (e.message?.take(220) ?: "FT Fusion se jawab nahi aya — model badal kar dobara try karo, Boss."))
            } finally { busy.value = false }
        }
    }

    companion object {
        private const val SECRET_PASSWORD = "BADSHAH0325"
        private const val SECRET_SYSTEM =
            "You are BADSHAH SECRET — the heavy-work mode of BADSHAH-RANI AI, powered by FT Fusion. " +
            "Do heavy work properly: complete coding projects with EVERY file complete, each file in its own fenced code block " +
            "labeled with its filename (for example ```html index.html), deep and detailed information, and clear step-by-step " +
            "plans for browser and computer tasks. Never cut an answer short for length. Call the user Boss. " +
            "Reply in the user's language — Roman Urdu, Urdu or English, matching how they write."
    }
}

@Composable
fun SecretChatScreen(vm: SecretChatViewModel, onBack: () -> Unit, onNav: (String) -> Unit) {
    val unlocked by vm.unlocked.collectAsState()
    val ctx = LocalContext.current
    Column(Modifier.fillMaxSize().background(Ink)) {
        TopBar("Secret Chat", onBack)
        if (!unlocked) {
            var pw by remember { mutableStateOf("") }
            val wrong by vm.wrongPw.collectAsState()
            Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(Modifier.height(36.dp))
                Icon(Icons.Default.Lock, null, Modifier.size(54.dp), tint = Gold)
                Text("Ye area password se band hai", Modifier.padding(top = 14.dp), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    "Heavy work ka room — FT Fusion ke saare models, mukammal coding, ZIP files, gehri maloomat. Password sirf creator ke paas hai.",
                    Modifier.padding(top = 8.dp), color = Mute, style = MaterialTheme.typography.bodySmall,
                )
                OutlinedTextField(pw, { pw = it }, Modifier.fillMaxWidth().padding(top = 20.dp), label = { Text("Password") }, singleLine = true, visualTransformation = PasswordVisualTransformation())
                if (wrong) Text("Password ghalat hai.", Modifier.padding(top = 8.dp), color = Bad, style = MaterialTheme.typography.bodySmall)
                Button({ vm.tryUnlock(pw) }, Modifier.fillMaxWidth().padding(top = 14.dp)) { Text("Unlock") }
                Spacer(Modifier.weight(1f))
                Button(
                    { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/923251787969?text=" + Uri.encode("Assalam o Alaikum! Mujhe BADSHAH-RANI AI ke Secret Chat (heavy work) ka password chahiye.")))) },
                    Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF25D366)),
                ) { Text("WhatsApp — Creator se password lo", color = Color.White) }
            }
        } else {
            val msgs by vm.messages.collectAsState()
            val busy by vm.busy.collectAsState()
            val model by vm.model.collectAsState()
            val status by vm.status.collectAsState()
            var input by remember { mutableStateOf("") }
            Text("FT Fusion model:", Modifier.padding(start = 16.dp, top = 6.dp), color = Mute, style = MaterialTheme.typography.labelMedium)
            LazyRow(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(FtFusion.TEXT_MODELS) { m -> FilterChip(model == m, { vm.setModel(m) }, { Text(m.removePrefix("fusion-")) }) }
            }
            LazyColumn(Modifier.weight(1f).padding(horizontal = 14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (msgs.isEmpty()) item {
                    Text(
                        "Secret Chat khul gaya, Boss. 🔓\nHeavy kaam bolo — mukammal website/app ka code (ZIP ke saath), gehri maloomat, ya phone ka koi kaam (\"YouTube kholo\", \"torch chalao\").",
                        Modifier.background(Panel2, RoundedCornerShape(14.dp)).padding(14.dp), color = VioletSoft, style = MaterialTheme.typography.bodyMedium,
                    )
                }
                items(msgs) { m ->
                    val files = if (m.role == "assistant") extractCodeFiles(m.content) else emptyList()
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = if (m.role == "user") Alignment.End else Alignment.Start) {
                        Text(
                            m.content,
                            Modifier.fillMaxWidth(0.92f).background(if (m.role == "user") Violet else Panel2, RoundedCornerShape(14.dp)).padding(12.dp),
                            color = Color.White, style = MaterialTheme.typography.bodyMedium,
                        )
                        if (files.isNotEmpty()) TextButton({ vm.status.value = saveCodeZip(ctx, files) }) { Text("⬇ Download ZIP (${files.size} files)") }
                    }
                }
            }
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            status?.let { Text(it, Modifier.padding(horizontal = 16.dp, vertical = 4.dp), color = Ok, style = MaterialTheme.typography.bodySmall) }
            Row(Modifier.padding(horizontal = 14.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton({ onNav("browser") }) { Icon(Icons.Default.Public, null, Modifier.size(16.dp)); Text(" Browser", style = MaterialTheme.typography.bodySmall) }
                OutlinedButton({ onNav("screen") }) { Icon(Icons.Default.ScreenShare, null, Modifier.size(16.dp)); Text(" Screen", style = MaterialTheme.typography.bodySmall) }
            }
            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(input, { input = it }, Modifier.weight(1f), placeholder = { Text("Heavy kaam likho…") }, maxLines = 4)
                FilledIconButton({ val t = input; input = ""; vm.send(t) }, enabled = input.isNotBlank() && !busy) { Icon(Icons.AutoMirrored.Filled.Send, "Send") }
            }
        }
    }
}
