package dev.pages.badshah222ai.core

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import dev.pages.badshah222ai.MainActivity
import dev.pages.badshah222ai.R

/**
 * The floating orb (MYRA's signature): a small glowing BADSHAH bubble that floats
 * over other apps once the user grants the overlay permission. Tap = open the app
 * straight into the voice call; drag = move it; long-press = remove it. It never
 * records or listens by itself — it is only a shortcut the user can dismiss.
 */
class FloatingOrbService : Service() {
    companion object {
        @Volatile var running = false
        fun start(ctx: Context) { ctx.startService(Intent(ctx, FloatingOrbService::class.java)) }
        fun stop(ctx: Context) { ctx.stopService(Intent(ctx, FloatingOrbService::class.java)) }
    }

    private var wm: WindowManager? = null
    private var orb: ImageView? = null
    private val visHandler = Handler(Looper.getMainLooper())
    private val visLoop = object : Runnable {
        override fun run() {
            // v7.2: app khud saamne ho to bubble chhupao — sirf doosri apps ke upar dikhao
            orb?.visibility = if (MainActivity.appInForeground) View.GONE else View.VISIBLE
            visHandler.postDelayed(this, 600)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        wm = getSystemService(WindowManager::class.java)
        val sizePx = (66 * resources.displayMetrics.density).toInt()
        val bg = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(0xFF171130.toInt())
            setStroke((3 * resources.displayMetrics.density).toInt(), 0xFF22D3EE.toInt())
        }
        val iv = ImageView(this).apply {
            background = bg
            val bmp = BitmapFactory.decodeResource(resources, R.drawable.ic_launcher_fg)
            setImageBitmap(bmp)
            scaleType = ImageView.ScaleType.CENTER_CROP
            clipToOutline = true
            contentDescription = "BADSHAH AI"
        }
        val type = if (Build.VERSION.SDK_INT >= 26) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE
        val params = WindowManager.LayoutParams(
            sizePx, sizePx, type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply { gravity = Gravity.TOP or Gravity.START; x = 24; y = 240 }

        var downX = 0; var downY = 0; var startX = 0; var startY = 0; var downAt = 0L
        iv.setOnTouchListener { v, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> { downX = e.rawX.toInt(); downY = e.rawY.toInt(); startX = params.x; startY = params.y; downAt = System.currentTimeMillis(); true }
                MotionEvent.ACTION_MOVE -> {
                    params.x = startX + (e.rawX.toInt() - downX); params.y = startY + (e.rawY.toInt() - downY)
                    runCatching { wm?.updateViewLayout(v, params) }; true
                }
                MotionEvent.ACTION_UP -> {
                    val moved = Math.abs(e.rawX.toInt() - downX) + Math.abs(e.rawY.toInt() - downY)
                    if (moved < 12) {
                        if (System.currentTimeMillis() - downAt > 600) {
                            stopSelf() // long-press removes the orb
                        } else {
                            val i = Intent(this, MainActivity::class.java)
                                .putExtra("EXTRA_WAKE", "1")
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                            startActivity(i)
                        }
                    }
                    true
                }
                else -> false
            }
        }
        orb = iv
        runCatching { wm?.addView(iv, params) }
        running = true
        visHandler.post(visLoop)
    }

    override fun onDestroy() {
        visHandler.removeCallbacks(visLoop)
        orb?.let { runCatching { wm?.removeView(it) } }
        orb = null; running = false
        super.onDestroy()
    }
}
