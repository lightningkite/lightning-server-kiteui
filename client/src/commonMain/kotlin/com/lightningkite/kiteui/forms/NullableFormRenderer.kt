package com.lightningkite.kiteui.forms

import com.lightningkite.kiteui.views.atTopStart
import com.lightningkite.kiteui.views.direct.*
import com.lightningkite.kiteui.views.expanding
import com.lightningkite.reactive.context.reactive
import com.lightningkite.reactive.context.reactiveScope
import com.lightningkite.reactive.core.remember
import com.lightningkite.reactive.lensing.lens
import com.lightningkite.services.database.default
import com.lightningkite.services.database.nullElement
import kotlinx.serialization.KSerializer

object NullableFormRenderer : FormRenderer.Generator, ViewRenderer.Generator {
    override val name: String get() = "Null Wrapper"
    override val basePriority: Float
        get() = 0.4f
    override val nullable: Boolean get() = true

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
        val innerSelector by lazy { selector.copy(innerSerializer) }
        val inner by lazy { module.form(innerSelector) }
        return FormRenderer(module, this, selector as FormSelector<Any?>) { field, mutable ->
            row {
                var ifNotNull: Any = mutable.state.getOrNull() ?: innerSerializer.default()
                padded - stack {
                    atTopStart - checkbox {
                        checked bind mutable.lens(
                            get = { v -> v != null },
                            modify = { e, v ->
                                if (v) ifNotNull else null
                            },
                        )
                    }
                }
                expanding - stack {
                    val isNull = remember { mutable() == null }
                    reactive {
                        clearChildren()
                        if (!isNull()) inner.render(
                            this@stack,
                            field,
                            mutable.lens(
                                get = { v -> v ?: innerSerializer.default() },
                                modify = { e, v ->
                                    ifNotNull = v
                                    if (e == null) null else v
                                },
                            ),
                        )
                    }
                }
            }
        } as FormRenderer<T>
    }

    @Suppress("UNCHECKED_CAST")
    override fun <T> view(module: FormModule, selector: FormSelector<T>): ViewRenderer<T> {
        val innerSerializer = selector.serializer.nullElement()!! as KSerializer<Any>
        val innerSelector by lazy { selector.copy(innerSerializer) }
        val inner by lazy { module.view(innerSelector) }
        return ViewRenderer(module, this, selector as FormSelector<Any?>) { field, readable ->
            stack {
                val isNull = remember { readable() == null }
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