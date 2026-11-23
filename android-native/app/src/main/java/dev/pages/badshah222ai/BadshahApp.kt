package dev.pages.badshah222ai

import android.app.Application
import dev.pages.badshah222ai.core.CallSession
import dev.pages.badshah222ai.core.SecureStore
import dev.pages.badshah222ai.data.api.BadshahApi
import dev.pages.badshah222ai.data.auth.AuthRepository
import dev.pages.badshah222ai.ui.theme.ChatThemeState
import dev.pages.badshah222ai.ui.theme.ChatThemes

/**
 * Manual dependency container (same approach as the old project's BadshahApp:
 * no DI plugin, no extra build risk).
 */
class Container(app: Application) {
    val secureStore = SecureStore(app)
    val auth = AuthRepository()
    val api = BadshahApi(token = { auth.idToken() })
    val callSession = CallSession
    val phoneExecutor = dev.pages.badshah222ai.data.phone.PhoneExecutor(app)

    init {
        // Wire the live-call manager once, app-wide (context + BYOK store).
        callSession.liveCall.attach(app, secureStore)
    }
    val chatTheme = ChatThemeState(
        ChatThemes.firstOrNull { it.id == secureStore.chatThemeId } ?: ChatThemes[0],
    )

    fun selectChatTheme(id: String) {
        ChatThemes.firstOrNull { it.id == id }?.let {
            chatTheme.select(it)
            secureStore.chatThemeId = id
        }
    }
}

class BadshahApp : Application() {
    lateinit var container: Container
        private set

    override fun onCreate() {
        super.onCreate()
        container = Container(this)
    }
}
