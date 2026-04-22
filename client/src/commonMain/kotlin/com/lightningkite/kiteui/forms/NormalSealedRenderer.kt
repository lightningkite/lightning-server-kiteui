@file:OptIn(ExperimentalSerializationApi::class)

package com.lightningkite.kiteui.forms

import com.lightningkite.kiteui.models.rem
import com.lightningkite.kiteui.views.ViewWriter
import com.lightningkite.kiteui.views.atTop
import com.lightningkite.kiteui.views.direct.*
import com.lightningkite.kiteui.views.expanding
import com.lightningkite.kiteui.views.fieldTheme
import com.lightningkite.reactive.core.Constant
import com.lightningkite.reactive.core.MutableReactive
import com.lightningkite.reactive.core.Reactive
import com.lightningkite.reactive.core.remember
import com.lightningkite.reactive.lensing.lens
import com.lightningkite.services.database.MySealedClassSerializerInterface
import com.lightningkite.services.database.default
import com.lightningkite.services.database.serializableOptions
import com.lightningkite.services.database.serializableOptionsIdentify
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PolymorphicKind
import kotlinx.serialization.descriptors.SerialKind
import kotlinx.serialization.descriptors.StructureKind
import kotlinx.serialization.internal.AbstractPolymorphicSerializer

/**
 * Renderer for sealed class types using MySealedClassSerializerInterface.
 *
 * Displays a dropdown to select the sealed subtype, then renders the selected
 * subtype's form/view recursively.
 *
 * by Claude
 */
object NormalSealedRenderer : Renderer<Any> {
    override val name: String = "Options"

    @OptIn(InternalSerializationApi::class)
    override fun priority(context: RenderContext<Any>, module: FormModule): Float {
        // Only match if serializer is MySealedClassSerializerInterface
        return if (context.serializer is AbstractPolymorphicSerializer) 0.9f else -1f
    }

    override fun columnWidth(context: RenderContext<Any>, module: FormModule): Double = 20.0

    @Suppress("UNCHECKED_CAST")
    override fun form(context: RenderContext<Any>, value: MutableReactive<Any>, module: FormModule): ViewWriter.() -> Unit {
        val serializer = context.serializer
        val options = (serializer.serializableOptions ?: arrayOf()).toList()

        return {
            val type = value.lens(
                get = { serializer.serializableOptionsIdentify(it) ?: options.first() },
                set = { it.serializer.default() }
            )
            val tt = remember { type() }
            row {
                atTop.sizeConstraints(width = 10.rem).fieldTheme.select {
                    bind(type, Constant(options)) { it.serializer.displayName }
                }
                expanding.swapView {
                    swapping(
                        current = { tt() },
                        views = { selectedType ->
                            val subSerializer = selectedType.serializer as KSerializer<Any>
                            val subContext = RenderContext(subSerializer, context.fieldAnnotations)
                            val subValue = value.lens(
                                get = { if (serializer.serializableOptionsIdentify(it) == selectedType) it else subSerializer.default() },
                                set = { it }
                            )
                            module.form(subContext, subValue)()
                        }
                    )
                }
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    override fun view(context: RenderContext<Any>, value: Reactive<Any>, module: FormModule): ViewWriter.() -> Unit {
        val serializer = context.serializer
        val options = (serializer.serializableOptions ?: arrayOf()).toList()

        return {
            val tt = remember { serializer.serializableOptionsIdentify(value()) ?: options.first() }
            row {
                text { ::content { tt().serializer.displayName } }
                expanding.swapView {
                    swapping(
                        current = { tt() },
                        views = { selectedType ->
                            val subSerializer = selectedType.serializer as KSerializer<Any>
                            val subContext = RenderContext(subSerializer, context.fieldAnnotations)
                            val subValue = value.lens {
                                if (selectedType == serializer.serializableOptionsIdentify(it)) it else subSerializer.default()
                            }
                            module.view(subContext, subValue)()
                        }
                    )
                }
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    override fun cellView(context: RenderContext<Any>, value: Reactive<Any>, module: FormModule): ViewWriter.() -> Unit {
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
fun FormModule.registerNormalSealed() {
    // Use wildcard selector - priority() handles the matching
    register(Selector(), NormalSealedRenderer)
}
