package dev.pages.badshah222ai.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.pages.badshah222ai.R
import dev.pages.badshah222ai.ui.theme.CyanAcc
import dev.pages.badshah222ai.ui.theme.Gold
import dev.pages.badshah222ai.ui.theme.Ok
import dev.pages.badshah222ai.ui.theme.Violet

/**
 * Floating avatar circle — FOR THE FLOATING AVATAR ONLY, never the call screen
 * (user correction 2026-11-23 ~13:57: "Nahi call screen par nahi").
 *
 * Design (approved mock /tmp/mock-circle.jpg, source screenshot 279335_3_eow1.png):
 * thick gold arch behind, 3 orbiting rings (gold / teal / purple) with glowing
 * light dots, high-class neon bloom (not flat lines). Inside: the poster CENTRE
 * cut (face + shoulders, no text/border) — poster is NEVER the whole avatar.
 * Rings rotate, dots pulse. State changes ring speed / dot brightness.
 */
enum class OrbState { IDLE, LISTENING, SPEAKING, THINKING }

@Composable
fun FloatingCircle(
    state: OrbState = OrbState.IDLE,
    size: Dp = 220.dp,
    modifier: Modifier = Modifier,
) {
    val speed = when (state) {
        OrbState.IDLE -> 1f
        OrbState.LISTENING -> 0.6f
        OrbState.SPEAKING -> 1.8f
        OrbState.THINKING -> 1.3f
    }
    val transition = rememberInfiniteTransition(label = "orb")
    val rotGold by transition.animateFloat(0f, 360f, infiniteRepeatable(tween((14000 / speed).toInt(), easing = LinearEasing), RepeatMode.Restart), label = "rotGold")
    val rotTeal by transition.animateFloat(360f, 0f, infiniteRepeatable(tween((20000 / speed).toInt(), easing = LinearEasing), RepeatMode.Restart), label = "rotTeal")
    val rotPurple by transition.animateFloat(0f, 360f, infiniteRepeatable(tween((26000 / speed).toInt(), easing = LinearEasing), RepeatMode.Restart), label = "rotPurple")
    val pulse by transition.animateFloat(0.55f, 1f, infiniteRepeatable(tween(1600, easing = LinearEasing), RepeatMode.Reverse), label = "pulse")

    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val c = this.size.minDimension / 2f
            val center = Offset(c, c)
            // --- Thick gold arch behind (top half), neon bloom: wide faint + narrow bright.
            val archBrush = Brush.sweepGradient(listOf(Gold, Color(0xFFFFDF8A), Gold, Color(0xFFB8860B)))
            drawArc(brush = archBrush, startAngle = 180f, sweepAngle = 180f, useCenter = false, topLeft = Offset(c * 0.18f, c * 0.18f), size = Size(c * 1.64f, c * 1.64f), style = Stroke(width = c * 0.16f), alpha = 0.18f * pulse)
            drawArc(brush = archBrush, startAngle = 180f, sweepAngle = 180f, useCenter = false, topLeft = Offset(c * 0.18f, c * 0.18f), size = Size(c * 1.64f, c * 1.64f), style = Stroke(width = c * 0.07f))
            // --- Orbit rings: tilted ellipses with glowing dots on them.
            drawOrbitRing(center, c * 1.55f, c * 0.62f, rotGold, -25f, Gold, pulse)
            drawOrbitRing(center, c * 1.62f, c * 0.66f, rotTeal, 20f, CyanAcc, pulse)
            drawOrbitRing(center, c * 1.70f, c * 0.70f, rotPurple, 55f, Violet, pulse)
        }
        // --- Poster centre cut inside the circle (tight crop: no text/mic/crown/watch).
        Image(
            painter = painterResource(R.drawable.poster_cut_avatar),
            contentDescription = "BADSHAH AI",
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(size * 0.58f).clip(CircleShape),
        )
        // --- Small live status dot.
        Canvas(Modifier.fillMaxSize()) {
            val c = this.size.minDimension / 2f
            val dot = Offset(c + c * 0.62f, c + c * 0.62f)
            drawCircle(Ok.copy(alpha = 0.25f * pulse), radius = c * 0.10f, center = dot)
            drawCircle(Ok, radius = c * 0.05f, center = dot)
        }
    }
}

/** One tilted elliptical orbit ring + its glowing dots. Angles are in degrees. */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawOrbitRing(
    center: Offset,
    rx: Float,
    ry: Float,
    rotation: Float,
    tilt: Float,
    color: Color,
    pulse: Float,
) {
    val w = size.minDimension
    rotate(tilt, center) {
        // Glow pass (wide, faint) + core pass (narrow, bright) = neon bloom, not flat lines.
        drawOval(color.copy(alpha = 0.16f * pulse), topLeft = Offset(center.x - rx, center.y - ry), size = Size(rx * 2, ry * 2), style = Stroke(width = w * 0.018f))
        drawOval(color.copy(alpha = 0.85f), topLeft = Offset(center.x - rx, center.y - ry), size = Size(rx * 2, ry * 2), style = Stroke(width = w * 0.006f))
        // Glowing dots riding the ring.
        for (angleDeg in listOf(rotation, rotation + 140f, rotation + 250f)) {
            val a = Math.toRadians(angleDeg.toDouble())
            val p = Offset(center.x + (rx * kotlin.math.cos(a)).toFloat(), center.y + (ry * kotlin.math.sin(a)).toFloat())
            drawCircle(color.copy(alpha = 0.30f * pulse), radius = w * 0.022f, center = p)
            drawCircle(Color.White, radius = w * 0.008f, center = p)
        }
    }
}
