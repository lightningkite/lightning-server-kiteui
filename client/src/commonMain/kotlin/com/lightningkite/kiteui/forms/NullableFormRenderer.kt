package com.lightningkite.kiteui.forms

import com.lightningkite.kiteui.reactive.*
import com.lightningkite.kiteui.views.ViewWriter
import com.lightningkite.kiteui.views.direct.*
import com.lightningkite.kiteui.views.expanding
import com.lightningkite.serialization.SerializableProperty
import com.lightningkite.serialization.default
import com.lightningkite.serialization.nullElement
import kotlinx.serialization.KSerializer

object NullableFormRenderer : FormRenderer.Generator, ViewRenderer.Generator {
    override val name: String get() = "Null Wrapper"
    override val basePriority: Float
        get() = 0.3f

    override fun matches(module: FormModule, selector: FormSelector<*>): Boolean {
        return selector.serializer.descriptor.isNullable
    }

    override fun size(module: FormModule, selector: FormSelector<*>): FormSize {
        val innerSerializer = selector.serializer.nullElement()!! as KSerializer<Any>
        val innerSelector = selector.copy(innerSerializer)
        val inner = module.form(innerSelector)
        return inner.size.copy(approximateWidth = inner.size.approximateWidth + 3.0)
    }

    @Suppress("UNCHECKED_CAST")
    override fun <T> form(module: FormModule, selector: FormSelector<T>): FormRenderer<T> {
        val innerSerializer = selector.serializer.nullElement()!! as KSerializer<Any>
        val innerSelector = selector.copy(innerSerializer)
        val inner = module.form(innerSelector)
        return FormRenderer(module, this, selector as FormSelector<Any?>) { field, writable ->
            row {
                var ifNotNull: Any = writable.state.getOrNull() ?: innerSerializer.default()
                checkbox {
                    checked bind writable.lens(
                        get = { v -> v != null },
                        modify = { e, v ->
                            if (v) ifNotNull else null
                        },
                    )
                }
                expanding - onlyWhen { writable() != null } - inner.render(
                    this,
                    field,
                    writable.lens(
                        get = { v -> v ?: innerSerializer.default() },
                        modify = { e, v ->
                            ifNotNull = v
                            if (e == null) null else v
                        },
                    ),
                )
            }
        } as FormRenderer<T>
    }

    @Suppress("UNCHECKED_CAST")
    override fun <T> view(module: FormModule, selector: FormSelector<T>): ViewRenderer<T> {
        val innerSerializer = selector.serializer.nullElement()!! as KSerializer<Any>
        val innerSelector = selector.copy(innerSerializer)
        val inner = module.view(innerSelector)
        return ViewRenderer(module, this, selector as FormSelector<Any?>) { field, readable ->
            stack {
                val isNull = shared { readable() == null }
                reactiveScope {
                    clearChildren()
                    if (isNull()) {
                        text("N/A")
                    } else {
                        inner.render(
                            this@stack,
                            field,
                            readable.lens { it ?: innerSerializer.default() }
                        )
                    }
                }
            }
        } as ViewRenderer<T>
    }
}