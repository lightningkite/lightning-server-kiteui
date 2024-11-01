package com.lightningkite.admin

import com.lightningkite.kiteui.*
import com.lightningkite.kiteui.navigation.ScreenNavigator
import com.lightningkite.kiteui.views.*
import com.lightningkite.lightningserver.admin.AutoRoutes
import com.lightningkite.lightningserver.admin.app
import com.lightningkite.lightningserver.admin.appTheme
import kotlinx.browser.window

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
