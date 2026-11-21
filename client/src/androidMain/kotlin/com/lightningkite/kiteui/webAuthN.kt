package com.lightningkite.kiteui

import android.os.Build
import androidx.credentials.CreatePublicKeyCredentialRequest
import androidx.credentials.CreatePublicKeyCredentialResponse
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetPublicKeyCredentialOption
import androidx.credentials.PublicKeyCredential
import androidx.credentials.exceptions.CreateCredentialCancellationException
import androidx.credentials.exceptions.CreateCredentialException
import androidx.credentials.exceptions.CreateCredentialProviderConfigurationException
import androidx.credentials.exceptions.CreateCredentialUnsupportedException
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.GetCredentialProviderConfigurationException
import androidx.credentials.exceptions.GetCredentialUnsupportedException
import androidx.credentials.exceptions.NoCredentialException
import androidx.credentials.exceptions.domerrors.AbortError
import androidx.credentials.exceptions.domerrors.DomError
import androidx.credentials.exceptions.domerrors.InvalidStateError
import androidx.credentials.exceptions.domerrors.NotAllowedError
import androidx.credentials.exceptions.domerrors.NotSupportedError
import androidx.credentials.exceptions.domerrors.SecurityError
import androidx.credentials.exceptions.domerrors.TimeoutError
import androidx.credentials.exceptions.publickeycredential.CreatePublicKeyCredentialDomException
import androidx.credentials.exceptions.publickeycredential.GetPublicKeyCredentialDomException
import com.lightningkite.kiteui.views.AndroidAppContext
import com.lightningkite.lightningserver.sessions.proofs.WebAuthN
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * Runs WebAuthn ceremonies through Jetpack Credential Manager, which hands them to the user's credential providers.
 *
 * Which authenticators are offered depends on the providers installed. Google Play services covers synced passkeys
 * and FIDO2 security keys over USB and NFC, and third-party providers such as password managers or Yubico's
 * Passkey Enabler can be used too.
 *
 * On Android 14 and later Credential Manager is part of the system. On Android 9 to 13 it only works through Google
 * Play services, and the app must add `androidx.credentials:credentials-play-services-auth` itself. This library
 * leaves that dependency out so that apps not using WebAuthn don't pull in Play services.
 *
 * Credential Manager requires the app to be associated with the relying party's domain through a Digital Asset Links
 * file served at `https://<rpId>/.well-known/assetlinks.json`. The server must also accept the app's
 * `android:apk-key-hash:<hash>` origin, where `<hash>` is the SHA-256 of the app's signing certificate as unpadded
 * base64url, such as `android:apk-key-hash:_qfkn9LDOHUO_xYIR2K4H4svPVXTsJF2dc00b36nAHo`. This isn't the
 * colon-separated hex fingerprint that `assetlinks.json` and `keytool` use, and an origin in that form fails
 * verification. To convert a fingerprint:
 * ```
 * echo "<hex fingerprint>" | tr -d ':' | xxd -r -p | base64 | tr '+/' '-_' | tr -d '='
 * ```
 *
 * Passkey autofill is not supported, so [autofillAvailable] always returns false.
 */
@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
public actual class ClientAuthenticator {

    public actual companion object {
        private val default = ClientAuthenticator()
        public actual fun getClientAuthenticator(): ClientAuthenticator = default

        private const val PLAY_SERVICES_BACKEND = "androidx.credentials.playservices.CredentialProviderPlayServicesImpl"
        private val json = Json { ignoreUnknownKeys = true }
    }

    private val credentialManager: CredentialManager by lazy { CredentialManager.create(AndroidAppContext.applicationCtx) }

    public actual suspend fun webAuthNAvailable(): Boolean = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> true
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.P -> try {
            Class.forName(PLAY_SERVICES_BACKEND)
            true
        } catch (e: ClassNotFoundException) {
            false
        }
        else -> false
    }

    public actual suspend fun autofillAvailable(): Boolean = false

    public actual suspend fun createWebAuthNCredentials(request: WebAuthN.Registration.PublicKeyCredentialCreationOptions): WebAuthN.Registration.AttestedPublicKeyCredential {
        val response = try {
            // Providers aren't required to honor the timeout in the request JSON, so it is enforced here.
            withWebAuthNTimeout(request.timeout) {
                credentialManager.createCredential(
                    context = activity(),
                    request = CreatePublicKeyCredentialRequest(requestJson = request.toRequestJson().toString()),
                )
            }
        } catch (e: CreateCredentialException) {
            throw e.toWebAuthNException()
        }
        val responseJson = (response as? CreatePublicKeyCredentialResponse)?.registrationResponseJson
            ?: throw WebAuthNFailure.Failed.exception("The credential provider did not return a public key credential.")
        return json.parseToJsonElement(responseJson).jsonObject.toAttestedCredential()
    }

    public actual suspend fun getWebAuthNCredentials(
        request: WebAuthN.Authentication.PublicKeyCredentialRequestOptions,
        mediation: WebAuthNMediationType,
    ): WebAuthN.Authentication.AssertedPublicKeyCredential {
        // Passkey autofill isn't supported, which autofillAvailable reports, so callers don't get here.
        if (mediation == WebAuthNMediationType.Conditional) throw WebAuthNFailure.NotSupported.exception()
        request.extensions.requireValidLargeBlob()
        val response = try {
            withWebAuthNTimeout(request.timeout) {
                credentialManager.getCredential(
                    context = activity(),
                    request = GetCredentialRequest(
                        credentialOptions = listOf(GetPublicKeyCredentialOption(requestJson = request.toRequestJson().toString())),
                        preferImmediatelyAvailableCredentials = mediation == WebAuthNMediationType.Silent,
                    ),
                )
            }
        } catch (e: GetCredentialException) {
            throw e.toWebAuthNException()
        }
        val responseJson = (response.credential as? PublicKeyCredential)?.authenticationResponseJson
            ?: throw WebAuthNFailure.Failed.exception("The credential provider did not return a public key credential.")
        return json.parseToJsonElement(responseJson).jsonObject.toAssertedCredential()
    }

    private fun activity() = AndroidAppContext.activityCtx
        ?: throw IllegalStateException("WebAuthn requires a running KiteUiActivity.")
}

// Credential Manager has no exception of its own for an excluded credential, a timeout or a missing association, and
// passes those on as the DOMException a browser would raise. Having no usable credential is NotAllowedError in a
// browser, so NoCredentialException is mapped to it too.
private fun CreateCredentialException.toWebAuthNException() = when (this) {
    is CreateCredentialCancellationException -> WebAuthNFailure.NotAllowed
    is CreatePublicKeyCredentialDomException -> domError.toWebAuthNFailure()
    // A configuration error means Android 9-13 without credentials-play-services-auth or Play services.
    is CreateCredentialProviderConfigurationException, is CreateCredentialUnsupportedException -> WebAuthNFailure.NotSupported
    else -> WebAuthNFailure.Failed
}.exception(errorMessage?.toString() ?: type)

private fun GetCredentialException.toWebAuthNException() = when (this) {
    is GetCredentialCancellationException, is NoCredentialException -> WebAuthNFailure.NotAllowed
    is GetPublicKeyCredentialDomException -> domError.toWebAuthNFailure()
    is GetCredentialProviderConfigurationException, is GetCredentialUnsupportedException -> WebAuthNFailure.NotSupported
    else -> WebAuthNFailure.Failed
}.exception(errorMessage?.toString() ?: type)

private fun DomError.toWebAuthNFailure() = when (this) {
    // A browser reports a timeout as NotAllowedError, and its AbortError only follows the page aborting the call.
    is NotAllowedError, is TimeoutError, is AbortError -> WebAuthNFailure.NotAllowed
    is InvalidStateError -> WebAuthNFailure.AlreadyRegistered
    is SecurityError -> WebAuthNFailure.Misconfigured
    is NotSupportedError -> WebAuthNFailure.NotSupported
    else -> WebAuthNFailure.Failed
}

// The server compares challenges and user ids as the UTF-8 bytes of their strings, as the JS client sends them, so
// those bytes are what get base64url-encoded into the request JSON.
@OptIn(ExperimentalEncodingApi::class)
private fun String.utf8Base64Url(): String = webAuthNBase64.encode(encodeToByteArray())

private fun JsonObjectBuilder.putExistingCredentials(key: String, credentials: List<WebAuthN.ExistingCredential>) {
    if (credentials.isEmpty()) return
    putJsonArray(key) {
        credentials.forEach { credential ->
            add(buildJsonObject {
                put("type", credential.type)
                put("id", credential.id)
                if (credential.transports.isNotEmpty())
                    putJsonArray("transports") { credential.transports.forEach { add(JsonPrimitive(it.standardName)) } }
            })
        }
    }
}

private fun JsonObjectBuilder.putHints(hints: List<WebAuthN.Hints>) {
    if (hints.isNotEmpty()) putJsonArray("hints") { hints.forEach { add(JsonPrimitive(it.standardName)) } }
}

internal fun WebAuthN.Registration.PublicKeyCredentialCreationOptions.toRequestJson(): JsonObject = buildJsonObject {
    put("challenge", challenge.utf8Base64Url())
    putJsonObject("rp") {
        put("id", rp.id)
        put("name", rp.name)
    }
    putJsonObject("user") {
        put("id", user.id.utf8Base64Url())
        put("name", user.name)
        put("displayName", user.displayName)
    }
    putJsonArray("pubKeyCredParams") {
        pubKeyCredParams.forEach {
            add(buildJsonObject {
                put("type", it.type)
                put("alg", it.alg.coseAlgorithmId)
            })
        }
    }
    timeout?.let { put("timeout", it) }
    putExistingCredentials("excludeCredentials", excludeCredentials)
    putJsonObject("authenticatorSelection") {
        authenticatorSelection.authenticatorAttachment?.let { put("authenticatorAttachment", it.standardName) }
        put("residentKey", authenticatorSelection.residentKey.standardName)
        put("requireResidentKey", authenticatorSelection.residentKey == WebAuthN.GeneralPreference.Required)
        put("userVerification", authenticatorSelection.userVerification.standardName)
    }
    put("attestation", attestation.standardName)
    if (attestationFormats.isNotEmpty())
        putJsonArray("attestationFormats") { attestationFormats.forEach { add(JsonPrimitive(it)) } }
    putHints(hints)
    putJsonObject("extensions") {
        extensions.appidExclude?.let { put("appidExclude", it) }
        extensions.credProps?.let { put("credProps", it) }
        extensions.credentialProtectionPolicy?.let { put("credentialProtectionPolicy", it.standardName) }
        extensions.enforceCredentialProtectionPolicy?.let { put("enforceCredentialProtectionPolicy", it) }
        extensions.largeBlob?.let { putJsonObject("largeBlob") { put("support", it.support.standardName) } }
        extensions.minPinLength?.let { put("minPinLength", it) }
    }
}

internal fun WebAuthN.Authentication.PublicKeyCredentialRequestOptions.toRequestJson(): JsonObject = buildJsonObject {
    put("challenge", challenge.utf8Base64Url())
    put("rpId", rpId)
    timeout?.let { put("timeout", it) }
    putExistingCredentials("allowCredentials", allowCredentials)
    put("userVerification", userVerification.standardName)
    putHints(hints)
    putJsonObject("extensions") {
        extensions.appid?.let { put("appid", it) }
        extensions.largeBlob?.let { largeBlob ->
            putJsonObject("largeBlob") {
                largeBlob.read?.let { put("read", it) }
                largeBlob.write?.let { put("write", it) }
            }
        }
    }
}

private fun JsonObject.string(key: String): String? = (get(key) as? JsonPrimitive)?.contentOrNull
private fun JsonObject.requireString(key: String): String =
    string(key) ?: throw WebAuthNFailure.Failed.exception("The credential provider's response is missing '$key'.")
private fun JsonObject.obj(key: String): JsonObject? = get(key) as? JsonObject
// The JS client always reports an extension results object, even an empty one, so this does too.
private fun JsonObject.extensionResults(): JsonObject = obj("clientExtensionResults") ?: JsonObject(emptyMap())
private fun JsonObject.bool(key: String): Boolean? = (get(key) as? JsonPrimitive)?.booleanOrNull

private fun JsonElement?.toTransports(): List<WebAuthN.Transport> =
    (this as? JsonArray).orEmpty().map { (it as? JsonPrimitive)?.contentOrNull }.toWebAuthNTransports()

internal fun JsonObject.toAttestedCredential(): WebAuthN.Registration.AttestedPublicKeyCredential {
    val response = obj("response") ?: throw WebAuthNFailure.Failed.exception("The credential provider's response is missing 'response'.")
    val transports = response["transports"].toTransports()
    return WebAuthN.Registration.AttestedPublicKeyCredential(
        authenticatorAttachment = webAuthNAttachment(string("authenticatorAttachment"), transports),
        clientExtensionResults = extensionResults().let {
            WebAuthN.Registration.CreateExtensionResponse(
                appidExclude = it.bool("appidExclude"),
                credProps = it.obj("credProps")?.bool("rk")?.let { rk -> WebAuthN.Registration.CredPropsResponse(rk) },
                credProtect = (it["credProtect"] as? JsonPrimitive)?.intOrNull,
                largeBlob = it.obj("largeBlob")?.bool("supported")?.let { s -> WebAuthN.Registration.LargeBlobResponse(s) },
                minPinLength = (it["minPinLength"] as? JsonPrimitive)?.intOrNull?.toUInt(),
            )
        },
        id = requireString("id"),
        response = WebAuthN.Registration.AuthenticatorAttestationResponse(
            attestationObject = response.requireString("attestationObject"),
            clientDataJSON = response.requireString("clientDataJSON"),
            transports = transports,
        ),
    )
}

internal fun JsonObject.toAssertedCredential(): WebAuthN.Authentication.AssertedPublicKeyCredential {
    val response = obj("response") ?: throw WebAuthNFailure.Failed.exception("The credential provider's response is missing 'response'.")
    return WebAuthN.Authentication.AssertedPublicKeyCredential(
        id = requireString("id"),
        clientExtensionResults = extensionResults().let {
            WebAuthN.Authentication.RequestExtensionsResponse(
                appid = it.bool("appid"),
                largeBlob = it.obj("largeBlob")?.let { blob ->
                    WebAuthN.Authentication.LargeBlobResponse(
                        written = blob.bool("written") ?: false,
                        blob = blob.string("blob"),
                    )
                },
            )
        },
        response = WebAuthN.Authentication.AuthenticatorAssertionResponse(
            authenticatorData = response.requireString("authenticatorData"),
            clientDataJSON = response.requireString("clientDataJSON"),
            signature = response.requireString("signature"),
            userHandle = response.string("userHandle"),
        ),
    )
}
