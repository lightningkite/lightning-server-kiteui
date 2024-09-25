package com.lightningkite.kiteui.forms

import com.lightningkite.kiteui.models.HeaderSizeSemantic
import com.lightningkite.kiteui.models.SubtextSemantic
import com.lightningkite.kiteui.models.px
import com.lightningkite.kiteui.models.rem
import com.lightningkite.kiteui.reactive.AppState
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
    override fun matches(module: FormModule, selector: FormSelector<*>): Boolean {
        return super<FormRenderer.Generator>.matches(module, selector) && !selector.serializer.descriptor.isNullable
    }
    override fun size(module: FormModule, selector: FormSelector<*>): FormSize {
        val info = TypeInfo(module, selector.serializer, selector.desiredSize.approximateWidthBound ?: (AppState.windowInfo.value.width.px / 1.rem.px))
        return FormSize(
            selector.desiredSize.approximateWidthBound ?:  FormSize.Block.approximateWidth,
            info.viewApproximateHeight
        )
    }
    override fun <T> form(module: FormModule, selector: FormSelector<T>): FormRenderer<T> {
        val info = TypeInfo(module, selector.serializer, selector.desiredSize.approximateWidthBound ?: (AppState.windowInfo.value.width.px / 1.rem.px))
        return FormRenderer<T>(module, this, selector) { field, writable ->
            if (field != null) card
            col {
//                text("Available width: ${info.availableWidth} ${info.formGroup.map { it.size }}")
                info.formGroup.forEach {
                    if (it.size == 1) {
                        it[0].form(this, writable)
                    } else {
                        row {
                            it.forEach {
                                weight(it.formSize.approximateWidth.toFloat())
                                it.form(this, writable)
                            }
                        }
                    }
                }
            }
        }
    }

    override fun <T> view(module: FormModule, selector: FormSelector<T>): ViewRenderer<T> {
        val info = TypeInfo(module, selector.serializer, selector.desiredSize.approximateWidthBound ?: (AppState.windowInfo.value.width.px / 1.rem.px))
        return ViewRenderer<T>(module, this, selector) { field, readable ->
            if (field != null) card
            col {
//                text("Available width: ${info.availableWidth} ${info.viewGroup.map { it.size }}")
                info.viewGroup.forEach {
                    if (it.size == 1) {
                        it[0].view(this, readable)
                    } else {
                        row {
                            it.forEach {
                                weight(it.viewSize.approximateWidth.toFloat())
                                it.view(this, readable)
                            }
                        }
                    }
                }
            }
        }
    }

    private class TypeInfo<T>(val module: FormModule, val serializer: KSerializer<T>, val availableWidth: Double = AppState.windowInfo.value.width.px / 1.rem.px) {
        inner class Sub<S>(
            val field: SerializableProperty<T, S>,
            val form: FormRenderer<S>,
            val view: ViewRenderer<S>,
        ) {
            val formSize = field.sentence?.let {
                form.size.copy(approximateWidth = (form.size.approximateWidth + it.length * 3 / 4) * (HeaderSizeSemantic.lookup.getOrNull(field.importance - 1) ?: 0.8))
            } ?: form.size.copy(approximateWidth = (form.size.approximateWidth) * (HeaderSizeSemantic.lookup.getOrNull(field.importance - 1) ?: 0.75))
            val viewSize = field.sentence?.let {
                view.size.copy(approximateWidth = (view.size.approximateWidth + it.length * 3 / 4) * (HeaderSizeSemantic.lookup.getOrNull(field.importance - 1) ?: 0.8))
            } ?: view.size.copy(approximateWidth = (view.size.approximateWidth) * (HeaderSizeSemantic.lookup.getOrNull(field.importance - 1) ?: 0.8))

            inline fun f(viewWriter: ViewWriter, render: Renderer<*>, inner: ViewWriter.()->Unit) = with(viewWriter) {
                field.importance.let {
                    when (it) {
                        in 1..6 -> HeaderSizeSemantic(it).onNext
                        7 -> {}
                        8 -> SubtextSemantic.onNext
                        else -> {}
                    }
                }
                if(render.handlesField || field.doesNotNeedLabel) {
                    inner(this)
                } else {
                    field.sentence?.let {
                        val before = it.substringBefore('_')
                        val after = it.substringAfter('_')
                        row {
                            spacing = 0.3.rem
                            if (before.isNotBlank()) {
                                centered - text(before)
                            }
//                            if (before.isBlank() || after.isBlank()) expanding
                            centered - inner(this)
                            if (after.isNotBlank()) {
                                centered - text(after)
                            }
                        }
                    } ?: col {
                        spacing = 0.px
                        subtext(field.displayName)
                        inner(this)
                    }
                }
            }
            fun form(writer: ViewWriter, writable: Writable<T>) {
                val w = writable.lensPath(
                    DataClassPathAccess<T, T, S>(
                        DataClassPathSelf(serializer),
                        field
                    )
                )
                if (field.visibility == FieldVisibility.READ)
                    f(writer, view) {
                        view.render(this, field, w)
                    }
                else
                    f(writer, form) {
                        form.render(this, field, w)
                    }
            }

            fun view(writer: ViewWriter, readable: Readable<T>) {
                val r = readable.lens { field.get(it) }
                f(writer, view) { view.render(this@f, field, r) }
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
            val sel = FormSelector(it.serializer, it.serializableAnnotations, FormLayoutPreferences(availableWidth - 2.0, 10.0)) as FormSelector<Any?>
            Sub(
                it as SerializableProperty<T, Any?>,
                module.form(sel),
                module.view(sel),
            )
        }.filter { it.field.visibility != FieldVisibility.HIDDEN }


        val formGroup: List<List<Sub<*>>> = run {
            val used = HashSet<Sub<*>>()
            val grouped = ArrayList<List<Sub<*>>>()
            subs.groupBy { it.field.group }.forEach { (group, fields) ->
                used += fields
                grouped += fields
            }
            grouped.flatMap {
                val m = ArrayList<List<Sub<*>>>()
                var current = ArrayList<Sub<*>>()
                var currentTotal = 0.0
                for (item in it) {
                    if(item.field.importance < 7)
                        m.add(listOf(item))
                    else  {
                        val w = item.formSize.approximateWidth
                        if (currentTotal != 0.0 && currentTotal + w >= availableWidth) {
                            m.add(current)
                            current = ArrayList()
                            currentTotal = 0.0
                        }
                        current.add(item)
                        currentTotal += w
                    }
                }
                if (current.isNotEmpty()) {
                    m.add(current)
                }
                m
            }.sortedBy { subs.indexOf(it[0]) }
        }
        val viewGroup: List<List<Sub<*>>> = run {
            val used = HashSet<Sub<*>>()
            val grouped = ArrayList<List<Sub<*>>>()
            subs.groupBy { it.field.group }.forEach { (group, fields) ->
                used += fields
                grouped += fields
            }
            grouped.flatMap {
                val m = ArrayList<List<Sub<*>>>()
                var current = ArrayList<Sub<*>>()
                var currentTotal = 0.0
                for (item in it) {
                    if(item.field.importance < 7)
                        m.add(listOf(item))
                    else  {
                        val w = item.formSize.approximateWidth
                        if (currentTotal != 0.0 && currentTotal + w >= availableWidth) {
                            m.add(current)
                            current = ArrayList()
                            currentTotal = 0.0
                        }
                        current.add(item)
                        currentTotal += w
                    }
                }
                if (current.isNotEmpty()) {
                    m.add(current)
                }
                m
            }.sortedBy { subs.indexOf(it[0]) }
        }

        val formApproximateHeight = formGroup.sumOf { it.maxOfOrNull { it.formSize.approximateHeight } ?: 0.0 }
        val viewApproximateHeight = viewGroup.sumOf { it.maxOfOrNull { it.viewSize.approximateHeight } ?: 0.0 }
    }
}
