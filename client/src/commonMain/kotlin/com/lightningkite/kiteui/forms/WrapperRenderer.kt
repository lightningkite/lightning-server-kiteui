@file:OptIn(ExperimentalSerializationApi::class)

package com.lightningkite.kiteui.forms

import com.lightningkite.kiteui.views.ViewWriter
import com.lightningkite.reactive.core.MutableReactive
import com.lightningkite.reactive.core.Reactive
import com.lightningkite.reactive.lensing.lens
import com.lightningkite.services.database.WrappingSerializer
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer

/**
 * Renderer for value classes (wrapper types).
 *
 * Unwraps the value class and delegates to the inner type's renderer.
 * Uses WrappingSerializer to handle the wrapping/unwrapping.
 *
 * by Claude
 */
object WrapperRenderer : Renderer<Any> {
    override val name: String = "Value"  // by Claude

    override fun priority(context: RenderContext<Any>, module: FormModule): Float {
        // Only match if the serializer is a WrappingSerializer
        return if (context.serializer is WrappingSerializer<*, *>) 0.9f else -1f
    }

    @Suppress("UNCHECKED_CAST")
    override fun form(context: RenderContext<Any>, value: MutableReactive<Any>, module: FormModule): ViewWriter.() -> Unit {
        val wrapper = context.serializer as WrappingSerializer<Any, Any?>
        val innerSerializer = wrapper.getDeferred() as KSerializer<Any?>
        val innerContext = RenderContext(innerSerializer, context.fieldAnnotations)
        val innerRenderer = module.select(innerContext)

        val innerValue = value.lens(
            get = { wrapper.inner(it) },
            set = { wrapper.outer(it) }
        )

        return innerRenderer.form(innerContext, innerValue as MutableReactive<Any?>, module)
    }

    @Suppress("UNCHECKED_CAST")
    override fun view(context: RenderContext<Any>, value: Reactive<Any>, module: FormModule): ViewWriter.() -> Unit {
        val wrapper = context.serializer as WrappingSerializer<Any, Any?>
        val innerSerializer = wrapper.getDeferred() as KSerializer<Any?>
        val innerContext = RenderContext(innerSerializer, context.fieldAnnotations)
        val innerRenderer = module.select(innerContext)

        val innerValue = value.lens { wrapper.inner(it) }

        return innerRenderer.view(innerContext, innerValue, module)
    }

    @Suppress("UNCHECKED_CAST")
    override fun cellView(context: RenderContext<Any>, value: Reactive<Any>, module: FormModule): ViewWriter.() -> Unit {
        val wrapper = context.serializer as WrappingSerializer<Any, Any?>
        val innerSerializer = wrapper.getDeferred() as KSerializer<Any?>
        val innerContext = RenderContext(innerSerializer, context.fieldAnnotations)
        val innerRenderer = module.select(innerContext)

        val innerValue = value.lens { wrapper.inner(it) }

        return innerRenderer.cellView(innerContext, innerValue, module)
    }

    @Suppress("UNCHECKED_CAST")
    override fun cellForm(context: RenderContext<Any>, value: MutableReactive<Any>, module: FormModule): ViewWriter.() -> Unit {
        val wrapper = context.serializer as WrappingSerializer<Any, Any?>
        val innerSerializer = wrapper.getDeferred() as KSerializer<Any?>
        val innerContext = RenderContext(innerSerializer, context.fieldAnnotations)
        val innerRenderer = module.select(innerContext)

        val innerValue = value.lens(
            get = { wrapper.inner(it) },
            set = { wrapper.outer(it) }
        )

        return innerRenderer.cellForm(innerContext, innerValue as MutableReactive<Any?>, module)
    }

    @Suppress("UNCHECKED_CAST")
    override fun columnWidth(context: RenderContext<Any>, module: FormModule): Double? {
        val wrapper = context.serializer as WrappingSerializer<Any, Any?>
        val innerSerializer = wrapper.getDeferred() as KSerializer<Any?>
        val innerContext = RenderContext(innerSerializer, context.fieldAnnotations)
        return module.columnWidth(innerContext)
    }

    override fun labeledForm(
        context: RenderContext<Any>,
        value: MutableReactive<Any>,
        module: FormModule,
        label: String,
        description: String?
    ): ViewWriter.() -> Unit = { fieldWithoutBorder(label, description) { form(context, value, module)() } }

    override fun labeledView(
        context: RenderContext<Any>,
        value: Reactive<Any>,
        module: FormModule,
        label: String,
        description: String?
    ): ViewWriter.() -> Unit = { fieldWithoutBorder(label, description) { view(context, value, module)() } }
}

fun FormModule.registerWrapper() {
    register(Selector(), WrapperRenderer)
}
