package dev.pages.badshah222ai.core

import dev.pages.badshah222ai.data.live.LiveCallManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * App-wide call holder. The LiveCallManager lives here (not in a screen) so
 * the call survives navigation — minimize (2e) just navigates to Chat while
 * this keeps running. Phase 3 moves the audio/session ownership into the
 * foreground CallService; this holder stays the single source of truth for UI.
 */
object CallSession {
    val liveCall = LiveCallManager()

    private val _showFloatingCircle = MutableStateFlow(false)
    /** True while a call is minimized (or a call is live and the user left the Call screen). */
    val showFloatingCircle: StateFlow<Boolean> = _showFloatingCircle.asStateFlow()

    fun onCallMinimized() {
        _showFloatingCircle.value = true
    }

    fun onCallRestored() {
        _showFloatingCircle.value = false
    }

    fun onCallEnded() {
        _showFloatingCircle.value = false
    }

    private val _openCallRequest = MutableStateFlow(false)
    /** Set by the overlay circle (over other apps): bring the Call screen forward. */
    val openCallRequest: StateFlow<Boolean> = _openCallRequest.asStateFlow()

    fun requestOpenCall() {
        _openCallRequest.value = true
    }

    fun clearOpenCallRequest() {
        _openCallRequest.value = false
    }
}
