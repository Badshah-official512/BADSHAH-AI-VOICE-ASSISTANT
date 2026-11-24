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
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import dev.pages.badshah222ai.MainActivity

/**
 * Call keep-alive, the way real VoIP apps do it: while a voice call is running,
 * this microphone-type foreground service keeps the app process (and therefore
 * the WebView, its mic capture and the Gemini Live socket) alive when the user
 * minimizes the app or switches to another app. A visible notification shows
 * the whole time; ending the call stops the service. Started only from a user
 * tap inside the app (micTakeover), never in the background on its own.
 */
class CallService : Service() {
    companion object {
        private const val TAG = "BadshahCall"
        // v7.8.3 A4: safety ceiling for the call wake lock — a lock that is
        // somehow never released still dies on its own after 4 hours.
        private const val WAKE_LOCK_TIMEOUT_MS = 4L * 60L * 60L * 1000L

        fun start(ctx: Context) {
            val i = Intent(ctx, CallService::class.java)
            if (Build.VERSION.SDK_INT >= 26) ctx.startForegroundService(i) else ctx.startService(i)
        }

        fun stop(ctx: Context) { ctx.stopService(Intent(ctx, CallService::class.java)) }
    }

    // v7.8.3 A4: PARTIAL_WAKE_LOCK for the duration of the call session.
    // The FGS alone keeps the PROCESS alive, but without a wake lock the CPU
    // can doze when the screen times out mid-call, stalling the WebView's
    // JavaScript timers and the Gemini Live socket while the call still
    // looks alive. Held only while this service runs; released in onDestroy.
    private var callWakeLock: PowerManager.WakeLock? = null

    private fun acquireCallWakeLock() {
        try {
            val held = callWakeLock
            if (held != null && held.isHeld) return
            val pm = getSystemService(PowerManager::class.java) ?: return
            @Suppress("DEPRECATION")
            val lock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "BadshahAI:VoiceCall")
            lock.setReferenceCounted(false)
            lock.acquire(WAKE_LOCK_TIMEOUT_MS)
            callWakeLock = lock
            Log.d(TAG, "CallService: wake lock acquired")
        } catch (e: Exception) {
            // A wake-lock failure must never crash or block the call itself.
            Log.d(TAG, "CallService: wake lock acquire failed: ${e.message}")
        }
    }

    private fun releaseCallWakeLock() {
        try {
            val lock = callWakeLock ?: return
            if (lock.isHeld) lock.release()
            Log.d(TAG, "CallService: wake lock released")
        } catch (e: Exception) {
            Log.d(TAG, "CallService: wake lock release failed: ${e.message}")
        } finally {
            callWakeLock = null
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(NotificationChannel("bh_call", "Voice call", NotificationManager.IMPORTANCE_LOW))
        }
        val openIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notif: Notification = NotificationCompat.Builder(this, "bh_call")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("BADSHAH AI — call chal rahi hai")
            .setContentText("App minimize karne par bhi baat jaari rahegi — wapas aane ke liye tap karo")
            .setContentIntent(openIntent)
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(44, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } else {
            startForeground(44, notif)
        }
        // v7.8.3 A4: call session is live — hold the CPU awake for it.
        // Idempotent, so START_STICKY restarts re-entering here are safe.
        acquireCallWakeLock()
        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // v7.8.3 A4: the app task is gone, so the call session this service
        // was keeping alive is over — drop the wake lock and stop cleanly
        // instead of relying on the 4-hour safety timeout.
        releaseCallWakeLock()
        stopSelf()
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        // v7.8.3 A4: every stop path (micRelease -> stopService, onTaskRemoved,
        // system kill of the service) funnels through here.
        releaseCallWakeLock()
        super.onDestroy()
    }
}
