//
// CODE REVIEW SUMMARY
// ===================
// Simple.kt is the JS entry point for the admin panel web application.
// It sets up error handling, reads injected backend URL from HTML, and initializes the app.
//
// IMPROVEMENT SUGGESTIONS:
//
// 1. ERROR HANDLING (Line 24): JSON parsing for InjectedBackendInformation can throw
//    if script content is malformed. Should have try-catch with fallback.
//
// 2. DEBUG LOGGING (Line 20): "ON ERROR HANDLER" println should use proper logging
//    or be removed in production builds.
//
// 3. UNUSED VARIABLE (Line 18): `created` is declared but never assigned or used.
//    Remove this dead code.
//
// 4. MISSING KDOC: No documentation for InjectedBackendInformation or the injection
//    mechanism from server-side rendering.
//
// 5. FALLBACK URL: If no injectedBackendInformation script is present, serverUrl
//    keeps its default. Consider logging a warning about missing configuration.
//
// 6. PACKAGE NAME MISMATCH: File is in `com.lightningkite.admin` but other admin
//    files use `com.lightningkite.lightningserver.admin`. May cause import confusion.
//
package com.lightningkite.admin

import com.lightningkite.kiteui.navigation.DefaultJson
import com.lightningkite.kiteui.navigation.PageNavigator
import com.lightningkite.kiteui.printStackTrace2
import com.lightningkite.kiteui.root
import com.lightningkite.kiteui.views.RView
import com.lightningkite.lightningserver.admin.AutoRoutes
import com.lightningkite.lightningserver.admin.app
import com.lightningkite.lightningserver.admin.appTheme
import com.lightningkite.lightningserver.admin.serverUrl
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
    root(appTheme) {
        app(PageNavigator { AutoRoutes }, PageNavigator { AutoRoutes })
    }
}

@Serializable
data class InjectedBackendInformation(val url: String)