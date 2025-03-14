package com.lightningkite.kiteui

import com.lightningkite.kiteui.reactive.Constant
import com.lightningkite.kiteui.reactive.Readable
import com.lightningkite.lightningserver.auth.proof.AssertedPublicKeyCredential
import com.lightningkite.lightningserver.auth.proof.AttestedPublicKeyCredential
import com.lightningkite.lightningserver.auth.proof.PublicKeyCredentialCreationOptions
import com.lightningkite.lightningserver.auth.proof.PublicKeyCredentialRequestOptions

actual object ClientAuthenticator {
    actual val passkeyAvailable: Readable<Boolean> = Constant(false)
    actual val autofillAvailable: Readable<Boolean> = Constant(false)
    actual suspend fun createPasskey(request: PublicKeyCredentialCreationOptions): AttestedPublicKeyCredential = TODO()
    actual suspend fun getPasskey(request: PublicKeyCredentialRequestOptions, mediation: PasskeyMediationType): AssertedPublicKeyCredential = TODO()
}
