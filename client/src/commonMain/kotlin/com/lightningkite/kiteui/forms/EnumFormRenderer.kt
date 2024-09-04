package com.lightningkite.kiteui.forms

import com.lightningkite.kiteui.reactive.Constant
import com.lightningkite.kiteui.reactive.Readable
import com.lightningkite.kiteui.reactive.Writable
import com.lightningkite.kiteui.reactive.invoke
import com.lightningkite.kiteui.views.ViewWriter
import com.lightningkite.kiteui.views.direct.select
import com.lightningkite.kiteui.views.direct.text
import com.lightningkite.kiteui.views.fieldTheme
import com.lightningkite.serialization.*
import com.lightningkite.titleCase
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.SerialKind

object EnumFormRenderer: FormRenderer.Generator, ViewRenderer.Generator {
    override val name: String = "Enum"
    override val basePriority: Float
        get() = 0.7f
    override val kind = SerialKind.ENUM
    class TypeInfo<T>(val serializer: KSerializer<T>) {
        fun toDisplayName(it: T): String {
            return if (it == null) "N/A"
            else (it as? VirtualEnumValue)?.let {
                it.enum.options[it.index].let {
                    it.annotations.find { it.fqn == "com.lightningkite.lightningdb.DisplayName" }?.values?.get(
                        "text"
                    )?.let { it as? SerializableAnnotationValue.StringValue }?.value ?: it.name.titleCase()
                }
            } ?: (it as? Enum<*>)?.let {
                serializer.getElementSerializableAnnotations(it.ordinal)
                    .find { it.fqn == "com.lightningkite.lightningdb.DisplayName" }?.values?.get(
                        "text"
                    )?.let { it as? SerializableAnnotationValue.StringValue }?.value ?: it.name.titleCase()
            } ?: it.toString().titleCase()
        }
    }
    override fun <T> form(selector: FormSelector<T>): FormRenderer<T> {
        val info = TypeInfo(selector.serializer)
        return FormRenderer(this, selector) { _, writable ->
            fieldTheme - select {
                @Suppress("UNCHECKED_CAST")
                bind(
                    edits = writable,
                    data = Constant((info.serializer.nullElement() ?: info.serializer).enumValues().let {
                        if (info.serializer.descriptor.isNullable) listOf(null) + it else it
                    } as List<T>),
                    render = info::toDisplayName
                )
            }
        }
    }
    override fun <T> view(selector: FormSelector<T>): ViewRenderer<T> {
        val info = TypeInfo(selector.serializer)
        return ViewRenderer(this, selector) { _, readable ->
            text {
                ::content { info.toDisplayName(readable.invoke()) }
            }
        }
    }
}