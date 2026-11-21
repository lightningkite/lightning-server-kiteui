@file:OptIn(ExperimentalForeignApi::class, ExperimentalEncodingApi::class)

package com.lightningkite.kiteui

import com.lightningkite.lightningserver.sessions.proofs.WebAuthN
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import platform.AuthenticationServices.ASAuthorization
import platform.AuthenticationServices.ASAuthorizationController
import platform.AuthenticationServices.ASAuthorizationControllerDelegateProtocol
import platform.AuthenticationServices.ASAuthorizationControllerPresentationContextProvidingProtocol
import platform.AuthenticationServices.ASAuthorizationControllerRequestOptionPreferImmediatelyAvailableCredentials
import platform.AuthenticationServices.ASAuthorizationErrorCanceled
import platform.AuthenticationServices.ASAuthorizationErrorDomain
import platform.AuthenticationServices.ASAuthorizationErrorMatchedExcludedCredential
import platform.AuthenticationServices.ASAuthorizationErrorNotInteractive
import platform.AuthenticationServices.ASAuthorizationPlatformPublicKeyCredentialAssertion
import platform.AuthenticationServices.ASAuthorizationPlatformPublicKeyCredentialDescriptor
import platform.AuthenticationServices.ASAuthorizationPlatformPublicKeyCredentialProvider
import platform.AuthenticationServices.ASAuthorizationPlatformPublicKeyCredentialRegistration
import platform.AuthenticationServices.ASAuthorizationPublicKeyCredentialAssertionProtocol
import platform.AuthenticationServices.ASAuthorizationPublicKeyCredentialAttachment
import platform.AuthenticationServices.ASAuthorizationPublicKeyCredentialLargeBlobAssertionInput
import platform.AuthenticationServices.ASAuthorizationPublicKeyCredentialLargeBlobAssertionOperation
import platform.AuthenticationServices.ASAuthorizationPublicKeyCredentialLargeBlobRegistrationInput
import platform.AuthenticationServices.ASAuthorizationPublicKeyCredentialLargeBlobSupportRequirement
import platform.AuthenticationServices.ASAuthorizationPublicKeyCredentialParameters
import platform.AuthenticationServices.ASAuthorizationPublicKeyCredentialRegistrationProtocol
import platform.AuthenticationServices.ASAuthorizationRequest
import platform.AuthenticationServices.ASAuthorizationSecurityKeyPublicKeyCredentialAssertionRequest
import platform.AuthenticationServices.ASAuthorizationSecurityKeyPublicKeyCredentialDescriptor
import platform.AuthenticationServices.ASAuthorizationSecurityKeyPublicKeyCredentialProvider
import platform.AuthenticationServices.ASAuthorizationSecurityKeyPublicKeyCredentialRegistration
import platform.AuthenticationServices.ASAuthorizationSecurityKeyPublicKeyCredentialRegistrationRequest
import platform.AuthenticationServices.ASAuthorizationWebBrowserPlatformPublicKeyCredentialRegistrationRequestProtocol
import platform.AuthenticationServices.ASPresentationAnchor
import platform.Foundation.NSData
import platform.Foundation.NSError
import platform.UIKit.UIApplication
import platform.UIKit.UIDevice
import platform.UIKit.UIWindow
import platform.darwin.NSObject
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * Runs WebAuthn ceremonies through Apple's AuthenticationServices.
 *
 * Each ceremony offers both kinds of authenticator unless the server's options rule one out: platform passkeys,
 * which sync through iCloud Keychain and use Face ID or Touch ID and can also come from a nearby phone, and FIDO2
 * security keys over NFC, USB-C or Lightning.
 *
 * This needs iOS 15 or later, so [webAuthNAvailable] returns false on older versions. The app needs a
 * `webcredentials:<rpId>` Associated Domains entitlement, and the rpId domain must serve an
 * apple-app-site-association file listing the app. The server must also accept the `https://<rpId>` origin.
 *
 * Extensions are handled as Safari handles them: `credProps`, and from iOS 17 `largeBlob` for passkeys. Others are
 * ignored, as a browser ignores extensions it doesn't support.
 *
 * Where AuthenticationServices can't do what a browser does:
 * - Before iOS 17.4, `excludeCredentials` only reaches security keys, and iCloud Keychain replaces an existing
 *   passkey for the same rpId and user id instead of refusing.
 * - Before iOS 16, the request's `timeout` is ignored, since there's no way to dismiss the system sheet when it runs
 *   out.
 * - With a `cross-platform` attachment, a phone over hybrid isn't offered, since it can only be offered alongside
 *   iCloud Keychain passkeys, which are `platform`.
 * - A missing Associated Domains setup is reported as a general failure, with Apple's description appended, since
 *   it can't be told apart from other failures.
 *
 * Passkey autofill is not supported yet, so [autofillAvailable] always returns false.
 */
@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
public actual class ClientAuthenticator {

    public actual companion object {
        private val default = ClientAuthenticator()
        public actual fun getClientAuthenticator(): ClientAuthenticator = default
    }

    public actual suspend fun webAuthNAvailable(): Boolean = iosVersionAtLeast(15)

    public actual suspend fun autofillAvailable(): Boolean = false

    public actual suspend fun createWebAuthNCredentials(request: WebAuthN.Registration.PublicKeyCredentialCreationOptions): WebAuthN.Registration.AttestedPublicKeyCredential {
        requireSupported()
        // The server compares challenges and user ids as the UTF-8 bytes of their strings, as the JS client sends them.
        val challenge = request.challenge.encodeToByteArray().toNSData()
        val userId = request.user.id.encodeToByteArray().toNSData()
        val attachment = request.authenticatorSelection.authenticatorAttachment
        // Before iOS 17 the extension is ignored, as a browser ignores one it doesn't support.
        val largeBlob = request.extensions.largeBlob?.support?.takeIf { iosVersionAtLeast(17) }
        val usePlatform = attachment != WebAuthN.AuthenticatorAttachment.CrossPlatform
        // A browser leaves out authenticators that can't meet a required largeBlob, and security keys can't here.
        val useSecurityKey = attachment != WebAuthN.AuthenticatorAttachment.Platform &&
                largeBlob != WebAuthN.Registration.LargeBlobPreference.Required

        val requests = buildList<ASAuthorizationRequest> {
            if (usePlatform) {
                val r = ASAuthorizationPlatformPublicKeyCredentialProvider(relyingPartyIdentifier = request.rp.id)
                    .createCredentialRegistrationRequestWithChallenge(challenge = challenge, name = request.user.name, userID = userId)
                r.displayName = request.user.displayName
                r.userVerificationPreference = request.authenticatorSelection.userVerification.standardName
                r.attestationPreference = request.attestation.standardName
                // iOS 17.4 added excludedCredentials here, but Kotlin's bindings leave out the class's conformance to
                // the protocol that declares it, so it is set through a cast that checks conformance at runtime.
                if (iosVersionAtLeast(17, 4) && request.excludeCredentials.isNotEmpty())
                    (r as? ASAuthorizationWebBrowserPlatformPublicKeyCredentialRegistrationRequestProtocol)
                        ?.excludedCredentials = request.excludeCredentials.map { it.toPlatformDescriptor() }
                largeBlob?.let { r.largeBlob = ASAuthorizationPublicKeyCredentialLargeBlobRegistrationInput(it.toSupportRequirement()) }
                add(r)
            }
            if (useSecurityKey) {
                val r = ASAuthorizationSecurityKeyPublicKeyCredentialProvider(relyingPartyIdentifier = request.rp.id)
                    .createCredentialRegistrationRequestWithChallenge(
                        challenge = challenge,
                        displayName = request.user.displayName,
                        name = request.user.name,
                        userID = userId,
                    )
                // Apple only supports ES256 for security keys, so offering the others would only risk a failure.
                val es256 = request.pubKeyCredParams.filter { it.alg == WebAuthN.PublicKeyAlgorithm.ES256 }
                r.credentialParameters = es256.ifEmpty { request.pubKeyCredParams }
                    .map { ASAuthorizationPublicKeyCredentialParameters(algorithm = it.alg.coseAlgorithmId.toLong()) }
                r.residentKeyPreference = request.authenticatorSelection.residentKey.standardName
                r.userVerificationPreference = request.authenticatorSelection.userVerification.standardName
                r.attestationPreference = request.attestation.standardName
                r.excludedCredentials = request.excludeCredentials.map { it.toSecurityKeyDescriptor() }
                add(r)
            }
        }.inHintOrder(request.hints)

        if (requests.isEmpty()) throw WebAuthNFailure.NotSupported.exception()

        val credential = withWebAuthNTimeout(ceremonyTimeout(request.timeout)) { performAuthorization(requests) }.credential
        if (credential !is ASAuthorizationPublicKeyCredentialRegistrationProtocol)
            throw WebAuthNFailure.Failed.exception("The authenticator did not return a new public key credential.")
        val attestationObject = credential.rawAttestationObject
            ?: throw WebAuthNFailure.Failed.exception("The authenticator did not return an attestation object.")

        val crossPlatform: Boolean
        val transports: List<WebAuthN.Transport>
        val residentKey: Boolean?
        val largeBlobSupported: Boolean?
        when (credential) {
            is ASAuthorizationPlatformPublicKeyCredentialRegistration -> {
                // A passkey made on a nearby phone is reported as cross-platform from iOS 16.6.
                crossPlatform = iosVersionAtLeast(16, 6) &&
                        credential.attachment == ASAuthorizationPublicKeyCredentialAttachment.ASAuthorizationPublicKeyCredentialAttachmentCrossPlatform
                transports = if (crossPlatform) listOf(WebAuthN.Transport.Hybrid)
                else listOf(WebAuthN.Transport.Internal, WebAuthN.Transport.Hybrid)
                // Platform passkeys are always discoverable.
                residentKey = true
                largeBlobSupported = largeBlob?.let { credential.largeBlob?.isSupported ?: false }
            }
            is ASAuthorizationSecurityKeyPublicKeyCredentialRegistration -> {
                crossPlatform = true
                transports = if (iosVersionAtLeast(17, 5)) credential.transports.map { it as? String }.toWebAuthNTransports()
                else emptyList()
                // A security key refuses to register when residency is required but it can't provide it.
                residentKey = true.takeIf { request.authenticatorSelection.residentKey == WebAuthN.GeneralPreference.Required }
                largeBlobSupported = largeBlob?.let { false }
            }
            else -> throw WebAuthNFailure.Failed.exception("The authenticator returned an unknown kind of credential.")
        }

        return WebAuthN.Registration.AttestedPublicKeyCredential(
            authenticatorAttachment = if (crossPlatform) WebAuthN.AuthenticatorAttachment.CrossPlatform.standardName
            else WebAuthN.AuthenticatorAttachment.Platform.standardName,
            clientExtensionResults = WebAuthN.Registration.CreateExtensionResponse(
                // Reported only when asked for, as a browser does.
                credProps = residentKey?.takeIf { request.extensions.credProps == true }
                    ?.let { WebAuthN.Registration.CredPropsResponse(it) },
                largeBlob = largeBlobSupported?.let { WebAuthN.Registration.LargeBlobResponse(it) },
            ),
            id = credential.credentialID.toBase64Url(),
            response = WebAuthN.Registration.AuthenticatorAttestationResponse(
                attestationObject = attestationObject.toBase64Url(),
                clientDataJSON = credential.rawClientDataJSON.toBase64Url(),
                transports = transports,
            ),
        )
    }

    public actual suspend fun getWebAuthNCredentials(
        request: WebAuthN.Authentication.PublicKeyCredentialRequestOptions,
        mediation: WebAuthNMediationType,
    ): WebAuthN.Authentication.AssertedPublicKeyCredential {
        requireSupported()
        // Passkey autofill isn't supported, which autofillAvailable reports, so callers don't get here.
        if (mediation == WebAuthNMediationType.Conditional) throw WebAuthNFailure.NotSupported.exception()
        request.extensions.requireValidLargeBlob()
        val challenge = request.challenge.encodeToByteArray().toNSData()
        // Before iOS 17 the extension is ignored, as a browser ignores one it doesn't support.
        val largeBlob = request.extensions.largeBlob?.takeIf { iosVersionAtLeast(17) }?.toAssertionInput()

        val requests = buildList<ASAuthorizationRequest> {
            val platform = ASAuthorizationPlatformPublicKeyCredentialProvider(relyingPartyIdentifier = request.rpId)
                .createCredentialAssertionRequestWithChallenge(challenge)
            platform.allowedCredentials = request.allowCredentials.map { it.toPlatformDescriptor() }
            platform.userVerificationPreference = request.userVerification.standardName
            largeBlob?.let { platform.largeBlob = it }
            add(platform)

            val securityKey = ASAuthorizationSecurityKeyPublicKeyCredentialProvider(relyingPartyIdentifier = request.rpId)
                .createCredentialAssertionRequestWithChallenge(challenge)
            securityKey.allowedCredentials = request.allowCredentials.map { it.toSecurityKeyDescriptor() }
            securityKey.userVerificationPreference = request.userVerification.standardName
            add(securityKey)
        }.inHintOrder(request.hints)

        val credential = withWebAuthNTimeout(ceremonyTimeout(request.timeout)) {
            performAuthorization(requests, preferImmediatelyAvailable = mediation == WebAuthNMediationType.Silent)
        }.credential
        if (credential !is ASAuthorizationPublicKeyCredentialAssertionProtocol)
            throw WebAuthNFailure.Failed.exception("The authenticator did not return a signed assertion.")
        // Declared non-null, but empty or missing for a security key's non-discoverable credential.
        val userHandle: NSData? = credential.userID
        // A security key has no largeBlob output, which a browser reports as nothing written or read.
        val largeBlobOutput = (credential as? ASAuthorizationPlatformPublicKeyCredentialAssertion)
            ?.takeIf { largeBlob != null }?.largeBlob
        return WebAuthN.Authentication.AssertedPublicKeyCredential(
            id = credential.credentialID.toBase64Url(),
            clientExtensionResults = WebAuthN.Authentication.RequestExtensionsResponse(
                largeBlob = largeBlob?.let {
                    WebAuthN.Authentication.LargeBlobResponse(
                        written = largeBlobOutput?.didWrite ?: false,
                        blob = largeBlobOutput?.readData?.toBase64Url(),
                    )
                },
            ),
            response = WebAuthN.Authentication.AuthenticatorAssertionResponse(
                authenticatorData = (credential.rawAuthenticatorData
                    ?: throw WebAuthNFailure.Failed.exception("The authenticator did not return authenticator data.")).toBase64Url(),
                clientDataJSON = credential.rawClientDataJSON.toBase64Url(),
                signature = (credential.signature
                    ?: throw WebAuthNFailure.Failed.exception("The authenticator did not return a signature.")).toBase64Url(),
                userHandle = userHandle?.takeIf { it.length > 0u }?.toBase64Url(),
            ),
        )
    }

    private fun requireSupported() {
        if (!iosVersionAtLeast(15)) throw WebAuthNFailure.NotSupported.exception()
    }

    /**
     * Returns the timeout to enforce, or null before iOS 16, where the sheet can't be dismissed. Timing out there would
     * fail the call while the sheet stays up, and a retry would present a second one over it.
     */
    private fun ceremonyTimeout(timeoutMillis: Int?): Int? = timeoutMillis?.takeIf { iosVersionAtLeast(16) }

    /**
     * Shows the system sheet for [requests]. With [preferImmediatelyAvailable], which Android also uses for silent
     * mediation, it fails instead of offering credentials that aren't on the device. That needs iOS 16.
     *
     * Runs on the main thread whatever the caller's dispatcher, since AuthenticationServices requires it and the
     * delegate's callbacks, which also touch [inFlight], arrive there.
     */
    private suspend fun performAuthorization(
        requests: List<ASAuthorizationRequest>,
        preferImmediatelyAvailable: Boolean = false,
    ): ASAuthorization = withContext(Dispatchers.Main) {
        suspendCancellableCoroutine { cont ->
            val controller = ASAuthorizationController(authorizationRequests = requests)
            lateinit var delegate: AuthorizationDelegate
            delegate = AuthorizationDelegate { result ->
                inFlight.remove(delegate)
                if (cont.isActive) result.fold(onSuccess = { cont.resume(it) }, onFailure = { cont.resumeWithException(it) })
            }
            // The controller holds its delegate weakly, so keep it here until the ceremony ends.
            inFlight.add(delegate)
            controller.delegate = delegate
            controller.presentationContextProvider = delegate
            if (preferImmediatelyAvailable && iosVersionAtLeast(16))
                controller.performRequestsWithOptions(ASAuthorizationControllerRequestOptionPreferImmediatelyAvailableCredentials)
            else controller.performRequests()
            cont.invokeOnCancellation {
                dispatch_async(dispatch_get_main_queue()) {
                    // Before iOS 16 there's no way to dismiss the sheet, which is why no timeout is enforced there. The
                    // delegate is released when the user dismisses it.
                    if (iosVersionAtLeast(16)) controller.cancel()
                }
            }
        }
    }

    /** Only touched on the main thread. */
    private val inFlight = mutableSetOf<AuthorizationDelegate>()
}

private class AuthorizationDelegate(
    private val onResult: (Result<ASAuthorization>) -> Unit,
) : NSObject(), ASAuthorizationControllerDelegateProtocol, ASAuthorizationControllerPresentationContextProvidingProtocol {

    override fun authorizationController(controller: ASAuthorizationController, didCompleteWithAuthorization: ASAuthorization) {
        onResult(Result.success(didCompleteWithAuthorization))
    }

    override fun authorizationController(controller: ASAuthorizationController, didCompleteWithError: NSError) {
        onResult(Result.failure(didCompleteWithError.toWebAuthNException()))
    }

    override fun presentationAnchorForAuthorizationController(controller: ASAuthorizationController): ASPresentationAnchor {
        val app = UIApplication.sharedApplication
        return app.keyWindow ?: app.windows.firstOrNull() as? UIWindow ?: UIWindow()
    }
}

/**
 * Maps AuthenticationServices errors onto the failure kinds every platform reports. Cancelling, and having nothing to
 * offer with [ASAuthorizationControllerRequestOptionPreferImmediatelyAvailableCredentials], are NotAllowedError in a
 * browser.
 */
private fun NSError.toWebAuthNException() = when {
    domain != ASAuthorizationErrorDomain -> WebAuthNFailure.Failed
    code == ASAuthorizationErrorCanceled || code == ASAuthorizationErrorNotInteractive -> WebAuthNFailure.NotAllowed
    code == ASAuthorizationErrorMatchedExcludedCredential -> WebAuthNFailure.AlreadyRegistered
    else -> WebAuthNFailure.Failed
}.exception(localizedDescription)

// Unpadded like a browser's, which matters for the credential id since the server stores and looks it up as a string.
private fun NSData.toBase64Url(): String = webAuthNBase64.encode(toByteArray())

private fun WebAuthN.Registration.LargeBlobPreference.toSupportRequirement() = when (this) {
    WebAuthN.Registration.LargeBlobPreference.Required ->
        ASAuthorizationPublicKeyCredentialLargeBlobSupportRequirement.ASAuthorizationPublicKeyCredentialLargeBlobSupportRequirementRequired
    WebAuthN.Registration.LargeBlobPreference.Preferred ->
        ASAuthorizationPublicKeyCredentialLargeBlobSupportRequirement.ASAuthorizationPublicKeyCredentialLargeBlobSupportRequirementPreferred
}

/** Returns the input for a read or a write, or null if neither is asked for. Asking for both is rejected earlier. */
private fun WebAuthN.Authentication.LargeBlob.toAssertionInput(): ASAuthorizationPublicKeyCredentialLargeBlobAssertionInput? {
    val write = write
    return when {
        read == true -> ASAuthorizationPublicKeyCredentialLargeBlobAssertionInput(
            ASAuthorizationPublicKeyCredentialLargeBlobAssertionOperation.ASAuthorizationPublicKeyCredentialLargeBlobAssertionOperationRead
        )
        write != null -> ASAuthorizationPublicKeyCredentialLargeBlobAssertionInput(
            ASAuthorizationPublicKeyCredentialLargeBlobAssertionOperation.ASAuthorizationPublicKeyCredentialLargeBlobAssertionOperationWrite
        ).apply { dataToWrite = WebAuthN.base64Decoder.decode(write).toNSData() }
        else -> null
    }
}

/**
 * Puts the security key request first when the server's hints rank security keys above the platform, which offers
 * both device passkeys and a phone over hybrid. Hints are only preferences, as in a browser, so nothing is left out.
 */
private fun List<ASAuthorizationRequest>.inHintOrder(hints: List<WebAuthN.Hints>): List<ASAuthorizationRequest> {
    val securityKeyRank = hints.indexOf(WebAuthN.Hints.SecurityKey)
    val platformRank = hints.indexOfFirst { it == WebAuthN.Hints.ClientDevice || it == WebAuthN.Hints.Hybrid }
    val preferSecurityKey = securityKeyRank >= 0 && (platformRank < 0 || securityKeyRank < platformRank)
    return if (!preferSecurityKey) this
    else sortedBy {
        it !is ASAuthorizationSecurityKeyPublicKeyCredentialRegistrationRequest &&
                it !is ASAuthorizationSecurityKeyPublicKeyCredentialAssertionRequest
    }
}

private fun WebAuthN.ExistingCredential.toPlatformDescriptor() =
    ASAuthorizationPlatformPublicKeyCredentialDescriptor(credentialID = WebAuthN.base64Decoder.decode(id).toNSData())

private val securityKeyTransports = listOf(WebAuthN.Transport.USB, WebAuthN.Transport.NFC, WebAuthN.Transport.BLE)

private fun WebAuthN.ExistingCredential.toSecurityKeyDescriptor() = ASAuthorizationSecurityKeyPublicKeyCredentialDescriptor(
    credentialID = WebAuthN.base64Decoder.decode(id).toNSData(),
    transports = transports.filter { it in securityKeyTransports }.ifEmpty { securityKeyTransports }.map { it.standardName },
)

private fun iosVersionAtLeast(major: Int, minor: Int = 0): Boolean {
    val parts = UIDevice.currentDevice.systemVersion.split(".").map { it.toIntOrNull() ?: 0 }
    val actualMajor = parts.getOrElse(0) { 0 }
    val actualMinor = parts.getOrElse(1) { 0 }
    return actualMajor > major || (actualMajor == major && actualMinor >= minor)
}
