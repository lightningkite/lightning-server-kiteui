package com.lightningkite.kiteui

import com.lightningkite.kiteui.exceptions.PlainTextException
import com.lightningkite.lightningserver.auth.proof.AssertedPublicKeyCredential
import com.lightningkite.lightningserver.auth.proof.AttestedPublicKeyCredential
import com.lightningkite.lightningserver.auth.proof.Transport
import com.lightningkite.readable.Readable
import com.lightningkite.readable.sharedProcess
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.ExperimentalSerializationApi
import org.khronos.webgl.Int8Array
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

@OptIn(ExperimentalSerializationApi::class)
actual object ClientAuthenticator {

    private val webauthnAPIAvailable: Boolean
        get() = js("(window.PublicKeyCredential && PublicKeyCredential.isConditionalMediationAvailable)")

    actual val webAuthNAvailable: Readable<Boolean> = sharedProcess {
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

    @OptIn(ExperimentalEncodingApi::class)
    actual suspend fun createWebAuthNCredentials(request: com.lightningkite.lightningserver.auth.proof.PublicKeyCredentialCreationOptions): AttestedPublicKeyCredential =
        suspendCancellableCoroutine<AttestedPublicKeyCredential> { cont ->

            val controller = AbortController()
            val options: CreateOptions = object : CreateOptions {}

            val publicKey = object : PublicKeyCredentialCreationOptions {}.apply {
                attestation = request.attestation.jsonName
                attestationFormats = request.attestationFormats.toTypedArray()
                authenticatorSelection = request.authenticatorSelection?.let {
                    object : AuthenticatorSelection {}.apply {
                        authenticatorAttachment = it.authenticatorAttachment?.jsonName
                        residentKey = it.residentKey.jsonName
                        userVerification = it.userVerification.jsonName
                    }
                }

                challenge = request.challenge.encodeToByteArray()
                excludeCredentials = request.excludeCredentials.map {
                    object : ExistingCredential {}.apply {
                        id = it.id
                        transports = it.transports.map { it.jsonName }.toTypedArray()
                        type = it.type
                    }
                }.toTypedArray()
                extensions = object {}
                request.extensions.entries.forEach {
                    extensions[it.key] = it.value
                }

                hints = request.hints.map { it.jsonName }.toTypedArray()
                pubKeyCredParams = request.pubKeyCredParams.map {
                    object : PublicKeyCredentialParameters {}.apply {
                        alg = it.alg.coseAlgorithmId
                        type = it.type
                    }
                }.toTypedArray()
                rp = object : PublicKeyCredentialRpEntity {}.apply {
                    id = request.rp.id
                    name = request.rp.name
                }
                timeout = request.timeout
                user = object : PublicKeyCredentialUserEntity {}.apply {
                    id = request.user.id.encodeToByteArray()
                    name = request.user.name
                    displayName = request.user.displayName
                }
            }

            options.publicKey = publicKey
            options.signal = controller.signal

            getCredentials()
                .create(options)
                .then(
                    onFulfilled = { result: PublicKeyCredential ->
                        val typedResponse = result.response.unsafeCast<AuthenticatorAttestationResponse>()
                        val output = AttestedPublicKeyCredential(
                            authenticatorAttachment = result.authenticatorAttachment!!,
                            clientExtensionResults = result.clientExtensionResults,
                            id = result.id!!,
                            response = com.lightningkite.lightningserver.auth.proof.AuthenticatorAttestationResponse(
                                attestationObject = Base64.encode(Int8Array(typedResponse.attestationObject!!).unsafeCast<ByteArray>()),
                                authenticatorData = Base64.encode(Int8Array(typedResponse.getAuthenticatorData()).unsafeCast<ByteArray>()),
                                clientDataJSON = Base64.encode(Int8Array(typedResponse.clientDataJSON!!).unsafeCast<ByteArray>()),
                                publicKey = Base64.encode(Int8Array(typedResponse.getPublicKey()).unsafeCast<ByteArray>()),
                                publicKeyAlgorithm = typedResponse.getPublicKeyAlgorithm(),
                                transports = typedResponse.getTransports().map { outer -> Transport.entries.find { it.jsonName == outer }!! },
                            )
                        )
                        cont.resume(output)
                    },
                    onRejected = { error ->
                        cont.resumeWithException(error.asKotlinException())
                    }
                )
            cont.invokeOnCancellation { controller.abort() }
        }

    actual suspend fun getWebAuthNCredentials(
        request: com.lightningkite.lightningserver.auth.proof.PublicKeyCredentialRequestOptions,
        mediation: WebAuthNMediationType,
    ): AssertedPublicKeyCredential =
        suspendCancellableCoroutine<AssertedPublicKeyCredential> { cont ->

            val controller = AbortController()
            val options = GetOptions()

            options.publicKey = request
            options.signal = controller.signal
            options.mediation = mediation.jsName

            getCredentials()
                .get(options)
                .then(
                    onFulfilled = { result: PublicKeyCredential ->
                        cont.resume(
                            AssertedPublicKeyCredential(
                                result.id!!,
                                result.response
                            )
                        )
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

fun getCredentials(): CredentialContainer = js("(navigator.credentials)")
