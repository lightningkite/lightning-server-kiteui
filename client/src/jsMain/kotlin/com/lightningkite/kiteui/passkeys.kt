package com.lightningkite.kiteui

import com.lightningkite.lightningserver.auth.proof.AssertedPublicKeyCredential
import com.lightningkite.lightningserver.auth.proof.AttestedPublicKeyCredential
import com.lightningkite.lightningserver.auth.proof.PublicKeyCredentialCreationOptions
import com.lightningkite.lightningserver.auth.proof.PublicKeyCredentialRequestOptions
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToDynamic
import kotlinx.serialization.json.decodeFromDynamic
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine
import kotlin.js.Promise

@OptIn(ExperimentalSerializationApi::class)
actual object ClientAuthenticator {
    actual suspend fun createPasskey(request: PublicKeyCredentialCreationOptions): AttestedPublicKeyCredential =
        suspendCoroutine<AttestedPublicKeyCredential> { cont ->
            val request = Json.encodeToDynamic(PublicKeyCredentialCreationOptions.serializer(), request)
            val credential = credentials.create(js("({ publicKey: PublicKeyCredential.parseCreationOptionsFromJSON(request) })"))
            credential.then(
                onFulfilled = { result ->
                    cont.resume(Json.decodeFromDynamic(result.toJSON()))
                },
                onRejected = { error ->
                    cont.resumeWithException(error)
                }
            )
        }

    actual suspend fun getPasskey(request: PublicKeyCredentialRequestOptions, mediation: PasskeyMediationType) =
        suspendCoroutine<AssertedPublicKeyCredential> { cont ->
            val request = Json.encodeToDynamic(PublicKeyCredentialRequestOptions.serializer(), request)
            val mediationString = when (mediation) {
                PasskeyMediationType.Optional -> "optional"
                PasskeyMediationType.Required -> "required"
                PasskeyMediationType.Conditional -> "conditional"
                PasskeyMediationType.Silent -> "silent"
            }
            val credential = credentials.get(js("({ mediation: mediationString, publicKey: PublicKeyCredential.parseRequestOptionsFromJSON(request) })"))
            credential.then(
                onFulfilled = { result ->
                    cont.resume(Json.decodeFromDynamic(result.toJSON()))
                },
                onRejected = { error ->
                    cont.resumeWithException(error)
                }
            )
        }
}

val credentials: CredentialServices get() = js("(navigator.credentials)")

external class CredentialServices {
    fun create(options: dynamic): Promise<PublicKeyCredential>
    fun get(options: dynamic): Promise<PublicKeyCredential>
}

external class PublicKeyCredential {
    fun toJSON(): dynamic
}
