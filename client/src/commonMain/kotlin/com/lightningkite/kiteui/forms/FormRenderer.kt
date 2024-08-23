@file:OptIn(ExperimentalSerializationApi::class)

package com.lightningkite.kiteui.forms

import com.lightningkite.kiteui.reactive.Readable
import com.lightningkite.kiteui.reactive.Writable
import com.lightningkite.kiteui.reactive.withWrite
import com.lightningkite.kiteui.views.ViewWriter
import com.lightningkite.lightningdb.*
import kotlinx.serialization.ContextualSerializer
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.SerialKind
import kotlinx.serialization.descriptors.StructureKind
import kotlinx.serialization.serializer

interface FormRenderer<T> {
    fun size(selector: FormSelector<T>): FormSize = FormSize.Large

    fun render(
        writer: ViewWriter,
        selector: FormSelector<T>,
        field: SerializableProperty<*, *>?,
        writable: Writable<T>
    ): Unit

    fun renderReadOnly(
        writer: ViewWriter,
        selector: FormSelector<T>,
        field: SerializableProperty<*, *>?,
        readable: Readable<T>
    ): Unit = render(writer, selector, field, readable.withWrite {})

    companion object {
        var module = ClientModule
        val forAnnotation = HashMap<String, FormRenderer<*>>()
        val forSerialName = HashMap<String, FormRenderer<*>>()
        val forSerialNameAndAnnotation = HashMap<Pair<String, String>, FormRenderer<*>>()

        init {
            builtins()
        }

        @Suppress("UNCHECKED_CAST")
        fun getAny(selector: FormSelector<*>): FormRenderer<Any?> {
            return get(selector) as FormRenderer<Any?>
        }

        @Suppress("UNCHECKED_CAST")
        operator fun <T> get(selector: FormSelector<T>): FormRenderer<T> {
            val serialName = selector.serializer.descriptor.serialName
            selector.annotations.forEach {
                forSerialNameAndAnnotation[serialName to it.fqn]?.let { return it as FormRenderer<T> }
            }
            selector.annotations.forEach {
                forAnnotation[it.fqn]?.let { return it as FormRenderer<T> }
            }
            forSerialName[serialName]?.let { return it as FormRenderer<T> }

            if (selector.serializer.descriptor.kind == SerialKind.ENUM)
                return EnumFormRenderer(selector.serializer)

            if (selector.serializer.descriptor.isNullable) {
                return NullableFormRenderer(selector.serializer as KSerializer<Any>) as FormRenderer<T>
            }

            (selector.serializer as? WrappingSerializer<*, *>)?.let {
                return WrapperFormRenderer(it) as FormRenderer<T>
            }

            (selector.serializer as? MySealedClassSerializerInterface<*>)?.let {
                return MySealedFormRenderer(it) as FormRenderer<T>
            }

            if (selector.serializer.descriptor.kind == StructureKind.CLASS) {
                return StructFormRenderer(selector.serializer)
            }
            if (selector.serializer.descriptor.kind == StructureKind.OBJECT) {
                return ObjectFormRenderer(selector.serializer)
            }

            println("Could not find or create form renderer for ${selector.serializer.descriptor.serialName}")
            throw IllegalArgumentException("Could not find or create form renderer for ${selector.serializer.descriptor.serialName}")
        }
    }
}

class FormSelector<T>(
    serializer: KSerializer<T>,
    val annotations: List<SerializableAnnotation>
) {
    @Suppress("UNCHECKED_CAST")
    val serializer = run {
        if (serializer is ContextualSerializer<*>) FormRenderer.module.getContextual(serializer) as KSerializer<T>
        else serializer
    }

    @Suppress("UNCHECKED_CAST")
    fun <O> copy(
        serializer: KSerializer<O>,
        annotations: List<SerializableAnnotation> = this.annotations,
    ) = FormSelector<O>(
        serializer = serializer,
        annotations = annotations
    )
}

fun <T> ViewWriter.form(
    serializer: KSerializer<T>,
    writable: Writable<T>,
    annotations: List<SerializableAnnotation> = serializer.serializableAnnotations,
    field: SerializableProperty<*, *>? = null
) {
    val sel = FormSelector<T>(serializer, annotations)
    FormRenderer[sel].render(this, sel, field, writable)
}
fun <T> ViewWriter.view(
    serializer: KSerializer<T>,
    readable: Readable<T>,
    annotations: List<SerializableAnnotation> = serializer.serializableAnnotations,
    field: SerializableProperty<*, *>? = null
) {
    val sel = FormSelector<T>(serializer, annotations)
    FormRenderer[sel].renderReadOnly(this, sel, field, readable)
}