package com.lightningkite.kiteui

import com.lightningkite.lightningserver.sessions.proofs.WebAuthN


@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
actual class ClientAuthenticator {
    actual suspend fun webAuthNAvailable(): Boolean = false
    actual suspend fun autofillAvailable(): Boolean = false
    actual suspend fun createWebAuthNCredentials(request: WebAuthN.Registration.PublicKeyCredentialCreationOptions): WebAuthN.Registration.AttestedPublicKeyCredential = TODO()
    actual suspend fun getWebAuthNCredentials(request: WebAuthN.Authentication.PublicKeyCredentialRequestOptions, mediation: WebAuthNMediationType): WebAuthN.Authentication.AssertedPublicKeyCredential = TODO()

    actual companion object {
        actual fun getClientAuthenticator(): ClientAuthenticator = ClientAuthenticator()
    }
}
