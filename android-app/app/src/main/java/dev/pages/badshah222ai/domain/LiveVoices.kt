package dev.pages.badshah222ai.domain

/**
 * The REAL voices of BADSHAH AI: Google's Gemini Live prebuilt voices — the same
 * natural, human voices the reference apps (LIA / MYRA) use. These are NOT the
 * phone's robotic text-to-speech voices: the Live API generates native audio with
 * real tone, breathing and emotion. Names and style words are Google's own
 * documented descriptors for each voice.
 */
data class LiveVoice(val name: String, val style: String, val female: Boolean) {
    val label: String get() = "$name — $style"
}

object LiveVoices {
    // Owner's rule (2026-11-17): only Boyfriend-mode and Girlfriend-mode voices —
    // the best few of Google's Gemini Live voices for each mode, MYRA-style.
    // BF default = Charon, GF default = Aoede (MYRA's own pair).
    val male = listOf(
        LiveVoice("Charon", "Informative, clear", false),
        LiveVoice("Fenrir", "Excitable, lively", false),
        LiveVoice("Puck", "Upbeat, cheerful", false),
        LiveVoice("Orus", "Firm, strong", false),
        LiveVoice("Algieba", "Smooth, deep", false),
        LiveVoice("Achird", "Friendly, warm", false),
    )
    val female = listOf(
        LiveVoice("Aoede", "Breezy, sweet", true),
        LiveVoice("Kore", "Firm, confident", true),
        LiveVoice("Zephyr", "Bright, fresh", true),
        LiveVoice("Leda", "Youthful, lovely", true),
        LiveVoice("Sulafat", "Warm, gentle", true),
        LiveVoice("Callirrhoe", "Easy-going, soft", true),
    )
    val all: List<LiveVoice> = male + female
    fun fromName(name: String?): LiveVoice = all.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: male.first()
}
