package dev.pages.badshah222ai.data.auth

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.tasks.await

/**
 * Firebase Auth (project badshah-ai-badshah222) — native.
 * Email/password + Google (Credential Manager ID token) + guest mode.
 * The ID token feeds BadshahApi (worker verifies it via Firebase JWKS for
 * /api/live-key, /api/memories, /api/device).
 */
class AuthRepository(private val auth: FirebaseAuth = FirebaseAuth.getInstance()) {

    val currentUser: FirebaseUser? get() = auth.currentUser
    val isSignedIn: Boolean get() = auth.currentUser != null

    suspend fun idToken(forceRefresh: Boolean = false): String? =
        try {
            auth.currentUser?.getIdToken(forceRefresh)?.await()?.token
        } catch (e: Exception) {
            null
        }

    suspend fun signInEmail(email: String, password: String): Result<FirebaseUser> =
        try {
            val r = auth.signInWithEmailAndPassword(email.trim(), password).await()
            Result.success(r.user!!)
        } catch (e: Exception) {
            Result.failure(e)
        }

    suspend fun signUpEmail(email: String, password: String): Result<FirebaseUser> =
        try {
            val r = auth.createUserWithEmailAndPassword(email.trim(), password).await()
            Result.success(r.user!!)
        } catch (e: Exception) {
            Result.failure(e)
        }

    /** Google sign-in: [idToken] comes from Credential Manager (Google ID). */
    suspend fun signInWithGoogle(idToken: String): Result<FirebaseUser> =
        try {
            val cred = GoogleAuthProvider.getCredential(idToken, null)
            val r = auth.signInWithCredential(cred).await()
            Result.success(r.user!!)
        } catch (e: Exception) {
            Result.failure(e)
        }

    fun signOut() = auth.signOut()
}
