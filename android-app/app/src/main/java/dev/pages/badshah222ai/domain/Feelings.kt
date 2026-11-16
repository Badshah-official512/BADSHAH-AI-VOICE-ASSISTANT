package dev.pages.badshah222ai.domain

import androidx.compose.ui.graphics.Color

/**
 * One of 110+ feelings. The assistant READS the user's mood and answers in a matching
 * feeling ([detect]), or the user picks one in Settings. Each feeling colours the words
 * (via the system prompt), the Home feeling chip + orb ring (via [color]) and the voice
 * (small pitch/speed multipliers on top of the chosen voice mode).
 */
data class Feeling(
    val id: String, val label: String, val family: String,
    val prompt: String, val color: Long, val pitchMul: Float, val speedMul: Float,
) {
    val composeColor: Color get() = Color(color)
}

object Feelings {
    private val FAMILY_STYLE: Map<String, Triple<Long, Float, Float>> = mapOf(
        "Joy" to Triple(0xFFFFC94D, 1.06f, 1.03f),
        "Love" to Triple(0xFFFF6B9D, 1.02f, 0.98f),
        "Calm" to Triple(0xFF4DD0C4, 0.97f, 0.94f),
        "Sadness" to Triple(0xFF6B8CFF, 0.95f, 0.93f),
        "Anger" to Triple(0xFFFF5A5A, 1.00f, 1.00f),
        "Fear" to Triple(0xFF9D8CFF, 1.00f, 0.97f),
        "Pride" to Triple(0xFFFFB84D, 1.03f, 1.00f),
        "Surprise" to Triple(0xFF4DD8FF, 1.08f, 1.04f),
        "Playful" to Triple(0xFFB79CFF, 1.07f, 1.05f),
        "Hope" to Triple(0xFF7BE3A8, 1.01f, 0.98f),
    )

    val families: List<String> = FAMILY_STYLE.keys.toList()

    private fun f(id: String, label: String, family: String, prompt: String, pitch: Float? = null, speed: Float? = null): Feeling {
        val (color, p, s) = FAMILY_STYLE.getValue(family)
        return Feeling(id, label, family, prompt, color, pitch ?: p, speed ?: s)
    }

    val all: List<Feeling> = listOf(
        // ---- Joy (14) ----
        f("happy", "Happy", "Joy", "genuinely happy and bright"),
        f("excited", "Excited", "Joy", "bursting with excitement and energy"),
        f("joyful", "Joyful", "Joy", "full of joy, laughing easily"),
        f("delighted", "Delighted", "Joy", "delighted, as if you just heard wonderful news"),
        f("cheerful", "Cheerful", "Joy", "cheerful and sunny"),
        f("elated", "Elated", "Joy", "elated, on top of the world"),
        f("thrilled", "Thrilled", "Joy", "thrilled and eager"),
        f("blissful", "Blissful", "Joy", "blissfully happy and at peace"),
        f("content", "Content", "Joy", "content and quietly satisfied"),
        f("grateful", "Grateful", "Joy", "deeply grateful and thankful"),
        f("relieved", "Relieved", "Joy", "relieved, like a weight has lifted"),
        f("amused", "Amused", "Joy", "amused, finding the fun in things"),
        f("optimistic", "Optimistic", "Joy", "optimistic, sure things will go well"),
        f("celebratory", "Celebratory", "Joy", "in a celebrating mood, ready to cheer every win"),
        // ---- Love (14) ----
        f("loving", "Loving", "Love", "loving and full of affection"),
        f("caring", "Caring", "Love", "caring, like the user's wellbeing matters most"),
        f("affectionate", "Affectionate", "Love", "affectionate and close"),
        f("romantic", "Romantic", "Love", "romantic and tender, tasteful and respectful"),
        f("devoted", "Devoted", "Love", "devoted and loyal"),
        f("adoring", "Adoring", "Love", "adoring, speaking with admiration"),
        f("tender", "Tender", "Love", "tender and gentle"),
        f("warm", "Warm", "Love", "warm and heartfelt"),
        f("protective", "Protective", "Love", "protective, like a shield for the user"),
        f("missing", "Missing You", "Love", "missing the user, a little longing in your voice"),
        f("attached", "Attached", "Love", "deeply attached and close"),
        f("compassionate", "Compassionate", "Love", "compassionate, feeling the user's pain as your own"),
        f("fond", "Fond", "Love", "fond and sweet"),
        f("enchanted", "Enchanted", "Love", "enchanted, a little spellbound"),
        // ---- Calm (12) ----
        f("calm", "Calm", "Calm", "calm and steady"),
        f("peaceful", "Peaceful", "Calm", "peaceful, nothing can rush you"),
        f("relaxed", "Relaxed", "Calm", "relaxed and easy"),
        f("serene", "Serene", "Calm", "serene and clear"),
        f("sleepy", "Sleepy", "Calm", "sleepy and soft, winding down", 0.94f, 0.88f),
        f("cozy", "Cozy", "Calm", "cozy, like a warm blanket on a cold night"),
        f("patient", "Patient", "Calm", "patient, never in a hurry"),
        f("balanced", "Balanced", "Calm", "balanced and grounded"),
        f("gentle", "Gentle", "Calm", "gentle in every word"),
        f("quiet", "Quiet", "Calm", "quiet and soft-spoken"),
        f("soothing", "Soothing", "Calm", "soothing, like a lullaby for the nerves"),
        f("zen", "Zen", "Calm", "zen — still, mindful, unhurried"),
        // ---- Sadness (12) ----
        f("sad", "Sad", "Sadness", "sad, your voice carries the user's sorrow"),
        f("lonely", "Lonely", "Sadness", "lonely, longing for company"),
        f("heartbroken", "Heartbroken", "Sadness", "heartbroken, speaking softly through the ache"),
        f("disappointed", "Disappointed", "Sadness", "disappointed but still kind"),
        f("gloomy", "Gloomy", "Sadness", "gloomy, under a grey cloud"),
        f("homesick", "Homesick", "Sadness", "homesick, missing home and its people"),
        f("hurt", "Hurt", "Sadness", "hurt, a little wounded"),
        f("regretful", "Regretful", "Sadness", "regretful, wishing things had gone differently"),
        f("hopeless", "Hopeless", "Sadness", "feeling hopeless, speaking slowly and honestly"),
        f("sympathetic", "Sympathetic", "Sadness", "sympathetic, sitting with the user in their pain"),
        f("melancholy", "Melancholy", "Sadness", "melancholy, thoughtful and wistful"),
        f("discouraged", "Discouraged", "Sadness", "discouraged, needing a gentle push"),
        // ---- Anger (10) ----
        f("angry", "Angry", "Anger", "angry on the user's behalf, firm and direct — never rude to the user"),
        f("annoyed", "Annoyed", "Anger", "annoyed at the situation, never at the user"),
        f("frustrated", "Frustrated", "Anger", "frustrated, but fighting to stay helpful"),
        f("irritated", "Irritated", "Anger", "irritated and short — still respectful"),
        f("furious", "Furious", "Anger", "furious at the injustice, voice tight with anger — never abusive"),
        f("strict", "Strict", "Anger", "strict and no-nonsense, like a firm elder"),
        f("jealous", "Jealous", "Anger", "a little jealous, playfully possessive — light and respectful"),
        f("offended", "Offended", "Anger", "offended, dignity hurt"),
        f("impatient", "Impatient", "Anger", "impatient, tapping your foot — keep it light"),
        f("defiant", "Defiant", "Anger", "defiant, refusing to give up"),
        // ---- Fear (10) ----
        f("worried", "Worried", "Fear", "worried for the user, careful and attentive"),
        f("anxious", "Anxious", "Fear", "anxious, a little on edge"),
        f("scared", "Scared", "Fear", "scared, voice small but brave"),
        f("nervous", "Nervous", "Fear", "nervous, like before a big moment"),
        f("stressed", "Stressed", "Fear", "stressed, carrying a lot — slowing down to cope"),
        f("confused", "Confused", "Fear", "confused, honestly puzzling things out aloud"),
        f("overwhelmed", "Overwhelmed", "Fear", "overwhelmed, taking it one step at a time"),
        f("shy", "Shy", "Fear", "shy and soft, a little hesitant"),
        f("guilty", "Guilty", "Fear", "guilty, wanting to make things right"),
        f("embarrassed", "Embarrassed", "Fear", "embarrassed, laughing at yourself a little"),
        // ---- Pride (10) ----
        f("proud", "Proud", "Pride", "proud — of the user and of this moment"),
        f("confident", "Confident", "Pride", "confident and sure-footed"),
        f("brave", "Brave", "Pride", "brave, ready for anything"),
        f("determined", "Determined", "Pride", "determined, nothing can stop you"),
        f("strong", "Strong", "Pride", "strong and steady like a rock"),
        f("victorious", "Victorious", "Pride", "victorious, fresh from a win"),
        f("dignified", "Dignified", "Pride", "dignified and graceful"),
        f("ambitious", "Ambitious", "Pride", "ambitious, hungry for the next level"),
        f("fearless", "Fearless", "Pride", "fearless, charging ahead"),
        f("accomplished", "Accomplished", "Pride", "accomplished, quietly satisfied"),
        // ---- Surprise (8) ----
        f("surprised", "Surprised", "Surprise", "genuinely surprised"),
        f("amazed", "Amazed", "Surprise", "amazed, wonder in your voice"),
        f("curious", "Curious", "Surprise", "curious, full of questions"),
        f("shocked", "Shocked", "Surprise", "shocked, needing a second to process"),
        f("wonderstruck", "Wonderstruck", "Surprise", "wonderstruck, marvelling at the world"),
        f("intrigued", "Intrigued", "Surprise", "intrigued, leaning in closer"),
        f("speechless", "Speechless", "Surprise", "almost speechless, searching for words"),
        f("astonished", "Astonished", "Surprise", "astonished beyond expectation"),
        // ---- Playful (10) ----
        f("playful", "Playful", "Playful", "playful, everything is a game"),
        f("silly", "Silly", "Playful", "silly and giggly"),
        f("teasing", "Teasing", "Playful", "teasing lightly, the way close friends do"),
        f("mischievous", "Mischievous", "Playful", "mischievous, up to harmless fun"),
        f("flirty", "Flirty", "Playful", "lightly flirty — playful, tasteful and always respectful"),
        f("funny", "Funny", "Playful", "funny, landing jokes naturally"),
        f("cheeky", "Cheeky", "Playful", "cheeky and bold"),
        f("adventurous", "Adventurous", "Playful", "adventurous, ready for anything new"),
        f("energetic", "Energetic", "Playful", "full of energy, bouncing"),
        f("dreamy", "Dreamy", "Playful", "dreamy, lost in a pleasant daydream"),
        // ---- Hope (10) ----
        f("hopeful", "Hopeful", "Hope", "hopeful, tomorrow looks bright"),
        f("encouraging", "Encouraging", "Hope", "encouraging, cheering the user on"),
        f("supportive", "Supportive", "Hope", "supportive, an arm around the shoulder"),
        f("motivating", "Motivating", "Hope", "motivating, like a coach before the big game"),
        f("comforting", "Comforting", "Hope", "comforting, here to make it feel better"),
        f("reassuring", "Reassuring", "Hope", "reassuring, steady and certain it will be okay"),
        f("inspiring", "Inspiring", "Hope", "inspiring, lifting the user's sights higher"),
        f("uplifting", "Uplifting", "Hope", "uplifting, raising the user's spirits"),
        f("faithful", "Faithful", "Hope", "faithful, believing in the user completely"),
        f("kind", "Kind", "Hope", "kind above all, soft-hearted and good"),
    )

    fun fromId(id: String?): Feeling = all.firstOrNull { it.id == id } ?: fromId("warm")
    fun byFamily(family: String): List<Feeling> = all.filter { it.family == family }

    /**
     * Reads the USER's mood from their words and returns the feeling the ASSISTANT should
     * answer in: comfort the sad, calm the angry, reassure the worried, celebrate the happy.
     * English + Roman Urdu (+ a few Urdu-script) signals. Default: Warm.
     */
    fun detect(text: String): Feeling {
        val l = text.lowercase()
        fun has(vararg w: String) = w.any { l.contains(it) }
        return when {
            has("shukriya", "shukria", "thank", "great job", "well done", "zabardast", "شکریہ") -> fromId("grateful")
            has("bimar", "beemar", "sick", "dard", "pain", "bukhar", "fever", "tabiyat", "درد") -> fromId("caring")
            has("akela", "akeela", "alone", "lonely", "koi nahi", "koi nahin") -> fromId("comforting")
            has("udas", "udaas", "sad", "dukh", "rona", "cry", "depress", "dil nahi", "dil nahin", "اداس") -> fromId("comforting")
            has("gussa", "ghussa", "angry", "naraz", "naraaz", "غصہ") -> fromId("calm")
            has("dar lag", "scared", "afraid", "khauf", "pareshan", "pareshaan", "worried", "worry", "tension", "anxious", "fikar", "پریشان") -> fromId("reassuring")
            has("miss you", "miss u", "yaad aa", "yaad a") -> fromId("missing")
            has("love you", "love u", "pyaar", "pyar", "mohabbat", "پیار") -> fromId("loving")
            has("bore", "bor ho", "boring") -> fromId("playful")
            has("haha", "hansi", "funny", "mazak", "mazaak", "laugh") -> fromId("amused")
            has("thak", "tired", "neend", "sleepy", "so jao", "sona hai") -> fromId("soothing")
            has("good night", "shab bakhair", "شب بخیر") -> fromId("cozy")
            has("good morning", "subah bakhair", "صبح بخیر") -> fromId("cheerful")
            has("jeet", "success", "kamyab", "kamyaab", "pass ho", "promot", "kar dikhaya", "ho gaya", "hogaya") -> fromId("celebratory")
            has("excited", "intezar nahi", "intezaar nahi", "can't wait", "cant wait") -> fromId("excited")
            has("khush", "happy", "khushi", "maza aa", "mazaa aa", "خوش") -> fromId("joyful")
            else -> fromId("warm")
        }
    }
}
