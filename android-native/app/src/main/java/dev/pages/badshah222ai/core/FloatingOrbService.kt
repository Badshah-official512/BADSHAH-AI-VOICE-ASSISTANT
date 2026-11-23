package dev.pages.badshah222ai.core

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.foundation.clickable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import dev.pages.badshah222ai.MainActivity
import dev.pages.badshah222ai.ui.components.FloatingCircle
import dev.pages.badshah222ai.ui.components.OrbState

/** Minimal owners so a ComposeView can live inside a Service window. */
private class ServiceComposeOwner : LifecycleOwner, SavedStateRegistryOwner, ViewModelStoreOwner {
    private val lifecycleReg = LifecycleRegistry(this)
    private val savedStateCtrl = SavedStateRegistryController.create(this)
    override val lifecycle: Lifecycle get() = lifecycleReg
    override val savedStateRegistry: SavedStateRegistry get() = savedStateCtrl.savedStateRegistry
    override val viewModelStore: ViewModelStore = ViewModelStore()

    fun onCreate() {
        savedStateCtrl.performRestore(null)
        lifecycleReg.currentState = Lifecycle.State.CREATED
        lifecycleReg.currentState = Lifecycle.State.STARTED
        lifecycleReg.currentState = Lifecycle.State.RESUMED
    }

    fun onDestroy() {
        lifecycleReg.currentState = Lifecycle.State.DESTROYED
        viewModelStore.clear()
    }
}

/**
 * Floating AI circle OVER other apps (feature #1 "Har jagah AI circle" +
 * the minimize flow, user decision 2026-11-23 ~14:01).
 * Shows ONLY while a call is minimized and only if the user granted
 * "Draw over other apps" in the permission tour. The circle is the approved
 * design (gold arch + orbit rings + poster CENTRE CUT — the poster is never
 * used whole). Tap = return to the call screen; the call itself lives in
 * CallService + LiveSession and is never ended by this service.
 */
class FloatingOrbService : Service() {
    private var wm: WindowManager? = null
    private var view: ComposeView? = null
    private var owner: ServiceComposeOwner? = null

    companion object {
        fun start(ctx: Context) {
            if (Build.VERSION.SDK_INT >= 23 && !Settings.canDrawOverlays(ctx)) return
            ctx.startService(Intent(ctx, FloatingOrbService::class.java))
        }

        fun stop(ctx: Context) {
            ctx.stopService(Intent(ctx, FloatingOrbService::class.java))
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val o = ServiceComposeOwner()
        owner = o
        o.onCreate()
        wm = getSystemService(WindowManager::class.java)
        val cv = ComposeView(this)
        cv.setViewTreeLifecycleOwner(o)
        cv.setViewTreeSavedStateRegistryOwner(o)
        cv.setViewTreeViewModelStoreOwner(o)
        cv.setContent {
            FloatingCircle(
                state = OrbState.SPEAKING,
                size = 84.dp,
                modifier = Modifier.clickable {
                    CallSession.requestOpenCall()
                    val i = Intent(this@FloatingOrbService, MainActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    startActivity(i)
                },
            )
        }
        view = cv
        val type = if (Build.VERSION.SDK_INT >= 26) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE
        }
        val density = resources.displayMetrics.density
        val px = (92 * density).toInt()
        val params = WindowManager.LayoutParams(
            px, px, type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.END or Gravity.BOTTOM
            x = (12 * density).toInt()
            y = (96 * density).toInt()
        }
        try {
            wm?.addView(cv, params)
        } catch (e: Exception) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        try {
            view?.let { wm?.removeView(it) }
        } catch (e: Exception) {
            // View already detached.
        }
        view = null
        owner?.onDestroy()
        owner = null
        super.onDestroy()
    }
}
