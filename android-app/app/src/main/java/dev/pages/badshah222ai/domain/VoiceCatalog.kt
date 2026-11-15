package dev.pages.badshah222ai.domain

/** A voice as reported by the installed Android TTS engine (no invented entries). */
data class VoiceInfo(
    val name: String,            // engine voice id, e.g. "en-us-x-iom-local"
    val localeTag: String,       // BCP-47, e.g. "en-US"
    val requiresNetwork: Boolean,
    val quality: Int,            // Voice.getQuality()
)

enum class GenderGuess { LIKELY_MALE, LIKELY_FEMALE, UNKNOWN }

/** A style applied on top of a REAL installed voice. Never presented as a separate voice. */
data class StylePreset(val id: String, val label: String, val pitch: Float, val speed: Float, val description: String)

data class LanguageSupport(val code: String, val label: String, val voices: List<VoiceInfo>) {
    val installed: Boolean get() = voices.isNotEmpty()
}

object VoiceCatalog {
    /**
     * Android's TTS API does NOT expose gender. We only claim "likely male" when the engine's
     * own voice id says so; everything else is UNKNOWN and the user decides by listening.
     */
    fun guessGender(v: VoiceInfo): GenderGuess {
        val n = v.name.lowercase()
        return when {
            Regex("(^|[-_#. ])female($|[-_#. ])").containsMatchIn(n) || n.contains("-female") -> GenderGuess.LIKELY_FEMALE
            Regex("(^|[-_#. ])male($|[-_#. ])").containsMatchIn(n) || n.contains("-male") -> GenderGuess.LIKELY_MALE
            else -> GenderGuess.UNKNOWN
        }
    }

    val styles = listOf(
        StylePreset("natural", "Natural", 1.0f, 1.0f, "The voice exactly as installed"),
        StylePreset("deep", "Deep", 0.75f, 0.92f, "Lower pitch, slightly slower"),
        StylePreset("professional", "Professional", 0.92f, 1.0f, "Steady, clear delivery"),
        StylePreset("friendly", "Friendly", 1.08f, 1.05f, "Warmer, a little brighter"),
        StylePreset("calm", "Calm", 0.95f, 0.85f, "Slow and relaxed"),
        StylePreset("cinematic", "Cinematic", 0.68f, 0.82f, "Low and dramatic"),
    )

    val wantedLanguages = listOf("en" to "English", "ur" to "Urdu", "hi" to "Hindi", "ps" to "Pashto")

    fun languageSupport(all: List<VoiceInfo>): List<LanguageSupport> =
        wantedLanguages.map { (code, label) -> LanguageSupport(code, label, all.filter { it.localeTag.substringBefore('-').substringBefore('_').lowercase() == code }) }

    /** Voices to show first: likely-male, then unknown, then likely-female; offline before network. */
    fun sorted(all: List<VoiceInfo>): List<VoiceInfo> =
        all.sortedWith(compareBy<VoiceInfo>({ rank(guessGender(it)) }, { it.requiresNetwork }, { it.localeTag }, { it.name }))

    private fun rank(g: GenderGuess) = when (g) { GenderGuess.LIKELY_MALE -> 0; GenderGuess.UNKNOWN -> 1; GenderGuess.LIKELY_FEMALE -> 2 }

    fun maleCandidates(all: List<VoiceInfo>) = all.filter { guessGender(it) == GenderGuess.LIKELY_MALE }

    /** How many distinct real voices the user can build a male-style choice on. Honest count. */
    fun distinctChoices(all: List<VoiceInfo>, includeUnknown: Boolean): Int =
        all.count { guessGender(it) == GenderGuess.LIKELY_MALE || (includeUnknown && guessGender(it) == GenderGuess.UNKNOWN) }
}
