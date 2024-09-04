package com.lightningkite.kiteui.forms

import com.lightningkite.kiteui.models.HeaderSizeSemantic
import com.lightningkite.kiteui.models.SubtextSemantic
import com.lightningkite.kiteui.models.px
import com.lightningkite.kiteui.models.rem
import com.lightningkite.kiteui.reactive.Readable
import com.lightningkite.kiteui.reactive.Writable
import com.lightningkite.kiteui.reactive.lens
import com.lightningkite.kiteui.views.*
import com.lightningkite.kiteui.views.direct.*
import com.lightningkite.serialization.*
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.StructureKind
import kotlinx.serialization.internal.GeneratedSerializer

enum class FieldVisibility { EDIT, READ, HIDDEN }

object ByFieldRenderer : FormRenderer.Generator, ViewRenderer.Generator {
    override val name: String = "By Field"
    override val basePriority: Float
        get() = 0.7f
    override val kind = StructureKind.CLASS
    override val size: FormSize = FormSize.Block
    override fun <T> form(selector: FormSelector<T>): FormRenderer<T> {
        val info = TypeInfo(selector.serializer)
        return FormRenderer<T>(this, selector) { field, writable ->
            if (field != null) card
            col {
                info.grouped.forEach {
                    if (it.size == 1) {
                        it[0].form(this, writable)
                    } else {
                        rowCollapsingToColumn(60.rem) {
                            it.forEach {
                                expanding
                                it.form(this, writable)
                            }
                        }
                    }
                }
            }
        }
    }

    override fun <T> view(selector: FormSelector<T>): ViewRenderer<T> {
        val info = TypeInfo(selector.serializer)
        return ViewRenderer<T>(this, selector) { field, readable ->
            if (field != null) card
            col {
                info.grouped.forEach {
                    if (it.size == 1) {
                        it[0].view(this, readable)
                    } else {
                        rowCollapsingToColumn(60.rem) {
                            it.forEach {
                                expanding
                                it.view(this, readable)
                            }
                        }
                    }
                }
            }
        }
    }

    private class TypeInfo<T>(val serializer: KSerializer<T>) {
        inner class Sub<S>(
            val field: SerializableProperty<T, S>,
            val form: FormRenderer<S>,
            val view: ViewRenderer<S>,
        ) {

            inline fun f(viewWriter: ViewWriter, render: Renderer<*>, inner: ViewWriter.()->Unit) = with(viewWriter) {
                if(render.handlesField) {
                    inner()
                } else {
                    field?.importance?.let {
                        when (it) {
                            in 1..6 -> HeaderSizeSemantic(it).onNext
                            7 -> {}
                            8 -> SubtextSemantic.onNext
                            else -> {}
                        }
                    }
                    if (field == null || field.doesNotNeedLabel) inner()
                    else field.sentence?.let {
                        val before = it.substringBefore('_')
                        val after = it.substringAfter('_')
                        atBottom - row {
                            spacing = 0.3.rem
                            if (before.isNotBlank()) {
                                centered - text(before)
                            }
                            if (before.isBlank() || after.isBlank()) expanding
                            inner()
                            if (after.isNotBlank()) {
                                centered - text(after)
                            }
                        }
                    } ?: col {
                        spacing = 0.px
                        subtext(field.displayName)
                        inner()
                    }
                }
            }
            val selector = FormSelector(field.serializer, field.serializableAnnotations)
            fun form(writer: ViewWriter, writable: Writable<T>) {
                val w = writable.lensPath(
                    DataClassPathAccess<T, T, S>(
                        DataClassPathSelf(serializer),
                        field
                    )
                )
                if (field.visibility == FieldVisibility.READ)
                    f(writer, view) { view.render(this, field, w) }
                else
                    f(writer, form) { form.render(this, field, w) }
            }

            fun view(writer: ViewWriter, readable: Readable<T>) {
                val r = readable.lens { field.get(it) }
                f(writer, view) { view.render(writer, field, r) }
            }
        }

        @Suppress("UNCHECKED_CAST")
        @OptIn(InternalSerializationApi::class, ExperimentalSerializationApi::class)
        fun bestPropertiesAttempt(): Array<SerializableProperty<T, *>> {
            val serializer = serializer
            if (serializer is GeneratedSerializer<*>) {
                return serializer.childSerializers().mapIndexed { index, it ->
                    object : SerializableProperty<T, Any?> {
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
        val subs = (serializer.serializableProperties ?: bestPropertiesAttempt()).map {
            val sel = FormSelector(it.serializer, it.serializableAnnotations) as FormSelector<Any?>
            Sub(
                it as SerializableProperty<T, Any?>,
                FormRenderer[sel],
                ViewRenderer[sel],
            )
        }.filter { it.field.visibility != FieldVisibility.HIDDEN }


        val grouped: List<List<Sub<*>>> = run {
            val used = HashSet<Sub<*>>()
            val grouped = ArrayList<List<Sub<*>>>()
            subs.groupBy { it.field.group }.forEach { (group, fields) ->
                used += fields
                grouped += fields
            }
            grouped.flatMap {
                val m = ArrayList<List<Sub<*>>>()
                var current = ArrayList<Sub<*>>()
                for (item in it) {
                    if (current.size >= 3) {
                        m.add(current)
                        current = ArrayList()
                    }
                    when (item.form.size) {
                        FormSize.Inline -> current.add(item)
                        FormSize.Block -> {
//                        if(current.isNotEmpty()) {
//                            m.add(current)
//                            current = ArrayList()
//                        }
                            m.add(listOf(item))
                        }
                    }
                }
                if (current.isNotEmpty()) {
                    m.add(current)
                }
                m
            }.sortedBy { subs.indexOf(it[0]) }
        }
    }
}

