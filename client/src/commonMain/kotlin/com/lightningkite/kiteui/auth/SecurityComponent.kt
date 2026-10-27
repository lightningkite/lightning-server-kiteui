package com.lightningkite.kiteui.auth

import com.lightningkite.kiteui.ClientAuthenticator
import com.lightningkite.kiteui.locale.RenderSize
import com.lightningkite.kiteui.locale.renderToString
import com.lightningkite.kiteui.models.*
import com.lightningkite.kiteui.download
import com.lightningkite.kiteui.setClipboardText
import com.lightningkite.kiteui.toBlob
import com.lightningkite.kiteui.views.*
import com.lightningkite.kiteui.views.direct.*
import com.lightningkite.kiteui.views.l2.*
import com.lightningkite.lightningserver.LsErrorException
import com.lightningkite.lightningserver.auth.AuthEndpoints
import com.lightningkite.lightningserver.sessions.*
import com.lightningkite.lightningserver.sessions.proofs.*
import com.lightningkite.lightningserver.typed.ClientModelRestEndpoints
import com.lightningkite.reactive.context.*
import com.lightningkite.reactive.core.*
import com.lightningkite.services.database.*
import com.lightningkite.services.database.optics.notNull
import kotlin.time.Clock.System.now
import kotlin.uuid.Uuid

/**
 * Lets a signed-in subject manage their own credentials: password, authenticator app, passkeys,
 * security keys, and backup codes.
 *
 * The server only accepts these changes from a freshly proven session, so the subject re-authenticates
 * first; [elevate] then builds endpoints authorized by the session token that produces.
 */
public open class SecurityComponent(
    public val endpoints: AuthEndpoints,
    public val subjectId: String,
    public val subjectType: String = endpoints.subjects.keys.single(),
    public val subject: AuthClientEndpoints<*, *> = endpoints.subjects[subjectType]!!,
    public val elevate: (sessionToken: String) -> Elevated,
) {
    /** Leave out any method the server doesn't offer. */
    public class Elevated(
        public val password: ProofClientEndpoints.Password? = null,
        public val totp: TotpEndpoints<*>? = null,
        public val webAuthN: WebAuthNEndpoints<*>? = null,
        public val backupCode: ProofClientEndpoints.BackupCode? = null,
    )

    /** Generated SDKs expose the proof and its secrets table as one object; pass that. */
    public class TotpEndpoints<T>(endpoints: T)
            where T : ProofClientEndpoints.TimeBasedOTP, T : ClientModelRestEndpoints<TotpSecret, Uuid> {
        public val proof: ProofClientEndpoints.TimeBasedOTP = endpoints
        public val secrets: ClientModelRestEndpoints<TotpSecret, Uuid> = endpoints
    }

    /** Generated SDKs expose the proof and its credentials table as one object; pass that. */
    public class WebAuthNEndpoints<T>(endpoints: T)
            where T : ProofClientEndpoints.WebAuthN, T : ClientModelRestEndpoints<WebAuthNCredential, String> {
        public val proof: ProofClientEndpoints.WebAuthN = endpoints
        public val credentials: ClientModelRestEndpoints<WebAuthNCredential, String> = endpoints
    }

    public val elevated: Signal<Elevated?> = Signal(null)
    private val changed = BasicListenable()

    private val totpSecrets = rememberSuspending {
        rerunOn(changed)
        val totp = elevated()?.totp ?: return@rememberSuspending emptyList()
        elevatedCall {
            totp.secrets.query(Query(condition {
                it.subjectId.eq(subjectId) and it.disabledAt.eq(null) and
                        (it.expiresAt.eq(null) or it.expiresAt.notNull.gte(now()))
            }))
        }
    }

    private val webAuthNCredentials = rememberSuspending {
        rerunOn(changed)
        val webAuthN = elevated()?.webAuthN ?: return@rememberSuspending emptyList()
        elevatedCall {
            webAuthN.credentials.query(Query(condition { it.subjectId.eq(subjectId) and it.disabledAt.eq(null) }))
        }
    }

    private val backupCodesEstablished = rememberSuspending {
        rerunOn(changed)
        val backupCode = elevated()?.backupCode ?: return@rememberSuspending false
        elevatedCall { backupCode.established() }
    }

    /** Shown once, right after the server generates them. */
    private val newBackupCodes = Signal<List<String>?>(null)

    private suspend fun <R> elevatedCall(call: suspend () -> R): R = try {
        call()
    } catch (e: LsErrorException) {
        // The re-authenticated session is short-lived; once it lapses, ask again.
        if (e.status == 401) elevated.value = null
        throw e
    }

    /** Backup codes only matter once there is a second factor to lose, so they're created with the first one. */
    private suspend fun secondFactorAdded() {
        val backupCode = elevated.value?.backupCode
        if (backupCode != null && !elevatedCall { backupCode.established() })
            newBackupCodes.value = elevatedCall { backupCode.resetCodes() }
        changed.invokeAll()
    }

    private suspend fun secondFactorRemoved(removedId: Any) {
        val backupCode = elevated.value?.backupCode
        val remaining = totpSecrets.await().count { it._id != removedId && it.lastUsedAt != null } +
                webAuthNCredentials.await().count { it._id != removedId }
        if (backupCode != null && remaining == 0)
            elevatedCall { backupCode.clearCodes() }
        changed.invokeAll()
    }

    public open fun render(to: ViewWriter) {
        to.col {
            shownWhen { elevated() == null }.col {
                h5("Confirm it's you to change your security settings.")
                frame {
                    reactive {
                        if (elevated() != null) return@reactive
                        clearChildren()
                        ReAuthComponent(
                            endpoints = endpoints,
                            subjectId = subjectId,
                            subjectType = subjectType,
                            subject = subject,
                        ) { token -> elevated.value = elevate(token) }.render(this@frame)
                    }
                }
            }

            shownWhen { elevated()?.password != null }.col {
                h3("Password")
                val newPassword = Signal("")
                val confirmation = Signal("")
                val saved = Signal(false)
                field("New Password") {
                    textInput {
                        keyboardHints = KeyboardHints.newPassword
                        content bind newPassword
                    }
                }
                field("Confirm New Password") {
                    textInput {
                        keyboardHints = KeyboardHints.newPassword
                        content bind confirmation
                    }
                }
                shownWhen { confirmation().isNotEmpty() && confirmation() != newPassword() }.danger.subtext("Passwords do not match")
                shownWhen { saved() }.subtext("Password saved")
                atStart.important.button {
                    ::enabled { newPassword().isNotEmpty() && confirmation() == newPassword() }
                    text("Set Password")
                    onClick {
                        val password = elevated.value?.password ?: return@onClick
                        elevatedCall { password.establishPassword(EstablishPassword(password = newPassword())) }
                        newPassword.value = ""
                        confirmation.value = ""
                        saved.value = true
                    }
                }
            }

            shownWhen { elevated()?.totp != null || elevated()?.webAuthN != null }.col {
                h3("Two-Factor Authentication")

                centered.shownWhen { totpSecrets().isEmpty() && webAuthNCredentials().isEmpty() }
                    .subtext("No second factor set up")

                col { forEach(totpSecrets) { secret ->
                    row {
                        expanding.col {
                            text(secret.label)
                            subtext(
                                secret.lastUsedAt?.let { "Authenticator app, last used ${it.renderToString(RenderSize.Numerical)}" }
                                    ?: "Authenticator app, setup not finished"
                            )
                        }
                        if (secret.lastUsedAt == null) centered.button {
                            text("Finish Setup")
                            onClick {
                                context.dialog { close ->
                                    col {
                                        h3("Finish setting up ${secret.label}")
                                        text("If you added this to your authenticator app but didn't enter a code, enter one now.")
                                        val code = Signal("")
                                        field("Code") {
                                            textInput {
                                                keyboardHints = KeyboardHints.oneTimeCode
                                                content bind code
                                            }
                                        }
                                        row {
                                            expanding.button {
                                                centered.text("Cancel")
                                                onClick { close() }
                                            }
                                            expanding.important.button {
                                                centered.text("Confirm")
                                                onClick {
                                                    val totp = elevated.value?.totp ?: return@onClick
                                                    elevatedCall { totp.proof.confirmOneTimePassword(code()) }
                                                    secondFactorAdded()
                                                    close()
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        centered.button {
                            icon(Icon.delete, "Remove ${secret.label}")
                            onClick {
                                context.confirmDanger(
                                    title = "Remove ${secret.label}?",
                                    body = "Removing a second factor makes your account easier to break into.",
                                    actionName = "Remove",
                                ) {
                                    val totp = elevated.value?.totp ?: return@confirmDanger
                                    elevatedCall { totp.secrets.modify(secret._id, modification { it.disabledAt assign now() }) }
                                    secondFactorRemoved(secret._id)
                                }
                            }
                        }
                    }
                } }

                col { forEach(webAuthNCredentials) { credential ->
                    row {
                        expanding.col {
                            text(credential.displayName)
                            val kind = if (credential.residentKey) "Passkey" else "Security key"
                            subtext(
                                credential.lastUsedAt?.let { "$kind, last used ${it.renderToString(RenderSize.Numerical)}" }
                                    ?: "$kind, never used"
                            )
                        }
                        centered.button {
                            icon(Icon.delete, "Remove ${credential.displayName}")
                            onClick {
                                context.confirmDanger(
                                    title = "Remove ${credential.displayName}?",
                                    body = "Removing a second factor makes your account easier to break into.",
                                    actionName = "Remove",
                                ) {
                                    val webAuthN = elevated.value?.webAuthN ?: return@confirmDanger
                                    elevatedCall {
                                        webAuthN.credentials.modify(credential._id, modification { it.disabledAt assign now() })
                                    }
                                    secondFactorRemoved(credential._id)
                                }
                            }
                        }
                    }
                } }

                renderAddSecondFactor()
            }

            shownWhen {
                elevated()?.backupCode != null && (backupCodesEstablished() || newBackupCodes() != null ||
                        totpSecrets().isNotEmpty() || webAuthNCredentials().isNotEmpty())
            }.col {
                h3("Backup Codes")
                renderBackupCodes()
            }
        }
    }

    private fun ElementWriter.renderAddSecondFactor() {
        col {
            val name = Signal("")
            val totpSetupUrl = Signal<String?>(null)

            shownWhen { totpSetupUrl() == null }.col {
                field("Device Name") {
                    textInput {
                        hint = "My phone"
                        keyboardHints = KeyboardHints.title
                        content bind name
                    }
                }
                row {
                    shownWhen { elevated()?.totp != null }.button {
                        ::enabled { name().isNotBlank() }
                        text {
                            ::content {
                                if (totpSecrets().any { it.lastUsedAt != null }) "Replace Authenticator App"
                                else "Add Authenticator App"
                            }
                        }
                        onClick {
                            val totp = elevated.value?.totp ?: return@onClick
                            totpSetupUrl.value = elevatedCall { totp.proof.establishOneTimePassword(EstablishTotp(name())) }
                            changed.invokeAll()
                        }
                    }
                    shownWhen { elevated()?.webAuthN != null }.button {
                        ::enabled { name().isNotBlank() }
                        text("Add Passkey")
                        onClick {
                            registerWebAuthN(WebAuthN.GeneralPreference.Required, name())
                            name.value = ""
                        }
                    }
                    shownWhen { elevated()?.webAuthN != null }.button {
                        ::enabled { name().isNotBlank() }
                        text("Add Security Key")
                        onClick {
                            registerWebAuthN(WebAuthN.GeneralPreference.Discouraged, name())
                            name.value = ""
                        }
                    }
                }
            }

            shownWhen { totpSetupUrl() != null }.col {
                // otpauth://totp/Issuer:label?secret=...&digits=6&...
                val parameters = remember {
                    totpSetupUrl()?.substringAfter('?', "")?.split('&')
                        ?.associate { it.substringBefore('=') to it.substringAfter('=', "") }
                        ?: emptyMap()
                }
                val code = Signal("")

                text("Scan this with your authenticator app, then enter the code it shows.")
                centered.image {
                    description = "QR code for authenticator app setup"
                    ::source { totpSetupUrl()?.let { qrCode(it) } }
                }
                atStart.button {
                    text("Can't scan it? Copy the setup key")
                    onClick { context.setClipboardText(parameters()["secret"] ?: return@onClick) }
                }
                field("Code") {
                    textInput {
                        keyboardHints = KeyboardHints.oneTimeCode
                        content bind code
                    }
                }
                row {
                    expanding.button {
                        centered.text("Cancel")
                        onClick {
                            totpSetupUrl.value = null
                            code.value = ""
                        }
                    }
                    expanding.important.button {
                        ::enabled { code().length == (parameters()["digits"]?.toInt() ?: 6) }
                        centered.text("Confirm")
                        onClick {
                            val totp = elevated.value?.totp ?: return@onClick
                            elevatedCall { totp.proof.confirmOneTimePassword(code()) }
                            totpSetupUrl.value = null
                            code.value = ""
                            name.value = ""
                            secondFactorAdded()
                        }
                    }
                }
            }
        }
    }

    private suspend fun registerWebAuthN(residentKey: WebAuthN.GeneralPreference, name: String) {
        val webAuthN = elevated.value?.webAuthN ?: return
        val (challengeId, request) = elevatedCall { webAuthN.proof.registerStart(residentKey) }
        val credential = ClientAuthenticator.getClientAuthenticator().createWebAuthNCredentials(request)
        elevatedCall {
            webAuthN.proof.registerFinish(
                WebAuthN.Registration.RegisterRequest(challengeId = challengeId, displayName = name, credential = credential)
            )
        }
        secondFactorAdded()
    }

    private fun ElementWriter.renderBackupCodes() {
        col {
            shownWhen { newBackupCodes() == null }.row {
                centered.expanding.subtext("If you lose your second factor, a backup code lets you sign in once.")
                button {
                    text { ::content { if (backupCodesEstablished()) "Replace Codes" else "Create Codes" } }
                    onClick {
                        val backupCode = elevated.value?.backupCode ?: return@onClick
                        newBackupCodes.value = elevatedCall { backupCode.resetCodes() }
                        changed.invokeAll()
                    }
                }
            }

            shownWhen { newBackupCodes() != null }.col {
                val saved = Signal(false)
                bold.text("Store these somewhere safe. You won't be able to see them again.")
                col {
                    forEach(remember { newBackupCodes() ?: emptyList() }) { text(it) }
                }
                row {
                    button {
                        icon(Icon.copy, "Copy Backup Codes")
                        onClick {
                            context.setClipboardText(newBackupCodes.value?.joinToString("\n") ?: return@onClick)
                            saved.value = true
                        }
                    }
                    button {
                        icon(Icon.download, "Download Backup Codes")
                        onClick {
                            val codes = newBackupCodes.value?.joinToString("\n") ?: return@onClick
                            context.download("backup-codes.txt", codes.toBlob("text/plain"))
                            saved.value = true
                        }
                    }
                    expanding.space()
                    important.button {
                        text("Done")
                        onClick {
                            if (saved()) {
                                newBackupCodes.value = null
                            } else {
                                context.confirmDanger(
                                    title = "Codes not saved",
                                    body = "You haven't copied or downloaded these codes, and you won't be able to see them again.",
                                    actionName = "Done Anyway",
                                ) { newBackupCodes.value = null }
                            }
                        }
                    }
                }
            }
        }
    }
}
