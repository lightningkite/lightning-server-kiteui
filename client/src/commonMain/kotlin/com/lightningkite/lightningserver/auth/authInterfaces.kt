package com.lightningkite.lightningserver.auth

import com.lightningkite.kiteui.HttpMethod
import com.lightningkite.kiteui.navigation.DefaultJson
import com.lightningkite.kiteui.navigation.UrlProperties
import com.lightningkite.lightningdb.HasId
import com.lightningkite.lightningserver.auth.oauth.OauthResponse
import com.lightningkite.lightningserver.auth.oauth.OauthTokenRequest
import com.lightningkite.lightningserver.auth.proof.*
import com.lightningkite.lightningserver.auth.subject.IdAndAuthMethods
import com.lightningkite.lightningserver.auth.subject.SubSessionRequest
import com.lightningkite.lightningserver.networking.Fetcher
import kotlinx.serialization.KSerializer
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.properties.Properties
import kotlinx.serialization.serializer


data class AuthClientEndpoints(
    val subjects: Map<String, UserAuthClientEndpoints<*>>,
    val authenticatedSubjects: Map<String, AuthenticatedUserAuthClientEndpoints<*, *>>,
    val smsProof: SmsProofClientEndpoints? = null,
    val emailProof: EmailProofClientEndpoints? = null,
    val oneTimePasswordProof: OneTimePasswordProofClientEndpoints? = null,
    val passwordProof: PasswordProofClientEndpoints? = null,
    val knownDeviceProof: KnownDeviceProofClientEndpoints? = null,
    val authenticatedOneTimePasswordProof: AuthenticatedOneTimePasswordProofClientEndpoints? = null,
    val authenticatedPasswordProof: AuthenticatedPasswordProofClientEndpoints? = null,
    val authenticatedKnownDeviceProof: AuthenticatedKnownDeviceProofClientEndpoints? = null,
) {
    val proofEndpoints get() = listOfNotNull(
        smsProof,
        emailProof,
        oneTimePasswordProof,
        passwordProof,
        knownDeviceProof
    )
}

data class Identification(
    val type: String,
    val property: String,
    val value: String,
) {
    fun withPassword(password: String) =
        IdentificationAndPassword(
            type,
            property,
            value,
            password
        )
}

sealed interface ProofEndpoints {
    interface AuthComponent {
        val name: String
        val message: String
        val hint: String
        suspend fun start(identifier: Identification)
        suspend fun submit(password: String): Proof
    }
    val authComponent: AuthComponent
}

interface SmsProofClientEndpoints: ProofEndpoints {
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

    class SmsComponent(val endpoints: SmsProofClientEndpoints): ProofEndpoints.AuthComponent {
        var phoneNumber: String? = null

        override val name: String = "SMS"
        override val message: String = "Enter the PIN sent to $phoneNumber to log in"
        override val hint: String = "PIN"

        var token: String? = null
        override suspend fun start(identifier: Identification) {
            if (identifier.property != "phoneNumber") throw IllegalArgumentException("SMS start() must be given a phone number")
            phoneNumber = identifier.value
            token = endpoints.beginSmsOwnershipProof(identifier.value)
        }
        override suspend fun submit(password: String): Proof {
            return endpoints.provePhoneOwnership(
                FinishProof(
                    key = token ?: throw IllegalStateException("SMS submit() called before start()"),
                    password = password
                )
            )
        }
    }
    override val authComponent: ProofEndpoints.AuthComponent get() = SmsComponent(this)
}
interface EmailProofClientEndpoints: ProofEndpoints {
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

    class EmailComponent(val endpoints: EmailProofClientEndpoints): ProofEndpoints.AuthComponent {
        var email: String? = null

        override val name: String = "Email"
        override val message: String = "Enter the PIN sent to $email to log in"
        override val hint: String = "PIN"

        var token: String? = null
        override suspend fun start(identifier: Identification) {
            if (identifier.property != "email") throw IllegalArgumentException("Email: start() must be given an email")
            email = identifier.value
            token = endpoints.beginEmailOwnershipProof(identifier.value)
        }
        override suspend fun submit(password: String): Proof {
            return endpoints.proveEmailOwnership(
                FinishProof(
                    key = token ?: throw IllegalStateException("Email submit() called before start()"),
                    password = password
                )
            )
        }
    }
    override val authComponent: ProofEndpoints.AuthComponent get() = EmailComponent(this)
}

interface OneTimePasswordProofClientEndpoints: ProofEndpoints {
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

    class OTPComponent(val endpoints: OneTimePasswordProofClientEndpoints): ProofEndpoints.AuthComponent {
        override val name: String = "OTP"
        override val message: String = "Enter your One-Time Password below to log in"
        override val hint: String = "Password"

        var identification: Identification? = null
        override suspend fun start(identifier: Identification) {
            identification = identifier
        }
        override suspend fun submit(password: String): Proof {
            return endpoints.proveOTP(
                identification?.withPassword(password) ?: throw IllegalStateException("OTP submit() called before start()")
            )
        }
    }
    override val authComponent: ProofEndpoints.AuthComponent get() = OTPComponent(this)
}
interface PasswordProofClientEndpoints: ProofEndpoints {
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

    class PasswordComponent(val endpoints: PasswordProofClientEndpoints): ProofEndpoints.AuthComponent {
        override val name: String = "Password"
        override val message: String = "Enter your password below to log in"
        override val hint: String = "Password"

        var identification: Identification? = null
        override suspend fun start(identifier: Identification) {
            identification = identifier
        }
        override suspend fun submit(password: String): Proof {
            println("id + password: ${identification?.withPassword(password)}")
            return endpoints.provePasswordOwnership(
                identification?.withPassword(password) ?: throw IllegalStateException("Password submit() called before start()")
            )
        }
    }
    override val authComponent: ProofEndpoints.AuthComponent get() = PasswordComponent(this)
}
interface KnownDeviceProofClientEndpoints: ProofEndpoints {
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

    class KnownDeviceComponent(val endpoints: KnownDeviceProofClientEndpoints): ProofEndpoints.AuthComponent {
        override val name: String = "Known Device"
        override val message: String = "This should be done automatically"
        override val hint: String = "Known Device"

        override suspend fun start(identifier: Identification) {
            /*Do Nothing*/
        }
        override suspend fun submit(password: String): Proof {
            return endpoints.proveKnownDevice(password)
        }
    }
    override val authComponent: ProofEndpoints.AuthComponent get() = KnownDeviceComponent(this)
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


