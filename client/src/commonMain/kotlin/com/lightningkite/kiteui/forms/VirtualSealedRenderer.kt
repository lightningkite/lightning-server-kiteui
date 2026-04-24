@file:OptIn(ExperimentalSerializationApi::class)

package com.lightningkite.kiteui.forms

import com.lightningkite.kiteui.models.rem
import com.lightningkite.kiteui.views.ElementWriter
import com.lightningkite.kiteui.views.ViewWriter
import com.lightningkite.kiteui.views.atTop
import com.lightningkite.kiteui.views.centered
import com.lightningkite.kiteui.views.direct.*
import com.lightningkite.kiteui.views.expanding
import com.lightningkite.kiteui.views.fieldTheme
import com.lightningkite.reactive.context.reactive
import com.lightningkite.reactive.core.Constant
import com.lightningkite.reactive.core.MutableReactive
import com.lightningkite.reactive.core.Reactive
import com.lightningkite.reactive.core.remember
import com.lightningkite.reactive.lensing.lens
import com.lightningkite.services.database.VirtualInstance
import com.lightningkite.services.database.VirtualSealed
import com.lightningkite.services.database.VirtualSealedInstance
import com.lightningkite.services.database.VirtualSealedOption
import com.lightningkite.services.database.default
import com.lightningkite.services.data.titleCase
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer

public object VirtualSealedRenderer : Renderer<VirtualSealedInstance> {
    override val name: String = "Options"

    override fun priority(context: RenderContext<VirtualSealedInstance>, module: FormModule): Float {
        return if (context.serializer is VirtualSealed.Concrete) 1f else -1f
    }

    override fun columnWidth(context: RenderContext<VirtualSealedInstance>, module: FormModule): Double = 20.0

    @Suppress("UNCHECKED_CAST")
    override fun form(context: RenderContext<VirtualSealedInstance>, value: MutableReactive<VirtualSealedInstance>, module: FormModule): ElementWriter.CanAddTheme.() -> Unit {
        val ser = context.serializer as VirtualSealed.Concrete

        return {
            val type = value.lens(
                get = { v -> v.option },
                set = { option -> VirtualSealedInstance(option, ser.serializableOptions[option.index].serializer.default()!!) }
            )
            val tt = remember { type() }
            row {
                centered.sizeConstraints(width = 10.rem).fieldTheme.select {
                    bind(type, Constant(ser.sealed.options.toList())) { it.name.substringAfterLast('.').titleCase() }
                }
                expanding.swapView {
                    swapping(
                        current = { tt() },
                        views = { selectedType ->
                            val subSerializer = ser.serializableOptions[selectedType.index].serializer
                            val subContext = RenderContext(subSerializer, context.fieldAnnotations)
                            val subValue = value.lens(
                                get = { v ->
                                    if (v.option.index == selectedType.index) v.value
                                    else subSerializer.default()
                                },
                                set = { VirtualSealedInstance(selectedType, it!!) }
                            )
                            module.form(subContext, subValue)()
                        }
                    )
                }
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    override fun view(context: RenderContext<VirtualSealedInstance>, value: Reactive<VirtualSealedInstance>, module: FormModule): ElementWriter.CanAddTheme.() -> Unit {
        val ser = context.serializer as VirtualSealed.Concrete

        return {
            val tt = remember { value().option }
            row {
                centered.text { ::content { tt().name.substringAfterLast('.').titleCase() } }
                expanding.swapView {
                    swapping(
                        current = { tt() },
                        views = { selectedType ->
                            val subSerializer = ser.serializableOptions[selectedType.index].serializer
                            val subContext = RenderContext(subSerializer, context.fieldAnnotations)
                            val subValue = value.lens(
                                get = { v ->
                                    if (v.option.index == selectedType.index) v.value
                                    else subSerializer.default()
                                },
                            )
                            module.view(subContext, subValue)()
                        }
                    )
                }
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    override fun cellView(context: RenderContext<VirtualSealedInstance>, value: Reactive<VirtualSealedInstance>, module: FormModule): ElementWriter.CanAddTheme.() -> Unit {
        return {
            text {
                ::content { value().option.name.substringAfterLast('.').titleCase() }
            }
        }
    }
}

public fun FormModule.registerVirtualSealed() {
    register(Selector(), VirtualSealedRenderer)
}
