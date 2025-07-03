package com.lightningkite.kiteui

import com.lightningkite.lightningserver.auth.proof.WebAuthN
import com.lightningkite.readable.Constant
import com.lightningkite.readable.Readable


@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
actual class ClientAuthenticator {
    actual suspend fun webAuthNAvailable(): Boolean = false
    actual suspend fun autofillAvailable(): Boolean = false
    actual suspend fun createWebAuthNCredentials(request: WebAuthN.Registration.PublicKeyCredentialCreationOptions): WebAuthN.Registration.AttestedPublicKeyCredential = TODO()
    actual suspend fun getWebAuthNCredentials(request: WebAuthN.Authentication.PublicKeyCredentialRequestOptions, mediation: WebAuthNMediationType): WebAuthN.Authentication.AssertedPublicKeyCredential = TODO()

    actual companion object {
        actual fun getClientAuthenticator(): ClientAuthenticator = ClientAuthenticator()
    }
}

// THIS IS NOT COMPLETE. I don't like this implementation as it requires Google Play Services.
// As well I never completed a full test due to challenge validation and Activity Result work arounds.
// I did do prep work on ClientAuthenticator such as getClientAuthenticator for this android implementation
// and I have left it in.
//implementation("com.google.android.gms:play-services-fido:21.2.0")


//@OptIn(ExperimentalEncodingApi::class)
//@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
//actual class ClientAuthenticator private constructor(
//    val activity: KiteUiActivity,
//    ) {
//    var fidoClient: Fido2ApiClient = Fido2ApiClient(activity)
//
//    //    val authResult: LateInitProperty
//    var authenticatorHandler: ActivityResultLauncher<IntentSenderRequest?> = activity.registerForActivityResult(
//        ActivityResultContracts.StartIntentSenderForResult(),
//        { result: ActivityResult ->
//            println(result)
//            println(result.resultCode)
////            println(result.data?.)
//            if (result.resultCode == Activity.RESULT_OK) {
//                try {
//                    val bytes = result.data?.getByteArrayExtra(Fido.FIDO2_KEY_CREDENTIAL_EXTRA)
//                        ?: run {
////                    cont.resumeWithException(Exception("Failed to register Credentials"))
//                            return@registerForActivityResult
//                        }
//
//                    val publicKey = PublicKeyCredential.deserializeFromBytes(bytes)
//                    val response = publicKey.response
//                    if(response is AuthenticatorErrorResponse){
//                        return@registerForActivityResult
//                    }
//                    WebAuthN.Authentication.AssertedPublicKeyCredential(
//                        id = publicKey.id!!,
//                        clientExtensionResults = null,
//                        response = (response as? AuthenticatorAssertionResponse)!!.let { response ->
//                            WebAuthN.Authentication.AuthenticatorAssertionResponse(
//                                authenticatorData = WebAuthN.base64Encoder.encode(response.authenticatorData),
//                                clientDataJSON = WebAuthN.base64Encoder.encode(response.clientDataJSON),
//                                signature = WebAuthN.base64Encoder.encode(response.signature),
//                                userHandle = response.userHandle?.let { WebAuthN.base64Encoder.encode(it) }
//                            )
//                        }
//                    )
//                } catch (e: Exception) {
//                    e.printStackTrace()
//                }
//            }
//        }
//    )
//
//    var registerHandler: ActivityResultLauncher<IntentSenderRequest?> = activity.registerForActivityResult(
//        ActivityResultContracts.StartIntentSenderForResult(),
//        { result: ActivityResult ->
//            val bytes = result.data?.getByteArrayExtra(Fido.FIDO2_KEY_CREDENTIAL_EXTRA)
//                ?: run {
////                    cont.resumeWithException(Exception("Failed to register Credentials"))
//                    return@registerForActivityResult
//                }
//            val publicKey = PublicKeyCredential.deserializeFromBytes(bytes)
//
//            if(publicKey.response is AuthenticatorErrorResponse){
//
//                return@registerForActivityResult
//            }
//            WebAuthN.Registration.AttestedPublicKeyCredential(
//                authenticatorAttachment = publicKey.authenticatorAttachment!!,
//                id = publicKey.id!!,
//                clientExtensionResults = publicKey.clientExtensionResults?.let { extensions ->
//                    WebAuthN.Registration.CreateExtensionResponse(
//                        credProps = extensions.credProps?.isDiscoverableCredential
//                            ?.let { WebAuthN.Registration.CredPropsResponse(it) },
//                    )
//                },
//                response = (publicKey.response as? AuthenticatorAttestationResponse)!!.let { response ->
//                    WebAuthN.Registration.AuthenticatorAttestationResponse(
//                        attestationObject = WebAuthN.base64Encoder.encode(response.attestationObject),
//                        clientDataJSON = WebAuthN.base64Encoder.encode(response.clientDataJSON),
//                        transports = response.transports.map {
//                            WebAuthN.Transport.fromStandardName(
//                                it
//                            )
//                        },
//                    )
//                }
//            )
//        }
//    )
//
//
//    actual companion object {
//        private lateinit var default: ClientAuthenticator
//        fun establishAuthenticator(activity: KiteUiActivity) {
//            default = ClientAuthenticator(activity)
//        }
//
//        actual fun getClientAuthenticator(): ClientAuthenticator = default
//    }
//
//    actual val webAuthNAvailable: Readable<Boolean> = sharedSuspending {
//        val result = fidoClient.isUserVerifyingPlatformAuthenticatorAvailable
//
//        suspendCancellableCoroutine<Boolean> { cont ->
//            result.addOnSuccessListener { it: Boolean? ->
//                cont.resume(it ?: false)
//            }
//            result.addOnFailureListener {
//                cont.resumeWithException(it)
//            }
//        }
//    }
//    actual val autofillAvailable: Readable<Boolean> = Constant(false)
//
//    actual suspend fun createWebAuthNCredentials(request: WebAuthN.Registration.PublicKeyCredentialCreationOptions): WebAuthN.Registration.AttestedPublicKeyCredential {
//        val intent = fidoClient.getRegisterPendingIntent(
//            PublicKeyCredentialCreationOptions.Builder()
//                .apply {
//                    setAttestationConveyancePreference(AttestationConveyancePreference.fromString(request.attestation.standardName))
//                    setAttestationFormats(request.attestationFormats)
//                    setAuthenticatorSelection(
//                        AuthenticatorSelectionCriteria.Builder()
//                            .apply {
//                                request.authenticatorSelection.authenticatorAttachment
//                                    ?.also { setAttachment(Attachment.fromString(it.standardName)) }
//                                setResidentKeyRequirement(
//                                    ResidentKeyRequirement
//                                        .fromString(request.authenticatorSelection.residentKey.standardName)
//                                )
//                            }
//                            .build()
//                    )
//                    setChallenge(request.challenge.encodeToByteArray())
//                    setExcludeList(request.excludeCredentials.map { existing ->
//                        PublicKeyCredentialDescriptor(
//                            /* type = */ existing.type,
//                            /* id = */ WebAuthN.base64Decoder.decode(existing.id),
//                            /* transports = */ existing.transports.map { Transport.fromString(it.standardName) }
//                        )
//                    })
//                    setAuthenticationExtensions(
//                        AuthenticationExtensions.Builder()
//                            .apply {
//                                request.extensions.appidExclude
//                                    ?.also { setFido2Extension(FidoAppIdExtension(it)) }
//                                request.extensions.credProps
//                                    ?.also { setUserVerificationMethodExtension(UserVerificationMethodExtension(it)) }
//                            }
//                            .build())
//                    setParameters(
//                        request.pubKeyCredParams
//                            .map { PublicKeyCredentialParameters(it.type, it.alg.coseAlgorithmId) })
//                    setRp(PublicKeyCredentialRpEntity(request.rp.id, request.rp.name, null))
//                    setTimeoutSeconds(request.timeout?.toDouble())
//                    setUser(
//                        PublicKeyCredentialUserEntity(
//                            /* id = */ request.user.id.encodeToByteArray(),
//                            /* name = */ request.user.name,
//                            /* icon = */ null,
//                            /* displayName = */ request.user.displayName,
//                        )
//                    )
//                }
//                .build()
//        )
//
//        return suspendCancellableCoroutine { cont ->
//            intent.addOnSuccessListener { intent: PendingIntent? ->
//                if (intent != null) {
//                    registerHandler.launch(IntentSenderRequest.Builder(intent).build())
//                } else {
//                    cont.resumeWithException(Exception("Failed to launch Authentication"))
//                }
//            }
//            intent.addOnFailureListener {
//                cont.resumeWithException(it)
//            }
//        }
//    }
//
//    actual suspend fun getWebAuthNCredentials(
//        request: WebAuthN.Authentication.PublicKeyCredentialRequestOptions,
//        mediation: WebAuthNMediationType,
//    ): WebAuthN.Authentication.AssertedPublicKeyCredential {
//
//        val intent = fidoClient.getSignPendingIntent(
//            PublicKeyCredentialRequestOptions.Builder()
//                .apply {
//                    setAllowList(request.allowCredentials.map { existing ->
//                        PublicKeyCredentialDescriptor(
//                            /* type = */ existing.type,
//                            /* id = */ WebAuthN.base64Decoder.decode(existing.id),
//                            /* transports = */ existing.transports.map { Transport.fromString(it.standardName) }
//                        )
//                    })
//                    setChallenge(request.challenge.encodeToByteArray())
//                    setTimeoutSeconds(request.timeout?.toDouble())
//                    setRpId(request.rpId)
//                }
//                .build()
//        )
//
//        return suspendCancellableCoroutine { cont ->
//            intent.addOnSuccessListener { intent: PendingIntent? ->
//                if (intent != null) {
//                    try {
//                        authenticatorHandler.launch(IntentSenderRequest.Builder(intent).build())
//                    } catch (e: Exception) {
//                        cont.resumeWithException(e)
//                    }
//                }
//            }
//
//            intent.addOnFailureListener {
//                cont.resumeWithException(it)
//            }
//        }
//    }
//}
