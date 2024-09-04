package com.lightningkite.lightningserver.auth

import com.lightningkite.UUID
import com.lightningkite.kiteui.HttpMethod
import com.lightningkite.kiteui.RetryWebsocket
import com.lightningkite.kiteui.fetch
import com.lightningkite.kiteui.navigation.DefaultJson
import com.lightningkite.kiteui.navigation.UrlProperties
import com.lightningkite.lightningdb.HasId
import com.lightningkite.lightningserver.auth.oauth.OauthResponse
import com.lightningkite.lightningserver.auth.oauth.OauthTokenRequest
import com.lightningkite.lightningserver.auth.proof.*
import com.lightningkite.lightningserver.auth.subject.IdAndAuthMethods
import com.lightningkite.lightningserver.auth.subject.SubSessionRequest
import com.lightningkite.lightningserver.db.ClientModelRestEndpoints
import com.lightningkite.lightningserver.networking.Fetcher
import com.lightningkite.lightningserver.schema.LightningServerKSchema
import kotlinx.serialization.KSerializer
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.properties.Properties
import kotlinx.serialization.serializer


data class AuthClientEndpoints(
    val subjects: List<UserAuthClientEndpoints<*>>,
    val authenticatedSubjects: List<UserAuthClientEndpoints<*>>,
    val smsProof: SmsProofClientEndpoints? = null,
    val emailProof: EmailProofClientEndpoints? = null,
    val oneTimePasswordProof: OneTimePasswordProofClientEndpoints? = null,
    val passwordProof: PasswordProofClientEndpoints? = null,
    val knownDeviceProof: KnownDeviceProofClientEndpoints? = null,
    val authenticatedOneTimePasswordProof: AuthenticatedOneTimePasswordProofClientEndpoints? = null,
    val authenticatedPasswordProof: AuthenticatedPasswordProofClientEndpoints? = null,
    val authenticatedKnownDeviceProof: AuthenticatedKnownDeviceProofClientEndpoints? = null,
) {
}


interface SmsProofClientEndpoints {
    suspend fun beginSmsOwnershipProof(input: String): String
    suspend fun provePhoneOwnership(input: FinishProof): Proof

    open class StandardImpl(
        val fetchImplementation: Fetcher,
        val json: Json = DefaultJson,
        val properties: Properties = UrlProperties
    ): SmsProofClientEndpoints {
        override suspend fun beginSmsOwnershipProof(input: String): String = fetchImplementation(
            url = "start",
            method = HttpMethod.POST,
            jsonBody = json.encodeToString(input),
            outSerializer = json.serializersModule.serializer()
        )
        override suspend fun provePhoneOwnership(input: FinishProof): Proof = fetchImplementation(
            url = "prove",
            method = HttpMethod.POST,
            jsonBody = json.encodeToString(input),
            outSerializer = json.serializersModule.serializer()
        )
    }
}
interface EmailProofClientEndpoints {
    suspend fun beginEmailOwnershipProof(input: String): String
    suspend fun proveEmailOwnership(input: FinishProof): Proof
    open class StandardImpl(
        val fetchImplementation: Fetcher,
        val json: Json = DefaultJson,
        val properties: Properties = UrlProperties
    ): EmailProofClientEndpoints {
        override suspend fun beginEmailOwnershipProof(input: String): String = fetchImplementation(
            url = "start",
            method = HttpMethod.POST,
            jsonBody = json.encodeToString(input),
            outSerializer = json.serializersModule.serializer()
        )
        override suspend fun proveEmailOwnership(input: FinishProof): Proof = fetchImplementation(
            url = "prove",
            method = HttpMethod.POST,
            jsonBody = json.encodeToString(input),
            outSerializer = json.serializersModule.serializer()
        )
    }
}

interface OneTimePasswordProofClientEndpoints {
    suspend fun proveOTP(input: IdentificationAndPassword): Proof

    open class StandardImpl(
        val fetchImplementation: Fetcher,
        val json: Json = DefaultJson,
        val properties: Properties = UrlProperties
    ): OneTimePasswordProofClientEndpoints {
        override suspend fun proveOTP(input: IdentificationAndPassword): Proof = fetchImplementation(
            url = "prove",
            method = HttpMethod.POST,
            jsonBody = json.encodeToString(input),
            outSerializer = json.serializersModule.serializer()
        )
    }
}
interface PasswordProofClientEndpoints {
    suspend fun provePasswordOwnership(input: IdentificationAndPassword): Proof
    open class StandardImpl(
        val fetchImplementation: Fetcher,
        val json: Json = DefaultJson,
        val properties: Properties = UrlProperties
    ): PasswordProofClientEndpoints {
        override suspend fun provePasswordOwnership(input: IdentificationAndPassword): Proof = fetchImplementation(
            url = "prove",
            method = HttpMethod.POST,
            jsonBody = json.encodeToString(input),
            outSerializer = json.serializersModule.serializer()
        )
    }
}
interface KnownDeviceProofClientEndpoints {
    suspend fun proveKnownDevice(input: String): Proof
    open class StandardImpl(
        val fetchImplementation: Fetcher,
        val json: Json = DefaultJson,
        val properties: Properties = UrlProperties
    ): KnownDeviceProofClientEndpoints {
        override suspend fun proveKnownDevice(input: String): Proof = fetchImplementation(
            url = "prove",
            method = HttpMethod.POST,
            jsonBody = json.encodeToString(input),
            outSerializer = json.serializersModule.serializer()
        )
    }
}

interface AuthenticatedOneTimePasswordProofClientEndpoints {
    open class StandardImpl(
        val fetchImplementation: Fetcher,
        val json: Json = DefaultJson,
        val properties: Properties = UrlProperties
    ): AuthenticatedOneTimePasswordProofClientEndpoints {
        override suspend fun establishOneTimePassword(input: EstablishOtp): String = fetchImplementation(
            url = "establish",
            method = HttpMethod.POST,
            jsonBody = json.encodeToString(input),
            outSerializer = json.serializersModule.serializer()
        )
    }
    suspend fun establishOneTimePassword(input: EstablishOtp): String
}
interface AuthenticatedPasswordProofClientEndpoints {
    suspend fun establishPassword(input: EstablishPassword): Unit
    open class StandardImpl(
        val fetchImplementation: Fetcher,
        val json: Json = DefaultJson,
        val properties: Properties = UrlProperties
    ): AuthenticatedPasswordProofClientEndpoints {
        override suspend fun establishPassword(input: EstablishPassword): Unit = fetchImplementation(
            url = "establish",
            method = HttpMethod.POST,
            jsonBody = json.encodeToString(input),
            outSerializer = json.serializersModule.serializer()
        )
    }
}
interface AuthenticatedKnownDeviceProofClientEndpoints {
    suspend fun establishKnownDevice(): String
    open class StandardImpl(
        val fetchImplementation: Fetcher,
        val json: Json = DefaultJson,
        val properties: Properties = UrlProperties
    ): AuthenticatedKnownDeviceProofClientEndpoints {
        override suspend fun establishKnownDevice(): String = fetchImplementation(
            url = "establish",
            method = HttpMethod.POST,
            jsonBody = "{}",
            outSerializer = json.serializersModule.serializer()
        )
    }

}

interface UserAuthClientEndpoints<ID: Comparable<ID>> {
    suspend fun logIn(input: List<Proof>): IdAndAuthMethods<ID>
    suspend fun openSession(input: String): String
    suspend fun getToken(input: OauthTokenRequest): OauthResponse
    suspend fun getTokenSimple(input: String): String
    open class StandardImpl<USER: HasId<ID>, ID: Comparable<ID>>(
        val fetchImplementation: Fetcher,
        val idSerializer: KSerializer<ID>,
        val json: Json = DefaultJson,
        val properties: Properties = UrlProperties,
    ): UserAuthClientEndpoints<ID> {
        override suspend fun logIn(input: List<Proof>): IdAndAuthMethods<ID> = fetchImplementation(
            url = "login",
            method = HttpMethod.POST,
            jsonBody = json.encodeToString(input),
            outSerializer = IdAndAuthMethods.serializer(idSerializer)
        )
        override suspend fun openSession(input: String): String = fetchImplementation(
            url = "open-session",
            method = HttpMethod.POST,
            jsonBody = json.encodeToString(input),
            outSerializer = json.serializersModule.serializer()
        )
        override suspend fun getToken(input: OauthTokenRequest): OauthResponse = fetchImplementation(
            url = "token",
            method = HttpMethod.POST,
            jsonBody = json.encodeToString(input),
            outSerializer = json.serializersModule.serializer()
        )
        override suspend fun getTokenSimple(input: String): String = fetchImplementation(
            url = "token/simple",
            method = HttpMethod.POST,
            jsonBody = json.encodeToString(input),
            outSerializer = json.serializersModule.serializer()
        )
    }
}
interface AuthenticatedUserAuthClientEndpoints<User: HasId<ID>, ID: Comparable<ID>> {
    suspend fun createSubSession(input: SubSessionRequest, ): String
    suspend fun getSelf(): User
    open class StandardImpl<USER: HasId<ID>, ID: Comparable<ID>>(
        val fetchImplementation: Fetcher,
        val userSerializer: KSerializer<USER>,
        val idSerializer: KSerializer<ID>,
        val json: Json = DefaultJson,
        val properties: Properties = UrlProperties,
    ): AuthenticatedUserAuthClientEndpoints<USER, ID> {
        override suspend fun createSubSession(input: SubSessionRequest, ): String = fetchImplementation(
            url = "sub-session",
            method = HttpMethod.POST,
            jsonBody = json.encodeToString(input),
            outSerializer = json.serializersModule.serializer()
        )
        override suspend fun getSelf(): USER = fetchImplementation(
            url = "self",
            method = HttpMethod.GET,
            jsonBody = "{}",
            outSerializer = userSerializer
        )
    }
}


