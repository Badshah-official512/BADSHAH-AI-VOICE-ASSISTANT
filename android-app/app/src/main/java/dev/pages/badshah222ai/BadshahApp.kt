package dev.pages.badshah222ai

import android.app.Application
import android.app.NotificationManager
import dev.pages.badshah222ai.core.SecureStore
import dev.pages.badshah222ai.data.ai.AiRepository
import dev.pages.badshah222ai.data.auth.AuthRepository
import dev.pages.badshah222ai.data.ensureNotificationChannel
import dev.pages.badshah222ai.data.local.ChatStore
import dev.pages.badshah222ai.data.phone.PhoneExecutor
import dev.pages.badshah222ai.data.supabase.SupabaseApi
import dev.pages.badshah222ai.data.voice.LiveSession
import dev.pages.badshah222ai.data.voice.SpeechInput
import dev.pages.badshah222ai.data.voice.TtsManager

/** Simple manual dependency container (keeps the project free of DI-plugin build risk). */
class Container(app: Application) {
    val store = SecureStore(app)
    val auth = AuthRepository(app.getString(R.string.default_web_client_id)) // generated from google-services.json
    val cloud = SupabaseApi { auth.supabaseToken() }
    val ai = AiRepository(store, cloud)
    val tts = TtsManager(app, store, cloud)
    val speech = SpeechInput(app)
    val live = LiveSession(app, store)
    val phone = PhoneExecutor(app)
    val chats = ChatStore(app)
}

class BadshahApp : Application() {
    lateinit var container: Container
    override fun onCreate() {
        super.onCreate()
        container = Container(this)
        ensureNotificationChannel(getSystemService(NotificationManager::class.java))
        container.tts.init()
    }
}
