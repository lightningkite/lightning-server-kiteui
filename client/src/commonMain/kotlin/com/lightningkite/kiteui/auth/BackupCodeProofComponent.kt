package com.lightningkite.kiteui.auth

import com.lightningkite.kiteui.models.Icon
import com.lightningkite.kiteui.models.KeyboardHints
import com.lightningkite.kiteui.models.SubtextSemantic
import com.lightningkite.kiteui.reactive.Action
import com.lightningkite.kiteui.views.ViewModifiable
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
 * Authentication proof component for backup recovery codes.
 *
 * This component handles authentication via single-use backup codes, typically provided
 * when users first set up 2FA. These codes serve as a fallback when primary 2FA methods
 * are unavailable (e.g., lost phone, broken authenticator app).
 *
 * @property p The backup code proof endpoints client for server communication
 * @property type The user type/domain for authentication (e.g., "user", "admin")
 *
 * Key behaviors:
 * - Auto-focuses code input field on render
 * - Accepts alphanumeric backup codes (typically hyphen-separated)
 * - Each code is single-use and invalidated after successful authentication
 * - No resend capability (codes are provided during initial 2FA setup)
 *
 * Important: Backup codes should be stored securely by users as they are the last
 * resort for account recovery when other authentication methods fail.
 */
data class BackupCodeProofComponent(val p: ProofClientEndpoints.BackupCode, val type: String) : ProofComponent {
    override val name: String = "Enter Backup Code"
    override val icon: Icon = Icon.security
    override val via: String = p.via
    override val property: String? = p.property

    /**
     * Renders the backup code entry UI.
     *
     * Creates a simple form with:
     * - Alphanumeric code input field with auto-focus
     * - Submit button
     *
     * @param to The view writer to render into
     * @param primaryIdentifier The user's primary identification (used as fallback for property/value)
     * @param option The proof option containing the identity information
     * @param onResult Callback invoked with the proof result (or null on error)
     * @return The root view modifier for the rendered UI
     */
    override fun render(
        to: ViewWriter,
        primaryIdentifier: UserIdentification?,
        option: ProofOption,
        onResult: (Proof?) -> Unit
    ): ViewModifiable = to.col {
        // Holds the user-entered backup code
        val code = Signal("")

        // TODO: Variable name "provePasswordOwnership" is misleading - this is a backup code, not password
        // Action to submit the backup code to the server for verification
        val provePasswordOwnership = Action("Submit", Icon.Companion.done) {
            // TODO: Potential bug - if both option.method.property/value and primaryIdentifier
            // are null, empty strings are sent. Should validate or fail earlier.
            onResult(
                p.proveBackupCode(
                    IdentificationAndPassword(
                        type = type,
                        // Fallback chain: option.method.property -> primaryIdentifier.property -> ""
                        property = option.method.property ?: primaryIdentifier?.property ?: "",
                        // Fallback chain: option.value -> primaryIdentifier.value -> ""
                        value = option.value ?: primaryIdentifier?.value ?: "",
                        // Note: "password" field is reused to carry the backup code
                        password = code.await()
                    )
                )
            )
        }

        fieldNoErrorText("Backup Code") {
            textInput {
                // Hint shows typical backup code format with hyphens
                ::hint { "xxxxx-xxxxx-xxxxx-xxxxx" }
                // Auto-focus for immediate code entry
                requestFocus()
                content bind code
                action = provePasswordOwnership
                // One-time code keyboard for alphanumeric codes
                keyboardHints = KeyboardHints.Companion.oneTimeCodeLetters
            }
        }
        // Error messages appear here when backup code is invalid or already used
        SubtextSemantic.onNext - errorText()

        important - button {
            centered - text("Submit")
            action = provePasswordOwnership
        }
    }
}

/*
 * API Improvement Recommendations:
 *
 * TODO: Rename "provePasswordOwnership" variable to "proveBackupCode" or similar
 *
 * TODO: Add validation for missing identification data
 *       - Currently sends empty strings if property/value are unavailable
 *       - Should validate or show error message to user instead
 *
 * TODO: Add code format validation and normalization
 *       - Strip hyphens/spaces before submission for flexibility
 *       - Allow users to enter "xxxxx-xxxxx" or "xxxxxxxxxx"
 *       - Validate expected code length (if known)
 *
 * TODO: Add warning about single-use nature
 *       - Display message: "This code will be invalidated after use"
 *       - Helps users understand they should save remaining codes
 *       - Prevents confusion when code stops working
 *
 * TODO: Add remaining codes counter
 *       - If server provides this info, show "X backup codes remaining"
 *       - Warn when running low (e.g., "Only 2 codes left")
 *       - Prompt to generate new codes when depleted
 *
 * TODO: Add option to view/generate new backup codes
 *       - After successful authentication, offer to view remaining codes
 *       - Provide way to generate fresh set of codes
 *       - Requires additional screen/flow
 *
 * TODO: Add accessibility improvements
 *       - Label should be properly associated with input
 *       - Error messages should be announced to screen readers
 *       - Warn screen reader users about single-use nature
 *
 * TODO: Consider case-insensitive comparison
 *       - Users may enter codes in different cases
 *       - Server should handle normalization, but client could too
 *
 * TODO: Add paste support with auto-cleanup
 *       - Auto-remove hyphens, spaces, and special characters
 *       - Users often copy codes from secure storage
 *       - Improve UX by handling various formats
 *
 * TODO: Add "What are backup codes?" help text or link
 *       - Many users unfamiliar with backup codes
 *       - Brief explanation or link to documentation
 *       - Especially important in recovery scenarios
 *
 * TODO: Consider showing example of valid format
 *       - Current hint is good, but could add helper text
 *       - "Enter one of your backup codes (e.g., abc12-def34-ghi56)"
 *
 * TODO: Add warning if this is the last backup code
 *       - Critical to warn user before using final code
 *       - Prompt to set up alternative 2FA method
 *       - Prevent account lockout scenarios
 */