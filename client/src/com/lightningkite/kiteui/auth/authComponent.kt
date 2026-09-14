package com.lightningkite.kiteui.auth

import com.lightningkite.kiteui.*
import com.lightningkite.kiteui.models.*
import com.lightningkite.kiteui.reactive.Action
import com.lightningkite.kiteui.reactive.PersistentProperty
import com.lightningkite.kiteui.views.*
import com.lightningkite.kiteui.views.direct.*
import com.lightningkite.kiteui.views.l2.field
import com.lightningkite.lightningserver.auth.AuthEndpoints
import com.lightningkite.lightningserver.auth.LightningServerAuthentication
import com.lightningkite.lightningserver.sessions.LogInRequest
import com.lightningkite.lightningserver.sessions.ProofsCheckResult
import com.lightningkite.lightningserver.sessions.proofs.*
import com.lightningkite.reactive.context.*
import com.lightningkite.reactive.core.*
import com.lightningkite.services.data.toEmailAddress
import com.lightningkite.services.data.toPhoneNumber
import kotlinx.coroutines.*
import kotlinx.serialization.Serializable
import kotlin.math.roundToInt
import kotlin.time.Clock.System.now
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.DurationUnit

/**
 * Represents a user's primary identifier for authentication.
 *
 * @property property The type of identifier (e.g., "email", "phone", "username", "_id")
 * @property value The actual identifier value (e.g., "user@example.com", "+1234567890")
 */
public data class UserIdentification(val property: String, val value: String)

// Pattern to detect email addresses
private val emailRegex = Regex("""[\w\-+._]+@(?:[\w\-]+\w\.)+[\w\-]+\w$""")

// Pattern to detect phone numbers - requires at least 7 digits with optional separators
private val phoneRegex = Regex("""\+?(?:[0-9][-. ]?){6,}[0-9]$""")

/**
 * DSL function to render an authentication component in a ViewWriter.
 *
 * This is the primary entry point for adding proof-based authentication to your app.
 * It creates an [AuthComponent] instance and renders it into the view hierarchy.
 *
 * @param endpoints The authentication endpoints configuration from the server
 * @param subjectType The type of subject being authenticated (e.g., "user", "admin"). Defaults to single subject type.
 * @param subject The specific authentication client endpoints for this subject type
 * @param supportUsernames Whether to allow username-based authentication (in addition to email/phone)
 * @param knownDeviceLocalStorageName Key for storing known device credentials in local storage. Null disables known device feature.
 * @param filterMethods Optional function to filter/reorder available authentication methods based on context
 * @param onAuthentication Callback invoked with the refresh token when authentication succeeds
 * @return The rendered view
 */
public fun ViewWriter.authComponent(
    endpoints: AuthEndpoints,
    subjectType: String = endpoints.subjects.keys.single(),
    subject: AuthClientEndpoints<*, *> = endpoints.subjects[subjectType]!!,
    supportUsernames: Boolean = false,
    knownDeviceLocalStorageName: String? = "known-device",
    filterMethods: suspend (UserIdentification?, List<ProofComponent>) -> List<ProofComponent> = { _, it -> it },
    onAuthentication: suspend (token: String) -> Unit,
): Unit = AuthComponent(
    endpoints = endpoints,
    supportUsernames = supportUsernames,
    subjectType = subjectType,
    subject = subject,
    knownDeviceLocalStorageName = knownDeviceLocalStorageName,
    filterMethods = filterMethods,
    onAuthentication = onAuthentication,
).render(to = this)

/**
 * Modern proof-based authentication component with progressive proof collection.
 *
 * This component implements a flexible multi-factor authentication flow where:
 * 1. User enters their primary identifier (email/phone/username/passkey)
 * 2. System determines which proof methods are available
 * 3. User provides one or more proofs until authentication requirements are met
 * 4. User finalizes login with optional session length and device memory
 *
 * The component tracks authentication progress and automatically checks proofs with the server.
 * It supports various proof methods: email codes, SMS codes, passwords, TOTP, backup codes, and WebAuthN.
 *
 * Key features:
 * - Progressive proof collection (proofs add up to required strength)
 * - Known device support for faster future logins
 * - Configurable session lengths
 * - WebAuthN/Passkey support with conditional UI
 * - Real-time validation and error handling
 *
 * @property endpoints The authentication endpoints configuration from the server
 * @property subjectType The type of subject being authenticated (e.g., "user", "admin")
 * @property subject The specific authentication client endpoints for this subject type
 * @property supportUsernames Whether to allow username-based authentication
 * @property knownDeviceLocalStorageName Key for storing known device credentials. Null disables feature.
 * @property filterMethods Function to filter/reorder available auth methods dynamically
 * @property onAuthentication Callback invoked with refresh token on successful authentication
 */
public open class AuthComponent(
    public val endpoints: AuthEndpoints,
    public val subjectType: String = endpoints.subjects.keys.single(),
    public val subject: AuthClientEndpoints<*, *> = endpoints.subjects[subjectType]!!,
    public val supportUsernames: Boolean = false,
    public val knownDeviceLocalStorageName: String? = "known-device",
    public val filterMethods: suspend (UserIdentification?, List<ProofComponent>) -> List<ProofComponent> = { _, it -> it },
    public val onAuthentication: suspend (token: String) -> Unit,
) {
    /** The user's current primary identifier (email/phone/username/passkey ID), or null if not yet determined */
    public val primaryIdentifier: Signal<UserIdentification?> = Signal<UserIdentification?>(null)
    public val rawPrimaryInput: Signal<String> = Signal("")

    /** List of successfully collected proofs. Accumulates as user completes authentication steps. */
    public val proofs: Signal<List<Proof>> = Signal(listOf<Proof>())

    /** The currently active proof component being rendered, or null if showing proof selection screen */
    public val currentProof: Signal<ProofComponent?> = Signal<ProofComponent?>(null)

    /**
     * Server validation result for the current set of proofs.
     * Automatically re-checks whenever proofs change.
     * Contains available next proof options, strength requirements, and ready-to-login status.
     */
    public val authResult: Reactive<ProofsCheckResult<out Comparable<*>>?> = rememberSuspending {
        subject.checkProofs(proofs().also { if (it.isEmpty()) return@rememberSuspending null })
    }

    /**
     * Calculates the list of currently available proof methods based on:
     * - Which proofs have already been solved
     * - Platform support for each method
     * - Primary identifier requirements
     * - Server-provided available options
     * - Custom filtering via filterMethods
     *
     * This list updates reactively as authentication progresses.
     */
    public val proofOptions: Reactive<List<ProofComponent>> = rememberSuspending {
        val primaryIdentifier = primaryIdentifier()
        val result = authResult()
        // Track which proof methods have already been used
        val solved = proofs.value.mapTo(HashSet()) { it.via }
        // Get server-provided available methods (if known)
        val available = result?.options?.map { it.method.via }
        endpoints.components(subjectType)
            .filter { it.via !in solved } // Don't show already-used methods
            .filter { it.supported() } // Check platform support
            .filter {
                // Apply primary identifier requirements
                if (primaryIdentifier == null)
                    !it.primaryIdentifierRequired // Only allow methods that don't need identifier
                else if (proofs.value.isEmpty())
                // First proof: must match primary identifier type
                    (it.property == null || it.property == primaryIdentifier.property) &&
                            (supportUsernames || primaryIdentifier.property != "username")
                else
                    true // After first proof, all methods allowed
            }
            .filter { available == null || it.via in available } // Respect server's available list
            .let { filterMethods(primaryIdentifier, it) } // Apply custom filtering
    }

    /**
     * Action to automatically select the first available proof method.
     * Used on web platform when user presses enter in the primary identifier field.
     */
    public val selectFirstAction: Action = Action("Select First", Icon.done) {
        if (currentProof.value == null) {
            proofOptions().firstOrNull()?.let {
                currentProof.value = it
            }
        }
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
    public open fun render(to: ElementWriter) {
        to.col {

            renderPrimaryIdentifier(this)

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
                forEachAnimated(remember { listOfNotNull(currentProof()).map { it to authResult() } }.debounce(10.milliseconds)) { (it, authResult) ->
                    it.render(this@forEachAnimated, primaryIdentifier.value, authResult) {
                        if (it != null) {
                            // Proof successfully collected
                            proofs.value += it
                            // Set primary identifier from first proof if not already set (passkey flow)
                            if (primaryIdentifier.value == null) {
                                primaryIdentifier.value = UserIdentification(it.property, it.value)
                            }
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
     * Renders the primary identifier input and display.
     *
     * Shows either:
     * - Input field for email/phone/username (at start of flow)
     * - Display of current identifier with cancel button (after identifier set)
     * - "Use Passkey" button (if WebAuthN enabled and no identifier set)
     *
     * Automatically detects identifier type (email vs phone vs username) based on input format.
     */
    public open fun renderPrimaryIdentifier(to: RowOrCol): Unit = with(to) {

        shownWhen { proofs().isEmpty() && currentProof() == null }.field(
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
                debugName = "primaryInput"
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
                // Local signal for text field binding (separate from validated primaryIdentifier)
                content bind rawPrimaryInput
                // Sync text field with validated identifier (but not for passkey IDs)
                reactive {
                    val p = primaryIdentifier()
                    // Don't show internal passkey IDs in the text field
                    if (p?.property?.contains("_id") != true)
                        rawPrimaryInput.value = p?.value ?: rawPrimaryInput.value
                }
                // Validate and parse user input to determine identifier type
                reactive {
                    val it = rawPrimaryInput()
                    if (it == primaryIdentifier.value?.value) return@reactive
                    primaryIdentifier.value = run lens@{
                        if (it.isBlank()) return@lens null
                        // Try email first
                        try {
                            if (emailRegex.matches(it)) {
                                it.trim().toEmailAddress() // Validate email format
                                UserIdentification("email", it.trim())
                            } else null
                        } catch (_: Exception) {
                            null
                        } ?: try {
                            // Try phone number
                            if (phoneRegex.matches(it)) {
                                it.trim().toPhoneNumber() // Validate phone format
                                UserIdentification("phone", it.trim())
                            } else {
                                null
                            }
                        } catch (e: Exception) {
                            e.printStackTrace2()
                            null
                        } ?: if (supportUsernames) UserIdentification("username", it.trim()) else null
                        // Fall back to username if enabled, otherwise null
                    }
                }
                reactive {
                    if (currentProof() == null) requestFocus()
                }
                if (Platform.current == Platform.Web) {
                    action = selectFirstAction
                }
            }
        }

        shownWhen { proofs().isNotEmpty() || currentProof() != null }.row {
            centered.button {
                debugName = "cancelButton"
                padding = 0.2.rem
                icon(Icon.arrowBack, "Cancel")
                onClick {
                    if (primaryIdentifier.value?.property?.contains("_id") == true) {
                        primaryIdentifier.value = null
                    }
                    currentProof.value = null
                    proofs.value = emptyList()
                }
            }
            centered.expanding.text {
                ::content{ primaryIdentifier()?.takeIf { !it.property.contains("_id") }?.value ?: "Using Passkey" }
            }
        }

        endpoints.webAuthNProof?.let { webAuthn ->
            if (endpoints.webAuthNIncludePasskeyUI) {
                val webAuthAvailable =
                    rememberSuspending { ClientAuthenticator.getClientAuthenticator().webAuthNAvailable() }
                shownWhen { primaryIdentifier() == null && currentProof() == null && proofs().isEmpty() && webAuthAvailable() }.col {

                    centered.text("Or")

                    card.buttonTheme.button {
                        debugName = "usePasskeyButton"
                        centered.sizeConstraints(width = 16.rem).frame {
                            centered.row {
                                centered.icon(Icon.passkey, "")
                                centered.text("Use Passkey")
                            }
                        }
                        onClick {
                            currentProof.value =
                                WebAuthNProofComponent(webAuthn, endpoints.subjects.keys.single(), false)
                        }
                    }
                }
            }
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
    public open fun ElementWriter.renderFinalize() {
        col {
            val desiredSessionLength = Signal<Duration?>(1.days)
            val rememberDevice = Signal(Platform.current != Platform.Web)
            val knownDevice =
                knownDeviceLocalStorageName?.let { PersistentProperty<KnownDeviceSecretInfoStuff?>(it, null) }
            val knownDeviceOptions = rememberSuspending {
                endpoints.knownDeviceProof?.knownDeviceOptions()
            }
            centered.h5("Ready to login")
            shownWhen { knownDeviceOptions() != null }.row {
                centered.checkbox { debugName = "rememberDeviceCheckbox"; checked bind rememberDevice }
                centered.text {
                    content = "This is my device"
                }
            }
            shownWhen { rememberDevice() || knownDeviceOptions() == null }.row {
                centered.checkbox {
                    debugName = "keepLoggedInCheckbox"
                    checked bind desiredSessionLength.lens(
                        get = { it != 1.days },
                        set = { if (it) null else 1.days }
                    )
                }
                centered.text {
                    ::content {
                        val days = authResult()?.maxExpiration?.let { it - now() }?.toDouble(DurationUnit.DAYS)
                            ?.roundToInt()
                        if (days != null) "Keep me logged in for $days days" else "Keep me logged in"
                    }
                }
            }


            important.buttonTheme.button {
                debugName = "loginButton"
                centered.text("Login")
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
                                        subjectType,
                                        it
                                    )
                                ).knownDeviceProof?.establishKnownDeviceV2()?.let {
                                    knownDevice?.value = KnownDeviceSecretInfoStuff(
                                        info = it,
                                        primaryIdentifier = primaryIdentifier.value?.value ?: ""
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

    /**
     * Renders the proof method selection screen.
     *
     * Shows a list of available proof methods as buttons. Also starts any "early proof"
     * tasks (e.g., WebAuthN conditional UI) that can run in the background.
     *
     * When user selects a method, cancels background tasks and sets currentProof.
     */
    public open fun ElementWriter.pickProof() {
        col {
            // Launch background tasks for early proofs (e.g., WebAuthN autofill)
            val cancelIfSelected = launch {
                proofOptions()
                    .mapNotNull {
                        it.earlyProof?.let { task ->
                            launch {
                                // If early proof succeeds, add it to proofs list automatically
                                task()?.let { proofs.value += it }
                            }
                        }
                    }
            }
            // Render button for each available proof method
            forEachAnimated(proofOptions) {
                card.buttonTheme.button {
                    debugName = it.name(true)
                    centered.sizeConstraints(width = 16.rem).frame {
                        centered.row {
                            centered.icon(it.icon, "")
                            centered.text { ::content{ it.name(proofs().isEmpty()) } }
                        }
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
public data class KnownDeviceSecretInfoStuff(
    val info: KnownDeviceSecretAndExpiration,
    val primaryIdentifier: String,
)

/*
 * API IMPROVEMENT RECOMMENDATIONS:
 *
 * 1. PHONE NUMBER VALIDATION - phoneRegex is too permissive
 *    - Regex "\+?[0-9-. ]+$" matches invalid inputs like "123-456" or "1 2 3"
 *    - Consider using a proper phone number validation library
 *    - Add minimum/maximum digit count validation
 *
 * 2. ERROR HANDLING - Exception handling could be more specific
 *    - Currently catches all exceptions during email/phone validation
 *    - Consider specific exception types and user-friendly error messages
 *    - The printStackTrace2() call at line 325 should be logged properly
 *
 * 3. STATE MANAGEMENT - Multiple Signal instances could cause race conditions
 *    - primaryIdentifier, proofs, currentProof are independent signals
 *    - Consider consolidating into single state machine or sealed class hierarchy
 *    - Would prevent invalid states (e.g., currentProof set but primaryIdentifier null)
 *
 * 4. DUPLICATE LOGIC - Primary identifier detection appears in multiple places
 *    - The reactive block at line 302-329 has complex nested logic
 *    - Extract to separate function: fun detectIdentifierType(input: String): UserIdentification?
 *    - Would improve testability and reusability
 *
 * 5. PLATFORM-SPECIFIC BEHAVIOR - selectFirstAction only applies on Web
 *    - Line 333: if (Platform.current == Platform.Web) { action = selectFirstAction }
 *    - Document this platform difference more clearly
 *    - Consider making platform behavior more explicit/configurable
 *
 * 6. MAGIC VALUES - Several hardcoded values should be constants
 *    - 10.milliseconds debounce (line 229)
 *    - 0.01f..0.99f progress range (line 211)
 *    - 1.days default session length (line 389)
 *    - 16.rem button width (line 478)
 *
 * 7. KNOWN DEVICE HANDLING - Known device logic mixed with authentication flow
 *    - Lines 390-392 create local knownDevice property
 *    - But class also has knownDeviceLocalStorageName property
 *    - Consider extracting known device logic to separate component/helper
 *
 * 8. CALLBACK COMPLEXITY - onAuthentication runs complex async logic
 *    - Lines 415-433 show complex flow: login -> callback -> establish known device
 *    - Errors in known device establishment are silently ignored
 *    - Consider separating concerns: authentication vs device memory
 *
 * 9. IDENTIFIER DISPLAY - Special handling for "_id" properties
 *    - Lines 297-299, 331: if (p?.property?.contains("_id") != true)
 *    - Magic string "_id" appears multiple times
 *    - Extract constant and document why _id fields are treated differently
 *
 * 10. WEBSOCKET/BACKGROUND TASK CLEANUP - earlyProof tasks may leak
 *     - cancelIfSelected.cancel() only called on explicit selection (line 484)
 *     - If user navigates away or component unmounts, tasks may continue
 *     - Add cleanup in component lifecycle or use ResourceUse pattern
 *
 * 11. REACTIVE DEPENDENCIES - rememberSuspending blocks have implicit dependencies
 *     - authResult (line 138) depends on proofs, but dependency not explicit
 *     - proofOptions (line 152) has many dependencies that could change
 *     - Consider explicit dependency tracking or documentation
 *
 * 12. ACCESSIBILITY - No accessibility hints or labels for screen readers
 *     - Important for visually impaired users
 *     - Add content descriptions for icons
 *     - Add labels for progress indicators and loading states
 */
