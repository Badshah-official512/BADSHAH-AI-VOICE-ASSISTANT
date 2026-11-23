package dev.pages.badshah222ai.core

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Secrets + settings for the native app.
 * API keys (BYOK) live ONLY in the encrypted store: AES-256, Android
 * Keystore-backed, never logged, never sent anywhere except the provider
 * the user picked. Non-secret settings live in plain prefs.
 */
class SecureStore(context: Context) {
    private val secrets: SharedPreferences = try {
        createSecrets(context)
    } catch (e: Exception) {
        // Keystore corrupted (rare, e.g. after restore): start clean rather than crash.
        context.deleteSharedPreferences("badshah_secrets")
        createSecrets(context)
    }
    private val prefs: SharedPreferences =
        context.getSharedPreferences("badshah_settings", Context.MODE_PRIVATE)

    private fun createSecrets(context: Context): SharedPreferences {
        val mk = MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
        return EncryptedSharedPreferences.create(
            context, "badshah_secrets", mk,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    // ---- BYOK keys (voice call needs the user's OWN Gemini key) ----
    fun geminiKey(): String? = secrets.getString("key_gemini_byok", null)?.takeIf { it.isNotBlank() }
    fun setGeminiKey(key: String?) = putSecret("key_gemini_byok", key)

    fun elevenKey(): String? = secrets.getString("key_elevenlabs", null)?.takeIf { it.isNotBlank() }
    fun setElevenKey(key: String?) = putSecret("key_elevenlabs", key)

    private fun putSecret(name: String, value: String?) {
        secrets.edit().apply {
            if (value.isNullOrBlank()) remove(name) else putString(name, value.trim())
        }.apply()
    }

    // ---- plain settings ----
    var guestMode: Boolean
        get() = prefs.getBoolean("guest_mode", false)
        set(v) = prefs.edit().putBoolean("guest_mode", v).apply()

    var tourDone: Boolean
        get() = prefs.getBoolean("tour_done", false)
        set(v) = prefs.edit().putBoolean("tour_done", v).apply()

    var chatThemeId: String
        get() = prefs.getString("chat_theme_id", "purple") ?: "purple"
        set(v) = prefs.edit().putString("chat_theme_id", v).apply()

    var liveVoice: String
        get() = prefs.getString("live_voice", "Charon") ?: "Charon"
        set(v) = prefs.edit().putString("live_voice", v).apply()

    var appLanguage: String
        get() = prefs.getString("app_language", "en") ?: "en"
        set(v) = prefs.edit().putString("app_language", v).apply()

    var avatarId: String
        get() = prefs.getString("avatar_id", "hero") ?: "hero"
        set(v) = prefs.edit().putString("avatar_id", v).apply()

    /** BCP-47 code for the Live backup setup variant (native-audio models pick
     *  the language themselves in the primary setup; this is only a fallback). */
    val languageCode: String
        get() = when (appLanguage) {
            "roman", "urdu" -> "ur-PK"
            else -> "en-US"
        }

    // ---- Live session diagnostics (which model/setup actually connected) ----
    var lastLiveError: String?
        get() = prefs.getString("live_last_error", null)
        set(v) = prefs.edit().putString("live_last_error", v).apply()

    var liveModel: String?
        get() = prefs.getString("live_model", null)
        set(v) = prefs.edit().putString("live_model", v).apply()

    var liveSetupLevel: Int
        get() = prefs.getInt("live_setup_level", 0)
        set(v) = prefs.edit().putInt("live_setup_level", v).apply()

    var liveVersion: String?
        get() = prefs.getString("live_version", null)
        set(v) = prefs.edit().putString("live_version", v).apply()
}
