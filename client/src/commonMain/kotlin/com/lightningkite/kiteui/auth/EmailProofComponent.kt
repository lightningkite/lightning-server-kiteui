package com.lightningkite.kiteui.auth

import com.lightningkite.kiteui.models.Icon
import com.lightningkite.kiteui.models.KeyboardHints
import com.lightningkite.kiteui.models.SubtextSemantic
import com.lightningkite.kiteui.models.rem
import com.lightningkite.kiteui.reactive.Action
import com.lightningkite.kiteui.views.*
import com.lightningkite.kiteui.views.direct.*
import com.lightningkite.kiteui.views.l2.errorText
import com.lightningkite.kiteui.views.l2.icon
import com.lightningkite.lightningserver.sessions.proofs.FinishProof
import com.lightningkite.lightningserver.sessions.proofs.Proof
import com.lightningkite.lightningserver.sessions.proofs.ProofClientEndpoints
import com.lightningkite.lightningserver.sessions.proofs.ProofOption
import com.lightningkite.reactive.context.await
import com.lightningkite.reactive.context.invoke
import com.lightningkite.reactive.context.reactive
import com.lightningkite.reactive.context.rerunOn
import com.lightningkite.reactive.core.BasicListenable
import com.lightningkite.reactive.core.Signal
import com.lightningkite.reactive.core.rememberSuspending
import kotlin.time.Duration.Companion.seconds

/**
 * Authentication proof component that verifies email ownership through a one-time code.
 *
 * This component sends a verification code to the user's email address and provides UI
 * for entering that code. It includes automatic resend functionality with rate limiting.
 *
 * @property p The email proof endpoints client for server communication
 *
 * Key behaviors:
 * - Automatically sends initial verification email on render
 * - Enforces 15-second cooldown between resend attempts
 * - Shows countdown timer during cooldown period
 * - Provides immediate feedback when email is sent
 * - Auto-focuses input field when email is sent
 *
 * Note: The code input uses one-time code keyboard hints for better mobile UX
 */
data class EmailProofComponent(val p: ProofClientEndpoints.Email) : ProofComponent {
    override val name: String = "Email Code"
    override val icon: Icon = Icon.Companion.email
    override val via: String = p.via
    override val property: String? = p.property

    /** Holds the user-entered verification code */
    val code = Signal("")

    /** Minimum time between resend attempts to prevent spam */
    val resendTime = 15.seconds

    /**
     * Renders the email verification UI.
     *
     * Creates a form with:
     * - Loading state while sending email
     * - Code input field with auto-focus
     * - Submit button
     * - Resend button with cooldown timer
     *
     * @param to The view writer to render into
     * @param primaryIdentifier The user's primary identification (may be null)
     * @param option The proof option containing the email address
     * @param onResult Callback invoked with the proof result (or null on error)
     * @return The root view modifier for the rendered UI
     */
    override fun render(
        to: ViewWriter,
        primaryIdentifier: UserIdentification?,
        option: ProofOption,
        onResult: (Proof?) -> Unit
    ) {
        to.col {
            // Listenable to trigger email resends - invoking this causes the challenge to be re-fetched
            val resend = BasicListenable()

            // Fetch the email challenge from server, automatically retries when resend is triggered
            // withTimestamp() adds a client-side timestamp to track when the email was sent
            val challenge = rememberSuspending {
                rerunOn(resend)
                p.beginEmailOwnershipProof(option.value ?: "").withTimestamp()
            }

            // Action to submit the verification code to the server
            val proveEmailOwnership = Action("Submit", Icon.Companion.done) {
                onResult(p.proveEmailOwnership(FinishProof(challenge().value, code.await())))
            }

            // Loading state: shown until initial email is sent
            centered.shownWhen { !challenge.state().ready }.text("Sending email...")

            // Main form: shown once email is sent
            shownWhen { challenge.state().ready }.col {

                fieldNoErrorText("Login code emailed to ${option.value ?: ""}") {
                    textInput {
                        ::hint { "ABCDEF" }
                        // Auto-focus once email is sent for better UX
                        reactive { if (challenge.state().ready) requestFocus() }
                        content bind code
                        action = proveEmailOwnership
                        keyboardHints = KeyboardHints.Companion.oneTimeCodeLetters
                    }
                }
                // Error messages appear here when proof fails
                SubtextSemantic.onNext.errorText()

                important.buttonTheme.button {
                    centered.text("Submit")
                    action = proveEmailOwnership
                }

                // Resend button with three states:
                // 1. "Sent!" for first 3 seconds
                // 2. Countdown timer for remaining cooldown period
                // 3. "Send new code" when cooldown expires
                button {
                    // Button is disabled during cooldown (except first 3 seconds when showing "Sent!")
                    ::enabled { nowBySecond() !in challenge().timestamp + 3.seconds..challenge().timestamp + resendTime }

                    // State 3: Ready to resend
                    centered.shownWhen { nowBySecond() > challenge().timestamp + resendTime }.text("Send new code")

                    // State 1: Just sent confirmation
                    centered.shownWhen { nowBySecond() < challenge().timestamp + 3.seconds }.row {
                        centered.icon(Icon.Companion.done.copy(1.rem, 1.rem), "")
                        centered.text("Sent!")
                    }

                    // State 2: Cooldown countdown
                    centered.shownWhen { nowBySecond() in challenge().timestamp + 3.seconds..challenge().timestamp + resendTime }
                        .text {
                            ::content { "Can send new code in ${(challenge().timestamp + resendTime - nowBySecond()).inWholeSeconds}" }
                        }

                    onClick {
                        // Only trigger resend if cooldown has expired
                        if (nowBySecond() > challenge().timestamp + resendTime)
                            resend.invokeAll()
                    }
                }
            }
        }
    }
}

/*
 * API Improvement Recommendations:
 *
 * TODO: Consider making resendTime configurable per-instance or via server settings
 *       - Current hardcoded 15 seconds may not fit all use cases
 *       - Different email providers may have different rate limits
 *
 * TODO: Add retry logic for failed email sends
 *       - Currently no indication if beginEmailOwnershipProof fails
 *       - User has no way to know if email never arrived vs. still in transit
 *
 * TODO: Consider adding email delivery status feedback
 *       - Could show "Email sent successfully" vs. "Failed to send email"
 *       - Would require server-side changes to return delivery status
 *
 * TODO: Add accessibility improvements
 *       - Announce state changes to screen readers (loading, sent, cooldown)
 *       - Proper ARIA labels for countdown timer
 *
 * TODO: Consider adding code expiration warning
 *       - Most email codes expire after a certain time (e.g., 10 minutes)
 *       - Could show "Code expires in X minutes" if server provides expiration
 *
 * TODO: Validate code format before submission
 *       - Could check if code matches expected pattern (e.g., 6 characters)
 *       - Reduce unnecessary server calls for obviously invalid codes
 *
 * TODO: Clear code input on resend
 *       - Old code becomes invalid when new one is sent
 *       - Auto-clearing would prevent user confusion
 *
 * TODO: Add option to change email address
 *       - User may have mistyped their email
 *       - Currently no way to go back and correct it
 */