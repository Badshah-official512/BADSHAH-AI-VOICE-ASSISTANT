package dev.pages.badshah222ai.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import dev.pages.badshah222ai.R

/**
 * Call stage — approved mock /tmp/mock-call-fit.jpg (user ~13:56: "Call screen ya wala sahi ha").
 *
 * Hero full-body character (char-full.webp) stands INSIDE the platform circle,
 * feet on the centre/inner rings. Fractions, not fixed dp, so feet stay on the
 * platform on every phone size (the v7.7.2 bug: on his 720x1600 phone the
 * character was shifted right with feet floating in the air).
 * Layout: top spacer 16% + character 62% of stage height + bottom spacer 22%
 * => feet land at ~78% of stage height, on the platform rings of call-bg.jpg.
 * The floating-circle design is NEVER used here (user correction ~13:57).
 */
@Composable
fun HeroCallStage(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize()) {
        Image(
            painter = painterResource(R.drawable.call_bg),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxSize(),
        ) {
            Spacer(Modifier.fillMaxHeight(0.16f))
            // Height drives the size; width follows the image aspect ratio (442x895).
            // Never fix both dimensions: a fixed width would letterbox the cutout
            // and lift the feet off the platform (the v7.7.2 phone bug).
            Image(
                painter = painterResource(R.drawable.char_full),
                contentDescription = "Badshah",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxHeight(0.62f)
                    .aspectRatio(442f / 895f),
            )
            Spacer(Modifier.fillMaxHeight(0.22f))
        }
    }
}
