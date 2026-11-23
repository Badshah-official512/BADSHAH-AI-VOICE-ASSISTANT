package dev.pages.badshah222ai.features.memory

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.pages.badshah222ai.data.api.ApiResult
import dev.pages.badshah222ai.data.api.BadshahApi
import kotlinx.coroutines.launch

/**
 * Memory (Yaad) — Phase 1 scaffold with real /api/memories wiring.
 * Phase 2 adds: list rendering, add/delete, Supabase-backed detail view.
 * Auth note: /api/memories needs the Firebase token (wired in Phase 2).
 */
@Composable
fun MemoryScreen(api: BadshahApi) {
    var output by remember { mutableStateOf("Tap Load to fetch your memories.") }
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Memory — Yaad")
        Spacer(Modifier.height(12.dp))
        Button(onClick = {
            scope.launch {
                output = when (val r = api.memories()) {
                    is ApiResult.Ok -> r.value.take(3000).ifBlank { "No memories yet." }
                    is ApiResult.Err -> "Error: ${r.message}"
                }
            }
        }) { Text("Load memories") }
        Spacer(Modifier.height(12.dp))
        Text(output)
    }
}
