package dev.pages.badshah222ai.domain

/**
 * A voice mode: a named, one-tap voice built from a REAL installed TTS voice plus a
 * pitch/speed character. [voiceIndex] picks from the gender pool at apply time
 * (male pool = likely-male + unverified voices, female pool = likely-female voices),
 * so different modes genuinely sound different whenever the phone has several voices.
 * For hundreds more premium voices the user can connect ElevenLabs (optional, Settings).
 */
data class VoiceMode(
    val id: String, val label: String, val description: String,
    val female: Boolean, val voiceIndex: Int, val pitch: Float, val speed: Float,
)

object VoiceModes {
    val male = listOf(
        VoiceMode("m_badshah", "Badshah Deep", "Low, powerful, confident", false, 0, 0.72f, 0.92f),
        VoiceMode("m_sultan", "Sultan", "Heavy royal voice, slow and grand", false, 1, 0.68f, 0.85f),
        VoiceMode("m_commander", "Commander", "Firm and clear, like an officer", false, 2, 0.80f, 0.98f),
        VoiceMode("m_professor", "Professor", "Calm, wise, measured", false, 3, 0.85f, 0.90f),
        VoiceMode("m_hero", "Hero", "Bold and energetic", false, 0, 0.88f, 1.02f),
        VoiceMode("m_rj", "RJ Star", "Radio jockey — lively and bright", false, 1, 0.95f, 1.08f),
        VoiceMode("m_storyteller", "Storyteller", "Warm and slow, made for stories", false, 2, 0.82f, 0.88f),
        VoiceMode("m_boss", "Boss", "Short, sharp, no-nonsense", false, 3, 0.78f, 1.00f),
        VoiceMode("m_friend", "Best Friend", "Natural, easy, everyday", false, 0, 0.95f, 1.00f),
        VoiceMode("m_champion", "Champion", "Motivating sports energy", false, 1, 1.00f, 1.05f),
        VoiceMode("m_cinematic", "Cinematic", "Movie-trailer depth", false, 2, 0.65f, 0.82f),
        VoiceMode("m_news", "News Anchor", "Clean broadcast delivery", false, 3, 0.90f, 0.97f),
    )

    val female = listOf(
        VoiceMode("f_sweet", "Ayesha — Sweet", "Soft, sweet and friendly", true, 0, 1.18f, 0.98f),
        VoiceMode("f_bright", "Mahnoor — Bright", "Cheerful and clear", true, 1, 1.25f, 1.03f),
        VoiceMode("f_calm", "Zara — Calm", "Gentle and relaxed", true, 2, 1.12f, 0.90f),
    )

    val all: List<VoiceMode> = male + female

    fun fromId(id: String?): VoiceMode? = all.firstOrNull { it.id == id }

    /** Resolve a mode to a real installed voice name (null = keep the engine default). */
    fun resolveVoiceName(mode: VoiceMode, installed: List<VoiceInfo>): String? {
        if (installed.isEmpty()) return null
        val sortedAll = VoiceCatalog.sorted(installed)
        val pool = if (mode.female) {
            val f = sortedAll.filter { VoiceCatalog.guessGender(it) == GenderGuess.LIKELY_FEMALE }
            if (f.isNotEmpty()) f else sortedAll
        } else {
            val m = sortedAll.filter { VoiceCatalog.guessGender(it) != GenderGuess.LIKELY_FEMALE }
            if (m.isNotEmpty()) m else sortedAll
        }
        return pool[mode.voiceIndex % pool.size].name
    }
}
