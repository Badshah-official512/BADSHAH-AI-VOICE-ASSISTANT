package dev.pages.badshah222ai.features.about

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.pages.badshah222ai.R
import dev.pages.badshah222ai.core.Config
import dev.pages.badshah222ai.features.settings.TopBar
import dev.pages.badshah222ai.ui.theme.*

private data class Social(val name: String, val handle: String, val url: String)
private val SOCIALS = listOf(
    Social("TikTok", "@zain_ff45", Config.TIKTOK), Social("YouTube", "@Badshahofficial-222", Config.YOUTUBE),
    Social("Facebook", "BADSHAH official", Config.FACEBOOK), Social("Instagram", "@badshahofficial222", Config.INSTAGRAM),
    Social("WhatsApp", "+92 325 1787969", Config.WHATSAPP),
)

@Composable
fun AboutScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        TopBar("About BADSHAH AI", onBack)
        LazyColumn(Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
            item {
                Image(painterResource(R.drawable.hero_banner), "BADSHAH AI — Chat. Create. Imagine.", Modifier.fillMaxWidth().height(190.dp).clip(RoundedCornerShape(20.dp)), contentScale = ContentScale.Crop)
            }
            item {
                Text("BADSHAH AI", style = MaterialTheme.typography.headlineMedium, color = Gold, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                Text("YOUR PERSONAL AI VOICE ASSISTANT", style = MaterialTheme.typography.labelLarge, color = Mute, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                Text("BADSHAH AI is a voice-first personal assistant created by BADSHAH. Talk to it, ask anything, and let it open your apps, prepare calls and messages, set alarms and timers, all with the AI provider and the voice you choose. Your keys stay on your phone.", Modifier.padding(top = 12.dp), textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onBackground)
            }
            item {
                Image(painterResource(R.drawable.about_creator), "About the creator — Zain Ullah Shah, Founder of BADSHAH AI", Modifier.fillMaxWidth().height(300.dp).clip(RoundedCornerShape(20.dp)), contentScale = ContentScale.Crop)
            }
            item { Text("Follow BADSHAH", style = MaterialTheme.typography.titleMedium, color = Gold) }
            items(SOCIALS) { s ->
                Card(Modifier.fillMaxWidth().clickable { runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(s.url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }, colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(16.dp)) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) { Text(s.name, style = MaterialTheme.typography.titleMedium); Text(s.handle, color = Mute, style = MaterialTheme.typography.bodySmall) }
                        Text("Open", color = Gold)
                    }
                }
            }
            item { Text("Version 1.0.0", Modifier.fillMaxWidth(), textAlign = TextAlign.Center, color = Mute, style = MaterialTheme.typography.bodySmall) }
        }
    }
}
