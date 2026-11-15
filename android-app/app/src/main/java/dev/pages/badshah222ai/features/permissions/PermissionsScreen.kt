package dev.pages.badshah222ai.features.permissions

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import dev.pages.badshah222ai.features.settings.TopBar
import dev.pages.badshah222ai.ui.theme.*

private data class Perm(val permission: String, val title: String, val why: String, val minSdk: Int = 1)
private val PERMS = listOf(
    Perm(Manifest.permission.RECORD_AUDIO, "Microphone", "Lets you talk to BADSHAH AI. Audio is processed by Android speech recognition; the app does not record or store it."),
    Perm(Manifest.permission.READ_CONTACTS, "Contacts", "Used only to find a number when you say \"call Ali\". Contacts are never uploaded."),
    Perm(Manifest.permission.POST_NOTIFICATIONS, "Notifications", "Tells you when support replies or announcements arrive.", Build.VERSION_CODES.TIRAMISU),
)

@Composable
fun PermissionsScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { tick++ }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) { val o = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) tick++ }; owner.lifecycle.addObserver(o); onDispose { owner.lifecycle.removeObserver(o) } }
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        TopBar("Permissions", onBack)
        Text("BADSHAH AI asks only for what a feature needs. Calls and messages are never sent automatically: the dialer or message app opens pre-filled and you press the final button.", Modifier.padding(16.dp), color = Mute)
        LazyColumn(Modifier.padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(PERMS.filter { Build.VERSION.SDK_INT >= it.minSdk }) { p ->
                val granted = tick >= 0 && ContextCompat.checkSelfPermission(ctx, p.permission) == PackageManager.PERMISSION_GRANTED
                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Panel)) { Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) { Text(p.title, style = MaterialTheme.typography.titleMedium); Text(p.why, style = MaterialTheme.typography.bodySmall, color = Mute) }
                    if (granted) Text("Allowed", color = Ok) else Button({ launcher.launch(p.permission) }) { Text("Allow") }
                } }
            }
            item { OutlinedButton({ ctx.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}"))) }) { Text("Open Android app settings") } }
        }
    }
}
