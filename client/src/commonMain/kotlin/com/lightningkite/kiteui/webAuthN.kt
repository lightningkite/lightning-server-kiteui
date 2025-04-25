package com.lightningkite.kiteui

import com.lightningkite.lightningserver.auth.proof.WebAuthN

@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
expect class ClientAuthenticator {
    suspend fun webAuthNAvailable(): Boolean
    suspend fun autofillAvailable(): Boolean
    suspend fun createWebAuthNCredentials(request: WebAuthN.Registration.PublicKeyCredentialCreationOptions): WebAuthN.Registration.AttestedPublicKeyCredential
    suspend fun getWebAuthNCredentials(
        request: WebAuthN.Authentication.PublicKeyCredentialRequestOptions,
        mediation: WebAuthNMediationType = WebAuthNMediationType.Optional,
    ): WebAuthN.Authentication.AssertedPublicKeyCredential

    companion object{
        fun getClientAuthenticator(): ClientAuthenticator
    }
}

enum class WebAuthNMediationType(val standardName:String) {
    Conditional("conditional"),
    Optional("optional"),
    Required("required"),
    Silent("silent")
}
