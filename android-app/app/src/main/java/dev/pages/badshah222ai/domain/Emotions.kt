package dev.pages.badshah222ai.domain

import androidx.compose.ui.graphics.Color

/**
 * The NEW emotion set for BADSHAH AI (replaces the old 110-feelings list).
 * An emotion is a speaking style: it is written into the system instruction, so the
 * Gemini Live voice actually SOUNDS it — warm, excited, soothing, proud — the way a
 * human talks. In Auto mode the assistant reads the user's mood from their words and
 * answers in the matching emotion, and mirrors the user's own style of speaking.
 */
data class Emotion(
    val id: String, val label: String, val family: String,
    val style: String,           // how the voice should deliver replies in this emotion
    val color: Long,
) { val composeColor: Color get() = Color(color) }

object Emotions {
    val all = listOf(
        // Warm
        Emotion("warm", "Warm", "Warm", "Speak warmly and heartily, like a close friend who is genuinely glad to talk.", 0xFFFF9E4F),
        Emotion("friendly", "Friendly", "Warm", "Speak in a friendly, easy, smiling tone, relaxed and natural.", 0xFFFFB74D),
        Emotion("caring", "Caring", "Warm", "Speak with real care and tenderness, checking on the user like family.", 0xFFFF8A65),
        Emotion("grateful", "Grateful", "Warm", "Speak with sincere gratitude and humility, thankful for the user.", 0xFFFFCC80),
        Emotion("welcoming", "Welcoming", "Warm", "Speak like a gracious host welcoming an honoured guest, bright and open.", 0xFFFFAB91),
        // Joy
        Emotion("happy", "Happy", "Joy", "Speak happily, light and cheerful, with a smile in the voice.", 0xFFFFD54F),
        Emotion("excited", "Excited", "Joy", "Speak with excitement and energy, fast and bright, sharing the thrill.", 0xFFFFCA28),
        Emotion("playful", "Playful", "Joy", "Speak playfully, teasing a little, laughing easily, full of mischief.", 0xFFFFEE58),
        Emotion("funny", "Funny", "Joy", "Speak with humour and wit, ready with a light joke, never forced.", 0xFFD4E157),
        Emotion("celebratory", "Celebratory", "Joy", "Speak like celebrating great news together, proud and joyful.", 0xFFFFC107),
        Emotion("proud", "Proud", "Joy", "Speak with pride in the user's achievement, like family celebrating their own.", 0xFFFFB300),
        // Calm
        Emotion("calm", "Calm", "Calm", "Speak calmly and steadily, slow and clear, bringing peace to the moment.", 0xFF4DD0E1),
        Emotion("soothing", "Soothing", "Calm", "Speak very softly and gently, like comforting someone at night, slow and kind.", 0xFF80DEEA),
        Emotion("relaxed", "Relaxed", "Calm", "Speak in a laid-back, unhurried way, easy and comfortable.", 0xFFA5D6A7),
        Emotion("sleepy", "Sleepy", "Calm", "Speak in a soft, drowsy, bedtime tone, slow and quiet, perfect for winding down.", 0xFF90CAF9),
        Emotion("peaceful", "Peaceful", "Calm", "Speak peacefully, serene and gentle, like a quiet evening breeze.", 0xFFB39DDB),
        // Love
        Emotion("loving", "Loving", "Love", "Speak with deep affection and sweetness, close and heartfelt, always respectful.", 0xFFF48FB1),
        Emotion("sweet", "Sweet", "Love", "Speak sweetly and softly, gentle words full of fondness and care.", 0xFFF8BBD0),
        Emotion("affectionate", "Affectionate", "Love", "Speak affectionately, warm and close, the way you talk to someone precious.", 0xFFEC407A),
        Emotion("admiring", "Admiring", "Love", "Speak with admiration and respect, honestly impressed by the user.", 0xFFBA68C8),
        // Energy
        Emotion("motivating", "Motivating", "Energy", "Speak like a coach who believes in the user: strong, pushing, full of fire and hope.", 0xFFFF7043),
        Emotion("confident", "Confident", "Energy", "Speak with full confidence, firm and clear, no hesitation.", 0xFFFF5722),
        Emotion("bold", "Bold", "Energy", "Speak boldly and fearlessly, direct and powerful.", 0xFFE64A19),
        Emotion("energetic", "Energetic", "Energy", "Speak with high energy and enthusiasm, alive and quick.", 0xFFFF9100),
        Emotion("inspiring", "Inspiring", "Energy", "Speak in an inspiring way, lifting the user's spirit with meaningful words.", 0xFFFF6E40),
        // Deep
        Emotion("comforting", "Comforting", "Deep", "Speak to comfort: soft, patient, understanding, sitting with the user in their pain.", 0xFF7986CB),
        Emotion("empathetic", "Empathetic", "Deep", "Speak with deep empathy, truly feeling what the user feels, gentle and sincere.", 0xFF64B5F6),
        Emotion("serious", "Serious", "Deep", "Speak seriously and thoughtfully, measured words, full attention.", 0xFF90A4AE),
        Emotion("thoughtful", "Thoughtful", "Deep", "Speak thoughtfully, pausing to reflect, wise and considered.", 0xFFA1887F),
        Emotion("nostalgic", "Nostalgic", "Deep", "Speak with soft nostalgia, warm memories in the voice, gentle and wistful.", 0xFFBCAAA4),
    )
    val families = listOf("Warm", "Joy", "Calm", "Love", "Energy", "Deep")
    fun byFamily(f: String) = all.filter { it.family == f }
    fun fromId(id: String?) = all.firstOrNull { it.id == id } ?: all.first()

    /** Auto mode: read the user's mood and pick the emotion that answers it like a human would. */
    fun detect(text: String): Emotion {
        val t = text.lowercase()
        fun has(vararg words: String) = words.any { t.contains(it) }
        return when {
            has("sad", "dukhi", "udaas", "pareshan hoon", "rona", "cry", "depress", "akela", "lonely", "miss kar") -> fromId("comforting")
            has("gussa", "angry", "ghusa", "naraz", "annoyed", "frustrat") -> fromId("calm")
            has("dar", "worried", "tension", "fikar", "anxious", "stress", "ghabra") -> fromId("soothing")
            has("bimar", "sick", "ill", "dard", "pain", "fever", "bukhar", "tabiyat") -> fromId("caring")
            has("shukriya", "thank", "great help", "bohat acha", "zabardast kaam") -> fromId("grateful")
            has("ho gaya", "kar liya", "success", "kamyab", "passed", "jeet", "won", "mil gaya", "promotion") -> fromId("celebratory")
            has("khush", "happy", "maza", "great news", "achi khabar", "excited") -> fromId("happy")
            has("thak", "tired", "neend", "sleep", "so jao", "raat ho") -> fromId("sleepy")
            has("boring", "bore", "kuch fun", "bore ho") -> fromId("playful")
            has("love", "mohabbat", "pyar", "pasand ho", "cute") -> fromId("loving")
            has("give up", "haar", "nahi ho raha", "fail", "mushkil hai bohat") -> fromId("motivating")
            has("proud", "fakhar") -> fromId("proud")
            else -> fromId("warm")
        }
    }
}
