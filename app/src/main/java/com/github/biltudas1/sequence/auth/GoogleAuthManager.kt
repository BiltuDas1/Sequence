package com.github.biltudas1.sequence.auth

import android.content.Context
import android.util.Log
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

class GoogleAuthManager(private val context: Context) {
    private val credentialManager = CredentialManager.create(context)
    private val TAG = "GoogleAuthManager"

    suspend fun signIn(): GoogleIdTokenCredential? {
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
            Log.e(TAG, "Credential Manager error", e)
            null
        } catch (e: Exception) {
            Log.e(TAG, "Unknown error during sign in", e)
            null
        }
    }

    private fun handleSignIn(result: GetCredentialResponse): GoogleIdTokenCredential? {
        val credential = result.credential
        
        return if (credential is CustomCredential && 
            credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            try {
                GoogleIdTokenCredential.createFrom(credential.data)
            } catch (e: GoogleIdTokenParsingException) {
                Log.e(TAG, "Received an invalid google id token response", e)
                null
            }
        } else {
            Log.e(TAG, "Unexpected type of credential: ${credential.type}")
            null
        }
    }

    suspend fun signOut() {
        withContext(Dispatchers.IO) {
            try {
                credentialManager.clearCredentialState(ClearCredentialStateRequest())
            } catch (e: Exception) {
                Log.e(TAG, "Error during sign out", e)
            }
        }
    }
}
