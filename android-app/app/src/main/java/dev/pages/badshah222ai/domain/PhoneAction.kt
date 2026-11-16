package dev.pages.badshah222ai.domain

/** Everything the assistant can ask the phone to do. Pure data: no Android types. */
sealed class PhoneAction {
    data class OpenApp(val appName: String) : PhoneAction()
    object OpenYouTube : PhoneAction()
    object OpenScreen : PhoneAction()
    object OpenWhatsApp : PhoneAction()
    object OpenChrome : PhoneAction()
    object OpenCamera : PhoneAction()
    object OpenGallery : PhoneAction()
    object OpenVideos : PhoneAction()
    object OpenFiles : PhoneAction()
    object PickContact : PhoneAction()
    data class Call(val target: String) : PhoneAction()
    data class WhatsAppMessage(val target: String, val message: String) : PhoneAction()
    data class Sms(val target: String, val message: String) : PhoneAction()
    data class SetAlarm(val hour24: Int, val minute: Int, val label: String? = null) : PhoneAction()
    data class StartTimer(val seconds: Int, val label: String? = null) : PhoneAction()
    data class WebSearch(val query: String) : PhoneAction()
    /** The assistant's OWN in-app browser (empty query = just open it). Handled by the UI layer, not the executor. */
    data class OpenBrowser(val query: String) : PhoneAction()
    data class Flashlight(val on: Boolean) : PhoneAction()

    /** Calls and messages are never sent silently: the user must confirm first. */
    val needsConfirmation: Boolean
        get() = this is Call || this is WhatsAppMessage || this is Sms
}
