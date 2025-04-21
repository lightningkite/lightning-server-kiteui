package com.lightningkite.kiteui

import com.lightningkite.lightningserver.auth.proof.WebAuthN
import com.lightningkite.readable.Constant
import com.lightningkite.readable.Readable

@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
actual class ClientAuthenticator {
    actual val webAuthNAvailable: Readable<Boolean> = Constant(false)
    actual val autofillAvailable: Readable<Boolean> = Constant(false)
    actual suspend fun createWebAuthNCredentials(request: WebAuthN.Registration.PublicKeyCredentialCreationOptions): WebAuthN.Registration.AttestedPublicKeyCredential = TODO()
    actual suspend fun getWebAuthNCredentials(request: WebAuthN.Authentication.PublicKeyCredentialRequestOptions, mediation: WebAuthNMediationType): WebAuthN.Authentication.AssertedPublicKeyCredential = TODO()

    actual companion object {
        actual fun getClientAuthenticator(): ClientAuthenticator = TODO()
    }
}
