package com.lightningkite.lightningserver.admin

import com.lightningkite.kiteui.Routable
import com.lightningkite.kiteui.forms.FormModule
import com.lightningkite.kiteui.forms.form
import com.lightningkite.kiteui.navigation.Page
import com.lightningkite.kiteui.views.ViewModifiable
import com.lightningkite.kiteui.views.ViewWriter
import com.lightningkite.kiteui.views.direct.col
import com.lightningkite.kiteui.views.l2.field
import com.lightningkite.reactive.core.Signal
import kotlinx.serialization.builtins.nullable
import kotlinx.serialization.builtins.serializer

@Routable("quick-test")
class QuickTestPage: Page {
    override fun ViewWriter.render(): ViewModifiable = col {
        val module = FormModule()
        module.showTypePicker = true
        val s = Int.serializer().nullable
        field("Nullable int test") {
            form(module, s, Signal<Int?>(null))
        }
    }
}