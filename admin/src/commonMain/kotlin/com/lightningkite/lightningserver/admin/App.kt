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

@JsModule("@js-joda/timezone")
@JsNonModule
external object JsJodaTimeZoneModule

fun ViewWriter.app(navigator: PageNavigator, dialog: PageNavigator) {
    val x = JsJodaTimeZoneModule
    SerializationRegistry.master.register(LSError.serializer())
    SerializationRegistry.master.register(ServerFile.serializer())
//    rootTheme = { appTheme() }
    ExceptionToMessages.root.installLsError()
    appNavFactory.value = ViewWriter::appNavTopAndLeft
    println("BOOT")
    appNav(navigator, dialog) {
        appName = "KiteUI Sample App"
        ::navItems {
            val permissions = loadedPermissions()
            buildList {
                add(NavLink("Home", icon = Icon.home) { HomePage() })
                if (adminSettings().showEndpoints) {
                    add(NavLink("Endpoints", icon = Icon.menu) { EndpointsPage() })
                }
                println("BUILDING NAV OK")
                adminServer().models.entries.sortedBy { it.value.serializer.displayName }.forEach {
                    if(permissions[it.key]?.read != Condition.Never) {
                        add(
                            NavLink(
                                it.value.docGroup?.titleCase() ?: it.value.serializer.displayName,
//                                    it.value.serializer.displayName,
                                icon = Icon.list
                            ) { CollectionAdminPage(it.key) }
                        )
                    } else {
                        println("Skipping ${it.key} because it has no read permission.")
                    }
                }
                println("nav complete")
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
                                    val sub = adminServer().authEndpoints(creds)
                                        .subjects[adminCredentials()?.userType ?: return@label "Anonymous"]
                                        ?: return@label "Anonymous"

                                    val serializer = (sub as LiveAuthClientEndpoints<*, *>).subjectSerializer

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
                                                        adminServer().authEndpoints(null).subjects.keys.toList()
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
                                                    val session = v.takeUnless { it.isBlank() }
                                                    o?.copy(session = session) ?: AdminCredentials(session = session)
                                                }
                                            )
                                        }
                                    }
                                    sizeConstraints(width = 20.rem) - stack {
                                        reactive {
                                            clearChildren()
                                            try {
                                                authComponent2(
                                                    adminServer().authEndpoints(null),
                                                    userType() ?: return@reactive
                                                ) { v ->
                                                    adminCredentials.modify {
                                                        it?.copy(
                                                            session = v
                                                        ) ?: AdminCredentials(session = v)
                                                    }
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
