package com.lightningkite.kiteui

import org.khronos.webgl.ArrayBuffer
import kotlin.js.Promise

external interface AuthenticatorSelection {
    var authenticatorAttachment: String?
        get() = definedExternally
        set(value) = definedExternally
    var residentKey: String?
        get() = definedExternally
        set(value) = definedExternally
    var userVerification: String?
        get() = definedExternally
        set(value) = definedExternally
}


external interface ExistingCredential {
    var id: ByteArray?
        get() = definedExternally
        set(value) = definedExternally
    var transports: Array<String>?
        get() = definedExternally
        set(value) = definedExternally
    var type: String?
        get() = definedExternally
        set(value) = definedExternally
}

external interface PublicKeyCredentialParameters {
    var alg: Int?
        get() = definedExternally
        set(value) = definedExternally
    var type: String?
        get() = definedExternally
        set(value) = definedExternally
}

external interface PublicKeyCredentialRpEntity {
    var id: String?
        get() = definedExternally
        set(value) = definedExternally
    var name: String?
        get() = definedExternally
        set(value) = definedExternally
}

external interface PublicKeyCredentialUserEntity {
    var displayName: String?
        get() = definedExternally
        set(value) = definedExternally
    var id: ByteArray?
        get() = definedExternally
        set(value) = definedExternally
    var name: String?
        get() = definedExternally
        set(value) = definedExternally
}

external interface PublicKeyCredentialCreationOptions {
    var attestation: String?
        get() = definedExternally
        set(value) = definedExternally
    var attestationFormats: Array<String>?
        get() = definedExternally
        set(value) = definedExternally
    var authenticatorSelection: AuthenticatorSelection?
        get() = definedExternally
        set(value) = definedExternally
    var challenge: ByteArray?
        get() = definedExternally
        set(value) = definedExternally
    var excludeCredentials: Array<ExistingCredential>?
        get() = definedExternally
        set(value) = definedExternally
    var extensions: dynamic
        get() = definedExternally
        set(value) = definedExternally
    var hints: Array<String>?
        get() = definedExternally
        set(value) = definedExternally
    var pubKeyCredParams: Array<PublicKeyCredentialParameters>?
        get() = definedExternally
        set(value) = definedExternally
    var rp: PublicKeyCredentialRpEntity?
        get() = definedExternally
        set(value) = definedExternally
    var timeout: Int?
        get() = definedExternally
        set(value) = definedExternally
    var user: PublicKeyCredentialUserEntity?
        get() = definedExternally
        set(value) = definedExternally
}

external interface CreateOptions {
    var publicKey: PublicKeyCredentialCreationOptions?
        get() = definedExternally
        set(value) = definedExternally
    var password: String?
        get() = definedExternally
        set(value) = definedExternally
    var federated: dynamic
        get() = definedExternally
        set(value) = definedExternally
    var signal: dynamic
        get() = definedExternally
        set(value) = definedExternally
}


external interface PublicKeyCredentialRequestOptions {
    var allowCredentials: Array<ExistingCredential>?
        get() = definedExternally
        set(value) = definedExternally
    var challenge: ByteArray?
        get() = definedExternally
        set(value) = definedExternally
    var extensions: dynamic
        get() = definedExternally
        set(value) = definedExternally
    var rp: String?
        get() = definedExternally
        set(value) = definedExternally
    var timeout: Int?
        get() = definedExternally
        set(value) = definedExternally
    var userVerification: String?
        get() = definedExternally
        set(value) = definedExternally
}


external interface GetOptions {
    var mediation: String?
        get() = definedExternally
        set(value) = definedExternally
    var password: Boolean?
        get() = definedExternally
        set(value) = definedExternally
    var identity: Boolean?
        get() = definedExternally
        set(value) = definedExternally
    var otp: Array<String>?
        get() = definedExternally
        set(value) = definedExternally
    var signal: dynamic
        get() = definedExternally
        set(value) = definedExternally
    var publicKey: PublicKeyCredentialRequestOptions?
        get() = definedExternally
        set(value) = definedExternally
}


external interface CredentialContainer {
    fun create(options: CreateOptions): Promise<PublicKeyCredential>
    fun get(options: GetOptions): Promise<PublicKeyCredential>
}


external interface AuthenticatorAttestationResponse {
    val attestationObject: ArrayBuffer?
        get() = definedExternally
    val clientDataJSON: ArrayBuffer?
        get() = definedExternally

    fun getAuthenticatorData(): ArrayBuffer
    fun getPublicKey(): ArrayBuffer
    fun getPublicKeyAlgorithm(): Int
    fun getTransports(): Array<String>
}


external interface AuthenticatorAssertionResponse {
    val authenticatorData: ArrayBuffer?
        get() = definedExternally
    val clientDataJSON: ArrayBuffer?
        get() = definedExternally
    val signature: ArrayBuffer?
        get() = definedExternally
    val userHandle: ArrayBuffer?
        get() = definedExternally
}


external interface PublicKeyCredential {

    val authenticatorAttachment: String?
        get() = definedExternally
    val clientExtensionResults: dynamic
        get() = definedExternally
    val id: String?
        get() = definedExternally
    val response: dynamic // this is union of: AuthenticatorAttestationResponse, and AuthenticatorAssertionResponse
        get() = definedExternally
    val type: String?
        get() = definedExternally

    fun toJSON(): String

    companion object {
        fun isConditionalMediationAvailable(): Promise<Boolean>
        fun isUserVerifyingPlatformAuthenticatorAvailable(): Promise<Boolean>
        fun parseCreationOptionsFromJSON(input: dynamic): PublicKeyCredentialCreationOptions
    }
}


external class AbortController {
    fun abort()
    val signal: dynamic
}
