package com.lightningkite.template

import com.lightningkite.kiteui.forms.prepareModelsClient
import com.lightningkite.kiteui.views.ViewWriter
import com.lightningkite.kiteui.models.*
import com.lightningkite.kiteui.navigation.DefaultSerializersModule
import com.lightningkite.kiteui.navigation.PageNavigator
import com.lightningkite.kiteui.views.ViewModifiable
import com.lightningkite.readable.*
import com.lightningkite.kiteui.views.l2.*
import com.lightningkite.serialization.ClientModule

//val defaultTheme = brandBasedExperimental("bsa", normalBack = Color.white)
val defaultTheme = Theme.flat("default", Angle(0.55f))// brandBasedExperimental("bsa", normalBack = Color.white)
val appTheme = Property<Theme>(defaultTheme)

// Notification Items
val fcmToken: Property<String?> = Property(null)
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
