package dev.pages.badshah222ai

import android.Manifest
import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.app.Activity
import android.graphics.Bitmap
import android.media.projection.MediaProjectionManager
import android.util.Base64
import androidx.activity.result.contract.ActivityResultContracts
import dev.pages.badshah222ai.core.BadshahAccessibilityService
import dev.pages.badshah222ai.core.CallService
import dev.pages.badshah222ai.core.FloatingOrbService
import dev.pages.badshah222ai.core.ScreenShareService
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.delay
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.graphics.Color
import android.net.Uri
import android.app.role.RoleManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.provider.Settings
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.webkit.JavascriptInterface
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.PermissionRequest
import android.webkit.URLUtil
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import dev.pages.badshah222ai.core.AgentService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * BADSHAH AI v4 — the app IS the living web assistant (same experience as the
 * website: living logo, chat, live calls, key pool). This shell adds the native
 * powers the browser cannot: mic permission for Live calls, real downloads,
 * and the background AgentService.
 */
class MainActivity : ComponentActivity() {
    private lateinit var web: WebView
    private var rootLayout: FrameLayout? = null
    private var triedFallback = false
    private var pendingPermReq: PermissionRequest? = null
    private val uiScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var audioFocusReq: AudioFocusRequest? = null
    @Volatile private var screenPumpOn = false

    private val projectionLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        if (res.resultCode == Activity.RESULT_OK && res.data != null) {
            ScreenShareService.start(this, res.resultCode, res.data!!)
            startFramePump()
        } else {
            runOnUiThread { if (this::web.isInitialized) web.evaluateJavascript("window.__bhScreenOff&&window.__bhScreenOff()", null) }
        }
    }

    private fun startFramePump() {
        screenPumpOn = true
        uiScope.launch {
            var lastSent = 0L
            while (screenPumpOn) {
                val bmp = ScreenShareService.frame.value
                val now = System.currentTimeMillis()
                if (bmp != null && ScreenShareService.running.value && now - lastSent >= 1000) {
                    lastSent = now
                    try {
                        val scaled = if (bmp.width > 640) Bitmap.createScaledBitmap(bmp, 640, (bmp.height * 640f / bmp.width).toInt().coerceAtLeast(2), true) else bmp
                        val baos = ByteArrayOutputStream()
                        scaled.compress(Bitmap.CompressFormat.JPEG, 55, baos)
                        if (scaled !== bmp) scaled.recycle()
                        val b64 = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
                        runOnUiThread { if (this@MainActivity::web.isInitialized) web.evaluateJavascript("window.__bhScreenFrame&&window.__bhScreenFrame('" + b64 + "')", null) }
                    } catch (e: Exception) { }
                }
                delay(250)
            }
        }
    }

    companion object {
        const val PRIMARY_URL = "https://badshah222-ai.pages.dev/app.html"
        const val FALLBACK_URL = "https://badshah-xjc.pages.dev/app.html"
        private const val REQ_WEB = 45
        private const val REQ_MIC_PRE = 44
        @Volatile var appInForeground = false
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_WEB) {
            val req = pendingPermReq ?: return
            pendingPermReq = null
            val grant = ArrayList<String>()
            for (res in req.resources) {
                val perm = when (res) {
                    PermissionRequest.RESOURCE_AUDIO_CAPTURE -> Manifest.permission.RECORD_AUDIO
                    PermissionRequest.RESOURCE_VIDEO_CAPTURE -> Manifest.permission.CAMERA
                    else -> null
                }
                if (perm != null && ContextCompat.checkSelfPermission(this, perm) == PackageManager.PERMISSION_GRANTED) grant.add(res)
            }
            if (grant.isNotEmpty()) req.grant(grant.toTypedArray()) else {
                req.deny()
                Toast.makeText(this, "Permission ke baghair ye feature nahi chal sakta", Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onResume() { super.onResume(); appInForeground = true }

    override fun onPause() { appInForeground = false; super.onPause() }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        AgentService.start(this)

        web = WebView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(Color.parseColor("#F7F7F4"))
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                databaseEnabled = true
                mediaPlaybackRequiresUserGesture = false
                allowFileAccess = false
                allowContentAccess = true
                cacheMode = WebSettings.LOAD_DEFAULT
                useWideViewPort = true
                loadWithOverviewMode = true
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) safeBrowsingEnabled = true
            }
            addJavascriptInterface(BadshahBridge(), "BadshahBridge")
            CookieManager.getInstance().setAcceptCookie(true)
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

            webChromeClient = object : WebChromeClient() {
                override fun onPermissionRequest(request: PermissionRequest) {
                    // v6.2: NEVER deny outright — hold the web request, ask Android,
                    // then grant/deny in onRequestPermissionsResult so the SAME call connects.
                    // v7.2: AUDIO (call) + VIDEO (Camera Share) — jo permission missing ho, wahi mango.
                    val wantsAudio = request.resources.contains(PermissionRequest.RESOURCE_AUDIO_CAPTURE)
                    val wantsVideo = request.resources.contains(PermissionRequest.RESOURCE_VIDEO_CAPTURE)
                    if (!wantsAudio && !wantsVideo) { request.deny(); return }
                    val missing = ArrayList<String>()
                    if (wantsAudio && ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) missing.add(Manifest.permission.RECORD_AUDIO)
                    if (wantsVideo && ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) missing.add(Manifest.permission.CAMERA)
                    if (missing.isEmpty()) { request.grant(request.resources); return }
                    if (pendingPermReq != null) { runCatching { pendingPermReq?.deny() }; pendingPermReq = null }
                    pendingPermReq = request
                    ActivityCompat.requestPermissions(this@MainActivity, missing.toTypedArray(), REQ_WEB)
                }
            }
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, req: WebResourceRequest): Boolean {
                    val host = req.url.host ?: ""
                    val internal = host.endsWith("pages.dev") || host.contains("google") ||
                        host.contains("firebase") || host.contains("gstatic") || host.contains("googleapis")
                    if (internal) return false
                    startActivity(Intent(Intent.ACTION_VIEW, req.url))
                    return true
                }

                override fun onReceivedError(view: WebView, req: WebResourceRequest, err: WebResourceError) {
                    if (req.isForMainFrame && !triedFallback) {
                        triedFallback = true
                        view.loadUrl(FALLBACK_URL)
                    }
                }
            }
            setDownloadListener { url, _, contentDisposition, mime, _ ->
                try {
                    val name = URLUtil.guessFileName(url, contentDisposition, mime)
                    val req = DownloadManager.Request(Uri.parse(url))
                        .setMimeType(mime)
                        .setTitle(name)
                        .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                        .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, name)
                    (getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).enqueue(req)
                    Toast.makeText(this@MainActivity, "Download shuru ho gaya ⬇", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(this@MainActivity, "Download nahi ho saka", Toast.LENGTH_SHORT).show()
                }
            }
            loadUrl(PRIMARY_URL)
        }
        // v6.3 top-bar fix: WebView ko ek container mein rakh kar MARGIN se neeche kiya —
        // status bar + camera cutout ke neeche, upar aur ~30dp extra. Padding WebView par
        // kuch phones (TECNO waghera) par kaam nahi karti thi, margin hamesha kaam karta hai.
        val root = FrameLayout(this).apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            setBackgroundColor(Color.parseColor("#F7F7F4"))
            addView(web)
        }
        rootLayout = root
        setContentView(root)
        val extraTop = (30 * resources.displayMetrics.density).toInt()
        root.setOnApplyWindowInsetsListener { _, insets ->
            val bars = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
            val lp = web.layoutParams as FrameLayout.LayoutParams
            lp.setMargins(bars.left, bars.top + extraTop, bars.right, bars.bottom)
            web.layoutParams = lp
            insets
        }
        root.post { root.requestApplyInsets() }
    }

    /** v6.1 device bridge: the web assistant (window.BadshahBridge) can open apps,
     *  play YouTube, search the web and draft messages on the user's own command. */
    inner class BadshahBridge {
        @JavascriptInterface
        fun openApp(target: String) {            runOnUiThread {
                try {
                    when (target) {
                        "camera" -> startActivity(Intent(MediaStore.ACTION_IMAGE_CAPTURE))
                        "settings" -> startActivity(Intent(Settings.ACTION_SETTINGS))
                        "gallery" -> startActivity(Intent(Intent.ACTION_VIEW).setType("image/*"))
                        else -> {
                            val launch = packageManager.getLaunchIntentForPackage(target)
                            if (launch != null) startActivity(launch) else {
                                Toast.makeText(this@MainActivity, "Ye app is phone mein nahi mili", Toast.LENGTH_SHORT).show()
                                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/search?q=" + Uri.encode(target) + "&c=apps")))
                            }
                        }
                    }
                } catch (e: Exception) {
                    Toast.makeText(this@MainActivity, "App nahi khul saka", Toast.LENGTH_SHORT).show()
                }
            }
        }

        @JavascriptInterface
        fun openAppByName(name: String): String {
            // v7.1: phone mein installed KOI BHI app naam se kholo — installed apps ke
            // labels search karke best match launch karte hain. Koi naya permission nahi:
            // manifest ki <queries> MAIN/LAUNCHER declaration hi kaafi hai.
            return try {
                val q = name.lowercase().replace(" ", "").trim()
                if (q.length < 2) return "notfound"
                val qi = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                val apps = packageManager.queryIntentActivities(qi, 0)
                var bestPkg: String? = null
                var bestScore = -1
                for (ri in apps) {
                    val label = ri.loadLabel(packageManager).toString().lowercase().replace(" ", "")
                    val score = when {
                        label == q -> 100
                        label.startsWith(q) -> 80
                        label.contains(q) -> 60
                        label.length >= 3 && q.contains(label) -> 40
                        else -> -1
                    }
                    if (score > bestScore) { bestScore = score; bestPkg = ri.activityInfo.packageName }
                }
                val pkg = bestPkg
                if (pkg == null || bestScore < 0) return "notfound"
                runOnUiThread {
                    try {
                        val launch = packageManager.getLaunchIntentForPackage(pkg)
                        if (launch != null) startActivity(launch)
                    } catch (e: Exception) { }
                }
                "ok"
            } catch (e: Exception) { "notfound" }
        }

        @JavascriptInterface
        fun openYouTube(query: String) {
            runOnUiThread {
                val url = "https://www.youtube.com/results?search_query=" + Uri.encode(query)
                try {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).setPackage("com.google.android.youtube"))
                } catch (e: Exception) {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                }
            }
        }

        @JavascriptInterface
        fun searchWeb(query: String) {
            runOnUiThread {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=" + Uri.encode(query))))
            }
        }

        @JavascriptInterface
        fun openUrl(url: String) {
            runOnUiThread {
                try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) } catch (e: Exception) { }
            }
        }

        @JavascriptInterface
        fun shareText(text: String) {
            runOnUiThread {
                val i = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
                startActivity(Intent.createChooser(i, "Message bhejo"))
            }
        }

        @JavascriptInterface
        fun dial(number: String) {
            runOnUiThread {
                startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number")))
            }
        }

        @JavascriptInterface
        fun micTakeover() {
            // v6.6: MIC CHHEEN-LO system — asal call apps ki tarah. Audio focus +
            // MODE_IN_COMMUNICATION se system dusri apps ka mic khali kar deta hai,
            // phir WebView ka getUserMedia araam se mic le leta hai. Speaker ON.
            runOnUiThread {
                try {
                    val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
                    am.mode = AudioManager.MODE_IN_COMMUNICATION
                    val attrs = AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                    val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                        .setAudioAttributes(attrs)
                        .setOnAudioFocusChangeListener { }
                        .build()
                    audioFocusReq = req
                    am.requestAudioFocus(req)
                    @Suppress("DEPRECATION") am.isSpeakerphoneOn = true
                } catch (e: Exception) { }
                // Call ko minimize/background mein zinda rakho (foreground service)
                try { CallService.start(this@MainActivity) } catch (e: Exception) { }
                // v7.2: floating bubble — sirf jab overlay permission pehle se granted ho
                try { if (Settings.canDrawOverlays(this@MainActivity)) FloatingOrbService.start(this@MainActivity) } catch (e: Exception) { }
            }
        }

        @JavascriptInterface
        fun micRelease() {
            runOnUiThread {
                try {
                    val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
                    audioFocusReq?.let { runCatching { am.abandonAudioFocusRequest(it) } }
                    audioFocusReq = null
                    @Suppress("DEPRECATION") am.isSpeakerphoneOn = false
                    am.mode = AudioManager.MODE_NORMAL
                } catch (e: Exception) { }
                try { CallService.stop(this@MainActivity) } catch (e: Exception) { }
                try { FloatingOrbService.stop(this@MainActivity) } catch (e: Exception) { }
            }
        }

        @JavascriptInterface
        fun openWhatsApp(number: String, text: String) {
            // v6.7: WhatsApp chat kholo, message type karke tayyar — send user khud dabayega
            // (WhatsApp kisi app ko auto-send ki ijazat nahi deta).
            runOnUiThread {
                val num = number.replace(Regex("[^0-9]"), "")
                val url = "https://wa.me/" + num + if (text.isNotEmpty()) "?text=" + Uri.encode(text) else ""
                try {
                    val i = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                    i.setPackage("com.whatsapp")
                    startActivity(i)
                } catch (e: Exception) {
                    try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
                    catch (e2: Exception) { Toast.makeText(this@MainActivity, "WhatsApp nahi khul saka", Toast.LENGTH_SHORT).show() }
                }
            }
        }

        @JavascriptInterface
        fun startScreenShare() {
            // v6.7: Android ka apna consent dialog aayega — user Allow kare to hi screen Gemini ko jayegi.
            runOnUiThread {
                try {
                    val mpm = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                    projectionLauncher.launch(mpm.createScreenCaptureIntent())
                } catch (e: Exception) {
                    if (this@MainActivity::web.isInitialized) web.evaluateJavascript("window.__bhScreenOff&&window.__bhScreenOff()", null)
                }
            }
        }

        @JavascriptInterface
        fun stopScreenShare() {
            screenPumpOn = false
            ScreenShareService.stop(this@MainActivity)
        }

        @JavascriptInterface
        fun ensureMic() {
            // v6.4: Call dabane par pehle hi Android mic permission pakki kar lo —
            // phir getUserMedia ko sirf grant karna rehta hai, race khatam.
            if (ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                runOnUiThread { ActivityCompat.requestPermissions(this@MainActivity, arrayOf(Manifest.permission.RECORD_AUDIO), REQ_MIC_PRE) }
            }
        }

        @JavascriptInterface
        fun googleSignIn() {
            // v6.4: ASLI Google sign-in — phone ke Gmail accounts ka native picker
            // (Credential Manager). WebView ke andar popup OAuth Google band karta hai,
            // is liye token yahan se la kar web Firebase ko dete hain.
            uiScope.launch {
                try {
                    val option = GetGoogleIdOption.Builder()
                        .setFilterByAuthorizedAccounts(false)
                        .setServerClientId(getString(R.string.default_web_client_id))
                        .build()
                    val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
                    val result = CredentialManager.create(this@MainActivity).getCredential(this@MainActivity, request)
                    val cred = result.credential
                    if (cred is CustomCredential && cred.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                        val token = GoogleIdTokenCredential.createFrom(cred.data).idToken
                        runOnUiThread { web.evaluateJavascript("window.__bhGoogleToken&&window.__bhGoogleToken(" + JSONObject.quote(token) + ")", null) }
                    } else {
                        runOnUiThread { web.evaluateJavascript("window.__bhGoogleError&&window.__bhGoogleError('Google account nahi mila')", null) }
                    }
                } catch (e: Exception) {
                    val msg = if (e is androidx.credentials.exceptions.NoCredentialException) "Is phone par koi Google account nahi mila — pehle phone ki Settings mein Gmail add karo" else "Google sign-in cancel ya fail ho gaya"
                    runOnUiThread { web.evaluateJavascript("window.__bhGoogleError&&window.__bhGoogleError(" + JSONObject.quote(msg) + ")", null) }
                }
            }
        }


        @JavascriptInterface
        fun a11yStatus(): String {
            return if (BadshahAccessibilityService.isEnabled(this@MainActivity)) "on" else "off"
        }

        @JavascriptInterface
        fun openA11ySettings() {
            runOnUiThread {
                try { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) } catch (e: Exception) { }
            }
        }

        @JavascriptInterface
        fun overlayStatus(): String {
            return try { if (Settings.canDrawOverlays(this@MainActivity)) "on" else "off" } catch (e: Exception) { "off" }
        }

        @JavascriptInterface
        fun openOverlaySettings() {
            runOnUiThread {
                try { startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + packageName))) } catch (e: Exception) { }
            }
        }

        @JavascriptInterface
        fun a11yType(text: String): String {
            val s = BadshahAccessibilityService.instance ?: return "off"
            return s.typeText(text)
        }

        @JavascriptInterface
        fun a11yTap(text: String): String {
            val s = BadshahAccessibilityService.instance ?: return "off"
            return s.tapText(text)
        }

        @JavascriptInterface
        fun a11yScroll(dir: String): String {
            val s = BadshahAccessibilityService.instance ?: return "off"
            return s.scroll(dir)
        }

        @JavascriptInterface
        fun a11yBack(): String {
            val s = BadshahAccessibilityService.instance ?: return "off"
            return s.goBack()
        }

        @JavascriptInterface
        fun a11yHome(): String {
            val s = BadshahAccessibilityService.instance ?: return "off"
            return s.goHome()
        }

        @JavascriptInterface
        fun a11yRead(): String {
            val s = BadshahAccessibilityService.instance ?: return "off"
            return s.readScreen()
        }

        @JavascriptInterface
        @JavascriptInterface
        fun deviceInfo(): String {
            // v7.2 Phase 5: Settings > Performance & Device ke liye asal device maloomat
            return try {
                val pi = packageManager.getPackageInfo(packageName, 0)
                JSONObject()
                    .put("model", Build.MODEL ?: "")
                    .put("maker", Build.MANUFACTURER ?: "")
                    .put("android", Build.VERSION.RELEASE ?: "")
                    .put("app", pi.versionName ?: "")
                    .toString()
            } catch (e: Exception) { "{}" }
        }

        @JavascriptInterface
        fun assistantStatus(): String {
            // v7.2 Phase 5: kya BADSHAH AI phone ka default assistant hai?
            if (Build.VERSION.SDK_INT < 29) return "na"
            return try {
                val rm = getSystemService(RoleManager::class.java)
                if (rm != null && rm.isRoleAvailable(RoleManager.ROLE_ASSISTANT) && rm.isRoleHeld(RoleManager.ROLE_ASSISTANT)) "on" else "off"
            } catch (e: Exception) { "na" }
        }

        @JavascriptInterface
        fun requestAssistant() {
            // v7.2 Phase 5: Android ki apni screen se default assistant chunne do (user khud tap karega)
            if (Build.VERSION.SDK_INT < 29) return
            runOnUiThread {
                try {
                    val rm = getSystemService(RoleManager::class.java)
                    if (rm != null && rm.isRoleAvailable(RoleManager.ROLE_ASSISTANT) && !rm.isRoleHeld(RoleManager.ROLE_ASSISTANT)) {
                        @Suppress("DEPRECATION")
                        startActivityForResult(rm.createRequestRoleIntent(RoleManager.ROLE_ASSISTANT), 46)
                    }
                } catch (e: Exception) { }
            }
        }

        fun setChromeTheme(mode: String) {
            // v7.2 G4: call screen (dark) par status-bar hissa bhi dark, warna light
            runOnUiThread {
                try {
                    val dark = mode == "dark"
                    rootLayout?.setBackgroundColor(Color.parseColor(if (dark) "#0A0F2E" else "#F7F7F4"))
                    if (Build.VERSION.SDK_INT >= 30) {
                        val ctl = window.insetsController
                        if (dark) ctl?.setSystemBarsAppearance(0, WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS)
                        else ctl?.setSystemBarsAppearance(WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS, WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS)
                    }
                } catch (e: Exception) { }
            }
        }

        @JavascriptInterface
        fun appVersionCode(): Int {
            return try {
                val pi = packageManager.getPackageInfo(packageName, 0)
                if (Build.VERSION.SDK_INT >= 28) pi.longVersionCode.toInt() else @Suppress("DEPRECATION") pi.versionCode
            } catch (e: Exception) { 0 }
        }

        @JavascriptInterface
        fun toast(msg: String) {
            runOnUiThread { Toast.makeText(this@MainActivity, msg, Toast.LENGTH_SHORT).show() }
        }

        @JavascriptInterface
        fun setTorch(on: String) {
            // v7: Phone Control — Gemini Live tool / chat command se torch on/off.
            // CameraManager.setTorchMode ko CAMERA permission nahi chahiye.
            runOnUiThread {
                try {
                    val cm = getSystemService(Context.CAMERA_SERVICE) as android.hardware.camera2.CameraManager
                    val id = cm.cameraIdList.firstOrNull {
                        cm.getCameraCharacteristics(it).get(android.hardware.camera2.CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
                    }
                    if (id != null) cm.setTorchMode(id, on == "on")
                } catch (e: Exception) { }
            }
        }

        @JavascriptInterface
        fun adjustVolume(dir: String) {
            // v7: Phone Control — media volume barhao/kam karo (system UI ke saath).
            runOnUiThread {
                try {
                    val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
                    val d = if (dir == "down") AudioManager.ADJUST_LOWER else AudioManager.ADJUST_RAISE
                    am.adjustStreamVolume(AudioManager.STREAM_MUSIC, d, AudioManager.FLAG_SHOW_UI)
                } catch (e: Exception) { }
            }
        }
    }

    override fun onBackPressed() {
        if (this::web.isInitialized && web.canGoBack()) web.goBack() else super.onBackPressed()
    }

    override fun onDestroy() {
        uiScope.cancel()
        if (this::web.isInitialized) web.destroy()
        super.onDestroy()
    }
}
