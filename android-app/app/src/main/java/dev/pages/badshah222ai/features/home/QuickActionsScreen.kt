package dev.pages.badshah222ai.features.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.pages.badshah222ai.features.settings.TopBar
import dev.pages.badshah222ai.ui.theme.*

private data class QuickAction(val icon: ImageVector, val title: String, val example: String, val command: String?)

private val ACTIONS = listOf(
    QuickAction(Icons.Default.Apps, "Open an app", "\"YouTube kholo\"", "Open YouTube"),
    QuickAction(Icons.Default.Call, "Call someone", "\"Ali ko call karo\"", "Pick a contact"),
    QuickAction(Icons.Default.Message, "Send a message", "\"Papa ko WhatsApp karo\"", "Pick a contact"),
    QuickAction(Icons.Default.PhotoLibrary, "Open gallery", "\"Gallery kholo\"", "Open gallery"),
    QuickAction(Icons.Default.VideoLibrary, "Open videos", "\"Meri videos kholo\"", "Open my videos"),
    QuickAction(Icons.Default.Folder, "Open files", "\"Files kholo\"", "Open files"),
    QuickAction(Icons.Default.FlashlightOn, "Flashlight", "\"Torch chalao\"", "Turn on the flashlight"),
    QuickAction(Icons.Default.Timer, "Set a timer", "\"5 minute ka timer\"", "Set a timer for 5 minutes"),
    QuickAction(Icons.Default.Alarm, "Set an alarm", "\"Subah 7 baje ka alarm\"", "Set an alarm for 7 am"),
    QuickAction(Icons.Default.Image, "Create image", "\"Ek golden crown logo banao\"", "Ek golden crown ka premium logo banao, image banao"),
    QuickAction(Icons.Default.Mic, "Talk to BADSHAH", "Start a voice conversation", null),
)

/** Phone-control quick actions, modelled on the reference app's Quick actions page. */
@Composable
fun QuickActionsScreen(vm: HomeViewModel, onBack: () -> Unit, onNav: (String) -> Unit) {
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        TopBar("Quick actions", onBack)
        Text(
            "Tap one to start. Say it the way you'd say it to a friend — English, Urdu or both.",
            Modifier.padding(horizontal = 16.dp, vertical = 4.dp), color = Mute, style = MaterialTheme.typography.bodyMedium,
        )
        LazyVerticalGrid(GridCells.Fixed(2), Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(ACTIONS) { a ->
                Card(
                    Modifier.fillMaxWidth().clickable {
                        if (a.command == null) onNav("voice") else { vm.send(a.command); onNav("chat") }
                    },
                    colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(18.dp),
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        BoxIcon(a.icon)
                        Text(a.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(a.example, color = Mute, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        Text(
            "These are examples — you can always say your own words in Chat or on the voice screen.",
            Modifier.padding(horizontal = 16.dp), color = Mute, style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun BoxIcon(icon: ImageVector) {
    Card(colors = CardDefaults.cardColors(containerColor = VioletDeep), shape = RoundedCornerShape(12.dp)) {
        Icon(icon, null, Modifier.padding(10.dp), tint = androidx.compose.ui.graphics.Color.White)
    }
}
