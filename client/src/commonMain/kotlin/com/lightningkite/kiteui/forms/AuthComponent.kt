package com.lightningkite.kiteui.forms

import com.lightningkite.kiteui.ClientAuthenticator
import com.lightningkite.kiteui.WebAuthNMediationType
import com.lightningkite.kiteui.models.Align
import com.lightningkite.kiteui.models.Icon
import com.lightningkite.kiteui.models.KeyboardHints
import com.lightningkite.kiteui.models.rem
import com.lightningkite.kiteui.printStackTrace2
import com.lightningkite.kiteui.reactive.Action
import com.lightningkite.kiteui.reactive.PersistentProperty
import com.lightningkite.kiteui.views.*
import com.lightningkite.kiteui.views.direct.*
import com.lightningkite.kiteui.views.l2.dialog
import com.lightningkite.kiteui.views.l2.field
import com.lightningkite.kiteui.views.l2.icon
import com.lightningkite.lightningserver.LsErrorException
import com.lightningkite.lightningserver.auth.*
import com.lightningkite.lightningserver.sessions.proofs.*
import com.lightningkite.lightningserver.sessions.LogInRequest
import com.lightningkite.lightningserver.sessions.ProofsCheckResult
import com.lightningkite.reactive.context.*
import com.lightningkite.reactive.core.*
import kotlinx.coroutines.*
import kotlinx.serialization.Serializable
import kotlin.math.roundToInt
import kotlin.time.Clock.System.now
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.DurationUnit

/**
 * Regex patterns for validating user identifiers.
 * These are used to auto-detect identifier type from user input.
 */
private object Regexes {
    /** Email address pattern matching */
    val email = Regex("""[\w\-+._]+@(?:[\w\-]+\w\.)+[\w\-]+\w$""")

    /** Phone number pattern (US format with optional dashes) */
    // TODO: This pattern only supports US phone numbers (XXX-XXX-XXXX)
    // International phone numbers will not be recognized. Consider using libphonenumber.
    val phoneNumber = Regex("^\\d{3}-?\\d{3}-?\\d{4}$")
}

/**
 * DSL function to render a legacy authentication component in a ViewWriter.
 *
 * NOTE: This is the legacy auth component. Consider using [authComponent2] instead
 * for the newer proof-based authentication flow with better UX and features.
 *
 * @param endpoints The authentication endpoints configuration from the server
 * @param subjectPath The type of subject being authenticated (e.g., "user", "admin")
 * @param subject The specific authentication client endpoints for this subject type
 * @param knownDeviceLocalStorageName Key for storing known device credentials. Null disables feature.
 * @param onAuthentication Callback invoked with refresh token on successful authentication
 * @see authComponent2 for the newer implementation
 */
@ViewDsl
fun ViewWriter.login(
    endpoints: AuthEndpoints,
    subjectPath: String = endpoints.subjects.keys.single(),
    subject: AuthClientEndpoints<*, *> = endpoints.subjects[subjectPath]!!,
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

/**
 * Stores known device information for persistent authentication.
 *
 * When a user chooses to "remember this device", this data is saved to local storage
 * allowing future logins to skip certain authentication proofs.
 *
 * @property info The device secret and expiration time from the server
 * @property primaryIdentifier The user's primary identifier (email/phone) for this device
 */
@Serializable
data class KnownDeviceSecretInfoStuff(
    val info: KnownDeviceSecretAndExpiration,
    val primaryIdentifier: String,
)


/**
 * Legacy email verification proof component.
 *
 * Renders UI for entering an email verification code sent to the user's email address.
 * Includes a resend mechanism with countdown timer.
 *
 * @property p The email proof endpoints for sending codes and verification
 * @property id The email address to send the code to
 * @property codeKey The current verification session key (updated on resend)
 */
class EmailProof(val p: ProofClientEndpoints.Email, val id: String, var codeKey: String) : CurrentProof {
    /** User's input for the verification code */
    val code = Signal("")

    /** Minimum time between code resend requests */
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
            val newCodeSentAt = Signal(now())
            val nowBySecond = reactiveProcess {
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


/**
 * Legacy SMS verification proof component.
 *
 * Renders UI for entering an SMS verification code sent to the user's phone number.
 * Includes a resend mechanism with countdown timer.
 *
 * @property p The SMS proof endpoints for sending codes and verification
 * @property id The phone number to send the code to
 * @property codeKey The current verification session key (updated on resend)
 */
class SmsProof(val p: ProofClientEndpoints.Sms, val id: String, var codeKey: String) : CurrentProof {
    /** User's input for the verification code */
    val code = Signal("")

    /** Minimum time between code resend requests */
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
            val newCodeSentAt = Signal(now())
            val nowBySecond = reactiveProcess {
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

/**
 * Legacy password authentication proof component.
 *
 * Renders UI for entering a password to authenticate.
 *
 * @property p The password proof endpoints for verification
 * @property type The subject type being authenticated (e.g., "user")
 * @property key The identifier type ("email", "phone", or "_id")
 * @property value The identifier value (email address, phone number, or user ID)
 */
class PasswordProof(
    val p: ProofClientEndpoints.Password,
    val type: String,
    val key: String,
    val value: String,
) : CurrentProof {
    /** User's input for the password */
    val code = Signal("")

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

/**
 * Legacy TOTP (Time-based One-Time Password) proof component.
 *
 * Renders UI for entering a 6-digit code from an authenticator app (e.g., Google Authenticator).
 *
 * @property p The TOTP proof endpoints for verification
 * @property type The subject type being authenticated (e.g., "user")
 * @property key The identifier type ("email", "phone", or "_id")
 * @property value The identifier value (email address, phone number, or user ID)
 */
class TotpProof(
    val p: ProofClientEndpoints.TimeBasedOTP,
    val type: String,
    val key: String,
    val value: String,
) : CurrentProof {
    /** User's input for the 6-digit TOTP code */
    val code = Signal("")

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

/**
 * Legacy backup code proof component.
 *
 * Renders UI for entering a backup recovery code (typically used when primary 2FA method unavailable).
 *
 * @property p The backup code proof endpoints for verification
 * @property type The subject type being authenticated (e.g., "user")
 * @property key The identifier type ("email", "phone", or "_id")
 * @property value The identifier value (email address, phone number, or user ID)
 */
class BackupCodeProof(
    val p: ProofClientEndpoints.BackupCode,
    val type: String,
    val key: String,
    val value: String,
) : CurrentProof {
    /** User's input for the backup code */
    val code = Signal("")

    override fun ViewWriter.render(onProof: (Proof) -> Unit, onException: (Exception) -> Unit) {
        col {
            val proveBackupCodeProofAction = Action("Submit", Icon.done) {
                onProof(p.proveBackupCode(IdentificationAndPassword(type, key, value, code.await())))
            }
            field("Backup Code") {
                textInput {
                    ::hint { "xxxxx-xxxxx-xxxxx-xxxxx" }
                    requestFocus()
                    content bind code
                    action = proveBackupCodeProofAction
                }
            }
            button {
                centered - text("Submit")
                action = proveBackupCodeProofAction
            }
        }
    }
}

/**
 * Legacy WebAuthN (passkey/security key) proof component.
 *
 * Automatically initiates WebAuthN authentication when rendered. Shows a loading indicator
 * while waiting for the user to interact with their security key or passkey.
 *
 * @property p The WebAuthN proof endpoints for challenge/response flow
 * @property type The subject type being authenticated (e.g., "user")
 * @property property The identifier property type (e.g., "user/_id")
 * @property key The identifier value (user ID or null for discoverable credentials)
 * @property isPrimary Whether this is primary authentication (passkey) vs secondary (security key)
 */
class WebAuthNProof(
    val p: ProofClientEndpoints.WebAuthN,
    val type: String,
    val property: String?,
    val key: String?,
    val isPrimary: Boolean,
) : CurrentProof {
    override fun ViewWriter.render(onProof: (Proof) -> Unit, onException: (Exception) -> Unit) {
        important - buttonTheme - frame {

            launch {
                try {
                    val (key, getOptions) = p.start(
                        Identification(
                            type = type,
                            property = property,
                            value = key,
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

/**
 * Interface for legacy proof components.
 *
 * Each implementation represents a specific authentication method and knows how to
 * render its own UI and handle user interaction.
 *
 * NOTE: This is the legacy interface. New code should use [ProofComponent] instead.
 *
 * @see ProofComponent for the modern interface
 */
interface CurrentProof {
    /**
     * Renders the proof collection UI.
     *
     * @param onProof Callback invoked with the collected proof on success
     * @param onException Callback invoked if proof collection fails
     */
    fun ViewWriter.render(onProof: (Proof) -> Unit, onException: (Exception) -> Unit)
}

/**
 * Legacy re-authentication component for elevated permissions.
 *
 * Used when a logged-in user needs to re-verify their identity for sensitive operations.
 * Only shows proof methods that the user has already set up (no registration flow).
 *
 * The component automatically attempts to use known device credentials if available,
 * then prompts for additional proofs as needed to meet authentication requirements.
 *
 * @property subjectId The ID of the subject (user) to re-authenticate
 * @property endpoints The authentication endpoints configuration
 * @property subjectType The type of subject being re-authenticated (e.g., "user")
 * @property subject The specific authentication client endpoints
 * @property knownDeviceLocalStorageName Key for loading known device credentials
 * @property newSessionDuration How long the new elevated session should last (default 15 minutes)
 * @property onAuthentication Callback invoked with refresh token on successful re-authentication
 */
class ReAuthComponent(
    val subjectId: String,
    val endpoints: AuthEndpoints,
    val subjectType: String,
    val subject: AuthClientEndpoints<*, *>,
    val knownDeviceLocalStorageName: String? = "known-device",
    val newSessionDuration: Duration = 15.minutes,
    val onAuthentication: suspend (String) -> Unit,
) {
    /** List of successfully collected proofs */
    val proofs = Signal<List<Proof>>(listOf())

    /** Currently active proof component being rendered */
    val currentProof = Signal<CurrentProof?>(null)

    /** Whether authentication is in progress (checking proofs with server) */
    val authenticating = Signal(false)

    /** Known device credentials loaded from local storage */
    val knownDevice = knownDeviceLocalStorageName?.let { PersistentProperty<KnownDeviceSecretInfoStuff?>(it, null) }

    /** Server's authentication requirements for this user */
    val requirements = rememberSuspending { subject.authRequirements() }

    /** Whether authentication requirements have been met */
    val readyToLogin = Signal(false)

    /**
     * Renders the re-authentication UI.
     *
     * The UI flow:
     * 1. Attempts known device authentication (if available)
     * 2. Shows progress bar for collected proofs
     * 3. Displays available proof method buttons
     * 4. Renders active proof component
     * 5. Automatically logs in when requirements met
     */
    fun ViewWriter.render(): ViewModifiable {
        // Automatically check proofs with server as they're collected
        reactiveSuspending {
            val proofs = proofs.await()

            if (proofs.isEmpty()) return@reactiveSuspending
            authenticating.value = true
            try {
                val result = subject.checkProofs(proofs)
                readyToLogin.value = result.readyToLogIn
            } catch (e: LsErrorException) {
                // Client errors (4xx) = invalid proofs, clear and restart
                if (e.status / 100 == 4) this@ReAuthComponent.proofs.value = listOf()
                // Server errors (5xx) = propagate to caller
                if (e.status / 100 == 5) throw e
                null
            } finally {
                authenticating.value = false
            }
        }

        // Automatically complete login when ready
        reactiveSuspending {
            if (!readyToLogin()) return@reactiveSuspending

            val result = subject.logInV2(
                LogInRequest(
                    proofs = proofs(),
                    expires = now() + newSessionDuration
                )
            )

            // TODO: Handle case where logInV2 succeeds but refreshToken is null
            // This should not happen but the null safety suggests it's possible
            result.refreshToken?.also { onAuthentication(it) }
        }

        return col {
            shownWhen { proofs().isNotEmpty() } - card - progressBar {
                ::ratio {
                    proofs().sumOf { it.strength } / requirements().strengthRequired.toFloat()
                }
            }

            // Attempt automatic known device authentication if available
            knownDevice?.value?.takeIf {
                now() < it.info.expiresAt
            }?.let { kd ->
                endpoints.knownDeviceProof?.let { endpoints ->
                    // TODO: This coroutine launches without tracking/cleanup
                    // If component unmounts before completion, may leak or cause issues
                    (this as CoroutineScope).launch {
                        try {
                            authenticating.value = true
                            proofs.value += endpoints.proveKnownDevice(kd.info.secret)
                        } catch (e: Exception) {
                            authenticating.value = false
                            e.printStackTrace2()
                            // Clear invalid known device
                            knownDevice.value = null
                        }
                    }
                }
            }

            shownWhen { currentProof() == null && !authenticating() && readyToLogin() != true } - col {

                shownWhen { proofs().isNotEmpty() } - text("We need more information.")

                endpoints.emailProof?.let { p ->
                    shownWhen {
                        proofs().none { it.via == p.via } &&
                                requirements().options.any { it.method.via == p.via }
                    } - important - buttonTheme - button {
                        this.action = Action("Email Code", Icon.send) {
                            val id = requirements().options.find { it.method.via == p.via }?.value ?: return@Action
                            currentProof.value = EmailProof(p, id, p.beginEmailOwnershipProof(id))
                        }
                        centered - text("Email Code")
                    }
                }

                endpoints.smsProof?.let { p ->
                    shownWhen {
                        proofs().none { it.via == p.via } &&
                                requirements().options.any { it.method.via == p.via }
                    } - important - buttonTheme - button {
                        this.action = Action("Text Code", Icon.send) {
                            val id = requirements().options.find { it.method.via == p.via }?.value ?: return@Action
                            currentProof.value = SmsProof(p, id, p.beginSmsOwnershipProof(id))
                        }
                        centered - text("Text Code")
                    }
                }

                endpoints.passwordProof?.let { p ->
                    shownWhen {
                        proofs().none { it.via == p.via } &&
                                requirements().options.any { it.method.via == p.via }
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
                        proofs().none { it.via == p.via } &&
                                requirements().options.any { it.method.via == p.via }
                    } - important - buttonTheme - button {
                        centered - text("Use Authenticator App")
                        this.action = Action("Use Authenticator App", Icon.chevronRight) {
                            currentProof.value = TotpProof(
                                p = p,
                                type = subjectType,
                                key = "${subjectType}/_id",
                                value = subjectId
                            )
                        }
                    }
                }

                endpoints.backupCodeProof?.let { p ->
                    shownWhen {
                        proofs().none { it.via == p.via } &&
                                requirements().options.any { it.method.via == p.via }
                    } - important - buttonTheme - button {
                        centered - text("Use Backup Code")
                        this.action = Action("Use Backup Code", Icon.chevronRight) {
                            currentProof.value = BackupCodeProof(
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

                    val isPrimary = remember {
                        endpoints.webAuthNIncludePasskeyUI &&
                                proofs().isEmpty()
                    }
                    val webAuthNAvailable = rememberSuspending {
                        ClientAuthenticator.getClientAuthenticator().webAuthNAvailable()
                    }

                    shownWhen {
                        webAuthNAvailable() &&
                                proofs().none { it.via == webAuthNProof.via } &&
                                requirements().options.any { it.method.via == webAuthNProof.via }
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
                                    property = "${subjectType}/_id",
                                    key = subjectId,
                                    isPrimary = isPrimary.await()
                                )
                            }
                        }
                    }
                }
            }

            // Render the active proof component
            frame {
                reactive {
                    clearChildren()
                    currentProof()?.run {
                        // Flag to prevent double-callback (race condition protection)
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
                                // Show error dialog to user
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


/**
 * Legacy authentication component for login and registration flows.
 *
 * This is the older authentication implementation. Consider using [AuthComponent2] instead
 * for improved UX and better progressive proof collection.
 *
 * Key differences from AuthComponent2:
 * - Uses different UI patterns for proof selection
 * - Separate render methods for each proof type
 * - Different primary identifier auto-detection logic
 * - Less flexible filtering of proof methods
 *
 * @property endpoints The authentication endpoints configuration
 * @property subjectPath The type of subject being authenticated (e.g., "user")
 * @property subject The specific authentication client endpoints
 * @property knownDeviceLocalStorageName Key for storing known device credentials
 * @property onAuthentication Callback invoked with refresh token on success
 * @see AuthComponent2 for the modern implementation
 */
class AuthComponent(
    val endpoints: AuthEndpoints,
    val subjectPath: String = endpoints.subjects.keys.single(),
    val subject: AuthClientEndpoints<*, *> = endpoints.subjects[subjectPath]!!,
    val knownDeviceLocalStorageName: String? = "known-device",
    val onAuthentication: suspend (String) -> Unit,
) {
    /** User's primary identifier input (email/phone/username) */
    val primaryIdentifier = Signal("")

    /** Extracted/validated phone number from primary identifier or auth result */
    val phone = remember {
        primaryIdentifier().takeIf { Regexes.phoneNumber.matches(it) }?.filter { it.isDigit() }
            ?: authResult()?.options?.find { it.method.property == "phone" }?.value
    }

    /** Extracted/validated email from primary identifier or auth result */
    val email = remember {
        primaryIdentifier().takeIf { Regexes.email.matches(it) }
            ?: authResult()?.options?.find { it.method.property == "email" }?.value
    }

    /** List of successfully collected proofs */
    val proofs = Signal<List<Proof>>(listOf())

    /** Currently active proof component being rendered */
    val currentProof = Signal<CurrentProof?>(null)

    /** Whether authentication is in progress */
    val authenticating = Signal(false)

    /** Whether user wants to remember this device */
    val rememberDevice = Signal(false)

    /** User's desired session length (1 day vs maximum) */
    val desiredSessionLength = Signal<Duration?>(1.days)

    /** Current error state, if any */
    val error = Signal<Exception?>(null)

    /**
     * Server validation result for collected proofs.
     * Automatically re-checks when proofs change.
     */
    val authResult: Reactive<ProofsCheckResult<out Comparable<*>>?> = rememberSuspending {
        val proofs = proofs.await()

        if (proofs.isEmpty()) {
            error.value = null
            return@rememberSuspending null
        }
        authenticating.value = true
        try {
            subject.checkProofs(proofs).also {
                error.value = null
            }
        } catch (e: LsErrorException) {
            // Client errors (4xx) = invalid proofs, clear and restart
            if (e.status / 100 == 4) {
                this@AuthComponent.proofs.value = listOf()
            }
            error.value = e
            null
        } catch (e: Exception) {
            error.value = e
            null
        } finally {
            authenticating.value = false
        }
    }

    /** Known device credentials loaded from local storage */
    val knownDevice = knownDeviceLocalStorageName?.let { PersistentProperty<KnownDeviceSecretInfoStuff?>(it, null) }

    /** Server's known device configuration options */
    val knownDeviceOptions = rememberSuspending {
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
                    rememberSuspending { ClientAuthenticator.getClientAuthenticator().autofillAvailable() }
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

            // Attempt automatic known device authentication if available
            knownDevice?.value?.takeIf {
                now() < it.info.expiresAt
            }?.let { kd ->
                endpoints.knownDeviceProof?.let { endpoints ->
                    // TODO: This coroutine launches without tracking/cleanup
                    // If component unmounts before completion, may leak or cause issues
                    (this as CoroutineScope).launch {
                        try {
                            authenticating.value = true
                            primaryIdentifier.value = kd.primaryIdentifier
                            proofs.value += endpoints.proveKnownDevice(kd.info.secret)
                        } catch (e: Exception) {
                            authenticating.value = false
                            e.printStackTrace2()
                            // Clear invalid known device
                            knownDevice.value = null
                        }
                    }
                }
            }

            shownWhen { currentProof() == null && !authenticating() && authResult()?.readyToLogIn != true } - col {

                shownWhen { proofs().isNotEmpty() } - text("We need more information.")
                val validId =
                    remember { Regexes.email.matches(primaryIdentifier()) || Regexes.phoneNumber.matches(primaryIdentifier()) }

                val emailStartAction = endpoints.emailProof?.let { p ->
                    val action = Action("Email Code", Icon.send) {
                        val id = email.await() ?: return@Action
                        currentProof.value = EmailProof(p, id, p.beginEmailOwnershipProof(id))
                    }
                    shownWhen {
                        proofs().none { it.via == p.via } && (authResult()?.options?.any { it.method.via == p.via }
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
                        proofs().none { it.via == p.via } &&
                                (authResult()?.options?.any { it.method.via == p.via }
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
                        proofs().none { it.via == p.via } &&
                                (authResult()?.options?.any { it.method.via == p.via } ?: true) &&
                                validId()
                    } - important - buttonTheme - button {
                        centered - text("Use Password")
                        this.action = action
                    }
                    action
                }

                val totpAction = endpoints.oneTimePasswordProof?.let { p ->
                    val action = Action("Use Authenticator App", Icon.chevronRight) {
                        currentProof.value = TotpProof(
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
                        proofs().none { it.via == p.via } &&
                                (authResult()?.options?.any { it.method.via == p.via } ?: true) &&
                                validId()
                    } - important - buttonTheme - button {
                        this.action = action
                        centered - text("Use Authenticator App")
                    }
                    action
                }


                val backupCodeAction = endpoints.backupCodeProof?.let { p ->
                    val action = Action("Use Backup Code", Icon.chevronRight) {
                        currentProof.value = BackupCodeProof(
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
                        proofs().none { it.via == p.via } &&
                                (authResult()?.options?.any { it.method.via == p.via } ?: true) &&
                                validId()
                    } - important - buttonTheme - button {
                        this.action = action
                        centered - text("Use Backup Code")
                    }
                    action
                }

                // WebAuthN is only supported in Web at the moment.
                endpoints.webAuthNProof?.let { webAuthNProof ->

                    // Background task for WebAuthN autofill (conditional UI)
                    // This allows passkeys to appear in autofill without explicit user action
                    val pendingWebauthnRequest = launch {
                        if (!ClientAuthenticator.getClientAuthenticator().autofillAvailable()) return@launch

                        val response = webAuthNProof.start(
                            Identification(
                                endpoints.subjects.keys.single(),
                                null,
                                null,
                            )
                        )
                        // Conditional mode = shows passkey in autofill UI
                        val signedChallenge = ClientAuthenticator.getClientAuthenticator()
                            .getWebAuthNCredentials(response.options, WebAuthNMediationType.Conditional)

                        // If user selects from autofill, add proof automatically
                        proofs.value += webAuthNProof
                            .prove(WebAuthN.Authentication.ProveRequest(response.challengeId, signedChallenge))
                    }

                    val isPrimary = remember {
                        endpoints.webAuthNIncludePasskeyUI &&
                                proofs().isEmpty() &&
                                phone() == null &&
                                email() == null
                    }

                    val webAuthNAvailable = rememberSuspending {
                        ClientAuthenticator.getClientAuthenticator().webAuthNAvailable()
                    }

                    shownWhen {
                        webAuthNAvailable() &&
                                proofs().none { it.via == webAuthNProof.via } &&
                                (isPrimary() ||
                                        authResult()?.options?.any { it.method.via == webAuthNProof.via } == true)
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
                                val type = endpoints.subjects.keys.single()
                                pendingWebauthnRequest.cancel()
                                currentProof.value = WebAuthNProof(
                                    p = webAuthNProof,
                                    type = type,
                                    property = when {
                                        authResult.awaitOnce()?.id != null -> "$type/_id"
                                        Regexes.email.matches(primaryIdentifier.await()) -> "email"
                                        Regexes.phoneNumber.matches(primaryIdentifier.await()) -> "phone"
                                        else -> "_id"
                                    },
                                    key = authResult.awaitOnce()?.id?.toString() ?: primaryIdentifier.await(),
                                    isPrimary = isPrimary.await()
                                )
                            }
                        }
                    }
                }

                // Automatically determine which action to run when user presses enter
                // Priority order: Email > SMS > Password > Backup Code > TOTP
                primaryIdentifierField::action {
                    val id = primaryIdentifier()
                    val validId = Regexes.email.matches(id) || Regexes.phoneNumber.matches(id)
                    when {
                        proofs().none { it.via == endpoints.emailProof?.via } && email() != null && emailStartAction != null -> emailStartAction
                        proofs().none { it.via == endpoints.smsProof?.via } && phone() != null && smsStartAction != null -> smsStartAction
                        proofs().none { it.via == endpoints.passwordProof?.via } && validId && passwordStartAction != null -> passwordStartAction
                        proofs().none { it.via == endpoints.backupCodeProof?.via } && validId && backupCodeAction != null -> backupCodeAction
                        proofs().none { it.via == endpoints.oneTimePasswordProof?.via } && validId && totpAction != null -> totpAction
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

                        result.refreshToken?.let {
                            onAuthentication(it)
                            (AppScope + Dispatchers.Main).launch {
                                if (rememberDevice.await()) {
                                    endpoints.withAuth(
                                        LightningServerAuthentication(
                                            subject,
                                            subjectPath,
                                            it
                                        )
                                    ).knownDeviceProof?.establishKnownDeviceV2()?.let {
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

/**
 * Renders the session length selection UI shown after authentication requirements are met.
 *
 * Allows user to choose:
 * - Whether to remember this device (if known device feature enabled)
 * - Session length: 1 day (default) vs maximum allowed duration
 *
 * @param knownDeviceOptions Server's known device configuration (null if feature disabled)
 * @param rememberDevice Signal controlling "remember this device" checkbox
 * @param desiredSessionLength Signal controlling session duration selection
 * @param authResult Current authentication result containing max expiration time
 */
fun ViewWriter.sessionLengthComponent(
    knownDeviceOptions: Reactive<KnownDeviceOptions?>,
    rememberDevice: Signal<Boolean>,
    desiredSessionLength: Signal<Duration?>,
    authResult: Reactive<ProofsCheckResult<out Comparable<*>>?>,
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

/*
 * API IMPROVEMENT RECOMMENDATIONS FOR AuthComponent.kt:
 *
 * 1. PHONE NUMBER VALIDATION - Regexes.phoneNumber only supports US format
 *    - Pattern "^\d{3}-?\d{3}-?\d{4}$" rejects international numbers
 *    - Consider using libphonenumber library for proper international support
 *    - Add region/country code detection
 *
 * 2. LEGACY vs MODERN - Two competing authentication implementations in same codebase
 *    - AuthComponent (this file) is the older implementation
 *    - AuthComponent2 (authComponent2.kt) is the newer implementation
 *    - Consider deprecating AuthComponent or documenting migration path clearly
 *    - Duplicate code between implementations should be extracted to shared utilities
 *
 * 3. COROUTINE LIFECYCLE MANAGEMENT - Multiple untracked coroutines launched
 *    - Known device authentication (lines ~534, ~901)
 *    - WebAuthN autofill request (line ~1031)
 *    - No cleanup if component unmounts during async operations
 *    - Use ResourceUse pattern or structured concurrency to prevent leaks
 *
 * 4. CALLBACK RACE CONDITIONS - callBackDone flag pattern is repeated
 *    - Used to prevent double-callbacks in onProof/onException handlers
 *    - This pattern appears in multiple places (ReAuthComponent and AuthComponent)
 *    - Extract to reusable function: fun <T> onceCallback(block: (T) -> Unit): (T) -> Unit
 *
 * 5. ERROR HANDLING - Generic exception handling loses context
 *    - Multiple catch(e: Exception) blocks that don't distinguish error types
 *    - printStackTrace2() calls (lines ~377, ~540, ~692, ~908) should use proper logging
 *    - User-facing error messages could be more specific (e.g., network vs auth vs server errors)
 *
 * 6. WEBAUTHN CANCELLATION - Background autofill request may not be cancelled properly
 *    - pendingWebauthnRequest.cancel() only called when explicit button clicked (line ~863)
 *    - If user navigates away or enters email/password, request continues in background
 *    - Could cause confusion if user later interacts with stale autofill UI
 *
 * 7. IDENTIFIER AUTO-DETECTION - Complex nested logic in multiple places
 *    - Phone/email extraction logic repeated in AuthComponent2 and AuthComponent
 *    - Consider extracting: fun detectIdentifierType(input: String): IdentifierType?
 *    - Would centralize validation and improve testability
 *
 * 8. MAGIC STRINGS - "_id" property appears throughout code
 *    - Lines ~424, ~441, ~458, ~497, ~744, ~768, ~794, ~868, ~871
 *    - Extract constant: const val PROPERTY_USER_ID = "_id"
 *    - Document why "_id" fields are treated differently
 *
 * 9. NULLABILITY CONFUSION - refreshToken can be null after successful login?
 *    - Lines ~357, ~517: result.refreshToken?.also { onAuthentication(it) }
 *    - If null is valid, document when/why this happens
 *    - If null is error, throw exception instead of silently ignoring
 *
 * 10. STATE MACHINE COMPLEXITY - Multiple independent Signals create invalid states
 *     - currentProof, proofs, authenticating, primaryIdentifier are separate signals
 *     - Possible invalid states: currentProof set but primaryIdentifier null
 *     - Consider sealed class AuthState to make valid states explicit
 *
 * 11. DUPLICATE PROOF RENDERING - Each proof class has its own render implementation
 *     - EmailProof and SmsProof have nearly identical code (only text differs)
 *     - PasswordProof, TotpProof, BackupCodeProof are very similar
 *     - Extract common patterns to reduce duplication
 *
 * 12. COMMENTED CODE - Line 1226 has commented-out alternative text
 *     - "Remember this device for ${knownDeviceOptions()?.duration?.inWholeDays} days"
 *     - Either implement this feature or remove the comment
 *
 * 13. ACCESSIBILITY - No accessibility labels or screen reader support
 *     - Important for visually impaired users
 *     - Add content descriptions for all icons
 *     - Add labels for progress bars, loading indicators
 *     - Add announcements for error states
 *
 * 14. UNIT TESTING - Complex reactive logic would benefit from tests
 *     - Identifier detection/validation logic
 *     - Proof priority selection
 *     - State transitions during authentication flow
 *     - Known device expiration handling
 *
 * 15. TIMER IMPLEMENTATION - resendTime countdown uses polling every second
 *     - EmailProof and SmsProof use reactiveProcess { while(true) { emit(now()); delay(1000) } }
 *     - This creates a coroutine that polls forever
 *     - Consider using Flow.timer or more efficient countdown approach
 */
