@file:OptIn(ExperimentalSerializationApi::class)

package com.lightningkite.kiteui.forms

import com.lightningkite.kiteui.views.ElementWriter
import com.lightningkite.kiteui.views.direct.*
import com.lightningkite.kiteui.views.fieldTheme
import com.lightningkite.reactive.core.Constant
import com.lightningkite.reactive.core.MutableReactive
import com.lightningkite.reactive.core.Reactive
import com.lightningkite.services.database.SerializableAnnotationValue
import com.lightningkite.services.database.VirtualEnumValue
import com.lightningkite.services.database.getElementSerializableAnnotations
import com.lightningkite.services.database.nullElement
import com.lightningkite.titleCase
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.SerialKind

/**
 * Renderer for enum types. Uses a dropdown select.
 *
 * Displays enum values using:
 * 1. @DisplayName annotation if present on the enum value
 * 2. VirtualEnumValue name if present
 * 3. Title-cased enum name as fallback
 *
 * by Claude
 */
class EnumRendererInstance<T>(serializer: KSerializer<T>) : Renderer<T> {
    override val name: String = "Dropdown"  // by Claude
    @Suppress("UNCHECKED_CAST")
    private val options = Constant(
        (serializer.nullElement() ?: serializer).enumValues().let {
            if (serializer.descriptor.isNullable) listOf(null) + it else it
        } as List<T>
    )

    private val baseSerializer = serializer.nullElement() ?: serializer

    private fun toDisplayName(it: T): String {
        return if (it == null) "N/A"
        else (it as? VirtualEnumValue)?.let {
            it.enum.options[it.index].let {
                it.annotations.find { it.fqn == Annotations.DisplayName }?.values?.get("text")
                    ?.let { it as? SerializableAnnotationValue.StringValue }?.value
                    ?: it.name.titleCase()
            }
        }
        ?: (it as? Enum<*>)?.let {
            baseSerializer.getElementSerializableAnnotations(it.ordinal)
                .find { it.fqn == Annotations.DisplayName }?.values?.get("text")
                ?.let { it as? SerializableAnnotationValue.StringValue }?.value
                ?: it.name.titleCase()
        }
        ?: it.toString().titleCase()
    }

    override fun priority(context: RenderContext<T>, module: FormModule) = 0.9f

    override fun form(context: RenderContext<T>, value: MutableReactive<T>, module: FormModule): ElementWriter.CanAddTheme.() -> Unit = {
        fieldTheme.select {
            bind(value, options, ::toDisplayName)
        }
    }

    override fun view(context: RenderContext<T>, value: Reactive<T>, module: FormModule): ElementWriter.CanAddTheme.() -> Unit = {
        text { ::content { toDisplayName(value()) } }
    }

    override fun cellForm(context: RenderContext<T>, value: MutableReactive<T>, module: FormModule): ElementWriter.CanAddTheme.() -> Unit = form(context, value, module)
    override fun columnWidth(context: RenderContext<T>, module: FormModule) = 15.0
}

/**
 * Generic enum renderer that creates type-specific instances.
 * This is registered with the module and delegates to EnumRendererInstance.
 */
object EnumRenderer : Renderer<Any> {
    override val name: String = "Dropdown"  // by Claude
    private val cache = mutableMapOf<KSerializer<*>, EnumRendererInstance<*>>()

    @Suppress("UNCHECKED_CAST")
    private fun <T> getInstance(serializer: KSerializer<T>): EnumRendererInstance<T> {
        return cache.getOrPut(serializer) { EnumRendererInstance(serializer) } as EnumRendererInstance<T>
    }

    override fun priority(context: RenderContext<Any>, module: FormModule) = 0.9f

    @Suppress("UNCHECKED_CAST")
    override fun form(context: RenderContext<Any>, value: MutableReactive<Any>, module: FormModule): ElementWriter.CanAddTheme.() -> Unit {
        val instance = getInstance(context.serializer)
        return instance.form(context as RenderContext<Any>, value, module)
    }

    @Suppress("UNCHECKED_CAST")
    override fun view(context: RenderContext<Any>, value: Reactive<Any>, module: FormModule): ElementWriter.CanAddTheme.() -> Unit {
        val instance = getInstance(context.serializer)
        return instance.view(context as RenderContext<Any>, value, module)
    }

    override fun cellForm(context: RenderContext<Any>, value: MutableReactive<Any>, module: FormModule): ElementWriter.CanAddTheme.() -> Unit = form(context, value, module)
    override fun columnWidth(context: RenderContext<Any>, module: FormModule) = 15.0
}

fun FormModule.registerEnum() {
    register(Selector(kind = SerialKind.ENUM), EnumRenderer)
}
