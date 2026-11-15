package dev.pages.badshah222ai.data.voice

import dev.pages.badshah222ai.data.ai.http
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException

class ElevenException(val code: String, val userMessage: String) : Exception(userMessage)
data class ElevenVoice(val id: String, val name: String, val category: String)

/** OPTIONAL premium voices. Completely independent of Gemini/Groq. The user supplies their own key. */
class ElevenLabsClient {
    private val base = "https://api.elevenlabs.io/v1"

    suspend fun listVoices(key: String): List<ElevenVoice> = withContext(Dispatchers.IO) {
        try {
            http.newCall(Request.Builder().url("$base/voices").header("xi-api-key", key).build()).execute().use { r ->
                val body = r.body?.string().orEmpty()
                if (!r.isSuccessful) throw map(r.code)
                val a = JSONObject(body).optJSONArray("voices") ?: return@use emptyList<ElevenVoice>()
                (0 until a.length()).map { a.getJSONObject(it).let { o -> ElevenVoice(o.getString("voice_id"), o.optString("name", "Voice"), o.optString("category", "")) } }
            }
        } catch (e: IOException) { throw ElevenException("network", "No connection to ElevenLabs.") }
    }

    suspend fun synthesize(key: String, voiceId: String, text: String): ByteArray = withContext(Dispatchers.IO) {
        val payload = JSONObject().put("text", text).put("model_id", "eleven_multilingual_v2").toString()
        try {
            http.newCall(Request.Builder().url("$base/text-to-speech/$voiceId?output_format=mp3_44100_128")
                .header("xi-api-key", key).header("Accept", "audio/mpeg")
                .post(payload.toRequestBody("application/json".toMediaType())).build()).execute().use { r ->
                if (!r.isSuccessful) throw map(r.code)
                r.body?.bytes() ?: throw ElevenException("empty", "ElevenLabs returned no audio.")
            }
        } catch (e: IOException) { throw ElevenException("network", "No connection to ElevenLabs.") }
    }

    private fun map(code: Int) = when (code) {
        401 -> ElevenException("invalid_key", "ElevenLabs rejected the API key.")
        402, 429 -> ElevenException("quota", "ElevenLabs quota or rate limit reached.")
        404 -> ElevenException("voice_not_found", "That ElevenLabs voice is no longer available.")
        else -> ElevenException("http_$code", "ElevenLabs error (HTTP $code).")
    }
}
