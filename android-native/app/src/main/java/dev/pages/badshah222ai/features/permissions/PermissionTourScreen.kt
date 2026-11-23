package dev.pages.badshah222ai.features.permissions

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.pages.badshah222ai.ui.theme.Mute

/**
 * Permission tour — 12 steps, auto-starts after login (v7.5 fix), English.
 * WIRED (Phase 2): each step requests its REAL runtime permission, or opens
 * the system Settings screen for the grant types Android reserves for the
 * user (overlay, accessibility, exact alarms, battery). Denying never blocks
 * the tour — the user can skip and grant later in Settings.
 * Camera is asked HERE and ONLY here — once, one tap (user decision ~14:00).
 * After that the front camera auto-starts when a call starts.
 * NOT asked (Play Protect safety, v7.7.2 set): SMS, call log, phone state, usage stats.
 */
private data class TourStep(
    val title: String,
    val note: String,
    val permissions: Array<String> = emptyArray(),
    val settingsAction: String? = null,
)

private fun steps(): List<TourStep> {
    val media = if (Build.VERSION.SDK_INT >= 33) {
        arrayOf(Manifest.permission.READ_MEDIA_IMAGES)
    } else {
        arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }
    val notifications = if (Build.VERSION.SDK_INT >= 33) {
        arrayOf(Manifest.permission.POST_NOTIFICATIONS)
    } else {
        emptyArray()
    }
    return listOf(
        TourStep("Microphone", "Voice calls need the mic — only during calls.", arrayOf(Manifest.permission.RECORD_AUDIO)),
        TourStep("Camera — asked only once", "One tap here, never again. Front camera auto-starts when a call starts.", arrayOf(Manifest.permission.CAMERA)),
        TourStep("Notifications", "Call and reply announcements.", notifications),
        TourStep("Contacts", "Call / WhatsApp by contact name.", arrayOf(Manifest.permission.READ_CONTACTS)),
        TourStep("Phone calls", "Let the AI place calls for you.", arrayOf(Manifest.permission.CALL_PHONE)),
        TourStep("Location", "Weather, prayer times and nearby help.", arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)),
        TourStep("Photos & media", "Pick a custom avatar and share images.", media),
        TourStep("Calendar", "Reminders and alarms.", arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR)),
        TourStep("Alarms", "Exact alarms for reminders.", settingsAction = Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM),
        TourStep("Draw over other apps", "Floating circle over any app (granted in Settings).", settingsAction = Settings.ACTION_MANAGE_OVERLAY_PERMISSION),
        TourStep("Accessibility", "Full phone control by voice (granted in Settings, you switch it on).", settingsAction = Settings.ACTION_ACCESSIBILITY_SETTINGS),
        TourStep("Battery optimization", "Keep calls alive in the background.", settingsAction = Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS),
    )
}

@Composable
fun PermissionTourScreen(onDone: () -> Unit) {
    val context = LocalContext.current
    val stepList = remember { steps() }
    var index by remember { mutableStateOf(0) }
    val step = stepList[index]

    fun advance() {
        if (index < stepList.lastIndex) index++ else onDone()
    }

    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { advance() }

    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center) {
        Text("Step ${index + 1} of ${stepList.size}")
        Spacer(Modifier.height(16.dp))
        Text(step.title)
        Text(step.note, color = Mute)
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = {
                when {
                    step.permissions.isNotEmpty() -> permLauncher.launch(step.permissions)
                    step.settingsAction != null -> {
                        val intent = Intent(step.settingsAction).apply {
                            if (step.settingsAction == Settings.ACTION_MANAGE_OVERLAY_PERMISSION ||
                                step.settingsAction == Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM ||
                                step.settingsAction == Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
                            ) {
                                data = Uri.parse("package:${context.packageName}")
                            }
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        runCatching { context.startActivity(intent) }
                        advance()
                    }
                    else -> advance()
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text(if (index < stepList.lastIndex) "Allow & continue" else "Finish") }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = { advance() }, modifier = Modifier.fillMaxWidth()) {
            Text("Skip for now")
        }
        Spacer(Modifier.height(16.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items(stepList) { s -> Text("• ${s.title}", color = Mute) }
        }
    }
}
