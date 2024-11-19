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
import com.lightningkite.lightningserver.auth.AuthenticatedUserAuthClientEndpoints
import com.lightningkite.lightningserver.schema.*
import com.lightningkite.serialization.ClientModule
import com.lightningkite.serialization.SerializableProperty
import com.lightningkite.serialization.serializableProperties
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
            try {
                listOf(
                    NavLink("Endpoints", icon = Icon.menu) { EndpointsScreen() }
                ) + adminServer().models.entries.sortedBy { it.value.serializer.displayName }.map {
                    NavLink(it.value.serializer.displayName, icon = Icon.list) { CollectionAdminScreen(it.key) }
                }
            } catch(e: Exception) {
                listOf()
            }
        }

        actions = run {
            listOf(
                NavCustom(
                    title = { "Profile" },
                    icon = { Icon.person },
                    count = null,
                    hidden = { false },
                    square = {
                        compact - menuButton {
                            val me = sharedSuspending label@{
                                try {
                                    val creds = adminAuthentication() ?: return@label "Anonymous"
                                    val sub = adminServer().auth.authenticatedSubjects[adminCredentials()?.userType ?: return@label "Anonymous"]?.invoke(creds)
                                        ?: return@label "Anonymous"
                                    val serializer = (sub as AuthenticatedUserAuthClientEndpoints.StandardImpl<*, *>).userSerializer
                                    val self = sub.getSelf()
                                    serializer.serializableProperties
                                        ?.find { it.name == "email" || it.name == "phone" || it.name == "username" }
                                        ?.let { it as SerializableProperty<Any, Any?> }
                                        ?.get(self)
                                        ?.toString()
                                        ?: self.toString().take(40)
                                } catch(e: Exception) {
                                    "No Server"
                                }
                            }
                            col {
                                centered - icon(Icon.person, "Login")
                                centered - subtext {
                                    ::content { me().take(10) }
                                }
                            }
                            preferredDirection = PopoverPreferredDirection.belowLeft
                            requireClick = true
                            opensMenu {
                                col {
                                    centered - subtext {
                                        ::content { me() }
                                    }
                                    sizeConstraints(20.rem) - field("Server") {
                                        textInput {
                                            content bind serverUrl.debounceWrite(500.milliseconds)
                                        }
                                    }
                                    val userType = adminCredentials.lens(
                                        get = { it?.userType },
                                        modify = { o, v -> o?.copy(userType = v) ?: AdminCredentials(userType = v) }
                                    )
                                    sizeConstraints(20.rem) - field("User Type") {
                                        select {
                                            bind(
                                                userType,
                                                shared { listOf(null) + try {
                                                    adminServer().auth.subjects.keys.toList()
                                                } catch(e: Exception) { listOf() } },
                                                { it ?: "None" }
                                            )
                                        }
                                    }
                                    sizeConstraints(20.rem) - field("Token") {
                                        textInput {
                                            content bind adminCredentials.lens(
                                                get = { it?.session ?: "" },
                                                modify = { o, v -> o?.copy(session = v.takeUnless { it.isBlank() }) ?: AdminCredentials(session = v.takeUnless { it.isBlank() }) }
                                            )
                                        }
                                    }
                                    sizeConstraints(20.rem) - stack {
                                        reactive {
                                            clearChildren()
                                            try {
                                                login(adminServer().auth, userType() ?: return@reactive) { v ->
                                                    adminCredentials.value = adminCredentials.value?.copy(session = v) ?: AdminCredentials(session = v)
                                                }
                                            } catch(e: Exception) {
                                                text("Need valid URL")
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

//        ::exists {
//            navigator.currentScreen.await() !is UseFullScreen
//        }
    }
}
