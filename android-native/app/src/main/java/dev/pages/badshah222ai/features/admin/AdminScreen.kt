package dev.pages.badshah222ai.features.admin

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Admin — Phase 1 scaffold. Phase 2 wires /admin-api/... (x-admin-password header). */
@Composable
fun AdminScreen() {
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Admin")
        Text("Admin gateway wiring arrives in Phase 2 (admin-api + Supabase verify).")
    }
}
