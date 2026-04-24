package com.lightningkite.kiteui

import com.lightningkite.lightningserver.sessions.proofs.WebAuthN

@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
public expect class ClientAuthenticator {
    public suspend fun webAuthNAvailable(): Boolean
    public suspend fun autofillAvailable(): Boolean
    public suspend fun createWebAuthNCredentials(request: WebAuthN.Registration.PublicKeyCredentialCreationOptions): WebAuthN.Registration.AttestedPublicKeyCredential
    public suspend fun getWebAuthNCredentials(
        request: WebAuthN.Authentication.PublicKeyCredentialRequestOptions,
        mediation: WebAuthNMediationType = WebAuthNMediationType.Optional,
    ): WebAuthN.Authentication.AssertedPublicKeyCredential

    public companion object{
        public fun getClientAuthenticator(): ClientAuthenticator
    }
}

public enum class WebAuthNMediationType(public val standardName:String) {
    Conditional("conditional"),
    Optional("optional"),
    Required("required"),
    Silent("silent")
}
