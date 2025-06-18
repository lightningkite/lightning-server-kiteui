package com.lightningkite.template

import com.lightningkite.kiteui.*
import com.lightningkite.kiteui.models.ThemeDerivation
import com.lightningkite.kiteui.navigation.PageNavigator
import com.lightningkite.kiteui.navigation.basePath
import com.lightningkite.readable.invoke
import com.lightningkite.kiteui.views.*
import kotlinx.browser.document
import kotlinx.browser.window
import org.w3c.dom.events.KeyboardEvent
import org.w3c.files.BlobPropertyBag

fun main() {
    window.onerror = { a, b, c, d, e ->
        println("ON ERROR HANDLER $a $b $c $d $e")
        if (e is Exception) e.printStackTrace2()
    }
    root(appTheme.value) {
        app(PageNavigator { AutoRoutes }, PageNavigator { AutoRoutes })
    }
}
