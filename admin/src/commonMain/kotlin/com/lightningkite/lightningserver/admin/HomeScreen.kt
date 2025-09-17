package com.lightningkite.lightningserver.admin

import com.lightningkite.kiteui.Routable
import com.lightningkite.kiteui.models.*
import com.lightningkite.kiteui.navigation.Page
import com.lightningkite.kiteui.views.*
import com.lightningkite.kiteui.views.direct.*
import com.lightningkite.lightningserver.networking.LsHttpMethod
import com.lightningkite.lightningserver.typed.ServerHealth
import com.lightningkite.reactive.context.invoke
import com.lightningkite.reactive.core.remember
import com.lightningkite.reactive.extensions.asyncReactive
import com.lightningkite.reactive.extensions.withWrite
import com.lightningkite.services.HealthStatus
import kotlinx.serialization.builtins.serializer
import kotlin.time.Clock.System.now

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
                row {
                    expanding - centered - text("Live Data")
                    centered - switch {
                        checked bind adminSettings.lens(
                            get = { it.liveData },
                            modify = { o, it -> o.copy(liveData = it) }
                        )
                    }
                }
            }
//            card - col {
//                text("Theme")
//                scrollingHorizontally - row {
//                    expanding - space()
//                    for (type in ThemePreference.entries) {
//                        ThemeDerivation.Set(type.theme(null)).onNext - centered - card - button {
//                            text(type.display)
//                            onClick { themePreference.value = type }
//                        }
//                    }
//                    expanding - space()
//                }
//                scrollingHorizontally - row {
//                    expanding - space()
//                    val colorOptions = (0..360 step 45).map {
//                        HSPColor(hue = it.degrees, saturation = 0.8f, brightness = 0.7f, alpha = 1f).toRGB()
//                    } + Color.white + Color.black + Color.gray(0.5f)
//                    card - button {
//                        text("Default")
//                        onClick { themePreferenceColor.value = null }
//                    }
//                    for (color in colorOptions) {
//                        ThemeDerivation(
//                            Theme(id = "color-${color.toInt()}", background = color, foreground = Color.white)
//                        ).onNext - button {
//                            onClick {
//                                themePreferenceColor.value = color
//                            }
//                        }
//                    }
//                    expanding - space()
//                }
//            }
            card - col {
                h2("Server Status")
                val status = asyncReactive {
                    val endpoint = adminServer().health ?: return@asyncReactive null
                    val fetcher = adminServer().fetcher(adminAuthentication() ?: return@asyncReactive null)
                    val health = fetcher(endpoint.path, LsHttpMethod.GET, Unit.serializer(), Unit, ServerHealth.serializer())
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
                    forEach(remember { status()?.features?.entries?.sortedBy { it.key } ?: listOf() }) {
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