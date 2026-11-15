package dev.pages.badshah222ai.data.ai

import dev.pages.badshah222ai.core.SecureStore
import dev.pages.badshah222ai.data.supabase.SupabaseApi
import dev.pages.badshah222ai.domain.*

/** Provider switching + per-user keys + usage reporting. There is NO shared developer key anywhere. */
class AiRepository(private val store: SecureStore, private val cloud: SupabaseApi) {
    private val clients = mapOf(Provider.GEMINI to GeminiClient(), Provider.GROQ to GroqClient())
    private fun client(p: Provider) = clients.getValue(p)

    fun isConfigured(p: Provider) = store.apiKey(p) != null && store.model(p) != null

    /** Tests the key with a real request and returns the models it may use. */
    suspend fun testConnection(p: Provider, key: String): List<String> {
        require(key.isNotBlank()) { "empty key" }
        val models = client(p).listModels(key.trim())
        if (models.isEmpty()) throw AiException("no_models", "no models", "The key works but no chat models are available on this account.")
        return models
    }

    suspend fun chat(history: List<ChatMessage>, personality: String = "friendly, confident and respectful"): AiReply {
        val p = store.provider
        val key = store.apiKey(p) ?: throw AiException("no_key", "no key", "Add your ${p.label} API key in Settings to start chatting.")
        val model = store.model(p) ?: throw AiException("no_model", "no model", "Test your ${p.label} key and choose a model in Settings.")
        return try {
            val reply = client(p).chat(key, model, SystemPrompt.build(personality, store.language), history.takeLast(20))
            cloud.recordUsage(p.id, model, "chat", true, reply.inputTokens, reply.outputTokens, reply.latencyMs.toInt(), null)
            reply
        } catch (e: AiException) {
            cloud.recordUsage(p.id, model, "chat", false, 0, 0, null, e.code)
            throw e
        }
    }
}
