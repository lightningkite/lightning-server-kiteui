package com.lightningkite.mppexampleapp

import com.lightningkite.kiteui.Blob
import com.lightningkite.kiteui.ExternalServices
import com.lightningkite.kiteui.launchGlobal
import com.lightningkite.kiteui.models.Theme
import com.lightningkite.kiteui.models.ThemeDerivation
import com.lightningkite.kiteui.navigation.ScreenNavigator
import com.lightningkite.kiteui.printStackTrace2
import com.lightningkite.kiteui.reactive.invoke
import com.lightningkite.kiteui.views.*
import com.lightningkite.kiteui.views.direct.KeyCodes
import kotlinx.browser.document
import kotlinx.browser.window
import org.w3c.dom.events.KeyboardEvent
import org.w3c.files.BlobPropertyBag

fun main() {
    var created: RView? = null
    window.onerror = { a, b, c, d, e ->
        println("ON ERROR HANDLER $a $b $c $d $e")
        if (e is Exception) e.printStackTrace2()
    }
    val context = RContext("/")
    object : ViewWriter() {
        override val context: RContext = context

        override fun addChild(view: RView) {
            document.body?.append(view.native.create())
//            created = view
        }

        val theme: suspend () -> Theme = { appTheme() }

        init {
            beforeNextElementSetup {
                ::themeChoice { ThemeDerivation(theme()) }
            }
        }
    }.run {
        app(ScreenNavigator { AutoRoutes }, ScreenNavigator { AutoRoutes })
    }
}
