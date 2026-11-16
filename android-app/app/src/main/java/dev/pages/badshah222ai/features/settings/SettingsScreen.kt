package dev.pages.badshah222ai.features.settings

import android.Manifest
import android.app.Application
import android.content.Intent
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Hearing
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.pages.badshah222ai.BadshahApp
import dev.pages.badshah222ai.R
import dev.pages.badshah222ai.core.WakeWordService
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

    val engine = MutableStateFlow(c.store.voiceEngine)
    val elevenSaved = MutableStateFlow(c.store.elevenKey() != null)
    val elevenVoices = MutableStateFlow<List<ElevenVoice>>(emptyList())
    val elevenVoice = MutableStateFlow(c.store.elevenVoiceId)
    val elevenMsg = MutableStateFlow<Pair<Boolean, String>?>(null)
    val speak = MutableStateFlow(c.store.speakReplies); val lowPower = MutableStateFlow(c.store.lowPower); val language = MutableStateFlow(c.store.language)
    val personalityId = MutableStateFlow(c.store.personalityId)
    val brainId = MutableStateFlow(c.store.brainId)
    val wakeWord = MutableStateFlow(c.store.wakeWord)
    val customWakeWord = MutableStateFlow(c.store.customWakeWord)

    // ---- Gemini Live ----
    val liveVoiceName = MutableStateFlow(c.store.liveVoiceName)
    val liveModel = MutableStateFlow(c.store.liveModel)
    val geminiSaved = MutableStateFlow(c.store.apiKey(Provider.GEMINI) != null)
    val liveCandidates = MutableStateFlow(c.store.liveCandidates)
    val lastLiveError = MutableStateFlow(c.store.lastLiveError)
    val liveEnabled = MutableStateFlow(c.store.liveEnabled)
    val liveMsg = MutableStateFlow<Pair<Boolean, String>?>(null)
    val liveTesting = MutableStateFlow(false)

    // ---- emotions & identity ----
    val emotionId = MutableStateFlow(c.store.emotionId)
    val assistantName = MutableStateFlow(c.store.assistantName)
    val responseStyle = MutableStateFlow(c.store.responseStyle)
    val avatarChoice = MutableStateFlow(c.store.avatarChoice)

    // ---- FT Fusion images & Google Voice ----
    val imageModel = MutableStateFlow(c.store.imageModel)
    fun setImageModel(m: String) { c.store.imageModel = m; imageModel.value = m }
    val googleSaved = MutableStateFlow(c.store.googleTtsKey() != null)
    val googleVoice = MutableStateFlow(c.store.googleTtsVoice)
    val googleMsg = MutableStateFlow<Pair<Boolean, String>?>(null)
    fun testGoogle(key: String) {
        val k = key.ifBlank { c.store.googleTtsKey() }; if (k == null) { googleMsg.value = false to "Pehle Google Cloud API key paste karo."; return }
        viewModelScope.launch {
            try { GoogleTtsClient().synthesize(k, googleVoice.value, "Hello, I am Badshah AI."); c.store.setGoogleTtsKey(k); googleSaved.value = true; googleMsg.value = true to "Google Voice connected." }
            catch (e: Exception) { googleMsg.value = false to (e.message?.take(130) ?: "Google Voice connect nahi hua.") }
        }
    }
    fun pickGoogleVoice(v: String) { c.store.googleTtsVoice = v; googleVoice.value = v }
    fun removeGoogle() { c.store.setGoogleTtsKey(null); googleSaved.value = false; if (c.store.voiceEngine == "google") setEngine("android"); googleMsg.value = true to "Google Voice key removed." }

    // ---- AI ----
    fun selectProvider(p: Provider) { c.store.provider = p; provider.value = p; models.value = if (p == Provider.FUSION) dev.pages.badshah222ai.data.ai.FtFusion.TEXT_MODELS else emptyList(); selectedModel.value = c.store.model(p); keySaved.value = c.store.apiKey(p) != null; aiMsg.value = null; pushPrefs() }
    fun testAndSave(key: String) {
        val p = provider.value; if (p != Provider.FUSION && key.isBlank() && !keySaved.value) { aiMsg.value = false to "Paste your ${p.label} API key first."; return }
        viewModelScope.launch {
            testing.value = true; aiMsg.value = null
            try {
                val k = if (p == Provider.FUSION) "" else key.ifBlank { c.store.apiKey(p)!! }
                val list = c.ai.testConnection(p, k)
                c.store.setApiKey(p, k); keySaved.value = true; models.value = list
                val keep = c.store.model(p)?.takeIf { it in list } ?: list.first(); c.store.setModel(p, keep); selectedModel.value = keep
                aiMsg.value = true to "Connected. ${list.size} models available."
                if (p == Provider.GEMINI) {
                    // Detect the Live model right away, so the Voice Call connects in seconds later.
                    val lm = runCatching { c.ai.detectLiveModel() }.getOrNull()
                    liveModel.value = lm; liveCandidates.value = c.store.liveCandidates; geminiSaved.value = true
                    if (lm != null) aiMsg.value = true to "Connected. ${list.size} models available. Live voice bhi ready hai ($lm)."
                }
                uid?.let { runCatching { c.cloud.saveProviderStatus(it, p.id, true, "ok") } }; pushPrefs()
            } catch (e: AiException) { aiMsg.value = false to e.userMessage; uid?.let { runCatching { c.cloud.saveProviderStatus(it, p.id, c.store.apiKey(p) != null, "failed") } } }
            catch (e: Exception) { aiMsg.value = false to "Could not test the key." } finally { testing.value = false }
        }
    }
    fun selectModel(m: String) { c.store.setModel(provider.value, m); selectedModel.value = m; pushPrefs() }
    fun removeKey() { val p = provider.value; c.store.setApiKey(p, null); keySaved.value = false; models.value = emptyList(); aiMsg.value = true to "Key removed from this phone."; uid?.let { u -> viewModelScope.launch { runCatching { c.cloud.saveProviderStatus(u, p.id, false, "removed") } } } }

    // ---- Gemini Live ----
    fun setLiveVoice(v: LiveVoice) { c.store.liveVoiceName = v.name; liveVoiceName.value = v.name }
    fun setLiveEnabled(on: Boolean) { c.store.liveEnabled = on; liveEnabled.value = on }
    fun testLive() {
        if (c.store.apiKey(Provider.GEMINI) == null) { liveMsg.value = false to "Pehle Connections mein apni Gemini API key save karo."; return }
        viewModelScope.launch {
            liveTesting.value = true; liveMsg.value = null
            try {
                val m = c.ai.detectLiveModel()
                liveModel.value = m; liveCandidates.value = c.store.liveCandidates; lastLiveError.value = c.store.lastLiveError
                liveMsg.value = if (m != null) true to "Live ready — model: $m" else false to "Is key par koi Live model nahi mila. Voice Call classic mode mein chalegi."
            } catch (e: Exception) { liveMsg.value = false to "Live test failed. Internet check karke dobara try karo." }
            finally { liveTesting.value = false }
        }
    }

    // ---- identity, emotion, look ----
    fun setEmotion(e: Emotion?) { c.store.emotionId = e?.id; emotionId.value = e?.id }
    fun setAssistantName(n: String) { c.store.assistantName = n.trim().ifBlank { "BADSHAH" }; assistantName.value = c.store.assistantName }
    fun setResponseStyle(s: String) { c.store.responseStyle = s; responseStyle.value = s }
    fun setAvatar(a: String) { c.store.avatarChoice = a; avatarChoice.value = a }
    fun setCustomWakeWord(w: String) { c.store.customWakeWord = w.trim(); customWakeWord.value = c.store.customWakeWord }

    // ---- voice basics ----
    fun setEngine(e: String) { c.store.voiceEngine = e; engine.value = e; pushPrefs() }
    fun setSpeak(v: Boolean) { c.store.speakReplies = v; speak.value = v }
    fun setLowPower(v: Boolean) { c.store.lowPower = v; lowPower.value = v }
    fun setLanguage(tag: String) { c.store.language = tag; language.value = tag; pushPrefs() }
    fun setPersonality(p: Personality) { c.store.personalityId = p.id; personalityId.value = p.id }
    fun setBrain(b: Brain) { c.store.brainId = b.id; brainId.value = b.id }
    fun setWakeWord(on: Boolean) { c.store.wakeWord = on; wakeWord.value = on }

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

    private fun pushPrefs() { val u = uid ?: return; viewModelScope.launch { runCatching { c.cloud.savePreferences(u, provider.value.id, selectedModel.value, null, engine.value, 1.0f, 1.0f, language.value) } } }
}

@Composable private fun Section(title: String, icon: ImageVector, content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(20.dp)) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) { Icon(icon, null, tint = VioletSoft); Text(title, style = MaterialTheme.typography.titleMedium, color = VioletSoft) }; content() } }
}
@Composable private fun Msg(m: Pair<Boolean, String>?) { m?.let { Text(it.second, color = if (it.first) Ok else Bad, style = MaterialTheme.typography.bodySmall) } }

val LANGUAGES = listOf(
    "en-US" to "English", "ur-PK" to "Urdu", "hi-IN" to "Hindi", "ps-AF" to "Pashto",
    "pa-PK" to "Punjabi", "sd-PK" to "Sindhi", "ar-SA" to "Arabic", "fa-IR" to "Persian",
    "bn-BD" to "Bengali", "tr-TR" to "Turkish", "es-ES" to "Spanish", "fr-FR" to "French",
    "de-DE" to "German", "it-IT" to "Italian", "pt-BR" to "Portuguese", "ru-RU" to "Russian",
    "zh-CN" to "Chinese", "ja-JP" to "Japanese", "ko-KR" to "Korean", "id-ID" to "Indonesian",
    "ms-MY" to "Malay", "nl-NL" to "Dutch", "sv-SE" to "Swedish", "pl-PL" to "Polish",
    "uk-UA" to "Ukrainian", "ta-IN" to "Tamil", "te-IN" to "Telugu", "mr-IN" to "Marathi",
    "gu-IN" to "Gujarati", "ml-IN" to "Malayalam",
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(session: SessionViewModel, vm: SettingsViewModel, onBack: () -> Unit, onPermissions: () -> Unit) {
    val ctx = LocalContext.current
    val provider by vm.provider.collectAsState(); val models by vm.models.collectAsState(); val selModel by vm.selectedModel.collectAsState()
    val keySaved by vm.keySaved.collectAsState(); val aiMsg by vm.aiMsg.collectAsState(); val testing by vm.testing.collectAsState()
    val engine by vm.engine.collectAsState()
    val elevenSaved by vm.elevenSaved.collectAsState(); val elevenVoices by vm.elevenVoices.collectAsState(); val elevenVoice by vm.elevenVoice.collectAsState(); val elevenMsg by vm.elevenMsg.collectAsState()
    val speak by vm.speak.collectAsState(); val low by vm.lowPower.collectAsState(); val lang by vm.language.collectAsState()
    val personalityId by vm.personalityId.collectAsState(); val brainId by vm.brainId.collectAsState(); val wakeOn by vm.wakeWord.collectAsState()
    val liveVoiceName by vm.liveVoiceName.collectAsState(); val liveModel by vm.liveModel.collectAsState(); val liveEnabled by vm.liveEnabled.collectAsState()
    val geminiSaved by vm.geminiSaved.collectAsState(); val liveCandidates by vm.liveCandidates.collectAsState(); val lastLiveError by vm.lastLiveError.collectAsState()
    val liveMsg by vm.liveMsg.collectAsState(); val liveTesting by vm.liveTesting.collectAsState()
    val emotionId by vm.emotionId.collectAsState(); val assistantName by vm.assistantName.collectAsState()
    val imageModel by vm.imageModel.collectAsState()
    val googleSaved by vm.googleSaved.collectAsState(); val googleVoice by vm.googleVoice.collectAsState(); val googleMsg by vm.googleMsg.collectAsState()
    var gKey by remember { mutableStateOf("") }; var gShow by remember { mutableStateOf(false) }
    val responseStyle by vm.responseStyle.collectAsState(); val avatarChoice by vm.avatarChoice.collectAsState()
    val user by session.user.collectAsState()
    var emotionFamily by remember { mutableStateOf(Emotions.families.first()) }
    var nameDraft by remember(assistantName) { mutableStateOf(assistantName) }
    var wakeDraft by remember { mutableStateOf(vm.customWakeWord.value) }
    val wakeMic = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok && vm.wakeWord.value) WakeWordService.start(ctx) else if (!ok) vm.setWakeWord(false)
    }
    var key by remember(provider) { mutableStateOf("") }; var showKey by remember { mutableStateOf(false) }
    var eKey by remember { mutableStateOf("") }; var modelMenu by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        TopBar("Settings", onBack)
        LazyColumn(Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
            item {
                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(20.dp)) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Image(painterResource(R.drawable.badshah_cutout), "BADSHAH", Modifier.size(54.dp).clip(CircleShape), contentScale = ContentScale.Crop)
                        Column(Modifier.padding(start = 12.dp)) {
                            Text(assistantName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(user?.email ?: "Signed in", color = Mute, style = MaterialTheme.typography.bodySmall)
                            Text("Your assistant · ${Personalities.fromId(personalityId).label} · ${Brains.fromId(brainId).label}", color = VioletSoft, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
            item { Text("AI SETTINGS", color = Mute, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(start = 4.dp)) }
            item { Section("Connections", Icons.Default.Key) {
                Text("Each person uses their own API key. It is stored encrypted on this phone only.", style = MaterialTheme.typography.bodySmall, color = Mute)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { Provider.entries.forEach { p -> FilterChip(provider == p, { vm.selectProvider(p) }, { Text(p.label) }) } }
                if (provider == Provider.FUSION) {
                    Text("FT Fusion built-in hai — koi key nahi chahiye. Test dabao: 12 models chat aur coding ke liye ready ho jayenge. Images ke liye neeche Image Studio dekho.", style = MaterialTheme.typography.bodySmall, color = Mute)
                } else {
                OutlinedTextField(key, { key = it }, Modifier.fillMaxWidth(), label = { Text(if (keySaved) "API key saved (paste a new one to replace)" else "${provider.label} API key") }, singleLine = true,
                    visualTransformation = if (showKey) androidx.compose.ui.text.input.VisualTransformation.None else PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailingIcon = { TextButton({ showKey = !showKey }) { Text(if (showKey) "Hide" else "Show") } })
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Button({ vm.testAndSave(key); key = "" }, enabled = !testing) { if (testing) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp) else Text("Test & save") }
                    if (provider != Provider.FUSION) OutlinedButton({ ctx.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(provider.keyUrl))) }) { Text("Get a key") }
                    if (keySaved && provider != Provider.FUSION) TextButton(vm::removeKey) { Text("Remove") }
                }
                Msg(aiMsg)
                if (keySaved || provider == Provider.FUSION) Box { OutlinedButton({ modelMenu = true }, enabled = models.isNotEmpty() || selModel != null) { Text("Model: ${selModel ?: "-"}") }
                    DropdownMenu(modelMenu, { modelMenu = false }) { models.forEach { m -> DropdownMenuItem({ Text(m) }, { vm.selectModel(m); modelMenu = false }) } } }
                if (keySaved && models.isEmpty()) Text("Press Test & save to load the list of models for your key.", style = MaterialTheme.typography.bodySmall, color = Mute)
            } }
            item { Section("Live Voice — real human voices", Icons.Default.RecordVoiceOver) {
                Text("Voice Call ab Gemini Live par chalti hai — Google ki asli insani voices, bilkul LIA AI ki tarah. Koi robotic phone voice nahi. Apni Gemini key ka Live model pehle test kar lo, phir apni pasand ki voice chun lo.", style = MaterialTheme.typography.bodySmall, color = Mute)
                Row(verticalAlignment = Alignment.CenterVertically) { Text("Use Live voice on Voice Call", Modifier.weight(1f)); Switch(liveEnabled, vm::setLiveEnabled) }
                Text("Live model: ${liveModel ?: "not tested yet"}", style = MaterialTheme.typography.bodySmall, color = if (liveModel != null) Ok else Mute)
                Text("Gemini key: ${if (geminiSaved) "saved hai ✓" else "NAHI daali — Live sirf Gemini key se chalta hai (Groq/FT Fusion se nahi). Connections mein Google Gemini select karke key daalo."}", style = MaterialTheme.typography.bodySmall, color = if (geminiSaved) Ok else Bad)
                if (liveCandidates.isNotBlank()) Text("Live models mile: ${liveCandidates.split(",").size} — ${liveCandidates.take(110)}", style = MaterialTheme.typography.bodySmall, color = Mute)
                lastLiveError?.let { Text("Aakhri Live koshish ki wajah: $it", style = MaterialTheme.typography.bodySmall, color = Bad) }
                Button(vm::testLive, enabled = !liveTesting) { if (liveTesting) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp) else Text("Test Live model") }
                Msg(liveMsg)
                Text("Male voices", style = MaterialTheme.typography.labelLarge, color = Gold)
                LiveVoices.male.forEach { v ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(liveVoiceName == v.name, { vm.setLiveVoice(v) })
                        Column { Text(v.name, style = MaterialTheme.typography.bodyLarge); Text(v.style, style = MaterialTheme.typography.bodySmall, color = Mute) }
                    }
                }
                Text("Girls voices", style = MaterialTheme.typography.labelLarge, color = Gold)
                LiveVoices.female.forEach { v ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(liveVoiceName == v.name, { vm.setLiveVoice(v) })
                        Column { Text(v.name, style = MaterialTheme.typography.bodyLarge); Text(v.style, style = MaterialTheme.typography.bodySmall, color = Mute) }
                    }
                }
                Text("Voice sunne ke liye Voice Call kholo — wahan live baat hogi isi voice mein.", style = MaterialTheme.typography.bodySmall, color = Mute)
            } }
            item { Section("Image Studio", Icons.Default.Image) {
                Text("Chat mein bolo \"ek golden crown ka logo banao, image banao\" — BADSHAH khud image bana dega (FT Fusion). Model chuno:", style = MaterialTheme.typography.bodySmall, color = Mute)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(imageModel == "fusion-uni", { vm.setImageModel("fusion-uni") }, { Text("Fusion Uni") })
                    FilterChip(imageModel == "fusion-astra", { vm.setImageModel("fusion-astra") }, { Text("Fusion Astra") })
                }
            } }
            item { Section("Personality", Icons.Default.Person) {
                Text("Choose who your assistant is for you. The personality changes how it talks, advises and behaves with you.", style = MaterialTheme.typography.bodySmall, color = Mute)
                Personalities.all.forEach { p ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(personalityId == p.id, { vm.setPersonality(p) })
                        Column { Text(p.label, style = MaterialTheme.typography.bodyLarge); Text(p.tagline, style = MaterialTheme.typography.bodySmall, color = Mute) }
                    }
                }
            } }
            item { Section("AI Brain", Icons.Default.Psychology) {
                Text("Choose the expert mind your assistant thinks with. Doctor ki tarah treat karega, Teacher ki tarah parhayega, Poet ki tarah bolega — har brain apne profession ki tarah behave karta hai.", style = MaterialTheme.typography.bodySmall, color = Mute)
                Brains.all.forEach { b ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(brainId == b.id, { vm.setBrain(b) })
                        Column { Text(b.label, style = MaterialTheme.typography.bodyLarge); Text(b.tagline, style = MaterialTheme.typography.bodySmall, color = Mute) }
                    }
                }
            } }
            item { Section("Emotions", Icons.Default.Favorite) {
                Text("Naye emotions — har emotion mein awaaz ka lehja sach mein badalta hai. Auto par assistant tumhare alfaaz se tumhara mood pehchanta hai aur usi emotion mein, tumhare apne andaz mein jawab deta hai. Ya khud koi emotion fix kar lo.", style = MaterialTheme.typography.bodySmall, color = Mute)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(emotionId == null, { vm.setEmotion(null) })
                    Column { Text("Auto — reads your mood", style = MaterialTheme.typography.bodyLarge); Text("Recommended", style = MaterialTheme.typography.bodySmall, color = Mute) }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Emotions.families.forEach { fam -> FilterChip(emotionFamily == fam, { emotionFamily = fam }, { Text(fam) }) }
                }
                Emotions.byFamily(emotionFamily).forEach { e ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(emotionId == e.id, { vm.setEmotion(e) })
                        Box(Modifier.size(10.dp).clip(CircleShape).background(e.composeColor))
                        Text(e.label, Modifier.padding(start = 8.dp), style = MaterialTheme.typography.bodyLarge)
                    }
                }
            } }
            item { Section("Assistant", Icons.Default.Face) {
                Text("Assistant name", style = MaterialTheme.typography.labelLarge)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(nameDraft, { nameDraft = it }, Modifier.weight(1f), singleLine = true, label = { Text("Name") })
                    Button({ vm.setAssistantName(nameDraft) }) { Text("Save") }
                }
                Text("Response style", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(responseStyle == "balanced", { vm.setResponseStyle("balanced") }, { Text("Balanced") })
                    FilterChip(responseStyle == "short", { vm.setResponseStyle("short") }, { Text("Short") })
                    FilterChip(responseStyle == "detailed", { vm.setResponseStyle("detailed") }, { Text("Detailed") })
                }
                Row(verticalAlignment = Alignment.CenterVertically) { Text("Speak chat replies aloud (backup phone voice)", Modifier.weight(1f)); Switch(speak, vm::setSpeak) }
            } }
            item { Section("Look & feel", Icons.Default.AutoAwesome) {
                Text("Face & logo — Home aur Voice Call ke orb mein yahi nazar ayega. Apna style khud choose karo.", style = MaterialTheme.typography.bodySmall, color = Mute)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(avatarChoice == "photo", { vm.setAvatar("photo") }, { Text("My photo") })
                    FilterChip(avatarChoice == "logo", { vm.setAvatar("logo") }, { Text("Crown logo") })
                    FilterChip(avatarChoice == "girl", { vm.setAvatar("girl") }, { Text("AISHA") })
                }
                Row(verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text("Low-power mode"); Text("Simpler animation for older phones", style = MaterialTheme.typography.bodySmall, color = Mute) }; Switch(low, vm::setLowPower) }
            } }
            item { Section("Language", Icons.Default.RecordVoiceOver) {
                Text("Assistant isi language mein sunega aur bolega. Urdu, English, Hindi, Pashto samet 30 international languages.", style = MaterialTheme.typography.bodySmall, color = Mute)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { LANGUAGES.forEach { (tag, label) -> FilterChip(lang == tag, { vm.setLanguage(tag) }, { Text(label) }) } }
            } }
            item { Section("ElevenLabs voice (optional)", Icons.Default.AutoAwesome) {
                Text("Premium voices for chat replies. Completely separate from Gemini/Groq. You need your own key and it costs ElevenLabs credits.", style = MaterialTheme.typography.bodySmall, color = Mute)
                OutlinedTextField(eKey, { eKey = it }, Modifier.fillMaxWidth(), label = { Text(if (elevenSaved) "Key saved (paste to replace)" else "ElevenLabs API key") }, singleLine = true, visualTransformation = PasswordVisualTransformation())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button({ vm.testEleven(eKey); eKey = "" }) { Text("Test & save") }
                    OutlinedButton({ ctx.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://elevenlabs.io/app/settings/api-keys"))) }) { Text("Get a key") }
                    if (elevenSaved) TextButton(vm::removeEleven) { Text("Remove") }
                }
                Msg(elevenMsg)
                elevenVoices.take(30).forEach { v -> Row(verticalAlignment = Alignment.CenterVertically) { RadioButton(elevenVoice == v.id, { vm.pickEleven(v.id) }); Text("${v.name} ${if (v.category.isNotBlank()) "(${v.category})" else ""}") } }
                if (elevenSaved) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { FilterChip(engine == "elevenlabs", { vm.setEngine("elevenlabs") }, { Text("Use for chat replies") }); FilterChip(engine == "android", { vm.setEngine("android") }, { Text("Phone voice") }) }
                if (googleSaved) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { FilterChip(engine == "google", { vm.setEngine("google") }, { Text("Google voice for replies") }) }
            } }
            item { Section("Google Voice (optional)", Icons.Default.RecordVoiceOver) {
                Text("Google ki apni premium awaazein (Journey waghera) — phone ki robot awaaz se kahin zyada natural. Apni Google Cloud API key lagao (console.cloud.google.com → Text-to-Speech API enable karo). Key sirf is phone par rehti hai.", style = MaterialTheme.typography.bodySmall, color = Mute)
                OutlinedTextField(gKey, { gKey = it }, Modifier.fillMaxWidth(), label = { Text(if (googleSaved) "Google Cloud key saved" else "Google Cloud API key") }, singleLine = true,
                    visualTransformation = if (gShow) androidx.compose.ui.text.input.VisualTransformation.None else PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailingIcon = { TextButton({ gShow = !gShow }) { Text(if (gShow) "Hide" else "Show") } })
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Button({ vm.testGoogle(gKey); gKey = "" }) { Text("Test & save") }
                    if (googleSaved) TextButton(vm::removeGoogle) { Text("Remove") }
                }
                Msg(googleMsg)
                if (googleSaved) {
                    Text("Voice chuno, phir ElevenLabs wale hisse mein \"Google voice for replies\" on karo:", style = MaterialTheme.typography.bodySmall, color = Mute)
                    GoogleVoices.all.forEach { (id, label) -> Row(verticalAlignment = Alignment.CenterVertically) { RadioButton(googleVoice == id, { vm.pickGoogleVoice(id) }); Column { Text(label, style = MaterialTheme.typography.bodyMedium); Text(id, style = MaterialTheme.typography.bodySmall, color = Mute) } } }
                }
            } }
            item { Section("Wake word", Icons.Default.Hearing) {
                Text("Jab ye ON ho, phone background mein assistant ka naam sunta rahega — doosri apps ke upar bhi. Naam bolo, assistant direct hazir ho jayega. Note: kuch phones battery bachane ke liye background listening band kar sakte hain.", style = MaterialTheme.typography.bodySmall, color = Mute)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Wake word", Modifier.weight(1f))
                    Switch(wakeOn, { on ->
                        vm.setWakeWord(on)
                        if (on) {
                            if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) WakeWordService.start(ctx)
                            else wakeMic.launch(Manifest.permission.RECORD_AUDIO)
                        } else WakeWordService.stop(ctx)
                    })
                }
                Text("Custom wake phrase (optional, BADSHAH ke ilawa)", style = MaterialTheme.typography.labelLarge)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(wakeDraft, { wakeDraft = it }, Modifier.weight(1f), singleLine = true, label = { Text("e.g. Hey assistant") })
                    Button({ vm.setCustomWakeWord(wakeDraft); if (wakeOn) { WakeWordService.stop(ctx); WakeWordService.start(ctx) } }) { Text("Save") }
                }
            } }
            item { Section("App settings", Icons.Default.Tune) {
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
