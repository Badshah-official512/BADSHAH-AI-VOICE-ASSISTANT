package dev.pages.badshah222ai

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import dev.pages.badshah222ai.core.CallSession
import dev.pages.badshah222ai.features.admin.AdminScreen
import dev.pages.badshah222ai.features.auth.AuthScreen
import dev.pages.badshah222ai.features.call.CallScreen
import dev.pages.badshah222ai.features.chat.ChatScreen
import dev.pages.badshah222ai.features.help.HelpScreen
import dev.pages.badshah222ai.features.home.HomeScreen
import dev.pages.badshah222ai.features.memory.MemoryScreen
import dev.pages.badshah222ai.features.permissions.PermissionTourScreen
import dev.pages.badshah222ai.features.settings.SettingsScreen
import dev.pages.badshah222ai.features.splash.SplashScreen
import dev.pages.badshah222ai.features.tools.ToolsScreen
import dev.pages.badshah222ai.ui.components.FloatingCircle
import dev.pages.badshah222ai.ui.components.OrbState
import dev.pages.badshah222ai.ui.navigation.Routes
import dev.pages.badshah222ai.ui.theme.BadshahChatTheme
import dev.pages.badshah222ai.ui.theme.BadshahTheme

/**
 * BADSHAH AI — FULL NATIVE. Single Activity, Navigation-Compose, zero WebView.
 * The old WebView shell (android-app/MainActivity) stays untouched until this
 * app reaches parity and the user approves the switch.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as BadshahApp).container
        setContent {
            BadshahTheme {
                BadshahChatTheme(container.chatTheme) {
                    BadshahNav(container)
                }
            }
        }
    }
}

@Composable
private fun BadshahNav(container: Container) {
    val nav = rememberNavController()
    val context = androidx.compose.ui.platform.LocalContext.current
    val showCircle by CallSession.showFloatingCircle.collectAsState()
    val openCall by CallSession.openCallRequest.collectAsState()
    androidx.compose.runtime.LaunchedEffect(openCall) {
        if (openCall) {
            CallSession.clearOpenCallRequest()
            CallSession.liveCall.restoreFromMinimized()
            CallSession.onCallRestored()
            dev.pages.badshah222ai.core.FloatingOrbService.stop(context)
            nav.navigate(Routes.CALL)
        }
    }
    Scaffold { innerPadding ->
        Box(Modifier.fillMaxSize().padding(innerPadding)) {
            NavHost(navController = nav, startDestination = Routes.SPLASH) {
                composable(Routes.SPLASH) {
                    SplashScreen(
                        onReady = {
                            // Routing: signed-out (and not guest) -> Auth; signed-in
                            // but tour not done -> Tour (auto-start, the v7.5 fix);
                            // everyone else -> Home.
                            val dest = when {
                                !container.auth.isSignedIn && !container.secureStore.guestMode -> Routes.AUTH
                                container.auth.isSignedIn && !container.secureStore.tourDone -> Routes.TOUR
                                else -> Routes.HOME
                            }
                            nav.navigate(dest) {
                                popUpTo(Routes.SPLASH) { inclusive = true }
                            }
                        },
                    )
                }
                composable(Routes.TOUR) {
                    PermissionTourScreen(onDone = {
                        container.secureStore.tourDone = true
                        nav.navigate(Routes.HOME) { popUpTo(Routes.TOUR) { inclusive = true } }
                    })
                }
                composable(Routes.AUTH) {
                    AuthScreen(
                        auth = container.auth,
                        store = container.secureStore,
                        onSignedIn = {
                            val dest = if (container.auth.isSignedIn && !container.secureStore.tourDone) {
                                Routes.TOUR
                            } else {
                                Routes.HOME
                            }
                            nav.navigate(dest) { popUpTo(Routes.AUTH) { inclusive = true } }
                        },
                    )
                }
                composable(Routes.HOME) {
                    HomeScreen(
                        onChat = { nav.navigate(Routes.CHAT) },
                        onCall = { nav.navigate(Routes.CALL) },
                        onTools = { nav.navigate(Routes.TOOLS) },
                        onMemory = { nav.navigate(Routes.MEMORY) },
                        onSettings = { nav.navigate(Routes.SETTINGS) },
                    )
                }
                composable(Routes.CHAT) { ChatScreen(api = container.api, phoneExecutor = container.phoneExecutor, store = container.secureStore, onBack = { nav.popBackStack() }) }
                composable(Routes.CALL) {
                    CallScreen(
                        call = CallSession.liveCall,
                        onMinimized = {
                            CallSession.liveCall.minimize()
                            CallSession.onCallMinimized()
                            dev.pages.badshah222ai.core.FloatingOrbService.start(context)
                            nav.navigate(Routes.CHAT) { popUpTo(Routes.HOME) }
                        },
                        onEnded = {
                            CallSession.liveCall.endCall()
                            CallSession.onCallEnded()
                            dev.pages.badshah222ai.core.FloatingOrbService.stop(context)
                            nav.navigate(Routes.HOME) { popUpTo(Routes.HOME) { inclusive = true } }
                        },
                    )
                }
                composable(Routes.TOOLS) { ToolsScreen(api = container.api, onScreenGuide = { nav.navigate(Routes.SCREEN) }) }
                composable(Routes.SCREEN) {
                    dev.pages.badshah222ai.features.screen.ScreenShareScreen(
                        api = container.api,
                        store = container.secureStore,
                        onBack = { nav.popBackStack() },
                    )
                }
                composable(Routes.MEMORY) { MemoryScreen(api = container.api) }
                composable(Routes.SETTINGS) {
                    SettingsScreen(container = container, onBack = { nav.popBackStack() })
                }
                composable(Routes.ADMIN) { AdminScreen() }
                composable(Routes.HELP) { HelpScreen() }
            }
            // Floating circle: visible while a call is minimized (2e). Tap = back to Call.
            if (showCircle) {
                FloatingCircle(
                    state = OrbState.SPEAKING,
                    size = 84.dp,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(16.dp)
                        .clickable {
                            CallSession.liveCall.restoreFromMinimized()
                            CallSession.onCallRestored()
                            dev.pages.badshah222ai.core.FloatingOrbService.stop(context)
                            nav.navigate(Routes.CALL)
                        },
                )
            }
        }
    }
}
