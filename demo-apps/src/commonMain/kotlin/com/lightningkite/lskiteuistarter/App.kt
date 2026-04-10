package com.lightningkite.lskiteuistarter

import com.lightningkite.kiteui.*
import com.lightningkite.kiteui.exceptions.ExceptionToMessages
import com.lightningkite.kiteui.exceptions.installLsError
import com.lightningkite.kiteui.models.*
import com.lightningkite.kiteui.navigation.PageNavigator
import com.lightningkite.kiteui.navigation.dialogPageNavigator
import com.lightningkite.kiteui.reactive.*
import com.lightningkite.kiteui.views.ViewWriter
import com.lightningkite.kiteui.views.confirmDanger
import com.lightningkite.kiteui.views.direct.confirmDanger
import com.lightningkite.kiteui.views.l2.appNav
import com.lightningkite.lskiteuistarter.extensions.toAppPlatform
import com.lightningkite.lskiteuistarter.sdk.*
import com.lightningkite.lskiteuistarter.utils.*
import com.lightningkite.reactive.context.*
import com.lightningkite.reactive.core.AppScope
import com.lightningkite.reactive.core.Signal
import com.lightningkite.services.database.*
import kotlinx.coroutines.launch

//val defaultTheme = brandBasedExperimental("bsa", normalBack = Color.white)
val defaultTheme = Theme.flat2("default", Angle(0.55f))// brandBasedExperimental("bsa", normalBack = Color.white)
val appTheme = Signal<Theme>(defaultTheme)

// Notification Items
val fcmToken: Signal<String?> = Signal(null)
val setFcmToken =
    { token: String -> fcmToken.value = token } //This is for iOS. It is used in the iOS app. Do not remove.

var appUpdateChecked = false

fun ViewWriter.app(navigator: PageNavigator, dialog: PageNavigator) {
    context.exceptionHandlers.installLsError()
    context.exceptionHandlers.installLoggedOutErrors()

    AppScope.reactiveSuspending {
        if (currentSession() == null) return@reactiveSuspending
        val permission = notificationPermissions()
        when (permission) {
            false -> {}

            true -> {
                fcmSetup()
            }

            null -> {
                context.confirmDanger(
                    "Send notifications?",
                    "LS KiteUI Starter would like to send you notifications.",
                    "Allow"
                ) {
                    requestNotificationPermissions()
                }
            }
        }
    }


    if (Platform.current != Platform.Web && !appUpdateChecked) {
        appUpdateChecked = true
        AppScope.launch {
            val currentBuild = Build.version
            val releases = try {
                selectedApi.await().api.appRelease.query(
                    Query(
                        condition { it.platform.eq(Platform.current.toAppPlatform()) }
                    ))
            } catch (_: Exception) {
                return@launch
            }

            val currentRelease = releases.find { it.version == currentBuild } ?: return@launch
            val latestRelease = releases.maxByOrNull { it.releaseDate } ?: return@launch
            if (latestRelease._id != currentRelease._id) {
                dialogPageNavigator.navigate(
                    UpdateDialog(
                        newVersion = latestRelease.version,
                        forceUpdate = releases.any { it.requiredUpdate && it.releaseDate > currentRelease.releaseDate }
                    )
                )
            }
        }
    }

    navigator.navigate(LandingPage())
    return appNav(navigator, dialog) {
        appName = "KiteUI Sample App"
        ::navItems {
            listOf(
                NavLink(title = { "Home" }, icon = { Icon.home }) { { HomePage() } },
                NavLink(title = { "Users" }, icon = { Icon.person }) { { UserListPage() } },  // by Claude
            )
        }

        ::exists {
            navigator.currentPage() !is UseFullPage
        }
    }
}

interface UseFullPage


