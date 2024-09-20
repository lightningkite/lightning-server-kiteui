package com.lightningkite.lightningserver.auth

import com.lightningkite.kiteui.HttpMethod
import com.lightningkite.kiteui.navigation.DefaultJson
import com.lightningkite.kiteui.navigation.UrlProperties
import com.lightningkite.lightningdb.HasId
import com.lightningkite.lightningserver.LSError
import com.lightningkite.lightningserver.LsErrorException
import com.lightningkite.lightningserver.auth.oauth.OauthResponse
import com.lightningkite.lightningserver.auth.oauth.OauthTokenRequest
import com.lightningkite.lightningserver.auth.proof.*
import com.lightningkite.lightningserver.auth.subject.IdAndAuthMethods
import com.lightningkite.lightningserver.auth.subject.LogInRequest
import com.lightningkite.lightningserver.auth.subject.ProofsCheckResult
import com.lightningkite.lightningserver.auth.subject.SubSessionRequest
import com.lightningkite.lightningserver.networking.Fetcher
import com.lightningkite.now
import kotlinx.coroutines.delay
import kotlinx.serialization.KSerializer
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.properties.Properties
import kotlinx.serialization.serializer
import kotlin.math.max
import kotlin.time.Duration.Companion.days


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

    companion object {
        val dummy = AuthClientEndpoints(
            subjects = mapOf("User" to object: UserAuthClientEndpoints<String> {
                override suspend fun getToken(input: OauthTokenRequest): OauthResponse = OauthResponse("")
                override suspend fun getTokenSimple(input: String): String = ""
                override suspend fun logIn(input: List<Proof>): IdAndAuthMethods<String> {
                    delay(1000)
                    return IdAndAuthMethods(
                        id = "id",
                        options = listOf(
                            ProofOption(ProofMethodInfo("email", "email"), "test@test.com"),
                            ProofOption(ProofMethodInfo("sms", "phone"), "801-000-0000"),
                            ProofOption(ProofMethodInfo("password", null)),
                            ProofOption(ProofMethodInfo("otp", null)),
                        ).filter { it.method.via !in input.map { it.via } },
                        strengthRequired = 3,
                        session = if(input.sumOf { it.strength } >= 3) "" else null
                    )
                }
                override suspend fun logInV2(input: LogInRequest): IdAndAuthMethods<String> {
                    delay(1000)
                    return IdAndAuthMethods(
                        id = "id",
                        options = listOf(
                            ProofOption(ProofMethodInfo("email", "email"), "test@test.com"),
                            ProofOption(ProofMethodInfo("sms", "phone"), "801-000-0000"),
                            ProofOption(ProofMethodInfo("password", null)),
                            ProofOption(ProofMethodInfo("otp", null)),
                        ).filter { it.method.via !in input.proofs.map { it.via } },
                        strengthRequired = 3,
                        session = if(input.proofs.sumOf { it.strength } >= 3) "" else null
                    )
                }
                override suspend fun checkProofs(input: List<Proof>): ProofsCheckResult<String> {
                    delay(1000)
                    return ProofsCheckResult(
                        id = "id",
                        options = listOf(
                            ProofOption(ProofMethodInfo("email", "email"), "test@test.com"),
                            ProofOption(ProofMethodInfo("sms", "phone"), "801-000-0000"),
                            ProofOption(ProofMethodInfo("password", null)),
                            ProofOption(ProofMethodInfo("otp", null)),
                        ).filter { it.method.via !in input.map { it.via } },
                        strengthRequired = 3,
                        maxExpiration = now() + 7.days,
                        readyToLogIn = input.sumOf { it.strength } >= 3
                    )
                }
                override suspend fun openSession(input: String): String = ""
            }),
            authenticatedSubjects = mapOf(),
            smsProof = object: SmsProofClientEndpoints {
                override suspend fun beginSmsOwnershipProof(input: String): String {
                    delay(1000)
                    return input
                }
                override suspend fun provePhoneOwnership(input: FinishProof): Proof {
                    delay(1000)
                    if(input.password == "wrong") throw LsErrorException(400, LSError(400, "", "", ""))
                    return Proof("sms", property = "phone", value = input.key, at = now(), signature = "")
                }
            },
            emailProof = object: EmailProofClientEndpoints {
                override suspend fun beginEmailOwnershipProof(input: String): String {
                    delay(1000)
                    return input
                }
                override suspend fun proveEmailOwnership(input: FinishProof): Proof {
                    delay(1000)
                    if(input.password == "wrong") throw LsErrorException(400, LSError(400, "", "", ""))
                    return Proof("email", property = "email", value = input.key, at = now(), signature = "")
                }
            },
            passwordProof = object: PasswordProofClientEndpoints {
                override suspend fun provePasswordOwnership(input: IdentificationAndPassword): Proof {
                    delay(1000)
                    if(input.password == "wrong") throw LsErrorException(400, LSError(400, "", "", ""))
                    return Proof("password", property = "password", value = "id", at = now(), signature = "")
                }
            },
            oneTimePasswordProof = object: OneTimePasswordProofClientEndpoints {
                override suspend fun proveOTP(input: IdentificationAndPassword): Proof {
                    delay(1000)
                    if(input.password == "wrong") throw LsErrorException(400, LSError(400, "", "", ""))
                    return Proof("otp", property = "otp", value = "id", at = now(), signature = "")
                }
            },
            knownDeviceProof = object: KnownDeviceProofClientEndpoints {
                override suspend fun proveKnownDevice(input: String): Proof {
                    delay(1000)
                    if(input == "wrong") throw LsErrorException(400, LSError(400, "", "", ""))
                    return Proof("known-device", 1, "id", "value", now(), "")
                }

                override suspend fun knownDeviceOptions(): KnownDeviceOptions {
                    delay(1000)
                    return KnownDeviceOptions(30.days, 1)
                }
            },
            authenticatedKnownDeviceProof = object: AuthenticatedKnownDeviceProofClientEndpoints {
                override suspend fun establishKnownDevice(): String {
                    delay(1000)
                    return "ok"
                }

                override suspend fun establishKnownDeviceV2(): KnownDeviceSecretAndExpiration {
                    delay(1000)
                    return KnownDeviceSecretAndExpiration("ok", now() + 30.days)
                }
            },
            authenticatedOneTimePasswordProof = object: AuthenticatedOneTimePasswordProofClientEndpoints {
                override suspend fun establishOneTimePassword(input: EstablishOtp): String {
                    delay(1000)
                    return "URL for OTP"
                }
            },
            authenticatedPasswordProof = object: AuthenticatedPasswordProofClientEndpoints {
                override suspend fun establishPassword(input: EstablishPassword) {
                    delay(1000)
                }
            }
        )
    }
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
}
interface KnownDeviceProofClientEndpoints: ProofEndpoints {
    suspend fun knownDeviceOptions(): KnownDeviceOptions
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
        override suspend fun knownDeviceOptions(): KnownDeviceOptions = fetchImplementation(
            url = "options",
            method = HttpMethod.GET,
            jsonBody = null,
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
    suspend fun establishKnownDeviceV2(): KnownDeviceSecretAndExpiration
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
        override suspend fun establishKnownDeviceV2(): KnownDeviceSecretAndExpiration = fetchImplementation(
            url = "establish2",
            method = HttpMethod.POST,
            jsonBody = "{}",
            outSerializer = json.serializersModule.serializer()
        )
    }

}

interface UserAuthClientEndpoints<ID: Comparable<ID>> {
    suspend fun logIn(input: List<Proof>): IdAndAuthMethods<ID>
    suspend fun logInV2(input: LogInRequest): IdAndAuthMethods<ID>
    suspend fun checkProofs(input: List<Proof>): ProofsCheckResult<ID>
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
        override suspend fun logInV2(input: LogInRequest): IdAndAuthMethods<ID> = fetchImplementation(
            url = "login2",
            method = HttpMethod.POST,
            jsonBody = json.encodeToString(input),
            outSerializer = IdAndAuthMethods.serializer(idSerializer)
        )
        override suspend fun checkProofs(input: List<Proof>): ProofsCheckResult<ID> = fetchImplementation(
            url = "proofs-check",
            method = HttpMethod.POST,
            jsonBody = json.encodeToString(input),
            outSerializer = ProofsCheckResult.serializer(idSerializer)
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


