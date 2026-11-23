package dev.pages.badshah222ai.features.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.pages.badshah222ai.data.api.ApiResult
import dev.pages.badshah222ai.data.api.BadshahApi
import dev.pages.badshah222ai.ui.theme.LocalChatTheme
import kotlinx.coroutines.launch

private data class Msg(val role: String, val text: String)

/**
 * Chat — Phase 1 scaffold with real /api/chat wiring.
 * Bubbles follow the selected chat theme (THEMES.md): user bubbles = Accent,
 * AI bubbles = Surface. Theme applies instantly, no restart.
 * Phase 2 adds: history (Supabase conversations/messages), streaming, images,
 * guest mode, secret chat.
 */
@Composable
fun ChatScreen(api: BadshahApi, phoneExecutor: dev.pages.badshah222ai.data.phone.PhoneExecutor, store: dev.pages.badshah222ai.core.SecureStore, onBack: () -> Unit) {
    val theme = LocalChatTheme.current
    val context = androidx.compose.ui.platform.LocalContext.current
    val messages = remember { mutableStateListOf<Msg>() }
    var input by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // Feature #10: sawal ki photo -> hal (vision via the user's BYOK Gemini key).
    val photoLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.GetContent(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val key = store.geminiKey()
        if (key == null) {
            messages += Msg("ai", "Photo sawal ke liye pehle Settings mein apni Gemini API key add karo (free, aistudio.google.com).")
            return@rememberLauncherForActivityResult
        }
        val question = input.trim().ifBlank {
            "Is photo mein jo sawal hai, usay step by step hal karo aur asaan alfaaz mein samjhao."
        }
        input = ""
        messages += Msg("user", "[Photo] $question")
        busy = true
        scope.launch {
            val b64 = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                runCatching {
                    val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                        ?: return@runCatching null
                    val bmp = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                        ?: return@runCatching null
                    val scale = minOf(1f, 1024f / maxOf(bmp.width, bmp.height))
                    val small = if (scale < 1f) {
                        android.graphics.Bitmap.createScaledBitmap(
                            bmp, (bmp.width * scale).toInt(), (bmp.height * scale).toInt(), true,
                        )
                    } else {
                        bmp
                    }
                    val baos = java.io.ByteArrayOutputStream()
                    small.compress(android.graphics.Bitmap.CompressFormat.JPEG, 80, baos)
                    android.util.Base64.encodeToString(baos.toByteArray(), android.util.Base64.NO_WRAP)
                }.getOrNull()
            }
            if (b64 == null) {
                messages += Msg("ai", "Photo parh nahi sakay — dobara try karo.")
            } else {
                when (val r = api.visionAnswer(key, b64, question)) {
                    is ApiResult.Ok -> messages += Msg("ai", r.value.take(4000))
                    is ApiResult.Err -> messages += Msg("ai", "Error: ${r.message}")
                }
            }
            busy = false
        }
    }

    Box(
        Modifier.fillMaxSize().background(
            Brush.verticalGradient(listOf(theme.bgTop, theme.bgBottom)),
        ),
    ) {
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            Text("Chat", color = theme.text)
            Spacer(Modifier.height(8.dp))
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f).fillMaxWidth(),
            ) {
                items(messages) { m ->
                    val isUser = m.role == "user"
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start) {
                        Text(
                            m.text,
                            color = if (isUser) Color(0xFF14092E) else theme.text,
                            modifier = Modifier
                                .widthIn(max = 300.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(if (isUser) theme.accent else theme.surface)
                                .padding(12.dp),
                        )
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Button(
                    enabled = !busy,
                    onClick = { photoLauncher.launch("image/*") },
                ) { Text("Photo") }
                Spacer(Modifier.width(8.dp))
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    label = { Text("Type a message…", color = theme.subText) },
                    modifier = Modifier.weight(1f),
                )
                Button(
                    enabled = input.isNotBlank() && !busy,
                    onClick = {
                        val q = input.trim()
                        input = ""
                        messages += Msg("user", q)
                        busy = true
                        scope.launch {
                            // Phone control first (feature: bol kar phone control) —
                            // "youtube kholo", "ali ko call karo", "5 minute ka timer".
                            val action = dev.pages.badshah222ai.domain.CommandParser.parse(q)
                            if (action != null) {
                                val r = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                    phoneExecutor.execute(action)
                                }
                                messages += Msg(
                                    "ai",
                                    when (r) {
                                        is dev.pages.badshah222ai.data.phone.PhoneResult.Done -> r.message
                                        is dev.pages.badshah222ai.data.phone.PhoneResult.Failed -> r.message
                                        is dev.pages.badshah222ai.data.phone.PhoneResult.NeedPermission ->
                                            "Permission needed: ${r.why}. Grant it in the permission tour or Settings."
                                        dev.pages.badshah222ai.data.phone.PhoneResult.LaunchContactPicker ->
                                            "Pick a contact from your contacts app."
                                    },
                                )
                                busy = false
                                return@launch
                            }
                            when (val r = api.chatReply("gemini-3.8-flash", q)) {
                                is ApiResult.Ok -> messages += Msg("ai", r.value.take(4000))
                                is ApiResult.Err -> messages += Msg("ai", "Error: ${r.message}")
                            }
                            busy = false
                        }
                    },
                ) { Text("Send") }
            }
        }
    }
}
