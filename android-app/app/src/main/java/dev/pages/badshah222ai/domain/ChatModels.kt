package dev.pages.badshah222ai.domain

enum class Provider(val id: String, val label: String, val keyUrl: String) {
    GEMINI("gemini", "Google Gemini", "https://aistudio.google.com/app/apikey"),
    GROQ("groq", "Groq", "https://console.groq.com/keys");
    companion object { fun fromId(id: String?) = entries.firstOrNull { it.id == id } ?: GEMINI }
}

data class ChatMessage(val role: String, val content: String, val timeMs: Long = System.currentTimeMillis()) // role: user|assistant

data class AiReply(
    val text: String, val provider: Provider, val model: String,
    val inputTokens: Long, val outputTokens: Long, val latencyMs: Long,
)

class AiException(val code: String, message: String, val userMessage: String) : Exception(message)

object SystemPrompt {
    fun build(personality: String, language: String) =
        "You are BADSHAH AI, a personal AI voice assistant. Your tone is $personality. " +
        "Reply in the user's language (default $language). Keep answers short and natural because they are spoken aloud: " +
        "no markdown, no bullet symbols, no emojis. If asked to perform a phone action you cannot do, say so briefly."
}
