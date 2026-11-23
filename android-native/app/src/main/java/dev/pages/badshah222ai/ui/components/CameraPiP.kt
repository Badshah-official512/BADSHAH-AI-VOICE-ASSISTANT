package dev.pages.badshah222ai.ui.components

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Call camera preview (PiP) — CameraX.
 * The camera permission is asked ONCE in the permission tour; after that the
 * FRONT camera auto-starts when a call starts (user decision 2026-11-23
 * ~14:00). [front] switches front/back. If the permission is missing the
 * preview simply stays hidden — the call itself is audio-first and never
 * blocked by the camera.
 */
@Composable
fun CameraPiP(front: Boolean, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val granted = remember {
        ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
    }
    if (!granted) return

    val previewView = remember { PreviewView(context) }
    val providerRef = remember { mutableStateOf<ProcessCameraProvider?>(null) }
    LaunchedEffect(front) {
        val provider = awaitCameraProvider(context)
        providerRef.value = provider
        val preview = Preview.Builder().build().also {
            it.surfaceProvider = previewView.surfaceProvider
        }
        val selector = if (front) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
        try {
            provider.unbindAll()
            provider.bindToLifecycle(lifecycleOwner, selector, preview)
        } catch (e: Exception) {
            // No such camera / busy — PiP stays blank, call continues.
        }
    }
    DisposableEffect(Unit) {
        onDispose { providerRef.value?.unbindAll() }
    }
    AndroidView(factory = { previewView }, modifier = modifier)
}

private suspend fun awaitCameraProvider(context: Context): ProcessCameraProvider =
    suspendCancellableCoroutine { cont ->
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener(
            {
                try {
                    cont.resume(future.get())
                } catch (e: Exception) {
                    cont.cancel(e)
                }
            },
            ContextCompat.getMainExecutor(context),
        )
    }
