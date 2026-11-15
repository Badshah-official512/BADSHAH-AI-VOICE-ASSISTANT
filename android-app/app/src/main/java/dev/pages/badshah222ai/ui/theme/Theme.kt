package dev.pages.badshah222ai.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Gold = Color(0xFFF5B942)
val GoldLight = Color(0xFFFFDF8A)
val Ink = Color(0xFF0B0D14)
val Panel = Color(0xFF141824)
val Panel2 = Color(0xFF1A2030)
val Mute = Color(0xFF8B94B3)
val Ok = Color(0xFF35D49A)
val Bad = Color(0xFFFF5D6C)
val Blue = Color(0xFF5AA2FF)

private val dark = darkColorScheme(
    primary = Gold, onPrimary = Color(0xFF1B1304), secondary = Blue, background = Ink, onBackground = Color(0xFFE8EBF5),
    surface = Panel, onSurface = Color(0xFFE8EBF5), surfaceVariant = Panel2, onSurfaceVariant = Mute, error = Bad, outline = Color(0xFF262E45),
)

/** BADSHAH AI is a dark, gold-accent brand in both system modes. */
@Composable
fun BadshahTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = dark, content = content)
}
