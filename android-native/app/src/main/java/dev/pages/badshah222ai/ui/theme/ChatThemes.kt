package dev.pages.badshah222ai.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

/**
 * Chat themes — user decision 2026-11-23 ~14:10 (THEMES.md).
 * All 5 ship; the user switches in Settings; change applies instantly, no restart.
 * Call screen theme is FIXED to Theme 1 Purple Neon (user selection ~14:12) —
 * the other 4 themes never apply to the call screen.
 */
data class ChatTheme(
    val id: String,
    val name: String,
    val bgTop: Color,
    val bgBottom: Color,
    val accent: Color,
    val accent2: Color,
    val surface: Color,
    val text: Color,
    val subText: Color,
)

val ChatThemes = listOf(
    ChatTheme("purple", "Purple Neon", Color(0xFF1A0C3A), Color(0xFF0A061C), Color(0xFF966EFF), Color(0xFF40F0D2), Color(0xFF261C4A), Color(0xFFF0EEFF), Color(0xFFB2A8DC)),
    ChatTheme("gold", "Royal Gold", Color(0xFF181308), Color(0xFF080602), Color(0xFFE8B84B), Color(0xFFFFD678), Color(0xFF2B2310), Color(0xFFFFF6E0), Color(0xFFCEBC94)),
    ChatTheme("ocean", "Ocean Blue", Color(0xFF061A30), Color(0xFF030A18), Color(0xFF38BDF8), Color(0xFF2DD4BF), Color(0xFF0D2640), Color(0xFFEBF6FF), Color(0xFFA0C4E2)),
    ChatTheme("emerald", "Emerald", Color(0xFF062218), Color(0xFF03100C), Color(0xFF34D399), Color(0xFFE8B84B), Color(0xFF0C2E24), Color(0xFFECFDF5), Color(0xFFA4D6C4)),
    ChatTheme("crimson", "Crimson", Color(0xFF280A10), Color(0xFF100408), Color(0xFFF85060), Color(0xFFFFAA5A), Color(0xFF3A121A), Color(0xFFFFF0F0), Color(0xFFDEA8AC)),
)

/** Default = Theme 1 Purple Neon (the brand identity). */
val DefaultChatTheme: ChatTheme = ChatThemes[0]

val LocalChatTheme = compositionLocalOf { DefaultChatTheme }

/** Material scheme derived from the selected chat theme (user bubbles = accent, AI bubbles = surface). */
@Composable
fun ChatThemeColors(theme: ChatTheme) = darkColorScheme(
    primary = theme.accent,
    onPrimary = Color(0xFF14092E),
    secondary = theme.accent2,
    background = theme.bgBottom,
    onBackground = theme.text,
    surface = theme.surface,
    onSurface = theme.text,
    surfaceVariant = theme.surface,
    onSurfaceVariant = theme.subText,
    error = Color(0xFFFF5D6C),
    outline = theme.accent.copy(alpha = 0.45f),
)

/** Holds the user-picked theme in memory; Phase 2 persists it in SecureStore. */
class ChatThemeState(initial: ChatTheme = DefaultChatTheme) {
    var theme by mutableStateOf(initial)
        private set

    fun select(theme: ChatTheme) {
        this.theme = theme
    }
}

@Composable
fun rememberChatThemeState(): ChatThemeState = remember { ChatThemeState() }

@Composable
fun BadshahChatTheme(state: ChatThemeState, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalChatTheme provides state.theme) {
        androidx.compose.material3.MaterialTheme(
            colorScheme = ChatThemeColors(state.theme),
            content = content,
        )
    }
}
