//
// CODE REVIEW SUMMARY
// ===================
// QuickTestPage is a minimal test page for validating nullable type form rendering.
// Used for development/debugging of the FormModule's type picker feature.
//
// IMPROVEMENT SUGGESTIONS:
//
// 1. DEBUG PAGE: This is a development/test page. Consider hiding from production
//    navigation or marking clearly as a debug tool.
//
// 2. HARDCODED TEST: Only tests Int?. Consider adding more type examples to make
//    this a more comprehensive form testing page.
//
// 3. MISSING KDOC: No documentation explaining what this page tests or how to use it.
//
// 4. SIGNAL SCOPE: The Signal is created inline. Value changes won't persist or
//    be observable outside this render. This may be intentional for testing.
//
package com.lightningkite.lightningserver.admin

import com.lightningkite.kiteui.Routable
import com.lightningkite.kiteui.forms.FormModule
import com.lightningkite.kiteui.forms.form
import com.lightningkite.kiteui.navigation.Page

import com.lightningkite.kiteui.views.ViewWriter
import com.lightningkite.kiteui.views.direct.col
import com.lightningkite.kiteui.views.l2.field
import com.lightningkite.reactive.core.Signal
import kotlinx.serialization.builtins.nullable
import kotlinx.serialization.builtins.serializer

@Routable("quick-test")
class QuickTestPage: Page {
    override fun ViewWriter.render() {
        col {
            val module = FormModule()
            module.showTypePicker = true
            val s = Int.serializer().nullable
            field("Nullable int test") {
                form(module, s, Signal<Int?>(null))
            }
        }
    }
}