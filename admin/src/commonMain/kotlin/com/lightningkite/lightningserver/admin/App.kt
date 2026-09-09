@file:OptIn(ExperimentalKiteUi::class, EvolvingAppearance::class)

package com.lightningkite.lightningserver.admin

//
// IMPROVEMENT SUGGESTIONS:
//
// 1. Error Handling Specificity: Multiple catch blocks catch generic Exception and show
//    non-specific messages (e.g., "No Server", "Need valid URL"). Consider:
//    - Distinguishing network errors from auth errors
//    - Providing actionable error messages to users
//    - Logging errors for debugging while showing user-friendly messages
//
// 2. User Display Logic Extraction: The logic for extracting the user display name in
//    `profileMenuButton` using email/phone/username fields is complex. Consider extracting this
//    to a utility function in the auth module that could be reused elsewhere.
//
// 3. Testing Considerations: This file is a UI composition entry point with heavy
//    dependency on KiteUI ViewWriter and reactive contexts. Unit testing requires:
//    - Mock ViewWriter infrastructure (not currently available in test utils)
//    - Mock PageNavigator
//    - Browser environment (JS-only module)
//    Consider integration/E2E tests for this file, or extract testable business logic
//    (like user display name extraction) into separate pure functions.
//
// 4. Navigation Rebuild Performance: The entire menu item list rebuilds when permissions
//    or schema change. For large schemas, consider caching or diffing to avoid unnecessary
//    UI rebuilds.
//
// 5. JsJodaTimeZoneModule Reference: The variable `x` in `app` is intentionally unused
//    to force module initialization. Consider adding a suppress annotation for clarity:
//    @Suppress("UNUSED_VARIABLE")

import com.lightningkite.kiteui.EvolvingAppearance
import com.lightningkite.kiteui.ExperimentalKiteUi
import com.lightningkite.kiteui.auth.authComponent
import com.lightningkite.kiteui.exceptions.installLsError
import com.lightningkite.kiteui.forms.displayName
import com.lightningkite.kiteui.forms.titleCase
import com.lightningkite.kiteui.models.*
import com.lightningkite.kiteui.navigation.PageNavigator
import com.lightningkite.kiteui.navigation.pageNavigator
import com.lightningkite.kiteui.views.ViewWriter
import com.lightningkite.kiteui.views.card
import com.lightningkite.kiteui.views.centered
import com.lightningkite.kiteui.views.compact
import com.lightningkite.kiteui.views.direct.*
import com.lightningkite.kiteui.views.direct.icon
import com.lightningkite.kiteui.views.expanding
import com.lightningkite.kiteui.views.l2.*
import com.lightningkite.kiteui.views.themed
import com.lightningkite.lightningserver.LSError
import com.lightningkite.lightningserver.sessions.proofs.LiveAuthClientEndpoints
import com.lightningkite.reactive.context.ReactiveContext
import com.lightningkite.reactive.context.invoke
import com.lightningkite.reactive.context.reactive
import com.lightningkite.reactive.core.remember
import com.lightningkite.reactive.core.rememberSuspending
import com.lightningkite.reactive.extensions.debounceWrite
import com.lightningkite.reactive.extensions.modify
import com.lightningkite.services.data.Cents
import com.lightningkite.services.database.Condition
import com.lightningkite.services.database.SerializableAnnotationValue
import com.lightningkite.services.database.SerializableProperty
import com.lightningkite.services.database.SerializationRegistry
import com.lightningkite.services.database.serializableAnnotations
import com.lightningkite.services.database.serializableProperties
import com.lightningkite.services.files.DirectServerFileSerializer
import com.lightningkite.services.files.ServerFile
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

private const val APP_NAME = "Lightning Server Admin"

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
 *
 * Note: This function performs reactive setup and will rebuild navigation when server schema changes.
 */
fun ViewWriter.app(navigator: PageNavigator) {
    // Force initialization of the timezone module to ensure timezone support is available
    // This reference is intentional - the module must be loaded even if not directly used
    val x = JsJodaTimeZoneModule

    // Register core serializers for Lightning Server error handling and file uploads
    // These must be registered globally to enable proper deserialization across the app
    SerializationRegistry.master.register(Cents.serializer())
    SerializationRegistry.master.register(LSError.serializer())
    SerializationRegistry.master.register(ServerFile.serializer())
//    SerializationRegistry.master.register(ServerFileWithMetadata.serializer())
    SerializationRegistry.master.register(ServerFile.serializer(), "kotlinx.serialization.ContextualSerializer<ServerFile>")
    SerializationRegistry.master.register(ServerFile.serializer(), "com.lightningkite.services.files.ServerFile/DeferToContextualServerFileSerializer")
    SerializationRegistry.master.register(DirectServerFileSerializer)  //backwards compat

    // Install custom error message handlers for Lightning Server errors
    // Provides user-friendly error messages for common Lightning Server API errors
    context.exceptionHandlers.installLsError()

    appBase(navigator) {
        // A console with many grouped destinations is exactly the shape navSidebar is for, and it
        // covers the whole width range on its own: labeled rail, compact rail, then a drawer.

        val expandedBreakpoint = 60.rem
        val compactBreakpoint = 40.rem

        val nav = Nav(this,
            appLogo = Icon.settings.toImageSource(Color.white),
            appName = APP_NAME,
            showNav = { true },
            menuItems = { collectionMenuItems() },
            footerItems = { listOf() },
            actionItems = { listOf(profileNavItem) },
        )

        themed(OuterSemantic).col {
            debugName = "nav-sidebar"
            nav.appBar(this, menuButtonFor = MediaQuery.MaxWidth(compactBreakpoint))
            expanding.themed(OuterSemantic).frame {
                applySafeInsets(top = false)
                debugName = "nav and content"
                themed(OuterSemantic).row {
                    shownForQuery(MediaQuery.MinWidth(expandedBreakpoint)).frame {
                        nav.rail(this, labeled = true, width = 16.rem)
                    }
                    shownForQuery(
                        MediaQuery.And(
                            setOf(
                                MediaQuery.MinWidth(compactBreakpoint),
                                MediaQuery.MaxWidth(expandedBreakpoint),
                            )
                        )
                    ).frame {
                        nav.rail(this, labeled = false, width = 5.5.rem)
                    }
                    expanding.navigatorView(context.pageNavigator)
                }
                nav.drawer(this, closeAbove = compactBreakpoint)
            }
            frame { applySafeInsets(top = false) }
        }
    }
}

/**
 * The admin panel's destinations, derived from the server schema and the signed-in user's
 * permissions. Reactive: it rebuilds when either changes.
 */
private fun ReactiveContext.collectionMenuItems(): List<Nav.Item> {
    val permissions = loadedPermissions()
    return buildList {
        add(Nav.Link(title = "Home", icon = Icon.home) { HomePage() })

        // Optional endpoints screen (enabled via admin settings)
        // Useful for debugging and API exploration
        if (adminSettings().showEndpoints) {
            add(Nav.Link(title = "Endpoints", icon = Icon.menu) { EndpointsPage() })
            add(Nav.Link(title = "Renderer Gallery", icon = Icon.list) { com.lightningkite.lightningserver.admin.tests.RendererGalleryScreen() })
        }

        // Auto-generate navigation items for each collection in the schema
        // Sorted alphabetically by display name for consistent ordering
        add(
            Nav.Group(
                title = "Collections",
                icon = Icon.list,
                children = adminServer().models.entries.groupBy {
                    it.value.serializer.serializableAnnotations
                        .find { it.fqn == "com.lightningkite.services.data.Group" }
                        ?.values
                        ?.get("name")
                        ?.let { it as? SerializableAnnotationValue.StringValue }
                        ?.value
                        ?: "Other"
                }.entries.sortedBy { it.key }.map {
                    Nav.Group(
                        title = it.key,
                        icon = Icon.menu,
                        children = it.value.mapNotNull {
                            // Only show collections where the user has read permission
                            // Condition.Never means no read access at all
                            if (permissions[it.key]?.read != Condition.Never) {
                                Nav.Link(
                                    // Use docGroup if available (for organizing related collections),
                                    // otherwise fall back to serializer display name
                                    title = it.value.docGroup?.split('.')?.joinToString(" / ") {
                                        it.removeSuffix("Api").removeSuffix("RestEndpoints").titleCase()
                                    } ?: it.value.serializer.displayName,
                                    icon = Icon.list
                                ) { CollectionAdminPage(it.key) }
                            } else null
                        }
                    )
                }
            )
        )
    }
}

/**
 * The profile menu in the app bar: who you are signed in as, which server the panel talks to, and
 * the controls for authenticating against it.
 *
 * A [Nav.Custom] because none of the other item kinds can hold a popover full of form fields.
 * `narrow` is left to default to `wide` - the button is already icon-sized either way.
 */
private val profileNavItem = Nav.Custom(
    title = "Profile",
    icon = Icon.person,
    wide = { profileMenuButton() },
)

private fun ViewWriter.profileMenuButton() {
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
                sizeConstraints(width = SETTINGS_FIELD_WIDTH).field("Token") {
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
                sizeConstraints(width = SETTINGS_FIELD_WIDTH).frame {
                    reactive {
                        clearChildren()
                        try {
                            authComponent(
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
                shownWhen { adminCredentials() != null }.card.button {
                    centered.row {
                        centered.icon(Icon.logout, "Log Out")
                        centered.text("Log Out")
                    }
                    onClick("Log Out") {
                        context.confirmDanger("Log Out", "Are you sure you want to log out?", "Log Out") {
                            // TODO: Preserve current navigation state instead of always resetting to HomePage
                            context.pageNavigator.reset(HomePage())
                            adminCredentials.value = null
                        }
                    }
                }
            }
        }
    }
}
