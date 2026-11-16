package dev.pages.badshah222ai.core

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import dev.pages.badshah222ai.MainActivity
import kotlinx.coroutines.flow.MutableStateFlow

/** One-shot signal: the wake word was heard, open the voice screen. */
object WakeSignal {
    val ticks = MutableStateFlow(0L)
    fun ping() { ticks.value = System.currentTimeMillis() }
}

/**
 * Best-effort wake word. While ON, a foreground service keeps the phone's speech
 * recognizer listening for "BADSHAH"; on a match it opens the app straight into the
 * voice screen, like answering a call. Honest limits: OEM battery savers can stop
 * background listening, and recognition needs the phone's speech service (usually
 * Google's) to be available. After a wake, listening pauses ~25s so the voice
 * screen owns the microphone.
 */
class WakeWordService : Service(), RecognitionListener {
    private var recognizer: SpeechRecognizer? = null
    private val handler = Handler(Looper.getMainLooper())
    private var running = false
    private var errorStreak = 0
    private var pausedUntil = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        getSystemService(NotificationManager::class.java)
            .createNotificationChannel(NotificationChannel(CHANNEL, "Wake word", NotificationManager.IMPORTANCE_LOW))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForegroundCompat(buildNotification())
        if (!running) {
            running = true
            if (SpeechRecognizer.isRecognitionAvailable(this)) startListening() else stopSelf()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        running = false
        handler.removeCallbacksAndMessages(null)
        runCatching { recognizer?.destroy() }
        recognizer = null
        super.onDestroy()
    }

    private fun buildNotification(): Notification {
        val tap = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("BADSHAH is listening")
            .setContentText("Say BADSHAH to wake your assistant")
            .setContentIntent(tap)
            .setOngoing(true)
            .build()
    }

    private fun startForegroundCompat(n: Notification) {
        try {
            if (Build.VERSION.SDK_INT >= 34) startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
            else startForeground(NOTIF_ID, n)
        } catch (e: Exception) {
            runCatching { startForeground(NOTIF_ID, n) }
        }
    }

    private fun startListening() {
        if (!running) return
        val wait = pausedUntil - System.currentTimeMillis()
        if (wait > 0) {
            handler.postDelayed({ startListening() }, wait + 200)
            return
        }
        runCatching { recognizer?.destroy() }
        recognizer = SpeechRecognizer.createSpeechRecognizer(this).also {
            it.setRecognitionListener(this)
            runCatching { it.startListening(recognizerIntent()) }
        }
    }

    private fun recognizerIntent() = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
        putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, packageName)
    }

    /** Returns true when the wake word was heard (and handled). */
    private fun heard(texts: List<String>?): Boolean {
        val custom = runCatching { SecureStore(this).customWakeWord.lowercase().trim() }.getOrDefault("")
        val hit = texts.orEmpty().any { t ->
            val n = t.lowercase()
            n.contains("badshah") || n.contains("bad shah") || n.contains("baadshah") || n.contains("badsha") ||
                (custom.isNotEmpty() && n.contains(custom))
        }
        if (hit) onWake()
        return hit
    }

    private fun onWake() {
        errorStreak = 0
        pausedUntil = System.currentTimeMillis() + 25_000
        runCatching { recognizer?.cancel() }
        packageManager.getLaunchIntentForPackage(packageName)?.apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra(EXTRA_WAKE, "1")
        }?.let { runCatching { startActivity(it) } }
        WakeSignal.ping()
        handler.postDelayed({ startListening() }, 25_200)
    }

    private fun restart(delayMs: Long) {
        handler.postDelayed({ startListening() }, delayMs)
    }

    // ---- RecognitionListener ----
    override fun onReadyForSpeech(params: Bundle?) {}
    override fun onBeginningOfSpeech() {}
    override fun onRmsChanged(rmsdB: Float) {}
    override fun onBufferReceived(buffer: ByteArray?) {}
    override fun onEndOfSpeech() {}
    override fun onPartialResults(partialResults: Bundle?) {
        heard(partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION))
    }

    override fun onResults(results: Bundle?) {
        errorStreak = 0
        val woke = heard(results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION))
        if (!woke) restart(350)
    }

    override fun onError(error: Int) {
        if (error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) {
            stopSelf()
            return
        }
        errorStreak++
        restart(if (errorStreak > 4) 2_500 else 800)
    }

    override fun onEvent(eventType: Int, params: Bundle?) {}

    companion object {
        const val EXTRA_WAKE = "dev.pages.badshah222ai.WAKE"
        private const val CHANNEL = "wake_word"
        private const val NOTIF_ID = 42

        fun start(ctx: Context) {
            ContextCompat.startForegroundService(ctx, Intent(ctx, WakeWordService::class.java))
        }

        fun stop(ctx: Context) {
            ctx.stopService(Intent(ctx, WakeWordService::class.java))
        }
    }
}
