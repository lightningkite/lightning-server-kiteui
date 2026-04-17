@file:OptIn(ExperimentalSerializationApi::class)

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
import com.lightningkite.services.database.VirtualInstance
import com.lightningkite.services.database.VirtualSealed
import com.lightningkite.services.database.default
import com.lightningkite.titleCase
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer

private data class VirtualSealedOption(
    val index: Int,
    val name: String,
    val displayName: String,
    val serializer: KSerializer<Any?>
)

object VirtualSealedRenderer : Renderer<Any> {
    override val name: String = "Sealed Options"

    override fun priority(context: RenderContext<Any>, module: FormModule): Float {
        return if (context.serializer is VirtualSealed.Concrete) 1f else -1f
    }

    override fun columnWidth(context: RenderContext<Any>, module: FormModule): Double = 20.0

    private fun extractOptions(serializer: KSerializer<*>): List<VirtualSealedOption> {
        println("DEBUG extract options")
        println("DEBUG serializer: ${serializer.descriptor.serialName}")
        println("DEBUG serializer ${serializer}")
        val concrete = serializer as? VirtualSealed.Concrete ?: return emptyList()
        println("DEBUG concrete: ${concrete.descriptor.serialName}")
        println("DEBUG sealed: ${concrete.sealed}")
        concrete.sealed.options.map{
            println("DEBUG it ${concrete}")
        }
        return concrete.sealed.options.mapIndexed { index, opt ->
            VirtualSealedOption(
                index = index,
                name = opt.name,
                displayName = opt.name.substringAfterLast('.').titleCase(),
                serializer = concrete.optionSerializers[index]
            )
        }
    }

    private fun findOption(value: Any?, options: List<VirtualSealedOption>): VirtualSealedOption? {
        if (value !is VirtualInstance) return null
        return options.find { it.name == value.type.serialName }
    }

    @Suppress("UNCHECKED_CAST")
    override fun form(context: RenderContext<Any>, value: MutableReactive<Any>, module: FormModule): ViewWriter.() -> Unit {
        val options = extractOptions(context.serializer)
        if (options.isEmpty()) return { text("No sealed subtypes found") }

        return {
            val type = value.lens(
                get = { v -> findOption(v, options) ?: options.first() },
                set = { option -> option.serializer.default() as Any }
            )
            row {
                atTop.sizeConstraints(width = 10.rem).fieldTheme.select {
                    bind(type, Constant(options)) { it.displayName }
                }
                expanding.frame {
                    reactive {
                        val selectedType = type()
                        clearChildren()
                        val subSerializer = selectedType.serializer as KSerializer<Any>
                        val subContext = RenderContext(subSerializer, context.fieldAnnotations)
                        val subValue = value.lens(
                            get = { v ->
                                val matched = findOption(v, options)
                                if (matched?.index == selectedType.index) v
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
        val options = extractOptions(context.serializer)
        if (options.isEmpty()) return { text("No sealed subtypes found") }

        return {
            row {
                reactive {
                    clearChildren()
                    val currentValue = value()
                    val selectedType = findOption(currentValue, options) ?: options.first()

                    text(selectedType.displayName)
                    space()
                    val subSerializer = selectedType.serializer as KSerializer<Any>
                    val subContext = RenderContext(subSerializer, context.fieldAnnotations)
                    val subValue = value.lens { v ->
                        val matched = findOption(v, options)
                        if (matched?.index == selectedType.index) v
                        else subSerializer.default()
                    }
                    module.view(subContext, subValue)()
                }
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    override fun cellView(context: RenderContext<Any>, value: Reactive<Any>, module: FormModule): ViewWriter.() -> Unit {
        val options = extractOptions(context.serializer)
        return {
            text {
                ::content {
                    val currentValue = value()
                    val selectedType = findOption(currentValue, options) ?: options.firstOrNull()
                    selectedType?.displayName ?: "Unknown"
                }
            }
        }
    }
}

fun FormModule.registerVirtualSealed() {
    register(Selector(), VirtualSealedRenderer)
}
