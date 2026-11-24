package dev.pages.badshah222ai.core

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import dev.pages.badshah222ai.MainActivity
import dev.pages.badshah222ai.R
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * The floating orb (MYRA's signature): a small glowing BADSHAH bubble that floats
 * over other apps once the user grants the overlay permission. Tap = open the app
 * straight into the voice call; drag = move it; long-press = remove it. It never
 * records or listens by itself — it is only a shortcut the user can dismiss.
 *
 * v7.5: agentic look — the orb gets a rotating neon arc-ring, a breathing halo
 * and a live dot; the edge glow becomes two energy comets travelling around the
 * screen border over a soft base glow.
 */
class FloatingOrbService : Service() {
    companion object {
        @Volatile var running = false
        fun start(ctx: Context) { ctx.startService(Intent(ctx, FloatingOrbService::class.java)) }
        fun stop(ctx: Context) { ctx.stopService(Intent(ctx, FloatingOrbService::class.java)) }
    }

    private var wm: WindowManager? = null
    private var orb: AgenticOrbView? = null
    private var bar: PillBarView? = null
    private var glow: EdgeGlowView? = null

    // v7.8: do forms — circular ORB (idle form, purana behaviour) aur horizontal
    // pill BAR (ref-2, minimized-call form). App foreground se background jaye
    // (call minimize) to form BAR ho jata hai; bar ke avatar hisse par tap
    // karne se wapas ORB form aa jata hai. Sirf ek form ek waqt mein dikhta hai.
    private enum class Form { ORB, BAR }
    @Volatile private var form = Form.ORB
    @Volatile private var wasForeground = true

    private fun applyVisibility() {
        // v7.2: app khud saamne ho to floating element chhupao — sirf doosri apps ke upar dikhao
        val fg = MainActivity.appInForeground
        if (wasForeground && !fg) form = Form.BAR // minimize = pill bar form
        wasForeground = fg
        orb?.visibility = if (!fg && form == Form.ORB) View.VISIBLE else View.GONE
        bar?.visibility = if (!fg && form == Form.BAR) View.VISIBLE else View.GONE
        glow?.visibility = if (!fg && form == Form.BAR) View.VISIBLE else View.GONE
    }

    private fun switchForm(next: Form) {
        form = next
        applyVisibility()
    }

    private fun openApp() {
        val i = Intent(this, MainActivity::class.java)
            .putExtra("EXTRA_WAKE", "1")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        startActivity(i)
    }

    private val visHandler = Handler(Looper.getMainLooper())
    private val visLoop = object : Runnable {
        override fun run() {
            applyVisibility()
            visHandler.postDelayed(this, 600)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        wm = getSystemService(WindowManager::class.java)
        val sizePx = (76 * resources.displayMetrics.density).toInt()
        val bmp = runCatching { BitmapFactory.decodeResource(resources, R.drawable.orb_character) }.getOrNull()
        val iv = AgenticOrbView(this, bmp).apply { contentDescription = "BADSHAH AI" }
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
                            openApp()
                        }
                    }
                    true
                }
                else -> false
            }
        }
        orb = iv
        runCatching { wm?.addView(iv, params) }

        // v7.8 (ref-2): horizontal pill bar — minimized-call form. Same drag /
        // tap-to-restore / long-press-remove behaviour as the orb; tap on the
        // left avatar zone collapses the bar back to the circular orb form.
        // NOTE: is service ko call ka state maloom nahi hota (CallService koi
        // static flag nahi deta aur naya IPC nahi banaya gaya), is liye bar ki
        // status line "Tap to talk" rehti hai.
        val density = resources.displayMetrics.density
        val barW = minOf((312 * density).toInt(), resources.displayMetrics.widthPixels - (24 * density).toInt())
        val barH = (68 * density).toInt()
        val bv = PillBarView(this, bmp).apply { contentDescription = "Ask BADSHAH AI" }
        val barParams = WindowManager.LayoutParams(
            barW, barH, type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply { gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL; x = 0; y = (54 * density).toInt() }
        var bDownX = 0; var bDownY = 0; var bStartX = 0; var bStartY = 0; var bDownAt = 0L
        bv.setOnTouchListener { v, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> { bDownX = e.rawX.toInt(); bDownY = e.rawY.toInt(); bStartX = barParams.x; bStartY = barParams.y; bDownAt = System.currentTimeMillis(); true }
                MotionEvent.ACTION_MOVE -> {
                    barParams.x = bStartX + (e.rawX.toInt() - bDownX); barParams.y = bStartY + (e.rawY.toInt() - bDownY)
                    runCatching { wm?.updateViewLayout(v, barParams) }; true
                }
                MotionEvent.ACTION_UP -> {
                    val moved = Math.abs(e.rawX.toInt() - bDownX) + Math.abs(e.rawY.toInt() - bDownY)
                    if (moved < 12) {
                        if (System.currentTimeMillis() - bDownAt > 600) {
                            stopSelf() // long-press removes the bar (orb wala hi rule)
                        } else if (e.x < 64 * density) {
                            switchForm(Form.ORB) // avatar zone tap = chhota orb form
                        } else {
                            openApp() // baqi har tap = app restore / call par wapas
                        }
                    }
                    true
                }
                else -> false
            }
        }
        bv.visibility = View.GONE
        bar = bv
        runCatching { wm?.addView(bv, barParams) }
        // v7.3: RGB edge glow — call ke doran poori screen ke kinaron par roshni,
        // app background mein ho tab bhi rehti hai (service sirf call ke doran chalti hai)
        // v7.8.1: hologram perimeter — ab sirf BAR form active honay par dikhta
        // hai (applyVisibility dekho); FLAG_NOT_TOUCHABLE hai, touch block nahi hota.
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
        orb?.stopAnim()
        bar?.stopAnim()
        glow?.let { runCatching { wm?.removeView(it) } }
        orb?.let { runCatching { wm?.removeView(it) } }
        bar?.let { runCatching { wm?.removeView(it) } }
        glow = null
        bar = null
        orb = null; running = false
        super.onDestroy()
    }
}

/**
 * v7.8 pill bar (ref-2): horizontal floating bar with a purple neon glow
 * border — round poster centre-cut photo on the left, "Ask BADSHAH AI" +
 * status line, an animated purple waveform of small vertical bars, and two
 * round buttons on the right (mic = filled purple, keyboard = dark with a
 * purple outline; glyphs drawn with canvas strokes). Status line is
 * "Tap to talk": this service has no call-state signal (see service note).
 * Drawn on a ~33 ms tick; no behaviour lives here — drag / tap /
 * long-press are handled by the service's touch listener.
 *
 * v7.8.1: bar ke gird dynamic hologram halo — layered strokes jin ka rang
 * purple -> cyan -> green cycle karta hai aur intensity saans leti hai
 * (listening state mein zyada roshan; setListening dekho). Poore phone ki
 * screen ke chaaron kinaron wali hologram lighting EdgeGlowView mein hai.
 */
class PillBarView(ctx: Context, private val bmp: Bitmap?) : View(ctx) {
    private val d = ctx.resources.displayMetrics.density
    private val sd = ctx.resources.displayMetrics.scaledDensity
    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; color = Color.argb(238, 23, 17, 48) }
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 1.8f * d; color = Color.argb(235, 168, 120, 255)
    }
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 2f * d; color = Color.argb(230, 168, 120, 255)
    }
    private val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; textSize = 14.5f * sd; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    private val statusPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(220, 196, 181, 253); textSize = 11.5f * sd
    }
    private val wavePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; color = Color.rgb(168, 120, 255); strokeWidth = 3.2f * d
    }
    private val micFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; color = Color.rgb(124, 58, 237) }
    private val kbFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; color = Color.argb(255, 38, 28, 74) }
    private val kbStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 1.4f * d; color = Color.argb(220, 168, 120, 255)
    }
    private val glyphPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; color = Color.WHITE; strokeWidth = 1.7f * d
    }
    private val glyphFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; color = Color.WHITE }
    private val haloPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; maskFilter = BlurMaskFilter(5f * d, BlurMaskFilter.Blur.NORMAL)
    }
    private val rect = RectF()
    private val clip = Path()
    private var startAt = System.currentTimeMillis()
    // v7.8.1 hologram palette: purple -> cyan -> green
    private val holoColors = intArrayOf(Color.rgb(139, 61, 255), Color.rgb(34, 211, 238), Color.rgb(52, 245, 162))
    @Volatile private var listening = false
    /** Listening state mein halo zyada roshan hota hai. Is service ke paas
     *  call-state ka koi signal nahi (service wali note dekho), is liye abhi
     *  isay koi call nahi karta — mechanism view mein tayyar hai. */
    fun setListening(on: Boolean) { listening = on }
    private fun lerpColor(c1: Int, c2: Int, f: Float): Int = Color.rgb(
        (Color.red(c1) + (Color.red(c2) - Color.red(c1)) * f).toInt(),
        (Color.green(c1) + (Color.green(c2) - Color.green(c1)) * f).toInt(),
        (Color.blue(c1) + (Color.blue(c2) - Color.blue(c1)) * f).toInt(),
    )
    private fun holoColor(now: Long): Int {
        val t = ((now - startAt) % 6000) / 6000f
        val seg = (t * holoColors.size).toInt() % holoColors.size
        val frac = (t * holoColors.size) - (t * holoColors.size).toInt()
        return lerpColor(holoColors[seg], holoColors[(seg + 1) % holoColors.size], frac)
    }
    private var animOn = false
    private val tick = object : Runnable {
        override fun run() { invalidate(); if (animOn) handler.postDelayed(this, 33) }
    }
    override fun onAttachedToWindow() { super.onAttachedToWindow(); animOn = true; handler.post(tick) }
    override fun onDetachedFromWindow() { stopAnim(); super.onDetachedFromWindow() }
    fun stopAnim() { animOn = false; handler.removeCallbacks(tick) }

    private fun drawMicGlyph(canvas: Canvas, cx: Float, cy: Float) {
        // mic body
        rect.set(cx - 3.4f * d, cy - 8f * d, cx + 3.4f * d, cy + 1.5f * d)
        canvas.drawRoundRect(rect, 3.4f * d, 3.4f * d, glyphFill)
        // cradle arc + stem + base
        rect.set(cx - 6.4f * d, cy - 5.5f * d, cx + 6.4f * d, cy + 7.5f * d)
        canvas.drawArc(rect, 20f, 140f, false, glyphPaint)
        canvas.drawLine(cx, cy + 7.5f * d, cx, cy + 10.5f * d, glyphPaint)
        canvas.drawLine(cx - 4.5f * d, cy + 10.5f * d, cx + 4.5f * d, cy + 10.5f * d, glyphPaint)
    }

    private fun drawKeyboardGlyph(canvas: Canvas, cx: Float, cy: Float) {
        rect.set(cx - 9.5f * d, cy - 6.5f * d, cx + 9.5f * d, cy + 6.5f * d)
        canvas.drawRoundRect(rect, 2.5f * d, 2.5f * d, glyphPaint)
        // key dots: top row of 4, bottom row of 3 + spacebar
        for (i in 0..3) canvas.drawCircle(cx + (i - 1.5f) * 4.4f * d, cy - 2.8f * d, 0.9f * d, glyphFill)
        for (i in 0..2) canvas.drawCircle(cx + (i - 1f) * 4.4f * d, cy + 0.8f * d, 0.9f * d, glyphFill)
        rect.set(cx - 4.6f * d, cy + 3.4f * d, cx + 4.6f * d, cy + 4.6f * d)
        canvas.drawRoundRect(rect, 1.2f * d, 1.2f * d, glyphFill)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat(); val h = height.toFloat()
        val cy = h / 2f
        val now = System.currentTimeMillis()
        val pulse = (sin((now % 2400) / 2400.0 * 2 * Math.PI).toFloat() + 1f) / 2f // 0..1 breathing
        val bodyInset = 7f * d
        val radius = (h - 2 * bodyInset) / 2f

        // v7.8.1: dynamic hologram halo — layered strokes in a hue that keeps
        // cycling purple -> cyan -> green, with breathing intensity (brighter
        // while listening). The outer layer is blurred, so the light falls
        // off softly beyond the pill edge instead of ending in a hard line.
        val holo = holoColor(now)
        val boost = if (listening) 1.35f else 1f
        val inten = (0.70f + 0.30f * pulse) * boost
        rect.set(bodyInset, bodyInset, w - bodyInset, h - bodyInset)
        haloPaint.color = holo; haloPaint.alpha = (34 * inten).toInt().coerceIn(0, 120); haloPaint.strokeWidth = 12f * d
        canvas.drawRoundRect(rect, radius, radius, haloPaint)
        glowPaint.color = holo; glowPaint.alpha = (58 * inten).toInt().coerceIn(0, 170); glowPaint.strokeWidth = 7f * d
        canvas.drawRoundRect(rect, radius, radius, glowPaint)
        canvas.drawRoundRect(rect, radius, radius, bgPaint)
        borderPaint.color = holo; borderPaint.alpha = (215 + 40 * pulse).toInt().coerceIn(0, 255)
        canvas.drawRoundRect(rect, radius, radius, borderPaint)

        // left: round poster centre-cut photo
        val avCx = 32f * d; val avR = 23f * d
        if (bmp != null) {
            clip.reset(); clip.addCircle(avCx, cy, avR, Path.Direction.CW)
            canvas.save(); canvas.clipPath(clip)
            val scale = maxOf((avR * 2) / bmp.width, (avR * 2) / bmp.height)
            val dw = bmp.width * scale; val dh = bmp.height * scale
            canvas.drawBitmap(bmp, avCx - dw / 2f, cy - dh / 2f, null)
            canvas.restore()
        } else {
            canvas.drawCircle(avCx, cy, avR, kbFill)
        }
        ringPaint.color = holo
        canvas.drawCircle(avCx, cy, avR + 0.8f * d, ringPaint)

        // text column
        val textX = 63f * d
        canvas.drawText("Ask BADSHAH AI", textX, cy - 3.5f * d, titlePaint)
        canvas.drawText("Tap to talk", textX, cy + 13.5f * d, statusPaint)

        // right: two round buttons (keyboard, then mic nearest the edge)
        val btnR = 17f * d
        val micCx = w - 25f * d
        val kbCx = micCx - 2 * btnR - 8f * d
        canvas.drawCircle(kbCx, cy, btnR, kbFill)
        canvas.drawCircle(kbCx, cy, btnR, kbStroke)
        drawKeyboardGlyph(canvas, kbCx, cy)
        canvas.drawCircle(micCx, cy, btnR, micFill)
        drawMicGlyph(canvas, micCx, cy)

        // waveform: 5 small vertical bars between text and buttons, animated
        wavePaint.color = holo
        val waveCx = kbCx - btnR - 16f * d
        for (i in 0..4) {
            val ph = (now % 900) / 900.0 * 2 * Math.PI + i * 0.9
            val bh = (5f + 6.5f * (0.5f + 0.5f * sin(ph).toFloat())) * d
            val x = waveCx + (i - 2) * 6.4f * d
            canvas.drawLine(x, cy - bh / 2f, x, cy + bh / 2f, wavePaint)
        }
    }
}

/**
 * v7.7.3 approved circle look (user design): poster CENTRE-CUT photo inside a
 * neon ring, a thick GOLD ARCH over the top, and three tilted ORBIT rings
 * (gold / teal / purple) whose glowing dots travel around the face — high-class
 * neon bloom, the same visual language as the call screen. The orbits turn
 * slowly on the existing 40 ms tick; a breathing halo pulses behind it all.
 */
class AgenticOrbView(ctx: Context, private val bmp: Bitmap?) : View(ctx) {
    private val d = ctx.resources.displayMetrics.density
    private val haloPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val archGlow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; color = Color.argb(90, 245, 197, 66)
    }
    private val archPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; color = Color.rgb(242, 193, 78)
    }
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; color = Color.argb(225, 150, 100, 255)
    }
    private val orbitPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; color = 0xFF171130.toInt() }
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val rect = RectF()
    private val clip = Path()
    private var startAt = System.currentTimeMillis()
    private var animOn = false
    private val tick = object : Runnable {
        override fun run() { invalidate(); if (animOn) handler.postDelayed(this, 40) }
    }
    override fun onAttachedToWindow() { super.onAttachedToWindow(); animOn = true; handler.post(tick) }
    override fun onDetachedFromWindow() { stopAnim(); super.onDetachedFromWindow() }
    fun stopAnim() { animOn = false; handler.removeCallbacks(tick) }

    // tilt (deg), period ms, direction, colour, dot phase offset
    private data class Orbit(val tilt: Float, val period: Long, val dir: Float, val color: Int, val phase: Double)
    private val orbits = listOf(
        Orbit(-18f, 9000L, 1f, Color.rgb(245, 197, 66), 0.0),
        Orbit(48f, 13000L, -1f, Color.rgb(45, 226, 198), 2.1),
        Orbit(96f, 17000L, 1f, Color.rgb(168, 120, 255), 4.2),
    )

    private fun drawOrbit(canvas: Canvas, cx: Float, cy: Float, rx: Float, ry: Float, o: Orbit, now: Long) {
        val elapsed = (now - startAt).toDouble()
        val tiltNow = o.tilt + (elapsed % 60000.0) / 60000.0 * 360.0 * 0.25 // slow precession
        canvas.save()
        canvas.translate(cx, cy)
        canvas.rotate(tiltNow.toFloat())
        orbitPaint.color = o.color; orbitPaint.alpha = 205; orbitPaint.strokeWidth = 1.4f * d
        rect.set(-rx, -ry, rx, ry)
        canvas.drawOval(rect, orbitPaint)
        // glowing dot travelling on the ellipse
        val th = o.phase + o.dir * (elapsed % o.period.toDouble()) / o.period.toDouble() * 2 * Math.PI
        val dx = (rx * cos(th)).toFloat(); val dy = (ry * sin(th)).toFloat()
        dotPaint.color = o.color; dotPaint.alpha = 80
        canvas.drawCircle(dx, dy, 4.6f * d, dotPaint)
        dotPaint.alpha = 255
        canvas.drawCircle(dx, dy, 2.1f * d, dotPaint)
        canvas.restore()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat(); val h = height.toFloat()
        val cx = w / 2f; val cy = h / 2f
        val now = System.currentTimeMillis()
        val pulse = (sin((now % 2400) / 2400.0 * 2 * Math.PI).toFloat() + 1f) / 2f // 0..1 breathing
        val faceR = min(w, h) / 2f - 14f * d

        // breathing halo
        haloPaint.color = Color.argb((36 + pulse * 46).toInt(), 139, 61, 255)
        canvas.drawCircle(cx, cy, faceR + 6f * d + pulse * 3.5f * d, haloPaint)

        // gold arch (behind the face, over the top)
        val archR = faceR + 5.5f * d
        rect.set(cx - archR, cy - archR, cx + archR, cy + archR)
        archGlow.strokeWidth = 8.5f * d
        canvas.drawArc(rect, 170f, 200f, false, archGlow)
        archPaint.strokeWidth = 4.6f * d
        canvas.drawArc(rect, 170f, 200f, false, archPaint)

        // face: poster centre-cut photo (circle-cropped) or dark disc
        if (bmp != null) {
            clip.reset(); clip.addCircle(cx, cy, faceR, Path.Direction.CW)
            canvas.save(); canvas.clipPath(clip)
            val scale = maxOf((faceR * 2) / bmp.width, (faceR * 2) / bmp.height)
            val dw = bmp.width * scale; val dh = bmp.height * scale
            canvas.drawBitmap(bmp, cx - dw / 2f, cy - dh / 2f, null)
            canvas.restore()
        } else {
            canvas.drawCircle(cx, cy, faceR, fillPaint)
        }

        // neon ring hugging the photo
        ringPaint.strokeWidth = 2f * d
        canvas.drawCircle(cx, cy, faceR + 1.2f * d, ringPaint)

        // three tilted orbits with travelling light dots
        val rx = faceR + 10.5f * d; val ry = faceR + 4f * d
        for (o in orbits) drawOrbit(canvas, cx, cy, rx, ry, o, now)
    }
}

/**
 * Screen ke kinaron par "energy border": halki base roshni ke upar do
 * chamakti dum-daar dharaein (comets) mukhalif simton mein safar karti
 * hain — purple/blue aur green — bilkul agentic system wali harkat.
 *
 * v7.8.1 (user correction): yehi asal "dynamic hologram lighting" hai —
 * POORE phone ki screen ke chaaron kinaron par, sirf bar ke gird nahi.
 * Palette user ki spec par purple -> cyan -> green kar di gayi hai, base
 * glow saans leti (pulse) hai, aur do comets kinaron par safar karti hain.
 * Overlay FLAG_NOT_TOUCHABLE hai (touch kabhi block nahi hota) aur sirf
 * BAR form active honay par dikhta hai; service band honay par ye view
 * remove ho jata hai.
 */
class EdgeGlowView(ctx: Context) : View(ctx) {
    private val density = ctx.resources.displayMetrics.density
    private val basePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val rect = RectF()
    // v7.8.1: hologram palette — sirf purple -> cyan -> green (user spec)
    private val colors = intArrayOf(
        Color.rgb(139, 61, 255), Color.rgb(34, 211, 238),
        Color.rgb(52, 245, 162),
    )
    private var startAt = System.currentTimeMillis()
    private val tick = object : Runnable {
        override fun run() { invalidate(); if (animOn) handler.postDelayed(this, 50) }
    }
    private var animOn = false
    init { alpha = 0.95f }
    override fun onAttachedToWindow() { super.onAttachedToWindow(); animOn = true; handler.post(tick) }
    override fun onDetachedFromWindow() { stopAnim(); super.onDetachedFromWindow() }
    fun stopAnim() { animOn = false; handler.removeCallbacks(tick) }

    private fun lerpColor(c1: Int, c2: Int, f: Float): Int = Color.rgb(
        (Color.red(c1) + (Color.red(c2) - Color.red(c1)) * f).toInt(),
        (Color.green(c1) + (Color.green(c2) - Color.green(c1)) * f).toInt(),
        (Color.blue(c1) + (Color.blue(c2) - Color.blue(c1)) * f).toInt(),
    )

    /** Rounded-rect perimeter par distance d (px) par point (x, y) deta hai. */
    private fun pointAt(dTotal: Float, x0: Float, y0: Float, x1: Float, y1: Float, r: Float): FloatArray {
        val wt = (x1 - x0) - 2 * r
        val ht = (y1 - y0) - 2 * r
        val arc = (Math.PI * r / 2).toFloat()
        var d = dTotal
        if (d < wt) return floatArrayOf(x0 + r + d, y0)
        d -= wt
        if (d < arc) { val a = -Math.PI / 2 + (d / arc) * (Math.PI / 2); return floatArrayOf(x1 - r + (r * cos(a)).toFloat(), y0 + r + (r * sin(a)).toFloat()) }
        d -= arc
        if (d < ht) return floatArrayOf(x1, y0 + r + d)
        d -= ht
        if (d < arc) { val a = 0.0 + (d / arc) * (Math.PI / 2); return floatArrayOf(x1 - r + (r * cos(a)).toFloat(), y1 - r + (r * sin(a)).toFloat()) }
        d -= arc
        if (d < wt) return floatArrayOf(x1 - r - d, y1)
        d -= wt
        if (d < arc) { val a = Math.PI / 2 + (d / arc) * (Math.PI / 2); return floatArrayOf(x0 + r + (r * cos(a)).toFloat(), y1 - r + (r * sin(a)).toFloat()) }
        d -= arc
        if (d < ht) return floatArrayOf(x0, y1 - r - d)
        d -= ht
        val a = Math.PI + (d / arc) * (Math.PI / 2)
        return floatArrayOf(x0 + r + (r * cos(a)).toFloat(), y0 + r + (r * sin(a)).toFloat())
    }

    private fun drawComet(canvas: Canvas, head: Float, perim: Float, inset: Float, r: Float, colA: Int, colB: Int) {
        val trail = 26
        val stepPx = 9f * density
        for (i in trail - 1 downTo 0) {
            val f = i / trail.toFloat() // 0 = head, 1 = tail
            var dd = (head - i * stepPx) % perim; if (dd < 0) dd += perim
            val p = pointAt(dd, inset, inset, width - inset, height - inset, r)
            dotPaint.color = lerpColor(colA, colB, f)
            dotPaint.alpha = ((1f - f) * 235).toInt()
            val rad = (3.6f - f * 2.3f) * density
            canvas.drawCircle(p[0], p[1], rad, dotPaint)
        }
        dotPaint.alpha = 255
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val now = System.currentTimeMillis()
        val t = ((now - startAt) % 6000) / 6000f
        val seg = (t * colors.size).toInt() % colors.size
        val frac = (t * colors.size) - (t * colors.size).toInt()
        val baseCol = lerpColor(colors[seg], colors[(seg + 1) % colors.size], frac)
        val inset = 2.2f * density
        val r = 26f * density
        rect.set(inset, inset, width - inset, height - inset)

        // soft outer glow (v7.8.1: breathing pulse)
        val pulse = (sin((now % 2400) / 2400.0 * 2 * Math.PI).toFloat() + 1f) / 2f // 0..1
        glowPaint.color = baseCol; glowPaint.alpha = (18 + pulse * 26).toInt(); glowPaint.strokeWidth = (7f + pulse * 3f) * density
        canvas.drawRoundRect(rect, r, r, glowPaint)
        // faint full border (purani pehchan, halki)
        basePaint.color = baseCol; basePaint.alpha = 64; basePaint.strokeWidth = 2.2f * density
        canvas.drawRoundRect(rect, r, r, basePaint)

        // two energy comets, opposite directions
        val wt = (width - 2 * inset) - 2 * r
        val ht = (height - 2 * inset) - 2 * r
        val perim = 2 * wt + 2 * ht + 2 * Math.PI.toFloat() * r
        if (perim <= 0) return
        val headA = ((now - startAt) % 9000) / 9000f * perim
        drawComet(canvas, headA, perim, inset, r, Color.rgb(139, 61, 255), Color.rgb(34, 211, 238))
        val headB = (headA + perim / 2f) % perim
        drawComet(canvas, headB, perim, inset, r, Color.rgb(34, 211, 238), Color.rgb(52, 245, 162))
    }
}
