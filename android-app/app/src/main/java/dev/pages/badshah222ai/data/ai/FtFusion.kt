package dev.pages.badshah222ai.data.ai

import dev.pages.badshah222ai.domain.AiException
import dev.pages.badshah222ai.domain.AiReply
import dev.pages.badshah222ai.domain.ChatMessage
import dev.pages.badshah222ai.domain.Provider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * FT Fusion — the owner's own AI gateway (built by the FT team), integrated at the
 * owner's explicit instruction with its key built in:
 *  - 12 text models for chat and coding
 *  - fusion-uni / fusion-astra for image generation (returns a hosted image URL)
 * The gateway answers JSON: {status:"success", ...} or {status:"error", error:"..."}.
 * Server errors are surfaced honestly to the user — never faked into a reply.
 */
object FtFusion {
    private const val BASE = "https://ft-fusion.shehryarteam8876.workers.dev/ft/api"
    private const val KEY = "ft-12fcb8038e7ee88b97f993063412e203fa085116bc2cd658"

    val TEXT_MODELS = listOf(
        "fusion-flex", "fusion-max", "fusion-pulse", "fusion-spark", "fusion-lexus", "fusion-synth",
        "fusion-void", "fusion-reaper", "fusion-dread", "fusion-shadow", "fusion-abyss", "fusion-vortex",
    )
    val IMAGE_MODELS = listOf("fusion-uni", "fusion-astra")

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS).readTimeout(120, TimeUnit.SECONDS).build()

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    private suspend fun call(model: String, q: String): JSONObject = withContext(Dispatchers.IO) {
        val url = "$BASE?key=${enc(KEY)}&model=${enc(model)}&q=${enc(q)}"
        val res = client.newCall(Request.Builder().url(url).build()).execute()
        val body = res.body?.string().orEmpty()
        val json = runCatching { JSONObject(body) }.getOrNull()
            ?: throw AiException("fusion_bad_response", "bad response", "FT Fusion ne ajeeb jawab diya. Dobara try karo.")
        if (json.optString("status") != "success") {
            val err = json.optString("error").ifBlank { "FT Fusion server error" }
            throw AiException("fusion_error", err, "FT Fusion: $err")
        }
        json
    }

    /** One text/coding completion. [prompt] is the fully composed conversation text. */
    suspend fun chat(model: String, prompt: String): String {
        val json = call(model, prompt)
        val reply = sequenceOf("response", "reply", "text", "answer", "message", "output")
            .map { json.optString(it) }.firstOrNull { it.isNotBlank() }
            ?: json.optJSONObject("data")?.let { d -> sequenceOf("response", "reply", "text", "answer").map { d.optString(it) }.firstOrNull { it.isNotBlank() } }
            ?: throw AiException("fusion_empty", "empty reply", "FT Fusion ne khaali jawab diya. Dobara try karo.")
        return reply
    }

    /** Generates an image and returns its hosted URL (valid for about an hour). */
    suspend fun image(model: String, prompt: String): String {
        val json = call(model, prompt)
        val url = json.optJSONObject("image")?.optString("image_url").orEmpty()
        if (url.isBlank()) throw AiException("fusion_no_image", "no image", "Image nahi ban saka. Dobara try karo.")
        return url
    }
}

/** AiClient adapter so FT Fusion sits next to Gemini/Groq in provider switching. */
class FtFusionClient : AiClient {
    override val provider = Provider.FUSION
    override suspend fun listModels(apiKey: String): List<String> = FtFusion.TEXT_MODELS
    override suspend fun chat(apiKey: String, model: String, system: String, history: List<ChatMessage>): AiReply {
        val t0 = System.currentTimeMillis()
        val sb = StringBuilder(system)
        sb.append("\n\nConversation so far:\n")
        history.takeLast(12).forEach { m -> sb.append(if (m.role == "user") "User: " else "Assistant: ").append(m.content).append('\n') }
        sb.append("\nReply as the Assistant to the last User message. If files/code were requested, follow the file-block format exactly.")
        val text = FtFusion.chat(model, sb.toString().take(9000))
        return AiReply(text, Provider.FUSION, model, 0, 0, System.currentTimeMillis() - t0)
    }
}
