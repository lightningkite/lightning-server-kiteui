// The platform files share this file's name, so its top-level functions need their own JVM class.
@file:kotlin.jvm.JvmName("WebAuthNCommonKt")

package com.lightningkite.kiteui

import com.lightningkite.kiteui.exceptions.PlainTextException
import com.lightningkite.lightningserver.sessions.proofs.WebAuthN
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.time.Duration.Companion.milliseconds

/**
 * Runs WebAuthn ceremonies on the current platform.
 *
 * Every platform reports results and failures in the same form. Binary values are unpadded base64url, and a failed
 * ceremony throws a [PlainTextException] whose message depends only on the kind of failure, never the platform.
 * As in browsers, a cancelled prompt, a timeout and having no usable credential are one kind of failure, so that
 * which credentials exist isn't revealed.
 */
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

/**
 * The kinds of failure each platform maps its own errors onto, each with one message used everywhere.
 *
 * These follow the DOMException names browsers use, since the JS client receives those directly.
 */
internal enum class WebAuthNFailure(val message: String) {
    /** `NotAllowedError`: the prompt was cancelled or timed out, or no credential could be used. */
    NotAllowed("The request was cancelled or timed out, or no passkey or security key could be used."),
    /** `InvalidStateError`: the authenticator already holds one of the excluded credentials. */
    AlreadyRegistered("This passkey or security key is already registered."),
    /** `SecurityError`: the app or site isn't associated with the relying party's domain. */
    Misconfigured("Passkeys aren't set up correctly for this app."),
    /** `NotSupportedError`: nothing available can satisfy the request. */
    NotSupported("This device doesn't support the passkey or security key this request needs."),
    /** Anything else. The platform's own description is appended, since there is no common one to give. */
    Failed("The passkey request failed."),
}

internal fun WebAuthNFailure.exception(detail: String? = null): PlainTextException = PlainTextException(
    if (this == WebAuthNFailure.Failed && !detail.isNullOrBlank()) "$message $detail" else message
)

/** Encodes binary WebAuthn output as unpadded base64url, as browsers encode credential ids. */
@OptIn(ExperimentalEncodingApi::class)
internal val webAuthNBase64: Base64 = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT)

/** Reads transport names, dropping any this library doesn't know, such as `smart-card`, rather than failing. */
internal fun Iterable<String?>.toWebAuthNTransports(): List<WebAuthN.Transport> =
    mapNotNull { name -> WebAuthN.Transport.entries.find { it.standardName == name } }

/**
 * Returns the reported authenticator attachment. Some browsers and credential providers don't report one, and then
 * it is inferred from the transports, since only a built-in authenticator uses the `internal` transport.
 */
internal fun webAuthNAttachment(reported: String?, transports: List<WebAuthN.Transport>): String = reported
    ?: if (WebAuthN.Transport.Internal in transports) WebAuthN.AuthenticatorAttachment.Platform.standardName
    else WebAuthN.AuthenticatorAttachment.CrossPlatform.standardName

/**
 * Enforces the request's timeout for platforms whose APIs don't, failing as [WebAuthNFailure.NotAllowed] like a
 * browser does when its timeout runs out.
 */
internal suspend fun <T : Any> withWebAuthNTimeout(timeoutMillis: Int?, ceremony: suspend () -> T): T =
    if (timeoutMillis == null || timeoutMillis <= 0) ceremony()
    else withTimeoutOrNull(timeoutMillis.milliseconds) { ceremony() } ?: throw WebAuthNFailure.NotAllowed.exception()

/** Rejects asking to read and write a large blob at once, as browsers do with `NotSupportedError`. */
internal fun WebAuthN.Authentication.RequestExtensions.requireValidLargeBlob() {
    val blob = largeBlob ?: return
    if (blob.read == true && blob.write != null) throw WebAuthNFailure.NotSupported.exception()
}
