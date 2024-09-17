package com.lightningkite.mppexampleapp

import com.lightningkite.kiteui.*
import com.lightningkite.kiteui.models.Theme
import com.lightningkite.kiteui.models.ThemeDerivation
import com.lightningkite.kiteui.navigation.ScreenNavigator
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
    root(appTheme.value) {
        app(ScreenNavigator { AutoRoutes }, ScreenNavigator { AutoRoutes })
    }
}
