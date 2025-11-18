package com.lightningkite.lightningserver.admin

import com.lightningkite.kiteui.auth.authComponent2
import com.lightningkite.kiteui.exceptions.ExceptionToMessages
import com.lightningkite.kiteui.exceptions.installLsError
import com.lightningkite.kiteui.forms.ServerFileRenderer.type
import com.lightningkite.kiteui.forms.displayName
import com.lightningkite.kiteui.models.*
import com.lightningkite.kiteui.navigation.DefaultSerializersModule
import com.lightningkite.kiteui.navigation.PageNavigator
import com.lightningkite.kiteui.navigation.pageNavigator
import com.lightningkite.kiteui.views.ViewWriter
import com.lightningkite.kiteui.views.card
import com.lightningkite.kiteui.views.centered
import com.lightningkite.kiteui.views.compact
import com.lightningkite.kiteui.views.direct.*
import com.lightningkite.kiteui.views.l2.*
import com.lightningkite.lightningserver.LSError
import com.lightningkite.lightningserver.auth.LightningServerAuthentication
import com.lightningkite.lightningserver.sessions.proofs.LiveAuthClientEndpoints
import com.lightningkite.services.database.Condition
import com.lightningkite.reactive.context.invoke
import com.lightningkite.reactive.context.reactive
import com.lightningkite.reactive.core.remember
import com.lightningkite.reactive.core.rememberSuspending
import com.lightningkite.reactive.extensions.debounceWrite
import com.lightningkite.reactive.extensions.modify
import com.lightningkite.services.database.SerializableProperty
import com.lightningkite.services.database.SerializationRegistry
import com.lightningkite.services.database.serializableProperties
import com.lightningkite.services.files.ServerFile
import com.lightningkite.titleCase
import kotlinx.serialization.modules.EmptySerializersModule
import kotlin.time.Duration.Companion.milliseconds

/**
 * External JavaScript module for timezone support.
 * Imports the @js-joda/timezone library to enable timezone functionality in the admin panel.
 *
 * This is required for proper date/time handling across different timezones in the admin interface.
 * The module must be referenced (even if not directly used) to ensure it's loaded before any
 * date operations occur.
 */
@JsModule("@js-joda/timezone")
@JsNonModule
external object JsJodaTimeZoneModule

/** Default debounce time for server URL input to prevent rapid reconnections */
private val SERVER_URL_DEBOUNCE_TIME = 500.milliseconds

/** Standard width for settings input fields in the profile menu */
private val SETTINGS_FIELD_WIDTH = 20.rem

/**
 * Main application entry point for the Lightning Server admin panel.
 *
 * This function sets up the entire admin UI including:
 * - Authentication components and session management
 * - Navigation structure with schema-driven collection screens
 * - Global error handling and serialization
 * - User profile menu with server configuration
 *
 * @param navigator The main page navigator for screen transitions
 * @param dialog The dialog navigator for modal dialogs and overlays
 *
 * Note: This function performs reactive setup and will rebuild navigation when server schema changes.
 */
fun ViewWriter.app(navigator: PageNavigator, dialog: PageNavigator) {
    // Force initialization of the timezone module to ensure timezone support is available
    // This reference is intentional - the module must be loaded even if not directly used
    val x = JsJodaTimeZoneModule

    // Register core serializers for Lightning Server error handling and file uploads
    // These must be registered globally to enable proper deserialization across the app
    SerializationRegistry.master.register(LSError.serializer())
    SerializationRegistry.master.register(ServerFile.serializer())

    // Install custom error message handlers for Lightning Server errors
    // Provides user-friendly error messages for common Lightning Server API errors
    ExceptionToMessages.root.installLsError()

    // Configure the navigation factory to use top-and-left navigation layout
    // This sets up the sidebar + top bar layout for the admin panel
    appNavFactory.value = ViewWriter::appNavTopAndLeft

    appNav(navigator, dialog) {
        // TODO: Make app name configurable instead of hard-coded
        appName = "KiteUI Sample App"

        // Dynamically build navigation items based on server schema and permissions
        // This is reactive and will rebuild when permissions or schema change
        ::navItems {
            val permissions = loadedPermissions()
            buildList {
                add(NavLink("Home", icon = Icon.home) { HomePage() })

                // Optional endpoints screen (enabled via admin settings)
                // Useful for debugging and API exploration
                if (adminSettings().showEndpoints) {
                    add(NavLink("Endpoints", icon = Icon.menu) { EndpointsPage() })
                }

                // Auto-generate navigation items for each collection in the schema
                // Sorted alphabetically by display name for consistent ordering
                adminServer().models.entries.sortedBy { it.value.serializer.displayName }.forEach {
                    // Only show collections where the user has read permission
                    // Condition.Never means no read access at all
                    if(permissions[it.key]?.read != Condition.Never) {
                        add(
                            NavLink(
                                // Use docGroup if available (for organizing related collections),
                                // otherwise fall back to serializer display name
                                it.value.docGroup?.titleCase() ?: it.value.serializer.displayName,
                                icon = Icon.list
                            ) { CollectionAdminPage(it.key) }
                        )
                    }
                }
            }
        }

        // Configure the profile/settings menu in the navigation bar
        // This provides server configuration, authentication, and user profile access
        actions = run {
            listOf(
                NavCustom(
                    title = { "Profile" },
                    icon = { Icon.person },
                    count = null,
                    hidden = { false },
                    square = {
                        compact.menuButton {
                            // Fetch and display the current user's identity
                            // Falls back to "Anonymous" if not authenticated or "No Server" if unreachable
                            val me = rememberSuspending label@{
                                try {
                                    val creds = adminAuthentication() ?: return@label "Anonymous"
                                    val sub = adminServer().authEndpoints(creds)
                                        .subjects[adminCredentials()?.userType ?: return@label "Anonymous"]
                                        ?: return@label "Anonymous"

                                    val serializer = (sub as LiveAuthClientEndpoints<*, *>).subjectSerializer

                                    // Fetch current user and extract display name
                                    // Prefers email/phone/username fields if available
                                    // This provides a recognizable identifier in the UI
                                    val self = sub.getSelf()
                                    serializer.serializableProperties
                                        ?.find { it.name == "email" || it.name == "phone" || it.name == "username" }
                                        ?.let { it as SerializableProperty<Any, Any?> }
                                        ?.get(self)
                                        ?.toString()
                                        // Fallback to toString() but limit to 40 chars to avoid UI overflow
                                        ?: self.toString().take(40)

                                } catch (e: Exception) {
                                    // TODO: Distinguish between network errors, auth errors, and other failures
                                    "No Server"
                                }
                            }
                            col {
                                centered.icon(Icon.person, "Login")
                                centered.subtext {
                                    // Show first 10 characters of user identifier in collapsed menu button
                                    // Keeps the button compact while still showing recognizable info
                                    ::content { me().take(10) }
                                }
                            }
                            preferredDirection = PopoverPreferredDirection.belowLeft
                            requireClick = true
                            opensMenu {
                                col {
                                    // Full user identifier in expanded menu
                                    centered.subtext {
                                        ::content { me() }
                                    }

                                    // Server URL configuration with debounced updates
                                    // Debouncing prevents rapid re-connections during typing
                                    sizeConstraints(width = SETTINGS_FIELD_WIDTH).field("Server") {
                                        textInput {
                                            content bind serverUrl.debounceWrite(SERVER_URL_DEBOUNCE_TIME)
                                        }
                                    }

                                    // User type selector - dynamically populated from server's auth endpoints
                                    // Lens creates a bidirectional binding to the userType field of adminCredentials
                                    val userType = adminCredentials.lens(
                                        get = { it?.userType },
                                        modify = { o, v -> o?.copy(userType = v) ?: AdminCredentials(userType = v) }
                                    )
                                    sizeConstraints(width = SETTINGS_FIELD_WIDTH).field("User Type") {
                                        select {
                                            bind(
                                                userType,
                                                remember {
                                                    // Fetch available user types from the server schema
                                                    // null represents "no auth" option
                                                    listOf(null) + try {
                                                        adminServer().authEndpoints(null).subjects.keys.toList()
                                                    } catch (e: Exception) {
                                                        // TODO: Log error or show warning to user
                                                        listOf()
                                                    }
                                                },
                                                { it ?: "None" }
                                            )
                                        }
                                    }

                                    // Manual token/session input field
                                    // Allows directly pasting a session token instead of logging in
                                    // Useful for testing or when token is obtained externally
                                    // TODO: Extract magic number 20.rem to a constant
                                    sizeConstraints(width = 20.rem).field("Token") {
                                        textInput {
                                            content bind adminCredentials.lens(
                                                get = { it?.session ?: "" },
                                                modify = { o, v ->
                                                    val session = v.takeUnless { it.isBlank() }
                                                    o?.copy(session = session) ?: AdminCredentials(session = session)
                                                }
                                            )
                                        }
                                    }

                                    // Dynamic authentication component based on selected user type
                                    // Rebuilds when userType or server changes
                                    // Provides the full auth flow (password, MFA, etc.)
                                    // TODO: Extract magic number 20.rem to a constant
                                    sizeConstraints(width = 20.rem).stack {
                                        reactive {
                                            clearChildren()
                                            try {
                                                authComponent2(
                                                    adminServer().authEndpoints(null),
                                                    userType() ?: return@reactive
                                                ) { v ->
                                                    // Store the session token when authentication succeeds
                                                    // This token is used for all subsequent API requests
                                                    adminCredentials.modify {
                                                        it?.copy(
                                                            session = v
                                                        ) ?: AdminCredentials(session = v)
                                                    }
                                                }
                                            } catch (e: Exception) {
                                                // TODO: More specific error messaging based on exception type
                                                text("Need valid URL")
                                            }
                                        }
                                    }

                                    // Logout button - only visible when credentials exist
                                    // Provides confirmation dialog to prevent accidental logout
                                    onlyWhen { adminCredentials() != null }.card.button {
                                        centered.row {
                                            centered.icon(Icon.logout, "Log Out")
                                            centered.text("Log Out")
                                        }
                                        onClick("Log Out") {
                                            confirmDanger("Log Out", "Are you sure you want to log out?", "Log Out") {
                                                // TODO: Preserve current navigation state instead of always resetting to HomePage
                                                pageNavigator.reset(HomePage())
                                                adminCredentials.value = null
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                )
            )
        }

        // TODO: Commented code - either implement or remove this exists condition
//        ::exists {
//            navigator.currentPage.await() !is UseFullPage
//        }
    }
}

/*
 * TODO: API Improvement Recommendations for App.kt
 *
 * 1. Hard-coded App Name: The app name "KiteUI Sample App" should be configurable or derived from server schema
 *
 * 2. User Display Logic: The logic to extract user display name (email/phone/username) is complex and repeated.
 *    Consider extracting to a dedicated function or property in the auth system.
 *
 * 3. Navigation Rebuild Performance: The entire navigation rebuilds on schema/permission changes. Consider
 *    more granular reactivity to avoid rebuilding unchanged sections.
 *
 * 4. Error Handling: Multiple try-catch blocks with generic exception handling. Consider more specific
 *    error types and user-friendly error messages.
 *
 * 5. Debug Logging: Multiple println statements for debugging should be removed or replaced with proper
 *    logging infrastructure with configurable levels.
 *
 * 6. Type Safety: adminServer() returns nullable types that are force-unwrapped with !!. Add proper null
 *    checks or error screens when server is unavailable.
 *
 * 7. Magic Numbers: The debounce time (500ms) and size constraints (20.rem) should be extracted as constants.
 *
 * 8. Commented Code: Unused theme configuration code should either be removed or properly documented.
 *
 * 9. Settings Access: Admin settings visibility should have validation to prevent invalid states.
 *
 * 10. Authentication Flow: The authentication component rebuilds on any reactive change. Consider memoization
 *     to prevent unnecessary re-initialization.
 *
 * 11. Session Management: Logout confirmation dialog resets to HomePage - should preserve navigation state or
 *     allow configuration.
 *
 * 12. Permission Loading: loadedPermissions() is called synchronously but fetches async data. Add loading
 *     states and error handling for permission fetch failures.
 */
