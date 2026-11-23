package dev.pages.badshah222ai.features.tools

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
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

private data class Tool(val name: String, val label: String, val hint: String)

/**
 * Tools — Phase 1 scaffold with real /api/tools/... wiring.
 * Worker tools (verified in _worker.js): tiktok, lyrics-search, lyrics-get,
 * wiki, ask, fact, shorten, tts2 (+ /api/news for Khabrein).
 */
@Composable
fun ToolsScreen(api: BadshahApi, onScreenGuide: () -> Unit) {
    val tools = listOf(
        Tool("wiki", "Wikipedia", "Topic"),
        Tool("ask", "Ask AI", "Question"),
        Tool("fact", "Random Fact", ""),
        Tool("shorten", "Shorten Link", "https://…"),
        Tool("lyrics-search", "Lyrics Search", "Song name"),
        Tool("tiktok", "TikTok Download", "TikTok link"),
    )
    var selected by remember { mutableStateOf<Tool?>(null) }
    var input by remember { mutableStateOf("") }
    var output by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Tools")
        Button(onClick = onScreenGuide, modifier = Modifier.fillMaxWidth()) {
            Text("Screen Guide — AI dekhe aur step by step bataye")
        }
        LazyVerticalGrid(columns = GridCells.Fixed(2), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(tools) { t ->
                Button(onClick = { selected = t; output = "" }, modifier = Modifier.fillMaxWidth()) { Text(t.label) }
            }
        }
        selected?.let { t ->
            if (t.hint.isNotEmpty()) {
                OutlinedTextField(value = input, onValueChange = { input = it }, label = { Text(t.hint) }, modifier = Modifier.fillMaxWidth())
            }
            Button(onClick = {
                scope.launch {
                    val params = when (t.name) {
                        "tiktok" -> mapOf("url" to input)
                        "shorten" -> mapOf("url" to input)
                        else -> mapOf("q" to input)
                    }
                    output = when (val r = api.tool(t.name, params)) {
                        is ApiResult.Ok -> r.value.take(1500)
                        is ApiResult.Err -> "Error: ${r.message}"
                    }
                }
            }, modifier = Modifier.fillMaxWidth()) { Text("Run") }
            if (output.isNotEmpty()) Text(output)
        }
    }
}
