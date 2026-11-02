//
// CODE REVIEW SUMMARY
// ===================
// HomePage is the main landing page for the admin panel, displaying:
// - Server URL in the header
// - Admin settings toggles (show endpoints, hidden fields, edit all, etc.)
// - Server health status (CPU, memory, feature health)
//
// IMPROVEMENT SUGGESTIONS:
//
// 1. LARGE COMMENTED BLOCK (Lines 84-116): Contains unused theme preference UI code.
//    Either implement this feature or remove the dead code.
//
// 2. NULL SAFETY (Line 121): The early return when adminAuthentication() is null means
//    the health status section won't work for unauthenticated users. Consider showing
//    an appropriate message instead of silently returning null.
//
// 3. MAGIC NUMBER (Line 22): The width constraint `40.rem` is hardcoded. Consider
//    extracting to a constant like `SETTINGS_PANEL_WIDTH` for consistency.
//
// 4. MISSING LOADING STATE (Lines 119-125): When the health status is loading,
//    there's no visual indicator. Consider adding a loading spinner.
//
// 5. ERROR HANDLING: If the health endpoint call fails, the status will be null.
//    Consider catching exceptions and showing an error message.
//
// 6. ACCESSIBILITY: The settings toggles lack accessible labels/descriptions.
//    Screen readers may not understand the purpose of each toggle.
//
// 7. MISSING KDOC: The HomePage class and render function lack documentation
//    explaining the page's purpose and structure.
//
package com.lightningkite.lightningserver.admin

import com.lightningkite.kiteui.Routable
import com.lightningkite.kiteui.models.*
import com.lightningkite.kiteui.navigation.Page
import com.lightningkite.kiteui.views.*
import com.lightningkite.kiteui.views.direct.*
import com.lightningkite.lightningserver.networking.LsHttpMethod
import com.lightningkite.lightningserver.typed.ServerHealth
import com.lightningkite.reactive.context.ReactiveContext
import com.lightningkite.reactive.context.invoke
import com.lightningkite.reactive.core.MutableReactive
import com.lightningkite.reactive.core.remember
import com.lightningkite.reactive.extensions.asyncReactive
import com.lightningkite.reactive.extensions.withWrite
import com.lightningkite.services.data.HealthStatus
import kotlinx.serialization.builtins.serializer
import kotlin.math.roundToInt
import kotlin.time.Clock.System.now
import kotlin.time.Duration.Companion.minutes

@Routable("/")
class HomePage : Page {
    override fun ElementWriter.CanAddTheme.render() {
        frame {
            gravity(Align.Center, Align.Stretch).sizeConstraints(width = 40.rem).scrolls.col {
                h1 {
                    ::content { adminServer.invoke().schema.baseUrl.substringAfter("://") }
                }
                subtext { ::content { "${environmentFor(serverUrl()).label} server" } }
                shownWhen { adminAuthentication() == null }.card.col {
                    h3("You're not signed in")
                    text("Sign in from the Account menu at the top right to see this server's collections and status.")
                }
                card.col {
                    h2("Settings")
                    settingRow(
                        "Enable destructive actions",
                        {
                            val until = adminSettings().unlockDestructiveActions?.plus(30.minutes)
                            if (unlockDestructiveActions() && until != null)
                                "Bulk delete and import are unlocked for ${(until - nowByMinute()).inWholeMinutes.coerceAtLeast(1)} more minutes."
                            else "Unlocks bulk delete and import for 30 minutes."
                        },
                        unlockDestructiveActions.withWrite {
                            adminSettings.value =
                                adminSettings.value.copy(unlockDestructiveActions = if (it) now() else null)
                        }
                    )
                    settingRow(
                        "Live Data",
                        { "Lets pages switch to live updates over the server's update socket." },
                        adminSettings.lens(get = { it.liveData }, modify = { o, it -> o.copy(liveData = it) })
                    )
                    separator()
                    subtext("Forms")
                    settingRow(
                        "Show Hidden Fields",
                        { "Shows fields the server marks as hidden." },
                        adminSettings.lens(get = { it.showHiddenFields }, modify = { o, it -> o.copy(showHiddenFields = it) })
                    )
                    settingRow(
                        "Edit Unrecommended Fields",
                        { "Lets you edit fields that are normally read-only, such as IDs and denormalized values." },
                        adminSettings.lens(get = { it.editAllFields }, modify = { o, it -> o.copy(editAllFields = it) })
                    )
                    settingRow(
                        "Show View Type Switcher",
                        { "Lets you choose a different editor for each field." },
                        adminSettings.lens(get = { it.showAlternativeEditOptions }, modify = { o, it -> o.copy(showAlternativeEditOptions = it) })
                    )
                    separator()
                    subtext("Developer")
                    settingRow(
                        "Show Endpoints",
                        { "Adds the API endpoint explorer to the navigation." },
                        adminSettings.lens(get = { it.showEndpoints }, modify = { o, it -> o.copy(showEndpoints = it) })
                    )
                }
                shownWhen { adminAuthentication() != null }.card.col {
                    h2("Server Status")
                    val status = asyncReactive {
                        val endpoint = adminServer().health ?: return@asyncReactive null
                        val fetcher = adminServer().fetcher(adminAuthentication() ?: return@asyncReactive null)
                        val health =
                            fetcher(endpoint.path, LsHttpMethod.GET, Unit.serializer(), Unit, ServerHealth.serializer())
                        health
                    }
                    subtext {
                        ::content {
                            val features = status()?.features?.values
                            when {
                                adminServer().health == null -> "This server doesn't report its health."
                                features == null -> "Checking..."
                                features.all { it.level == HealthStatus.Level.OK } -> "All ${features.size} checks OK"
                                else -> "${features.count { it.level != HealthStatus.Level.OK }} of ${features.size} checks need attention"
                            }
                        }
                    }
                    row {
                        expanding.card.text {
                            ::content {
                                "CPU: ${status()?.loadAverageCpu?.let { (it * 100).roundToInt() } ?: "-"}%"
                            }
                        }
                        expanding.card.text {
                            ::content {
                                "Memory: ${status()?.memory?.usage?.let { (it * 100).roundToInt() } ?: "-"}%"
                            }
                        }
                    }
                    col {
                        forEach(remember { status()?.features?.entries?.sortedBy { it.key } ?: listOf() }) {
                            row {
                                dynamicTheme {
                                    when (it.value.level) {
                                        HealthStatus.Level.OK -> CardSemantic
                                        HealthStatus.Level.WARNING -> WarningSemantic
                                        HealthStatus.Level.URGENT -> DangerSemantic
                                        HealthStatus.Level.ERROR -> ErrorSemantic
                                    }
                                }
                                expanding.text { ::content { it.key } }
                                text { ::content { it.value.additionalMessage ?: it.value.level.name } }
                            }
                        }
                    }
                }

            }
        }
    }

    /** A switch with its name and, beneath it, what it does. */
    private fun ViewWriter.settingRow(title: String, description: ReactiveContext.() -> String, checked: MutableReactive<Boolean>) {
        row {
            expanding.col {
                gap = 0.rem
                text(title)
                subtext { ::content { description() } }
            }
            centered.switch { this.checked bind checked }
        }
    }
}
