package com.lightningkite.kiteui.forms

import com.lightningkite.kiteui.models.Icon
import com.lightningkite.kiteui.models.px
import com.lightningkite.kiteui.models.rem
import com.lightningkite.kiteui.models.times
import com.lightningkite.kiteui.reactive.*
import com.lightningkite.kiteui.views.card
import com.lightningkite.kiteui.views.centered
import com.lightningkite.kiteui.views.direct.button
import com.lightningkite.kiteui.views.direct.row
import com.lightningkite.kiteui.views.direct.scrollsHorizontally
import com.lightningkite.kiteui.views.direct.text
import com.lightningkite.kiteui.views.expanding
import com.lightningkite.kiteui.views.forEachUpdating
import com.lightningkite.kiteui.views.l2.icon
import com.lightningkite.serialization.SerializableAnnotation
import com.lightningkite.serialization.default
import com.lightningkite.serialization.listElement
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.SetSerializer
import kotlinx.serialization.builtins.serializer

abstract class ListRenderer<C> : FormRenderer.Generator, ViewRenderer.Generator {
    abstract val vertical: Boolean
    abstract val typeName: String
    abstract fun inner(serializer: KSerializer<C>): KSerializer<*>
    abstract fun lens(writable: Writable<C>): Writable<List<Any?>>
    abstract fun lens(readable: Readable<C>): Readable<List<Any?>>
    abstract fun remove(collection: C, item: Any?, index: Int): C
    abstract fun add(collection: C, item: Any?): C

    override val name: String get() = if (vertical) "Vertical $typeName" else "Horizontal $typeName"
    abstract override val type: String

    private fun inner(module: FormModule, selector: FormSelector<*>): FormSelector<out Any?> {
        val innerSer = inner(selector.serializer as KSerializer<C>)
        val inner = selector.copy(
            innerSer,
            desiredSize = if (vertical) FormLayoutPreferences.Block else FormLayoutPreferences.Bound,
            annotations = selector.annotations?.find { it.fqn == "com.lightningkite.lightningdb.MultipleReferences" }
                ?.let { selector.annotations + SerializableAnnotation("com.lightningkite.lightningdb.References", it.values) }
                ?: selector.annotations
        )
        return inner
    }

    override fun size(module: FormModule, selector: FormSelector<*>): FormSize {
        val inner = module.form(inner(module, selector)) as FormRenderer<Any?>
        return if (vertical)
            inner.size.copy(approximateHeight = (inner.size.approximateHeight) * 10 + 3)
        else
            inner.size.copy(approximateWidth = (inner.size.approximateWidth + 3) * 10 + 3)
    }

    @Suppress("UNCHECKED_CAST")
    override fun <T> form(module: FormModule, selector: FormSelector<T>): FormRenderer<T> {
        val inner = module.form(inner(module, selector)) as FormRenderer<Any?>
        return FormRenderer(module, this, selector as FormSelector<C>) { _, writable ->
            row {
                vertical = this@ListRenderer.vertical
                if (!vertical) expanding - scrollsHorizontally
                row {
                    vertical = this@ListRenderer.vertical
                    forEachUpdating(lens(writable).lensByElementAssumingSetNeverManipulates()) {
                        card - row {
                            spacing = 0.px
                            if (this@ListRenderer.vertical) expanding
                            inner.render(this, null, it.flatten())
                            centered - button {
                                icon(Icon.close.copy(width = 1.rem, height = 1.rem), "Delete")
                                onClick {
                                    writable set remove(writable(), it()(), it().index.value)
                                }
                            }
                        }
                    }
                }
                button {
                    if (this@ListRenderer.vertical) {
                        centered - row {
                            centered - text("Add")
                            centered - icon(Icon.add.copy(width = 1.rem, height = 1.rem), "")
                        }
                    } else {
                        centered - icon(Icon.add.copy(width = 1.rem, height = 1.rem), "Add")
                    }
                    onClick {
                        writable set (add(writable(), inner.selector.serializer.default()))
                    }
                }
            }
        } as FormRenderer<T>
    }

    @Suppress("UNCHECKED_CAST")
    override fun <T> view(module: FormModule, selector: FormSelector<T>): ViewRenderer<T> {
        val inner = module.view(inner(module, selector)) as ViewRenderer<Any?>
        return ViewRenderer(module, this, selector as FormSelector<C>) { _, readable ->
            row {
                vertical = this@ListRenderer.vertical
                forEachUpdating(lens(readable)) {
                    card - inner.render(this, null, it)
                }
            }
        } as ViewRenderer<T>
    }
}

object HorizontalListRenderer : ListRenderer<List<Any?>>() {
    override val vertical: Boolean = false
    override val typeName: String = "List"
    override val type: String = ListSerializer(Unit.serializer()).descriptor.serialName
    override fun add(collection: List<Any?>, item: Any?): List<Any?> = collection + item
    override fun remove(collection: List<Any?>, item: Any?, index: Int): List<Any?> = collection.toMutableList().apply { this.removeAt(index) }
    override fun lens(readable: Readable<List<Any?>>): Readable<List<Any?>> = readable
    override fun lens(writable: Writable<List<Any?>>): Writable<List<Any?>> = writable
    override fun inner(serializer: KSerializer<List<Any?>>): KSerializer<*> = serializer.listElement()!!
}

object VerticalListRenderer : ListRenderer<List<Any?>>() {
    override val vertical: Boolean = true
    override val typeName: String = "List"
    override val type: String = ListSerializer(Unit.serializer()).descriptor.serialName
    override fun add(collection: List<Any?>, item: Any?): List<Any?> = collection + item
    override fun remove(collection: List<Any?>, item: Any?, index: Int): List<Any?> = collection.toMutableList().apply { this.removeAt(index) }
    override fun lens(readable: Readable<List<Any?>>): Readable<List<Any?>> = readable
    override fun lens(writable: Writable<List<Any?>>): Writable<List<Any?>> = writable
    override fun inner(serializer: KSerializer<List<Any?>>): KSerializer<*> = serializer.listElement()!!
}

object HorizontalSetRenderer : ListRenderer<Set<Any?>>() {
    override val vertical: Boolean = false
    override val typeName: String = "Set"
    override val type: String = SetSerializer(Unit.serializer()).descriptor.serialName
    override fun add(collection: Set<Any?>, item: Any?): Set<Any?> = collection + item
    override fun remove(collection: Set<Any?>, item: Any?, index: Int): Set<Any?> = collection.toMutableSet().apply { this.remove(item) }
    override fun lens(readable: Readable<Set<Any?>>): Readable<List<Any?>> = readable.lens { it.toList() }
    override fun lens(writable: Writable<Set<Any?>>): Writable<List<Any?>> = writable.lens(get = { it.toList() }, set = { it.toSet() })
    override fun inner(serializer: KSerializer<Set<Any?>>): KSerializer<*> = serializer.listElement()!!
}

object VerticalSetRenderer : ListRenderer<Set<Any?>>() {
    override val vertical: Boolean = true
    override val typeName: String = "Set"
    override val type: String = SetSerializer(Unit.serializer()).descriptor.serialName
    override fun add(collection: Set<Any?>, item: Any?): Set<Any?> = collection + item
    override fun remove(collection: Set<Any?>, item: Any?, index: Int): Set<Any?> = collection.toMutableSet().apply { this.remove(item) }
    override fun lens(readable: Readable<Set<Any?>>): Readable<List<Any?>> = readable.lens { it.toList() }
    override fun lens(writable: Writable<Set<Any?>>): Writable<List<Any?>> = writable.lens(get = { it.toList() }, set = { it.toSet() })
    override fun inner(serializer: KSerializer<Set<Any?>>): KSerializer<*> = serializer.listElement()!!
}