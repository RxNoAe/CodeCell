package dev.codecell.app.data

import android.app.Activity
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Base64
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import dev.codecell.app.BuildConfig
import org.json.JSONObject

data class GoogleProfile(
    val name: String,
    val email: String,
    val picture: String,
)

class GoogleSignInCancelled : Exception("Google sign-in cancelled")

/**
 * Sign in with Google for identity, plus Google Drive app-data authorization
 * for the session backup. Identity and Drive consent are separate flows, as
 * recommended by Google Identity Services.
 */
object GoogleAuth {

    const val DRIVE_APPDATA = "https://www.googleapis.com/auth/drive.appdata"

    private val driveScope = Scope(DRIVE_APPDATA)

    @Volatile
    var configuredClientId: String = ""

    fun webClientId(): String =
        configuredClientId.takeIf { it.isNotBlank() } ?: BuildConfig.GOOGLE_WEB_CLIENT_ID

    fun isConfigured(): Boolean = webClientId().isNotBlank()

    suspend fun signIn(activity: Activity): GoogleProfile {
        val clientId = webClientId()
        if (clientId.isBlank()) {
            throw IllegalStateException("Google sign-in is not configured for this build.")
        }
        val manager = CredentialManager.create(activity)
        val option = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setServerClientId(clientId)
            .setAutoSelectEnabled(false)
            .build()
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(option)
            .build()
        val result = try {
            manager.getCredential(request = request, context = activity)
        } catch (e: GetCredentialCancellationException) {
            throw GoogleSignInCancelled()
        } catch (e: GetCredentialException) {
            throw IllegalStateException(e.message ?: "Google sign-in failed.", e)
        }
        val credential = result.credential
        if (credential !is CustomCredential ||
            credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
        ) {
            throw IllegalStateException("Unexpected sign-in credential.")
        }
        val google = GoogleIdTokenCredential.createFrom(credential.data)
        val email = google.email ?: ""
        val fallbackName = email.substringBefore('@').ifBlank { "there" }
        return GoogleProfile(
            name = google.displayName?.takeIf { it.isNotBlank() } ?: fallbackName,
            email = email,
            picture = google.profilePictureUri?.toString().orEmpty(),
        )
    }

    fun decodeProfile(jwt: String): GoogleProfile? = runCatching {
        val parts = jwt.split(".")
        if (parts.size < 2) return@runCatching null
        val payload = String(
            Base64.decode(parts[1], Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING),
        )
        val json = JSONObject(payload)
        val email = json.optString("email")
        GoogleProfile(
            name = json.optString("name").ifBlank { email.substringBefore('@') },
            email = email,
            picture = json.optString("picture"),
        )
    }.getOrNull()

    fun requestDriveToken(
        activity: Activity,
        onToken: (String) -> Unit,
        onResolution: (PendingIntent) -> Unit,
        onError: (Throwable) -> Unit,
    ) {
        val request = AuthorizationRequest.builder()
            .setRequestedScopes(listOf(driveScope))
            .build()
        Identity.getAuthorizationClient(activity)
            .authorize(request)
            .addOnSuccessListener { result ->
                if (result.hasResolution()) {
                    val pending = result.pendingIntent
                    if (pending != null) {
                        onResolution(pending)
                    } else {
                        onError(IllegalStateException("Google Drive permission is required."))
                    }
                } else {
                    val token = result.accessToken
                    if (token.isNullOrBlank()) {
                        onError(IllegalStateException("Google Drive access was not granted."))
                    } else {
                        onToken(token)
                    }
                }
            }
            .addOnFailureListener { onError(it) }
    }

    fun driveTokenFromIntent(activity: Activity, intent: Intent): String? = runCatching {
        Identity.getAuthorizationClient(activity)
            .getAuthorizationResultFromIntent(intent)
            ?.accessToken
    }.getOrNull()

    suspend fun signOut(context: Context) {
        runCatching {
            CredentialManager.create(context)
                .clearCredentialState(ClearCredentialStateRequest())
        }
    }
}