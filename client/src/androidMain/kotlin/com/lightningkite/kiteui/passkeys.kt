package com.lightningkite.kiteui

import com.lightningkite.lightningserver.auth.proof.AssertedPublicKeyCredential
import com.lightningkite.lightningserver.auth.proof.AttestedPublicKeyCredential
import com.lightningkite.lightningserver.auth.proof.PublicKeyCredentialCreationOptions
import com.lightningkite.lightningserver.auth.proof.PublicKeyCredentialRequestOptions

actual object ClientAuthenticator {
    actual suspend fun createPasskey(request: PublicKeyCredentialCreationOptions): AttestedPublicKeyCredential = TODO()
    actual suspend fun getPasskey(request: PublicKeyCredentialRequestOptions, mediation: PasskeyMediationType): AssertedPublicKeyCredential = TODO()
}
