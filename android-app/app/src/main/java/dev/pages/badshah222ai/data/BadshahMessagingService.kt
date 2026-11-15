package dev.pages.badshah222ai.data

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import dev.pages.badshah222ai.R

const val UPDATES_CHANNEL = "badshah_updates"

fun ensureNotificationChannel(nm: NotificationManager) {
    nm.createNotificationChannel(NotificationChannel(UPDATES_CHANNEL, "Support replies & announcements", NotificationManager.IMPORTANCE_DEFAULT))
}

/** Shows push messages (support replies, announcements) while the app is in the foreground. */
class BadshahMessagingService : FirebaseMessagingService() {
    override fun onMessageReceived(message: RemoteMessage) {
        val n = message.notification ?: return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        ensureNotificationChannel(getSystemService(NotificationManager::class.java))
        val notif = NotificationCompat.Builder(this, UPDATES_CHANNEL).setSmallIcon(R.drawable.ic_stat_badshah)
            .setContentTitle(n.title ?: getString(R.string.app_name)).setContentText(n.body).setAutoCancel(true).build()
        NotificationManagerCompat.from(this).notify(System.currentTimeMillis().toInt(), notif)
    }
}
