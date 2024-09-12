@file:OptIn(ExperimentalSerializationApi::class)

package com.lightningkite.kiteui.forms

import com.lightningkite.kiteui.forms.ViewRenderer.Companion
import com.lightningkite.kiteui.models.*
import com.lightningkite.kiteui.reactive.*
import com.lightningkite.kiteui.views.RView
import com.lightningkite.kiteui.views.ViewWriter
import com.lightningkite.kiteui.views.atTopEnd
import com.lightningkite.kiteui.views.direct.*
import com.lightningkite.serialization.*
import kotlinx.serialization.ContextualSerializer
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.SerialKind

interface RendererGenerator {
    val name: String
    val kind: SerialKind? get() = null
    val type: String? get() = null
    val annotation: String? get() = null
    val handlesField: Boolean get() = false
    fun size(selector: FormSelector<*>): FormSize = FormSize.Inline
    val basePriority: Float get() = 1f
    fun priority(selector: FormSelector<*>): Float {
        var amount = basePriority
        if (handlesField && selector.handlesField) amount *= 1.2f
        size(selector).let { size ->
            val widthOverage = ((size.approximateWidth - (selector.desiredSize.approximateWidthBound ?: 1000.0)) / (selector.desiredSize.approximateWidthBound ?: 1000.0)).coerceAtLeast(0.0)
            val heightOverage = ((size.approximateHeight - (selector.desiredSize.approximateHeightBound ?: 1000.0)) / (selector.desiredSize.approximateHeightBound ?: 1000.0)).coerceAtLeast(0.0)
            amount *= 1 / (1f + widthOverage.toFloat())
            amount *= 1 / (1f + heightOverage.toFloat())
        }
        return amount
    }
    fun matches(selector: FormSelector<*>): Boolean {
        if (type != null && selector.serializer.descriptor.serialName != type) return false
        if (kind != null && selector.serializer.descriptor.kind != kind) return false
        if (annotation != null && selector.annotations.none { it.fqn == annotation }) return false
        return true
    }
}

interface Renderer<T> {
    val generator: RendererGenerator?
    val selector: FormSelector<T>
    val size: FormSize
    val handlesField: Boolean
}

data class ViewRenderer<T>(
    override val generator: ViewRenderer.Generator?,
    override val selector: FormSelector<T>,
    override val size: FormSize = generator!!.size(selector),
    override val handlesField: Boolean = generator!!.handlesField,
    val render: ViewWriter.(field: SerializableProperty<*, *>?, readable: Readable<T>) -> Unit
): Renderer<T> {
    interface Generator: RendererGenerator {
        fun <T> view(selector: FormSelector<T>): ViewRenderer<T>
    }

    companion object {
        private val others: ArrayList<Generator> = ArrayList()
        private val type: HashMap<String, ArrayList<Generator>> = HashMap()
        private val kind: HashMap<SerialKind, ArrayList<Generator>> = HashMap()
        private val annotation: HashMap<String, ArrayList<Generator>> = HashMap()
        fun <T> candidates(key: FormSelector<T>): Sequence<Generator> = sequence {
            type[key.serializer.descriptor.serialName]?.let { yieldAll(it) }
            kind[key.serializer.descriptor.kind]?.let { yieldAll(it) }
            key.annotations.forEach {  anno ->
                annotation[anno.fqn]?.let { yieldAll(it) }
            }
            yieldAll(others)
        }
        operator fun <T> get(key: FormSelector<T>): ViewRenderer<T> {
            val options = candidates(key).filter { it.matches(key) }.sortedByDescending { it.priority(key) }.map { it.view(key) }.toList()
            if(!key.withPicker) return options.first()
            return ViewRenderer(null, key, size = options.first().size, handlesField = options.first().handlesField) { field, readable ->
                val selected = Property(options.first())
                stack {
                    stack {
                        reactive {
                            val sel = selected()
                            clearChildren()
                            sel.render(this@stack, field, readable)
                        }
                    }
                    sizeConstraints(width = 0.75.rem, height = 0.75.rem) - SubtextSemantic.onNext - atTopEnd - select {
                        spacing = 0.px
                        bind(selected, Constant(options)) { (it.generator?.name ?: "-") + " (${it.generator?.priority(key)}, ${it.size.approximateWidth} x ${it.size.approximateHeight})" }
                    }
                }
            }
        }
        operator fun plusAssign(generator: Generator) {
            generator.annotation?.let { annotation.getOrPut(it) { ArrayList() }.add(generator) }
                ?: generator.type?.let { type.getOrPut(it) { ArrayList() }.add(generator) }
                ?: generator.kind?.let { kind.getOrPut(it) { ArrayList() }.add(generator) }
                ?: others.add(generator)
        }

        init {
            BuiltinRendering
        }
    }
}

data class FormRenderer<T>(
    override val generator: FormRenderer.Generator?,
    override val selector: FormSelector<T>,
    override val size: FormSize = generator!!.size(selector),
    override val handlesField: Boolean = generator!!.handlesField,
    val render: ViewWriter.(field: SerializableProperty<*, *>?, writable: Writable<T>) -> Unit
): Renderer<T> {
    interface Generator: RendererGenerator {
        fun <T> form(selector: FormSelector<T>): FormRenderer<T>
    }

    companion object {
        var module = ClientModule

        private val others: ArrayList<Generator> = ArrayList()
        private val type: HashMap<String, ArrayList<Generator>> = HashMap()
        private val kind: HashMap<SerialKind, ArrayList<Generator>> = HashMap()
        private val annotation: HashMap<String, ArrayList<Generator>> = HashMap()
        fun <T> candidates(key: FormSelector<T>): Sequence<Generator> = sequence {
            type[key.serializer.descriptor.serialName]?.let { yieldAll(it) }
            kind[key.serializer.descriptor.kind]?.let { yieldAll(it) }
            key.annotations.forEach {  anno ->
                annotation[anno.fqn]?.let {
                    yieldAll(it)
                }
            }
            yieldAll(others)
        }
        operator fun <T> get(key: FormSelector<T>): FormRenderer<T> {
            val options = candidates(key).filter { it.matches(key) }.sortedByDescending { it.priority(key) }.map { it.form(key) }.toList()
            if(!key.withPicker) return options.first()
            return FormRenderer(null, key, size = options.first().size, handlesField = options.first().handlesField) { field, writable ->
                val selected = Property(options.first())
                stack {
                    stack {
                        reactive {
                            val sel = selected()
                            clearChildren()
                            sel.render(this@stack, field, writable)
                        }
                    }
                    sizeConstraints(width = 0.75.rem, height = 0.75.rem) - SubtextSemantic.onNext - atTopEnd - select {
                        spacing = 0.px
                        bind(selected, Constant(options)) { (it.generator?.name ?: "-") + " (${it.generator?.priority(key)}, ${it.size.approximateWidth} x ${it.size.approximateHeight})" }
                    }
                }
            }
        }
        operator fun plusAssign(generator: Generator) {
            generator.annotation?.let { annotation.getOrPut(it) { ArrayList() }.add(generator) }
                ?: generator.type?.let { type.getOrPut(it) { ArrayList() }.add(generator) }
                ?: generator.kind?.let { kind.getOrPut(it) { ArrayList() }.add(generator) }
                ?: others.add(generator)
        }

        init {
            BuiltinRendering
        }
    }
}

class FormSelector<T>(
    serializer: KSerializer<T>,
    val annotations: List<SerializableAnnotation>,
    val desiredSize: FormLayoutPreferences = FormLayoutPreferences.Block,
    val handlesField: Boolean = false,
    val withPicker: Boolean = true,
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
        desiredSize: FormLayoutPreferences = this.desiredSize,
        handlesField: Boolean = this.handlesField,
        withPicker: Boolean = this.withPicker,
    ) = FormSelector<O>(
        serializer = serializer,
        annotations = annotations,
        desiredSize = desiredSize,
        handlesField = handlesField,
        withPicker = withPicker
    )
}

fun <T> ViewWriter.form(
    serializer: KSerializer<T>,
    writable: Writable<T>,
    annotations: List<SerializableAnnotation> = serializer.serializableAnnotations,
    desiredSize: FormLayoutPreferences = FormLayoutPreferences.Unbound,
    field: SerializableProperty<*, *>? = null
) {
    val sel = FormSelector<T>(serializer, annotations, desiredSize)
    FormRenderer[sel].render(this, field, writable)
}

fun <T> ViewWriter.view(
    serializer: KSerializer<T>,
    readable: Readable<T>,
    annotations: List<SerializableAnnotation> = serializer.serializableAnnotations,
    desiredSize: FormLayoutPreferences = FormLayoutPreferences.Unbound,
    field: SerializableProperty<*, *>? = null
) {
    val sel = FormSelector<T>(serializer, annotations, desiredSize)
    ViewRenderer[sel].render(this, field, readable)
}