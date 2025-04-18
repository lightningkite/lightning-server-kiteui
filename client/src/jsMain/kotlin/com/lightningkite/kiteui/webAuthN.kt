package com.lightningkite.kiteui

import com.lightningkite.kiteui.exceptions.PlainTextException
import com.lightningkite.lightningserver.auth.proof.AssertedPublicKeyCredential
import com.lightningkite.lightningserver.auth.proof.AttestedPublicKeyCredential
import com.lightningkite.lightningserver.auth.proof.Transport
import com.lightningkite.lightningserver.auth.proof.WebAuthNDecoder
import com.lightningkite.lightningserver.auth.proof.WebAuthNEncoder
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
                authenticatorSelection = object : AuthenticatorSelection {}.apply {
                    authenticatorAttachment = request.authenticatorSelection.authenticatorAttachment?.jsonName
                    residentKey = request.authenticatorSelection.residentKey.jsonName
                    userVerification = request.authenticatorSelection.userVerification.jsonName
                }


                challenge = request.challenge.encodeToByteArray()
                excludeCredentials = request.excludeCredentials.map {
                    object : ExistingCredential {}.apply {
                        id = Base64.WebAuthNDecoder.decode(it.id)
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
                            clientExtensionResults = result.clientExtensionResults ?: emptyMap(),
                            id = result.id!!,
                            response = com.lightningkite.lightningserver.auth.proof.AuthenticatorAttestationResponse(
                                attestationObject = Base64.WebAuthNEncoder.encode(Int8Array(typedResponse.attestationObject!!).unsafeCast<ByteArray>()),
                                authenticatorData = Base64.WebAuthNEncoder.encode(Int8Array(typedResponse.getAuthenticatorData()).unsafeCast<ByteArray>()),
                                clientDataJSON = Base64.WebAuthNEncoder.encode(Int8Array(typedResponse.clientDataJSON!!).unsafeCast<ByteArray>()),
                                publicKey = Base64.WebAuthNEncoder.encode(Int8Array(typedResponse.getPublicKey()).unsafeCast<ByteArray>()),
                                publicKeyAlgorithm = typedResponse.getPublicKeyAlgorithm(),
                                transports = typedResponse.getTransports()
                                    .map { outer -> Transport.entries.find { it.jsonName == outer }!! },
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

    @OptIn(ExperimentalEncodingApi::class)
    actual suspend fun getWebAuthNCredentials(
        request: com.lightningkite.lightningserver.auth.proof.PublicKeyCredentialRequestOptions,
        mediation: WebAuthNMediationType,
    ): AssertedPublicKeyCredential =
        suspendCancellableCoroutine<AssertedPublicKeyCredential> { cont ->

            val controller = AbortController()
            val options: GetOptions = object : GetOptions {}

            val publicKey = object : PublicKeyCredentialRequestOptions {}.apply {
                allowCredentials = request.allowCredentials.map {
                    object : ExistingCredential {}.apply {
                        id = Base64.WebAuthNDecoder.decode(it.id)
                        transports = it.transports.map { it.jsonName }.toTypedArray()
                        type = it.type
                    }
                }.toTypedArray()
                challenge = request.challenge.encodeToByteArray()
                extensions = object {}
                rp = request.rpId
                timeout = request.timeout
                userVerification = request.userVerification.jsonName
            }

            options.publicKey = publicKey
            options.signal = controller.signal
            options.mediation = mediation.jsName

            getCredentials()
                .get(options)
                .then(
                    onFulfilled = { result: PublicKeyCredential ->

                        val typedResponse = result.response.unsafeCast<AuthenticatorAssertionResponse>()
                        val output = AssertedPublicKeyCredential(
                            id = result.id!!,
                            response = com.lightningkite.lightningserver.auth.proof.AuthenticatorAssertionResponse(
                                authenticatorData = Base64.WebAuthNEncoder.encode(Int8Array(typedResponse.authenticatorData!!).unsafeCast<ByteArray>()),
                                clientDataJSON = Base64.WebAuthNEncoder.encode(Int8Array(typedResponse.clientDataJSON!!).unsafeCast<ByteArray>()),
                                signature = Base64.WebAuthNEncoder.encode(Int8Array(typedResponse.signature!!).unsafeCast<ByteArray>()),
                                userHandle = typedResponse.userHandle?.let { handle ->
                                    Base64.WebAuthNEncoder.encode(
                                        Int8Array(handle).unsafeCast<ByteArray>()
                                    )
                                },
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

    private fun Throwable.asKotlinException(): Exception {
        val message: String = asDynamic().message
        return PlainTextException(message)
    }
}

fun getCredentials(): CredentialContainer = js("(navigator.credentials)")
