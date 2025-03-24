package com.lightningkite.kiteui.forms

import com.lightningkite.readable.*
import com.lightningkite.kiteui.views.ViewWriter
import com.lightningkite.kiteui.views.atTopStart
import com.lightningkite.kiteui.views.direct.*
import com.lightningkite.kiteui.views.expanding
import com.lightningkite.serialization.SerializableProperty
import com.lightningkite.serialization.default
import com.lightningkite.serialization.nullElement
import com.lightningkite.serialization.tryChildSerializers
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.SerialKind
import kotlinx.serialization.descriptors.StructureKind

object InlineFormRenderer : FormRenderer.Generator, ViewRenderer.Generator {
    override val name: String get() = "Inline Wrapper"
    override val basePriority: Float
        get() = 0.4f
    override val kind: SerialKind? = StructureKind.CLASS

    override fun matches(module: FormModule, selector: FormSelector<*>): Boolean {
        return selector.serializer.descriptor.isInline && super<FormRenderer.Generator>.matches(module, selector)
    }

    override fun size(module: FormModule, selector: FormSelector<*>): FormSize {
        val innerSerializer = selector.serializer.tryChildSerializers()!![0]!! as KSerializer<Any>
        val innerSelector = selector.copy(innerSerializer)
        val inner = module.form(innerSelector)
        return inner.size
    }

    @Suppress("UNCHECKED_CAST")
    override fun <T> form(module: FormModule, selector: FormSelector<T>): FormRenderer<T> {
        val innerSerializer = selector.serializer.tryChildSerializers()!![0]!! as KSerializer<Any>
        val innerSelector = selector.copy(innerSerializer)
        val inner = module.form(innerSelector)
        return FormRenderer(module, this, selector as FormSelector<Any?>) { field, writable ->
            inner.render(
                this,
                field,
                writable.lens(
                    get = { v -> selector.serializer.serializationCast(v, innerSerializer) },
                    set = { v ->
                        innerSerializer.serializationCast(v, selector.serializer)
                    },
                ),
            )
        } as FormRenderer<T>
    }

    @Suppress("UNCHECKED_CAST")
    override fun <T> view(module: FormModule, selector: FormSelector<T>): ViewRenderer<T> {
        val innerSerializer = selector.serializer.tryChildSerializers()!![0]!! as KSerializer<Any>
        val innerSelector = selector.copy(innerSerializer)
        val inner = module.view(innerSelector)
        return (ViewRenderer(module, this, selector as FormSelector<Any?>) { field, readable ->
            inner.render(
                this,
                field,
                readable.lens { v ->
                    selector.serializer.serializationCast(v, innerSerializer)
                },
            )
        } as ViewRenderer<T>)
    }
}