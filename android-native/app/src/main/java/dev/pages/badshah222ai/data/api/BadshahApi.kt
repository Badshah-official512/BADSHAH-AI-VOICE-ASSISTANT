package dev.pages.badshah222ai.data.api

import dev.pages.badshah222ai.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * Native client for the existing backend — NOTHING on the server changes.
 * Worker: https://badshah222-ai.pages.dev (routes verified in _worker.js).
 * Supabase REST is a separate client (reuse SupabaseApi from the old project, Phase 2).
 *
 * Endpoints (all relative to WORKER_BASE_URL):
 *   GET  /api/chat?model=..&q=.. | ?provider=groq&q=..
 *   GET  /api/image, /api/video, /api/voices, /api/news, /api/config
 *   GET  /api/live-key (login required — Firebase token header, Phase 2)
 *   POST /api/tts {text, voice} -> audio/mpeg
 *   POST /api/stt (multipart audio -> Groq Whisper)
 *   GET/POST/DELETE /api/memories
 *   GET  /api/tools/<tiktok|lyrics-search|lyrics-get|wiki|ask|fact|shorten|tts2>
 *   GET/POST /api/dost, /api/device, /api/agent/...
 *   POST /admin-api/<action> (x-admin-password header)
 */
sealed class ApiResult<out T> {
    data class Ok<T>(val value: T) : ApiResult<T>()
    data class Err(val message: String, val status: Int = 0) : ApiResult<Nothing>()
}

class BadshahApi(
    private val baseUrl: String = BuildConfig.WORKER_BASE_URL,
    private val token: suspend () -> String? = { null },
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    private suspend fun get(path: String, auth: Boolean = false): ApiResult<String> =
        withContext(Dispatchers.IO) {
            try {
                val b = Request.Builder().url(baseUrl + path)
                if (auth) token()?.let { b.header("Authorization", "Bearer $it") }
                client.newCall(b.build()).execute().use { r ->
                    val body = r.body?.string().orEmpty()
                    if (r.isSuccessful) ApiResult.Ok(body) else ApiResult.Err(body.ifBlank { "HTTP ${r.code}" }, r.code)
                }
            } catch (e: Exception) {
                ApiResult.Err(e.message ?: "Network error")
            }
        }

    private suspend fun postJson(path: String, json: JSONObject, auth: Boolean = false): ApiResult<ByteArray> =
        withContext(Dispatchers.IO) {
            try {
                val b = Request.Builder().url(baseUrl + path)
                    .post(json.toString().toRequestBody("application/json".toMediaType()))
                if (auth) token()?.let { b.header("Authorization", "Bearer $it") }
                client.newCall(b.build()).execute().use { r ->
                    val bytes = r.body?.bytes() ?: ByteArray(0)
                    if (r.isSuccessful) ApiResult.Ok(bytes) else ApiResult.Err(String(bytes).ifBlank { "HTTP ${r.code}" }, r.code)
                }
            } catch (e: Exception) {
                ApiResult.Err(e.message ?: "Network error")
            }
        }

    private suspend fun postJsonAuthed(path: String, json: JSONObject): ApiResult<String> =
        when (val r = postJson(path, json, auth = true)) {
            is ApiResult.Ok -> ApiResult.Ok(String(r.value))
            is ApiResult.Err -> r
        }

    private suspend fun delete(path: String, auth: Boolean = false): ApiResult<String> =
        withContext(Dispatchers.IO) {
            try {
                val b = Request.Builder().url(baseUrl + path).delete()
                if (auth) token()?.let { b.header("Authorization", "Bearer $it") }
                client.newCall(b.build()).execute().use { r ->
                    val body = r.body?.string().orEmpty()
                    if (r.isSuccessful) ApiResult.Ok(body) else ApiResult.Err(body.ifBlank { "HTTP ${r.code}" }, r.code)
                }
            } catch (e: Exception) {
                ApiResult.Err(e.message ?: "Network error")
            }
        }

    /** Chat — model ids are BARE (e.g. "gemini-3.8-flash"); returns the raw JSON. */
    suspend fun chat(model: String, query: String): ApiResult<String> =
        get("/api/chat?model=${enc(model)}&q=${enc(query)}")

    /** Chat with the reply text parsed out of the worker's {text, provider, model} JSON. */
    suspend fun chatReply(model: String, query: String): ApiResult<String> =
        when (val r = chat(model, query)) {
            is ApiResult.Err -> r
            is ApiResult.Ok -> try {
                val j = JSONObject(r.value)
                val text = j.optString("text")
                if (text.isNotBlank()) ApiResult.Ok(text)
                else ApiResult.Err(j.optString("error").ifBlank { "Empty reply" })
            } catch (e: Exception) {
                ApiResult.Ok(r.value)
            }
        }

    /**
     * Vision Q&A with the user's OWN Gemini key (BYOK — the worker's /api/chat
     * is text-only). Feature #10: sawal ki photo -> hal. The key never leaves
     * the phone except to Google's API. [base64Jpeg] is a JPEG, base64 (no wrap).
     */
    suspend fun visionAnswer(apiKey: String, base64Jpeg: String, question: String): ApiResult<String> =
        withContext(Dispatchers.IO) {
            try {
                val body = JSONObject().put(
                    "contents",
                    JSONArray().put(
                        JSONObject().put(
                            "parts",
                            JSONArray()
                                .put(JSONObject().put("text", question))
                                .put(
                                    JSONObject().put(
                                        "inline_data",
                                        JSONObject().put("mime_type", "image/jpeg").put("data", base64Jpeg),
                                    ),
                                ),
                        ),
                    ),
                )
                val req = Request.Builder()
                    .url("https://generativelanguage.googleapis.com/v1beta/models/gemini-3.8-flash:generateContent")
                    .header("x-goog-api-key", apiKey)
                    .header("Content-Type", "application/json")
                    .post(body.toString().toRequestBody("application/json".toMediaType()))
                    .build()
                client.newCall(req).execute().use { resp ->
                    val text = resp.body?.string().orEmpty()
                    if (!resp.isSuccessful) {
                        return@withContext ApiResult.Err("Vision error ${resp.code}", resp.code)
                    }
                    val out = runCatching {
                        JSONObject(text)
                            .getJSONArray("candidates").getJSONObject(0)
                            .getJSONObject("content").getJSONArray("parts")
                            .getJSONObject(0).getString("text")
                    }.getOrNull()
                    if (!out.isNullOrBlank()) ApiResult.Ok(out) else ApiResult.Err("Empty vision reply")
                }
            } catch (e: Exception) {
                ApiResult.Err(e.message ?: "Vision request failed")
            }
        }

    /** Groq chat path (?provider=groq). */
    suspend fun chatGroq(query: String): ApiResult<String> =
        get("/api/chat?provider=groq&q=${enc(query)}")

    /** Pooled Gemini key for Live calls — login required (Phase 2 wires the Firebase token). */
    suspend fun liveKey(): ApiResult<String> = get("/api/live-key", auth = true)

    /** ElevenLabs TTS via the pooled key — returns MP3 bytes. */
    suspend fun tts(text: String, voice: String): ApiResult<ByteArray> =
        postJson("/api/tts", JSONObject().put("text", text).put("voice", voice))

    /** Image generation — returns the raw JSON (image url / base64 per worker). */
    suspend fun image(prompt: String, model: String = "gemini-3.1-flash-image"): ApiResult<String> =
        get("/api/image?model=${enc(model)}&q=${enc(prompt)}")

    /** Long-term memories (Yaad) — GET returns {memories:[{id,kind,text,created_at}]}. */
    suspend fun memories(): ApiResult<String> = get("/api/memories", auth = true)

    /** Add a memory. kind: note | zaat | kaam | pasand. */
    suspend fun addMemory(text: String, kind: String = "note"): ApiResult<String> =
        postJsonAuthed("/api/memories", JSONObject().put("text", text).put("kind", kind))

    /** Delete one memory by id. */
    suspend fun deleteMemory(id: String): ApiResult<String> =
        delete("/api/memories?id=${enc(id)}", auth = true)

    /** Admin gateway — POST /admin-api/<action> with the x-admin-password header. */
    suspend fun admin(action: String, body: JSONObject, password: String): ApiResult<String> =
        withContext(Dispatchers.IO) {
            try {
                val req = Request.Builder().url(baseUrl + "/admin-api/" + action)
                    .header("x-admin-password", password)
                    .post(body.toString().toRequestBody("application/json".toMediaType()))
                    .build()
                client.newCall(req).execute().use { r ->
                    val text = r.body?.string().orEmpty()
                    if (r.isSuccessful) ApiResult.Ok(text) else ApiResult.Err(text.ifBlank { "HTTP ${r.code}" }, r.code)
                }
            } catch (e: Exception) {
                ApiResult.Err(e.message ?: "Network error")
            }
        }

    /** One tools endpoint, e.g. tools("wiki", mapOf("q" to "Lahore")). */
    suspend fun tool(name: String, params: Map<String, String> = emptyMap()): ApiResult<String> {
        val qs = params.entries.joinToString("&") { "${enc(it.key)}=${enc(it.value)}" }
        return get("/api/tools/$name" + if (qs.isEmpty()) "" else "?$qs")
    }

    /** Khabrein (free RSS). */
    suspend fun news(): ApiResult<String> = get("/api/news")

    /** App config / maintenance message. */
    suspend fun config(): ApiResult<String> = get("/api/config")
}
