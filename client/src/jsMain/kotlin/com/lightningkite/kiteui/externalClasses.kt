package com.lightningkite.kiteui

import org.khronos.webgl.ArrayBuffer
import kotlin.js.Promise

public external interface AuthenticatorSelection {
    public var authenticatorAttachment: String?
        get() = definedExternally
        set(value) = definedExternally
    public var residentKey: String?
        get() = definedExternally
        set(value) = definedExternally
    public var userVerification: String?
        get() = definedExternally
        set(value) = definedExternally
}


public external interface ExistingCredential {
    public var id: ByteArray?
        get() = definedExternally
        set(value) = definedExternally
    public var transports: Array<String>?
        get() = definedExternally
        set(value) = definedExternally
    public var type: String?
        get() = definedExternally
        set(value) = definedExternally
}

public external interface PublicKeyCredentialParameters {
    public var alg: Int?
        get() = definedExternally
        set(value) = definedExternally
    public var type: String?
        get() = definedExternally
        set(value) = definedExternally
}

public external interface PublicKeyCredentialRpEntity {
    public var id: String?
        get() = definedExternally
        set(value) = definedExternally
    public var name: String?
        get() = definedExternally
        set(value) = definedExternally
}

public external interface PublicKeyCredentialUserEntity {
    public var displayName: String?
        get() = definedExternally
        set(value) = definedExternally
    public var id: ByteArray?
        get() = definedExternally
        set(value) = definedExternally
    public var name: String?
        get() = definedExternally
        set(value) = definedExternally
}

public external interface LargeBlob {
    public var support: String?
        get() = definedExternally
        set(value) = definedExternally
    public var read: Boolean?
        get() = definedExternally
        set(value) = definedExternally
    public var write: ByteArray?
        get() = definedExternally
        set(value) = definedExternally
}

public external interface LargeBlobResponse {
    public var supported: Boolean?
        get() = definedExternally
        set(value) = definedExternally
    public var written: Boolean?
        get() = definedExternally
        set(value) = definedExternally
    public var blob: ArrayBuffer?
        get() = definedExternally
        set(value) = definedExternally
}

public external interface Extensions {
    public var appid: String?
        get() = definedExternally
        set(value) = definedExternally
    public var appidExclude: String?
        get() = definedExternally
        set(value) = definedExternally
    public var credProps: Boolean?
        get() = definedExternally
        set(value) = definedExternally
    public var credentialProtectionPolicy: String?
        get() = definedExternally
        set(value) = definedExternally
    public var enforceCredentialProtectionPolicy: Boolean?
        get() = definedExternally
        set(value) = definedExternally
    public var largeBlob: LargeBlob?
        get() = definedExternally
        set(value) = definedExternally
    public var minPinLength: Boolean?
        get() = definedExternally
        set(value) = definedExternally
}

public external interface CredPropsResponse{
    public var rk: Boolean?
        get() = definedExternally
        set(value) = definedExternally
}

public external interface ExtensionsResponse {
    public var appid: Boolean?
        get() = definedExternally
        set(value) = definedExternally
    public var appidExclude: Boolean?
        get() = definedExternally
        set(value) = definedExternally
    public var credProps: CredPropsResponse?
        get() = definedExternally
        set(value) = definedExternally
    public var credProtect: Int?
        get() = definedExternally
        set(value) = definedExternally
    public var largeBlob: LargeBlobResponse?
        get() = definedExternally
        set(value) = definedExternally
    public var minPinLength: Long?
        get() = definedExternally
        set(value) = definedExternally
}

public external interface PublicKeyCredentialCreationOptions {
    public var attestation: String?
        get() = definedExternally
        set(value) = definedExternally
    public var attestationFormats: Array<String>?
        get() = definedExternally
        set(value) = definedExternally
    public var authenticatorSelection: AuthenticatorSelection?
        get() = definedExternally
        set(value) = definedExternally
    public var challenge: ByteArray?
        get() = definedExternally
        set(value) = definedExternally
    public var excludeCredentials: Array<ExistingCredential>?
        get() = definedExternally
        set(value) = definedExternally
    public var extensions: Extensions?
        get() = definedExternally
        set(value) = definedExternally
    public var hints: Array<String>?
        get() = definedExternally
        set(value) = definedExternally
    public var pubKeyCredParams: Array<PublicKeyCredentialParameters>?
        get() = definedExternally
        set(value) = definedExternally
    public var rp: PublicKeyCredentialRpEntity?
        get() = definedExternally
        set(value) = definedExternally
    public var timeout: Int?
        get() = definedExternally
        set(value) = definedExternally
    public var user: PublicKeyCredentialUserEntity?
        get() = definedExternally
        set(value) = definedExternally
}

public external interface CreateOptions {
    public var publicKey: PublicKeyCredentialCreationOptions?
        get() = definedExternally
        set(value) = definedExternally
    public var password: String?
        get() = definedExternally
        set(value) = definedExternally
    public var federated: dynamic
        get() = definedExternally
        set(value) = definedExternally
    public var signal: dynamic
        get() = definedExternally
        set(value) = definedExternally
}


public external interface PublicKeyCredentialRequestOptions {
    public var allowCredentials: Array<ExistingCredential>?
        get() = definedExternally
        set(value) = definedExternally
    public var challenge: ByteArray?
        get() = definedExternally
        set(value) = definedExternally
    public var extensions: Extensions?
        get() = definedExternally
        set(value) = definedExternally
    public var rp: String?
        get() = definedExternally
        set(value) = definedExternally
    public var timeout: Int?
        get() = definedExternally
        set(value) = definedExternally
    public var userVerification: String?
        get() = definedExternally
        set(value) = definedExternally
}


public external interface GetOptions {
    public var mediation: String?
        get() = definedExternally
        set(value) = definedExternally
    public var password: Boolean?
        get() = definedExternally
        set(value) = definedExternally
    public var identity: Boolean?
        get() = definedExternally
        set(value) = definedExternally
    public var otp: Array<String>?
        get() = definedExternally
        set(value) = definedExternally
    public var signal: dynamic
        get() = definedExternally
        set(value) = definedExternally
    public var publicKey: PublicKeyCredentialRequestOptions?
        get() = definedExternally
        set(value) = definedExternally
}


public external interface CredentialContainer {
    public fun create(options: CreateOptions): Promise<PublicKeyCredential>
    public fun get(options: GetOptions): Promise<PublicKeyCredential>
}


public external interface AuthenticatorAttestationResponse {
    public val attestationObject: ArrayBuffer?
        get() = definedExternally
    public val clientDataJSON: ArrayBuffer?
        get() = definedExternally

    public fun getAuthenticatorData(): ArrayBuffer
    public fun getPublicKey(): ArrayBuffer
    public fun getPublicKeyAlgorithm(): Int
    public fun getTransports(): Array<String>
}


public external interface AuthenticatorAssertionResponse {
    public val authenticatorData: ArrayBuffer?
        get() = definedExternally
    public val clientDataJSON: ArrayBuffer?
        get() = definedExternally
    public val signature: ArrayBuffer?
        get() = definedExternally
    public val userHandle: ArrayBuffer?
        get() = definedExternally
}


public external interface PublicKeyCredential {

    public val authenticatorAttachment: String?
        get() = definedExternally
    public val id: String?
        get() = definedExternally
    public val response: dynamic // this is union of: AuthenticatorAttestationResponse, and AuthenticatorAssertionResponse
        get() = definedExternally
    public val type: String?
        get() = definedExternally

    public fun toJSON(): String
    public fun getClientExtensionResults(): ExtensionsResponse?

    public companion object {
        public fun isConditionalMediationAvailable(): Promise<Boolean>
        public fun isUserVerifyingPlatformAuthenticatorAvailable(): Promise<Boolean>
        public fun parseCreationOptionsFromJSON(input: dynamic): PublicKeyCredentialCreationOptions
    }
}


public external class AbortController {
    public fun abort()
    public val signal: dynamic
}
