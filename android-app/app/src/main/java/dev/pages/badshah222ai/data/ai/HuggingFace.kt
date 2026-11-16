package dev.pages.badshah222ai.data.ai

import dev.pages.badshah222ai.domain.AiException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Hugging Face Inference — free-tier image generation with the user's own free token
 * (huggingface.co → Settings → Access Tokens). Unlike the hosted-link providers this
 * returns the picture BYTES, which the app saves to a local file and shows in chat.
 */
object HuggingFace {
    private val http = OkHttpClient.Builder().connectTimeout(20, TimeUnit.SECONDS).readTimeout(120, TimeUnit.SECONDS).build()

    val MODELS = listOf(
        "black-forest-labs/FLUX.1-schnell" to "FLUX.1 Schnell (fast)",
        "stabilityai/stable-diffusion-xl-base-1.0" to "SDXL",
        "black-forest-labs/FLUX.1-dev" to "FLUX.1 Dev",
    )

    suspend fun image(token: String, model: String, prompt: String): ByteArray = withContext(Dispatchers.IO) {
        val body = JSONObject().put("inputs", prompt).toString().toRequestBody("application/json".toMediaType())
        val req = Request.Builder()
            .url("https://router.huggingface.co/hf-inference/models/$model")
            .header("Authorization", "Bearer ${token.trim()}")
            .post(body)
            .build()
        http.newCall(req).execute().use { res ->
            val bytes = res.body?.bytes() ?: ByteArray(0)
            if (!res.isSuccessful) {
                val note = when (res.code) {
                    401, 403 -> "Hugging Face token ghalat hai ya expire ho gaya — Settings → Image Studio mein naya free token lagao."
                    503 -> "Hugging Face model abhi load ho raha hai, thodi dair baad phir try karo."
                    429 -> "Hugging Face free limit khatam ho gayi hai abhi ke liye."
                    else -> "Hugging Face ne jawab nahi diya (HTTP ${res.code})."
                }
                throw AiException("hf_${res.code}", "HF HTTP ${res.code}", note)
            }
            if (bytes.size < 2000) throw AiException("hf_small", "tiny response", "Hugging Face se sahi image nahi mili.")
            bytes
        }
    }
}
