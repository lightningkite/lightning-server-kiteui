package com.lightningkite.kiteui.forms

import com.lightningkite.kiteui.FileReference
import com.lightningkite.kiteui.models.SubtextSemantic
import com.lightningkite.kiteui.models.px
import com.lightningkite.kiteui.models.rem
import com.lightningkite.kiteui.reactive.Constant
import com.lightningkite.kiteui.reactive.Property
import com.lightningkite.kiteui.reactive.reactive
import com.lightningkite.kiteui.views.atTopEnd
import com.lightningkite.kiteui.views.direct.row
import com.lightningkite.kiteui.views.direct.select
import com.lightningkite.kiteui.views.direct.sizeConstraints
import com.lightningkite.kiteui.views.direct.stack
import com.lightningkite.kiteui.views.expanding
import com.lightningkite.lightningserver.files.ServerFile
import com.lightningkite.serialization.ClientModule
import kotlinx.serialization.descriptors.SerialKind

class FormModule {
    var module = ClientModule
    var fileUpload: (suspend (FileReference) -> ServerFile)? = null
    var typeInfo: (type: String) -> FormTypeInfo<*, *>? = { _ -> println("WARN: Empty form context"); null }

    private val form_others: ArrayList<FormRenderer.Generator> = ArrayList()
    private val form_type: HashMap<String, ArrayList<FormRenderer.Generator>> = HashMap()
    private val form_kind: HashMap<SerialKind, ArrayList<FormRenderer.Generator>> = HashMap()
    private val form_annotation: HashMap<String, ArrayList<FormRenderer.Generator>> = HashMap()
    fun <T> formCandidates(key: FormSelector<T>): Sequence<FormRenderer.Generator> = sequence {
        form_type[key.serializer.descriptor.serialName]?.let { yieldAll(it) }
        form_kind[key.serializer.descriptor.kind]?.let { yieldAll(it) }
        key.annotations.forEach { anno ->
            form_annotation[anno.fqn]?.let {
                yieldAll(it)
            }
        }
        yieldAll(form_others)
    }

    private val view_others: ArrayList<ViewRenderer.Generator> = ArrayList()
    private val view_type: HashMap<String, ArrayList<ViewRenderer.Generator>> = HashMap()
    private val view_kind: HashMap<SerialKind, ArrayList<ViewRenderer.Generator>> = HashMap()
    private val view_annotation: HashMap<String, ArrayList<ViewRenderer.Generator>> = HashMap()
    fun <T> viewCandidates(key: FormSelector<T>): Sequence<ViewRenderer.Generator> = sequence {
        view_type[key.serializer.descriptor.serialName]?.let { yieldAll(it) }
        view_kind[key.serializer.descriptor.kind]?.let { yieldAll(it) }
        key.annotations.forEach { anno ->
            view_annotation[anno.fqn]?.let {
                yieldAll(it)
            }
        }
        yieldAll(view_others)
    }

    fun <T> view(key: FormSelector<T>): ViewRenderer<T> {
        val options = viewCandidates(key).filter { it.matches(this, key) }.sortedByDescending { it.priority(this, key) }.map { it.view(this, key) }.toList()
        if (!key.withPicker) return options.first()
        return ViewRenderer(this, null, key, size = options.first().size, handlesField = options.first().handlesField) { field, writable ->
            val selected = Property(options.first())
            row {
                spacing = 0.px
                expanding - stack {
                    reactive {
                        val sel = selected()
                        clearChildren()
                        sel.render(this@stack, field, writable)
                    }
                }
                sizeConstraints(width = 0.75.rem, height = 0.75.rem) - SubtextSemantic.onNext - atTopEnd - select {
                    spacing = 0.px
                    bind(selected, Constant(options)) { (it.generator?.name ?: "-") + " (${it.generator?.priority(this@FormModule, key)}, ${it.size.approximateWidth} x ${it.size.approximateHeight})" }
                }
            }
        }
    }
    fun <T> form(key: FormSelector<T>): FormRenderer<T> {
        val options = formCandidates(key).filter { it.matches(this, key) }.sortedByDescending { it.priority(this, key) }.map { it.form(this, key) }.toList()
        if (!key.withPicker) return options.first()
        return FormRenderer(this, null, key, size = options.first().size, handlesField = options.first().handlesField) { field, writable ->
            val selected = Property(options.first())
            row {
                spacing = 0.px
                expanding - stack {
                    reactive {
                        val sel = selected()
                        clearChildren()
                        sel.render(this@stack, field, writable)
                    }
                }
                sizeConstraints(width = 0.75.rem, height = 0.75.rem) - SubtextSemantic.onNext - atTopEnd - select {
                    spacing = 0.px
                    bind(selected, Constant(options)) { (it.generator?.name ?: "-") + " (${it.generator?.priority(this@FormModule, key)}, ${it.size.approximateWidth} x ${it.size.approximateHeight})" }
                }
            }
        }
    }

    operator fun plusAssign(generator: FormRenderer.Generator) {
        generator.annotation?.let { form_annotation.getOrPut(it) { ArrayList() }.add(generator) }
            ?: generator.type?.let { form_type.getOrPut(it) { ArrayList() }.add(generator) }
            ?: generator.kind?.let { form_kind.getOrPut(it) { ArrayList() }.add(generator) }
            ?: form_others.add(generator)
    }
    operator fun plusAssign(generator: ViewRenderer.Generator) {
        generator.annotation?.let { view_annotation.getOrPut(it) { ArrayList() }.add(generator) }
            ?: generator.type?.let { view_type.getOrPut(it) { ArrayList() }.add(generator) }
            ?: generator.kind?.let { view_kind.getOrPut(it) { ArrayList() }.add(generator) }
            ?: view_others.add(generator)
    }

    init {
        defaults()
    }
}