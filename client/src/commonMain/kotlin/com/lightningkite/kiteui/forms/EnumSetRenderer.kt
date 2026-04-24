@file:OptIn(ExperimentalSerializationApi::class)

package com.lightningkite.kiteui.forms

import com.lightningkite.kiteui.views.ViewWriter
import com.lightningkite.kiteui.views.direct.*
import com.lightningkite.reactive.context.invoke
import com.lightningkite.reactive.core.MutableReactive
import com.lightningkite.reactive.core.Reactive
import com.lightningkite.reactive.lensing.lens
import com.lightningkite.services.database.SerializableAnnotationValue
import com.lightningkite.services.database.VirtualEnumValue
import com.lightningkite.services.database.getElementSerializableAnnotations
import com.lightningkite.services.database.listElement
import com.lightningkite.services.data.titleCase
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.SerialKind
import kotlinx.serialization.descriptors.StructureKind

/**
 * Renderer for Set<EnumType> as a list of checkboxes.
 *
 * Each enum value is shown as a checkbox. Checked items are in the set.
 * Higher priority than SetRenderer for enum sets.
 *
 * Uses same display name logic as EnumRenderer:
 * 1. @DisplayName annotation if present
 * 2. VirtualEnumValue name if present
 * 3. Title-cased enum name as fallback
 *
 * by Claude
 */
public object EnumSetRenderer : Renderer<Set<Any?>> {
    override val name: String = "Checkboxes"  // by Claude

    override fun priority(context: RenderContext<Set<Any?>>, module: FormModule): Float {
        val descriptor = context.serializer.descriptor
        // Must be a Set
        if (descriptor.kind != StructureKind.LIST || !descriptor.serialName.contains("Set")) return -1f

        // Inner element must be an enum
        val elementSerializer = context.serializer.listElement() ?: return -1f
        if (elementSerializer.descriptor.kind != SerialKind.ENUM) return -1f

        // Higher priority than SetRenderer (0.85f)
        return 0.95f
    }

    @Suppress("UNCHECKED_CAST")
    override fun form(context: RenderContext<Set<Any?>>, value: MutableReactive<Set<Any?>>, module: FormModule): ViewWriter.() -> Unit {
        val elementSerializer = context.serializer.listElement() as KSerializer<Any?>
        val enumValues = elementSerializer.enumValues()

        return {
            col {
                for (enumValue in enumValues) {
                    row {
                        checkbox {
                            checked bind value.lens(
                                get = { enumValue in it },
                                modify = { original, checked ->
                                    if (checked) original + enumValue else original - enumValue
                                }
                            )
                        }
                        text(toDisplayName(enumValue, elementSerializer))
                    }
                }
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    override fun view(context: RenderContext<Set<Any?>>, value: Reactive<Set<Any?>>, module: FormModule): ViewWriter.() -> Unit {
        val elementSerializer = context.serializer.listElement() as KSerializer<Any?>

        return {
            text {
                ::content {
                    val set = value()
                    if (set.isEmpty()) "None"
                    else set.joinToString(", ") { toDisplayName(it, elementSerializer) }
                }
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    override fun cellView(context: RenderContext<Set<Any?>>, value: Reactive<Set<Any?>>, module: FormModule): ViewWriter.() -> Unit {
        val elementSerializer = context.serializer.listElement() as KSerializer<Any?>

        return {
            text {
                ::content {
                    val set = value()
                    if (set.isEmpty()) "None"
                    else set.joinToString(", ") { toDisplayName(it, elementSerializer) }
                }
            }
        }
    }

    override fun columnWidth(context: RenderContext<Set<Any?>>, module: FormModule): Double = 15.0

    // by Claude - copied from EnumRenderer for consistency
    private fun toDisplayName(value: Any?, serializer: KSerializer<*>): String {
        return if (value == null) "N/A"
        else (value as? VirtualEnumValue)?.let {
            it.enum.options[it.index].let { option ->
                option.annotations.find { it.fqn == Annotations.DisplayName }?.values?.get("text")
                    ?.let { it as? SerializableAnnotationValue.StringValue }?.value
                    ?: option.name.titleCase()
            }
        }
        ?: (value as? Enum<*>)?.let {
            serializer.getElementSerializableAnnotations(it.ordinal)
                .find { it.fqn == Annotations.DisplayName }?.values?.get("text")
                ?.let { it as? SerializableAnnotationValue.StringValue }?.value
                ?: it.name.titleCase()
        }
        ?: value.toString().titleCase()
    }
}

public fun FormModule.registerEnumSet() {
    register(Selector(kind = StructureKind.LIST), EnumSetRenderer)
}
