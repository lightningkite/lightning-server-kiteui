package com.lightningkite.kiteui

import com.lightningkite.lightningserver.sessions.proofs.WebAuthN

/** WebAuthn isn't supported on the JVM, so [webAuthNAvailable] is false and both ceremonies fail as not supported. */
@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
public actual class ClientAuthenticator {
    public actual suspend fun webAuthNAvailable(): Boolean = false
    public actual suspend fun autofillAvailable(): Boolean = false
    public actual suspend fun createWebAuthNCredentials(request: WebAuthN.Registration.PublicKeyCredentialCreationOptions): WebAuthN.Registration.AttestedPublicKeyCredential =
        throw WebAuthNFailure.NotSupported.exception()
    public actual suspend fun getWebAuthNCredentials(request: WebAuthN.Authentication.PublicKeyCredentialRequestOptions, mediation: WebAuthNMediationType): WebAuthN.Authentication.AssertedPublicKeyCredential =
        throw WebAuthNFailure.NotSupported.exception()

    public actual companion object {
        private val default = ClientAuthenticator()
        public actual fun getClientAuthenticator(): ClientAuthenticator = default
    }
}
