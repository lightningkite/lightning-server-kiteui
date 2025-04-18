package com.lightningkite.kiteui.forms

import com.lightningkite.kiteui.ClientAuthenticator
import com.lightningkite.kiteui.WebAuthNMediationType
import com.lightningkite.kiteui.models.*
import com.lightningkite.kiteui.printStackTrace2
import com.lightningkite.kiteui.reactive.Action
import com.lightningkite.kiteui.reactive.PersistentProperty
import com.lightningkite.kiteui.views.*
import com.lightningkite.kiteui.views.direct.*
import com.lightningkite.kiteui.views.l2.field
import com.lightningkite.kiteui.views.l2.icon
import com.lightningkite.lightningserver.LsErrorException
import com.lightningkite.lightningserver.auth.*
import com.lightningkite.lightningserver.auth.proof.FinishProof
import com.lightningkite.lightningserver.auth.proof.IdentificationAndPassword
import com.lightningkite.lightningserver.auth.proof.KnownDeviceOptions
import com.lightningkite.lightningserver.auth.proof.KnownDeviceSecretAndExpiration
import com.lightningkite.lightningserver.auth.proof.Proof
import com.lightningkite.lightningserver.auth.subject.LogInRequest
import com.lightningkite.lightningserver.auth.subject.ProofsCheckResult
import com.lightningkite.now
import com.lightningkite.readable.*
import kotlinx.coroutines.*
import kotlinx.serialization.Serializable
import kotlin.math.roundToInt
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.seconds
import kotlin.time.DurationUnit

private object Regexes {
    val email = Regex("""[\w\-+._]+@(?:[\w\-]+\w\.)+[\w\-]+\w$""")
    val phoneNumber = Regex("^\\d{3}-?\\d{3}-?\\d{4}$")
}

@ViewDsl
fun ViewWriter.login(
    endpoints: AuthClientEndpoints,
    subjectPath: String = endpoints.subjects.keys.single(),
    subject: UserAuthClientEndpoints<*> = endpoints.subjects[subjectPath]!!,
    knownDeviceLocalStorageName: String? = "known-device",
    onAuthentication: suspend (String) -> Unit,
) {
    AuthComponent(endpoints, subjectPath, subject, knownDeviceLocalStorageName, onAuthentication).apply {
        render()
    }
}

@Serializable
data class KnownDeviceSecretInfoStuff(
    val info: KnownDeviceSecretAndExpiration,
    val primaryIdentifier: String,
)

class AuthComponent(
    val endpoints: AuthClientEndpoints,
    val subjectPath: String = endpoints.subjects.keys.single(),
    val subject: UserAuthClientEndpoints<*> = endpoints.subjects[subjectPath]!!,
    val knownDeviceLocalStorageName: String? = "known-device",
    val onAuthentication: suspend (String) -> Unit,
) {
    val primaryIdentifier = Property("")
    val phone = shared {
        primaryIdentifier().takeIf { Regexes.phoneNumber.matches(it) }?.filter { it.isDigit() }
            ?: authResult()?.options?.find { it.method.property == "phone" }?.value
    }
    val email = shared {
        primaryIdentifier().takeIf { Regexes.email.matches(it) }
            ?: authResult()?.options?.find { it.method.property == "email" }?.value
    }
    val proofs = Property<List<Proof>>(listOf())
    val currentProof = Property<CurrentProof?>(null)
    val authenticating = Property(false)
    val rememberDevice = Property(false)
    val desiredSessionLength = Property<Duration?>(1.days)
    val authResult = sharedSuspending {
        val proofs = proofs.await()

        if (proofs.isEmpty()) return@sharedSuspending null
        authenticating.value = true
        try {
            val result = subject.checkProofs(proofs)
            if (result.readyToLogIn) {
                val result = subject.logInV2(
                    LogInRequest(
                        proofs = proofs,
                        expires = desiredSessionLength.await()?.let { now() + it }
                    ))
                result.session?.let {
                    onAuthentication(it)
                    (AppScope + Dispatchers.Main).launch {
                        if (rememberDevice.await()) {
                            endpoints.authenticatedKnownDeviceProof?.invoke(
                                LightningServerAuthentication(
                                    subject,
                                    subjectPath,
                                    it
                                )
                            )?.establishKnownDeviceV2()?.let {
                                knownDevice?.value = KnownDeviceSecretInfoStuff(
                                    info = it,
                                    primaryIdentifier = primaryIdentifier.value
                                )
                            }
                        } else {
                            knownDevice?.value = null
                        }
                    }
                }
            }
            result
        } catch (e: LsErrorException) {
            if (e.status / 100 == 4) this@AuthComponent.proofs.value = listOf()
            if(e.status / 100 == 5) throw e
            null
        } finally {
            authenticating.value = false
        }
    }
    val knownDevice = knownDeviceLocalStorageName?.let { PersistentProperty<KnownDeviceSecretInfoStuff?>(it, null) }
    val knownDeviceOptions = sharedSuspending {
        endpoints.knownDeviceProof?.knownDeviceOptions()
    }

    interface CurrentProof {
        fun ViewWriter.render(onProof: (Proof) -> Unit)
    }

    val resendTime = 15.seconds

    inner class EmailProof(val p: EmailProofClientEndpoints, val id: String, var codeKey: String) : CurrentProof {
        val code = Property("")
        override fun ViewWriter.render(onProof: (Proof) -> Unit) {
            col {
                val proveEmailOwnership = Action ("Submit", Icon.done) {
                    onProof(p.proveEmailOwnership(FinishProof(codeKey, code.await())))
                }

                field("Login code emailed to $id") {
                    val tf: TextField
                    textInput {
                        tf = this
                        ::hint { "ABCDEF" }
                        requestFocus()
                        content bind code
                        action = proveEmailOwnership
                        keyboardHints = KeyboardHints.id
                    }
                }
                sessionLengthComponent(knownDeviceOptions, rememberDevice, desiredSessionLength, authResult)
                important - button {
                    centered - text("Submit")
                    action = proveEmailOwnership
                }
                val newCodeSentAt = Property(now())
                val nowBySecond = readable {
                    while (true) {
                        emit(now()); delay(1000)
                    }
                }
                button {
                    ::enabled { nowBySecond() !in newCodeSentAt() + 3.seconds..newCodeSentAt() + resendTime }
                    centered - onlyWhen { nowBySecond() > newCodeSentAt() + resendTime } - text("Send new code")
                    centered - onlyWhen { nowBySecond() < newCodeSentAt() + 3.seconds } - row {
                        centered - icon(Icon.done.copy(1.rem, 1.rem), "")
                        centered - text("Sent!")
                    }
                    centered - onlyWhen { nowBySecond() in newCodeSentAt() + 3.seconds..newCodeSentAt() + resendTime } - text {
                        ::content { "Can send new code in ${(newCodeSentAt() + resendTime - nowBySecond()).inWholeSeconds}" }
                    }
                    onClick {
                        if (nowBySecond.await() > newCodeSentAt.await() + resendTime) {
                            codeKey = p.beginEmailOwnershipProof(id)
                            newCodeSentAt.value = now()
                        }
                    }
                }
            }
        }
    }

    inner class SmsProof(val p: SmsProofClientEndpoints, val id: String, var codeKey: String) : CurrentProof {
        val code = Property("")
        override fun ViewWriter.render(onProof: (Proof) -> Unit) {
            col {
                val provePhoneOwnership = Action("Submit", Icon.done) {
                    onProof(p.provePhoneOwnership(FinishProof(codeKey, code.await())))
                }
                field("Login code texted to $id") {
                    val tf: TextField
                    expanding - textInput {
                        tf = this
                        ::hint { "ABCDEF" }
                        requestFocus()
                        action = provePhoneOwnership
                        content bind code
                        keyboardHints = KeyboardHints.id
                    }
                }
                sessionLengthComponent(knownDeviceOptions, rememberDevice, desiredSessionLength, authResult)
                important - button {
                    centered - text("Submit")
                    action = provePhoneOwnership
                }
                val newCodeSentAt = Property(now())
                val nowBySecond = readable {
                    while (true) {
                        emit(now()); delay(1000)
                    }
                }
                button {
                    ::enabled { nowBySecond() !in newCodeSentAt() + 3.seconds..newCodeSentAt() + resendTime }
                    centered - onlyWhen { nowBySecond() > newCodeSentAt() + resendTime } - text("Send new code")
                    centered - onlyWhen { nowBySecond() < newCodeSentAt() + 3.seconds } - row {
                        centered - icon(Icon.done.copy(1.rem, 1.rem), "")
                        centered - text("Sent!")
                    }
                    centered - onlyWhen { nowBySecond() in newCodeSentAt() + 3.seconds..newCodeSentAt() + resendTime } - text {
                        ::content { "Can send new code in ${(newCodeSentAt() + resendTime - nowBySecond()).inWholeSeconds}" }
                    }
                    onClick {
                        if (nowBySecond.await() > newCodeSentAt.await() + resendTime) {
                            codeKey = p.beginSmsOwnershipProof(id)
                            newCodeSentAt.value = now()
                        }
                    }
                }
            }
        }
    }

    inner class PasswordProof(
        val p: PasswordProofClientEndpoints,
        val type: String,
        val key: String,
        val value: String
    ) : CurrentProof {
        val code = Property("")
        override fun ViewWriter.render(onProof: (Proof) -> Unit) {
            val provePasswordOwnership = Action("Submit", Icon.done) {
                onProof(p.provePasswordOwnership(IdentificationAndPassword(type, key, value, code.await())))
            }
            col {
                field("Password") {
                    val tf: TextField
                    textInput {
                        tf = this
                        ::hint { "" }
                        requestFocus()
                        content bind code
                        action = provePasswordOwnership
                        keyboardHints = KeyboardHints.password
                    }
                }
                sessionLengthComponent(knownDeviceOptions, rememberDevice, desiredSessionLength, authResult)
                important - button {
                    centered - text("Submit")
                    action = provePasswordOwnership
                }

            }
        }
    }

    inner class OtpProof(
        val p: OneTimePasswordProofClientEndpoints,
        val type: String,
        val key: String,
        val value: String
    ) : CurrentProof {
        val code = Property("")
        override fun ViewWriter.render(onProof: (Proof) -> Unit) {
            col {
                val proveOtpProofAction = Action("Submit", Icon.done) {
                    onProof(p.proveOTP(IdentificationAndPassword(type, key, value, code.await())))
                }
                    field("One-time Password from App") {
                    val tf: TextField
                    textInput {
                        tf = this
                        ::hint { "000000" }
                        requestFocus()
                        content bind code
                        keyboardHints = KeyboardHints.integer
                        action = proveOtpProofAction
                    }
                }
                        sessionLengthComponent(knownDeviceOptions, rememberDevice, desiredSessionLength, authResult)
                        button {
                            centered - text("Submit")
                            action = proveOtpProofAction
                        }
                    }
        }
    }

    init {
        primaryIdentifier.addListener {
            currentProof.value = null
            proofs.value = listOf()
        }
    }

    fun ViewWriter.render() {
        col {
            val primaryIdentifierField: TextField
            field(
                when {
                    endpoints.emailProof != null && endpoints.smsProof != null -> "Email or Phone Number"
                    endpoints.emailProof != null -> "Email"
                    endpoints.smsProof != null -> "Phone Number"
                    else -> "Username"
                }
            ) {
                textInput {
                    primaryIdentifierField = this
                    hint = when {
                        endpoints.emailProof != null && endpoints.smsProof != null -> "me@email.com OR 800-123-4567"
                        endpoints.emailProof != null -> "me@email.com"
                        endpoints.smsProof != null -> "800-123-4567"
                        else -> "MyUsername"
                    }
                    ::keyboardHints {
                        when {
                            endpoints.emailProof != null && endpoints.smsProof != null -> KeyboardHints.email
                            endpoints.emailProof != null -> KeyboardHints.email
                            endpoints.smsProof != null -> KeyboardHints.phone
                            else -> KeyboardHints.id
                        }.let {
                            if (endpoints.webAuthNProof != null && ClientAuthenticator.autofillAvailable())
                                it.copy(includePasskeys = true)
                            else it
                        }
                    }
                    content bind primaryIdentifier
                    reactive {
                        if (currentProof() == null) requestFocus()
                    }
                }
            }

//            val pendingWebauthnRequest = endpoints.webAuthNProof?.let { webAuthNProof ->
//                launch {
//                    val (key, registerOptions) = webAuthNProof.start()
//                    val signedChallenge = ClientAuthenticator.getWebAuthNCredentials(registerOptions, WebAuthNMediationType.Conditional)
//                    proofs.value += webAuthNProof.prove(WebAuthNProve(key, signedChallenge))
//                }
//            }

            val ratio =
                shared { authResult()?.let { proofs().sumOf { it.strength } / it.strengthRequired.toFloat() } ?: 0f }
            onlyWhen { ratio() in 0.001f..0.999f } - card - progressBar {
                ::ratio {
                    authResult()?.let { proofs().sumOf { it.strength } / it.strengthRequired.toFloat() } ?: 0.01f
                }
            }

            knownDevice?.value?.takeIf {
                now() < it.info.expiresAt
            }?.let { kd ->
                endpoints.knownDeviceProof?.let { endpoints ->
                    (this as CoroutineScope).launch {
                        try {
                            authenticating.value = true
                            primaryIdentifier.value = kd.primaryIdentifier
                            proofs.value += endpoints.proveKnownDevice(kd.info.secret)
                        } catch (e: Exception) {
                            authenticating.value = false
                            e.printStackTrace2()
                            knownDevice.value = null
                        }
                    }
                }
            }

            onlyWhen { currentProof() == null && !authenticating() && authResult()?.readyToLogIn != true } - col {

                onlyWhen { proofs().isNotEmpty() && authResult().let { it != null && !it.readyToLogIn } } - text("We need more information.")
                val validId =
                    shared { Regexes.email.matches(primaryIdentifier()) || Regexes.phoneNumber.matches(primaryIdentifier()) }

                val emailStartAction = endpoints.emailProof?.let { p ->
                    val action = Action("Email Code", Icon.send) {
                        val id = email.await() ?: return@Action
                        currentProof.value = EmailProof(p, id, p.beginEmailOwnershipProof(id))
                    }
                    onlyWhen {
                        proofs().none { it.property == "email" } && (authResult()?.options?.any { it.method.property == "email" }
                            ?: true) && email() != null
                    } - important - buttonTheme - button {
                        this.action = action
                        centered - text("Email Code")
                    }
                    action
                }

                val smsStartAction = endpoints.smsProof?.let { p ->
                    val action = Action("Text Code", Icon.send) {
                        val id = phone.await() ?: return@Action
                        println("Debug p ${p}")
                        println("Debug id ${id}")
                        currentProof.value = SmsProof(p, id, p.beginSmsOwnershipProof(id))
                        println("Debug CurrentProof.value ${currentProof.value}")
                    }
                    onlyWhen {
                        proofs().none { it.property == "phone" } && (authResult()?.options?.any { it.method.property == "phone" }
                            ?: true) && phone() != null
                    } - important - buttonTheme - button {
                        this.action = action
                        centered - text("Text Code")
                    }
                    action
                }

                val passwordStartAction = endpoints.passwordProof?.let { p ->
                    val action = Action("Use Password", Icon.chevronRight) {
                        currentProof.value = PasswordProof(
                            p, endpoints.subjects.keys.single(), when {
                                Regexes.email.matches(primaryIdentifier.await()) -> "email"
                                Regexes.phoneNumber.matches(primaryIdentifier.await()) -> "phone"
                                else -> "_id"
                            }, primaryIdentifier.await()
                        )
                    }
                    onlyWhen {
                        proofs().none { it.via == "password" } && (authResult()?.options?.any { it.method.via == "password" }
                            ?: true) && validId()
                    } - important - buttonTheme - button {
                        centered - text("Use Password")
                        this.action = action
                    }
                    action
                }

//                val passkeyAction = endpoints.webAuthNProof?.let { passkeyProof ->
//                    val action = Action("Sign in with passkey", Icon.passkey) {
//                        try {
//                            pendingWebauthnRequest?.cancelAndJoin()
//                        } finally {
//                            val (key, getOptions) = passkeyProof.start()
//                            val signedChallenge = ClientAuthenticator.getWebAuthNCredentials(getOptions, WebAuthNMediationType.Optional)
//                            proofs.value += passkeyProof.prove(WebAuthNProveRequest(key, signedChallenge))
//                        }
//                    }
//                    onlyWhen {
//                        proofs().none { it.via == "passkey" } && (authResult()?.options?.any { it.method.via == "passkey" }
//                            ?: true) && ClientAuthenticator.webAuthNAvailable()
//                    } - buttonTheme - button {
//                        this.action = action
//                        row {
//                            expanding - space()
//                            centered - icon(Icon.passkey, "Passkey")
//                            centered - text("Use passkey")
//                            expanding - space()
//                        }
//                    }
//                    action
//                }

                val otpAction = endpoints.oneTimePasswordProof?.let { p ->
                    val action = Action("Use Authenticator App", Icon.chevronRight) {
                        currentProof.value = OtpProof(
                            p, endpoints.subjects.keys.single(), when {
                                Regexes.email.matches(primaryIdentifier.await()) -> "email"
                                Regexes.phoneNumber.matches(primaryIdentifier.await()) -> "phone"
                                else -> "_id"
                            }, primaryIdentifier.await()
                        )
                    }
                    onlyWhen {
                        proofs().none { it.via == "otp" } && (authResult()?.options?.any { it.method.via == "otp" }
                            ?: true) && validId()
                    } - important - buttonTheme - button {
                        this.action = action
                        centered - text("Use Authenticator App")
                    }
                    action
                }

                primaryIdentifierField::action {
                    val id = primaryIdentifier()
                    val validId = Regexes.email.matches(id) || Regexes.phoneNumber.matches(id)
                    when {
                        proofs().none { it.via == "email" } && email() != null && emailStartAction != null -> emailStartAction
                        proofs().none { it.via == "sms" } && phone() != null && smsStartAction != null -> smsStartAction
                        proofs().none { it.via == "password" } && validId && passwordStartAction != null -> passwordStartAction
//                        proofs().none { it.via == "passkey" } && ClientAuthenticator.webAuthNAvailable() && passkeyAction != null -> passkeyAction
                        proofs().none { it.via == "otp" } && validId && otpAction != null -> otpAction
                        else -> null
                    }
                }
            }
//            sessionLengthComponent(knownDeviceOptions,rememberDevice,desiredSessionLength,authResult)


            stack {
                reactive {
                    clearChildren()
                    currentProof()?.run {
                        render {
                            proofs.value += it
                            currentProof.value = null
                        }
                    }
                }
            }
            centered - onlyWhen { authenticating() } - row {
                activityIndicator()
                centered - text("Authenticating...")
            }
        }
    }
}

fun ViewWriter.sessionLengthComponent(
    knownDeviceOptions: Readable<KnownDeviceOptions?>,
    rememberDevice: Property<Boolean>,
    desiredSessionLength: Property<Duration?>,
    authResult: Readable<ProofsCheckResult<out Comparable<*>>?>
) {
    onlyWhen { knownDeviceOptions() != null } - row {
        centered - checkbox { checked bind rememberDevice }
        centered - text {
            content = "This is my device"
//                        ::content { "Remember this device for ${knownDeviceOptions()?.duration?.inWholeDays} days" }
        }
    }
    onlyWhen { rememberDevice() || knownDeviceOptions() == null } - row {
        centered - checkbox {
            checked bind desiredSessionLength.lens(
                get = { it != 1.days },
                set = { if (it) null else 1.days }
            )
        }
        centered - text {
            ::content {
                val days = authResult()?.maxExpiration?.let { it - now() }?.toDouble(DurationUnit.DAYS)
                    ?.roundToInt()
                if (days != null) "Keep me logged in for $days days" else "Keep me logged in"
            }
        }
    }
}
