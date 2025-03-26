package com.lightningkite.lightningserver.admin

import com.lightningkite.kiteui.HttpMethod
import com.lightningkite.kiteui.Routable
import com.lightningkite.kiteui.forms.FormModule
import com.lightningkite.kiteui.forms.form
import com.lightningkite.kiteui.forms.view
import com.lightningkite.kiteui.models.*
import com.lightningkite.kiteui.navigation.Page
import com.lightningkite.readable.Property
import com.lightningkite.readable.*
import com.lightningkite.kiteui.views.ViewWriter
import com.lightningkite.kiteui.views.*
import com.lightningkite.kiteui.views.direct.*
import com.lightningkite.lightningserver.serverhealth.HealthStatus
import com.lightningkite.lightningserver.serverhealth.ServerHealth
import com.lightningkite.now
import com.lightningkite.serialization.lensPath
import kotlinx.serialization.builtins.nullable
import kotlinx.serialization.builtins.serializer

@Routable("/")
class HomePage : Page {
    override fun ViewWriter.render() = stack {
        gravity(Align.Center, Align.Stretch) - sizeConstraints(width = 40.rem) - scrolls - col {
            h1 {
                ::content { adminServer.invoke().schema.baseUrl.substringAfter("://") }
            }
            card - col {
                h2("Settings")
                row {
                    expanding - centered - text("Show Endpoints")
                    centered - switch {
                        checked bind adminSettings.lens(
                            get = { it.showEndpoints },
                            modify = { o, it -> o.copy(showEndpoints = it) }
                        )
                    }
                }
                row {
                    expanding - centered - text("Show Hidden Fields")
                    centered - switch {
                        checked bind adminSettings.lens(
                            get = { it.showHiddenFields },
                            modify = { o, it -> o.copy(showHiddenFields = it) }
                        )
                    }
                }
                row {
                    expanding - centered - text("Edit Unrecommended Fields")
                    centered - switch {
                        checked bind adminSettings.lens(
                            get = { it.editAllFields },
                            modify = { o, it -> o.copy(editAllFields = it) }
                        )
                    }
                }
                row {
                    expanding - centered - text("Show View Type Switcher")
                    centered - switch {
                        checked bind adminSettings.lens(
                            get = { it.showAlternativeEditOptions },
                            modify = { o, it -> o.copy(showAlternativeEditOptions = it) }
                        )
                    }
                }
                row {
                    expanding - centered - text("Enable destructive actions")
                    centered - switch {
                        checked bind unlockDestructiveActions
                            .withWrite {
                                if (it) adminSettings.value = adminSettings.value.copy(unlockDestructiveActions = now())
                            }
                    }
                }
            }
            card - col {
                h2("Server Status")
                val status = asyncReadable {
                    val endpoint = adminServer().health ?: return@asyncReadable null
                    val fetcher = adminServer().fetcher(adminAuthentication() ?: return@asyncReadable null)
                    val health = fetcher(endpoint.path, HttpMethod.GET, Unit.serializer(), Unit, ServerHealth.serializer())
                    health
                }
                row {
                    expanding - card - text {
                        ::content {
                            "CPU: ${status()?.loadAverageCpu?.toString() ?: "-" }%"
                        }
                    }
                    expanding - card - text {
                        ::content {
                            "Memory: ${status()?.memory?.usage?.toString() ?: "-" }%"
                        }
                    }
                }
                col {
                    forEach(shared { status()?.features?.entries?.sortedBy { it.key } ?: listOf() }) {
                        row {
                            dynamicTheme {
                                when(it.value.level) {
                                    HealthStatus.Level.OK -> CardSemantic
                                    HealthStatus.Level.WARNING -> WarningSemantic
                                    HealthStatus.Level.URGENT -> DangerSemantic
                                    HealthStatus.Level.ERROR -> ErrorSemantic
                                }
                            }
                            expanding - text { ::content { it.key } }
                            text { ::content { it.value.additionalMessage ?: it.value.level.name } }
                        }
                    }
                }
            }
        }
    }
}