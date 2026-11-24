package dev.pages.badshah222ai.core

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.accessibilityservice.GestureDescription
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Path
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Base64
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.io.ByteArrayOutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * v7.2 Accessibility Control (user decision 2026-11-21): user apni marzi se
 * Android Settings mein isay ON kare to BADSHAH us ke kehne par doosri apps
 * mein type, tap, scroll, back/home kar sakta hai aur screen parh sakta hai.
 * Koi data phone se bahar nahi jata — actions sirf usi waqt chalte hain jab
 * web layer bridge command bheje (call/chat mein user ki command par).
 */
class BadshahAccessibilityService : AccessibilityService() {
    companion object {
        @Volatile var instance: BadshahAccessibilityService? = null

        fun isEnabled(ctx: Context): Boolean {
            return try {
                val flat = Settings.Secure.getString(
                    ctx.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
                ) ?: return false
                val me = ComponentName(ctx, BadshahAccessibilityService::class.java)
                flat.split(':').any { ComponentName.unflattenFromString(it) == me }
            } catch (e: Exception) { false }
        }
    }

    override fun onServiceConnected() {
        instance = this
        /* v7.8.2: XML config ko code se bhi pakka karo — kuch OEM (Tecno/HiOS
           waghera) XML ke flags adhoore parhte hain; serviceInfo runtime par
           wohi event types + flags dobara set karne se salahiyat poori rehti hai. */
        try {
            val info = serviceInfo ?: AccessibilityServiceInfo()
            info.eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED or
                AccessibilityEvent.TYPE_VIEW_CLICKED or
                AccessibilityEvent.TYPE_VIEW_SCROLLED or
                AccessibilityEvent.TYPE_VIEW_FOCUSED or
                AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED or
                AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED or
                AccessibilityEvent.TYPE_WINDOWS_CHANGED
            info.flags = info.flags or
                AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
                AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS
            info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            info.notificationTimeout = 100
            serviceInfo = info
        } catch (e: Exception) { }
    }

    /* v7.8.2 Fault-2: system agar service ko unbind kare (masalan process par
       dabao) to TRUE wapas karo — is se framework dobara bind par poori
       onServiceConnected ki jagah onRebind bulata hai aur service baghair
       Settings toggle kiye wapas jud sakti hai. Pehle default (false) tha,
       jis par ek dafa unbind hone ke baad service murda padi rehti thi. */
    override fun onUnbind(intent: android.content.Intent?): Boolean {
        if (instance === this) instance = null
        return true
    }

    override fun onRebind(intent: android.content.Intent?) {
        super.onRebind(intent)
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) { }
    override fun onInterrupt() { }
    override fun onDestroy() {
        if (instance === this) instance = null
        super.onDestroy()
    }

    private fun root(): AccessibilityNodeInfo? = try { rootInActiveWindow } catch (e: Exception) { null }

    fun typeText(text: String): String {
        val r = root() ?: return "fail"
        return try {
            var node = r.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
            if (node == null) node = findEditable(r)
            if (node == null) return "noinput"
            node.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
            val args = Bundle()
            args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
            if (node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)) {
                "ok"
            } else {
                val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("badshah", text))
                if (node.performAction(AccessibilityNodeInfo.ACTION_PASTE)) "ok" else "fail"
            }
        } catch (e: Exception) { "fail" }
    }

    private fun findEditable(n: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (n == null) return null
        if (n.isEditable) return n
        for (i in 0 until n.childCount) {
            val c = findEditable(n.getChild(i))
            if (c != null) return c
        }
        return null
    }

    fun tapText(text: String): String {
        val r = root() ?: return "fail"
        return try {
            val q = text.lowercase().trim()
            if (q.isEmpty()) return "fail"
            val hits = ArrayList<AccessibilityNodeInfo>()
            collectByText(r, q, hits)
            val node = hits.firstOrNull() ?: return "notfound"
            var cur: AccessibilityNodeInfo? = node
            while (cur != null) {
                if (cur.isClickable) {
                    return if (cur.performAction(AccessibilityNodeInfo.ACTION_CLICK)) "ok" else "fail"
                }
                cur = cur.parent
            }
            "notfound"
        } catch (e: Exception) { "fail" }
    }

    private fun collectByText(n: AccessibilityNodeInfo?, q: String, out: ArrayList<AccessibilityNodeInfo>) {
        if (n == null) return
        val t = (n.text?.toString() ?: "").lowercase()
        val d = (n.contentDescription?.toString() ?: "").lowercase()
        if (t.contains(q) || d.contains(q)) out.add(n)
        for (i in 0 until n.childCount) collectByText(n.getChild(i), q, out)
    }

    fun scroll(dir: String): String {
        val r = root() ?: return "fail"
        return try {
            val node = findScrollable(r) ?: r
            val act = if (dir == "up" || dir == "back") {
                AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
            } else {
                AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
            }
            if (node.performAction(act)) "ok" else "fail"
        } catch (e: Exception) { "fail" }
    }

    private fun findScrollable(n: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (n == null) return null
        if (n.isScrollable) return n
        for (i in 0 until n.childCount) {
            val c = findScrollable(n.getChild(i))
            if (c != null) return c
        }
        return null
    }

    fun goBack(): String = try {
        if (performGlobalAction(GLOBAL_ACTION_BACK)) "ok" else "fail"
    } catch (e: Exception) { "fail" }

    fun goHome(): String = try {
        if (performGlobalAction(GLOBAL_ACTION_HOME)) "ok" else "fail"
    } catch (e: Exception) { "fail" }

    /* ----- v7.8.2: FULL phone control — recents, notifications, gestures, screenshot ----- */

    fun goRecents(): String = try {
        if (performGlobalAction(GLOBAL_ACTION_RECENTS)) "ok" else "fail"
    } catch (e: Exception) { "fail" }

    fun openNotifications(): String = try {
        if (performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)) "ok" else "fail"
    } catch (e: Exception) { "fail" }

    /** Screen par kisi bhi jagah (x,y) par asal ungli jaisa tap — dispatchGesture. */
    fun tapXY(x: Int, y: Int): String {
        return try {
            val p = Path(); p.moveTo(x.toFloat(), y.toFloat())
            val stroke = GestureDescription.StrokeDescription(p, 0, 70)
            if (dispatchGesture(GestureDescription.Builder().addStroke(stroke).build(), null, null)) "ok" else "fail"
        } catch (e: Exception) { "fail" }
    }

    /** Ek jagah se dusri jagah swipe (scroll/fling ka asal gesture rasta). */
    fun swipe(x1: Int, y1: Int, x2: Int, y2: Int): String {
        return try {
            val p = Path(); p.moveTo(x1.toFloat(), y1.toFloat()); p.lineTo(x2.toFloat(), y2.toFloat())
            val stroke = GestureDescription.StrokeDescription(p, 0, 320)
            if (dispatchGesture(GestureDescription.Builder().addStroke(stroke).build(), null, null)) "ok" else "fail"
        } catch (e: Exception) { "fail" }
    }

    /** Kisi text wale element ko der tak dabana (long-press menus ke liye). */
    fun longPressText(text: String): String {
        val r = root() ?: return "fail"
        return try {
            val q = text.lowercase().trim()
            if (q.isEmpty()) return "fail"
            val hits = ArrayList<AccessibilityNodeInfo>()
            collectByText(r, q, hits)
            val node = hits.firstOrNull() ?: return "notfound"
            var cur: AccessibilityNodeInfo? = node
            while (cur != null) {
                if (cur.isLongClickable) {
                    return if (cur.performAction(AccessibilityNodeInfo.ACTION_LONG_CLICK)) "ok" else "fail"
                }
                cur = cur.parent
            }
            "notfound"
        } catch (e: Exception) { "fail" }
    }

    /**
     * Screenshot (API 30+; canTakeScreenshot config lazmi hai). Bridge synchronous
     * hai is liye latch par zyada se zyada 3 second intezaar. Wapas: base64 JPEG
     * ya "fail"/"unsupported".
     */
    fun screenshotBase64(): String {
        if (Build.VERSION.SDK_INT < 30) return "unsupported"
        return try {
            val latch = CountDownLatch(1)
            var out = "fail"
            takeScreenshot(Display.DEFAULT_DISPLAY, mainExecutor, object : TakeScreenshotCallback {
                override fun onSuccess(result: ScreenshotResult) {
                    try {
                        val bmp = Bitmap.wrapHardwareBuffer(result.hardwareBuffer, result.colorSpace)
                        if (bmp != null) {
                            val copy = bmp.copy(Bitmap.Config.ARGB_8888, false)
                            bmp.recycle()
                            result.hardwareBuffer.close()
                            if (copy != null) {
                                val baos = ByteArrayOutputStream()
                                copy.compress(Bitmap.CompressFormat.JPEG, 65, baos)
                                copy.recycle()
                                out = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
                            }
                        } else {
                            result.hardwareBuffer.close()
                        }
                    } catch (e: Exception) { }
                    latch.countDown()
                }
                override fun onFailure(errorCode: Int) { latch.countDown() }
            })
            if (latch.await(3, TimeUnit.SECONDS)) out else "fail"
        } catch (e: Exception) { "fail" }
    }

    fun readScreen(): String {
        val r = root() ?: return ""
        val out = ArrayList<String>()
        try { collectTexts(r, out) } catch (e: Exception) { }
        val joined = out.distinct().joinToString(" | ")
        return if (joined.length > 1500) joined.substring(0, 1500) else joined
    }

    private fun collectTexts(n: AccessibilityNodeInfo?, out: ArrayList<String>) {
        if (n == null) return
        if (n.isVisibleToUser) {
            val t = n.text?.toString()?.trim()
            val d = n.contentDescription?.toString()?.trim()
            if (!t.isNullOrEmpty()) out.add(t) else if (!d.isNullOrEmpty()) out.add(d)
        }
        for (i in 0 until n.childCount) collectTexts(n.getChild(i), out)
    }
}
