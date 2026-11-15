package dev.pages.badshah222ai

import dev.pages.badshah222ai.domain.*

/** Framework-free assertions. JUnit wrappers in DomainLogicTest call these; so does the standalone runner. */
object DomainLogic {
    private var n = 0
    val failures = mutableListOf<String>()
    private fun eq(name: String, actual: Any?, expected: Any?) {
        n++; if (actual != expected) failures += "FAIL $name: expected <$expected> got <$actual>" else println("PASS  $name")
    }
    fun count() = n

    fun runAll() {
        val p = CommandParser::parse
        eq("open youtube", p("Open YouTube"), PhoneAction.OpenYouTube)
        eq("roman urdu youtube", p("youtube kholo"), PhoneAction.OpenYouTube)
        eq("open whatsapp", p("open whatsapp"), PhoneAction.OpenWhatsApp)
        eq("open chrome", p("launch Chrome"), PhoneAction.OpenChrome)
        eq("open camera", p("open the camera"), PhoneAction.OpenCamera)
        eq("open gallery", p("open photos"), PhoneAction.OpenGallery)
        eq("open arbitrary app", p("open Spotify app"), PhoneAction.OpenApp("spotify"))
        eq("pick contact", p("select a contact"), PhoneAction.PickContact)
        eq("call by name", p("call ali khan"), PhoneAction.Call("Ali Khan"))
        eq("call by number", p("call +92 325 1787969"), PhoneAction.Call("+923251787969"))
        eq("roman urdu call", p("ahmed ko call karo"), PhoneAction.Call("Ahmed"))
        eq("whatsapp msg", p("send whatsapp to Sara saying I'll be late"), PhoneAction.WhatsAppMessage("Sara", "I'll be late"))
        eq("whatsapp msg keeps case", p("WhatsApp Bilal: Meeting at 5 PM"), PhoneAction.WhatsAppMessage("Bilal", "Meeting at 5 PM"))
        eq("sms msg", p("send a text to mom saying call me back"), PhoneAction.Sms("Mom", "call me back"))
        eq("roman urdu sms", p("usman ko sms karo ke main aa raha hun"), PhoneAction.Sms("Usman", "main aa raha hun"))
        eq("alarm 7:30 am", p("set an alarm for 7:30 am"), PhoneAction.SetAlarm(7, 30))
        eq("alarm 6 pm", p("set alarm at 6 pm"), PhoneAction.SetAlarm(18, 0))
        eq("alarm 12 am", p("alarm 12 am"), PhoneAction.SetAlarm(0, 0))
        eq("alarm 24h", p("set alarm 19:45"), PhoneAction.SetAlarm(19, 45))
        eq("alarm invalid hour rejected", p("set alarm 27:00"), null)
        eq("timer minutes", p("set a timer for 5 minutes"), PhoneAction.StartTimer(300))
        eq("timer hours", p("start timer 2 hours"), PhoneAction.StartTimer(7200))
        eq("timer seconds", p("timer 30 seconds"), PhoneAction.StartTimer(30))
        eq("roman urdu timer", p("10 minute ka timer"), PhoneAction.StartTimer(600))
        eq("timer over 24h rejected", p("timer 99 hours"), null)
        eq("web search", p("search for best pizza near me"), PhoneAction.WebSearch("best pizza near me"))
        eq("plain question goes to AI", p("what is the capital of France"), null)
        eq("chit-chat goes to AI", p("how are you today"), null)
        eq("the word 'call' mid-sentence is not a call", p("what does the word call mean"), null)
        eq("empty", p("   "), null)
        eq("confirm: call", PhoneAction.Call("x").needsConfirmation, true)
        eq("confirm: sms", PhoneAction.Sms("x", "y").needsConfirmation, true)
        eq("confirm: whatsapp", PhoneAction.WhatsAppMessage("x", "y").needsConfirmation, true)
        eq("no confirm: open youtube", PhoneAction.OpenYouTube.needsConfirmation, false)
        eq("no confirm: alarm", PhoneAction.SetAlarm(7, 0).needsConfirmation, false)

        val voices = listOf(
            VoiceInfo("en-us-x-iom-local", "en-US", false, 400), VoiceInfo("en-US-language", "en-US", false, 300),
            VoiceInfo("en-gb-x-rjs-network", "en-GB", true, 400), VoiceInfo("hi-in-x-hid-local", "hi-IN", false, 400),
            VoiceInfo("ur-pk-x-cfn-local", "ur-PK", false, 300), VoiceInfo("some-male-voice", "en-IN", false, 300),
            VoiceInfo("some-female-voice", "en-IN", false, 300), VoiceInfo("tts_male#1", "en-AU", false, 300))
        eq("engine id with 'male' => likely male", VoiceCatalog.guessGender(voices[5]), GenderGuess.LIKELY_MALE)
        eq("'female' is NOT mistaken for male", VoiceCatalog.guessGender(voices[6]), GenderGuess.LIKELY_FEMALE)
        eq("'tts_male#1' => likely male", VoiceCatalog.guessGender(voices[7]), GenderGuess.LIKELY_MALE)
        eq("opaque google code => UNKNOWN (never guessed)", VoiceCatalog.guessGender(voices[0]), GenderGuess.UNKNOWN)
        eq("male candidates are exactly the labelled ones", VoiceCatalog.maleCandidates(voices).map { it.name }, listOf("some-male-voice", "tts_male#1"))
        eq("sorted puts likely-male first", VoiceCatalog.sorted(voices).first().name.contains("male"), true)
        eq("sorted keeps likely-female last", VoiceCatalog.sorted(voices).last().name, "some-female-voice")
        val sup = VoiceCatalog.languageSupport(voices).associateBy { it.code }
        eq("English detected", sup["en"]!!.installed, true)
        eq("Urdu detected", sup["ur"]!!.installed, true)
        eq("Hindi detected", sup["hi"]!!.installed, true)
        eq("Pashto reported as NOT installed (not faked)", sup["ps"]!!.installed, false)
        eq("distinct count excludes unknowns when asked", VoiceCatalog.distinctChoices(voices, false), 2)
        eq("distinct count with unknowns", VoiceCatalog.distinctChoices(voices, true), 7)
        eq("6 style presets, first is untouched natural", VoiceCatalog.styles.first().let { it.id to it.pitch }, "natural" to 1.0f)
        eq("deep is lower pitched than natural", VoiceCatalog.styles.first { it.id == "deep" }.pitch < 1.0f, true)
        eq("all pitches within TTS-safe range", VoiceCatalog.styles.all { it.pitch in 0.5f..2.0f && it.speed in 0.5f..2.0f }, true)
        eq("provider lookup falls back to Gemini", Provider.fromId("nonsense"), Provider.GEMINI)
        eq("provider lookup groq", Provider.fromId("groq"), Provider.GROQ)
    }
}
