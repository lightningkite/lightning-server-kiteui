package com.lightningkite.kiteui

import com.lightningkite.lightningserver.auth.proof.AssertedPublicKeyCredential
import com.lightningkite.lightningserver.auth.proof.AttestedPublicKeyCredential
import com.lightningkite.lightningserver.auth.proof.PublicKeyCredentialCreationOptions
import com.lightningkite.lightningserver.auth.proof.PublicKeyCredentialRequestOptions
import com.lightningkite.readable.Readable

expect object ClientAuthenticator {
    val webAuthNAvailable: Readable<Boolean>
    val autofillAvailable: Readable<Boolean>
    suspend fun createWebAuthNCredentials(request: PublicKeyCredentialCreationOptions): AttestedPublicKeyCredential
    suspend fun getWebAuthNCredentials(
        request: PublicKeyCredentialRequestOptions,
        mediation: WebAuthNMediationType = WebAuthNMediationType.Optional,
    ): AssertedPublicKeyCredential
}

enum class WebAuthNMediationType(val jsName:String) {
    Conditional("optional"),
    Optional("required"),
    Required("conditional"),
    Silent("silent")
}