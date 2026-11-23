package dev.pages.badshah222ai.data.live

import android.content.Context
import dev.pages.badshah222ai.core.CallService
import dev.pages.badshah222ai.core.SecureStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Live call state machine — WIRED (Phase 2) to the audited [LiveSession]:
 * native Gemini Live WebSocket (wss://generativelanguage.googleapis.com,
 * model gemini-3.8-live, MYRA-style setup, session resumption, Charon default).
 *
 * Key source: the user's OWN Gemini key (BYOK, Settings) — voice calls are
 * bring-your-own-key by the owner's rule. Voice rules: listen first, reply
 * only after Boss finishes; stop-and-listen on interruption (server-side
 * "interrupted" cuts playback in LiveSession); the system prompt carries the
 * slow/clear word-separation line; the call self-ends when Boss says an
 * end-call phrase ("bas ab call kaat do" etc.).
 *
 * MINIMIZED (user decision 2026-11-23 ~14:01): the call keeps running in the
 * foreground CallService while the user is on the Chat screen; the floating
 * circle stays on screen and tapping it returns to Call. The call ends ONLY
 * via the End button (or the AI ending it on Boss's phrase).
 */
enum class CallPhase { IDLE, CONNECTING, LIVE, MINIMIZED, ENDED }

data class CallState(
    val phase: CallPhase = CallPhase.IDLE,
    val statusText: String = "",
    val isMuted: Boolean = false,
    val frontCamera: Boolean = true,
    val cameraOn: Boolean = false,
    val elapsedSeconds: Long = 0L,
    val liveState: String = "",
)

class LiveCallManager {
    private val _state = MutableStateFlow(CallState())
    val state: StateFlow<CallState> = _state.asStateFlow()

    private var appContext: Context? = null
    private var store: SecureStore? = null
    private var session: LiveSession? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    @Volatile private var startedAtMs: Long = 0L

    /** Called once from the DI container with the application context. */
    fun attach(context: Context, store: SecureStore) {
        appContext = context.applicationContext
        this.store = store
    }

    fun startCall() {
        val ctx = appContext ?: return
        val st = store ?: return
        if (session?.isActive == true) return
        _state.value = CallState(phase = CallPhase.CONNECTING, statusText = "Connecting…")
        val s = LiveSession(ctx, st)
        session = s
        s.onUnavailable = { reason ->
            _state.value = _state.value.copy(
                phase = CallPhase.IDLE,
                statusText = reason,
                cameraOn = false,
            )
            CallService.stop(ctx)
        }
        s.onTurn = { user, _ ->
            if (isEndCallText(user)) {
                endCall()
            } else {
                // Bol kar phone control (feature #5): spoken commands execute on
                // the phone — "youtube kholo", "flashlight on karo", "ali ko whatsapp karo".
                val action = dev.pages.badshah222ai.domain.CommandParser.parse(user)
                if (action != null) {
                    val exec = dev.pages.badshah222ai.data.phone.PhoneExecutor(ctx)
                    scope.launch(Dispatchers.IO) { runCatching { exec.execute(action) } }
                }
            }
        }
        scope.launch {
            s.state.collect { ls ->
                val cur = _state.value
                if (cur.phase == CallPhase.ENDED) return@collect
                _state.value = when (ls) {
                    LiveSession.State.Connecting ->
                        cur.copy(phase = CallPhase.CONNECTING, statusText = "Connecting…", liveState = "connecting")
                    LiveSession.State.Listening ->
                        cur.copy(phase = livePhase(cur), statusText = "Listening…", liveState = "listening", cameraOn = true)
                    LiveSession.State.Thinking ->
                        cur.copy(phase = livePhase(cur), statusText = "Thinking…", liveState = "thinking", cameraOn = true)
                    LiveSession.State.Speaking ->
                        cur.copy(phase = livePhase(cur), statusText = "Speaking…", liveState = "speaking", cameraOn = true)
                    LiveSession.State.Error ->
                        cur.copy(phase = CallPhase.IDLE, statusText = s.error.value ?: "Call error", cameraOn = false)
                    LiveSession.State.Idle -> cur
                }
                if (ls == LiveSession.State.Listening && startedAtMs == 0L) {
                    startedAtMs = System.currentTimeMillis()
                }
            }
        }
        CallService.start(ctx)
        s.start(systemInstruction(st))
    }

    private fun livePhase(cur: CallState): CallPhase =
        if (cur.phase == CallPhase.MINIMIZED) CallPhase.MINIMIZED else CallPhase.LIVE

    fun onConnected() {
        _state.value = _state.value.copy(phase = CallPhase.LIVE, statusText = "Live", cameraOn = true)
    }

    fun onStatus(text: String) {
        _state.value = _state.value.copy(statusText = text)
    }

    /** Minimize: go to Chat, call keeps running. Tapping the floating circle returns. */
    fun minimize() {
        if (_state.value.phase == CallPhase.LIVE) {
            _state.value = _state.value.copy(phase = CallPhase.MINIMIZED)
        }
    }

    fun restoreFromMinimized() {
        if (_state.value.phase == CallPhase.MINIMIZED) {
            _state.value = _state.value.copy(phase = CallPhase.LIVE)
        }
    }

    fun toggleMute() {
        val m = !_state.value.isMuted
        _state.value = _state.value.copy(isMuted = m)
        session?.setMuted(m)
    }

    fun switchCamera() {
        _state.value = _state.value.copy(frontCamera = !_state.value.frontCamera)
        // Phase 3: CameraX preview binds here; the flag already drives the UI.
    }

    /** The ONLY way a call ends (besides the AI ending it itself on "call kaat do"). */
    fun endCall() {
        session?.stop()
        session = null
        startedAtMs = 0L
        appContext?.let { CallService.stop(it) }
        _state.value = CallState(phase = CallPhase.ENDED)
    }

    private fun systemInstruction(store: SecureStore): String {
        val lang = when (store.appLanguage) {
            "roman" -> "Reply in Roman Urdu (Latin script) when Boss speaks Roman Urdu, English when he speaks English. Never reply in Devanagari script."
            "urdu" -> "Reply in Urdu script when Boss speaks Urdu, English when he speaks English."
            else -> "Reply in the same language Boss speaks: Roman Urdu in Latin script, or English. Never reply in Devanagari script."
        }
        return """
            You are BADSHAH AI, a warm, friendly voice assistant — never boring, like a helpful friend.
            $lang
            Speak slowly and clearly — pronounce every single word separately and completely, never rush.
            Listen first and reply only after Boss has finished speaking. If Boss speaks while you are replying, stop and listen first, then reply.
            Keep answers short and natural for voice. If Boss says to end the call, say Allah Hafiz warmly — the app ends the call itself.
        """.trimIndent()
    }

    companion object {
        /** End-call phrases (v7.7.0 set): anchored to "call"/"phone" so normal
         *  sentences like "call karo" (place a call) never end the call. */
        private val END_PHRASES = listOf(
            "call kaat do", "call kat do", "call kaatdo", "call band karo",
            "call band kar do", "call band kardo", "call khatam karo",
            "call khatm karo", "call end karo", "end the call", "end call",
            "call rok do", "phone band karo",
        )

        fun isEndCallText(text: String): Boolean {
            val t = text.lowercase().trim()
            if (t.isEmpty()) return false
            return END_PHRASES.any { t.contains(it) }
        }
    }
}
