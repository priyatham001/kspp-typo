package com.example.auth

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import com.example.R
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.AuthCredential
import com.google.firebase.auth.GoogleAuthProvider
import java.security.MessageDigest
import java.util.UUID

object GoogleSignInHelper {

    suspend fun getGoogleAuthCredential(context: Context): AuthCredential? {
        val credentialManager = CredentialManager.create(context)
        val serverClientId = try {
            val resId = context.resources.getIdentifier("default_web_client_id", "string", context.packageName)
            val id = if (resId != 0) context.getString(resId) else ""
            if (id.isNotBlank()) id else "779877421762-5memp0o8m6stoik4d7or0kmcssdvi591.apps.googleusercontent.com"
        } catch (e: Exception) {
            "779877421762-5memp0o8m6stoik4d7or0kmcssdvi591.apps.googleusercontent.com"
        }

        // Raw nonce for replay attack prevention
        val rawNonce = UUID.randomUUID().toString()
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(rawNonce.toByteArray())
        val hashedNonce = digest.fold("") { str, it -> str + "%02x".format(it) }

        val googleIdOption = GetSignInWithGoogleOption.Builder(serverClientId)
            .setNonce(hashedNonce)
            .build()

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()

        val result = credentialManager.getCredential(
            request = request,
            context = context
        )

        val credential = result.credential
        if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
            return GoogleAuthProvider.getCredential(googleIdTokenCredential.idToken, null)
        }

        return null
    }
}
