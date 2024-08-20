package com.lightningkite.mppexampleapp

import com.lightningkite.kiteui.Routable
import com.lightningkite.kiteui.forms.form
import com.lightningkite.kiteui.views.ViewWriter
import com.lightningkite.kiteui.models.*
import com.lightningkite.kiteui.navigation.Screen
import com.lightningkite.kiteui.navigation.ScreenNavigator
import com.lightningkite.kiteui.reactive.*
import com.lightningkite.kiteui.views.*
import com.lightningkite.kiteui.views.direct.col
import com.lightningkite.kiteui.views.direct.text
import com.lightningkite.kiteui.views.l2.*
import com.lightningkite.lightningdb.*
import kotlinx.serialization.Serializable
import kotlin.math.absoluteValue
import kotlin.time.Duration.Companion.seconds

//val defaultTheme = brandBasedExperimental("bsa", normalBack = Color.white)
val defaultTheme = Theme.flat("default", Angle(0.55f))// brandBasedExperimental("bsa", normalBack = Color.white)
val appTheme = Property<Theme>(defaultTheme)

fun ViewWriter.app(navigator: ScreenNavigator, dialog: ScreenNavigator) {
    prepareModels()
    com.lightningkite.lightningdb.prepareModels()
//    rootTheme = { appTheme() }
    appNav(navigator, dialog) {
        appName = "KiteUI Sample App"
        ::navItems {
            listOf(
                NavLink(title = { "Home" }, icon = { Icon.home }) { { HomeScreen() } },
            )
        }

//        ::exists {
//            navigator.currentScreen.await() !is UseFullScreen
//        }
    }
}

@Serializable
@GenerateDataClassPaths
@Description("Test Item")
data class TestItem(
    val x: Int = 42,
    val y: String = "X",
    val z: String? = null,
)

@Routable("/")
class HomeScreen: Screen {
    override fun ViewWriter.render() {
        col {
            val prop = Property(TestItem())
            form(TestItem.serializer(), prop, arrayOf())
            text { ::content { prop().toString() }}

            val vtype = TestItem.serializer().makeVirtualType() as VirtualStructure
            text(vtype.annotations.toString())
            val prop2 = Property(vtype())
            form(vtype, prop2, arrayOf())
            text { ::content { prop2().toString() }}
        }
    }
}