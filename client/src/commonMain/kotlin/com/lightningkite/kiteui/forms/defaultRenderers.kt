package com.lightningkite.kiteui.forms

import com.lightningkite.kiteui.models.rem
import com.lightningkite.kiteui.reactive.*
import com.lightningkite.kiteui.views.*
import com.lightningkite.kiteui.views.direct.*
import com.lightningkite.lightningdb.*
import com.lightningkite.titleCase
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.internal.GeneratedSerializer


internal class EnumFormRenderer<T>(val serializer: KSerializer<T>) : FormRenderer<T> {
    override fun size(selector: FormSelector<T>): FormSize = FormSize.Small

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

    override fun render(
        writer: ViewWriter,
        selector: FormSelector<T>,
        field: SerializableProperty<*, *>?,
        writable: Writable<T>
    ) = with(writer) {
        defaultFieldWrapper(field) {
            fieldTheme - select {
                @Suppress("UNCHECKED_CAST")
                bind(
                    edits = writable,
                    data = Constant((serializer.nullElement() ?: serializer).enumValues().let {
                        if (serializer.descriptor.isNullable) listOf(null) + it else it
                    } as List<T>),
                    render = ::toDisplayName
                )
            }
        }
    }

    override fun renderReadOnly(
        writer: ViewWriter,
        selector: FormSelector<T>,
        field: SerializableProperty<*, *>?,
        readable: Readable<T>
    ) = with(writer) {
        defaultFieldWrapper(field) {
            text {
                ::content { toDisplayName(readable()) }
            }
        }
    }
}

enum class FieldVisibility { EDIT, READ, HIDDEN }

internal class StructFormRenderer<T>(val serializer: KSerializer<T>) : FormRenderer<T> {
    override fun size(selector: FormSelector<T>): FormSize = FormSize.Large
    private inner class Sub<S>(
        val field: SerializableProperty<T, S>,
        val renderer: FormRenderer<S>
    ) {
        val selector = FormSelector(field.serializer, field.serializableAnnotations)
        val size = renderer.size(selector)
        fun form(writer: ViewWriter, writable: Writable<T>) {
            println("RENDER ${field.name}")
            val w = writable.lensPath(
                DataClassPathAccess<T, T, S>(
                    DataClassPathSelf(serializer),
                    field
                )
            )
            if(field.visibility == FieldVisibility.READ)
                renderer.renderReadOnly(writer, selector, field, w)
            else
                renderer.render(writer, selector, field, w)
        }
        fun view(writer: ViewWriter, readable: Readable<T>) {
            println("RENDER ${field.name}")
            val r = readable.lens { field.get(it) }
            renderer.renderReadOnly(writer, selector, field, r)
        }
    }

    @Suppress("UNCHECKED_CAST")
    @OptIn(InternalSerializationApi::class, ExperimentalSerializationApi::class)
    fun bestPropertiesAttempt(): Array<SerializableProperty<T, *>> {
        val serializer = serializer
        if(serializer is GeneratedSerializer<*>) {
            return serializer.childSerializers().mapIndexed { index, it ->
                object: SerializableProperty<T, Any?> {
                    override val name: String = serializer.descriptor.getElementName(index)
                    override val serializer: KSerializer<Any?> = it as KSerializer<Any?>
                    override fun setCopy(receiver: T, value: Any?): T = (serializer as KSerializer<T>).set(receiver, index, it as KSerializer<Any?>, value)
                    override fun get(receiver: T): Any? = (serializer as KSerializer<T>).get(receiver, index, it)
                    override val serializableAnnotations: List<SerializableAnnotation> = serializer.descriptor.getElementAnnotations(index).mapNotNull { SerializableAnnotation.parseOrNull(it) }
                }
            }.toTypedArray()
        }
        return arrayOf()
    }

    @Suppress("UNCHECKED_CAST")
    private val subs = (serializer.serializableProperties ?: bestPropertiesAttempt()).map {
        Sub(
            it as SerializableProperty<T, Any?>,
            FormRenderer[FormSelector(it.serializer, it.serializableAnnotations)]
        )
    }.filter { it.field.visibility != FieldVisibility.HIDDEN }


    private val grouped: List<List<Sub<*>>> = run {
        val used = HashSet<Sub<*>>()
        val grouped = ArrayList<List<Sub<*>>>()
        subs.groupBy { it.field.group }.forEach { (group, fields) ->
            used += fields
            grouped += fields
        }
        grouped.flatMap {
            val m = ArrayList<List<Sub<*>>>()
            var current = ArrayList<Sub<*>>()
            for(item in it) {
                if(current.size >= 3) {
                    m.add(current)
                    current = ArrayList()
                }
                when(item.size) {
                    FormSize.Small -> current.add(item)
                    FormSize.Large -> {
                        if(current.isNotEmpty()) {
                            m.add(current)
                            current = ArrayList()
                        }
                        m.add(listOf(item))
                    }
                }
            }
            if(current.isNotEmpty()) {
                m.add(current)
            }
            m
        }.sortedBy { subs.indexOf(it[0]) }
    }

    override fun render(
        writer: ViewWriter,
        selector: FormSelector<T>,
        field: SerializableProperty<*, *>?,
        writable: Writable<T>
    ): Unit = with(writer) {
        defaultFieldWrapper(field) {
            if(field != null) card
            col {
                grouped.forEach {
                    if (it.size == 1) {
                        it[0].form(this, writable)
                    } else {
                        rowCollapsingToColumn(60.rem) {
                            it.forEach { expanding; it.form(this, writable) }
                        }
                    }
                }
            }
        }
    }

    override fun renderReadOnly(
        writer: ViewWriter,
        selector: FormSelector<T>,
        field: SerializableProperty<*, *>?,
        readable: Readable<T>
    ): Unit = with(writer) {
        defaultFieldWrapper(field) {
            if(field != null) card
            col {
                grouped.forEach {
                    if (it.size == 1) {
                        it[0].view(this, readable)
                    } else {
                        rowCollapsingToColumn(60.rem) {
                            it.forEach { expanding; it.view(this, readable) }
                        }
                    }
                }
            }
        }
    }
}

internal class WrapperFormRenderer<I, O>(val serializer: WrappingSerializer<I, O>): FormRenderer<I> {
    val innerSel = FormSelector(serializer.getDeferred(), listOf())
    val inner = FormRenderer[innerSel]
    override fun size(selector: FormSelector<I>): FormSize = inner.size(innerSel)
    override fun render(
        writer: ViewWriter,
        selector: FormSelector<I>,
        field: SerializableProperty<*, *>?,
        writable: Writable<I>
    ) {
        inner.render(writer, innerSel, field, writable.lens(get = serializer::inner, set = serializer::outer))
    }
    override fun renderReadOnly(
        writer: ViewWriter,
        selector: FormSelector<I>,
        field: SerializableProperty<*, *>?,
        readable: Readable<I>
    ) {
        inner.renderReadOnly(writer, innerSel, field, readable.lens(get = serializer::inner))
    }
}

internal class MySealedFormRenderer<T: Any>(val serializer: MySealedClassSerializerInterface<T>) : FormRenderer<T> {
    override fun render(
        writer: ViewWriter,
        selector: FormSelector<T>,
        field: SerializableProperty<*, *>?,
        writable: Writable<T>
    ): Unit = with(writer) {
        val type = writable.lens(
            get = { serializer.options.find { o -> o.isInstance(it) } ?: serializer.options.first() },
            set = { it.serializer.default() }
        )

        defaultFieldWrapper(field) {
            row {
                atTop - sizeConstraints(width = 10.rem) - select {
                    bind(type, Constant(serializer.options)) { it.serializer.displayName }
                }
                expanding - stack {
                    reactive {
                        val type = type()
                        clearChildren()
                        @Suppress("UNCHECKED_CAST")
                        form(type.serializer as KSerializer<Any>, writable.lens(
                            get = { if(type.isInstance(it)) it else type.serializer.default() },
                            set = { it as T }
                        ))
                    }
                }
            }
        }
    }
    override fun renderReadOnly(
        writer: ViewWriter,
        selector: FormSelector<T>,
        field: SerializableProperty<*, *>?,
        readable: Readable<T>
    ): Unit = with(writer) {
        val type = readable.lens(
            get = { serializer.options.find { o -> o.isInstance(it) } ?: serializer.options.first() },
        )

        defaultFieldWrapper(field) {
            row {
                atTop - text { ::content { type().serializer.displayName } }
                expanding - stack {
                    reactive {
                        val type = type()
                        clearChildren()
                        @Suppress("UNCHECKED_CAST")
                        view(type.serializer as KSerializer<Any>, readable.lens(
                            get = { if(type.isInstance(it)) it else type.serializer.default() },
                        ))
                    }
                }
            }
        }
    }

}

internal class ObjectFormRenderer<T>(val serializer: KSerializer<T>): FormRenderer<T> {
    override fun render(
        writer: ViewWriter,
        selector: FormSelector<T>,
        field: SerializableProperty<*, *>?,
        writable: Writable<T>
    ): Unit = with(writer) {
        // No op; type determined at this point
        stack {}
//        defaultFieldWrapper(field) { text(serializer.displayName) }
    }
}

internal class NullableFormRenderer<T>(val serializer: KSerializer<T>) : FormRenderer<T?> {
    override fun size(selector: FormSelector<T?>): FormSize {
        @Suppress("UNCHECKED_CAST")
        val selector = selector.copy(serializer.nullElement()!! as KSerializer<T>)
        return FormRenderer.get(selector).size(selector)
    }

    override fun render(
        writer: ViewWriter,
        selector: FormSelector<T?>,
        field: SerializableProperty<*, *>?,
        writable: Writable<T?>
    ) = with(writer) {
        val selector = selector.copy(serializer.nullElement()!! as KSerializer<T>)
        val it = selector.serializer
        defaultFieldWrapper(field) {
            row {
                var ifNotNull: T = writable.state.getOrNull() ?: it.default()
                checkbox {
                    checked bind writable.lens(
                        get = { v -> v != null },
                        modify = { e, v ->
                            if (v) (ifNotNull ?: e ?: it.default()) else null
                        },
                    )
                }
                expanding - onlyWhen { writable() != null } - form(
                    serializer = it,
                    writable = writable.lens(
                        get = { v -> v ?: it.default() },
                        modify = { e, v ->
                            ifNotNull = v
                            if (e == null) null else v
                        },
                    ),
                    annotations = selector.annotations
                )
            }
        }
    }

    override fun renderReadOnly(
        writer: ViewWriter,
        selector: FormSelector<T?>,
        field: SerializableProperty<*, *>?,
        readable: Readable<T?>
    ): Unit = with(writer) {
        val selector = selector.copy(serializer.nullElement()!! as KSerializer<T>)
        defaultFieldWrapper(field) {
            stack {
                val isNull = shared { readable() == null }
                reactiveScope {
                    clearChildren()
                    if (isNull()) {
                        text("N/A")
                    } else {
                        FormRenderer[selector].renderReadOnly(
                            this@stack,
                            selector,
                            field,
                            readable.lens { it ?: selector.serializer.default() })
                    }
                }
            }
        }
    }
}
