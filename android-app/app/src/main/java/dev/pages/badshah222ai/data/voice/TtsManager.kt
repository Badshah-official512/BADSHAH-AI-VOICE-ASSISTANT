package dev.pages.badshah222ai.data.voice

import android.content.Context
import android.media.MediaPlayer
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import dev.pages.badshah222ai.core.SecureStore
import dev.pages.badshah222ai.data.supabase.SupabaseApi
import dev.pages.badshah222ai.domain.VoiceInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

/** Speaks replies using the REAL installed Android TTS voices, or (optionally, independently) ElevenLabs. */
class TtsManager(private val context: Context, private val store: SecureStore, private val cloud: SupabaseApi) {
    private var tts: TextToSpeech? = null
    private var player: MediaPlayer? = null
    private val eleven = ElevenLabsClient()
    private var onFinished: (() -> Unit)? = null

    private val _ready = MutableStateFlow(false); val ready: StateFlow<Boolean> = _ready
    private val _speaking = MutableStateFlow(false); val speaking: StateFlow<Boolean> = _speaking
    private val _voices = MutableStateFlow<List<VoiceInfo>>(emptyList()); val voices: StateFlow<List<VoiceInfo>> = _voices
    private val _engineName = MutableStateFlow(""); val engineName: StateFlow<String> = _engineName

    fun init() {
        if (tts != null) return
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(id: String?) { _speaking.value = true }
                    override fun onDone(id: String?) { if (id?.endsWith("_last") == true) finish() }
                    @Deprecated("Deprecated in Java") override fun onError(id: String?) { finish() }
                })
                refreshVoices(); _ready.value = true
            }
        }
    }

    fun refreshVoices() {
        val raw = runCatching { tts?.voices }.getOrNull().orEmpty()
        _voices.value = raw.map { VoiceInfo(it.name, it.locale.toLanguageTag(), it.isNetworkConnectionRequired, it.quality) }
        _engineName.value = runCatching { tts?.defaultEngine }.getOrNull().orEmpty()
    }

    fun isLanguageAvailable(tag: String): Boolean {
        val r = tts?.isLanguageAvailable(Locale.forLanguageTag(tag)) ?: return false
        return r >= TextToSpeech.LANG_AVAILABLE
    }

    private fun finish() { _speaking.value = false; val cb = onFinished; onFinished = null; cb?.invoke() }

    fun stop() { tts?.stop(); runCatching { player?.stop() }; player?.release(); player = null; _speaking.value = false; onFinished = null }

    /** Speaks with whichever engine the user chose. ElevenLabs falls back to Android TTS on failure.
     *  [pitchMul]/[speedMul] carry the current feeling's small prosody shift (Android engine only;
     *  ElevenLabs voices carry their own emotion). */
    suspend fun speak(text: String, onDone: (() -> Unit)? = null, pitchMul: Float = 1f, speedMul: Float = 1f): String? {
        stop(); onFinished = onDone
        val key = store.elevenKey(); val vid = store.elevenVoiceId
        if (store.voiceEngine == "elevenlabs" && key != null && vid != null) {
            return try { speakEleven(key, vid, text); null }
            catch (e: ElevenException) { speakAndroid(text, store.ttsVoiceName, store.pitch * pitchMul, store.speed * speedMul); "ElevenLabs: ${e.userMessage} Using the phone voice instead." }
        }
        val gKey = store.googleTtsKey()
        if (store.voiceEngine == "google" && gKey != null) {
            return try { speakGoogle(gKey, text); null }
            catch (e: Exception) { speakAndroid(text, store.ttsVoiceName, store.pitch * pitchMul, store.speed * speedMul); "Google Voice: ${e.message?.take(90) ?: "masla aa gaya"}. Using the phone voice instead." }
        }
        speakAndroid(text, store.ttsVoiceName, store.pitch * pitchMul, store.speed * speedMul); return null
    }

    fun speakAndroid(text: String, voiceName: String?, pitch: Float, speed: Float) {
        val engine = tts ?: run { finish(); return }
        engine.stopSilently()
        val v = voiceName?.let { n -> runCatching { engine.voices }.getOrNull()?.firstOrNull { it.name == n } }
        if (v != null) engine.setVoice(v) else engine.setLanguage(Locale.forLanguageTag(store.language))
        engine.setPitch(pitch.coerceIn(0.5f, 2f)); engine.setSpeechRate(speed.coerceIn(0.5f, 2f))
        val max = (TextToSpeech.getMaxSpeechInputLength() - 100).coerceAtLeast(500)
        val chunks = text.chunkedBySentence(max)
        chunks.forEachIndexed { i, c ->
            val id = "u$i" + if (i == chunks.lastIndex) "_last" else ""
            engine.speak(c, if (i == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD, Bundle(), id)
        }
    }
    private fun TextToSpeech.stopSilently() { runCatching { stop() } }

    private suspend fun speakEleven(key: String, voiceId: String, text: String) {
        val t0 = System.currentTimeMillis()
        val bytes = try { eleven.synthesize(key, voiceId, text.take(2500)) } catch (e: ElevenException) {
            cloud.recordUsage("elevenlabs", "eleven_multilingual_v2", "tts", false, 0, 0, null, e.code); throw e
        }
        cloud.recordUsage("elevenlabs", "eleven_multilingual_v2", "tts", true, text.length.toLong(), 0, (System.currentTimeMillis() - t0).toInt(), null)
        val f = withContext(Dispatchers.IO) { File(context.cacheDir, "eleven_tts.mp3").also { it.writeBytes(bytes) } }
        val mp = MediaPlayer()
        mp.setDataSource(f.absolutePath)
        mp.setOnCompletionListener { it.release(); if (player === it) player = null; finish() }
        mp.setOnErrorListener { m, _, _ -> m.release(); finish(); true }
        mp.prepare(); player = mp; _speaking.value = true; mp.start()
    }

    private val googleTts = GoogleTtsClient()

    private suspend fun speakGoogle(key: String, text: String) {
        val bytes = googleTts.synthesize(key, store.googleTtsVoice, text.take(2500))
        val f = withContext(Dispatchers.IO) { File(context.cacheDir, "google_tts.mp3").also { it.writeBytes(bytes) } }
        val mp = MediaPlayer()
        mp.setDataSource(f.absolutePath)
        mp.setOnCompletionListener { it.release(); if (player === it) player = null; finish() }
        mp.setOnErrorListener { m, _, _ -> m.release(); finish(); true }
        mp.prepare(); player = mp; _speaking.value = true; mp.start()
    }

    fun shutdown() { stop(); tts?.shutdown(); tts = null }
}

internal fun String.chunkedBySentence(max: Int): List<String> {
    if (length <= max) return listOf(this)
    val out = mutableListOf<String>(); val sb = StringBuilder()
    for (s in split(Regex("(?<=[.!?۔\\n])\\s+"))) {
        if (sb.length + s.length + 1 > max && sb.isNotEmpty()) { out += sb.toString(); sb.clear() }
        if (s.length > max) { s.chunked(max).forEach { out += it } } else { if (sb.isNotEmpty()) sb.append(' '); sb.append(s) }
    }
    if (sb.isNotEmpty()) out += sb.toString()
    return out
}
