package dev.pages.badshah222ai.ui

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import dev.pages.badshah222ai.features.about.AboutScreen
import dev.pages.badshah222ai.features.auth.AuthScreen
import dev.pages.badshah222ai.features.auth.SessionViewModel
import dev.pages.badshah222ai.features.chat.ChatHistoryScreen
import dev.pages.badshah222ai.features.chat.ChatScreen
import dev.pages.badshah222ai.features.chat.HistoryViewModel
import dev.pages.badshah222ai.features.help.HelpScreen
import dev.pages.badshah222ai.features.help.HelpViewModel
import dev.pages.badshah222ai.features.home.HomeScreen
import dev.pages.badshah222ai.features.home.HomeViewModel
import dev.pages.badshah222ai.features.home.QuickActionsScreen
import dev.pages.badshah222ai.features.home.VoiceScreen
import dev.pages.badshah222ai.features.permissions.PermissionsScreen
import dev.pages.badshah222ai.features.settings.SettingsScreen
import dev.pages.badshah222ai.features.settings.SettingsViewModel
import dev.pages.badshah222ai.ui.character.CharState
import dev.pages.badshah222ai.ui.orb.VoiceOrb
import dev.pages.badshah222ai.ui.theme.*

@Composable
fun AppNav(activity: Activity) {
    val session: SessionViewModel = viewModel()
    val user by session.user.collectAsState(); val blocked by session.blocked.collectAsState()
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        when {
            user == null -> AuthScreen(session, activity)
            blocked -> Column(Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Account blocked", style = MaterialTheme.typography.headlineSmall, color = Bad)
                Text("This account has been blocked by the BADSHAH AI team. If you think this is a mistake, contact support through the WhatsApp link on the About page.", textAlign = TextAlign.Center, color = Mute, modifier = Modifier.padding(16.dp))
                Button({ session.signOut() }) { Text("Sign out") }
            }
            else -> SignedInNav(session)
        }
    }
}

private val TABS = setOf("home", "chat", "history", "settings")

@Composable
private fun SignedInNav(session: SessionViewModel) {
    val nav = rememberNavController()
    // ViewModels are created here (nav-graph scope) so Home state survives opening Settings/Help.
    val home: HomeViewModel = viewModel(); val settings: SettingsViewModel = viewModel(); val help: HelpViewModel = viewModel(); val history: HistoryViewModel = viewModel()
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route ?: "home"
    fun tab(r: String) { nav.navigate(r) { popUpTo("home") { saveState = true }; launchSingleTop = true; restoreState = true } }

    Scaffold(
        bottomBar = { if (route in TABS) LiaBottomBar(route, { tab(it) }, { nav.navigate("voice") }) },
        containerColor = Ink,
    ) { pad ->
        Box(Modifier.padding(pad)) {
            NavHost(nav, startDestination = "home") {
                composable("home") { HomeScreen(session, home) { nav.navigate(it) } }
                composable("chat") { ChatScreen(home, { nav.popBackStack() }, { nav.navigate("voice") }) }
                composable("voice") { VoiceScreen(home) { nav.popBackStack() } }
                composable("quick") { QuickActionsScreen(home, { nav.popBackStack() }, { nav.navigate(it) }) }
                composable("settings") { SettingsScreen(session, settings, { nav.popBackStack() }, { nav.navigate("permissions") }) }
                composable("help") { HelpScreen(help) { nav.popBackStack() } }
                composable("about") { AboutScreen { nav.popBackStack() } }
                composable("history") { ChatHistoryScreen(home, history) { nav.popBackStack() } }
                composable("permissions") { PermissionsScreen { nav.popBackStack() } }
            }
        }
    }
}

/** Floating pill bottom bar with the voice orb in the centre, modelled on the reference app. */
@Composable
private fun LiaBottomBar(current: String, onTab: (String) -> Unit, onVoice: () -> Unit) {
    Box(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp)) {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(Panel2).padding(horizontal = 8.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TabItem(Modifier.weight(1f), Icons.Default.Home, "Home", current == "home") { onTab("home") }
            TabItem(Modifier.weight(1f), Icons.Default.Chat, "Chat", current == "chat") { onTab("chat") }
            Spacer(Modifier.width(62.dp))
            TabItem(Modifier.weight(1f), Icons.Default.History, "History", current == "history") { onTab("history") }
            TabItem(Modifier.weight(1f), Icons.Default.Settings, "Settings", current == "settings") { onTab("settings") }
        }
        VoiceOrb(
            CharState.Idle, 0f, false,
            Modifier.align(Alignment.TopCenter).offset(y = (-16).dp).size(58.dp).clip(CircleShape).background(Ink).clickable { onVoice() },
        )
    }
}

@Composable
private fun TabItem(modifier: Modifier, icon: ImageVector, label: String, selected: Boolean, onClick: () -> Unit) {
    Column(
        modifier.clip(RoundedCornerShape(16.dp)).clickable { onClick() }.padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, label, tint = if (selected) VioletSoft else Mute, modifier = Modifier.size(22.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = if (selected) VioletSoft else Mute, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
    }
}
