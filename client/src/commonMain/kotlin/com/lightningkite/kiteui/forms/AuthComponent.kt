package com.lightningkite.kiteui.forms

import com.lightningkite.kiteui.ClientAuthenticator
import com.lightningkite.kiteui.WebAuthNMediationType
import com.lightningkite.kiteui.debugMode
import com.lightningkite.kiteui.exceptions.ExceptionHandlers
import com.lightningkite.kiteui.exceptions.ExceptionMessage
import com.lightningkite.kiteui.exceptions.ExceptionToMessage
import com.lightningkite.kiteui.exceptions.ExceptionToMessages
import com.lightningkite.kiteui.models.*
import com.lightningkite.kiteui.navigation.dialogPageNavigator
import com.lightningkite.kiteui.printStackTrace2
import com.lightningkite.kiteui.reactive.Action
import com.lightningkite.kiteui.reactive.PersistentProperty
import com.lightningkite.kiteui.views.*
import com.lightningkite.kiteui.views.direct.*
import com.lightningkite.kiteui.views.l2.dialog
import com.lightningkite.kiteui.views.l2.errorText
import com.lightningkite.kiteui.views.l2.field
import com.lightningkite.kiteui.views.l2.icon
import com.lightningkite.lightningserver.LSError
import com.lightningkite.lightningserver.LsErrorException
import com.lightningkite.lightningserver.auth.*
import com.lightningkite.lightningserver.auth.proof.FinishProof
import com.lightningkite.lightningserver.auth.proof.IdentificationAndPassword
import com.lightningkite.lightningserver.auth.proof.KnownDeviceOptions
import com.lightningkite.lightningserver.auth.proof.KnownDeviceSecretAndExpiration
import com.lightningkite.lightningserver.auth.proof.Proof
import com.lightningkite.lightningserver.auth.proof.WebAuthN
import com.lightningkite.lightningserver.auth.subject.LogInRequest
import com.lightningkite.lightningserver.auth.subject.ProofsCheckResult
import com.lightningkite.now
import com.lightningkite.readable.*
import kotlinx.coroutines.*
import kotlinx.serialization.Serializable
import kotlin.collections.plus
import kotlin.math.roundToInt
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes
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
    AuthComponent(
        endpoints,
        subjectPath,
        subject,
        knownDeviceLocalStorageName,
        onAuthentication
    ).apply {
        render()
    }
}

@Serializable
data class KnownDeviceSecretInfoStuff(
    val info: KnownDeviceSecretAndExpiration,
    val primaryIdentifier: String,
)


class EmailProof(val p: EmailProofClientEndpoints, val id: String, var codeKey: String) : CurrentProof {
    val code = Property("")
    val resendTime = 15.seconds
    override fun ViewWriter.render(onProof: (Proof) -> Unit, onException: (Exception) -> Unit) {
        col {
            val proveEmailOwnership = Action("Submit", Icon.done) {
                onProof(p.proveEmailOwnership(FinishProof(codeKey, code.await())))
            }

            field("Login code emailed to $id") {
                textInput {
                    ::hint { "ABCDEF" }
                    requestFocus()
                    content bind code
                    action = proveEmailOwnership
                    keyboardHints = KeyboardHints.id
                }
            }
            important - buttonTheme - button {
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
                centered - shownWhen { nowBySecond() > newCodeSentAt() + resendTime } - text("Send new code")
                centered - shownWhen { nowBySecond() < newCodeSentAt() + 3.seconds } - row {
                    centered - icon(Icon.done.copy(1.rem, 1.rem), "")
                    centered - text("Sent!")
                }
                centered - shownWhen { nowBySecond() in newCodeSentAt() + 3.seconds..newCodeSentAt() + resendTime } - text {
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


class SmsProof(val p: SmsProofClientEndpoints, val id: String, var codeKey: String) : CurrentProof {
    val code = Property("")
    val resendTime = 15.seconds
    override fun ViewWriter.render(onProof: (Proof) -> Unit, onException: (Exception) -> Unit) {
        col {
            val provePhoneOwnership = Action("Submit", Icon.done) {
                onProof(p.provePhoneOwnership(FinishProof(codeKey, code.await())))
            }
            field("Login code texted to $id") {
                expanding - textInput {
                    ::hint { "ABCDEF" }
                    requestFocus()
                    action = provePhoneOwnership
                    content bind code
                    keyboardHints = KeyboardHints.id
                }
            }
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
                centered - shownWhen { nowBySecond() > newCodeSentAt() + resendTime } - text("Send new code")
                centered - shownWhen { nowBySecond() < newCodeSentAt() + 3.seconds } - row {
                    centered - icon(Icon.done.copy(1.rem, 1.rem), "")
                    centered - text("Sent!")
                }
                centered - shownWhen { nowBySecond() in newCodeSentAt() + 3.seconds..newCodeSentAt() + resendTime } - text {
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

class PasswordProof(
    val p: PasswordProofClientEndpoints,
    val type: String,
    val key: String,
    val value: String,
) : CurrentProof {
    val code = Property("")
    override fun ViewWriter.render(onProof: (Proof) -> Unit, onException: (Exception) -> Unit) {
        val provePasswordOwnership = Action("Submit", Icon.done) {
            onProof(p.provePasswordOwnership(IdentificationAndPassword(type, key, value, code.await())))
        }
        col {
            field("Password") {
                textInput {
                    ::hint { "" }
                    requestFocus()
                    content bind code
                    action = provePasswordOwnership
                    keyboardHints = KeyboardHints.password
                }
            }
            important - button {
                centered - text("Submit")
                action = provePasswordOwnership
            }

        }
    }
}

class OtpProof(
    val p: OneTimePasswordProofClientEndpoints,
    val type: String,
    val key: String,
    val value: String,
) : CurrentProof {
    val code = Property("")
    override fun ViewWriter.render(onProof: (Proof) -> Unit, onException: (Exception) -> Unit) {
        col {
            val proveOtpProofAction = Action("Submit", Icon.done) {
                onProof(p.proveOTP(IdentificationAndPassword(type, key, value, code.await())))
            }
            field("One-time Password from App") {
                textInput {
                    ::hint { "000000" }
                    requestFocus()
                    content bind code
                    keyboardHints = KeyboardHints.integer
                    action = proveOtpProofAction
                }
            }
            button {
                centered - text("Submit")
                action = proveOtpProofAction
            }
        }
    }
}

class WebAuthNProof(
    val p: WebAuthNProofEndpoints,
    val type: String,
    val key: String?,
    val isPrimary: Boolean,
) : CurrentProof {
    override fun ViewWriter.render(onProof: (Proof) -> Unit, onException: (Exception) -> Unit) {
        important - buttonTheme - frame {

            launch {
                try {
                    val (key, getOptions) = p.start(
                        WebAuthN.Authentication.StartRequest(
                            subjectId = if (isPrimary) null else key,
                            subjectType = type
                        )
                    )
                    val signedChallenge =
                        ClientAuthenticator.getClientAuthenticator().getWebAuthNCredentials(
                            getOptions,
                            WebAuthNMediationType.Optional
                        )
                    val result = p.prove(
                        WebAuthN.Authentication.ProveRequest(
                            key,
                            signedChallenge
                        )
                    )
                    onProof(result)
                } catch (e: Exception) {
                    onException(Exception("Failed to prove ${if (isPrimary) "Passkey" else "Security Key"}"))
                }
            }

            row {
                expanding - space()
                centered - icon(Icon.passkey, "Passkey")
                centered - text {
                    ::content{
                        if (isPrimary) "Use a Passkey"
                        else "Use your Security Key"
                    }
                }
                expanding - space()
            }
            centered - activityIndicator()
        }
    }
}

interface CurrentProof {
    fun ViewWriter.render(onProof: (Proof) -> Unit, onException: (Exception) -> Unit)
}

class ReAuthComponent(
    val subjectId: String,
    val endpoints: AuthClientEndpoints,
    val subjectType: String,
    val subject: UserAuthClientEndpoints<*>,
    val authenticationSubject: AuthenticatedUserAuthClientEndpoints<*, *>,
    val knownDeviceLocalStorageName: String? = "known-device",
    val newSessionDuration: Duration = 15.minutes,
    val onAuthentication: suspend (String) -> Unit,
) {
    val proofs = Property<List<Proof>>(listOf())
    val currentProof = Property<CurrentProof?>(null)
    val authenticating = Property(false)
    val knownDevice = knownDeviceLocalStorageName?.let { PersistentProperty<KnownDeviceSecretInfoStuff?>(it, null) }
    val requirements = sharedSuspending {
        authenticationSubject.authRequirements()
    }
    val readyToLogin = Property(false)

    fun ViewWriter.render(): ViewModifiable {
        reactiveSuspending {
            val proofs = proofs.await()

            if (proofs.isEmpty()) return@reactiveSuspending
            authenticating.value = true
            try {
                val result = subject.checkProofs(proofs)
                readyToLogin.value = result.readyToLogIn
            } catch (e: LsErrorException) {
                if (e.status / 100 == 4) this@ReAuthComponent.proofs.value = listOf()
                if (e.status / 100 == 5) throw e
                null
            } finally {
                authenticating.value = false
            }
        }

        reactiveSuspending {
            if (!readyToLogin()) return@reactiveSuspending

            val result = subject.logInV2(
                LogInRequest(
                    proofs = proofs(),
                    expires = now() + newSessionDuration
                )
            )

            result.session?.also { onAuthentication(it) }

        }

        return col {
            shownWhen { proofs().isNotEmpty() } - card - progressBar {
                ::ratio {
                    proofs().sumOf { it.strength } / requirements().strengthRequired.toFloat()
                }
            }

            knownDevice?.value?.takeIf {
                now() < it.info.expiresAt
            }?.let { kd ->
                endpoints.knownDeviceProof?.let { endpoints ->
                    (this as CoroutineScope).launch {
                        try {
                            authenticating.value = true
                            proofs.value += endpoints.proveKnownDevice(kd.info.secret)
                        } catch (e: Exception) {
                            authenticating.value = false
                            e.printStackTrace2()
                            knownDevice.value = null
                        }
                    }
                }
            }

            shownWhen { currentProof() == null && !authenticating() && readyToLogin() != true } - col {

                shownWhen { proofs().isNotEmpty() } - text("We need more information.")

                endpoints.emailProof?.let { p ->
                    shownWhen {
                        proofs().none { it.via == "email" } &&
                                requirements().options.any { it.method.via == "email" }
                    } - important - buttonTheme - button {
                        this.action = Action("Email Code", Icon.send) {
                            val id = requirements().options.find { it.method.via == "email" }?.value ?: return@Action
                            currentProof.value = EmailProof(p, id, p.beginEmailOwnershipProof(id))
                        }
                        centered - text("Email Code")
                    }
                }

                endpoints.smsProof?.let { p ->
                    shownWhen {
                        proofs().none { it.via == "sms" } &&
                                requirements().options.any { it.method.via == "sms" }
                    } - important - buttonTheme - button {
                        this.action = Action("Text Code", Icon.send) {
                            val id = requirements().options.find { it.method.via == "sms" }?.value ?: return@Action
                            currentProof.value = SmsProof(p, id, p.beginSmsOwnershipProof(id))
                        }
                        centered - text("Text Code")
                    }
                }

                endpoints.passwordProof?.let { p ->
                    shownWhen {
                        proofs().none { it.via == "password" } &&
                                requirements().options.any { it.method.via == "password" }
                    } - important - buttonTheme - button {
                        centered - text("Use Password")
                        this.action = Action("Use Password", Icon.chevronRight) {
                            currentProof.value = PasswordProof(
                                p = p,
                                type = subjectType,
                                key = "_id",
                                value = subjectId
                            )
                        }
                    }
                }

                endpoints.oneTimePasswordProof?.let { p ->
                    shownWhen {
                        proofs().none { it.via == "otp" } &&
                                requirements().options.any { it.method.via == "otp" }
                    } - important - buttonTheme - button {
                        centered - text("Use Authenticator App")
                        this.action = Action("Use Authenticator App", Icon.chevronRight) {
                            currentProof.value = OtpProof(
                                p = p,
                                type = subjectType,
                                key = "${subjectType}/_id",
                                value = subjectId
                            )
                        }
                    }
                }

                // WebAuthN is only supported in Web at the moment.
                endpoints.webAuthNProof?.let { webAuthNProof ->

                    val isPrimary = shared {
                        endpoints.webAuthNIncludePasskeyUI &&
                                proofs().isEmpty()
                    }
                    val webAuthNAvailable = sharedSuspending {
                        ClientAuthenticator.getClientAuthenticator().webAuthNAvailable()
                    }

                    shownWhen {
                        webAuthNAvailable() &&
                                proofs().none { it.via == "WebAuthN" } &&
                                requirements().options.any { it.method.via == "WebAuthN" }
                    } - col {
                        important - buttonTheme - button {
                            row {
                                expanding - space()
                                centered - icon(Icon.passkey, "Passkey")
                                centered - text {
                                    ::content{
                                        if (isPrimary()) "Use a Passkey"
                                        else "Use your Security Key"
                                    }
                                }
                                expanding - space()
                            }
                            this.action = Action("Sign in with a Passkey", Icon.passkey) {
                                currentProof.value = WebAuthNProof(
                                    p = webAuthNProof,
                                    type = subjectType,
                                    key = subjectId,
                                    isPrimary = isPrimary.await()
                                )
                            }
                        }
                    }
                }
            }

            frame {
                reactive {
                    clearChildren()
                    currentProof()?.run {
                        var callBackDone = false
                        render(
                            onProof = {
                                if (callBackDone) return@render
                                callBackDone = true

                                proofs.value += it
                                currentProof.value = null
                            },
                            onException = {
                                if (callBackDone) return@render
                                callBackDone = true

                                currentProof.value = null
                                this@frame.dialog { close ->
                                    col {
                                        h2("Error")
                                        text(it.message ?: "???")
                                        row {
                                            expanding - space()
                                            buttonTheme - button {
                                                text("OK")
                                                onClick { close() }
                                            }
                                        }
                                    }
                                }
                            }
                        )
                    }
                }
            }

            shownWhen { currentProof() != null } - button {
                subtext {
                    content = "Use a different Method"
                    align = Align.Center
                }
                onClick {
                    currentProof.value = null
                }
            }

            centered - shownWhen { authenticating() } - row {
                activityIndicator()
                centered - text("Authenticating...")
            }
        }
    }
}


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
    val authResult: Readable<ProofsCheckResult<out Comparable<*>>?> = sharedSuspending {
        val proofs = proofs.await()

        if (proofs.isEmpty()) return@sharedSuspending null
        authenticating.value = true
        try {
            subject.checkProofs(proofs)
        } catch (e: LsErrorException) {
            if (e.status / 100 == 4) this@AuthComponent.proofs.value = listOf()
            if (e.status / 100 == 5) throw e
            null
        } finally {
            authenticating.value = false
        }
    }
    val knownDevice = knownDeviceLocalStorageName?.let { PersistentProperty<KnownDeviceSecretInfoStuff?>(it, null) }
    val knownDeviceOptions = sharedSuspending {
        endpoints.knownDeviceProof?.knownDeviceOptions()
    }

    fun ViewWriter.render(): ViewModifiable {
        return col {
            val primaryIdentifierField: TextField
            shownWhen { proofs().isEmpty() && currentProof() == null } - field(
                when {
                    endpoints.emailProof != null && endpoints.smsProof != null -> "Email or Phone Number"
                    endpoints.emailProof != null -> "Email"
                    endpoints.smsProof != null -> "Phone Number"
                    else -> "Username"
                }
            ) {
                val autoFillAvailable =
                    sharedSuspending { ClientAuthenticator.getClientAuthenticator().autofillAvailable() }
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

                            if (endpoints.webAuthNProof != null && autoFillAvailable()
                            )
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


            shownWhen { proofs().isNotEmpty() || currentProof() != null } - row {
                expanding - centered - text {
                    ::content{ primaryIdentifier().takeIf { it.isNotEmpty() } ?: "Using Passkey" }
                }
                centered - button {
                    padding = 0.2.rem
                    icon(Icon.close, "Restart Login")
                    onClick {
                        currentProof.value = null
                        primaryIdentifier.value = ""
                        rememberDevice.value = false
                        desiredSessionLength.value = 1.days
                        proofs.value = emptyList()
                    }
                }
            }

            shownWhen { proofs().isNotEmpty() } - card - progressBar {
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

            shownWhen { currentProof() == null && !authenticating() && authResult()?.readyToLogIn != true } - col {

                shownWhen { proofs().isNotEmpty() } - text("We need more information.")
                val validId =
                    shared { Regexes.email.matches(primaryIdentifier()) || Regexes.phoneNumber.matches(primaryIdentifier()) }

                val emailStartAction = endpoints.emailProof?.let { p ->
                    val action = Action("Email Code", Icon.send) {
                        val id = email.await() ?: return@Action
                        currentProof.value = EmailProof(p, id, p.beginEmailOwnershipProof(id))
                    }
                    shownWhen {
                        proofs().none { it.via == "email" } && (authResult()?.options?.any { it.method.via == "email" }
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
                        currentProof.value = SmsProof(p, id, p.beginSmsOwnershipProof(id))
                    }
                    shownWhen {
                        proofs().none { it.via == "sms" } &&
                                (authResult()?.options?.any { it.method.via == "sms" }
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
                            p = p,
                            type = endpoints.subjects.keys.single(),
                            key = when {
                                Regexes.email.matches(primaryIdentifier.await()) -> "email"
                                Regexes.phoneNumber.matches(primaryIdentifier.await()) -> "phone"
                                else -> "_id"
                            },
                            value = primaryIdentifier.await()
                        )
                    }
                    shownWhen {
                        proofs().none { it.via == "password" } &&
                                (authResult()?.options?.any { it.method.via == "password" } ?: true) &&
                                validId()
                    } - important - buttonTheme - button {
                        centered - text("Use Password")
                        this.action = action
                    }
                    action
                }

                val otpAction = endpoints.oneTimePasswordProof?.let { p ->
                    val action = Action("Use Authenticator App", Icon.chevronRight) {
                        currentProof.value = OtpProof(
                            p = p,
                            type = endpoints.subjects.keys.single(),
                            key = when {
                                Regexes.email.matches(primaryIdentifier.await()) -> "email"
                                Regexes.phoneNumber.matches(primaryIdentifier.await()) -> "phone"
                                else -> "_id"
                            },
                            value = primaryIdentifier.await()
                        )
                    }
                    shownWhen {
                        proofs().none { it.via == "otp" } &&
                                (authResult()?.options?.any { it.method.via == "otp" } ?: true) &&
                                validId()
                    } - important - buttonTheme - button {
                        this.action = action
                        centered - text("Use Authenticator App")
                    }
                    action
                }

                // WebAuthN is only supported in Web at the moment.
                endpoints.webAuthNProof?.let { webAuthNProof ->

                    val pendingWebauthnRequest = launch {
                        if (!ClientAuthenticator.getClientAuthenticator().autofillAvailable()) return@launch

                        val response = webAuthNProof.start(
                            WebAuthN.Authentication.StartRequest(
                                null,
                                endpoints.subjects.keys.single()
                            )
                        )
                        val signedChallenge = ClientAuthenticator.getClientAuthenticator()
                            .getWebAuthNCredentials(response.options, WebAuthNMediationType.Conditional)

                        proofs.value += webAuthNProof
                            .prove(WebAuthN.Authentication.ProveRequest(response.challengeId, signedChallenge))
                    }

                    val isPrimary = shared {
                        endpoints.webAuthNIncludePasskeyUI &&
                                proofs().isEmpty() &&
                                phone() == null &&
                                email() == null
                    }

                    val webAuthNAvailable = sharedSuspending {
                        ClientAuthenticator.getClientAuthenticator().webAuthNAvailable()
                    }

                    shownWhen {
                        webAuthNAvailable() &&
                                proofs().none { it.via == "WebAuthN" } &&
                                (isPrimary() ||
                                        authResult()?.options?.any { it.method.via == "WebAuthN" } == true)
                    } - col {
                        centered - shownWhen { isPrimary() } - text("Or")

                        important - buttonTheme - button {
                            row {
                                expanding - space()
                                centered - icon(Icon.passkey, "Passkey")
                                centered - text {
                                    ::content{
                                        if (isPrimary()) "Use a Passkey"
                                        else "Use your Security Key"
                                    }
                                }
                                expanding - space()
                            }

                            this.action = Action("Sign in with a Passkey", Icon.passkey) {
                                val identity = authResult.awaitOnce()?.id?.toString()
                                val type = endpoints.subjects.keys.single()

                                currentProof.value = WebAuthNProof(
                                    p = webAuthNProof,
                                    type = type,
                                    key = identity,
                                    isPrimary = isPrimary.await()
                                )
                            }
                        }
                    }
                }

                primaryIdentifierField::action {
                    val id = primaryIdentifier()
                    val validId = Regexes.email.matches(id) || Regexes.phoneNumber.matches(id)
                    when {
                        proofs().none { it.via.lowercase() == "email" } && email() != null && emailStartAction != null -> emailStartAction
                        proofs().none { it.via.lowercase() == "sms" } && phone() != null && smsStartAction != null -> smsStartAction
                        proofs().none { it.via.lowercase() == "password" } && validId && passwordStartAction != null -> passwordStartAction
                        proofs().none { it.via == "otp" } && validId && otpAction != null -> otpAction
                        else -> null
                    }
                }

            }

            frame {
                reactive {
                    clearChildren()
                    currentProof()?.run {
                        var callBackDone = false
                        render(
                            onProof = {
                                if (callBackDone) return@render
                                callBackDone = true

                                proofs.value += it
                                currentProof.value = null
                            },
                            onException = {
                                if (callBackDone) return@render
                                callBackDone = true

                                currentProof.value = null
                                this@frame.dialog { close ->
                                    col {
                                        h2("Error")
                                        text(it.message ?: "???")
                                        row {
                                            expanding - space()
                                            buttonTheme - button {
                                                text("OK")
                                                onClick { close() }
                                            }
                                        }
                                    }
                                }
                            }
                        )
                    }
                }
            }
            centered - shownWhen { authenticating() } - row {
                activityIndicator()
                centered - text("Authenticating...")
            }

            shownWhen { authResult()?.readyToLogIn == true } - col {

                centered - h5("Ready to login")
                sessionLengthComponent(knownDeviceOptions, rememberDevice, desiredSessionLength, authResult)


                important - buttonTheme - button {
                    centered - text("Login")
                    onClick {

                        val result = subject.logInV2(
                            LogInRequest(
                                proofs = proofs(),
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
                }
            }
        }
    }
}

fun ViewWriter.sessionLengthComponent(
    knownDeviceOptions: Readable<KnownDeviceOptions?>,
    rememberDevice: Property<Boolean>,
    desiredSessionLength: Property<Duration?>,
    authResult: Readable<ProofsCheckResult<out Comparable<*>>?>,
) {
    shownWhen { knownDeviceOptions() != null } - row {
        centered - checkbox { checked bind rememberDevice }
        centered - text {
            content = "This is my device"
//                        ::content { "Remember this device for ${knownDeviceOptions()?.duration?.inWholeDays} days" }
        }
    }
    shownWhen { rememberDevice() || knownDeviceOptions() == null } - row {
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
