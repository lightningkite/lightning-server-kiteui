// Live status of every configured subsystem.
//
// Lightning Server's MetaEndpoints already walks each setting and calls healthCheck() on it, so
// this screen is just a rendering of /meta/health -- no bespoke server code. It is the fastest
// way to see what the current settings.json actually resolved to, and which implementation
// answered. Ported from the kitchen-sink demo's HealthDemoPage.
package com.lightningkite.lskiteuistarter

import com.lightningkite.kiteui.Routable
import com.lightningkite.kiteui.navigation.Page
import com.lightningkite.kiteui.reactive.Action
import com.lightningkite.kiteui.views.*
import com.lightningkite.kiteui.views.direct.*
import com.lightningkite.lskiteuistarter.sdk.currentSessionNotNull
import com.lightningkite.reactive.context.invoke
import com.lightningkite.reactive.core.Constant
import com.lightningkite.reactive.core.Reactive
import com.lightningkite.reactive.core.Signal
import com.lightningkite.reactive.core.remember
import com.lightningkite.reactive.core.rememberSuspending
import com.lightningkite.services.data.HealthStatus

/** One row of the health table: the setting name plus whatever its service reported. */
private data class SubsystemRow(val name: String, val status: HealthStatus)

@Routable("/health")
class HealthPage : Page {
    override val title: Reactive<String> get() = Constant("Subsystem Health")

    override fun ElementWriter.CanAddTheme.render() {
        val session = currentSessionNotNull

        // Bumping this re-runs the query below; the server caches each check for its own
        // configured frequency, so a refresh is cheap and mostly reports the cached verdict.
        val refreshes = Signal(0)
        val health = rememberSuspending {
            refreshes()
            session().api.meta.getServerHealth()
        }
        val subsystems = remember {
            health().features.entries.sortedBy { it.key }.map { SubsystemRow(it.key, it.value) }
        }

        col {
            row {
                expanding.col { h2("Subsystem Health") }
                button {
                    centered.text("Refresh")
                    action = Action("Refresh") { refreshes.value++ }
                }
            }
            subtext("Every setting the server could health-check, and which implementation answered. This is /meta/health, straight from Lightning Server.")

            card.col {
                text { ::content { "Overall: ${health().overall}" } }
                subtext { ::content { "Server ${health().serverId}, ${health().features.size} subsystems checked" } }
            }

            expanding.scrolling.colOf(subsystems, id = { it.name }) { row ->
                card.row {
                    expanding.col {
                        text { ::content { row().name } }
                        subtext { ::content { row().status.additionalMessage ?: "" } }
                    }
                    col {
                        // The level decides both the wording and the theme, so it has to be read
                        // inside a reactive binding rather than branched on at render time.
                        text { ::content { row().status.level.name } }
                    }
                }
            }
        }
    }
}
