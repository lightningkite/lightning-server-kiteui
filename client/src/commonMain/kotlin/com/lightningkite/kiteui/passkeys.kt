package com.lightningkite.kiteui

import com.lightningkite.lightningserver.auth.proof.AssertedPublicKeyCredential
import com.lightningkite.lightningserver.auth.proof.AttestedPublicKeyCredential
import com.lightningkite.lightningserver.auth.proof.PublicKeyCredentialCreationOptions
import com.lightningkite.lightningserver.auth.proof.PublicKeyCredentialRequestOptions

expect object ClientAuthenticator {
    suspend fun createPasskey(request: PublicKeyCredentialCreationOptions): AttestedPublicKeyCredential
    suspend fun getPasskey(request: PublicKeyCredentialRequestOptions, mediation: PasskeyMediationType = PasskeyMediationType.Optional): AssertedPublicKeyCredential
}

enum class PasskeyMediationType { Conditional, Optional, Required, Silent }