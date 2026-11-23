package dev.pages.badshah222ai.features.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import dev.pages.badshah222ai.Container
import dev.pages.badshah222ai.ui.theme.ChatThemes
import dev.pages.badshah222ai.ui.theme.Mute
import dev.pages.badshah222ai.ui.theme.Ok

/** Gemini Live voices (owner's curated set, 2026-11-17): BF default Charon, GF default Aoede. */
private val MALE_VOICES = listOf("Charon", "Fenrir", "Puck", "Orus", "Algieba", "Achird")
private val FEMALE_VOICES = listOf("Aoede", "Kore", "Zephyr", "Leda", "Sulafat", "Callirrhoe")

/**
 * Settings — WIRED (Phase 2).
 * - Chat themes: all 5 (THEMES.md), switch applies instantly + persists.
 * - BYOK keys: the user's own Gemini key (voice calls REQUIRE it) + optional
 *   ElevenLabs key. Stored ONLY in the encrypted store.
 * - Voice picker: curated Gemini Live voices, default Charon.
 * - Language: English -> Roman Urdu -> Urdu (persisted).
 * - Avatar rule stays: poster preset = centre cut only, never the whole poster.
 */
@Composable
fun SettingsScreen(container: Container, onBack: () -> Unit) {
    val store = container.secureStore
    val themeState = container.chatTheme
    Column(
        Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Settings")
        Text("Chat theme", color = Color.White)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(ChatThemes) { t ->
                val selected = themeState.theme.id == t.id
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(t.surface)
                        .border(
                            width = if (selected) 2.dp else 1.dp,
                            color = if (selected) t.accent else Color.Transparent,
                            shape = RoundedCornerShape(12.dp),
                        )
                        .clickable { container.selectChatTheme(t.id) }
                        .padding(10.dp),
                ) {
                    Box(Modifier.size(44.dp).clip(CircleShape).background(t.accent))
                    Spacer(Modifier.height(6.dp))
                    Text(t.name, color = t.text)
                }
            }
        }

        Text("Voice (Gemini Live)", color = Color.White)
        Text("Boy voices", color = Mute)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(MALE_VOICES) { v -> VoiceChip(v, store.liveVoice == v) { store.liveVoice = v } }
        }
        Text("Girl voices", color = Mute)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(FEMALE_VOICES) { v -> VoiceChip(v, store.liveVoice == v) { store.liveVoice = v } }
        }

        Text("Language", color = Color.White)
        var lang by remember { mutableStateOf(store.appLanguage) }
        OutlinedButton(
            onClick = {
                lang = when (lang) {
                    "en" -> "roman"
                    "roman" -> "urdu"
                    else -> "en"
                }
                store.appLanguage = lang
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                when (lang) {
                    "roman" -> "Roman Urdu"
                    "urdu" -> "Urdu"
                    else -> "English"
                },
            )
        }

        Text("My API keys (BYOK)", color = Color.White)
        Text(
            "Voice calls use YOUR OWN free Gemini key. Get one at aistudio.google.com — " +
                "the key stays encrypted on this phone and is sent only to Google.",
            color = Mute,
        )
        var gemini by remember { mutableStateOf(store.geminiKey() ?: "") }
        var eleven by remember { mutableStateOf(store.elevenKey() ?: "") }
        var saved by remember { mutableStateOf(false) }
        OutlinedTextField(
            value = gemini, onValueChange = { gemini = it; saved = false },
            label = { Text("Gemini API key (required for calls)") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = eleven, onValueChange = { eleven = it; saved = false },
            label = { Text("ElevenLabs API key (optional)") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
        )
        Button(
            onClick = {
                store.setGeminiKey(gemini)
                store.setElevenKey(eleven)
                saved = true
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Save keys") }
        if (saved) Text("Keys saved.", color = Ok)

        Text(
            "Avatar: Hero is the default. The poster style always uses the centre cut " +
                "(face + shoulders) — never the whole poster.",
            color = Mute,
        )
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Back") }
    }
}

@Composable
private fun VoiceChip(name: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (selected) Color(0xFF7C4DFF) else Color(0xFF241547))
            .border(1.dp, if (selected) Color(0xFFFFC53D) else Color.Transparent, RoundedCornerShape(20.dp))
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Text(name, color = Color.White)
    }
}
