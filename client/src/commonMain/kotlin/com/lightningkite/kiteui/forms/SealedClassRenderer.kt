@file:OptIn(ExperimentalSerializationApi::class, InternalSerializationApi::class)

package com.lightningkite.kiteui.forms

import com.lightningkite.kiteui.models.rem
import com.lightningkite.kiteui.views.ViewWriter
import com.lightningkite.kiteui.views.atTop
import com.lightningkite.kiteui.views.direct.*
import com.lightningkite.kiteui.views.expanding
import com.lightningkite.kiteui.views.fieldTheme
import com.lightningkite.reactive.context.reactive
import com.lightningkite.reactive.core.Constant
import com.lightningkite.reactive.core.MutableReactive
import com.lightningkite.reactive.core.Reactive
import com.lightningkite.reactive.lensing.lens
import com.lightningkite.services.database.default
import com.lightningkite.titleCase
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PolymorphicKind
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.StructureKind
import kotlinx.serialization.encoding.AbstractDecoder
import kotlinx.serialization.encoding.AbstractEncoder
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.internal.AbstractPolymorphicSerializer
import kotlinx.serialization.modules.EmptySerializersModule
import kotlinx.serialization.modules.SerializersModule

private data class SealedOption(
    val serialName: String,
    val displayName: String,
    val serializer: KSerializer<Any>
)

private object StubDecoder : AbstractDecoder() {
    override val serializersModule: SerializersModule = EmptySerializersModule()
    override fun decodeElementIndex(descriptor: SerialDescriptor): Int = CompositeDecoder.DECODE_DONE
}

private object StubEncoder : AbstractEncoder() {
    override val serializersModule: SerializersModule = EmptySerializersModule()
}

@Suppress("UNCHECKED_CAST")
private fun extractOptions(serializer: KSerializer<Any>): List<SealedOption> {
    println("DEBUG in extract options")
    val poly = serializer as? AbstractPolymorphicSerializer<Any> ?: return emptyList()
    val valueDescriptor = serializer.descriptor.getElementDescriptor(1)
    return (0 until valueDescriptor.elementsCount).mapNotNull { i ->
        val serialName = valueDescriptor.getElementName(i)
        val subSerializer = poly.findPolymorphicSerializerOrNull(StubDecoder, serialName)
            as? KSerializer<Any> ?: return@mapNotNull null
        SealedOption(
            serialName = serialName,
            displayName = serialName.substringAfterLast('.').titleCase(),
            serializer = subSerializer
        )
    }
}

@Suppress("UNCHECKED_CAST")
private fun findOption(
    serializer: KSerializer<Any>,
    value: Any,
    options: List<SealedOption>
): SealedOption? {
    println("DEBUG findOption")
    val poly = serializer as? AbstractPolymorphicSerializer<Any> ?: return null
    val matched = poly.findPolymorphicSerializerOrNull(StubEncoder, value) ?: return null
    return options.find { it.serialName == matched.descriptor.serialName }
}

object SealedClassRenderer : Renderer<Any> {
    override val name: String = "Sealed Options"

    override fun priority(context: RenderContext<Any>, module: FormModule): Float {
        return if (context.serializer.descriptor.kind == PolymorphicKind.SEALED) 1f else -1f
    }

    override fun columnWidth(context: RenderContext<Any>, module: FormModule): Double = 20.0

    @Suppress("UNCHECKED_CAST")
    override fun form(context: RenderContext<Any>, value: MutableReactive<Any>, module: FormModule): ViewWriter.() -> Unit {
        println("DEBUG form")
        val options = extractOptions(context.serializer as KSerializer<Any>)
        if (options.isEmpty()) return { text("No sealed subtypes found") }

        return {
            val type = value.lens(
                get = { v -> findOption(context.serializer as KSerializer<Any>, v, options) ?: options.first() },
                set = { option -> option.serializer.default() }
            )
            row {
                atTop.sizeConstraints(width = 10.rem).fieldTheme.select {
                    bind(type, Constant(options)) { it.displayName }
                }
                expanding.frame {
                    reactive {
                        val selectedType = type()
                        clearChildren()
                        val subSerializer = selectedType.serializer
                        val subContext = RenderContext(subSerializer, context.fieldAnnotations)
                        val subValue = value.lens(
                            get = { v ->
                                val matched = findOption(context.serializer as KSerializer<Any>, v, options)
                                if (matched?.serialName == selectedType.serialName) v
                                else subSerializer.default()
                            },
                            set = { it }
                        )
                        module.form(subContext, subValue)()
                    }
                }
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    override fun view(context: RenderContext<Any>, value: Reactive<Any>, module: FormModule): ViewWriter.() -> Unit {
        println("DEBUG view")
        val options = extractOptions(context.serializer as KSerializer<Any>)
        if (options.isEmpty()) return { text("No sealed subtypes found") }

        return {
            row {
                reactive {
                    clearChildren()
                    val currentValue = value()
                    val selectedType = findOption(context.serializer as KSerializer<Any>, currentValue, options)
                        ?: options.first()

                    text(selectedType.displayName)
                    space()
                    val subSerializer = selectedType.serializer
                    val subContext = RenderContext(subSerializer, context.fieldAnnotations)
                    val subValue = value.lens { v ->
                        val matched = findOption(context.serializer as KSerializer<Any>, v, options)
                        if (matched?.serialName == selectedType.serialName) v
                        else subSerializer.default()
                    }
                    module.view(subContext, subValue)()
                }
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    override fun cellView(context: RenderContext<Any>, value: Reactive<Any>, module: FormModule): ViewWriter.() -> Unit {
        println("DEBUG cellView")
        val options = extractOptions(context.serializer as KSerializer<Any>)
        return {
            text {
                ::content {
                    val currentValue = value()
                    val selectedType = findOption(context.serializer as KSerializer<Any>, currentValue, options)
                        ?: options.firstOrNull()
                    selectedType?.displayName ?: "Unknown"
                }
            }
        }
    }
}

fun FormModule.registerSealedClass() {
    register(Selector(kind= StructureKind.CLASS), SealedClassRenderer)
}
