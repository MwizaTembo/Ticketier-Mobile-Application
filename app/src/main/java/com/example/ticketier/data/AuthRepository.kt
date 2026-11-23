package com.example.ticketier.data

import android.util.Log
import com.example.ticketier.data.remote.SupabaseConfig
import com.example.ticketier.data.remote.SupabaseProvider
import io.github.jan.supabase.auth.OtpType
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** What happened when an admin tried to make another user an admin. */
enum class PromoteResult { PROMOTED, NOT_FOUND, FAILED }

/** What happened when someone tried to sign in. */
sealed interface AuthResult {
    data object Success : AuthResult
    data class Error(val message: String) : AuthResult
}

// One row of the `profiles` table.


// One row of the `favorites` table.
@Serializable
data class FavoriteRow(
    @SerialName("user_id") val userId: String,
    @SerialName("event_id") val eventId: Int
)

object AuthRepository {
    private const val TAG = "AuthRepository"
    private val client get() = SupabaseProvider.client

    // ---------- Sign in ----------

    suspend fun signInCustomer(email: String, password: String): AuthResult =
        signIn(email, password, requireAdmin = false)

    suspend fun signInAdmin(email: String, password: String): AuthResult =
        signIn(email, password, requireAdmin = true)

    private suspend fun signIn(email: String, password: String, requireAdmin: Boolean): AuthResult {
        if (!SupabaseConfig.isConfigured) {
            return AuthResult.Error("Supabase is not set up yet. Paste your project URL into SupabaseConfig.kt.")
        }
        return try {
            client.auth.signInWith(Email) {
                this.email = email
                this.password = password
            }
            if (requireAdmin && !isAdmin()) {
                client.auth.signOut()
                AuthResult.Error("Access denied: admins only")
            } else {
                AuthResult.Success
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Sign in failed", e)
            AuthResult.Error(friendlyMessage(e))
        }
    }

    // ---------- Sign up ----------

    suspend fun signUp(email: String, password: String, fullName: String): AuthResult {
        if (!SupabaseConfig.isConfigured) {
            return AuthResult.Error("Supabase is not set up yet.")
        }
        return try {
            client.auth.signUpWith(Email) {
                this.email = email
                this.password = password
            }
            val uid = client.auth.currentUserOrNull()?.id
                ?: return AuthResult.Error(
                    "Account created but no session. Turn OFF 'Confirm email' in Supabase (Auth → Sign In / Providers → Email)."
                )
            client.from("profiles").update(mapOf("full_name" to fullName)) {
                filter { eq("id", uid) }
            }
            AuthResult.Success
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Sign up failed", e)
            AuthResult.Error(friendlySignUpMessage(e))
        }
    }

    // ---------- Profile ----------

    suspend fun isAdmin(): Boolean {
        val userId = client.auth.currentUserOrNull()?.id ?: return false
        val profile = client.from("profiles")
            .select { filter { eq("id", userId) } }
            .decodeSingle<Profile>()
        return profile.role == "admin"
    }

    suspend fun loadProfile(): Profile? {
        val userId = client.auth.currentUserOrNull()?.id ?: return null
        return try {
            client.from("profiles")
                .select { filter { eq("id", userId) } }
                .decodeSingleOrNull<Profile>()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Load profile failed", e)
            null
        }
    }

    suspend fun updateFullName(name: String): Boolean {
        val userId = client.auth.currentUserOrNull()?.id ?: return false
        return try {
            client.from("profiles").update(mapOf("full_name" to name)) {
                filter { eq("id", userId) }
            }
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Update name failed", e)
            false
        }
    }

    fun currentEmail(): String? = client.auth.currentUserOrNull()?.email

    // ---------- Favourites ----------

    suspend fun getFavoriteEventIds(): Set<Int> {
        val userId = client.auth.currentUserOrNull()?.id ?: return emptySet()
        return try {
            client.from("favorites")
                .select { filter { eq("user_id", userId) } }
                .decodeList<FavoriteRow>()
                .map { it.eventId }
                .toSet()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Get favorites failed", e)
            emptySet()
        }
    }

    /** Returns true if the event is now favourited, false if it was just removed. */
    suspend fun toggleFavorite(eventId: Int): Boolean {
        val userId = client.auth.currentUserOrNull()?.id ?: return false
        return try {
            val existing = client.from("favorites")
                .select {
                    filter {
                        eq("user_id", userId)
                        eq("event_id", eventId)
                    }
                }
                .decodeList<FavoriteRow>()
            if (existing.isEmpty()) {
                client.from("favorites").insert(FavoriteRow(userId, eventId))
                true
            } else {
                client.from("favorites").delete {
                    filter {
                        eq("user_id", userId)
                        eq("event_id", eventId)
                    }
                }
                false
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Toggle favorite failed", e)
            false
        }
    }

    // ---------- Forgot password ----------
    // Two steps. 1) Supabase emails a code (the Reset Password email template must contain
    // {{ .Token }}). 2) The user types the code and a new password.

    /** Step 1: emails a reset code to `email`. Returns an error message, or null on success. */
    suspend fun sendPasswordResetCode(email: String): String? {
        return try {
            client.auth.resetPasswordForEmail(email = email.trim())
            null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Sending reset code failed", e)
            "Couldn't send the email. Check your internet connection and try again."
        }
    }

    /** Step 2: checks the code, sets the new password, then signs out. Returns an error message, or null. */
    suspend fun resetPasswordWithCode(email: String, code: String, newPassword: String): String? {
        return try {
            // Entering the right code signs the user in briefly...
            client.auth.verifyEmailOtp(
                type = OtpType.Email.RECOVERY,
                email = email.trim(),
                token = code.trim()
            )
            // ...which allows changing the password.
            client.auth.updateUser { password = newPassword }
            // Make them log in with the new password.
            client.auth.signOut()
            null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Resetting password failed", e)
            val text = (e.message ?: "").lowercase()
            when {
                "different" in text || "same password" in text ->
                    "Choose a password you haven't used before."
                "expired" in text || "invalid" in text || "otp" in text || "token" in text ->
                    "That code is wrong or has expired. Request a new one."
                "password" in text && "6" in text ->
                    "Password must be at least 6 characters."
                else ->
                    "Couldn't reset the password. Check your internet connection and try again."
            }
        }
    }

    // ---------- Add admin ----------

    /** Admin only: makes an already-registered user an admin (runs the `promote_to_admin` database function). */
    suspend fun promoteToAdmin(email: String): PromoteResult {
        return try {
            val answer = client.postgrest.rpc(
                function = "promote_to_admin",
                parameters = buildJsonObject { put("target_email", email.trim()) }
            ).decodeAs<String>()
            when (answer) {
                "OK" -> PromoteResult.PROMOTED
                "NOT_FOUND" -> PromoteResult.NOT_FOUND
                else -> PromoteResult.FAILED
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Promote to admin failed", e)
            PromoteResult.FAILED
        }
    }

    // ---------- Stay logged in ----------

    /**
     * Called when the app starts. Supabase saves the session on the phone; this waits
     * until it has loaded it, then tells the app where to go:
     * "login" (nobody signed in), "home" (customer) or "adminHome" (admin).
     */
    suspend fun startDestination(): String {
        return try {
            client.auth.awaitInitialization()
            if (client.auth.currentSessionOrNull() == null) {
                "login"
            } else {
                // If the admin check fails (for example no internet), treat them as a customer.
                val admin = try {
                    isAdmin()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e(TAG, "Admin check failed on start", e)
                    false
                }
                if (admin) "adminHome" else "home"
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Could not restore session", e)
            "login"
        }
    }

    // ---------- Sign out ----------

    suspend fun signOut() {
        TicketRepository.clear() // don't leave this user's tickets in memory
        try {
            client.auth.signOut()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Sign out failed", e)
        }
    }

    // ---------- Errors ----------

    private fun friendlyMessage(e: Exception): String {
        val text = (e.message ?: "").lowercase()
        return when {
            "invalid login credentials" in text || "invalid_credentials" in text ->
                "Incorrect email or password."
            "email not confirmed" in text ->
                "Please confirm your email address first."
            else ->
                "Couldn't reach the server. Check your internet connection and try again."
        }
    }

    private fun friendlySignUpMessage(e: Exception): String {
        val text = (e.message ?: "").lowercase()
        return when {
            "already registered" in text || "user already exists" in text ->
                "That email already has an account. Try logging in."
            "password" in text && "6" in text ->
                "Password must be at least 6 characters."
            "invalid" in text && "email" in text ->
                "That doesn't look like a valid email."
            else ->
                "Couldn't create the account. Check your internet connection."
        }
    }
}