package dev.pages.badshah222ai.data.phone

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.AlarmClock
import android.provider.ContactsContract
import android.provider.MediaStore
import android.telephony.PhoneNumberUtils
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat
import dev.pages.badshah222ai.domain.PhoneAction
import java.net.URLEncoder

sealed class PhoneResult {
    data class Done(val message: String) : PhoneResult()
    data class NeedPermission(val permission: String, val why: String) : PhoneResult()
    data class Failed(val message: String) : PhoneResult()
    object LaunchContactPicker : PhoneResult()
}

/**
 * Executes assistant actions through OFFICIAL Android intents only.
 * Nothing here places a call or sends a message by itself: the dialer / SMS app / WhatsApp opens
 * pre-filled and the user presses the final button. (Callers also show a confirmation first.)
 */
class PhoneExecutor(private val ctx: Context) {
    private fun launch(i: Intent): Boolean = try { i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); ctx.startActivity(i); true } catch (e: ActivityNotFoundException) { false } catch (e: SecurityException) { false }
    private fun launchPackage(pkg: String): Boolean = ctx.packageManager.getLaunchIntentForPackage(pkg)?.let { launch(it) } ?: false
    private fun url(u: String) = launch(Intent(Intent.ACTION_VIEW, Uri.parse(u)))
    private fun hasContacts() = ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED

    fun execute(a: PhoneAction): PhoneResult = when (a) {
        PhoneAction.OpenYouTube -> if (launchPackage("com.google.android.youtube") || url("https://www.youtube.com")) PhoneResult.Done("Opening YouTube.") else PhoneResult.Failed("Could not open YouTube.")
        PhoneAction.OpenWhatsApp -> if (launchPackage("com.whatsapp") || launchPackage("com.whatsapp.w4b")) PhoneResult.Done("Opening WhatsApp.") else PhoneResult.Failed("WhatsApp is not installed on this phone.")
        PhoneAction.OpenChrome -> if (launchPackage("com.android.chrome") || url("https://www.google.com")) PhoneResult.Done("Opening Chrome.") else PhoneResult.Failed("Could not open a browser.")
        PhoneAction.OpenCamera -> if (launch(Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA))) PhoneResult.Done("Opening the camera.") else PhoneResult.Failed("No camera app found.")
        PhoneAction.OpenGallery -> if (launch(Intent(Intent.ACTION_VIEW, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)) || launch(Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI))) PhoneResult.Done("Opening your gallery.") else PhoneResult.Failed("No gallery app found.")
        PhoneAction.OpenVideos -> if (launch(Intent(Intent.ACTION_VIEW, MediaStore.Video.Media.EXTERNAL_CONTENT_URI)) || launch(Intent(Intent.ACTION_VIEW).setType("video/*"))) PhoneResult.Done("Opening your videos.") else PhoneResult.Failed("No video gallery found.")
        PhoneAction.OpenFiles -> if (launch(Intent(android.app.DownloadManager.ACTION_VIEW_DOWNLOADS)) || launch(Intent(Intent.ACTION_GET_CONTENT).setType("*/*").addCategory(Intent.CATEGORY_OPENABLE))) PhoneResult.Done("Opening your files.") else PhoneResult.Failed("No file manager found.")
        PhoneAction.PickContact -> PhoneResult.LaunchContactPicker
        is PhoneAction.OpenApp -> openByName(a.appName)
        is PhoneAction.WebSearch -> if (launch(Intent(Intent.ACTION_WEB_SEARCH).putExtra("query", a.query)) || url("https://www.google.com/search?q=" + URLEncoder.encode(a.query, "UTF-8"))) PhoneResult.Done("Searching for ${a.query}.") else PhoneResult.Failed("Could not start a search.")
        is PhoneAction.SetAlarm -> if (launch(Intent(AlarmClock.ACTION_SET_ALARM).putExtra(AlarmClock.EXTRA_HOUR, a.hour24).putExtra(AlarmClock.EXTRA_MINUTES, a.minute).putExtra(AlarmClock.EXTRA_MESSAGE, a.label ?: "BADSHAH AI").putExtra(AlarmClock.EXTRA_SKIP_UI, false)))
            PhoneResult.Done("Alarm set for %02d:%02d. Confirm it in the clock app.".format(a.hour24, a.minute)) else PhoneResult.Failed("No clock app found.")
        is PhoneAction.StartTimer -> if (launch(Intent(AlarmClock.ACTION_SET_TIMER).putExtra(AlarmClock.EXTRA_LENGTH, a.seconds).putExtra(AlarmClock.EXTRA_MESSAGE, a.label ?: "BADSHAH AI").putExtra(AlarmClock.EXTRA_SKIP_UI, false)))
            PhoneResult.Done("Timer started for ${a.seconds / 60} min ${a.seconds % 60} sec.") else PhoneResult.Failed("No clock app found.")
        is PhoneAction.Call -> withNumber(a.target) { num -> if (launch(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(num))))) PhoneResult.Done("Dialer opened for $num. Press call to connect.") else PhoneResult.Failed("No dialer found.") }
        is PhoneAction.Flashlight -> torch(a.on)
        is PhoneAction.Sms -> withNumber(a.target) { num -> if (launch(Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + Uri.encode(num))).putExtra("sms_body", a.message))) PhoneResult.Done("Message ready for $num. Press send in your messages app.") else PhoneResult.Failed("No SMS app found.") }
        is PhoneAction.WhatsAppMessage -> withNumber(a.target) { num ->
            val e164 = toE164(num) ?: return@withNumber PhoneResult.Failed("I need the number with country code (for example +92...) to open WhatsApp.")
            if (url("https://wa.me/${e164.removePrefix("+")}?text=" + URLEncoder.encode(a.message, "UTF-8"))) PhoneResult.Done("WhatsApp chat opened with your message ready. Press send.") else PhoneResult.Failed("Could not open WhatsApp.")
        }
    }

    /** Torch mode needs no permission and no camera app: CameraManager does it directly. */
    private fun torch(on: Boolean): PhoneResult {
        val cm = ctx.getSystemService(Context.CAMERA_SERVICE) as android.hardware.camera2.CameraManager
        val id = cm.cameraIdList.firstOrNull { cid ->
            runCatching { cm.getCameraCharacteristics(cid).get(android.hardware.camera2.CameraCharacteristics.FLASH_INFO_AVAILABLE) == true }.getOrDefault(false)
        } ?: return PhoneResult.Failed("This phone has no flashlight.")
        return try {
            cm.setTorchMode(id, on)
            PhoneResult.Done(if (on) "Flashlight on." else "Flashlight off.")
        } catch (e: Exception) {
            PhoneResult.Failed("Could not switch the flashlight.")
        }
    }

    private fun withNumber(target: String, f: (String) -> PhoneResult): PhoneResult {
        if (target.matches(Regex("^[+]?[0-9]{5,}$"))) return f(target)
        if (!hasContacts()) return PhoneResult.NeedPermission(Manifest.permission.READ_CONTACTS, "To find \"$target\" in your contacts I need permission to read contacts. Or say the phone number instead.")
        val num = findContactNumber(target) ?: return PhoneResult.Failed("I could not find \"$target\" in your contacts.")
        return f(num)
    }

    private fun findContactNumber(name: String): String? = ctx.contentResolver.query(
        ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
        arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER, ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME),
        "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?", arrayOf("%$name%"),
        "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} ASC LIMIT 1")?.use { c -> if (c.moveToFirst()) c.getString(0) else null }

    private fun toE164(raw: String): String? {
        val digits = raw.filter { it.isDigit() || it == '+' }
        if (digits.startsWith("+")) return digits
        val iso = (ctx.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager)?.simCountryIso?.uppercase().orEmpty()
        return if (iso.isNotEmpty()) PhoneNumberUtils.formatNumberToE164(digits, iso) else null
    }

    private fun openByName(name: String): PhoneResult {
        val pm = ctx.packageManager
        val apps = pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
            .map { it.loadLabel(pm).toString() to it.activityInfo.packageName }
        val q = name.lowercase()
        val hit = apps.firstOrNull { it.first.lowercase() == q } ?: apps.firstOrNull { it.first.lowercase().startsWith(q) } ?: apps.firstOrNull { it.first.lowercase().contains(q) }
        return if (hit != null && launchPackage(hit.second)) PhoneResult.Done("Opening ${hit.first}.") else PhoneResult.Failed("I could not find an app called \"$name\".")
    }
}
