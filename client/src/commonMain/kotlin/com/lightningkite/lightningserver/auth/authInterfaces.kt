@file:OptIn(ExperimentalSerializationApi::class)

package com.lightningkite.lightningserver.auth

import com.lightningkite.UUID
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
import com.lightningkite.readable.AppScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.datetime.Clock.System
import kotlinx.datetime.Instant
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.properties.Properties
import kotlinx.serialization.serializer
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes


data class LightningServerAuthentication(
    val subject: UserAuthClientEndpoints<*>,
    val subjectPath: String,
    val sessionToken: String,
) {
    var lastRefresh: Instant = Instant.DISTANT_PAST
    var token: Deferred<String>? = null
    val accessToken = subject.accessToken(sessionToken)
}

data class AuthClientEndpoints(
    val subjects: Map<String, UserAuthClientEndpoints<*>>,
    val authenticatedSubjects: Map<String, (LightningServerAuthentication) -> AuthenticatedUserAuthClientEndpoints<*, *>>,
    val smsProof: SmsProofClientEndpoints? = null,
    val emailProof: EmailProofClientEndpoints? = null,
    val oneTimePasswordProof: OneTimePasswordProofClientEndpoints? = null,
    val passwordProof: PasswordProofClientEndpoints? = null,
    val knownDeviceProof: KnownDeviceProofClientEndpoints? = null,
    val authenticatedOneTimePasswordProof: ((LightningServerAuthentication) -> AuthenticatedOneTimePasswordProofClientEndpoints)? = null,
    val authenticatedPasswordProof: ((LightningServerAuthentication) -> AuthenticatedPasswordProofClientEndpoints)? = null,
    val authenticatedKnownDeviceProof: ((LightningServerAuthentication) -> AuthenticatedKnownDeviceProofClientEndpoints)? = null,
) {
    val proofEndpoints
        get() = listOfNotNull(
            smsProof,
            emailProof,
            oneTimePasswordProof,
            passwordProof,
            knownDeviceProof
        )

    companion object {
        val dummy = AuthClientEndpoints(
            subjects = mapOf("User" to object : UserAuthClientEndpoints<String> {
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
                        session = if (input.sumOf { it.strength } >= 3) "" else null
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
                        session = if (input.proofs.sumOf { it.strength } >= 3) "" else null
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
            smsProof = object : SmsProofClientEndpoints {
                override suspend fun beginSmsOwnershipProof(input: String): String {
                    delay(1000)
                    return input
                }

                override suspend fun provePhoneOwnership(input: FinishProof): Proof {
                    delay(1000)
                    if (input.password == "wrong") throw LsErrorException(400, LSError(400, "", "Code incorrect. 4 attempts remain", ""))
                    return Proof("sms", property = "phone", value = input.key, at = now(), signature = "")
                }
            },
            emailProof = object : EmailProofClientEndpoints {
                override suspend fun beginEmailOwnershipProof(input: String): String {
                    delay(1000)
                    return input
                }

                override suspend fun proveEmailOwnership(input: FinishProof): Proof {
                    delay(1000)
                    if (input.password == "wrong") throw LsErrorException(400, LSError(400, "", "Code incorrect. 4 attempts remain", ""))
                    return Proof("email", property = "email", value = input.key, at = now(), signature = "")
                }
            },
            passwordProof = object : PasswordProofClientEndpoints {
                override suspend fun provePasswordOwnership(input: IdentificationAndPassword): Proof {
                    delay(1000)
                    if (input.password == "wrong") throw LsErrorException(400, LSError(400, "", "Password and user do not match", ""))
                    return Proof("password", property = "password", value = "id", at = now(), signature = "")
                }
            },
            oneTimePasswordProof = object : OneTimePasswordProofClientEndpoints {
                override suspend fun proveOTP(input: IdentificationAndPassword): Proof {
                    delay(1000)
                    if (input.password == "wrong") throw LsErrorException(400, LSError(400, "", "OTP and user do not match", ""))
                    return Proof("otp", property = "otp", value = "id", at = now(), signature = "")
                }
            },
            knownDeviceProof = object : KnownDeviceProofClientEndpoints {
                override suspend fun proveKnownDevice(input: String): Proof {
                    delay(1000)
                    if (input == "wrong") throw LsErrorException(400, LSError(400, "", "", ""))
                    return Proof("known-device", 1, "id", "value", now(), "")
                }

                override suspend fun knownDeviceOptions(): KnownDeviceOptions {
                    delay(1000)
                    return KnownDeviceOptions(30.days, 1)
                }
            },
            authenticatedKnownDeviceProof = {
                object : AuthenticatedKnownDeviceProofClientEndpoints {
                    override suspend fun establishKnownDevice(): String {
                        delay(1000)
                        return "ok"
                    }

                    override suspend fun establishKnownDeviceV2(): KnownDeviceSecretAndExpiration {
                        delay(1000)
                        return KnownDeviceSecretAndExpiration("ok", now() + 30.days)
                    }
                }
            },
            authenticatedOneTimePasswordProof = {
                object : AuthenticatedOneTimePasswordProofClientEndpoints {
                    override suspend fun establishOneTimePassword(input: EstablishOtp): String {
                        delay(1000)
                        return "URL for OTP"
                    }
                }
            },
            authenticatedPasswordProof = {
                object : AuthenticatedPasswordProofClientEndpoints {
                    override suspend fun establishPassword(input: EstablishPassword) {
                        delay(1000)
                    }
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

interface SmsProofClientEndpoints : ProofEndpoints {
    suspend fun beginSmsOwnershipProof(input: String): String
    suspend fun provePhoneOwnership(input: FinishProof): Proof

    open class StandardImpl(
        val fetcher: Fetcher,
        val subpath: String,
    ) : SmsProofClientEndpoints {
        override suspend fun beginSmsOwnershipProof(input: String): String = fetcher(
            url = "$subpath/start",
            method = HttpMethod.POST,
            inSerializer = String.serializer(),
            body = input,
            outSerializer = String.serializer(),
        )

        override suspend fun provePhoneOwnership(input: FinishProof): Proof = fetcher(
            url = "$subpath/prove",
            method = HttpMethod.POST,
            inSerializer = FinishProof.serializer(),
            body = input,
            outSerializer = Proof.serializer(),
        )
    }
}

interface EmailProofClientEndpoints : ProofEndpoints {
    suspend fun beginEmailOwnershipProof(input: String): String
    suspend fun proveEmailOwnership(input: FinishProof): Proof

    open class StandardImpl(
        val fetcher: Fetcher,
        val subpath: String,
    ) : EmailProofClientEndpoints {
        override suspend fun beginEmailOwnershipProof(input: String): String = fetcher(
            url = "$subpath/start",
            method = HttpMethod.POST,
            inSerializer = String.serializer(),
            body = input,
            outSerializer = String.serializer()
        )

        override suspend fun proveEmailOwnership(input: FinishProof): Proof = fetcher(
            url = "$subpath/prove",
            method = HttpMethod.POST,
            inSerializer = FinishProof.serializer(),
            body = input,
            outSerializer = Proof.serializer()
        )
    }

}

interface OneTimePasswordProofClientEndpoints : ProofEndpoints {
    suspend fun proveOTP(input: IdentificationAndPassword): Proof

    open class StandardImpl(
        val fetcher: Fetcher,
        val subpath: String,
    ) : OneTimePasswordProofClientEndpoints {
        override suspend fun proveOTP(input: IdentificationAndPassword): Proof = fetcher(
            url = "$subpath/prove",
            method = HttpMethod.POST,
            inSerializer = IdentificationAndPassword.serializer(),
            body = input,
            outSerializer = Proof.serializer()
        )
    }

}

interface PasswordProofClientEndpoints : ProofEndpoints {
    suspend fun provePasswordOwnership(input: IdentificationAndPassword): Proof
    open class StandardImpl(
        val fetcher: Fetcher,
        val subpath: String,
    ) : PasswordProofClientEndpoints {
        override suspend fun provePasswordOwnership(input: IdentificationAndPassword): Proof = fetcher(
            url = "$subpath/prove",
            method = HttpMethod.POST,
            inSerializer = IdentificationAndPassword.serializer(),
            body = input,
            outSerializer = Proof.serializer()
        )
    }
}

interface KnownDeviceProofClientEndpoints : ProofEndpoints {
    suspend fun knownDeviceOptions(): KnownDeviceOptions
    suspend fun proveKnownDevice(input: String): Proof
    open class StandardImpl(
        val fetcher: Fetcher,
        val subpath: String,
    ) : KnownDeviceProofClientEndpoints {
        override suspend fun proveKnownDevice(input: String): Proof = fetcher(
            url = "$subpath/prove",
            method = HttpMethod.POST,
            inSerializer = String.serializer(),
            body = input,
            outSerializer = Proof.serializer()
        )

        override suspend fun knownDeviceOptions(): KnownDeviceOptions = fetcher(
            url = "$subpath/options",
            method = HttpMethod.GET,
            inSerializer = Unit.serializer(),
            body = Unit,
            outSerializer = KnownDeviceOptions.serializer()
        )
    }
}

interface AuthenticatedOneTimePasswordProofClientEndpoints {
    open class StandardImpl(
        val fetcher: Fetcher,
        val subpath: String,
    ) : AuthenticatedOneTimePasswordProofClientEndpoints {
        override suspend fun establishOneTimePassword(input: EstablishOtp): String = fetcher(
            url = "$subpath/establish",
            method = HttpMethod.POST,
            inSerializer = EstablishOtp.serializer(),
            body = input,
            outSerializer = String.serializer()
        )
    }

    suspend fun establishOneTimePassword(input: EstablishOtp): String
}

interface AuthenticatedPasswordProofClientEndpoints {
    suspend fun establishPassword(input: EstablishPassword): Unit
    open class StandardImpl(
        val fetcher: Fetcher,
        val subpath: String,
    ) : AuthenticatedPasswordProofClientEndpoints {
        override suspend fun establishPassword(input: EstablishPassword): Unit = fetcher(
            url = "$subpath/establish",
            method = HttpMethod.POST,
            inSerializer = EstablishPassword.serializer(),
            body = input,
            outSerializer = Unit.serializer()
        )
    }
}

interface AuthenticatedKnownDeviceProofClientEndpoints {
    suspend fun establishKnownDevice(): String
    suspend fun establishKnownDeviceV2(): KnownDeviceSecretAndExpiration
    open class StandardImpl(
        val fetcher: Fetcher,
        val subpath: String,
    ) : AuthenticatedKnownDeviceProofClientEndpoints {
        override suspend fun establishKnownDevice(): String = fetcher(
            url = "$subpath/establish",
            method = HttpMethod.POST,
            inSerializer = Unit.serializer(),
            body = Unit,
            outSerializer = String.serializer()
        )

        override suspend fun establishKnownDeviceV2(): KnownDeviceSecretAndExpiration = fetcher(
            url = "$subpath/establish2",
            method = HttpMethod.POST,
            inSerializer = Unit.serializer(),
            body = Unit,
            outSerializer = KnownDeviceSecretAndExpiration.serializer()
        )
    }

}

fun <ID : Comparable<ID>> UserAuthClientEndpoints<ID>.accessToken(sessionToken: String): suspend () -> List<Pair<String, String>> {
    var lastRefresh: Instant = Instant.DISTANT_PAST
    var token: Deferred<String>? = null
    return {
        if (System.now() - lastRefresh > 4.minutes || token == null) {
            lastRefresh = System.now()
            token = AppScope.async {
                getTokenSimple(sessionToken)
            }
        }
        listOf("Authorization" to token.await())
    }
}

interface UserAuthClientEndpoints<ID : Comparable<ID>> {
    suspend fun logIn(input: List<Proof>): IdAndAuthMethods<ID>
    suspend fun logInV2(input: LogInRequest): IdAndAuthMethods<ID>
    suspend fun checkProofs(input: List<Proof>): ProofsCheckResult<ID>
    suspend fun openSession(input: String): String
    suspend fun getToken(input: OauthTokenRequest): OauthResponse
    suspend fun getTokenSimple(input: String): String
    open class StandardImpl<ID : Comparable<ID>>(
        val fetcher: Fetcher,
        val subpath: String,
        val idSerializer: KSerializer<ID>,
    ) : UserAuthClientEndpoints<ID> {
        override suspend fun logIn(input: List<Proof>): IdAndAuthMethods<ID> = fetcher(
            url = "$subpath/login",
            method = HttpMethod.POST,
            inSerializer = ListSerializer(Proof.serializer()),
            body = input,
            outSerializer = IdAndAuthMethods.serializer(idSerializer)
        )

        override suspend fun logInV2(input: LogInRequest): IdAndAuthMethods<ID> = fetcher(
            url = "$subpath/login2",
            method = HttpMethod.POST,
            inSerializer = LogInRequest.serializer(),
            body = input,
            outSerializer = IdAndAuthMethods.serializer(idSerializer)
        )

        override suspend fun checkProofs(input: List<Proof>): ProofsCheckResult<ID> = fetcher(
            url = "$subpath/proofs-check",
            method = HttpMethod.POST,
            inSerializer = ListSerializer(Proof.serializer()),
            body = input,
            outSerializer = ProofsCheckResult.serializer(idSerializer)
        )

        override suspend fun openSession(input: String): String = fetcher(
            url = "$subpath/open-session",
            method = HttpMethod.POST,
            inSerializer = String.serializer(),
            body = input,
            outSerializer = String.serializer()
        )

        override suspend fun getToken(input: OauthTokenRequest): OauthResponse = fetcher(
            url = "$subpath/token",
            method = HttpMethod.POST,
            inSerializer = OauthTokenRequest.serializer(),
            body = input,
            outSerializer = OauthResponse.serializer()
        )

        override suspend fun getTokenSimple(input: String): String = fetcher(
            url = "$subpath/token/simple",
            method = HttpMethod.POST,
            inSerializer = String.serializer(),
            body = input,
            outSerializer = String.serializer()
        )
    }
}

interface AuthenticatedUserAuthClientEndpoints<User : HasId<ID>, ID : Comparable<ID>> {
    suspend fun createSubSession(input: SubSessionRequest): String
    suspend fun getSelf(): User
    suspend fun terminateSession(): Unit
    suspend fun terminateOtherSession(sessionId: UUID): Unit
    open class StandardImpl<USER : HasId<ID>, ID : Comparable<ID>>(
        val fetcher: Fetcher,
        val subpath: String,
        val userSerializer: KSerializer<USER>,
        val idSerializer: KSerializer<ID>,
    ) : AuthenticatedUserAuthClientEndpoints<USER, ID> {
        override suspend fun createSubSession(input: SubSessionRequest): String = fetcher(
            url = "$subpath/sub-session",
            method = HttpMethod.POST,
            inSerializer = SubSessionRequest.serializer(),
            body = input,
            outSerializer = String.serializer()
        )

        override suspend fun getSelf(): USER = fetcher(
            url = "$subpath/self",
            method = HttpMethod.GET,
            inSerializer = Unit.serializer(),
            body = Unit,
            outSerializer = userSerializer
        )

        override suspend fun terminateSession(): Unit = fetcher(
            url = "$subpath/terminate",
            method = HttpMethod.POST,
            inSerializer = Unit.serializer(),
            body = Unit,
            outSerializer = Unit.serializer()
        )

        override suspend fun terminateOtherSession(sessionId: UUID): Unit = fetcher(
            url = "$subpath/${sessionId}/terminate",
            method = HttpMethod.POST,
            inSerializer = Unit.serializer(),
            body = Unit,
            outSerializer = Unit.serializer()
        )
    }
}


