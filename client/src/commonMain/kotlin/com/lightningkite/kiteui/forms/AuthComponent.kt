package com.lightningkite.kiteui.forms

import com.lightningkite.kiteui.AppScope
import com.lightningkite.kiteui.models.*
import com.lightningkite.kiteui.printStackTrace2
import com.lightningkite.kiteui.reactive.*
import com.lightningkite.kiteui.views.*
import com.lightningkite.kiteui.views.direct.*
import com.lightningkite.kiteui.views.l2.errorText
import com.lightningkite.kiteui.views.l2.field
import com.lightningkite.kiteui.views.l2.icon
import com.lightningkite.lightningserver.LsErrorException
import com.lightningkite.lightningserver.auth.*
import com.lightningkite.lightningserver.auth.proof.FinishProof
import com.lightningkite.lightningserver.auth.proof.IdentificationAndPassword
import com.lightningkite.lightningserver.auth.proof.KnownDeviceSecretAndExpiration
import com.lightningkite.lightningserver.auth.proof.Proof
import com.lightningkite.lightningserver.auth.subject.LogInRequest
import com.lightningkite.now
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
fun ViewWriter.login(endpoints: AuthClientEndpoints, knownDeviceLocalStorageName: String? = "known-device", onAuthentication: suspend (String) -> Unit) {
    AuthComponent(endpoints, knownDeviceLocalStorageName, onAuthentication).apply {
        render()
    }
}

@Serializable
data class KnownDeviceSecretInfoStuff(
    val info: KnownDeviceSecretAndExpiration,
    val primaryIdentifier: String
)

class AuthComponent(val endpoints: AuthClientEndpoints, val knownDeviceLocalStorageName: String? = "known-device", val onAuthentication: suspend (String) -> Unit) {
    val primaryIdentifier = Property("")
    val phone = shared {
        primaryIdentifier().takeIf { Regexes.phoneNumber.matches(it) }?.filter { it.isDigit() } ?: authResult()?.options?.find { it.method.property == "phone" }?.value
    }
    val email = shared {
        primaryIdentifier().takeIf { Regexes.email.matches(it) } ?: authResult()?.options?.find { it.method.property == "email" }?.value
    }
    val proofs = Property<List<Proof>>(listOf())
    val currentProof = Property<CurrentProof?>(null)
    val authenticating = Property(false)
    val rememberDevice = Property(false)
    val desiredSessionLength = Property<Duration?>(1.days)
    val authResult = sharedSuspending {
        val proofs = proofs()
        if (proofs.isEmpty()) return@sharedSuspending null
        authenticating.value = true
        try {
            val result = endpoints.subjects.values.single().checkProofs(proofs())
            result
        } catch(e: LsErrorException) {
            if(e.status / 100 == 4) this@AuthComponent.proofs.value = listOf()
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
                field("Login code emailed to $id") {
                    row {
                        val tf: TextField
                        expanding - textInput {
                            tf = this
                            ::hint { "ABCDEF" }
                            requestFocus()
                            content bind code
                            keyboardHints = KeyboardHints.id
                        }
                        button {
                            spacing = 0.px
                            centered - icon(Icon.send, "Submit")
                            onClickAssociatedField(tf) {
                                onProof(p.proveEmailOwnership(FinishProof(codeKey, code())))
                            }
                        }
                    }
                }
                errorText()
                val newCodeSentAt = Property(now())
                val nowBySecond = readable { while(true) { emit(now()); delay(1000) } }
                button {
                    ::enabled { nowBySecond() !in newCodeSentAt() + 3.seconds .. newCodeSentAt() + resendTime }
                    centered - onlyWhen { nowBySecond() > newCodeSentAt() + resendTime } - text("Send new code")
                    centered - onlyWhen { nowBySecond() < newCodeSentAt() + 3.seconds } - row {
                        centered - icon(Icon.done.copy(1.rem, 1.rem), "")
                        centered - text("Sent!")
                    }
                    centered - onlyWhen { nowBySecond() in newCodeSentAt() + 3.seconds .. newCodeSentAt() + resendTime } - text{
                        ::content { "Can send new code in ${(newCodeSentAt() + resendTime - nowBySecond()).inWholeSeconds}" }
                    }
                    onClick {
                        if(nowBySecond() > newCodeSentAt() + resendTime) {
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
                field("Login code texted to $id") {
                    row {
                        val tf: TextField
                        expanding - textInput {
                            tf = this
                            ::hint { "ABCDEF" }
                            requestFocus()
                            content bind code
                            keyboardHints = KeyboardHints.id
                        }
                        button {
                            spacing = 0.px
                            centered - icon(Icon.send, "Submit")
                            onClickAssociatedField(tf) {
                                onProof(p.provePhoneOwnership(FinishProof(codeKey, code())))
                            }
                        }
                    }
                }
                errorText()
                val newCodeSentAt = Property(now())
                val nowBySecond = readable { while(true) { emit(now()); delay(1000) } }
                button {
                    ::enabled { nowBySecond() !in newCodeSentAt() + 3.seconds .. newCodeSentAt() + resendTime }
                    centered - onlyWhen { nowBySecond() > newCodeSentAt() + resendTime } - text("Send new code")
                    centered - onlyWhen { nowBySecond() < newCodeSentAt() + 3.seconds } - row {
                        centered - icon(Icon.done.copy(1.rem, 1.rem), "")
                        centered - text("Sent!")
                    }
                    centered - onlyWhen { nowBySecond() in newCodeSentAt() + 3.seconds .. newCodeSentAt() + resendTime } - text{
                        ::content { "Can send new code in ${(newCodeSentAt() + resendTime - nowBySecond()).inWholeSeconds}" }
                    }
                    onClick {
                        if(nowBySecond() > newCodeSentAt() + resendTime) {
                            codeKey = p.beginSmsOwnershipProof(id)
                            newCodeSentAt.value = now()
                        }
                    }
                }
            }
        }
    }
    inner class PasswordProof(val p: PasswordProofClientEndpoints, val type: String, val key: String, val value: String) : CurrentProof {
        val code = Property("")
        override fun ViewWriter.render(onProof: (Proof) -> Unit) {
            col {
                field("Password") {
                    row {
                        val tf: TextField
                        expanding - textInput {
                            tf = this
                            ::hint { "" }
                            requestFocus()
                            content bind code
                            keyboardHints = KeyboardHints.password
                        }
                        button {
                            spacing = 0.px
                            centered - icon(Icon.send, "Submit")
                            onClickAssociatedField(tf) {
                                onProof(p.provePasswordOwnership(IdentificationAndPassword(type, key, value, code())))
                            }
                        }
                    }
                }
                errorText()
            }
        }
    }
    inner class OtpProof(val p: OneTimePasswordProofClientEndpoints, val type: String, val key: String, val value: String) : CurrentProof {
        val code = Property("")
        override fun ViewWriter.render(onProof: (Proof) -> Unit) {
            col {
                field("One-time Password from App") {
                    row {
                        val tf: TextField
                        expanding - textInput {
                            tf = this
                            ::hint { "000000" }
                            requestFocus()
                            content bind code
                            keyboardHints = KeyboardHints.integer
                        }
                        button {
                            spacing = 0.px
                            centered - icon(Icon.send, "Submit")
                            onClickAssociatedField(tf) {
                                onProof(p.proveOTP(IdentificationAndPassword(type, key, value, code())))
                            }
                        }
                    }
                }
                errorText()
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
            field(when {
                endpoints.emailProof != null && endpoints.smsProof != null  -> "Email or Phone Number"
                endpoints.emailProof != null -> "Email"
                endpoints.smsProof != null -> "Phone Number"
                else -> "Username"
            }) {
                textInput {
                    primaryIdentifierField = this
                    hint = when {
                        endpoints.emailProof != null && endpoints.smsProof != null  -> "me@email.com OR 800-123-4567"
                        endpoints.emailProof != null -> "me@email.com"
                        endpoints.smsProof != null -> "800-123-4567"
                        else -> "MyUsername"
                    }
                    keyboardHints = when {
                        endpoints.emailProof != null && endpoints.smsProof != null  -> KeyboardHints.email
                        endpoints.emailProof != null -> KeyboardHints.email
                        endpoints.smsProof != null -> KeyboardHints.phone
                        else -> KeyboardHints.id
                    }
                    content bind primaryIdentifier
                    reactive {
                        if(currentProof() == null) requestFocus()
                    }
                }
            }

            val ratio = shared { authResult()?.let { proofs().sumOf { it.strength } / it.strengthRequired.toFloat() } ?: 0f }
            onlyWhen { ratio() in 0.001f..0.999f } - card - progressBar {
                ::ratio { authResult()?.let { proofs().sumOf { it.strength } / it.strengthRequired.toFloat() } ?: 0.01f }
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
                        } catch(e: Exception) {
                            authenticating.value = false
                            e.printStackTrace2()
                            knownDevice.value = null
                        }
                    }
                }
            }

            onlyWhen { currentProof() == null && !authenticating() && authResult()?.readyToLogIn != true } - col {

                onlyWhen { proofs().isNotEmpty() && authResult().let { it != null && !it.readyToLogIn } } - text("We need more information.")
                val validId = shared { Regexes.email.matches(primaryIdentifier()) || Regexes.phoneNumber.matches(primaryIdentifier()) }

                val emailStartAction = endpoints.emailProof?.let { p ->
                    val action = Action("Email Code", Icon.send) {
                        val id = email() ?: return@Action
                        currentProof.value = EmailProof(p, id, p.beginEmailOwnershipProof(id))
                    }
                    onlyWhen { proofs().none { it.property == "email" } && (authResult()?.options?.any { it.method.property == "email" } ?: true) && email() != null } - important - buttonTheme - button {
                        this.action = action
                        centered - text("Email Code")
                    }
                    action
                }

                val smsStartAction = endpoints.smsProof?.let { p ->
                    val action = Action("Text Code", Icon.send) {
                        val id = phone() ?: return@Action
                        currentProof.value = SmsProof(p, id, p.beginSmsOwnershipProof(id))
                    }
                    onlyWhen { proofs().none { it.property == "phone" } && (authResult()?.options?.any { it.method.property == "phone" } ?: true) && phone() != null } - important - buttonTheme - button {
                        this.action = action
                        centered - text("Text Code")
                    }
                    action
                }

                val passwordStartAction = endpoints.passwordProof?.let { p ->
                    val action = Action("Use Password", Icon.chevronRight) {
                        currentProof.value = PasswordProof(p, endpoints.subjects.keys.single(), when {
                            Regexes.email.matches(primaryIdentifier()) -> "email"
                            Regexes.phoneNumber.matches(primaryIdentifier()) -> "phone"
                            else -> "_id"
                        }, primaryIdentifier())
                    }
                    onlyWhen { proofs().none { it.via == "password" } && (authResult()?.options?.any { it.method.via == "password" } ?: true) && validId() } - important - buttonTheme - button {
                        centered - text("Use Password")
                        this.action = action
                    }
                    action
                }

                val otpAction = endpoints.oneTimePasswordProof?.let { p ->
                    val action = Action("Use Authenticator App", Icon.chevronRight) {
                        currentProof.value = OtpProof(p, endpoints.subjects.keys.single(), when {
                            Regexes.email.matches(primaryIdentifier()) -> "email"
                            Regexes.phoneNumber.matches(primaryIdentifier()) -> "phone"
                            else -> "_id"
                        }, primaryIdentifier())
                    }
                    onlyWhen { proofs().none { it.via == "otp" } && (authResult()?.options?.any { it.method.via == "otp" } ?: true) && validId() } - important - buttonTheme - button {
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
                        proofs().none { it.via == "otp" } && validId && otpAction != null -> otpAction
                        else -> null
                    }
                }
            }

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

            onlyWhen { authResult()?.readyToLogIn == true } - col {
                centered - affirmative - stack {
                    col {
                        spacing = 0.px
                        centered - icon(Icon.done, "")
                        centered - text("You're ready to go!")
                    }
                }
                onlyWhen { knownDeviceOptions() != null } - row {
                    centered - checkbox { checked bind rememberDevice }
                    centered - text {
                        content = "This is my device"
//                        ::content { "Remember this device for ${knownDeviceOptions()?.duration?.inWholeDays} days" }
                    }
                }
                onlyWhen { rememberDevice() || knownDeviceOptions() == null } - row {
                    centered - checkbox { checked bind desiredSessionLength.lens(
                        get = { it != 1.days },
                        set = { if(it) null else 1.days }
                    ) }
                    centered - text {
                        ::content {
                            val days = authResult()?.maxExpiration?.let { it - now() }?.toDouble(DurationUnit.DAYS)?.roundToInt()
                            if (days != null) "Keep me logged in for $days days" else "Keep me logged in"
                        }
                    }
                }
                col {
                    buttonTheme - important - button {
                        centered - text("Log In")
                        reactive {
                            if (authResult()?.readyToLogIn == true) requestFocus()
                        }
                        onClick {
                            println("Requesting full auth...")
                            val result = endpoints.subjects.values.single().logInV2(LogInRequest(
                                proofs = proofs(),
                                expires = desiredSessionLength()?.let { now() + it }
                            ))
                            println("Result: $result")
                            result.session?.let {
                                onAuthentication(it)
                                (AppScope + Dispatchers.Main).launch {
                                    if (rememberDevice()) {
                                        endpoints.authenticatedKnownDeviceProof?.establishKnownDeviceV2()?.let {
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
                    }
                    errorText()
                }
            }

            centered - onlyWhen { authenticating() } - row {
                activityIndicator()
                centered - text("Authenticating...")
            }
        }
    }
}
