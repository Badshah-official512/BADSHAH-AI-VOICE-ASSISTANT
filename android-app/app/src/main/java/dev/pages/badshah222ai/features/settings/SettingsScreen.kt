package dev.pages.badshah222ai.features.settings

import android.app.Application
import android.content.Intent
import android.speech.tts.TextToSpeech
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.pages.badshah222ai.BadshahApp
import dev.pages.badshah222ai.data.ai.*
import dev.pages.badshah222ai.data.voice.*
import dev.pages.badshah222ai.domain.*
import dev.pages.badshah222ai.features.auth.SessionViewModel
import dev.pages.badshah222ai.ui.theme.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class SettingsViewModel(app: Application) : AndroidViewModel(app) {
    private val c = (app as BadshahApp).container
    private val uid get() = c.auth.current?.uid
    private val eleven = ElevenLabsClient()

    val provider = MutableStateFlow(c.store.provider)
    val models = MutableStateFlow<List<String>>(emptyList())
    val selectedModel = MutableStateFlow(c.store.model(c.store.provider))
    val keySaved = MutableStateFlow(c.store.apiKey(c.store.provider) != null)
    val aiMsg = MutableStateFlow<Pair<Boolean, String>?>(null)
    val testing = MutableStateFlow(false)

    val voices = c.tts.voices; val ttsReady = c.tts.ready; val engineName = c.tts.engineName
    val engine = MutableStateFlow(c.store.voiceEngine)
    val voiceName = MutableStateFlow(c.store.ttsVoiceName)
    val styleId = MutableStateFlow(c.store.styleId)
    val pitch = MutableStateFlow(c.store.pitch); val speed = MutableStateFlow(c.store.speed)
    val serverPresets = MutableStateFlow<List<VoiceProfileRow>>(emptyList())
    val elevenSaved = MutableStateFlow(c.store.elevenKey() != null)
    val elevenVoices = MutableStateFlow<List<ElevenVoice>>(emptyList())
    val elevenVoice = MutableStateFlow(c.store.elevenVoiceId)
    val elevenMsg = MutableStateFlow<Pair<Boolean, String>?>(null)
    val speak = MutableStateFlow(c.store.speakReplies); val lowPower = MutableStateFlow(c.store.lowPower); val language = MutableStateFlow(c.store.language)

    init { viewModelScope.launch { serverPresets.value = c.cloud.voiceProfiles() }; refreshVoices() }
    fun refreshVoices() = c.tts.refreshVoices()
    fun langAvailable(tag: String) = c.tts.isLanguageAvailable(tag)

    // ---- AI ----
    fun selectProvider(p: Provider) { c.store.provider = p; provider.value = p; models.value = emptyList(); selectedModel.value = c.store.model(p); keySaved.value = c.store.apiKey(p) != null; aiMsg.value = null; pushPrefs() }
    fun testAndSave(key: String) {
        val p = provider.value; if (key.isBlank() && !keySaved.value) { aiMsg.value = false to "Paste your ${p.label} API key first."; return }
        viewModelScope.launch {
            testing.value = true; aiMsg.value = null
            try {
                val k = key.ifBlank { c.store.apiKey(p)!! }
                val list = c.ai.testConnection(p, k)
                c.store.setApiKey(p, k); keySaved.value = true; models.value = list
                val keep = c.store.model(p)?.takeIf { it in list } ?: list.first(); c.store.setModel(p, keep); selectedModel.value = keep
                aiMsg.value = true to "Connected. ${list.size} models available."
                uid?.let { runCatching { c.cloud.saveProviderStatus(it, p.id, true, "ok") } }; pushPrefs()
            } catch (e: AiException) { aiMsg.value = false to e.userMessage; uid?.let { runCatching { c.cloud.saveProviderStatus(it, p.id, c.store.apiKey(p) != null, "failed") } } }
            catch (e: Exception) { aiMsg.value = false to "Could not test the key." } finally { testing.value = false }
        }
    }
    fun selectModel(m: String) { c.store.setModel(provider.value, m); selectedModel.value = m; pushPrefs() }
    fun removeKey() { val p = provider.value; c.store.setApiKey(p, null); keySaved.value = false; models.value = emptyList(); aiMsg.value = true to "Key removed from this phone."; uid?.let { u -> viewModelScope.launch { runCatching { c.cloud.saveProviderStatus(u, p.id, false, "removed") } } } }

    // ---- voice ----
    fun setEngine(e: String) { c.store.voiceEngine = e; engine.value = e; pushPrefs() }
    fun pickVoice(name: String?) { c.store.ttsVoiceName = name; voiceName.value = name; pushPrefs() }
    fun applyStyle(s: StylePreset) { c.store.styleId = s.id; styleId.value = s.id; setPitch(s.pitch); setSpeed(s.speed) }
    fun applyServerPreset(p: VoiceProfileRow) { c.store.styleId = p.id; styleId.value = p.id; setPitch(p.pitch); setSpeed(p.speed) }
    fun setPitch(v: Float) { c.store.pitch = v; pitch.value = v }
    fun setSpeed(v: Float) { c.store.speed = v; speed.value = v }
    fun commitVoice() = pushPrefs()
    fun preview(name: String? = voiceName.value) { c.tts.stop(); c.tts.speakAndroid("Hello, I am Badshah AI, your personal voice assistant.", name, pitch.value, speed.value) }
    fun previewEleven() { viewModelScope.launch { val w = c.tts.speak("Hello, I am Badshah AI, your personal voice assistant."); w?.let { elevenMsg.value = false to it } } }
    fun setSpeak(v: Boolean) { c.store.speakReplies = v; speak.value = v }
    fun setLowPower(v: Boolean) { c.store.lowPower = v; lowPower.value = v }
    fun setLanguage(tag: String) { c.store.language = tag; language.value = tag; pushPrefs() }

    // ---- ElevenLabs (optional) ----
    fun testEleven(key: String) {
        val k = key.ifBlank { c.store.elevenKey() }; if (k == null) { elevenMsg.value = false to "Paste your ElevenLabs API key first."; return }
        viewModelScope.launch {
            try { val v = eleven.listVoices(k); c.store.setElevenKey(k); elevenSaved.value = true; elevenVoices.value = v
                if (c.store.elevenVoiceId == null && v.isNotEmpty()) pickEleven(v.first().id)
                elevenMsg.value = true to "Connected. ${v.size} voices available." }
            catch (e: ElevenException) { elevenMsg.value = false to e.userMessage }
        }
    }
    fun pickEleven(id: String) { c.store.elevenVoiceId = id; elevenVoice.value = id }
    fun removeEleven() { c.store.setElevenKey(null); elevenSaved.value = false; elevenVoices.value = emptyList(); if (c.store.voiceEngine == "elevenlabs") setEngine("android"); elevenMsg.value = true to "ElevenLabs key removed." }

    private fun pushPrefs() { val u = uid ?: return; viewModelScope.launch { runCatching { c.cloud.savePreferences(u, provider.value.id, selectedModel.value, voiceName.value, engine.value, speed.value, pitch.value, language.value) } } }
}

@Composable private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Panel)) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { Text(title, style = MaterialTheme.typography.titleMedium, color = Gold); content() } }
}
@Composable private fun Msg(m: Pair<Boolean, String>?) { m?.let { Text(it.second, color = if (it.first) Ok else Bad, style = MaterialTheme.typography.bodySmall) } }

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(session: SessionViewModel, vm: SettingsViewModel, onBack: () -> Unit, onPermissions: () -> Unit) {
    val ctx = LocalContext.current
    val provider by vm.provider.collectAsState(); val models by vm.models.collectAsState(); val selModel by vm.selectedModel.collectAsState()
    val keySaved by vm.keySaved.collectAsState(); val aiMsg by vm.aiMsg.collectAsState(); val testing by vm.testing.collectAsState()
    val voices by vm.voices.collectAsState(); val engine by vm.engine.collectAsState(); val voiceName by vm.voiceName.collectAsState()
    val styleId by vm.styleId.collectAsState(); val pitch by vm.pitch.collectAsState(); val speed by vm.speed.collectAsState()
    val presets by vm.serverPresets.collectAsState(); val engineName by vm.engineName.collectAsState()
    val elevenSaved by vm.elevenSaved.collectAsState(); val elevenVoices by vm.elevenVoices.collectAsState(); val elevenVoice by vm.elevenVoice.collectAsState(); val elevenMsg by vm.elevenMsg.collectAsState()
    val speak by vm.speak.collectAsState(); val low by vm.lowPower.collectAsState(); val lang by vm.language.collectAsState()
    var key by remember(provider) { mutableStateOf("") }; var showKey by remember { mutableStateOf(false) }
    var eKey by remember { mutableStateOf("") }; var maleOnly by remember { mutableStateOf(true) }; var modelMenu by remember { mutableStateOf(false) }
    val sorted = remember(voices) { VoiceCatalog.sorted(voices) }
    val shown = if (maleOnly) sorted.filter { VoiceCatalog.guessGender(it) != GenderGuess.LIKELY_FEMALE } else sorted
    val support = remember(voices) { VoiceCatalog.languageSupport(voices) }

    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        TopBar("Settings", onBack)
        LazyColumn(Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
            item { Section("AI brain") {
                Text("Each person uses their own API key. It is stored encrypted on this phone only.", style = MaterialTheme.typography.bodySmall, color = Mute)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { Provider.entries.forEach { p -> FilterChip(provider == p, { vm.selectProvider(p) }, { Text(p.label) }) } }
                OutlinedTextField(key, { key = it }, Modifier.fillMaxWidth(), label = { Text(if (keySaved) "API key saved (paste a new one to replace)" else "${provider.label} API key") }, singleLine = true,
                    visualTransformation = if (showKey) androidx.compose.ui.text.input.VisualTransformation.None else PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailingIcon = { TextButton({ showKey = !showKey }) { Text(if (showKey) "Hide" else "Show") } })
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Button({ vm.testAndSave(key); key = "" }, enabled = !testing) { if (testing) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp) else Text("Test & save") }
                    OutlinedButton({ ctx.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(provider.keyUrl))) }) { Text("Get a key") }
                    if (keySaved) TextButton(vm::removeKey) { Text("Remove") }
                }
                Msg(aiMsg)
                if (keySaved) Box { OutlinedButton({ modelMenu = true }, enabled = models.isNotEmpty() || selModel != null) { Text("Model: ${selModel ?: "-"}") }
                    DropdownMenu(modelMenu, { modelMenu = false }) { models.forEach { m -> DropdownMenuItem({ Text(m) }, { vm.selectModel(m); modelMenu = false }) } } }
                if (keySaved && models.isEmpty()) Text("Press Test & save to load the list of models for your key.", style = MaterialTheme.typography.bodySmall, color = Mute)
            } }
            item { Section("Voice language & engine") {
                Text("Installed speech engine: ${engineName.ifBlank { "unknown" }}", style = MaterialTheme.typography.bodySmall, color = Mute)
                support.forEach { s -> Text("${s.label}: " + if (s.installed) "${s.voices.size} voice(s) installed" else "not installed on this phone", color = if (s.installed) Ok else Mute, style = MaterialTheme.typography.bodySmall) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton({ ctx.startActivity(Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }) { Text("Install voice data") }
                    OutlinedButton({ vm.refreshVoices() }) { Text("Refresh") }
                }
                Text("Speaking & listening language", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf("en-US" to "English", "ur-PK" to "Urdu", "hi-IN" to "Hindi", "ps-AF" to "Pashto").forEach { (tag, label) -> FilterChip(lang == tag, { vm.setLanguage(tag) }, { Text(label) }) } }
                Text("Voice engine", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { FilterChip(engine == "android", { vm.setEngine("android") }, { Text("Phone voices (Google TTS)") }); FilterChip(engine == "elevenlabs", { if (elevenSaved) vm.setEngine("elevenlabs") }, { Text("ElevenLabs") }, enabled = elevenSaved) }
                Row(verticalAlignment = Alignment.CenterVertically) { Text("Speak replies aloud", Modifier.weight(1f)); Switch(speak, vm::setSpeak) }
            } }
            item { Section("Phone voices") {
                Text("Android does not tell apps a voice's gender. Voices whose engine name says \"male\" are marked MALE; the rest are unverified, so listen and choose. Styles change pitch and speed of the voice you pick; they are not separate voices.", style = MaterialTheme.typography.bodySmall, color = Mute)
                Row(verticalAlignment = Alignment.CenterVertically) { Text("Hide voices labelled female", Modifier.weight(1f)); Switch(maleOnly, { maleOnly = it }) }
                Text("${VoiceCatalog.maleCandidates(voices).size} labelled male, ${voices.size} installed in total", style = MaterialTheme.typography.bodySmall, color = Gold)
                if (voices.isEmpty()) Text("No voices reported yet. Open Install voice data, then Refresh.", color = Mute)
            } }
            items(shown.take(80)) { v ->
                val g = VoiceCatalog.guessGender(v)
                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = if (voiceName == v.name) Panel2 else Panel)) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) { Text(v.name, style = MaterialTheme.typography.bodyMedium); Text("${v.localeTag}  ${if (v.requiresNetwork) "online" else "offline"}  ${when (g) { GenderGuess.LIKELY_MALE -> "MALE"; GenderGuess.LIKELY_FEMALE -> "female"; else -> "unverified" }}", style = MaterialTheme.typography.bodySmall, color = if (g == GenderGuess.LIKELY_MALE) Ok else Mute) }
                        TextButton({ vm.preview(v.name) }) { Text("Preview") }
                        TextButton({ vm.pickVoice(v.name) }) { Text(if (voiceName == v.name) "Selected" else "Use") }
                    }
                }
            }
            item { Section("Voice style") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { VoiceCatalog.styles.forEach { s -> FilterChip(styleId == s.id, { vm.applyStyle(s) }, { Text(s.label) }) } }
                if (presets.isNotEmpty()) { Text("From the BADSHAH team", style = MaterialTheme.typography.labelLarge); FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { presets.forEach { p -> FilterChip(styleId == p.id, { vm.applyServerPreset(p) }, { Text(p.label) }) } } }
                Text("Pitch %.2f".format(pitch)); Slider(pitch, vm::setPitch, valueRange = 0.5f..1.5f, onValueChangeFinished = vm::commitVoice)
                Text("Speed %.2f".format(speed)); Slider(speed, vm::setSpeed, valueRange = 0.5f..1.5f, onValueChangeFinished = vm::commitVoice)
                Button({ if (engine == "elevenlabs") vm.previewEleven() else vm.preview() }) { Text("Preview current voice") }
            } }
            item { Section("ElevenLabs (optional)") {
                Text("Premium voices. Completely separate from Gemini/Groq. You need your own key and it costs ElevenLabs credits.", style = MaterialTheme.typography.bodySmall, color = Mute)
                OutlinedTextField(eKey, { eKey = it }, Modifier.fillMaxWidth(), label = { Text(if (elevenSaved) "Key saved (paste to replace)" else "ElevenLabs API key") }, singleLine = true, visualTransformation = PasswordVisualTransformation())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button({ vm.testEleven(eKey); eKey = "" }) { Text("Test & save") }
                    OutlinedButton({ ctx.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://elevenlabs.io/app/settings/api-keys"))) }) { Text("Get a key") }
                    if (elevenSaved) TextButton(vm::removeEleven) { Text("Remove") }
                }
                Msg(elevenMsg)
                elevenVoices.take(30).forEach { v -> Row(verticalAlignment = Alignment.CenterVertically) { RadioButton(elevenVoice == v.id, { vm.pickEleven(v.id) }); Text("${v.name} ${if (v.category.isNotBlank()) "(${v.category})" else ""}") } }
            } }
            item { Section("General") {
                Row(verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text("Low-power mode"); Text("Simpler animation for older phones", style = MaterialTheme.typography.bodySmall, color = Mute) }; Switch(low, vm::setLowPower) }
                OutlinedButton(onPermissions) { Text("Permissions") }
                OutlinedButton({ session.signOut() }) { Text("Sign out") }
            } }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun TopBar(title: String, onBack: () -> Unit) {
    TopAppBar(title = { Text(title) }, navigationIcon = { IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = androidx.compose.ui.graphics.Color.Transparent))
}
