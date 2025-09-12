package com.lightningkite.lightningserver.admin

import com.lightningkite.kiteui.auth.authComponent2
import com.lightningkite.kiteui.exceptions.ExceptionToMessages
import com.lightningkite.kiteui.exceptions.installLsError
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
import com.lightningkite.services.database.Condition
import com.lightningkite.lightningserver.auth.AuthenticatedUserAuthClientEndpointsLive
import com.lightningkite.reactive.context.invoke
import com.lightningkite.reactive.context.reactive
import com.lightningkite.reactive.core.remember
import com.lightningkite.reactive.core.rememberSuspending
import com.lightningkite.reactive.extensions.debounceWrite
import com.lightningkite.services.database.ClientModule
import com.lightningkite.services.database.SerializableProperty
import com.lightningkite.services.database.serializableProperties
import com.lightningkite.titleCase
import kotlin.time.Duration.Companion.milliseconds

@JsModule("@js-joda/timezone")
@JsNonModule
external object JsJodaTimeZoneModule

fun ViewWriter.app(navigator: PageNavigator, dialog: PageNavigator) {
    val x = JsJodaTimeZoneModule
    com.lightningkite.prepareModelsShared()
    prepareModelsAdmin()
    DefaultSerializersModule = ClientModule
//    rootTheme = { appTheme() }
    ExceptionToMessages.root.installLsError()
    appNavFactory.value = ViewWriter::appNavTopAndLeft
    appNav(navigator, dialog) {
        appName = "KiteUI Sample App"
        ::navItems {
            val permissions = loadedPermissions()
            try {
                buildList {
                    add(NavLink("Home", icon = Icon.home) { HomePage() })
                    if (adminSettings().showEndpoints) {
                        add(NavLink("Endpoints", icon = Icon.menu) { EndpointsPage() })
                    }
                    adminServer().models.entries.sortedBy { it.value.serializer.displayName }.forEach {
                        if(permissions[it.key]?.read != Condition.Never) {
                            add(
                                NavLink(
                                    it.value.docGroup?.titleCase() ?: it.value.serializer.displayName,
//                                    it.value.serializer.displayName,
                                    icon = Icon.list
                                ) { CollectionAdminPage(it.key) }
                            )
                        }
                    }
                }
            } catch (e: Exception) {
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
                            val me = rememberSuspending label@{
                                try {
                                    val creds = adminAuthentication() ?: return@label "Anonymous"
                                    val sub = adminServer().auth.authenticatedSubjects[adminCredentials()?.userType
                                        ?: return@label "Anonymous"]?.invoke(creds)
                                        ?: return@label "Anonymous"
                                    val serializer =
                                        (sub as AuthenticatedUserAuthClientEndpointsLive<*, *>).userSerializer
                                    val self = sub.getSelf()
                                    serializer.serializableProperties
                                        ?.find { it.name == "email" || it.name == "phone" || it.name == "username" }
                                        ?.let { it as SerializableProperty<Any, Any?> }
                                        ?.get(self)
                                        ?.toString()
                                        ?: self.toString().take(40)
                                } catch (e: Exception) {
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
                                    sizeConstraints(width = 20.rem) - field("Server") {
                                        textInput {
                                            content bind serverUrl.debounceWrite(500.milliseconds)
                                        }
                                    }
                                    val userType = adminCredentials.lens(
                                        get = { it?.userType },
                                        modify = { o, v -> o?.copy(userType = v) ?: AdminCredentials(userType = v) }
                                    )
                                    sizeConstraints(width = 20.rem) - field("User Type") {
                                        select {
                                            bind(
                                                userType,
                                                remember {
                                                    listOf(null) + try {
                                                        adminServer().auth.subjects.keys.toList()
                                                    } catch (e: Exception) {
                                                        listOf()
                                                    }
                                                },
                                                { it ?: "None" }
                                            )
                                        }
                                    }
                                    sizeConstraints(width = 20.rem) - field("Token") {
                                        textInput {
                                            content bind adminCredentials.lens(
                                                get = { it?.session ?: "" },
                                                modify = { o, v ->
                                                    o?.copy(session = v.takeUnless { it.isBlank() })
                                                        ?: AdminCredentials(session = v.takeUnless { it.isBlank() })
                                                }
                                            )
                                        }
                                    }
                                    sizeConstraints(width = 20.rem) - stack {
                                        reactive {
                                            clearChildren()
                                            try {
                                                authComponent2(adminServer().auth, userType() ?: return@reactive) { v ->
                                                    adminCredentials.value =
                                                        adminCredentials.value?.copy(session = v) ?: AdminCredentials(
                                                            session = v
                                                        )
                                                }
                                            } catch (e: Exception) {
                                                text("Need valid URL")
                                            }
                                        }
                                    }
                                    onlyWhen { adminCredentials() != null } - card - button {
                                        centered - row {
                                            centered - icon(Icon.logout, "Log Out")
                                            centered - text("Log Out")
                                        }
                                        onClick("Log Out") {
                                            confirmDanger("Log Out", "Are you sure you want to log out?", "Log Out") {
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

//        ::exists {
//            navigator.currentPage.await() !is UseFullPage
//        }
    }
}
