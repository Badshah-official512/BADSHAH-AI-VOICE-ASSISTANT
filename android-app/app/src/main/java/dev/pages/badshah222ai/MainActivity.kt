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

    companion object {
        const val PRIMARY_URL = "https://badshah222-ai.pages.dev/app.html"
        const val FALLBACK_URL = "https://badshah-xjc.pages.dev/app.html"
        private const val REQ_MIC = 41
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
            CookieManager.getInstance().setAcceptCookie(true)
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

            webChromeClient = object : WebChromeClient() {
                override fun onPermissionRequest(request: PermissionRequest) {
                    val wantsAudio = request.resources.contains(PermissionRequest.RESOURCE_AUDIO_CAPTURE)
                    if (wantsAudio && ContextCompat.checkSelfPermission(
                            this@MainActivity, Manifest.permission.RECORD_AUDIO
                        ) != PackageManager.PERMISSION_GRANTED
                    ) {
                        ActivityCompat.requestPermissions(
                            this@MainActivity, arrayOf(Manifest.permission.RECORD_AUDIO), REQ_MIC
                        )
                        request.deny()
                        Toast.makeText(this@MainActivity, "Mic allow karke Call dobara dabao 🎙️", Toast.LENGTH_LONG).show()
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
    }

    override fun onBackPressed() {
        if (this::web.isInitialized && web.canGoBack()) web.goBack() else super.onBackPressed()
    }

    override fun onDestroy() {
        if (this::web.isInitialized) web.destroy()
        super.onDestroy()
    }
}
