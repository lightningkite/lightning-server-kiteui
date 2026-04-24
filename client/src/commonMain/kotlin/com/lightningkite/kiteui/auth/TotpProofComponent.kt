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
 * Authentication proof component for Time-based One-Time Password (TOTP) verification.
 *
 * This component handles 2FA authentication using authenticator apps like Google Authenticator,
 * Microsoft Authenticator, Authy, etc. Users enter a time-based 6-digit code from their app.
 *
 * @property p The TOTP proof endpoints client for server communication
 * @property type The user type/domain for authentication (e.g., "user", "admin")
 *
 * Key behaviors:
 * - Auto-focuses code input field on render
 * - Expects 6-digit numeric code (standard TOTP format)
 * - Uses numeric keyboard hints for easier entry
 * - No resend capability (codes regenerate automatically every 30 seconds)
 *
 * Note: This is typically used as a second factor after password authentication,
 * but can be configured as a standalone proof method depending on security requirements.
 */
public data class TotpProofComponent(val p: ProofClientEndpoints.TimeBasedOTP, val type: String) : EasierProofComponent {
    override val name: String = "Use Authenticator App"
    override val icon: Icon = Icon.Companion.pinCode
    override val via: String = p.via
    override val property: String? = p.property

    /**
     * Renders the TOTP code entry UI.
     *
     * Creates a simple form with:
     * - Numeric code input field with auto-focus
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
    ) {
        to.col {
            // Holds the user-entered TOTP code (typically 6 digits)
            val code = Signal("")

            // Action to submit the TOTP code to the server for verification
            val proveTotpOwnership = Action("Submit", Icon.Companion.done) {
                // TODO: Potential bug - if both option.method.property/value and primaryIdentifier
                // are null, empty strings are sent. Should validate or fail earlier.
                onResult(
                    p.proveOTP(
                        IdentificationAndPassword(
                            type = type,
                            // Fallback chain: option.method.property -> primaryIdentifier.property -> ""
                            property = option.method.property ?: primaryIdentifier?.property ?: "",
                            // Fallback chain: option.value -> primaryIdentifier.value -> ""
                            value = option.value ?: primaryIdentifier?.value ?: "",
                            // Note: "password" field is reused to carry the TOTP code
                            password = code.await()
                        )
                    )
                )
            }

            fieldNoErrorText("One-time Password from App") {
                textInput {
                    debugName = "codeInput"
                    ::hint { "000000" }
                    // Auto-focus for immediate code entry
                    requestFocus()
                    content bind code
                    action = proveTotpOwnership
                    // Numeric keyboard for easier code entry
                    keyboardHints = KeyboardHints.Companion.oneTimeCode
                }
            }
            // Error messages appear here when TOTP code is invalid or expired
            errorText()

            important.button {
                debugName = "submitButton"
                centered.text("Submit")
                action = proveTotpOwnership
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
 *
 * TODO: Add code format validation
 *       - TOTP codes are typically 6 digits
 *       - Could validate length before submission to catch user errors
 *       - Prevent unnecessary server calls
 *
 * TODO: Add countdown timer showing when current code expires
 *       - TOTP codes typically expire every 30 seconds
 *       - Visual countdown helps user know when to generate new code
 *       - Could sync with device clock to estimate remaining time
 *
 * TODO: Add auto-submit when 6 digits entered
 *       - Common UX pattern for fixed-length codes
 *       - Reduces friction by eliminating submit button click
 *       - Should still allow manual submission for accessibility
 *
 * TODO: Consider adding "Can't access your authenticator app?" link
 *       - Could offer backup code option
 *       - Or link to recovery flow
 *       - Improves user experience when device is unavailable
 *
 * TODO: Add accessibility improvements
 *       - Label should be properly associated with input
 *       - Error messages should be announced to screen readers
 *       - Countdown timer should be properly announced
 *
 * TODO: Add support for longer TOTP codes
 *       - Some systems use 8-digit codes instead of 6
 *       - Hint and validation should be flexible
 *
 * TODO: Consider showing QR code setup instructions
 *       - If this component is used during TOTP enrollment
 *       - May need separate setup vs. verification modes
 *
 * TODO: Add paste support with auto-cleanup
 *       - Some authenticator apps support copy/paste
 *       - Auto-remove spaces and dashes if user pastes formatted code
 *
 * TODO: Consider grouping digits for better readability
 *       - Show "123 456" instead of "123456"
 *       - Easier for users to verify they typed correctly
 */