package com.lightningkite.kiteui

import com.lightningkite.lightningserver.auth.proof.WebAuthN
import com.lightningkite.kiteui.exceptions.PlainTextException
import com.lightningkite.readable.Readable
import com.lightningkite.readable.sharedProcess
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.ExperimentalSerializationApi
import org.khronos.webgl.Int8Array
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi


@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
@OptIn(ExperimentalSerializationApi::class)
actual class ClientAuthenticator {

    actual companion object {
        private val default = ClientAuthenticator()
        actual fun getClientAuthenticator(): ClientAuthenticator = default
    }

    fun getCredentials(): CredentialContainer = js("(navigator.credentials)")

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
    actual suspend fun createWebAuthNCredentials(request: WebAuthN.Registration.PublicKeyCredentialCreationOptions): WebAuthN.Registration.AttestedPublicKeyCredential =
        suspendCancellableCoroutine<WebAuthN.Registration.AttestedPublicKeyCredential> { cont ->

            val controller = AbortController()
            val options: CreateOptions = object : CreateOptions {}

            val publicKey = object : PublicKeyCredentialCreationOptions {}.apply {
                attestation = request.attestation.standardName
                attestationFormats = request.attestationFormats.toTypedArray()
                authenticatorSelection = object : AuthenticatorSelection {}.apply {
                    authenticatorAttachment = request.authenticatorSelection.authenticatorAttachment?.standardName
                    residentKey = request.authenticatorSelection.residentKey.standardName
                    userVerification = request.authenticatorSelection.userVerification.standardName
                }


                challenge = request.challenge.encodeToByteArray()
                excludeCredentials = request.excludeCredentials.map {
                    object : ExistingCredential {}.apply {
                        id = WebAuthN.base64Decoder.decode(it.id)
                        transports = it.transports.map { it.standardName }.toTypedArray()
                        type = it.type
                    }
                }.toTypedArray()
                extensions = object : Extensions {}.apply {
                    request.extensions.appidExclude
                        ?.also { appidExclude = it }
                    request.extensions.credProps
                        ?.also { credProps = it }
                    request.extensions.credentialProtectionPolicy
                        ?.also { credentialProtectionPolicy = it.standardName }
                    request.extensions.enforceCredentialProtectionPolicy
                        ?.also { enforceCredentialProtectionPolicy = it }
                    request.extensions.largeBlob
                        ?.also {
                            largeBlob = object : LargeBlob {}.apply {
                                support = it.support.standardName
                            }
                        }
                    request.extensions.minPinLength
                        ?.also { minPinLength = it }
                }

                hints = request.hints.map { it.standardName }.toTypedArray()
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
                        val output = WebAuthN.Registration.AttestedPublicKeyCredential(
                            authenticatorAttachment = result.authenticatorAttachment!!,
                            clientExtensionResults = result.getClientExtensionResults()?.let { extensions ->
                                WebAuthN.Registration.CreateExtensionResponse(
                                    appidExclude = extensions.appidExclude,
                                    credProps = extensions.credProps?.rk
                                        ?.let { WebAuthN.Registration.CredPropsResponse(it) },
                                    credProtect = extensions.credProtect,
                                    largeBlob = extensions.largeBlob?.supported
                                        ?.let { WebAuthN.Registration.LargeBlobResponse(it) },
                                    minPinLength = extensions.minPinLength?.toUInt()
                                )
                            },
                            id = result.id!!,
                            response = WebAuthN.Registration.AuthenticatorAttestationResponse(
                                attestationObject = WebAuthN.base64Encoder.encode(Int8Array(typedResponse.attestationObject!!).unsafeCast<ByteArray>()),
                                clientDataJSON = WebAuthN.base64Encoder.encode(Int8Array(typedResponse.clientDataJSON!!).unsafeCast<ByteArray>()),
                                transports = typedResponse.getTransports()
                                    .map { WebAuthN.Transport.fromStandardName(it) },
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
        request: WebAuthN.Authentication.PublicKeyCredentialRequestOptions,
        mediation: WebAuthNMediationType,
    ): WebAuthN.Authentication.AssertedPublicKeyCredential =
        suspendCancellableCoroutine<WebAuthN.Authentication.AssertedPublicKeyCredential> { cont ->

            val controller = AbortController()
            val options: GetOptions = object : GetOptions {}

            val publicKey = object : PublicKeyCredentialRequestOptions {}.apply {
                allowCredentials = request.allowCredentials.map {
                    object : ExistingCredential {}.apply {
                        id = WebAuthN.base64Decoder.decode(it.id)
                        transports = it.transports.map { it.standardName }.toTypedArray()
                        type = it.type
                    }
                }.toTypedArray()
                challenge = request.challenge.encodeToByteArray()
                extensions = object : Extensions {}.apply {
                    request.extensions.appid
                        ?.also { appid = it }
                    request.extensions.largeBlob
                        ?.also {
                            largeBlob = object : LargeBlob {}.apply {
                                read = it.read
                                write = it.write?.encodeToByteArray()
                            }
                        }
                }
                rp = request.rpId
                timeout = request.timeout
                userVerification = request.userVerification.standardName
            }

            options.publicKey = publicKey
            options.signal = controller.signal
            options.mediation = mediation.standardName

            getCredentials()
                .get(options)
                .then(
                    onFulfilled = { result: PublicKeyCredential ->

                        val typedResponse = result.response.unsafeCast<AuthenticatorAssertionResponse>()
                        val output = WebAuthN.Authentication.AssertedPublicKeyCredential(
                            id = result.id!!,

                            clientExtensionResults = result.getClientExtensionResults()?.let { extensions ->
                                WebAuthN.Authentication.RequestExtensionsResponse(
                                    appid = extensions.appid,
                                    largeBlob = extensions.largeBlob
                                        ?.let {
                                            WebAuthN.Authentication.LargeBlobResponse(
                                                it.written!!,
                                                it.blob
                                                    ?.let { WebAuthN.base64Encoder.encode(Int8Array(it).unsafeCast<ByteArray>()) },
                                            )
                                        },
                                )
                            },
                            response = WebAuthN.Authentication.AuthenticatorAssertionResponse(
                                authenticatorData = WebAuthN.base64Encoder.encode(Int8Array(typedResponse.authenticatorData!!).unsafeCast<ByteArray>()),
                                clientDataJSON = WebAuthN.base64Encoder.encode(Int8Array(typedResponse.clientDataJSON!!).unsafeCast<ByteArray>()),
                                signature = WebAuthN.base64Encoder.encode(Int8Array(typedResponse.signature!!).unsafeCast<ByteArray>()),
                                userHandle = typedResponse.userHandle?.let { handle ->
                                    WebAuthN.base64Encoder.encode(
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

