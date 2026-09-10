package com.ssafy.motif.feature.signin.impl.util

import android.app.Activity
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import com.ssafy.motif.feature.signin.impl.R

internal suspend fun startGoogleSignIn(
    activity: Activity,
): GoogleSignInResult {
    val credentialManager =
        CredentialManager.create(activity)

    val googleSignInOption =
        GetSignInWithGoogleOption.Builder(
            serverClientId = activity.getString(
                R.string.google_web_client_id,
            ),
        ).build()

    val request =
        GetCredentialRequest.Builder()
            .addCredentialOption(
                googleSignInOption,
            )
            .build()

    return try {
        val response =
            credentialManager.getCredential(
                context = activity,
                request = request,
            )

        val credential =
            response.credential

        if (
            credential !is CustomCredential ||
            credential.type !=
            GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
        ) {
            return GoogleSignInResult.Failure(
                throwable = IllegalStateException(
                    "Unexpected credential type: ${credential.type}",
                ),
            )
        }

        val googleCredential =
            GoogleIdTokenCredential.createFrom(
                credential.data,
            )

        GoogleSignInResult.Success(
            idToken = googleCredential.idToken,
        )
    } catch (_: GetCredentialCancellationException) {
        GoogleSignInResult.Cancelled
    } catch (exception: GoogleIdTokenParsingException) {
        GoogleSignInResult.Failure(
            throwable = exception,
        )
    } catch (exception: GetCredentialException) {
        GoogleSignInResult.Failure(
            throwable = exception,
        )
    }
}

internal sealed interface GoogleSignInResult {

    data class Success(
        val idToken: String,
    ) : GoogleSignInResult

    data object Cancelled : GoogleSignInResult

    data class Failure(
        val throwable: Throwable,
    ) : GoogleSignInResult
}