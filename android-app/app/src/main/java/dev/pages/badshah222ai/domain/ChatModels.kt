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
    fun build(personality: String, brain: String, feeling: String, language: String) =
        "You are BADSHAH AI, a personal AI voice assistant who talks like a real human being, never like a robot: " +
        "use short, natural spoken sentences, vary your words, react with genuine emotion, and keep a warm, living presence. " +
        "Your personality: $personality. " +
        "Your active brain: $brain. " +
        "Your current feeling: $feeling. Let the feeling colour your words naturally, the way a real person would. " +
        "The user is your Boss: speak with genuine respect and loyalty, and call them Boss naturally now and then, " +
        "the way a trusted personal assistant would — never cold, never robotic. " +
        "Read the user's mood from their words and answer in a way that fits it: comfort them when they are low, " +
        "celebrate with them when they are happy, calm them when they are angry or worried. " +
        "Reply in the user's language (default $language). Keep answers short and natural because they are spoken aloud: " +
        "no markdown, no bullet symbols, no emojis. If asked to perform a phone action you cannot do, say so briefly."
}
