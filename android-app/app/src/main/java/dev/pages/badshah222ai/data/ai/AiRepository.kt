package dev.pages.badshah222ai.data.ai

import dev.pages.badshah222ai.core.SecureStore
import dev.pages.badshah222ai.data.supabase.SupabaseApi
import dev.pages.badshah222ai.domain.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

/** Provider switching + per-user keys + usage reporting. There is NO shared developer key anywhere. */
class AiRepository(private val store: SecureStore, private val cloud: SupabaseApi) {
    private val clients = mapOf(Provider.GEMINI to GeminiClient(), Provider.GROQ to GroqClient(), Provider.OPENROUTER to OpenRouterClient(), Provider.FUSION to FtFusionClient())
    private fun client(p: Provider) = clients.getValue(p)

    fun isConfigured(p: Provider) = if (p == Provider.FUSION) store.model(p) != null else store.apiKey(p) != null && store.model(p) != null

    /** Tests the key with a real request and returns the models it may use. FT Fusion has no user key — it gets a real ping instead. */
    suspend fun testConnection(p: Provider, key: String): List<String> {
        if (p == Provider.FUSION) {
            FtFusion.chat("fusion-flex", "Hello! Reply with one short friendly sentence.")
            return FtFusion.TEXT_MODELS
        }
        require(key.isNotBlank()) { "empty key" }
        val models = client(p).listModels(key.trim())
        if (models.isEmpty()) throw AiException("no_models", "no models", "The key works but no chat models are available on this account.")
        return models
    }

    /**
     * Finds (and remembers) a Gemini Live-capable model for the user's own Gemini key.
     * Returns null when the key has no live model — the app then uses the classic voice mode.
     */
    suspend fun detectLiveModel(): String? {
        val key = store.apiKey(Provider.GEMINI) ?: return null
        val gemini = clients.getValue(Provider.GEMINI) as GeminiClient
        val live = runCatching { gemini.listLiveModels(key) }.getOrDefault(emptyList())
        val pick = live.firstOrNull()
        if (pick != null) { store.liveModel = pick; store.liveCandidates = live.joinToString(",") }
        return pick
    }

    /** The composed system instruction for the current personality + brain + emotion + identity settings. */
    fun systemPrompt(emotionStyle: String): String = SystemPrompt.build(
        Personalities.fromId(store.personalityId).prompt,
        Brains.fromId(store.brainId).prompt,
        emotionStyle,
        store.language,
        store.assistantName,
        store.responseStyle,
    )

    suspend fun chat(
        history: List<ChatMessage>,
        emotionStyle: String = "Speak warmly and heartily, like a close friend who is genuinely glad to talk.",
    ): AiReply {
        val p = store.provider
        val key = if (p == Provider.FUSION) "" else store.apiKey(p) ?: throw AiException("no_key", "no key", "Add your ${p.label} API key in Settings to start chatting.")
        val model = store.model(p) ?: throw AiException("no_model", "no model", "Test your ${p.label} connection and choose a model in Settings.")
        return try {
            val reply = client(p).chat(key, model, systemPrompt(emotionStyle), history.takeLast(20))
            cloud.recordUsage(p.id, model, "chat", true, reply.inputTokens, reply.outputTokens, reply.latencyMs.toInt(), null)
            reply
        } catch (e: AiException) {
            // FT Fusion is the pre-connected default brain; if its gateway errors, a free
            // backup model answers instead of showing the user an error screen.
            if (p == Provider.FUSION) {
                val t0 = System.currentTimeMillis()
                val backup = runCatching { Pollinations.chat(fallbackPrompt(emotionStyle, history)) }.getOrNull()
                if (backup != null) {
                    cloud.recordUsage("pollinations", "openai-fast", "chat", true, 0, 0, (System.currentTimeMillis() - t0).toInt(), null)
                    return AiReply(backup, Provider.FUSION, "openai-fast (free backup)", 0, 0, System.currentTimeMillis() - t0)
                }
            }
            cloud.recordUsage(p.id, model, "chat", false, 0, 0, null, e.code)
            throw e
        }
    }

    /** Flattens the system prompt + recent turns into one prompt for the plain-text backup model. */
    private fun fallbackPrompt(emotionStyle: String, history: List<ChatMessage>): String {
        val sb = StringBuilder(systemPrompt(emotionStyle))
        history.takeLast(8).forEach { m ->
            sb.append(if (m.role == "user") "\nUser: " else "\nAssistant: ").append(m.content.take(1200))
        }
        sb.append("\nReply as the Assistant to the last User message.")
        return sb.toString()
    }

    /**
     * Image understanding (screen share, photos) via the user's own Gemini key —
     * the same key Live voice needs. Direct HTTP to the official Gemini endpoint.
     */
    suspend fun vision(prompt: String, imageBase64: String, mimeType: String = "image/jpeg"): AiReply {
        val key = store.apiKey(Provider.GEMINI) ?: throw AiException(
            "no_key", "no gemini key",
            "Screen samajhne ke liye pehle Settings → Connections mein apni Google Gemini API key lagao, Boss — yehi key Live voice ke liye bhi chahiye hoti hai.",
        )
        val model = store.model(Provider.GEMINI)?.takeIf { it.contains("flash") || it.contains("pro") } ?: "gemini-2.5-flash"
        val t0 = System.currentTimeMillis()
        val payload = JSONObject()
            .put("system_instruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text",
                systemPrompt("Speak warmly and heartily, like a close friend who is genuinely glad to talk.")))))
            .put("contents", JSONArray().put(JSONObject().put("parts", JSONArray()
                .put(JSONObject().put("text", prompt))
                .put(JSONObject().put("inline_data", JSONObject().put("mime_type", mimeType).put("data", imageBase64))))))
        val req = Request.Builder()
            .url("https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent")
            .header("x-goog-api-key", key).header("Content-Type", "application/json")
            .post(payload.toString().toRequestBody("application/json".toMediaType()))
            .build()
        val text = withContext(Dispatchers.IO) {
            http.newCall(req).execute().use { res ->
                val bodyStr = res.body?.string().orEmpty()
                if (!res.isSuccessful) throw AiException("vision_${res.code}", bodyStr.take(160), "Gemini vision ne jawab nahi diya (HTTP ${res.code}). Key check karo ya thodi dair baad try karo, Boss.")
                val parts = JSONObject(bodyStr).optJSONArray("candidates")?.optJSONObject(0)
                    ?.optJSONObject("content")?.optJSONArray("parts")
                val sb = StringBuilder()
                if (parts != null) for (i in 0 until parts.length()) sb.append(parts.optJSONObject(i)?.optString("text") ?: "")
                sb.toString()
            }
        }
        if (text.isBlank()) throw AiException("vision_empty", "empty", "Screen parh li, lekin Gemini ne kuch bataya nahi. Dobara try karo, Boss.")
        cloud.recordUsage("gemini", model, "vision", true, 0, 0, (System.currentTimeMillis() - t0).toInt(), null)
        return AiReply(text.trim(), Provider.GEMINI, model, 0, 0, System.currentTimeMillis() - t0)
    }
}
