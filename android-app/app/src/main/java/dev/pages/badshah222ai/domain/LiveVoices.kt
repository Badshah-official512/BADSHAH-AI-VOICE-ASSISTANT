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
    val male = listOf(
        LiveVoice("Charon", "Informative, clear", false),
        LiveVoice("Orus", "Firm, strong", false),
        LiveVoice("Fenrir", "Excitable, lively", false),
        LiveVoice("Puck", "Upbeat, cheerful", false),
        LiveVoice("Algieba", "Smooth, deep", false),
        LiveVoice("Alnilam", "Firm, commanding", false),
        LiveVoice("Enceladus", "Breathy, soft", false),
        LiveVoice("Iapetus", "Clear, crisp", false),
        LiveVoice("Umbriel", "Easy-going, relaxed", false),
        LiveVoice("Rasalgethi", "Informative, steady", false),
        LiveVoice("Sadaltager", "Knowledgeable, wise", false),
        LiveVoice("Zubenelgenubi", "Casual, friendly", false),
        LiveVoice("Achird", "Friendly, warm", false),
        LiveVoice("Algenib", "Gravelly, rugged", false),
    )
    val female = listOf(
        LiveVoice("Aoede", "Breezy, sweet", true),
        LiveVoice("Kore", "Firm, confident", true),
        LiveVoice("Zephyr", "Bright, fresh", true),
        LiveVoice("Leda", "Youthful, lovely", true),
        LiveVoice("Sulafat", "Warm, gentle", true),
        LiveVoice("Callirrhoe", "Easy-going, soft", true),
        LiveVoice("Autonoe", "Bright, shining", true),
        LiveVoice("Despina", "Smooth, silky", true),
        LiveVoice("Erinome", "Clear, pure", true),
        LiveVoice("Laomedeia", "Upbeat, joyful", true),
        LiveVoice("Achernar", "Soft, tender", true),
        LiveVoice("Gacrux", "Mature, graceful", true),
        LiveVoice("Pulcherrima", "Forward, bold", true),
        LiveVoice("Vindemiatrix", "Gentle, kind", true),
        LiveVoice("Sadachbia", "Lively, sparkling", true),
        LiveVoice("Schedar", "Even, calm", true),
    )
    val all: List<LiveVoice> = male + female
    fun fromName(name: String?): LiveVoice = all.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: male.first()
}
