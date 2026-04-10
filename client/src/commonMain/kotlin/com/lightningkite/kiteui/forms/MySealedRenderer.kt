@file:OptIn(ExperimentalSerializationApi::class)

package com.lightningkite.kiteui.forms

import com.lightningkite.kiteui.models.rem
import com.lightningkite.kiteui.views.ElementWriter
import com.lightningkite.kiteui.views.atTop
import com.lightningkite.kiteui.views.direct.*
import com.lightningkite.kiteui.views.expanding
import com.lightningkite.kiteui.views.fieldTheme
import com.lightningkite.reactive.context.reactive
import com.lightningkite.reactive.core.Constant
import com.lightningkite.reactive.core.MutableReactive
import com.lightningkite.reactive.core.Reactive
import com.lightningkite.reactive.lensing.lens
import com.lightningkite.services.database.MySealedClassSerializerInterface
import com.lightningkite.services.database.default
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer

/**
 * Renderer for sealed class types using MySealedClassSerializerInterface.
 *
 * Displays a dropdown to select the sealed subtype, then renders the selected
 * subtype's form/view recursively.
 *
 * by Claude
 */
object MySealedRenderer : Renderer<Any> {
    override val name: String = "Options"

    override fun priority(context: RenderContext<Any>, module: FormModule): Float {
        // Only match if serializer is MySealedClassSerializerInterface
        return if (context.serializer is MySealedClassSerializerInterface<*>) 1f else -1f
    }

    override fun columnWidth(context: RenderContext<Any>, module: FormModule): Double = 20.0

    @Suppress("UNCHECKED_CAST")
    override fun form(context: RenderContext<Any>, value: MutableReactive<Any>, module: FormModule): ElementWriter.CanAddTheme.() -> Unit {
        println("Sealed used")
        val serializer = context.serializer as MySealedClassSerializerInterface<Any>

        return {
            val type = value.lens(
                get = { serializer.options.find { o -> o.isInstance(it) } ?: serializer.options.first() },
                set = { it.serializer.default() }
            )
            row {
                atTop.sizeConstraints(width = 10.rem).fieldTheme.select {
                    bind(type, Constant(serializer.options)) { it.serializer.displayName }
                }
                expanding.frame {
                    reactive {
                        val selectedType = type()
                        clearChildren()
                        val subSerializer = selectedType.serializer as KSerializer<Any>
                        val subContext = RenderContext(subSerializer, context.fieldAnnotations)
                        val subValue = value.lens(
                            get = { if (selectedType.isInstance(it)) it else subSerializer.default() },
                            set = { it }
                        )
                        module.form(subContext, subValue)()
                    }
                }
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    override fun view(context: RenderContext<Any>, value: Reactive<Any>, module: FormModule): ElementWriter.CanAddTheme.() -> Unit {
        val serializer = context.serializer as MySealedClassSerializerInterface<Any>

        return {
            row {
                reactive {
                    clearChildren()
                    val currentValue = value()
                    val selectedType = serializer.options.find { it.isInstance(currentValue) }
                        ?: serializer.options.first()

                    text(selectedType.serializer.displayName)
                    space()
                    val subSerializer = selectedType.serializer as KSerializer<Any>
                    val subContext = RenderContext(subSerializer, context.fieldAnnotations)
                    val subValue = value.lens {
                        if (selectedType.isInstance(it)) it else subSerializer.default()
                    }
                    module.view(subContext, subValue)()
                }
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    override fun cellView(context: RenderContext<Any>, value: Reactive<Any>, module: FormModule): ElementWriter.CanAddTheme.() -> Unit {
        val serializer = context.serializer as MySealedClassSerializerInterface<Any>

        return {
            text {
                ::content {
                    val currentValue = value()
                    val selectedType = serializer.options.find { it.isInstance(currentValue) }
                        ?: serializer.options.first()
                    selectedType.serializer.displayName
                }
            }
        }
    }
}

// ===== Extension for display name on KSerializer =====

/**
 * Register the sealed class renderer with the module.
 *
 * by Claude
 */
fun FormModule.registerMySealed() {
    // Use wildcard selector - priority() handles the matching
    register(Selector(), MySealedRenderer)
}
