package com.lightningkite.template

import com.lightningkite.kiteui.forms.prepareModelsClient
import com.lightningkite.kiteui.models.*
import com.lightningkite.kiteui.navigation.DefaultSerializersModule
import com.lightningkite.kiteui.navigation.PageNavigator
import com.lightningkite.kiteui.reactive.*
import com.lightningkite.kiteui.views.ViewModifiable
import com.lightningkite.kiteui.views.ViewWriter
import com.lightningkite.kiteui.views.l2.*
import com.lightningkite.reactive.context.*
import com.lightningkite.reactive.core.*
import com.lightningkite.reactive.extensions.*
import com.lightningkite.reactive.lensing.*
import com.lightningkite.readable.*
import com.lightningkite.serialization.ClientModule

//val defaultTheme = brandBasedExperimental("bsa", normalBack = Color.white)
val defaultTheme = Theme.flat("default", Angle(0.55f))// brandBasedExperimental("bsa", normalBack = Color.white)
val appTheme = Signal<Theme>(defaultTheme)

// Notification Items
val fcmToken: Signal<String?> = Signal(null)
val setFcmToken =
    { token: String -> fcmToken.value = token } //This is for iOS. It is used in the iOS app. Do not remove.


fun ViewWriter.app(navigator: PageNavigator, dialog: PageNavigator): ViewModifiable {

    prepareModelsDemoShared()
    prepareModelsClient()
    com.lightningkite.prepareModelsShared()

    DefaultSerializersModule = ClientModule

    navigator.navigate(LandingPage() )
    return appNav(navigator, dialog) {
        appName = "KiteUI Sample App"
        ::navItems {
            listOf(
                NavLink(title = { "Home" }, icon = { Icon.home }) { { HomePage() } },
//                NavLink(title = { "Internal" }, icon = { Icon.home }) { { RootPage } },
//                NavLink(title = { "Documentation" }, icon = { Icon.list }) { { DocSearchPage } },
            )
        }

        ::exists {
            navigator.currentPage() !is UseFullPage
        }

    }
}

interface UseFullPage
