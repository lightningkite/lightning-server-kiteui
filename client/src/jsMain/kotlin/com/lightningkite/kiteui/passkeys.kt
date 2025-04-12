package com.lightningkite.kiteui

import com.lightningkite.kiteui.exceptions.PlainTextException
import com.lightningkite.lightningserver.auth.proof.AssertedPublicKeyCredential
import com.lightningkite.lightningserver.auth.proof.AttestedPublicKeyCredential
import com.lightningkite.lightningserver.auth.proof.PublicKeyCredentialCreationOptions
import com.lightningkite.lightningserver.auth.proof.PublicKeyCredentialRequestOptions
import com.lightningkite.readable.Readable
import com.lightningkite.readable.sharedProcess
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToDynamic
import kotlinx.serialization.json.decodeFromDynamic
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.js.Promise

@OptIn(ExperimentalSerializationApi::class)
actual object ClientAuthenticator {

    private val webauthnAPIAvailable: Boolean
        get() = js("(window.PublicKeyCredential && PublicKeyCredential.isConditionalMediationAvailable)")

    actual val passkeyAvailable: Readable<Boolean> = sharedProcess {
        if (!webauthnAPIAvailable) {
            emit(false)
        } else {
            PublicKeyCredential.isUserVerifyingPlatformAuthenticatorAvailable().then(
                onFulfilled = { emit(it) },
                onRejected = { emit(false) }
            )
        }
    }

    actual val autofillAvailable: Readable<Boolean> = sharedProcess {
        if (!webauthnAPIAvailable) {
            emit(false)
        } else {
            PublicKeyCredential.isConditionalMediationAvailable().then(
                onFulfilled = { emit(it) },
                onRejected = { emit(false) }
            )
        }
    }

    actual suspend fun createPasskey(request: PublicKeyCredentialCreationOptions): AttestedPublicKeyCredential =
        suspendCancellableCoroutine<AttestedPublicKeyCredential> { cont ->
            val controller = AbortController()
            val request = Json.encodeToDynamic(PublicKeyCredentialCreationOptions.serializer(), request)
            val credential = credentials.create(js("({ publicKey: PublicKeyCredential.parseCreationOptionsFromJSON(request), signal: controller.signal })"))
            credential.then(
                onFulfilled = { result ->
                    println(result)
                    println(result.toJSON().stringify())
                    cont.resume(Json.decodeFromDynamic(result.toJSON()))
                },
                onRejected = { error ->
                    cont.resumeWithException(error.asKotlinException())
                }
            )
            cont.invokeOnCancellation { controller.abort() }
        }

    actual suspend fun getPasskey(request: PublicKeyCredentialRequestOptions, mediation: PasskeyMediationType) =
        suspendCancellableCoroutine<AssertedPublicKeyCredential> { cont ->
            val controller = AbortController()
            val request = Json.encodeToDynamic(PublicKeyCredentialRequestOptions.serializer(), request)
            val mediationString = when (mediation) {
                PasskeyMediationType.Optional -> "optional"
                PasskeyMediationType.Required -> "required"
                PasskeyMediationType.Conditional -> "conditional"
                PasskeyMediationType.Silent -> "silent"
            }
            val credential = credentials.get(js("({ mediation: mediationString, publicKey: PublicKeyCredential.parseRequestOptionsFromJSON(request), signal: controller.signal })"))
            credential.then(
                onFulfilled = { result ->
                    cont.resume(Json.decodeFromDynamic(result.toJSON()))
                },
                onRejected = { error ->
                    cont.resumeWithException(error.asKotlinException())
                }
            )
            cont.invokeOnCancellation { controller.abort() }
        }

    private fun Throwable.asKotlinException(): Exception {
        val message: String = asDynamic().message
        return PlainTextException(message)
    }
}

val credentials: CredentialServices get() = js("(navigator.credentials)")

external class CredentialServices {
    fun create(options: dynamic): Promise<PublicKeyCredential>
    fun get(options: dynamic): Promise<PublicKeyCredential>
}

external class PublicKeyCredential {
    fun toJSON(): dynamic
    companion object {
        fun isConditionalMediationAvailable(): Promise<Boolean>
        fun isUserVerifyingPlatformAuthenticatorAvailable(): Promise<Boolean>
    }
}

external class AbortController {
    fun abort()
}
