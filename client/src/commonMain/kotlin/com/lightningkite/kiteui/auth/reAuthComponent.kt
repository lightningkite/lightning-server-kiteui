package com.lightningkite.kiteui.auth

import com.lightningkite.kiteui.models.ErrorSemantic
import com.lightningkite.kiteui.models.rem
import com.lightningkite.kiteui.views.*
import com.lightningkite.kiteui.views.direct.*
import com.lightningkite.kiteui.views.direct.icon
import com.lightningkite.lightningserver.auth.AuthEndpoints
import com.lightningkite.lightningserver.sessions.LogInRequest
import com.lightningkite.lightningserver.sessions.ProofsCheckResult
import com.lightningkite.lightningserver.sessions.proofs.*
import com.lightningkite.reactive.context.invoke
import com.lightningkite.reactive.core.*
import com.lightningkite.reactive.extensions.debounce
import kotlinx.coroutines.launch
import kotlin.time.Clock.System.now
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes


/**
 * DSL function to render an authentication component in a ViewWriter.
 *
 * This is the primary entry point for adding proof-based authentication to your app.
 * It creates an [ReAuthComponent] instance and renders it into the view hierarchy.
 *
 * @property endpoints The authentication endpoints configuration from the server
 * @property subjectType The type of subject being authenticated (e.g., "user", "admin")
 * @property subject The specific authentication client endpoints for this subject type
 * @property knownDeviceLocalStorageName Key for storing known device credentials. Null disables feature.
 * @property filterMethods Function to filter/reorder available auth methods dynamically
 * @property onAuthentication Callback invoked with refresh token on successful authentication
 * @return The rendered view
 */
fun ViewWriter.reAuthComponent(
    endpoints: AuthEndpoints,
    subjectId: String,
    subjectType: String = endpoints.subjects.keys.single(),
    subject: AuthClientEndpoints<*, *> = endpoints.subjects[subjectType]!!,
    knownDeviceLocalStorageName: String? = "known-device",
    newSessionDuration: Duration = 15.minutes,
    onAuthentication: suspend (token: String) -> Unit,
) = ReAuthComponent(
    subjectId = subjectId,
    endpoints = endpoints,
    subjectType = subjectType,
    subject = subject,
    knownDeviceLocalStorageName = knownDeviceLocalStorageName,
    newSessionDuration = newSessionDuration,
    onAuthentication = onAuthentication,
).render(to = this)

/**
 * Legacy re-authentication component for elevated permissions.
 *
 * Used when a logged-in user needs to re-verify their identity for sensitive operations.
 * Only shows proof methods that the user has already set up (no registration flow).
 *
 * The component automatically attempts to use known device credentials if available,
 * then prompts for additional proofs as needed to meet authentication requirements.
 *
 * @property endpoints The authentication endpoints configuration from the server
 * @property subjectType The type of subject being authenticated (e.g., "user", "admin")
 * @property subject The specific authentication client endpoints for this subject type
 * @property knownDeviceLocalStorageName Key for storing known device credentials. Null disables feature.
 * @property onAuthentication Callback invoked with refresh token on successful authentication
 */
open class ReAuthComponent(
    val endpoints: AuthEndpoints,
    val subjectId: String,
    val subjectType: String = endpoints.subjects.keys.single(),
    val subject: AuthClientEndpoints<*, *> = endpoints.subjects[subjectType]!!,
    val knownDeviceLocalStorageName: String? = "known-device",
    val newSessionDuration: Duration = 15.minutes,
    val onAuthentication: suspend (token: String) -> Unit,
) {

    /** List of successfully collected proofs. Accumulates as user completes authentication steps. */
    val proofs = Signal(listOf<Proof>())

    /** The currently active proof component being rendered, or null if showing proof selection screen */
    val currentProof = Signal<Pair<ProofComponent, ProofOption>?>(null)

    /** Whether authentication is in progress (checking proofs with server) */
    val authenticating = Signal(false)

    /**
     * Server validation result for the current set of proofs.
     * Automatically re-checks whenever proofs change.
     * Contains available next proof options, strength requirements, and ready-to-login status.
     */
    val authResult: Reactive<ProofsCheckResult<out Comparable<*>>?> = rememberSuspending {
        subject.checkProofs(proofs().also { if (it.isEmpty()) return@rememberSuspending null })
    }

    /** Server's authentication requirements for this user */
    val requirements = rememberSuspending { subject.authRequirements() }

    /**
     * Calculates the list of currently available proof methods based on:
     * - Which proofs have already been solved
     * - Platform support for each method
     * - Primary identifier requirements
     * - Server-provided available options
     *
     * This list updates reactively as authentication progresses.
     */
    val proofOptions = rememberSuspending {
        // Track which proof methods have already been used
        val solved = proofs.value.mapTo(HashSet()) { it.via }
        // Get server-provided available methods (if known)
        val available = requirements().options
        endpoints.components(subjectType)
            .filter { it.via !in solved } // Don't show already-used methods
            .filter { it.supported() } // Check platform support
            .mapNotNull { available.find { option -> option.method.via == it.via }?.let { option -> it to option } }
    }

    /**
     * Renders the main authentication UI flow.
     *
     * The UI adapts to show different states:
     * - Primary identifier input (email/phone/username/passkey)
     * - Progress bar showing authentication strength
     * - Proof method selection
     * - Active proof collection UI
     * - Finalization screen (session length, device memory)
     *
     * @param to The ViewWriter to render into
     * @return The rendered view
     */
    open fun render(to: ViewWriter) {
        to.col {

            // Calculate authentication progress as a percentage
            val progress =
                remember {
                    authResult()?.let { proofs().sumOf { it.strength } / it.strengthRequired.toFloat() } ?: 0.00f
                }
            // Show progress bar when partially authenticated
            shownWhen {
                progress() in 0.01f..0.99f
            }.card.progressBar {
                ::ratio { progress() }
            }

            // Loading indicator while checking proofs with server
            centered.shownWhen { !authResult.state().ready }.activityIndicator()
            // Error display if proof validation fails
            shownWhen { authResult.state().exception != null }.themed(ErrorSemantic).col {
                val msg = remember { authResult.state().exception?.let { context.exceptionMessage(it) } }
                text { ::content { msg()?.title ?: "Error" } }
                subtext { ::content { msg()?.body ?: "" } }
            }
            // Show proof selection when not ready to login and no proof currently active
            shownWhen { authResult()?.readyToLogIn != true && currentProof() == null }.pickProof()
            // Render the active proof component
            shownWhen { currentProof() != null }.col {
                // Debounce to prevent rapid re-renders during proof transitions

                forEachAnimated(remember { listOfNotNull(currentProof()) }.debounce(10.milliseconds)) { (component, option) ->
                    (component as? EasierProofComponent)?.render(
                        this@forEachAnimated,
                        primaryIdentifier = UserIdentification("$subjectType/_id", subjectId),
                        option
                    ) {
                        if (it != null) {
                            // Proof successfully collected
                            proofs.value += it
                        }
                        // Clear active proof (either success or cancel)
                        currentProof.value = null
                    }
                }
            }
            // Show finalization screen when authentication requirements are met
            shownWhen { authResult()?.readyToLogIn == true }.renderFinalize()
        }
    }

    /**
     * Renders the finalization screen shown when authentication requirements are met.
     *
     * Allows user to:
     * - Choose whether to remember this device (known device feature)
     * - Select session length (1 day vs maximum allowed)
     * - Complete the login
     *
     * On successful login, calls onAuthentication callback and optionally establishes known device.
     */
    open fun ElementWriter.renderFinalize() {
        col {
            centered.h5("Ready to login")

            important.buttonTheme.button {
                debugName = "loginButton"
                centered.text("Login")
                onClick {
                    val result = subject.logInV2(
                        LogInRequest(
                            proofs = proofs(),
                            expires = newSessionDuration.let { now() + it }
                        ))

                    result.refreshToken?.let {
                        onAuthentication(it)
                    }
                }
            }
        }
    }

    /**
     * Renders the proof method selection screen.
     *
     * Shows a list of available proof methods as buttons. Also starts any "early proof"
     * tasks (e.g., WebAuthN conditional UI) that can run in the background.
     *
     * When user selects a method, cancels background tasks and sets currentProof.
     */
    open fun ElementWriter.pickProof() {
        col {
            // Launch background tasks for early proofs (e.g., WebAuthN autofill)
            val cancelIfSelected = launch {
                proofOptions()
                    .mapNotNull { (component, option) ->
                        component.earlyProof?.let { task ->
                            launch {
                                // If early proof succeeds, add it to proofs list automatically
                                task(this@col)?.let { proofs.value += it }
                            }
                        }
                    }
            }
            // Render button for each available proof method
            forEachAnimated(proofOptions) { it ->
                card.buttonTheme.button {
                    debugName = it.first.name
                    centered.sizeConstraints(width = 16.rem).row {
                        icon(it.first.icon, "")
                        text(it.first.name)
                    }
                    onClick {
                        // User made explicit selection; cancel background tasks
                        cancelIfSelected.cancel()
                        currentProof.value = it
                    }
                }
            }
        }
    }
}
