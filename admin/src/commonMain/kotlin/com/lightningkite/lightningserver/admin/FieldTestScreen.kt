package com.lightningkite.lightningserver.admin

//
// PURPOSE:
// This is a diagnostic/debug screen that displays all registered form renderers in the FormModule.
// For each renderer that has a valid type, it creates a sample form field with a default value.
// This allows developers to visually test and verify that all form generators render correctly.
//
// IMPROVEMENT SUGGESTIONS:
// 1. Consider memoizing the FormModule instance instead of creating a new one in render().
//    Currently, a new FormModule() is created on every render, which could be inefficient
//    if this screen re-renders frequently. Consider moving it to a class property or using
//    a shared instance.
//
// 2. The empty annotations list (line 36) could be more explicit. Consider adding a comment
//    or using a named constant like `emptyList<SerializableAnnotation>()` to clarify intent.
//
// 3. Add logging or console output for skipped/failed renderers to aid debugging.
//    Currently, renderers with null types or serialization failures are silently skipped.
//    This makes it hard to diagnose why a renderer isn't appearing.
//
// 4. Consider adding UI controls to filter/search through renderers, especially as the number
//    of registered form types grows. A searchable list would improve usability.
//
// 5. The Signal wrapping the default value (line 39) creates a non-reactive signal.
//    Consider using MutableSignal if you want to enable interaction with the test forms,
//    or add a comment clarifying that these are read-only demonstrations.
//
// 6. Error messages display exception message only (line 41). Consider adding exception type
//    or stack trace (in dev mode) for better debugging: "Error on ${it.name}: ${e::class.simpleName}: ${e.message}"

import com.lightningkite.kiteui.Routable
import com.lightningkite.kiteui.forms.FormModule
import com.lightningkite.kiteui.forms.FormSelector
import com.lightningkite.kiteui.forms.displayName
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
            val module = FormModule()
            module.allForms.forEach {
                if (it.type == null) return@forEach
                val s = try {
                    SerializationRegistry.master.get(it.type!!, arrayOf()) as KSerializer<Any?>
                } catch (e: Throwable) {
                    return@forEach
                }
                field(it.name + " - " + s.displayName) {
                    try {
                        it.form(
                            module, selector = FormSelector<Any?>(
                                serializer = s,
                                listOf(),
                                handlesField = it.handlesField
                            )
                        ).render(this, null, Signal(s.default()))
                    } catch (e: Throwable) {
                        text("Error on ${it.name}: ${e.message}")
                    }
                }
            }
        }
    }
}