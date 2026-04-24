@file:OptIn(ExperimentalSerializationApi::class)

package com.lightningkite.kiteui.forms

import com.lightningkite.kiteui.views.ViewWriter
import com.lightningkite.kiteui.views.direct.frame
import com.lightningkite.kiteui.views.direct.text
import com.lightningkite.reactive.core.MutableReactive
import com.lightningkite.reactive.core.Reactive
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.descriptors.StructureKind

/**
 * Renderer for Kotlin object (singleton) types.
 *
 * Objects have no editable state, so this renders an empty frame for forms
 * and displays the type name for views.
 *
 * by Claude
 */
public object ObjectRenderer : Renderer<Any> {
    override val name: String = "Object"

    override fun priority(context: RenderContext<Any>, module: FormModule): Float {
        // Only match OBJECT kind (Kotlin singletons)
        return if (context.serializer.descriptor.kind == StructureKind.OBJECT) 1f else -1f
    }

    override fun columnWidth(context: RenderContext<Any>, module: FormModule): Double = 10.0

    override fun form(context: RenderContext<Any>, value: MutableReactive<Any>, module: FormModule): ViewWriter.() -> Unit = {
        // Objects have no editable state
        frame { }
    }

    override fun view(context: RenderContext<Any>, value: Reactive<Any>, module: FormModule): ViewWriter.() -> Unit = {
        // Show the object type name
        text(context.displayName)
    }

    override fun cellView(context: RenderContext<Any>, value: Reactive<Any>, module: FormModule): ViewWriter.() -> Unit = {
        text(context.displayName)
    }

    override fun cellForm(context: RenderContext<Any>, value: MutableReactive<Any>, module: FormModule): ViewWriter.() -> Unit = {
        // Objects have no editable state
        frame { }
    }
}

/**
 * Register the singleton object renderer with the module.
 *
 * by Claude
 */
public fun FormModule.registerSingletonObject() {
    register(Selector(kind = StructureKind.OBJECT), ObjectRenderer)
}
