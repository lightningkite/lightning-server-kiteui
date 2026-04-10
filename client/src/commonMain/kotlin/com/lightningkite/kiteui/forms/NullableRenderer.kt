@file:OptIn(ExperimentalSerializationApi::class)

package com.lightningkite.kiteui.forms

import com.lightningkite.kiteui.views.ElementWriter
import com.lightningkite.kiteui.views.centered
import com.lightningkite.kiteui.views.direct.*
import com.lightningkite.kiteui.views.expanding
import com.lightningkite.reactive.context.reactive
import com.lightningkite.reactive.core.MutableReactive
import com.lightningkite.reactive.core.Reactive
import com.lightningkite.reactive.lensing.lens
import com.lightningkite.services.database.default
import com.lightningkite.services.database.nullElement
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer

/**
 * Generic renderer for nullable types.
 *
 * Wraps the inner type's renderer with a checkbox to toggle null/non-null state.
 * When toggling from null to non-null, uses the type's default value.
 *
 * by Claude
 */
object NullableRenderer : Renderer<Any?> {
    override val name: String = "Optional"  // by Claude

    override fun priority(context: RenderContext<Any?>, module: FormModule): Float {
        return if (context.serializer.descriptor.isNullable) 0.4f else -1f
    }

    @Suppress("UNCHECKED_CAST")
    override fun form(context: RenderContext<Any?>, value: MutableReactive<Any?>, module: FormModule): ElementWriter.CanAddTheme.() -> Unit {
        val innerSerializer = context.serializer.nullElement() as? KSerializer<Any>
            ?: return { /* No inner serializer found */ }
        val innerContext = RenderContext(innerSerializer, context.fieldAnnotations)
        val innerRenderer = module.select(innerContext)

        return {
            row {
                // Track the last non-null value
                var ifNotNull: Any = value.state.getOrNull() ?: innerSerializer.default()

                // Checkbox to toggle null state - uses modify because we need original to know what to restore
                centered.checkbox {
                    checked bind value.lens(
                        get = { it != null },
                        modify = { original, checked -> if (checked) (original ?: ifNotNull) else null }
                    )
                }

                // Inner renderer (shown when not null)
                expanding.frame {
                    reactive {
                        clearChildren()
                        val current = value()
                        if (current != null) {
                            ifNotNull = current
                            // Use set since we're just unwrapping/wrapping the nullable
                            val nonNullValue = value.lens(
                                get = { it ?: ifNotNull },
                                set = { it }
                            )
                            innerRenderer.form(innerContext, nonNullValue, module)()
                        } else {
                            text("N/A")
                        }
                    }
                }
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    override fun view(context: RenderContext<Any?>, value: Reactive<Any?>, module: FormModule): ElementWriter.CanAddTheme.() -> Unit {
        val innerSerializer = context.serializer.nullElement() as? KSerializer<Any>
            ?: return { text("—") }
        val innerContext = RenderContext(innerSerializer, context.fieldAnnotations)
        val innerRenderer = module.select(innerContext)

        return {
            swapView {
                swapping(
                    current = { value() != null },
                    views = { isNotNull ->
                        if (isNotNull) {
                            val nonNullValue = value.lens { it ?: innerSerializer.default() }
                            innerRenderer.view(innerContext, nonNullValue, module)()
                        } else {
                            text("—")
                        }
                    }
                )
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    override fun cellView(context: RenderContext<Any?>, value: Reactive<Any?>, module: FormModule): ElementWriter.CanAddTheme.() -> Unit {
        val innerSerializer = context.serializer.nullElement() as? KSerializer<Any>
            ?: return { text("—") }
        val innerContext = RenderContext(innerSerializer, context.fieldAnnotations)
        val innerRenderer = module.select(innerContext)

        return {
            swapView {
                swapping(
                    current = { value() != null },
                    views = { isNotNull ->
                        if (isNotNull) {
                            val nonNullValue = value.lens { it ?: innerSerializer.default() }
                            innerRenderer.cellView(innerContext, nonNullValue, module)()
                        } else {
                            text("—")
                        }
                    }
                )
            }
        }
    }

    override fun columnWidth(context: RenderContext<Any?>, module: FormModule): Double? {
        val innerSerializer = context.serializer.nullElement() ?: return null
        val innerContext = RenderContext(innerSerializer, context.fieldAnnotations)
        return module.columnWidth(innerContext)?.let { it + 2 }
    }

    override fun labeledForm(
        context: RenderContext<Any?>,
        value: MutableReactive<Any?>,
        module: FormModule,
        label: String,
        description: String?
    ): ElementWriter.CanAddTheme.() -> Unit = { fieldWithoutBorder(label, description) { form(context, value, module)() } }

    override fun labeledView(
        context: RenderContext<Any?>,
        value: Reactive<Any?>,
        module: FormModule,
        label: String,
        description: String?
    ): ElementWriter.CanAddTheme.() -> Unit = { fieldWithoutBorder(label, description) { view(context, value, module)() } }
}

fun FormModule.registerNullable() {
    register(Selector(), NullableRenderer)
}
