@file:OptIn(ExperimentalSerializationApi::class)

package com.lightningkite.lightningserver.auth

import com.lightningkite.lightningserver.LSError
import com.lightningkite.lightningserver.LsErrorException
import com.lightningkite.lightningserver.sessions.*
import com.lightningkite.lightningserver.sessions.proofs.*
import com.lightningkite.reactive.core.AppScope
import com.lightningkite.reactive.core.Listenable
import com.lightningkite.services.database.HasId
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.serialization.ExperimentalSerializationApi
import kotlin.time.Clock.System.now
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlin.uuid.Uuid


data class LightningServerAuthentication(
    val subject: AuthClientEndpoints<*, *>,
    val subjectPath: String,
    val sessionToken: String,
) {
    val accessToken = subject.accessToken(sessionToken)
}

data class AuthEndpoints(
    val subjects: Map<String, AuthClientEndpoints<*, *>>,
    val authentication: LightningServerAuthentication? = null,
    val smsProof: ProofClientEndpoints.Sms? = null,
    val emailProof: ProofClientEndpoints.Email? = null,
    val oneTimePasswordProof: ProofClientEndpoints.TimeBasedOTP? = null,
    val passwordProof: ProofClientEndpoints.Password? = null,
    val backupCodeProof: ProofClientEndpoints.BackupCode? = null,
    val knownDeviceProof: ProofClientEndpoints.KnownDevice? = null,
    val webAuthNProof: ProofClientEndpoints.WebAuthN? = null,
    val webAuthNIncludePasskeyUI: Boolean = webAuthNProof != null,
    private val withAuthentication: AuthEndpoints.(LightningServerAuthentication?) -> AuthEndpoints = {
        copy(authentication = it)
    },
) {
    fun withAuth(auth: LightningServerAuthentication?) =
        if (auth == authentication) this
        else withAuthentication(auth)

    operator fun get(auth: LightningServerAuthentication) = withAuth(auth)

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

        val dummy = AuthEndpoints(
            subjects = mapOf("User" to object : AuthClientEndpoints<HasId<String>, String> {
                //                override suspend fun getToken(input: OauthTokenRequest): OauthResponse = OauthResponse("")
                override suspend fun getTokenSimple(input: String): String = ""
                override suspend fun getSelf(): HasId<String> = TODO()

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
                        maxExpiration = now() + 7.days,
                        readyToLogIn = input.sumOf { it.strength } >= 3
                    )
                }

                override suspend fun subsession(input: SubSessionRequest): String = ""
                override suspend fun authRequirements(): AuthRequirements = TODO()

                override suspend fun terminateSession() = TODO()

                override suspend fun terminateSession(sessionId: Uuid) = TODO()

            }),
            smsProof = object : ProofClientEndpoints.Sms {
                override suspend fun beginSmsOwnershipProof(input: String): String {
                    delay(1000)
                    return input
                }

                override suspend fun provePhoneOwnership(input: FinishProof): Proof {
                    delay(1000)
                    if (input.password == "wrong") throw LsErrorException(
                        LSError(400, "", "Code incorrect. 4 attempts remain", "")
                    )
                    return Proof(
                        "sms",
                        property = "phone",
                        value = input.key,
                        at = now(),
                        signature = "",
                        expiresAt = now().plus(15.minutes)
                    )
                }
            },
            emailProof = object : ProofClientEndpoints.Email {
                override suspend fun beginEmailOwnershipProof(input: String): String {
                    delay(1000)
                    return input
                }

                override suspend fun proveEmailOwnership(input: FinishProof): Proof {
                    delay(1000)
                    if (input.password == "wrong") throw LsErrorException(
                        LSError(400, "", "Code incorrect. 4 attempts remain", "")
                    )
                    return Proof(
                        "email",
                        property = "email",
                        value = input.key,
                        at = now(),
                        signature = "",
                        expiresAt = now().plus(15.minutes)
                    )
                }
            },
            passwordProof = object : ProofClientEndpoints.Password {
                override suspend fun provePasswordOwnership(input: IdentificationAndPassword): Proof {
                    delay(1000)
                    if (input.password == "wrong") throw LsErrorException(

                        LSError(400, "", "Password and user do not match", "")
                    )
                    return Proof(
                        "password",
                        property = "password",
                        value = "id",
                        at = now(),
                        signature = "",
                        expiresAt = now().plus(15.minutes)
                    )
                }

                override suspend fun establishPassword(input: EstablishPassword) {
                    delay(1000)
                }
            },
            oneTimePasswordProof = object : ProofClientEndpoints.TimeBasedOTP {
                override suspend fun proveOTP(input: IdentificationAndPassword): Proof {
                    delay(1000)
                    if (input.password == "wrong") throw LsErrorException(

                        LSError(400, "", "OTP and user do not match", "")
                    )
                    return Proof(
                        "otp",
                        property = "otp",
                        value = "id",
                        at = now(),
                        signature = "",
                        expiresAt = now().plus(15.minutes)
                    )
                }

                override suspend fun establishOneTimePassword(input: EstablishTotp): String {
                    delay(1000)
                    return "URL for OTP"
                }

                override suspend fun confirmOneTimePassword(input: String) {
                    delay(1000)
                }
            },
            knownDeviceProof = object : ProofClientEndpoints.KnownDevice {
                override suspend fun proveKnownDevice(input: String): Proof {
                    delay(1000)
                    if (input == "wrong") throw LsErrorException(LSError(400, "", "", ""))
                    return Proof(
                        "known-device",
                        1,
                        "id",
                        "value",
                        now(),
                        signature = "",
                        expiresAt = now().plus(15.minutes)
                    )
                }

                override suspend fun knownDeviceOptions(): KnownDeviceOptions {
                    delay(1000)
                    return KnownDeviceOptions(30.days, 1)
                }

                override suspend fun establishKnownDevice(): String {
                    delay(1000)
                    return "ok"
                }

                override suspend fun establishKnownDeviceV2(): KnownDeviceSecretAndExpiration {
                    delay(1000)
                    return KnownDeviceSecretAndExpiration("ok", now() + 30.days)
                }
            },
            webAuthNProof = object : ProofClientEndpoints.WebAuthN {
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
                    return Proof(
                        via = "WebAuthN",
                        property = "WebAuthN",
                        value = "id",
                        at = now(),
                        signature = "",
                        expiresAt = now().plus(15.minutes)
                    )
                }

                override suspend fun registerStart(input: WebAuthN.GeneralPreference): WebAuthN.Registration.RegistrationResponse =
                    TODO()

                override suspend fun registerFinish(input: WebAuthN.Registration.RegisterRequest) = TODO()
            },
            backupCodeProof = object : ProofClientEndpoints.BackupCode {
                override suspend fun proveBackupCode(input: IdentificationAndPassword): Proof {
                    delay(1000)
                    if (input.password == "wrong") throw LsErrorException(
                        LSError(400, "", "OTP and user do not match", "")
                    )
                    return Proof(
                        "backupcode",
                        property = "backupcode",
                        value = "id",
                        at = now(),
                        signature = "",
                        expiresAt = now().plus(15.minutes)
                    )
                }

                override suspend fun clearCodes() {
                    delay(1000)
                }

                override suspend fun established(): Boolean = false
                override suspend fun resetCodes(): List<String> = emptyList()
            },
        )
    }
}

fun <USER : HasId<ID>, ID : Comparable<ID>> AuthClientEndpoints<USER, ID>.accessToken(sessionToken: String): suspend () -> List<Pair<String, String>> =
    accessToken(sessionToken, null)

fun <USER : HasId<ID>, ID : Comparable<ID>> AuthClientEndpoints<USER, ID>.accessToken(
    sessionToken: String,
    forceInvalidate: Listenable?,
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
        if (now() - lastRefresh > 4.minutes || toUse == null) {
            lastRefresh = now()
            val out = AppScope.async {
                getTokenSimple(sessionToken)
            }
            token = out
            toUse = out
        }
        listOf("Authorization" to toUse.await())
    }
}
