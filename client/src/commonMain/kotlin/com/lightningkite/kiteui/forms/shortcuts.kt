@file:OptIn(ExperimentalSerializationApi::class)

package com.lightningkite.kiteui.forms

import com.lightningkite.kiteui.views.ViewModifiable
import com.lightningkite.kiteui.views.ViewWriter
import com.lightningkite.reactive.core.MutableReactive
import com.lightningkite.reactive.core.Reactive
import com.lightningkite.serialization.SerializableProperty
import com.lightningkite.titleCase
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.nonNullOriginal
import kotlinx.serialization.serializer

inline fun <reified V> FormModule.viewForType(
    size: FormSize,
    serializer: KSerializer<V> = module.serializer<V>(),
    name: String = (if(serializer.descriptor.isNullable) "Optional " else "") + serializer.descriptor.serialName.substringBefore('/').substringAfterLast('.').titleCase(),
    annotation: String? = null,
    priority: Float = 1f,
    crossinline generate: ViewWriter.(prop: Reactive<V>)->ViewModifiable
) {
    plusAssign(object: ViewRenderer.Generator {
        override val annotation: String? = annotation
        override val type: String? = serializer.descriptor.nonNullOriginal.serialName.substringBefore('/')
        override val nullable: Boolean = serializer.descriptor.isNullable
        override val name: String = name
        override val basePriority: Float = priority
        override fun size(module: FormModule, selector: FormSelector<*>): FormSize = size
        @Suppress("UNCHECKED_CAST")
        override fun <T> view(module: FormModule, selector: FormSelector<T>): ViewRenderer<T> = ViewRenderer<V>(module, this, selector as FormSelector<V>) { field, readable ->
            generate(this, readable)
        } as ViewRenderer<T>
    })
}
inline fun <reified V> FormModule.formForType(
    size: FormSize,
    serializer: KSerializer<V> = module.serializer<V>(),
    name: String = (if(serializer.descriptor.isNullable) "Optional " else "") + serializer.descriptor.serialName.substringBefore('/').substringAfterLast('.').titleCase(),
    annotation: String? = null,
    priority: Float = 1f,
    crossinline generate: ViewWriter.(prop: MutableReactive<V>)->ViewModifiable
) {
    plusAssign(object: FormRenderer.Generator {
        override val annotation: String? = annotation
        override val type: String? = serializer.descriptor.nonNullOriginal.serialName.substringBefore('/')
        override val nullable: Boolean = serializer.descriptor.isNullable
        override val name: String = name
        override val basePriority: Float = priority
        override fun size(module: FormModule, selector: FormSelector<*>): FormSize = size
        @Suppress("UNCHECKED_CAST")
        override fun <T> form(module: FormModule, selector: FormSelector<T>): FormRenderer<T> = FormRenderer<V>(module, this, selector as FormSelector<V>) { field, mutable ->
            generate(this, mutable)
        } as FormRenderer<T>
    })
}

inline fun <reified V> FormModule.viewForType(
    crossinline size: (FormSelector<*>) -> FormSize,
    serializer: KSerializer<V> = module.serializer<V>(),
    name: String = (if(serializer.descriptor.isNullable) "Optional " else "") + serializer.descriptor.serialName.substringBefore('/').substringAfterLast('.').titleCase(),
    annotation: String? = null,
    priority: Float = 1f,
    crossinline generate: ViewWriter.(prop: Reactive<V>)->ViewModifiable
) {
    plusAssign(object: ViewRenderer.Generator {
        override val annotation: String? = annotation
        override val type: String? = serializer.descriptor.nonNullOriginal.serialName.substringBefore('/')
        override val nullable: Boolean = serializer.descriptor.isNullable
        override val name: String = name
        override val basePriority: Float = priority
        override fun size(module: FormModule, selector: FormSelector<*>): FormSize = size(selector)
        @Suppress("UNCHECKED_CAST")
        override fun <T> view(module: FormModule, selector: FormSelector<T>): ViewRenderer<T> = ViewRenderer<V>(module, this, selector as FormSelector<V>) { field, readable ->
            generate(this, readable)
        } as ViewRenderer<T>
    })
}
inline fun <reified V> FormModule.formForType(
    crossinline size: (FormSelector<*>) -> FormSize,
    serializer: KSerializer<V> = module.serializer<V>(),
    name: String = (if(serializer.descriptor.isNullable) "Optional " else "") + serializer.descriptor.serialName.substringBefore('/').substringAfterLast('.').titleCase(),
    annotation: String? = null,
    priority: Float = 1f,
    crossinline generate: ViewWriter.(prop: MutableReactive<V>)->ViewModifiable
) {
    plusAssign(object: FormRenderer.Generator {
        override val annotation: String? = annotation
        override val type: String? = serializer.descriptor.nonNullOriginal.serialName.substringBefore('/')
        override val nullable: Boolean = serializer.descriptor.isNullable
        override val name: String = name
        override val basePriority: Float = priority
        override fun size(module: FormModule, selector: FormSelector<*>): FormSize = size(selector)
        @Suppress("UNCHECKED_CAST")
        override fun <T> form(module: FormModule, selector: FormSelector<T>): FormRenderer<T> = FormRenderer<V>(module, this, selector as FormSelector<V>) { field, mutable ->
            generate(this, mutable)
        } as FormRenderer<T>
    })
}

inline fun <reified V> FormModule.viewForTypeWithField(
    size: FormSize,
    serializer: KSerializer<V> = module.serializer<V>(),
    name: String = (if(serializer.descriptor.isNullable) "Optional " else "") + serializer.descriptor.serialName.substringBefore('/').substringAfterLast('.').titleCase(),
    annotation: String? = null,
    priority: Float = 1f,
    crossinline generate: ViewWriter.(field: SerializableProperty<*, *>?, prop: Reactive<V>)->ViewModifiable
) {
    plusAssign(object: ViewRenderer.Generator {
        override val annotation: String? = annotation
        override val type: String? = serializer.descriptor.nonNullOriginal.serialName.substringBefore('/')
        override val nullable: Boolean = serializer.descriptor.isNullable
        override val name: String = name
        override val basePriority: Float = priority
        override fun size(module: FormModule, selector: FormSelector<*>): FormSize = size
        @Suppress("UNCHECKED_CAST")
        override fun <T> view(module: FormModule, selector: FormSelector<T>): ViewRenderer<T> = ViewRenderer<V>(module, this, selector as FormSelector<V>) { field, readable ->
            generate(this, field, readable)
        } as ViewRenderer<T>
        override val handlesField: Boolean = true
    })
}
inline fun <reified V> FormModule.formForTypeWithField(
    size: FormSize,
    serializer: KSerializer<V> = module.serializer<V>(),
    name: String = (if(serializer.descriptor.isNullable) "Optional " else "") + serializer.descriptor.serialName.substringBefore('/').substringAfterLast('.').titleCase(),
    annotation: String? = null,
    priority: Float = 1f,
    crossinline generate: ViewWriter.(field: SerializableProperty<*, *>?, prop: MutableReactive<V>)->ViewModifiable
) {
    plusAssign(object: FormRenderer.Generator {
        override val annotation: String? = annotation
        override val type: String? = serializer.descriptor.nonNullOriginal.serialName.substringBefore('/')
        override val nullable: Boolean = serializer.descriptor.isNullable
        override val name: String = name
        override val basePriority: Float = priority
        override fun size(module: FormModule, selector: FormSelector<*>): FormSize = size
        @Suppress("UNCHECKED_CAST")
        override fun <T> form(module: FormModule, selector: FormSelector<T>): FormRenderer<T> = FormRenderer<V>(module, this, selector as FormSelector<V>) { field, mutable ->
            generate(this, field, mutable)
        } as FormRenderer<T>
        override val handlesField: Boolean = true
    })
}