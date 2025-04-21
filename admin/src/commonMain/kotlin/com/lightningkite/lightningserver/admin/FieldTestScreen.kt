package com.lightningkite.lightningserver.admin

import com.lightningkite.kiteui.Routable
import com.lightningkite.kiteui.forms.FormModule
import com.lightningkite.kiteui.forms.FormSelector
import com.lightningkite.kiteui.forms.displayName
import com.lightningkite.kiteui.models.rem
import com.lightningkite.kiteui.monitoring.Funnels
import com.lightningkite.kiteui.monitoring.funnel
import com.lightningkite.kiteui.navigation.Page
import com.lightningkite.kiteui.views.ViewModifiable
import com.lightningkite.readable.reactive
import com.lightningkite.kiteui.views.ViewWriter
import com.lightningkite.kiteui.views.direct.button
import com.lightningkite.kiteui.views.direct.col
import com.lightningkite.kiteui.views.direct.onClick
import com.lightningkite.kiteui.views.direct.row
import com.lightningkite.kiteui.views.direct.scrolling
import com.lightningkite.kiteui.views.direct.sizeConstraints
import com.lightningkite.kiteui.views.direct.text
import com.lightningkite.kiteui.views.expanding
import com.lightningkite.kiteui.views.l2.field
import com.lightningkite.readable.Property
import com.lightningkite.serialization.SerializationRegistry
import com.lightningkite.serialization.default
import kotlinx.serialization.KSerializer

@Routable("field-test-screen")
class FieldTestScreen: Page {
    override fun ViewWriter.render(): ViewModifiable = scrolling - col {
        val module = FormModule()
        module.allForms.forEach {
            if(it.type == null) return@forEach
            val s = try {
                SerializationRegistry.master.get(it.type!!, arrayOf()) as KSerializer<Any?>
            } catch(e: Throwable) { return@forEach }
            field(it.name + " - " + s.displayName) {
                try {
                    it.form(
                        module, selector = FormSelector<Any?>(
                            serializer = s,
                            listOf(),
                            handlesField = it.handlesField
                        )
                    ).render(this, null, Property(s.default()))
                } catch (e: Throwable) {
                    text("Error on ${it.name}: ${e.message}")
                }
            }
        }
    }
}