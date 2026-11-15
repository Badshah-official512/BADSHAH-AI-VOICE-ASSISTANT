package dev.pages.badshah222ai.data.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Voice recognition via Android's SpeechRecognizer. Must be used from the main thread. */
class SpeechInput(private val context: Context) {
    private var rec: SpeechRecognizer? = null
    private val _listening = MutableStateFlow(false); val listening: StateFlow<Boolean> = _listening
    private val _level = MutableStateFlow(0f); val level: StateFlow<Float> = _level   // 0..1 microphone loudness

    fun isAvailable() = SpeechRecognizer.isRecognitionAvailable(context)

    fun start(languageTag: String, onPartial: (String) -> Unit, onResult: (String) -> Unit, onError: (String) -> Unit) {
        stop()
        if (!isAvailable()) { onError("This phone has no speech-recognition service. Install or enable Google (Speech Services) and try again."); return }
        val r = SpeechRecognizer.createSpeechRecognizer(context); rec = r
        r.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(p: Bundle?) { _listening.value = true }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rms: Float) { _level.value = ((rms + 2f) / 12f).coerceIn(0f, 1f) }
            override fun onBufferReceived(b: ByteArray?) {}
            override fun onEndOfSpeech() { _listening.value = false; _level.value = 0f }
            override fun onError(error: Int) {
                _listening.value = false; _level.value = 0f
                onError(when (error) {
                    SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "I did not hear anything. Tap the mic and try again."
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission is needed. Allow it in Permissions."
                    SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Speech recognition needs internet for this language."
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Speech recognition is busy. Try again in a moment."
                    else -> "Speech recognition failed (code $error)."
                })
            }
            override fun onResults(res: Bundle?) { _listening.value = false; _level.value = 0f
                val t = res?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                if (t.isBlank()) onError("I did not catch that. Try again.") else onResult(t) }
            override fun onPartialResults(p: Bundle?) { p?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.let(onPartial) }
            override fun onEvent(t: Int, p: Bundle?) {}
        })
        r.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        })
    }

    fun stop() { rec?.runCatching { stopListening(); destroy() }; rec = null; _listening.value = false; _level.value = 0f }
}
