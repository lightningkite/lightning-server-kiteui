package com.lightningkite.admin

import com.lightningkite.kiteui.*
import com.lightningkite.kiteui.navigation.DefaultJson
import com.lightningkite.kiteui.navigation.PageNavigator
import com.lightningkite.kiteui.views.*
import com.lightningkite.lightningserver.admin.*
import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.serialization.Serializable
import org.w3c.dom.HTMLScriptElement

fun main() {
    var created: RView? = null
    window.onerror = { a, b, c, d, e ->
        println("ON ERROR HANDLER $a $b $c $d $e")
        if (e is Exception) e.printStackTrace2()
    }
    (document.getElementById("injectedBackendInformation") as? HTMLScriptElement)?.innerText?.let {
        val info = DefaultJson.decodeFromString<InjectedBackendInformation>(it)
        serverUrl.value = info.url
    }
    root(appTheme.value) {
        app(PageNavigator { AutoRoutes }, PageNavigator { AutoRoutes })
    }
}

@Serializable
data class InjectedBackendInformation(val url: String)