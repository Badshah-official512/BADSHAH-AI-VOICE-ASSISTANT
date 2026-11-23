package dev.pages.badshah222ai

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import android.widget.ImageView

/**
 * v7.7.3 premium startup screen (user-approved design): full-screen art —
 * neon room, hero standing in the ring, realistic golden crown,
 * "BADSHAH AI" and an English Loading... bar. Shown briefly on cold start,
 * then hands over to MainActivity with the original launch intent intact.
 */
class SplashActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        val iv = ImageView(this).apply {
            setImageResource(R.drawable.splash_art)
            scaleType = ImageView.ScaleType.CENTER_CROP
            contentDescription = "BADSHAH AI"
        }
        setContentView(iv)
        Handler(Looper.getMainLooper()).postDelayed({
            if (isFinishing || isDestroyed) return@postDelayed
            val next = Intent(this, MainActivity::class.java).apply {
                action = intent.action
                data = intent.data
                intent.extras?.let { putExtras(it) }
            }
            startActivity(next)
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
            finish()
        }, 1500)
    }
}
