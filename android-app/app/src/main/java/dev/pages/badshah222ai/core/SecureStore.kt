package dev.pages.badshah222ai.core

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dev.pages.badshah222ai.domain.Provider

/** API keys live ONLY here: AES-256 encrypted, Android Keystore-backed, never logged, never sent to our servers. */
class SecureStore(context: Context) {
    private val secrets: SharedPreferences = try {
        val mk = MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
        EncryptedSharedPreferences.create(context, "badshah_secrets", mk,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM)
    } catch (e: Exception) {
        // Keystore corrupted (rare, e.g. after restore): start clean rather than crash.
        context.deleteSharedPreferences("badshah_secrets")
        val mk = MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
        EncryptedSharedPreferences.create(context, "badshah_secrets", mk,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM)
    }
    private val prefs: SharedPreferences = context.getSharedPreferences("badshah_settings", Context.MODE_PRIVATE)

    fun apiKey(p: Provider): String? = secrets.getString("key_${p.id}", null)?.takeIf { it.isNotBlank() }
    fun setApiKey(p: Provider, key: String?) = secrets.edit().apply { if (key.isNullOrBlank()) remove("key_${p.id}") else putString("key_${p.id}", key.trim()) }.apply()
    fun elevenKey(): String? = secrets.getString("key_elevenlabs", null)?.takeIf { it.isNotBlank() }
    fun setElevenKey(key: String?) = secrets.edit().apply { if (key.isNullOrBlank()) remove("key_elevenlabs") else putString("key_elevenlabs", key.trim()) }.apply()
    fun googleTtsKey(): String? = secrets.getString("key_google_tts", null)?.takeIf { it.isNotBlank() }
    fun setGoogleTtsKey(key: String?) = secrets.edit().apply { if (key.isNullOrBlank()) remove("key_google_tts") else putString("key_google_tts", key.trim()) }.apply()
    fun wipeAllSecrets() = secrets.edit().clear().apply()

    // ---- non-secret settings ----
    var provider: Provider get() = Provider.fromId(prefs.getString("provider", null)); set(v) = prefs.edit().putString("provider", v.id).apply()
    fun model(p: Provider): String? = prefs.getString("model_${p.id}", null)
    fun setModel(p: Provider, m: String) = prefs.edit().putString("model_${p.id}", m).apply()
    var voiceEngine: String get() = prefs.getString("voice_engine", "android") ?: "android"; set(v) = prefs.edit().putString("voice_engine", v).apply()
    var ttsVoiceName: String? get() = prefs.getString("tts_voice", null); set(v) = prefs.edit().putString("tts_voice", v).apply()
    var styleId: String get() = prefs.getString("style", "deep") ?: "deep"; set(v) = prefs.edit().putString("style", v).apply()
    var pitch: Float get() = prefs.getFloat("pitch", 0.95f); set(v) = prefs.edit().putFloat("pitch", v).apply()
    var speed: Float get() = prefs.getFloat("speed", 1.0f); set(v) = prefs.edit().putFloat("speed", v).apply()
    var elevenVoiceId: String? get() = prefs.getString("eleven_voice", null); set(v) = prefs.edit().putString("eleven_voice", v).apply()
    var speakReplies: Boolean get() = prefs.getBoolean("speak", true); set(v) = prefs.edit().putBoolean("speak", v).apply()
    var lowPower: Boolean get() = prefs.getBoolean("low_power", false); set(v) = prefs.edit().putBoolean("low_power", v).apply()
    var language: String get() = prefs.getString("lang", "en-US") ?: "en-US"; set(v) = prefs.edit().putString("lang", v).apply()
    var personalityId: String get() = prefs.getString("personality", "assistant") ?: "assistant"; set(v) = prefs.edit().putString("personality", v).apply()
    var brainId: String get() = prefs.getString("brain", "general") ?: "general"; set(v) = prefs.edit().putString("brain", v).apply()
    /** null = Auto: the emotion is detected from the user's words on every message. */
    var emotionId: String? get() = prefs.getString("emotion", null); set(v) = prefs.edit().putString("emotion", v).apply()
    var wakeWord: Boolean get() = prefs.getBoolean("wake_word", false); set(v) = prefs.edit().putBoolean("wake_word", v).apply()
    /** Optional custom wake phrase (in addition to "BADSHAH"). */
    var customWakeWord: String get() = prefs.getString("wake_custom", "") ?: ""; set(v) = prefs.edit().putString("wake_custom", v).apply()

    // ---- Gemini Live (real human voices) ----
    /** Selected Gemini Live voice name, e.g. "Charon" (male) or "Aoede" (female). */
    var liveVoiceName: String get() = prefs.getString("live_voice", "Charon") ?: "Charon"; set(v) = prefs.edit().putString("live_voice", v).apply()
    /** Live-capable model found for the user's own Gemini key (auto-detected, never invented). */
    var liveModel: String? get() = prefs.getString("live_model", null); set(v) = prefs.edit().putString("live_model", v).apply()
    /** Use the Gemini Live engine on the Voice screen when a Gemini key exists. */
    var liveEnabled: Boolean get() = prefs.getBoolean("live_enabled", true); set(v) = prefs.edit().putBoolean("live_enabled", v).apply()
    /** All live-capable models found for the key (comma-separated), best first — the Live engine tries them in order. */
    var liveCandidates: String get() = prefs.getString("live_candidates", "") ?: ""; set(v) = prefs.edit().putString("live_candidates", v).apply()
    /** Which Live setup variant worked last time (0 = full, 1 = no language code, 2 = bare). Tried first next time. */
    var liveSetupLevel: Int get() = prefs.getInt("live_setup_level", 0); set(v) = prefs.edit().putInt("live_setup_level", v).apply()
    /** Why the last Live attempt could not run (shown in Settings + on the call screen). Cleared when Live connects. */
    var lastLiveError: String? get() = prefs.getString("live_last_error", null); set(v) = prefs.edit().putString("live_last_error", v).apply()

    // ---- FT Fusion images & Google voice ----
    /** Image generation model: fusion-uni | fusion-astra */
    var imageModel: String get() = prefs.getString("image_model", "fusion-uni") ?: "fusion-uni"; set(v) = prefs.edit().putString("image_model", v).apply()
    var googleTtsVoice: String get() = prefs.getString("google_tts_voice", "en-US-Journey-D") ?: "en-US-Journey-D"; set(v) = prefs.edit().putString("google_tts_voice", v).apply()

    // ---- assistant identity & look ----
    var assistantName: String get() = prefs.getString("assistant_name", "BADSHAH")?.ifBlank { "BADSHAH" } ?: "BADSHAH"; set(v) = prefs.edit().putString("assistant_name", v).apply()
    /** balanced | short | detailed */
    var responseStyle: String get() = prefs.getString("response_style", "balanced") ?: "balanced"; set(v) = prefs.edit().putString("response_style", v).apply()
    /** photo (his cutout) | girl (AISHA) | logo (crown logo) */
    var avatarChoice: String get() = prefs.getString("avatar", "photo") ?: "photo"; set(v) = prefs.edit().putString("avatar", v).apply()
}
