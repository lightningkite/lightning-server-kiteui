package com.lightningkite.kiteui.forms

import com.lightningkite.kiteui.FileReference
import com.lightningkite.kiteui.models.SubtextSemantic
import com.lightningkite.kiteui.models.px
import com.lightningkite.kiteui.models.rem
import com.lightningkite.kiteui.views.atTopEnd
import com.lightningkite.kiteui.views.direct.row
import com.lightningkite.kiteui.views.direct.select
import com.lightningkite.kiteui.views.direct.sizeConstraints
import com.lightningkite.kiteui.views.direct.stack
import com.lightningkite.kiteui.views.expanding
import com.lightningkite.reactive.context.reactive
import com.lightningkite.reactive.core.Constant
import com.lightningkite.reactive.core.Signal
import com.lightningkite.services.database.ClientModule
import com.lightningkite.services.files.ServerFile
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.descriptors.SerialKind

@OptIn(ExperimentalSerializationApi::class)
class FormModule {
    var module = ClientModule
    var showTypePicker = false
    var fileUpload: (suspend (FileReference) -> ServerFile)? = null
    var typeInfo: (type: String) -> FormTypeInfo<*, *>? = { _ -> println("WARN: Empty form context"); null }

    val visibilitySettings: MutableMap<String, FieldVisibility> = mutableMapOf(
        "com.lightningkite.lightningdb.AdminHidden" to FieldVisibility.HIDDEN,
        "com.lightningkite.lightningdb.Denormalized" to FieldVisibility.READ,
        "com.lightningkite.lightningdb.AdminViewOnly" to FieldVisibility.READ
    )

    private val form_others: ArrayList<FormRenderer.Generator> = ArrayList()
    private val form_type: HashMap<String, ArrayList<FormRenderer.Generator>> = HashMap()
    private val form_kind: HashMap<SerialKind, ArrayList<FormRenderer.Generator>> = HashMap()
    private val form_annotation: HashMap<String, ArrayList<FormRenderer.Generator>> = HashMap()
    val allForms get() = form_others + form_type.values.flatten() + form_kind.values.flatten() + form_annotation.values.flatten()
    fun <T> formCandidates(key: FormSelector<T>): Sequence<FormRenderer.Generator> = sequence {
        form_type[key.serializer.descriptor.serialName.substringBefore('/')]?.let { yieldAll(it) }
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
    val allViews get() = view_others + view_type.values.flatten() + view_kind.values.flatten() + form_annotation.values.flatten()
    fun <T> viewCandidates(key: FormSelector<T>): Sequence<ViewRenderer.Generator> = sequence {
        view_type[key.serializer.descriptor.serialName.substringBefore('/')]?.let { yieldAll(it) }
        view_kind[key.serializer.descriptor.kind]?.let { yieldAll(it) }
        key.annotations.forEach { anno ->
            view_annotation[anno.fqn]?.let {
                yieldAll(it)
            }
        }
        yieldAll(view_others)
    }

    private val viewCache = HashMap<FormSelector<*>, ViewRenderer<*>>()
    private val currentlyMakingViews = HashMap<FormSelector<*>, ViewRenderer.Placeholder<*>>()
    @Suppress("UNCHECKED_CAST")
    private fun <T> viewCache(key: FormSelector<T>, generate: ()->ViewRenderer<T>): ViewRenderer<T> {
        // Keeping track of views being currently made is necessary for nested data types
        return when (key) {
            in viewCache.keys -> viewCache[key] as ViewRenderer<T>
            in currentlyMakingViews.keys -> currentlyMakingViews[key]!!.also { it.used = true } as ViewRenderer<T>
            else -> {
                currentlyMakingViews[key] = ViewRenderer.Placeholder(this@FormModule, key)
                val result = generate()
                viewCache[key] = result
                (currentlyMakingViews.remove(key) as? ViewRenderer.Placeholder<T>)?.let {
                    if (it.used) {
                        it.current = result
                        it
                    } else result
                } ?: result
            }
        }
    }
    fun <T> view(key: FormSelector<T>): ViewRenderer<T> = viewCache(key) {
        val options = viewCandidates(key).filter { it.matches(this, key) }.sortedByDescending { it.priority(this, key) }.map { it.view(this, key) }.toList()
        if (!showTypePicker) options.first()
        else ViewRenderer(this, null, key, size = options.first().size, handlesField = options.first().handlesField) { field, mutable ->
            val selected = Signal(options.first())
            row {
//                gap = 0.px
                expanding - stack {
                    reactive {
                        val sel = selected()
                        clearChildren()
                        sel.render(this@stack, field, mutable)
                    }
                }
                sizeConstraints(width = 0.75.rem, height = 0.75.rem) - SubtextSemantic.onNext - atTopEnd - select {
                    gap = 0.px
                    bind(selected, Constant(options)) { (it.generator?.name ?: "-") + " (${it.generator?.priority(this@FormModule, key)}, ${it.size.approximateWidth} x ${it.size.approximateHeight})" }
                }
            }
        }
    }
    private val formCache = HashMap<FormSelector<*>, FormRenderer<*>>()
    private val currentlyMakingForms = HashMap<FormSelector<*>, FormRenderer.Placeholder<*>>()
    @Suppress("UNCHECKED_CAST")
    private fun <T> formCache(key: FormSelector<T>, generate: ()->FormRenderer<T>): FormRenderer<T> {
        return when (key) {
            in formCache.keys -> formCache[key] as FormRenderer<T>
            in currentlyMakingForms.keys -> currentlyMakingForms[key]!!.also { it.used = true } as FormRenderer<T>
            else -> {
                currentlyMakingForms[key] = FormRenderer.Placeholder(this@FormModule, key)
                val result = generate()
                formCache[key] = result
                (currentlyMakingForms.remove(key) as? FormRenderer.Placeholder<T>)?.let {
                    if (it.used) {
                        it.current = result
                        it
                    } else result
                } ?: result
            }
        }
    }
    fun <T> form(key: FormSelector<T>): FormRenderer<T> = formCache(key) {
        val options = formCandidates(key).filter { it.matches(this, key) }.sortedByDescending { it.priority(this, key) }.map { it.form(this, key) }.toList()
        if (!showTypePicker) options.first()
        else FormRenderer(this, null, key, size = options.first().size, handlesField = options.first().handlesField) { field, mutable ->
            val selected = Signal(options.first())
            row {
//                gap = 0.px
                expanding - stack {
                    reactive {
                        val sel = selected()
                        clearChildren()
                        sel.render(this@stack, field, mutable)
                    }
                }
                sizeConstraints(width = 0.75.rem, height = 0.75.rem) - SubtextSemantic.onNext - atTopEnd - select {
                    gap = 0.px
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