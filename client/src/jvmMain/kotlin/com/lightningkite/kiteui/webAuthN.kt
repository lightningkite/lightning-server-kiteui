package com.lightningkite.kiteui

import com.lightningkite.lightningserver.sessions.proofs.WebAuthN

@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
public actual class ClientAuthenticator {
    public actual suspend fun webAuthNAvailable(): Boolean = false
    public actual suspend fun autofillAvailable(): Boolean = false
    public actual suspend fun createWebAuthNCredentials(request: WebAuthN.Registration.PublicKeyCredentialCreationOptions): WebAuthN.Registration.AttestedPublicKeyCredential = TODO()
    public actual suspend fun getWebAuthNCredentials(request: WebAuthN.Authentication.PublicKeyCredentialRequestOptions, mediation: WebAuthNMediationType): WebAuthN.Authentication.AssertedPublicKeyCredential = TODO()

    public actual companion object {
        public actual fun getClientAuthenticator(): ClientAuthenticator = ClientAuthenticator()
    }
}
