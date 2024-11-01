package com.lightningkite.lightningserver.admin

import com.lightningkite.*
import com.lightningkite.kiteui.exceptions.ExceptionToMessages
import com.lightningkite.kiteui.exceptions.installLsError
import com.lightningkite.kiteui.forms.displayName
import com.lightningkite.kiteui.forms.login
import com.lightningkite.kiteui.models.*
import com.lightningkite.kiteui.navigation.*
import com.lightningkite.kiteui.reactive.*
import com.lightningkite.kiteui.views.*
import com.lightningkite.kiteui.views.direct.*
import com.lightningkite.kiteui.views.l2.*
import com.lightningkite.lightningdb.*
import com.lightningkite.lightningserver.schema.*
import com.lightningkite.serialization.ClientModule
import kotlin.time.Duration.Companion.milliseconds

//val defaultTheme = brandBasedExperimental("bsa", normalBack = Color.white)
val defaultTheme = Theme.flat("default", Angle(0.55f))
val appTheme = Property<Theme>(defaultTheme)

fun ViewWriter.app(navigator: ScreenNavigator, dialog: ScreenNavigator) {
    com.lightningkite.prepareModelsShared()
    prepareModelsAdmin()
    DefaultSerializersModule = ClientModule
//    rootTheme = { appTheme() }
    ExceptionToMessages.root.installLsError()
    appNavFactory.value = ViewWriter::appNavTopAndLeft
    appNav(navigator, dialog) {
        appName = "KiteUI Sample App"
        ::navItems {
            listOf(
                NavLink("Endpoints", icon = Icon.menu) { EndpointsScreen() }
            ) + adminServer().models.entries.sortedBy { it.value.serializer.displayName }.map {
                NavLink(it.value.serializer.displayName, icon = Icon.list) { CollectionAdminScreen(it.key) }
            }
        }

        ::actions {
            listOf(
                NavCustom(
                    title = { "Profile" },
                    icon = { Icon.person },
                    count = null,
                    hidden = { false },
                    square = {
                        compact - menuButton {
                            col {
                                centered - icon(Icon.person, "Login")
                                centered - subtext {
                                    ::content { "My Dude" }
                                }
                            }
                            preferredDirection = PopoverPreferredDirection.belowLeft
                            requireClick = true
                            opensMenu {
                                col {
                                    sizeConstraints(20.rem) - field("Server") {
                                        textInput {
                                            content bind serverUrl.debounceWrite(500.milliseconds)
                                        }
                                        reactive { serverSchema() }
                                    }
                                    sizeConstraints(20.rem) - field("User Type") {
                                        select {
                                            bind(
                                                adminCredentials.lens(
                                                    get = { it?.userType },
                                                    modify = { o, v -> o?.copy(userType = v) ?: AdminCredentials(userType = v) }
                                                ),
                                                shared { listOf(null) + adminServer().auth.subjects.keys.toList() },
                                                { it ?: "None" }
                                            )
                                        }
                                    }
                                    sizeConstraints(20.rem) - field("Token") {
                                        textInput {
                                            content bind adminCredentials.lens(
                                                get = { it?.session ?: "" },
                                                modify = { o, v -> o?.copy(session = v) ?: AdminCredentials(session = v) }
                                            )
                                        }
                                    }
                                    sizeConstraints(20.rem) - stack {
                                        reactive {
                                            clearChildren()
                                            login(adminServer().auth) { v ->
                                                adminCredentials.value = adminCredentials.value?.copy(session = v) ?: AdminCredentials(session = v)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                ))
        }

//        ::exists {
//            navigator.currentScreen.await() !is UseFullScreen
//        }
    }
}
