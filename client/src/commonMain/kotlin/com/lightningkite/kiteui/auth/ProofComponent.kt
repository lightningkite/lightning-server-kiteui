package com.lightningkite.kiteui.auth

import com.lightningkite.kiteui.models.Icon
import com.lightningkite.kiteui.views.ElementWriter

import com.lightningkite.kiteui.views.ViewWriter
import com.lightningkite.kiteui.views.direct.frame
import com.lightningkite.kiteui.views.direct.text
import com.lightningkite.lightningserver.sessions.proofs.*
import com.lightningkite.lightningserver.sessions.*

/**
 * Base interface for authentication proof components.
 *
 * Represents a single authentication method (e.g., email code, SMS, password, TOTP, WebAuthN).
 * Each proof component knows how to render its own UI and collect the required authentication data.
 *
 * Implementations include:
 * - EmailProofComponent (email verification codes)
 * - SmsProofComponent (SMS verification codes)
 * - PasswordProofComponent (password entry)
 * - TotpProofComponent (time-based one-time passwords)
 * - BackupCodeProofComponent (backup recovery codes)
 * - WebAuthNProofComponent (passkeys/security keys)
 *
 * @see AuthComponent2 for the main authentication flow that uses these components
 */
interface ProofComponent {
    /**
     * The user property this proof validates (e.g., "email", "phone", "_id").
     * Null if the proof doesn't validate a specific user property.
     */
    val property: String? get() = null

    /**
     * Unique identifier for this proof method (e.g., "email", "sms", "password", "totp").
     * Used to match against server-provided proof options and track which proofs have been completed.
     */
    val via: String

    /**
     * Human-readable display name for this proof method (e.g., "Email Code", "Text Message").
     */
    val name: String

    /**
     * Icon to display alongside this proof method in the UI.
     */
    val icon: Icon

    /**
     * Whether this proof method requires a primary identifier (email/phone/username) to be set first.
     * Default is true. WebAuthN may set this to false for passkey-only flows.
     */
    val primaryIdentifierRequired: Boolean get() = true

    /**
     * Checks if this proof method is supported on the current platform/environment.
     * Default implementation returns true. Override to check platform capabilities.
     */
    suspend fun supported(): Boolean = true

    /**
     * Returns an asynchronous task that's attempting to authenticate using this method.
     * The only current use of this is for [WebAuthNProofComponent] to enable autofill/conditional UI.
     *
     * The task runs in the background and may complete before the user explicitly selects this proof method.
     * If the task succeeds, the proof is automatically added to the authentication flow.
     */
    val earlyProof: (suspend (ViewWriter) -> Proof?)? get() = null

    /**
     * Renders the proof collection UI with automatic option extraction.
     *
     * This is a convenience method that extracts the appropriate ProofOption from the server's
     * ProofsCheckResult and delegates to the full render method.
     *
     * @param to The ViewWriter to render into
     * @param primaryIdentifier The user's primary identifier (email/phone/username), or null if not yet set
     * @param checks The server's response containing available proof options and requirements
     * @param onResult Callback invoked when proof collection completes (null if user cancels)
     * @return The rendered view
     */
    fun render(to: ElementWriter.CanAddTheme, primaryIdentifier: UserIdentification?, checks: ProofsCheckResult<*>?, onResult: (Proof?) -> Unit): Unit {
        // TODO: This error message should be impossible to reach; consider removing or replacing with exception
        val primaryIdentifier = primaryIdentifier ?: run {
            to.frame {
                text("How... how did you get here?")
            }
            return
        }
        // Extract the matching proof option from server response, or create a default one
        val option = checks?.options?.firstOrNull { it.method.via == via } ?: ProofOption(
            ProofMethodInfo(
                via,
                primaryIdentifier.property
            ), primaryIdentifier.value)
        return render(to, primaryIdentifier, option, onResult)
    }

    /**
     * Renders the proof collection UI for this authentication method.
     *
     * Implementations should:
     * - Display appropriate input fields for the proof type (code entry, password, etc.)
     * - Handle submission and validation
     * - Call onResult(proof) on success with the collected proof data
     * - Call onResult(null) if the user cancels
     *
     * @param to The ViewWriter to render into
     * @param primaryIdentifier The user's primary identifier (email/phone/username)
     * @param option The server-provided proof option containing method info and target value
     * @param onResult Callback invoked when proof collection completes
     * @return The rendered view
     */
    fun render(to: ElementWriter.CanAddTheme, primaryIdentifier: UserIdentification?, option: ProofOption, onResult: (Proof?) -> Unit)
}

/*
 * API IMPROVEMENT RECOMMENDATIONS:
 *
 * 1. DEFENSIVE PROGRAMMING - The render method with ProofsCheckResult parameter has unreachable error state
 *    - The "How... how did you get here?" message suggests this should never happen
 *    - Consider throwing IllegalStateException instead of rendering error UI
 *    - Or remove null check if primaryIdentifier is guaranteed to be non-null at call sites
 *
 * 2. DEFAULT IMPLEMENTATION - The main render method returns TODO()
 *    - This will crash at runtime if not overridden by implementations
 *    - Consider making it abstract or throwing a more descriptive exception
 *    - Add validation to ensure all implementations override this method
 *
 * 3. TYPE SAFETY - The ProofsCheckResult uses wildcard type <*>
 *    - Consider using a more specific generic constraint if possible
 *    - Document what types are expected in the result
 *
 * 4. CALLBACK PATTERN - onResult uses nullable Proof (null = cancel)
 *    - Consider sealed class Result { data class Success(val proof: Proof), object Cancelled }
 *    - This makes the intent more explicit and type-safe
 *
 * 5. DOCUMENTATION - Add examples showing typical implementation patterns
 *    - Show how to create a simple proof component
 *    - Document the lifecycle: render -> collect input -> call onResult
 *
 * 6. VALIDATION - No validation that 'via' matches the proof method
 *    - Consider adding validation that via is unique across implementations
 *    - Could use sealed interface to enforce compile-time uniqueness
 *
 * 7. EARLY PROOF CANCELLATION - earlyProof tasks may need explicit cancellation
 *    - Document that callers should cancel pending earlyProof tasks when user selects different method
 *    - Consider returning Job or other cancellable handle
 */