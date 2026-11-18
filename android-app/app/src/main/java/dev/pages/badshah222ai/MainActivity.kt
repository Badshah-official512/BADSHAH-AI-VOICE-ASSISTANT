package dev.pages.badshah222ai

import android.Manifest
import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.provider.Settings
import android.view.WindowInsets
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
import dev.pages.badshah222ai.core.WakeWordService

/**
 * BADSHAH AI v4 — the app IS the living web assistant (same experience as the
 * website: living logo, chat, live calls, key pool). This shell adds the native
 * powers the browser cannot: mic permission for Live calls, real downloads,
 * and the background AgentService.
 */
class MainActivity : ComponentActivity() {
    private lateinit var web: WebView
    private var triedFallback = false
    private var pendingPermReq: PermissionRequest? = null
    private var pendingWake = false
    private val uiScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    companion object {
        const val PRIMARY_URL = "https://badshah222-ai.pages.dev/app.html"
        const val FALLBACK_URL = "https://badshah-xjc.pages.dev/app.html"
        private const val REQ_MIC = 41
        private const val REQ_WAKE = 43
        private const val REQ_MIC_PRE = 44
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_WAKE) {
            val granted = grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }
            if (granted && wakeEnabled()) WakeWordService.start(this)
            return
        }
        if (requestCode == REQ_MIC) {
            val req = pendingPermReq ?: return
            pendingPermReq = null
            val allGranted = grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }
            if (allGranted) req.grant(arrayOf(PermissionRequest.RESOURCE_AUDIO_CAPTURE)) else {
                req.deny()
                Toast.makeText(this, "Mic permission ke baghair call nahi ho sakti 🎙️", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun wakeEnabled(): Boolean =
        getSharedPreferences("bh_shell", Context.MODE_PRIVATE).getBoolean("wake_on", false)

    private fun handleWakeIntent(intent: Intent?) {
        if (intent?.getStringExtra(WakeWordService.EXTRA_WAKE) == "1") pendingWake = true
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleWakeIntent(intent)
        if (pendingWake && this::web.isInitialized) {
            pendingWake = false
            web.evaluateJavascript("window.__bhWake&&window.__bhWake()", null)
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        AgentService.start(this)
        handleWakeIntent(intent)
        if (wakeEnabled() && ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            WakeWordService.start(this)
        }

        web = WebView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(Color.parseColor("#F3E8FF"))
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
                    // v6.3: sirf AUDIO — camera permission manifest mein hai hi nahi.
                    val wantsAudio = request.resources.contains(PermissionRequest.RESOURCE_AUDIO_CAPTURE)
                    if (!wantsAudio) { request.deny(); return }
                    if (pendingPermReq != null) { runCatching { pendingPermReq?.deny() }; pendingPermReq = null }
                    if (ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                        pendingPermReq = request
                        ActivityCompat.requestPermissions(this@MainActivity, arrayOf(Manifest.permission.RECORD_AUDIO), REQ_MIC)
                        return
                    }
                    request.grant(arrayOf(PermissionRequest.RESOURCE_AUDIO_CAPTURE))
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

                override fun onPageFinished(view: WebView, url: String) {
                    if (pendingWake) {
                        pendingWake = false
                        view.evaluateJavascript("window.__bhWake&&window.__bhWake()", null)
                    }
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
            setBackgroundColor(Color.parseColor("#F3E8FF"))
            addView(web)
        }
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
        fun openApp(target: String) {
            runOnUiThread {
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
        fun ensureMic() {
            // v6.4: Call dabane par pehle hi Android mic permission pakki kar lo —
            // phir getUserMedia ko sirf grant karna rehta hai, race khatam.
            if (ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                runOnUiThread { ActivityCompat.requestPermissions(this@MainActivity, arrayOf(Manifest.permission.RECORD_AUDIO), REQ_MIC_PRE) }
            }
        }

        @JavascriptInterface
        fun callStarted() {
            // Wake-word recognizer mic pakad kar baitha hota hai — call ke dauran use roko,
            // warna getUserMedia "audio source busy" par fail hota hai (TECNO par dekha gaya).
            if (wakeEnabled()) WakeWordService.stop(this@MainActivity)
        }

        @JavascriptInterface
        fun callEnded() {
            if (wakeEnabled() && ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                WakeWordService.start(this@MainActivity)
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
        fun setWakeWord(on: Boolean) {
            getSharedPreferences("bh_shell", Context.MODE_PRIVATE).edit().putBoolean("wake_on", on).apply()
            runOnUiThread {
                if (on) {
                    if (ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                        WakeWordService.start(this@MainActivity)
                        Toast.makeText(this@MainActivity, "Wake word ON — 'BADSHAH' bolo, main aa jaunga 🎙️", Toast.LENGTH_LONG).show()
                    } else {
                        ActivityCompat.requestPermissions(this@MainActivity, arrayOf(Manifest.permission.RECORD_AUDIO), REQ_WAKE)
                    }
                } else {
                    WakeWordService.stop(this@MainActivity)
                    Toast.makeText(this@MainActivity, "Wake word band ho gaya", Toast.LENGTH_SHORT).show()
                }
            }
        }

        @JavascriptInterface
        fun isWakeWord(): Boolean = wakeEnabled()

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
