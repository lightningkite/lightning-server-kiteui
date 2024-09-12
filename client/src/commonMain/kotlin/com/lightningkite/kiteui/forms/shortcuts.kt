package com.lightningkite.kiteui.forms

import com.lightningkite.kiteui.reactive.Readable
import com.lightningkite.kiteui.reactive.Writable
import com.lightningkite.kiteui.views.ViewWriter
import com.lightningkite.serialization.SerializableProperty
import com.lightningkite.titleCase
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.SerialKind
import kotlinx.serialization.serializer

inline fun <reified V> ViewRenderer.Companion.forType(
    size: FormSize,
    serializer: KSerializer<V> = FormRenderer.module.serializer<V>(),
    name: String = serializer.descriptor.serialName.substringAfterLast('.').titleCase(),
    annotation: String? = null,
    priority: Float = 1f,
    crossinline generate: ViewWriter.(prop: Readable<V>)->Unit
) {
    plusAssign(object: ViewRenderer.Generator {
        override val annotation: String? = annotation
        override val type: String? = serializer.descriptor.serialName
        override val name: String = name
        override val basePriority: Float = priority
        override fun size(selector: FormSelector<*>): FormSize = size
        @Suppress("UNCHECKED_CAST")
        override fun <T> view(selector: FormSelector<T>): ViewRenderer<T> = ViewRenderer<V>(this, selector as FormSelector<V>) { field, readable ->
            generate(this, readable)
        } as ViewRenderer<T>
    })
}
inline fun <reified V> FormRenderer.Companion.forType(
    size: FormSize,
    serializer: KSerializer<V> = FormRenderer.module.serializer<V>(),
    name: String = serializer.descriptor.serialName.substringAfterLast('.').titleCase(),
    annotation: String? = null,
    priority: Float = 1f,
    crossinline generate: ViewWriter.(prop: Writable<V>)->Unit
) {
    plusAssign(object: FormRenderer.Generator {
        override val annotation: String? = annotation
        override val type: String? = serializer.descriptor.serialName
        override val name: String = name
        override val basePriority: Float = priority
        override fun size(selector: FormSelector<*>): FormSize = size
        @Suppress("UNCHECKED_CAST")
        override fun <T> form(selector: FormSelector<T>): FormRenderer<T> = FormRenderer<V>(this, selector as FormSelector<V>) { field, writable ->
            generate(this, writable)
        } as FormRenderer<T>
    })
}

inline fun <reified V> ViewRenderer.Companion.forType(
    crossinline size: (FormSelector<*>) -> FormSize,
    serializer: KSerializer<V> = FormRenderer.module.serializer<V>(),
    name: String = serializer.descriptor.serialName.substringAfterLast('.').titleCase(),
    annotation: String? = null,
    priority: Float = 1f,
    crossinline generate: ViewWriter.(prop: Readable<V>)->Unit
) {
    plusAssign(object: ViewRenderer.Generator {
        override val annotation: String? = annotation
        override val type: String? = serializer.descriptor.serialName
        override val name: String = name
        override val basePriority: Float = priority
        override fun size(selector: FormSelector<*>): FormSize = size(selector)
        @Suppress("UNCHECKED_CAST")
        override fun <T> view(selector: FormSelector<T>): ViewRenderer<T> = ViewRenderer<V>(this, selector as FormSelector<V>) { field, readable ->
            generate(this, readable)
        } as ViewRenderer<T>
    })
}
inline fun <reified V> FormRenderer.Companion.forType(
    crossinline size: (FormSelector<*>) -> FormSize,
    serializer: KSerializer<V> = FormRenderer.module.serializer<V>(),
    name: String = serializer.descriptor.serialName.substringAfterLast('.').titleCase(),
    annotation: String? = null,
    priority: Float = 1f,
    crossinline generate: ViewWriter.(prop: Writable<V>)->Unit
) {
    plusAssign(object: FormRenderer.Generator {
        override val annotation: String? = annotation
        override val type: String? = serializer.descriptor.serialName
        override val name: String = name
        override val basePriority: Float = priority
        override fun size(selector: FormSelector<*>): FormSize = size(selector)
        @Suppress("UNCHECKED_CAST")
        override fun <T> form(selector: FormSelector<T>): FormRenderer<T> = FormRenderer<V>(this, selector as FormSelector<V>) { field, writable ->
            generate(this, writable)
        } as FormRenderer<T>
    })
}

inline fun <reified V> ViewRenderer.Companion.forTypeWithField(
    size: FormSize,
    serializer: KSerializer<V> = FormRenderer.module.serializer<V>(),
    name: String = serializer.descriptor.serialName.substringAfterLast('.').titleCase(),
    annotation: String? = null,
    priority: Float = 1f,
    crossinline generate: ViewWriter.(field: SerializableProperty<*, *>?, prop: Readable<V>)->Unit
) {
    plusAssign(object: ViewRenderer.Generator {
        override val annotation: String? = annotation
        override val type: String? = serializer.descriptor.serialName
        override val name: String = name
        override val basePriority: Float = priority
        override fun size(selector: FormSelector<*>): FormSize = size
        @Suppress("UNCHECKED_CAST")
        override fun <T> view(selector: FormSelector<T>): ViewRenderer<T> = ViewRenderer<V>(this, selector as FormSelector<V>) { field, readable ->
            generate(this, field, readable)
        } as ViewRenderer<T>
        override val handlesField: Boolean = true
    })
}
inline fun <reified V> FormRenderer.Companion.forTypeWithField(
    size: FormSize,
    serializer: KSerializer<V> = FormRenderer.module.serializer<V>(),
    name: String = serializer.descriptor.serialName.substringAfterLast('.').titleCase(),
    annotation: String? = null,
    priority: Float = 1f,
    crossinline generate: ViewWriter.(field: SerializableProperty<*, *>?, prop: Writable<V>)->Unit
) {
    plusAssign(object: FormRenderer.Generator {
        override val annotation: String? = annotation
        override val type: String? = serializer.descriptor.serialName
        override val name: String = name
        override val basePriority: Float = priority
        override fun size(selector: FormSelector<*>): FormSize = size
        @Suppress("UNCHECKED_CAST")
        override fun <T> form(selector: FormSelector<T>): FormRenderer<T> = FormRenderer<V>(this, selector as FormSelector<V>) { field, writable ->
            generate(this, field, writable)
        } as FormRenderer<T>
        override val handlesField: Boolean = true
    })
}