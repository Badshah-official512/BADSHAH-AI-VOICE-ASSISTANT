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
import dev.pages.badshah222ai.core.AgentService

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

    companion object {
        const val PRIMARY_URL = "https://badshah222-ai.pages.dev/app.html"
        const val FALLBACK_URL = "https://badshah-xjc.pages.dev/app.html"
        private const val REQ_MIC = 41
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_MIC) {
            val req = pendingPermReq ?: return
            pendingPermReq = null
            val allGranted = grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }
            if (allGranted) req.grant(req.resources) else {
                req.deny()
                Toast.makeText(this, "Mic permission ke baghair call nahi ho sakti 🎙️", Toast.LENGTH_LONG).show()
            }
        }
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
            setBackgroundColor(Color.parseColor("#06130F"))
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                databaseEnabled = true
                mediaPlaybackRequiresUserGesture = false
                allowFileAccess = false
                allowContentAccess = true
                cacheMode = WebSettings.LOAD_DEFAULT
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) safeBrowsingEnabled = true
            }
            addJavascriptInterface(BadshahBridge(), "BadshahBridge")
            CookieManager.getInstance().setAcceptCookie(true)
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

            webChromeClient = object : WebChromeClient() {
                override fun onPermissionRequest(request: PermissionRequest) {
                    // v6.2: NEVER deny outright — hold the web request, ask Android,
                    // then grant/deny in onRequestPermissionsResult so the SAME call connects.
                    val missing = mutableListOf<String>()
                    if (request.resources.contains(PermissionRequest.RESOURCE_AUDIO_CAPTURE) &&
                        ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED
                    ) missing.add(Manifest.permission.RECORD_AUDIO)
                    if (request.resources.contains(PermissionRequest.RESOURCE_VIDEO_CAPTURE) &&
                        ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED
                    ) missing.add(Manifest.permission.CAMERA)
                    if (missing.isNotEmpty()) {
                        pendingPermReq = request
                        ActivityCompat.requestPermissions(this@MainActivity, missing.toTypedArray(), REQ_MIC)
                        return
                    }
                    request.grant(request.resources)
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
        setContentView(web)
        web.setOnApplyWindowInsetsListener { v, insets ->
            val bars = insets.getInsets(WindowInsets.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
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
        fun toast(msg: String) {
            runOnUiThread { Toast.makeText(this@MainActivity, msg, Toast.LENGTH_SHORT).show() }
        }
    }

    override fun onBackPressed() {
        if (this::web.isInitialized && web.canGoBack()) web.goBack() else super.onBackPressed()
    }

    override fun onDestroy() {
        if (this::web.isInitialized) web.destroy()
        super.onDestroy()
    }
}
