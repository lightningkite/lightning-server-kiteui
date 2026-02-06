package com.lightningkite.lightningserver.admin

//
// PURPOSE:
// This is a diagnostic/debug screen that displays all registered form renderers in the FormModule.
// For each renderer that has a valid type, it creates a sample form field with a default value.
// This allows developers to visually test and verify that all form generators render correctly.
//
// Migrated to forms2 by Claude

import com.lightningkite.kiteui.Routable
import com.lightningkite.kiteui.forms.displayName
import com.lightningkite.kiteui.forms.FormModule
import com.lightningkite.kiteui.forms.RenderContext
import com.lightningkite.kiteui.forms.defaults
import com.lightningkite.kiteui.navigation.Page

import com.lightningkite.kiteui.views.ViewWriter
import com.lightningkite.kiteui.views.direct.col
import com.lightningkite.kiteui.views.direct.scrolling
import com.lightningkite.kiteui.views.direct.text
import com.lightningkite.kiteui.views.l2.field
import com.lightningkite.reactive.core.Signal
import com.lightningkite.services.database.SerializationRegistry
import com.lightningkite.services.database.default
import kotlinx.serialization.KSerializer

@Routable("field-test-screen")
class FieldTestScreen: Page {
    override fun ViewWriter.render() {
        scrolling.col {
            val module = FormModule().apply { defaults() }
            // by Claude - migrated to forms2 API
            // Iterate through all registered renderers
            module.allRenderers.forEach { (selector, renderer) ->
                // Skip renderers without a type selector (annotation-only or kind-only)
                if (selector.type == null) return@forEach
                val s = try {
                    SerializationRegistry.master.get(selector.type!!, arrayOf()) as KSerializer<Any?>
                } catch (e: Throwable) {
                    return@forEach
                }
                field(renderer.name + " - " + s.displayName) {
                    try {
                        val context = RenderContext(s)
                        val value = Signal(s.default())
                        // Use FormModule.form() instead of calling renderer directly - handles type variance
                        module.form(context, value)()
                    } catch (e: Throwable) {
                        text("Error on ${renderer.name}: ${e.message}")
                    }
                }
            }
        }
    }
}