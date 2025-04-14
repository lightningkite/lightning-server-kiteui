package com.lightningkite.kiteui

import com.lightningkite.readable.Constant
import com.lightningkite.readable.Readable
import com.lightningkite.lightningserver.auth.proof.AssertedPublicKeyCredential
import com.lightningkite.lightningserver.auth.proof.AttestedPublicKeyCredential
import com.lightningkite.lightningserver.auth.proof.PublicKeyCredentialCreationOptions
import com.lightningkite.lightningserver.auth.proof.PublicKeyCredentialRequestOptions

actual object ClientAuthenticator {
    actual val webAuthNAvailable: Readable<Boolean> = Constant(false)
    actual val autofillAvailable: Readable<Boolean> = Constant(false)
    actual suspend fun createWebAuthNCredentials(request: PublicKeyCredentialCreationOptions): AttestedPublicKeyCredential = TODO()
    actual suspend fun getWebAuthNCredentials(request: PublicKeyCredentialRequestOptions, mediation: WebAuthNMediationType): AssertedPublicKeyCredential = TODO()
}
