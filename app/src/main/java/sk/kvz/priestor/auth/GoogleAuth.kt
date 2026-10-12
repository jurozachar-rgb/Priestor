package sk.kvz.priestor.auth

import android.app.Activity
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.Firebase
import com.google.firebase.auth.AuthCredential
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import kotlinx.coroutines.tasks.await
import sk.kvz.priestor.R

/** Prihlásenie cez Google účet (Credential Manager) napojené na Firebase Auth. */
object GoogleAuth {

    class Cancelled : Exception("Zrušené")

    private suspend fun googleCredential(activity: Activity): AuthCredential {
        // default_web_client_id generuje plugin google-services z google-services.json
        val webClientId = activity.getString(R.string.default_web_client_id)
        val option = GetSignInWithGoogleOption.Builder(webClientId).build()
        val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
        val result = try {
            CredentialManager.create(activity).getCredential(activity, request)
        } catch (e: GetCredentialCancellationException) {
            throw Cancelled()
        }
        val cred = result.credential
        if (cred is CustomCredential && cred.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            val google = GoogleIdTokenCredential.createFrom(cred.data)
            return GoogleAuthProvider.getCredential(google.idToken, null)
        }
        error("Nepodporovaný typ prihlásenia")
    }

    suspend fun signIn(activity: Activity) {
        Firebase.auth.signInWithCredential(googleCredential(activity)).await()
    }

    /** Pred zmazaním účtu Firebase vyžaduje čerstvé prihlásenie. */
    suspend fun reauthenticate(activity: Activity) {
        val user = Firebase.auth.currentUser ?: return
        user.reauthenticate(googleCredential(activity)).await()
    }

    suspend fun signOut(activity: Activity) {
        Firebase.auth.signOut()
        runCatching { CredentialManager.create(activity).clearCredentialState(ClearCredentialStateRequest()) }
    }
}
