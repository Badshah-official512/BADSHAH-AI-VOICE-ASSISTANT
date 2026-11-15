package dev.pages.badshah222ai.ui

import android.app.Activity
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import dev.pages.badshah222ai.features.about.AboutScreen
import dev.pages.badshah222ai.features.auth.AuthScreen
import dev.pages.badshah222ai.features.auth.SessionViewModel
import dev.pages.badshah222ai.features.chat.ChatHistoryScreen
import dev.pages.badshah222ai.features.chat.HistoryViewModel
import dev.pages.badshah222ai.features.help.HelpScreen
import dev.pages.badshah222ai.features.help.HelpViewModel
import dev.pages.badshah222ai.features.home.HomeScreen
import dev.pages.badshah222ai.features.home.HomeViewModel
import dev.pages.badshah222ai.features.permissions.PermissionsScreen
import dev.pages.badshah222ai.features.settings.SettingsScreen
import dev.pages.badshah222ai.features.settings.SettingsViewModel
import dev.pages.badshah222ai.ui.theme.Bad
import dev.pages.badshah222ai.ui.theme.Mute

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

@Composable
private fun SignedInNav(session: SessionViewModel) {
    val nav = rememberNavController()
    // ViewModels are created here (nav-graph scope) so Home state survives opening Settings/Help.
    val home: HomeViewModel = viewModel(); val settings: SettingsViewModel = viewModel(); val help: HelpViewModel = viewModel(); val history: HistoryViewModel = viewModel()
    NavHost(nav, startDestination = "home") {
        composable("home") { HomeScreen(session, home) { nav.navigate(it) } }
        composable("settings") { SettingsScreen(session, settings, { nav.popBackStack() }, { nav.navigate("permissions") }) }
        composable("help") { HelpScreen(help) { nav.popBackStack() } }
        composable("about") { AboutScreen { nav.popBackStack() } }
        composable("history") { ChatHistoryScreen(home, history) { nav.popBackStack() } }
        composable("permissions") { PermissionsScreen { nav.popBackStack() } }
    }
}
