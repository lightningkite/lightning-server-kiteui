package com.lightningkite.lightningserver.admin

import com.lightningkite.kiteui.Routable
import com.lightningkite.kiteui.forms.FormModule
import com.lightningkite.kiteui.forms.FormSelector
import com.lightningkite.kiteui.forms.displayName
import com.lightningkite.kiteui.forms.form
import com.lightningkite.kiteui.monitoring.Funnels
import com.lightningkite.kiteui.monitoring.funnel
import com.lightningkite.kiteui.navigation.Page
import com.lightningkite.kiteui.views.ViewModifiable
import com.lightningkite.readable.reactive
import com.lightningkite.kiteui.views.ViewWriter
import com.lightningkite.kiteui.views.direct.button
import com.lightningkite.kiteui.views.direct.col
import com.lightningkite.kiteui.views.direct.onClick
import com.lightningkite.kiteui.views.direct.text
import com.lightningkite.kiteui.views.l2.field
import com.lightningkite.readable.Property
import com.lightningkite.serialization.default
import kotlinx.serialization.builtins.nullable
import kotlinx.serialization.builtins.serializer

@Routable("quick-test")
class QuickTestPage: Page {
    override fun ViewWriter.render(): ViewModifiable = col {
        val module = FormModule()
        module.showTypePicker = true
        val s = Int.serializer().nullable
        field("Nullable int test") {
            form(module, s, Property<Int?>(null))
        }
    }
}