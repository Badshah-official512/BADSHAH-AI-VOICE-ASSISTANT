package dev.pages.badshah222ai.features.auth

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import dev.pages.badshah222ai.core.SecureStore
import dev.pages.badshah222ai.data.auth.AuthRepository
import dev.pages.badshah222ai.ui.theme.Bad
import dev.pages.badshah222ai.ui.theme.Gold
import dev.pages.badshah222ai.ui.theme.Mute
import kotlinx.coroutines.launch

/** Web OAuth client id of Firebase project badshah-ai-badshah222 (public client id, also shipped in the web config). */
private const val GOOGLE_WEB_CLIENT_ID =
    "949903255351-s6ohu1a2aut5j3s62nv9mfsc15jlkh4g.apps.googleusercontent.com"

/**
 * Auth — WIRED (Phase 2): Google via Credential Manager, email/password via
 * Firebase Auth (project badshah-ai-badshah222), guest mode (chat/images
 * without account; voice call needs login + the user's own Gemini key).
 */
@Composable
fun AuthScreen(
    auth: AuthRepository,
    store: SecureStore,
    onSignedIn: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun done() {
        store.guestMode = false
        onSignedIn()
    }

    fun googleSignIn() {
        busy = true; error = null
        scope.launch {
            try {
                val idToken = googleIdToken(context)
                val r = auth.signInWithGoogle(idToken)
                if (r.isSuccess) done() else error = r.exceptionOrNull()?.message ?: "Google sign-in failed"
            } catch (e: Exception) {
                error = e.message ?: "Google sign-in failed"
            }
            busy = false
        }
    }

    fun emailAuth(signUp: Boolean) {
        if (email.isBlank() || password.isBlank()) {
            error = "Enter your email and password."
            return
        }
        busy = true; error = null
        scope.launch {
            val r = if (signUp) auth.signUpEmail(email, password) else auth.signInEmail(email, password)
            if (r.isSuccess) done() else error = r.exceptionOrNull()?.message ?: "Sign-in failed"
            busy = false
        }
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxSize().padding(24.dp),
    ) {
        Text("BADSHAH AI", fontSize = 32.sp, fontWeight = FontWeight.Bold, color = Gold)
        Text("Sign in to continue", color = Mute)
        Spacer(Modifier.height(32.dp))
        Button(onClick = { googleSignIn() }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
            Text("Continue with Google")
        }
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(value = email, onValueChange = { email = it }, label = { Text("Email") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(
            value = password, onValueChange = { password = it },
            label = { Text("Password") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
        )
        if (error != null) {
            Spacer(Modifier.height(8.dp))
            Text(error!!, color = Bad)
        }
        Spacer(Modifier.height(16.dp))
        Button(onClick = { emailAuth(signUp = false) }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
            Text("Sign in")
        }
        TextButton(onClick = { emailAuth(signUp = true) }, enabled = !busy) {
            Text("New here? Create account")
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick = { store.guestMode = true; onSignedIn() },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Continue as guest")
        }
        Text("Guests can chat and make images. Voice calls need sign-in.", color = Mute)
    }
}

private suspend fun googleIdToken(context: Context): String {
    val cm = CredentialManager.create(context)
    val option = GetGoogleIdOption.Builder()
        .setServerClientId(GOOGLE_WEB_CLIENT_ID)
        .setFilterByAuthorizedAccounts(false)
        .build()
    val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
    val result = cm.getCredential(context, request)
    val cred = GoogleIdTokenCredential.createFrom(result.credential.data)
    return cred.idToken
}
