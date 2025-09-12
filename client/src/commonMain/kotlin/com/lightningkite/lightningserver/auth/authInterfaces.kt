@file:OptIn(ExperimentalSerializationApi::class)

package com.lightningkite.lightningserver.auth

import kotlin.uuid.Uuid
import com.lightningkite.kiteui.HttpMethod
import com.lightningkite.services.database.HasId
import com.lightningkite.lightningserver.LSError
import com.lightningkite.lightningserver.LsErrorException
import com.lightningkite.lightningserver.sessions.proofs.*
import com.lightningkite.lightningserver.sessions.IdAndAuthMethods
import com.lightningkite.lightningserver.sessions.LogInRequest
import com.lightningkite.lightningserver.sessions.ProofsCheckResult
import com.lightningkite.lightningserver.sessions.SubSessionRequest
import com.lightningkite.lightningserver.networking.Fetcher
import com.lightningkite.lightningserver.sessions.EstablishOtp
import com.lightningkite.lightningserver.sessions.EstablishPassword
import com.lightningkite.lightningserver.sessions.proofs.oauth.OauthResponse
import com.lightningkite.lightningserver.sessions.proofs.oauth.OauthTokenRequest
import kotlin.time.Clock.System.now
import com.lightningkite.reactive.core.AppScope
import com.lightningkite.reactive.core.Listenable
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.datetime.Clock.System
import kotlin.time.Instant
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes


data class LightningServerAuthentication(
    val subject: UserAuthClientEndpoints<*>,
    val subjectPath: String,
    val sessionToken: String,
) {
    val accessToken = subject.accessToken(sessionToken)
}

data class AuthClientEndpoints(
    val subjects: Map<String, UserAuthClientEndpoints<*>>,
    val authenticatedSubjects: Map<String, (LightningServerAuthentication) -> AuthenticatedUserAuthClientEndpoints<*, *>>,
    val smsProof: SmsProofClientEndpoints? = null,
    val emailProof: EmailProofClientEndpoints? = null,
    val oneTimePasswordProof: OneTimePasswordProofClientEndpoints? = null,
    val passwordProof: PasswordProofClientEndpoints? = null,
    val backupCodeProof: BackupCodeProofClientEndpoints? = null,
    val knownDeviceProof: KnownDeviceProofClientEndpoints? = null,
    val webAuthNProof: WebAuthNProofEndpoints? = null,
    val webAuthNIncludePasskeyUI: Boolean = webAuthNProof != null,
    val authenticatedOneTimePasswordProof: ((LightningServerAuthentication) -> AuthenticatedOneTimePasswordProofClientEndpoints)? = null,
    val authenticatedPasswordProof: ((LightningServerAuthentication) -> AuthenticatedPasswordProofClientEndpoints)? = null,
    val authenticatedKnownDeviceProof: ((LightningServerAuthentication) -> AuthenticatedKnownDeviceProofClientEndpoints)? = null,
    val authenticatedBackupCodeProof: ((LightningServerAuthentication) -> AuthenticatedBackupCodeProofClientEndpoints)? = null,
    val webAuthNRegistration: ((LightningServerAuthentication) -> WebAuthNRegistrationEndpoints)? = null,
) {

    companion object {
        private val methodLookup = mapOf(
            "all" to listOf(
                ProofOption(ProofMethodInfo("email", "email"), "test@test.com"),
                ProofOption(ProofMethodInfo("sms", "phone"), "801-000-0000"),
                ProofOption(ProofMethodInfo("password", null)),
                ProofOption(ProofMethodInfo("otp", null)),
                ProofOption(ProofMethodInfo("backupcode", null)),
                ProofOption(ProofMethodInfo("WebAuthN", null)),
            ),
            "emailpass" to listOf(
                ProofOption(ProofMethodInfo("email", "email"), "test@test.com"),
                ProofOption(ProofMethodInfo("password", null)),
            ),
            "emailpassotp" to listOf(
                ProofOption(ProofMethodInfo("email", "email"), "test@test.com"),
                ProofOption(ProofMethodInfo("password", null)),
                ProofOption(ProofMethodInfo("otp", null)),
                ProofOption(ProofMethodInfo("backupcode", null)),
            ),
            "emailpasswebauth" to listOf(
                ProofOption(ProofMethodInfo("email", "email"), "test@test.com"),
                ProofOption(ProofMethodInfo("WebAuthN", null)),
            ),
            "phonepass" to listOf(
                ProofOption(ProofMethodInfo("sms", "phone"), "801-000-0000"),
                ProofOption(ProofMethodInfo("password", null)),
            ),
            "phonepassotp" to listOf(
                ProofOption(ProofMethodInfo("sms", "phone"), "801-000-0000"),
                ProofOption(ProofMethodInfo("password", null)),
                ProofOption(ProofMethodInfo("otp", null)),
                ProofOption(ProofMethodInfo("backupcode", null)),
            ),
            "phonepasswebauth" to listOf(
                ProofOption(ProofMethodInfo("sms", "phone"), "801-000-0000"),
                ProofOption(ProofMethodInfo("WebAuthN", null)),
            ),
            "none" to listOf(
            ),
        )
        val dummy = AuthClientEndpoints(
            subjects = mapOf("User" to object : UserAuthClientEndpoints<String> {
                override suspend fun getToken(input: OauthTokenRequest): OauthResponse = OauthResponse("")
                override suspend fun getTokenSimple(input: String): String = ""
                fun get(proofs: List<Proof>): List<ProofOption> {
                    val id = proofs.find { it.property == "email" }?.value?.substringBefore('@')?.substringBefore('+')
                    return methodLookup[id] ?: methodLookup["all"]!!
                }

                override suspend fun logIn(input: List<Proof>): IdAndAuthMethods<String> {
                    delay(1000)
                    return IdAndAuthMethods(
                        id = "id",
                        options = get(input).filter { it.method.via !in input.map { it.via } },
                        strengthRequired = 3,
                        refreshToken = if (input.sumOf { it.strength } >= 3) "" else null
                    )
                }

                override suspend fun logInV2(input: LogInRequest): IdAndAuthMethods<String> {
                    delay(1000)
                    return IdAndAuthMethods(
                        id = "id",
                        options = get(input.proofs).filter { it.method.via !in input.proofs.map { it.via } },
                        strengthRequired = 3,
                        refreshToken = if (input.proofs.sumOf { it.strength } >= 3) "" else null
                    )
                }

                override suspend fun checkProofs(input: List<Proof>): ProofsCheckResult<String> {
                    delay(1000)
                    return ProofsCheckResult(
                        id = "id",
                        options = get(input).filter { it.method.via !in input.map { it.via } },
                        strengthRequired = 3,
                        expires = now() + 7.days,
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
                    if (input.password == "wrong") throw LsErrorException(
                        LSError(400, "", "Code incorrect. 4 attempts remain", "")
                    )
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
                    if (input.password == "wrong") throw LsErrorException(
                        LSError(400, "", "Code incorrect. 4 attempts remain", "")
                    )
                    return Proof("email", property = "email", value = input.key, at = now(), signature = "")
                }
            },
            passwordProof = object : PasswordProofClientEndpoints {
                override suspend fun provePasswordOwnership(input: IdentificationAndPassword): Proof {
                    delay(1000)
                    if (input.password == "wrong") throw LsErrorException(

                        LSError(400, "", "Password and user do not match", "")
                    )
                    return Proof("password", property = "password", value = "id", at = now(), signature = "")
                }
            },
            oneTimePasswordProof = object : OneTimePasswordProofClientEndpoints {
                override suspend fun proveOTP(input: IdentificationAndPassword): Proof {
                    delay(1000)
                    if (input.password == "wrong") throw LsErrorException(

                        LSError(400, "", "OTP and user do not match", "")
                    )
                    return Proof("otp", property = "otp", value = "id", at = now(), signature = "")
                }
            },
            knownDeviceProof = object : KnownDeviceProofClientEndpoints {
                override suspend fun proveKnownDevice(input: String): Proof {
                    delay(1000)
                    if (input == "wrong") throw LsErrorException(LSError(400, "", "", ""))
                    return Proof("known-device", 1, "id", "value", now(), "")
                }

                override suspend fun knownDeviceOptions(): KnownDeviceOptions {
                    delay(1000)
                    return KnownDeviceOptions(30.days, 1)
                }
            },
            webAuthNProof = object : WebAuthNProofEndpoints {
                override suspend fun start(input: Identification): WebAuthN.Authentication.StartResponse {
                    delay(1000)
                    return WebAuthN.Authentication.StartResponse(
                        "sfhfhfsghdgjdghjfsgsgbbcnkfhkjrtshgdzfgv",
                        options = WebAuthN.Authentication.PublicKeyCredentialRequestOptions(
                            challenge = "sfhfhfsghdgjdghjfsgsgbbcnkfhkjrtshgdzfgv",
                            rpId = "com.test"
                        )
                    )
                }

                override suspend fun prove(input: WebAuthN.Authentication.ProveRequest): Proof {
                    delay(1000)
                    return Proof(via = "WebAuthN", property = "WebAuthN", value = "id", at = now(), signature = "")
                }
            },
            backupCodeProof = object : BackupCodeProofClientEndpoints {
                override suspend fun proveBackupCode(input: IdentificationAndPassword): Proof {
                    delay(1000)
                    if (input.password == "wrong") throw LsErrorException(
                        LSError(400, "", "OTP and user do not match", "")
                    )
                    return Proof("backupcode", property = "backupcode", value = "id", at = now(), signature = "")
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

                    override suspend fun confirmOneTimePassword(input: String) {
                        delay(1000)
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

sealed interface ProofEndpoints {
    val via: String
    val property: String? get() = null
}

interface SmsProofClientEndpoints : ProofEndpoints {
    override val via: String get() = "sms"
    override val property: String? get() = "phone"
    suspend fun beginSmsOwnershipProof(input: String): String
    suspend fun provePhoneOwnership(input: FinishProof): Proof

}

open class SmsProofClientEndpointsLive(
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

interface EmailProofClientEndpoints : ProofEndpoints {
    override val via: String get() = "email"
    override val property: String get() = "email"
    suspend fun beginEmailOwnershipProof(input: String): String
    suspend fun proveEmailOwnership(input: FinishProof): Proof

}

open class EmailProofClientEndpointsLive(
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

interface OneTimePasswordProofClientEndpoints : ProofEndpoints {
    override val via: String get() = "totp"
    suspend fun proveOTP(input: IdentificationAndPassword): Proof
}

open class OneTimePasswordProofClientEndpointsLive(
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

interface PasswordProofClientEndpoints : ProofEndpoints {
    override val via: String get() = "password"
    suspend fun provePasswordOwnership(input: IdentificationAndPassword): Proof
}

open class PasswordProofClientEndpointsLive(
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

interface BackupCodeProofClientEndpoints : ProofEndpoints {
    override val via: String get() = "backupcode"
    suspend fun proveBackupCode(input: IdentificationAndPassword): Proof
}

open class BackupCodeProofClientEndpointsLive(
    val fetcher: Fetcher,
    val subpath: String,
) : BackupCodeProofClientEndpoints {
    override suspend fun proveBackupCode(input: IdentificationAndPassword): Proof = fetcher(
        url = "$subpath/prove",
        method = HttpMethod.POST,
        inSerializer = IdentificationAndPassword.serializer(),
        body = input,
        outSerializer = Proof.serializer()
    )
}

interface WebAuthNProofEndpoints : ProofEndpoints {
    override val via: String get() = "WebAuthN"
    suspend fun start(input: Identification): WebAuthN.Authentication.StartResponse
    suspend fun prove(input: WebAuthN.Authentication.ProveRequest): Proof
}

open class WebAuthNProofEndpointsLive(
    val fetcher: Fetcher,
    val subpath: String,
) : WebAuthNProofEndpoints {
    override suspend fun start(input: Identification): WebAuthN.Authentication.StartResponse =
        fetcher(
            url = "$subpath/start",
            method = HttpMethod.POST,
            inSerializer = Identification.serializer(),
            body = input,
            outSerializer = WebAuthN.Authentication.StartResponse.serializer()
        )

    override suspend fun prove(input: WebAuthN.Authentication.ProveRequest): Proof = fetcher(
        url = "$subpath/prove",
        method = HttpMethod.POST,
        inSerializer = WebAuthN.Authentication.ProveRequest.serializer(),
        body = input,
        outSerializer = Proof.serializer()
    )
}

interface KnownDeviceProofClientEndpoints : ProofEndpoints {
    override val via: String get() = "known-device"
    suspend fun knownDeviceOptions(): KnownDeviceOptions
    suspend fun proveKnownDevice(input: String): Proof
}

open class KnownDeviceProofClientEndpointsLive(
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

interface AuthenticatedOneTimePasswordProofClientEndpoints {

    suspend fun establishOneTimePassword(input: EstablishOtp): String
    suspend fun confirmOneTimePassword(input: String): Unit
}

open class AuthenticatedOneTimePasswordProofClientEndpointsLive(
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

    override suspend fun confirmOneTimePassword(input: String): Unit = fetcher(
        url = "$subpath/existing",
        method = HttpMethod.POST,
        inSerializer = String.serializer(),
        body = input,
        outSerializer = Unit.serializer()
    )
}

interface AuthenticatedPasswordProofClientEndpoints {
    suspend fun establishPassword(input: EstablishPassword): Unit
}

open class AuthenticatedPasswordProofClientEndpointsLive(
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

interface WebAuthNRegistrationEndpoints {
    suspend fun registerStart(input: WebAuthN.GeneralPreference): WebAuthN.Registration.RegistrationResponse
    suspend fun registerFinish(input: WebAuthN.Registration.RegisterRequest): Unit
}

open class WebAuthNRegistrationEndpointsLive(
    val fetcher: Fetcher,
    val subpath: String,
) : WebAuthNRegistrationEndpoints {
    override suspend fun registerStart(input: WebAuthN.GeneralPreference): WebAuthN.Registration.RegistrationResponse =
        fetcher(
            url = "$subpath/register-start",
            method = HttpMethod.POST,
            inSerializer = WebAuthN.GeneralPreference.serializer(),
            body = input,
            outSerializer = WebAuthN.Registration.RegistrationResponse.serializer()
        )

    override suspend fun registerFinish(input: WebAuthN.Registration.RegisterRequest): Unit = fetcher(
        url = "$subpath/register-finish",
        method = HttpMethod.POST,
        inSerializer = WebAuthN.Registration.RegisterRequest.serializer(),
        body = input,
        outSerializer = Unit.serializer(),
    )
}

interface AuthenticatedKnownDeviceProofClientEndpoints {
    suspend fun establishKnownDevice(): String
    suspend fun establishKnownDeviceV2(): KnownDeviceSecretAndExpiration

}

open class AuthenticatedKnownDeviceProofClientEndpointsLive(
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

interface AuthenticatedBackupCodeProofClientEndpoints {
    suspend fun resetCodes(): List<String>
    suspend fun clearCodes(): Unit
    suspend fun established(): Boolean
}

open class AuthenticatedBackupCodeProofClientEndpointsLive(
    val fetcher: Fetcher,
    val subpath: String,
) : AuthenticatedBackupCodeProofClientEndpoints {
    override suspend fun resetCodes(): List<String> = fetcher(
        url = "$subpath/reset-codes",
        method = HttpMethod.POST,
        inSerializer = Unit.serializer(),
        body = Unit,
        outSerializer = ListSerializer(String.serializer())
    )

    override suspend fun clearCodes(): Unit = fetcher(
        url = "$subpath/clear-codes",
        method = HttpMethod.POST,
        inSerializer = Unit.serializer(),
        body = Unit,
        outSerializer = Unit.serializer(),
    )

    override suspend fun established(): Boolean = fetcher(
        url = "$subpath/established",
        method = HttpMethod.GET,
        inSerializer = Unit.serializer(),
        body = Unit,
        outSerializer = Boolean.serializer()
    )
}

fun <ID : Comparable<ID>> UserAuthClientEndpoints<ID>.accessToken(sessionToken: String): suspend () -> List<Pair<String, String>> =
    accessToken(sessionToken, null)

fun <ID : Comparable<ID>> UserAuthClientEndpoints<ID>.accessToken(
    sessionToken: String,
    forceInvalidate: Listenable?
): suspend () -> List<Pair<String, String>> {
    var lastRefresh: Instant = Instant.DISTANT_PAST
    var token: Deferred<String>? = null
    forceInvalidate?.let {
        it.addListener {
            lastRefresh = Instant.DISTANT_PAST
            token = null
        }
    }
    return {
        var toUse = token
        if (Clock.System.now() - lastRefresh > 4.minutes || toUse == null) {
            lastRefresh = Clock.System.now()
            val out = AppScope.async {
                getTokenSimple(sessionToken)
            }
            token = out
            toUse = out
        }
        listOf("Authorization" to toUse.await())
    }
}

interface UserAuthClientEndpoints<ID : Comparable<ID>> {
    suspend fun logIn(input: List<Proof>): IdAndAuthMethods<ID>
    suspend fun logInV2(input: LogInRequest): IdAndAuthMethods<ID>
    suspend fun checkProofs(input: List<Proof>): ProofsCheckResult<ID>
    suspend fun openSession(input: String): String
    suspend fun getToken(input: OauthTokenRequest): OauthResponse
    suspend fun getTokenSimple(input: String): String
}

open class UserAuthClientEndpointsLive<ID : Comparable<ID>>(
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

interface AuthenticatedUserAuthClientEndpoints<User : HasId<ID>, ID : Comparable<ID>> {
    suspend fun authRequirements(): AuthRequirements
    suspend fun createSubSession(input: SubSessionRequest): String
    suspend fun getSelf(): User
    suspend fun terminateSession(): Unit
    suspend fun terminateOtherSession(sessionId: Uuid): Unit

}


open class AuthenticatedUserAuthClientEndpointsLive<USER : HasId<ID>, ID : Comparable<ID>>(
    val fetcher: Fetcher,
    val subpath: String,
    val userSerializer: KSerializer<USER>,
    val idSerializer: KSerializer<ID>,
) : AuthenticatedUserAuthClientEndpoints<USER, ID> {

    override suspend fun authRequirements(): AuthRequirements = fetcher(
        url = "$subpath/auth-requirements",
        method = HttpMethod.GET,
        inSerializer = Unit.serializer(),
        body = Unit,
        outSerializer = AuthRequirements.serializer()
    )

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

    override suspend fun terminateOtherSession(sessionId: Uuid): Unit = fetcher(
        url = "$subpath/${sessionId}/terminate",
        method = HttpMethod.POST,
        inSerializer = Unit.serializer(),
        body = Unit,
        outSerializer = Unit.serializer()
    )
}