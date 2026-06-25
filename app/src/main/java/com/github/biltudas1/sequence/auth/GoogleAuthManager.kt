package com.github.biltudas1.sequence.auth

import android.content.Context
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import androidx.credentials.exceptions.GetCredentialException
import com.github.biltudas1.sequence.R
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

class GoogleAuthManager(private val context: Context) {
    private val credentialManager = CredentialManager.create(context)

    suspend fun signIn(): GoogleIdTokenCredential? {
        Timber.d("signIn: Initiating credential request")
        val serverClientId = context.getString(R.string.google_web_client_id)
        
        val signInWithGoogleOption = GetSignInWithGoogleOption.Builder(serverClientId)
            .build()

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(signInWithGoogleOption)
            .build()

        return try {
            val result = credentialManager.getCredential(
                context = context,
                request = request
            )
            handleSignIn(result)
        } catch (e: GetCredentialException) {
            Timber.e(e, "Credential Manager error")
            null
        } catch (e: Exception) {
            Timber.e(e, "Unknown error during sign in")
            null
        }
    }

    private fun handleSignIn(result: GetCredentialResponse): GoogleIdTokenCredential? {
        val credential = result.credential
        Timber.d("handleSignIn: Received credential of type ${credential.type}")
        
        return if (credential is CustomCredential && 
            credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            try {
                GoogleIdTokenCredential.createFrom(credential.data)
            } catch (e: GoogleIdTokenParsingException) {
                Timber.e(e, "Received an invalid google id token response")
                null
            }
        } else {
            Timber.e("Unexpected type of credential: ${credential.type}")
            null
        }
    }

    suspend fun signOut() {
        Timber.i("signOut: Clearing credential state")
        withContext(Dispatchers.IO) {
            try {
                credentialManager.clearCredentialState(ClearCredentialStateRequest())
            } catch (e: Exception) {
                Timber.e(e, "Error during sign out")
            }
        }
    }
}
