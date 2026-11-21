package dev.pages.badshah222ai.core

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
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
    private var glow: EdgeGlowView? = null
    private val visHandler = Handler(Looper.getMainLooper())
    private val visLoop = object : Runnable {
        override fun run() {
            // v7.2: app khud saamne ho to bubble chhupao — sirf doosri apps ke upar dikhao
            val vis = if (MainActivity.appInForeground) View.GONE else View.VISIBLE
            orb?.visibility = vis
            glow?.visibility = vis
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
            setStroke((3 * resources.displayMetrics.density).toInt(), 0xFF8B3DFF.toInt())
        }
        val iv = ImageView(this).apply {
            background = bg
            val bmp = BitmapFactory.decodeResource(resources, R.drawable.orb_character)
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
        ).apply { gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL; x = 0; y = (54 * resources.displayMetrics.density).toInt() }

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
        // v7.3: RGB edge glow — call ke doran poori screen ke kinaron par blue/red/lime roshni,
        // app background mein ho tab bhi rehti hai (service sirf call ke doran chalti hai)
        val gv = EdgeGlowView(this)
        val gParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT, type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        )
        glow = gv
        runCatching { wm?.addView(gv, gParams) }
        running = true
        visHandler.post(visLoop)
    }

    override fun onDestroy() {
        visHandler.removeCallbacks(visLoop)
        glow?.stopAnim()
        glow?.let { runCatching { wm?.removeView(it) } }
        orb?.let { runCatching { wm?.removeView(it) } }
        glow = null
        orb = null; running = false
        super.onDestroy()
    }
}

/** Poori screen ke kinaron par behti hui RGB roshni (blue → red → lime → purple). */
class EdgeGlowView(ctx: Context) : View(ctx) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3.2f * ctx.resources.displayMetrics.density
    }
    private val rect = RectF()
    private val colors = intArrayOf(
        Color.rgb(59, 130, 246), Color.rgb(239, 68, 68),
        Color.rgb(163, 230, 53), Color.rgb(139, 61, 255),
    )
    private var startAt = System.currentTimeMillis()
    private val tick = object : Runnable {
        override fun run() { invalidate(); if (animOn) handler.postDelayed(this, 50) }
    }
    private var animOn = false
    init { alpha = 0.9f }
    override fun onAttachedToWindow() { super.onAttachedToWindow(); animOn = true; handler.post(tick) }
    override fun onDetachedFromWindow() { stopAnim(); super.onDetachedFromWindow() }
    fun stopAnim() { animOn = false; handler.removeCallbacks(tick) }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val t = ((System.currentTimeMillis() - startAt) % 4800) / 4800f
        val seg = (t * colors.size).toInt() % colors.size
        val frac = (t * colors.size) - (t * colors.size).toInt()
        val c1 = colors[seg]; val c2 = colors[(seg + 1) % colors.size]
        paint.color = Color.rgb(
            (Color.red(c1) + (Color.red(c2) - Color.red(c1)) * frac).toInt(),
            (Color.green(c1) + (Color.green(c2) - Color.green(c1)) * frac).toInt(),
            (Color.blue(c1) + (Color.blue(c2) - Color.blue(c1)) * frac).toInt(),
        )
        val inset = paint.strokeWidth / 2f + 1f
        rect.set(inset, inset, width - inset, height - inset)
        val r = 26f * resources.displayMetrics.density
        canvas.drawRoundRect(rect, r, r, paint)
    }
}
