package dev.pages.badshah222ai.features.chat

import android.app.Application
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.pages.badshah222ai.BadshahApp
import dev.pages.badshah222ai.data.supabase.CloudException
import dev.pages.badshah222ai.data.supabase.RemoteConvo
import dev.pages.badshah222ai.data.supabase.RemoteMsg
import dev.pages.badshah222ai.features.home.HomeViewModel
import dev.pages.badshah222ai.features.settings.TopBar
import dev.pages.badshah222ai.ui.theme.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class HistoryViewModel(app: Application) : AndroidViewModel(app) {
    private val c = (app as BadshahApp).container
    val convos = MutableStateFlow<List<RemoteConvo>>(emptyList())
    val opened = MutableStateFlow<Pair<RemoteConvo, List<RemoteMsg>>?>(null)
    val error = MutableStateFlow<String?>(null)
    init { refresh() }
    fun refresh() = viewModelScope.launch { try { convos.value = c.cloud.listConversations(); error.value = null } catch (e: CloudException) { error.value = e.message } }
    fun open(cv: RemoteConvo) = viewModelScope.launch { runCatching { opened.value = cv to c.cloud.listMessages(cv.id) } }
    fun close() { opened.value = null }
    fun delete(cv: RemoteConvo) = viewModelScope.launch { runCatching { c.cloud.deleteConversation(cv.id) }; opened.value = null; refresh() }
}

@Composable
fun ChatHistoryScreen(home: HomeViewModel, vm: HistoryViewModel, onBack: () -> Unit) {
    val local by home.messages.collectAsState(); val convos by vm.convos.collectAsState(); val err by vm.error.collectAsState(); val opened by vm.opened.collectAsState()
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        TopBar("Chat history", onBack)
        LazyColumn(Modifier.padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
            item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { OutlinedButton({ home.newChat() }) { Text("Start a new chat") }; OutlinedButton({ vm.refresh() }) { Text("Refresh") } } }
            item { Text("On this phone (${local.size} messages)", style = MaterialTheme.typography.titleMedium, color = Gold) }
            items(local.takeLast(30)) { m -> Text((if (m.role == "user") "You: " else "BADSHAH: ") + m.content, color = if (m.role == "user") MaterialTheme.colorScheme.onSurface else Gold, style = MaterialTheme.typography.bodyMedium) }
            item { Text("Synced conversations", style = MaterialTheme.typography.titleMedium, color = Gold, modifier = Modifier.padding(top = 8.dp)) }
            err?.let { e -> item { Text("Cloud history unavailable: $e", color = Mute) } }
            items(convos) { cv -> Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Panel), onClick = { vm.open(cv) }) { Column(Modifier.padding(12.dp)) { Text(cv.title); Text(cv.updatedAt.take(16).replace('T', ' '), style = MaterialTheme.typography.bodySmall, color = Mute) } } }
        }
    }
    opened?.let { (cv, msgs) ->
        AlertDialog(onDismissRequest = vm::close, title = { Text(cv.title) },
            text = { LazyColumn { items(msgs) { m -> Text((if (m.role == "user") "You: " else "BADSHAH: ") + m.content, Modifier.padding(vertical = 4.dp)) } } },
            confirmButton = { TextButton(vm::close) { Text("Close") } }, dismissButton = { TextButton({ vm.delete(cv) }) { Text("Delete", color = Bad) } })
    }
}
