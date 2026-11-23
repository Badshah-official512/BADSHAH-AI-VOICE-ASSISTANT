package dev.pages.badshah222ai.features.screen

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.media.projection.MediaProjectionManager
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.pages.badshah222ai.core.ScreenShareService
import dev.pages.badshah222ai.core.SecureStore
import dev.pages.badshah222ai.data.api.ApiResult
import dev.pages.badshah222ai.data.api.BadshahApi
import dev.pages.badshah222ai.ui.theme.Mute
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Screen rehnumai (feature #2): the user shares the screen (system consent
 * dialog, always), BADSHAH AI sees the current frame and guides step by step.
 * Frames are answered with the user's BYOK Gemini vision — nothing is
 * captured without consent and capture stops instantly on Stop.
 */
@Composable
fun ScreenShareScreen(api: BadshahApi, store: SecureStore, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val running by ScreenShareService.running.collectAsState()
    val frame by ScreenShareService.frame.collectAsState()
    var question by remember { mutableStateOf("") }
    var answer by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }

    val consent = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            ScreenShareService.start(context, result.resultCode, result.data!!)
        }
    }

    Column(
        Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Screen Guide")
        Text(
            "Share your screen and ask — BADSHAH AI will see the screen and guide you step by step. " +
                "Android asks your permission first, every time.",
            color = Mute,
        )
        if (!running) {
            Button(
                onClick = {
                    val mpm = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                    consent.launch(mpm.createScreenCaptureIntent())
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Share my screen") }
        } else {
            frame?.let { bmp ->
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = "Shared screen",
                    modifier = Modifier.fillMaxWidth().height(220.dp),
                )
            }
            OutlinedTextField(
                value = question,
                onValueChange = { question = it },
                label = { Text("Ask about this screen") },
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                enabled = !busy && frame != null,
                onClick = {
                    val key = store.geminiKey()
                    if (key == null) {
                        answer = "Pehle Settings mein apni Gemini API key add karo (free)."
                        return@Button
                    }
                    val bmp = frame ?: return@Button
                    val q = question.ifBlank {
                        "Is screen par kya hai? Mujhe step by step batao ke agay kya karna hai."
                    }
                    busy = true
                    scope.launch {
                        val b64 = withContext(Dispatchers.IO) { bmp.toJpegBase64() }
                        answer = when (val r = api.visionAnswer(key, b64, q)) {
                            is ApiResult.Ok -> r.value.take(4000)
                            is ApiResult.Err -> "Error: ${r.message}"
                        }
                        busy = false
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Ask") }
            if (answer.isNotEmpty()) Text(answer)
            OutlinedButton(
                onClick = { ScreenShareService.stop(context) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Stop sharing") }
        }
        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Back") }
    }
}

private fun Bitmap.toJpegBase64(): String {
    val scale = minOf(1f, 1024f / maxOf(width, height))
    val small = if (scale < 1f) {
        Bitmap.createScaledBitmap(this, (width * scale).toInt(), (height * scale).toInt(), true)
    } else {
        this
    }
    val baos = ByteArrayOutputStream()
    small.compress(Bitmap.CompressFormat.JPEG, 75, baos)
    return Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
}
