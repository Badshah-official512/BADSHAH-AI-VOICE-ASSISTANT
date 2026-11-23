package dev.pages.badshah222ai.features.splash

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.pages.badshah222ai.R
import dev.pages.badshah222ai.ui.theme.AmbientLights
import dev.pages.badshah222ai.ui.theme.Gold
import dev.pages.badshah222ai.ui.theme.Mute
import dev.pages.badshah222ai.ui.theme.Violet
import kotlinx.coroutines.delay

/**
 * Premium splash — approved mock /tmp/mock-splash.jpg (user 👍 realistic crown + English).
 * Dark neon room (call-bg), glowing ring + hero half character (bottom faded into
 * the scene), realistic golden crown, big "BADSHAH AI", tagline, loading bar +
 * English "Loading...". The old plain white splash is gone.
 */
@Composable
fun SplashScreen(onReady: () -> Unit) {
    var started by remember { mutableStateOf(false) }
    val progress by animateFloatAsState(
        targetValue = if (started) 1f else 0f,
        animationSpec = tween(1800, easing = LinearEasing),
        label = "splash-progress",
    )
    LaunchedEffect(Unit) {
        started = true
        delay(1900)
        onReady()
    }

    Box(Modifier.fillMaxSize()) {
        Image(
            painter = painterResource(R.drawable.call_bg),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        AmbientLights(Modifier.fillMaxSize())
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxSize().padding(horizontal = 32.dp),
        ) {
            Spacer(Modifier.weight(0.10f))
            // Glowing ring + hero half character.
            Box(contentAlignment = Alignment.Center) {
                Canvas(Modifier.size(250.dp)) {
                    drawCircle(Violet.copy(alpha = 0.20f), radius = size.minDimension / 2f, style = Stroke(width = 26f))
                    drawCircle(Violet, radius = size.minDimension / 2f, style = Stroke(width = 5f))
                }
                Image(
                    painter = painterResource(R.drawable.char_half),
                    contentDescription = "Badshah",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(230.dp),
                )
                // Bottom fade of the character into the scene.
                Box(
                    Modifier.size(230.dp).background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Transparent, Color(0xFF0C0918).copy(alpha = 0.85f)),
                            startY = 300f,
                        ),
                    ),
                )
            }
            Spacer(Modifier.height(8.dp))
            // Realistic golden crown (luminance-keyed transparent PNG).
            Image(
                painter = painterResource(R.drawable.crown_gold),
                contentDescription = "Crown",
                modifier = Modifier.size(120.dp, 62.dp),
            )
            Text(
                "BADSHAH AI",
                color = Color.White,
                fontSize = 44.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 4.sp,
                textAlign = TextAlign.Center,
            )
            Text(
                "Your Personal AI Assistant",
                color = Mute,
                fontSize = 17.sp,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.weight(1f))
            LinearProgressIndicator(
                progress = { progress },
                color = Violet,
                trackColor = Color.White.copy(alpha = 0.25f),
                modifier = Modifier.fillMaxWidth().height(6.dp),
            )
            Spacer(Modifier.height(10.dp))
            Text("Loading…", color = Color.White, fontSize = 16.sp)
            Spacer(Modifier.height(48.dp))
        }
    }
}
