package com.lightningkite.kiteui.auth

import com.lightningkite.kiteui.models.Icon
import com.lightningkite.kiteui.models.KeyboardHints
import com.lightningkite.kiteui.models.SubtextSemantic
import com.lightningkite.kiteui.reactive.Action
import com.lightningkite.kiteui.views.ElementWriter

import com.lightningkite.kiteui.views.ViewWriter
import com.lightningkite.kiteui.views.centered
import com.lightningkite.kiteui.views.direct.button
import com.lightningkite.kiteui.views.direct.col
import com.lightningkite.kiteui.views.direct.text
import com.lightningkite.kiteui.views.direct.textInput
import com.lightningkite.kiteui.views.important
import com.lightningkite.kiteui.views.l2.errorText
import com.lightningkite.lightningserver.sessions.proofs.IdentificationAndPassword
import com.lightningkite.lightningserver.sessions.proofs.Proof
import com.lightningkite.lightningserver.sessions.proofs.ProofClientEndpoints
import com.lightningkite.lightningserver.sessions.proofs.ProofOption
import com.lightningkite.reactive.context.await
import com.lightningkite.reactive.core.Signal

/**
 * Authentication proof component that verifies user identity via password.
 *
 * This is a simpler proof component compared to email/SMS as it doesn't require
 * challenge/response - just direct password submission.
 *
 * @property p The password proof endpoints client for server communication
 * @property type The user type/domain for authentication (e.g., "user", "admin")
 *
 * Key behaviors:
 * - Auto-focuses password input field on render for immediate entry
 * - Uses secure password keyboard hints
 * - Combines user identification (property, value) with entered password
 * - No loading state or resend functionality needed
 *
 * Note: This component assumes the user identity (property/value) is already known
 * from the primaryIdentifier or option parameters. It only collects the password.
 */
data class PasswordProofComponent(val p: ProofClientEndpoints.Password, val type: String) : EasierProofComponent {
    override val name: String = "Enter Password"
    override val icon: Icon = Icon.Companion.password
    override val via: String = p.via
    override val property: String? = p.property

    /**
     * Renders the password entry UI.
     *
     * Creates a simple form with:
     * - Password input field with auto-focus
     * - Submit button
     *
     * @param to The view writer to render into
     * @param primaryIdentifier The user's primary identification (used as fallback for property/value)
     * @param option The proof option containing the identity information
     * @param onResult Callback invoked with the proof result (or null on error)
     * @return The root view modifier for the rendered UI
     */
    override fun render(
        to: ElementWriter.CanAddTheme,
        primaryIdentifier: UserIdentification?,
        option: ProofOption,
        onResult: (Proof?) -> Unit
    ): Unit {
        to.col {
            // Holds the user-entered password
            val password = Signal("")

            // Action to submit the password to the server for verification
            val provePasswordOwnership = Action("Submit", Icon.Companion.done) {
                // TODO: Potential bug - if both option.method.property/value and primaryIdentifier
                // are null, empty strings are sent. Should validate or fail earlier.
                onResult(
                    p.provePasswordOwnership(
                        IdentificationAndPassword(
                            type = type,
                            // Fallback chain: option.method.property -> primaryIdentifier.property -> ""
                            property = option.method.property ?: primaryIdentifier?.property ?: "",
                            // Fallback chain: option.value -> primaryIdentifier.value -> ""
                            value = option.value ?: primaryIdentifier?.value ?: "",
                            password = password.await()
                        )
                    )
                )
            }

            fieldNoErrorText("Password") {
                textInput {
                    debugName = "passwordInput"
                    ::hint { "" }
                    // Auto-focus for immediate password entry
                    requestFocus()
                    content bind password
                    action = provePasswordOwnership
                    keyboardHints = KeyboardHints.Companion.password
                }
            }
            // Error messages appear here when password is incorrect
            errorText()

            important.button {
                debugName = "submitButton"
                centered.text("Submit")
                action = provePasswordOwnership
            }
        }
    }
}

/*
 * API Improvement Recommendations:
 *
 * TODO: Add validation for missing identification data
 *       - Currently sends empty strings if property/value are unavailable
 *       - Should validate or show error message to user instead
 *       - Prevents confusing server errors
 *
 * TODO: Add "Forgot Password" link
 *       - Common UX pattern for password authentication
 *       - Could be passed as optional callback parameter
 *       - Or emit a separate event that parent can handle
 *
 * TODO: Add password visibility toggle
 *       - Allow users to show/hide password as they type
 *       - Especially helpful on mobile devices
 *       - Standard security best practice
 *
 * TODO: Add password strength indicator (for password creation scenarios)
 *       - While this is primarily for login, may be reused for registration
 *       - Could be optional via constructor parameter
 *
 * TODO: Consider adding "Remember me" option
 *       - Allow extended session duration
 *       - Would need to coordinate with session management
 *
 * TODO: Add rate limiting feedback
 *       - If server implements login attempt limiting
 *       - Show "Too many attempts, try again in X minutes"
 *       - Prevents user frustration with silent failures
 *
 * TODO: Add hint text in password field
 *       - Currently hint is empty string
 *       - Could show something like "Enter your password" or "••••••••"
 *
 * TODO: Consider clearing password on failed attempt
 *       - Security best practice to prevent shoulder surfing
 *       - Or at least provide option to do so
 *
 * TODO: Add accessibility improvements
 *       - Label should be properly associated with input
 *       - Error messages should be announced to screen readers
 *
 * TODO: Add support for password managers
 *       - Ensure autocomplete attributes are set correctly
 *       - May need additional HTML attributes on web platform
 */