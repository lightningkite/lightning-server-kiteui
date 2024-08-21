package com.lightningkite.kiteui.forms

import com.lightningkite.kiteui.models.rem
import com.lightningkite.kiteui.reactive.*
import com.lightningkite.kiteui.views.ViewWriter
import com.lightningkite.kiteui.views.card
import com.lightningkite.kiteui.views.direct.*
import com.lightningkite.kiteui.views.expanding
import com.lightningkite.kiteui.views.fieldTheme
import com.lightningkite.lightningdb.*
import com.lightningkite.titleCase
import kotlinx.serialization.KSerializer


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
            val r = readable.lens { field.get(it) }
            renderer.renderReadOnly(writer, selector, field, r)
        }
    }

    @Suppress("UNCHECKED_CAST")
    private val subs = serializer.serializableProperties?.map {
        Sub(
            it as SerializableProperty<T, Any?>,
            FormRenderer[FormSelector(it.serializer, it.serializableAnnotations)]
        )
    }?.filter { it.field.visibility != FieldVisibility.HIDDEN } ?: listOf()


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
                            this,
                            selector,
                            field,
                            readable.lens { it ?: selector.serializer.default() })
                    }
                }
            }
        }
    }
}
