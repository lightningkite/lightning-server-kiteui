@file:OptIn(ExperimentalSerializationApi::class)

package com.lightningkite.kiteui.forms

import com.lightningkite.kiteui.models.Icon
import com.lightningkite.kiteui.models.rem
import com.lightningkite.kiteui.views.ElementWriter
import com.lightningkite.kiteui.views.ViewWriter
import com.lightningkite.kiteui.views.centered
import com.lightningkite.kiteui.views.direct.*
import com.lightningkite.kiteui.views.expanding
import com.lightningkite.kiteui.views.direct.icon
import com.lightningkite.reactive.core.MutableReactive
import com.lightningkite.reactive.core.Reactive
import com.lightningkite.reactive.lensing.lens
import com.lightningkite.services.database.SerializableAnnotationValue
import com.lightningkite.services.database.SerializableProperty
import com.lightningkite.services.database.serializableProperties
import com.lightningkite.titleCase
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.StructureKind

// Maximum combined width (in rem) for grouped fields to be rendered side-by-side - by Claude
private const val MAX_GROUP_WIDTH = 50.0

/**
 * Renderer for data classes (objects with serializable properties).
 *
 * Renders each field vertically with labels, respecting visibility annotations.
 * Fields with the same @Group annotation are rendered side-by-side if they fit.
 *
 * by Claude
 */
object DataClassRenderer : Renderer<Any> {
    override val name: String = "Fields"  // by Claude

    override fun priority(context: RenderContext<Any>, module: FormModule): Float {
        val descriptor = context.serializer.descriptor
        // Only match CLASS kind that has serializable properties
        if (descriptor.kind != StructureKind.CLASS) return -1f
        if (context.serializer.serializableProperties.isNullOrEmpty()) return -1f
        // Lower priority to let more specific renderers win
        return 0.6f
    }

    @Suppress("UNCHECKED_CAST")
    override fun form(context: RenderContext<Any>, value: MutableReactive<Any>, module: FormModule): ElementWriter.CanAddTheme.() -> Unit {
        val properties = context.serializer.serializableProperties
            ?: return { /* No properties */ }

        // Filter and sort properties - by Claude
        val visibleProps = properties
            .filter { it.fieldVisibility(module) != FieldVisibility.HIDDEN }
            .sortedBy { it.importance }

        // Group properties by @Group annotation - by Claude
        val grouped = groupProperties(visibleProps, module)

        return {
            col {
//                gap = 2.rem
                for (group in grouped) {
                    if (group.size == 1) {
                        // Single field - render normally
                        val prop = group.first()
                        @Suppress("UNCHECKED_CAST")
                        val typedProp = prop as SerializableProperty<Any, Any?>
                        val fieldContext = RenderContext(typedProp.serializer as KSerializer<Any?>, typedProp.serializableAnnotations)

                        if (prop.fieldVisibility(module) == FieldVisibility.EDIT) {
                            renderFieldForm(typedProp, fieldContext, value, module)
                        } else {
                            renderFieldView(typedProp, fieldContext, value, module)
                        }
                    } else {
                        // Multiple fields in group - render side-by-side - by Claude
                        row {
                            for (prop in group) {
                                @Suppress("UNCHECKED_CAST")
                                val typedProp = prop as SerializableProperty<Any, Any?>
                                val fieldContext = RenderContext(typedProp.serializer as KSerializer<Any?>, typedProp.serializableAnnotations)

                                expanding.col {
                                    if (prop.fieldVisibility(module) == FieldVisibility.EDIT) {
                                        renderFieldForm(typedProp, fieldContext, value, module)
                                    } else {
                                        renderFieldView(typedProp, fieldContext, value, module)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    override fun view(context: RenderContext<Any>, value: Reactive<Any>, module: FormModule): ElementWriter.CanAddTheme.() -> Unit {
        val properties = context.serializer.serializableProperties
            ?: return { /* No properties */ }

        // Filter and sort properties - by Claude
        val visibleProps = properties
            .filter { it.fieldVisibility(module) != FieldVisibility.HIDDEN }
            .sortedBy { it.importance }

        // Group properties by @Group annotation - by Claude
        val grouped = groupProperties(visibleProps, module)

        return {
            col {
                for (group in grouped) {
                    if (group.size == 1) {
                        // Single field - render normally
                        val prop = group.first()
                        @Suppress("UNCHECKED_CAST")
                        val typedProp = prop as SerializableProperty<Any, Any?>
                        val fieldContext = RenderContext(typedProp.serializer as KSerializer<Any?>, typedProp.serializableAnnotations)
                        renderFieldView(typedProp, fieldContext, value, module)
                    } else {
                        // Multiple fields in group - render side-by-side - by Claude
                        row {
                            for (prop in group) {
                                @Suppress("UNCHECKED_CAST")
                                val typedProp = prop as SerializableProperty<Any, Any?>
                                val fieldContext = RenderContext(typedProp.serializer as KSerializer<Any?>, typedProp.serializableAnnotations)

                                expanding.col {
                                    renderFieldView(typedProp, fieldContext, value, module)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    override fun cellView(context: RenderContext<Any>, value: Reactive<Any>, module: FormModule): ElementWriter.CanAddTheme.() -> Unit {
        // In cell view, show the "title" fields (name, title, subject, etc.)
        val properties = context.serializer.serializableProperties
            ?: return { text { ::content { value().toString() } } }
        val titleProp = properties.find { it.name == "name" }
            ?: properties.find { it.name == "title" }
            ?: properties.find { it.name == "subject" }
            ?: properties.find { it.name == "label" }
            ?: properties.firstOrNull()

        return if (titleProp != null) {
            @Suppress("UNCHECKED_CAST")
            val typedProp = titleProp as SerializableProperty<Any, Any?>
            val fieldContext = RenderContext(typedProp.serializer as KSerializer<Any?>, typedProp.serializableAnnotations)
            val fieldRenderer = module.select(fieldContext);
            {
                val fieldValue = value.lens { typedProp.get(it) }
                fieldRenderer.cellView(fieldContext, fieldValue, module)()
            }
        } else {
            { text { ::content { value().toString() } } }
        }
    }

    // cellForm uses default dialog behavior

    override fun columnWidth(context: RenderContext<Any>, module: FormModule) = 20.0

    // by Claude - Data classes use section header instead of field() wrapper to avoid nesting
    override fun labeledForm(
        context: RenderContext<Any>,
        value: MutableReactive<Any>,
        module: FormModule,
        label: String,
        description: String?
    ): ElementWriter.CanAddTheme.() -> Unit = {
        col {
            row {
                h4(label)
                description?.let { desc ->
                    centered.textPopover(desc).icon(Icon.info.copy(width = 1.rem, height = 1.rem), "Info")
                }
            }
            form(context, value, module)()
        }
    }

    override fun labeledView(
        context: RenderContext<Any>,
        value: Reactive<Any>,
        module: FormModule,
        label: String,
        description: String?
    ): ElementWriter.CanAddTheme.() -> Unit = {
        col {
            row {
                h4(label)
                description?.let { desc ->
                    centered.textPopover(desc).icon(Icon.info.copy(width = 1.rem, height = 1.rem), "Info")
                }
            }
            view(context, value, module)()
        }
    }

    // ===== Helper Functions =====
    // by Claude

    @Suppress("UNCHECKED_CAST")
    private fun ViewWriter.renderFieldForm(
        prop: SerializableProperty<Any, Any?>,
        fieldContext: RenderContext<Any?>,
        parentValue: MutableReactive<Any>,
        module: FormModule
    ) {
        // Use modify since we're updating one field of an object
        val fieldValue = parentValue.lens(
            get = { prop.get(it) },
            modify = { parent, newFieldValue -> prop.setCopy(parent, newFieldValue) }
        )

        when {
            prop.doesNotNeedLabel -> {
                // No label - render just the control
                module.form(fieldContext, fieldValue)()
            }
            prop.sentence != null -> {
                // Sentence format: "Before _ after" - custom inline layout
                val sentence = prop.sentence!!
                val before = sentence.substringBefore('_')
                val after = sentence.substringAfter('_').removePrefix("_")
                row {
                    if (before.isNotBlank()) text(before)
                    module.form(fieldContext, fieldValue)()
                    if (after.isNotBlank()) text(after)
                }
            }
            else -> {
                // Use module's labeled form with switcher support - by Claude
                module.labeledFormWithSwitcher(fieldContext, fieldValue, prop.displayName, prop.description)()
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun ViewWriter.renderFieldView(
        prop: SerializableProperty<Any, Any?>,
        fieldContext: RenderContext<Any?>,
        parentValue: Reactive<Any>,
        module: FormModule
    ) {
        val fieldValue = parentValue.lens { prop.get(it) }

        when {
            prop.doesNotNeedLabel -> {
                // No label - render just the view
                module.view(fieldContext, fieldValue)()
            }
            prop.sentence != null -> {
                // Sentence format: "Before _ after" - custom inline layout
                val sentence = prop.sentence!!
                val before = sentence.substringBefore('_')
                val after = sentence.substringAfter('_').removePrefix("_")
                row {
                    if (before.isNotBlank()) text(before)
                    module.view(fieldContext, fieldValue)()
                    if (after.isNotBlank()) text(after)
                }
            }
            else -> {
                // Use module's labeled view with switcher support - by Claude
                module.labeledViewWithSwitcher(fieldContext, fieldValue, prop.displayName, prop.description)()
            }
        }
    }
}

// ===== Field Helpers =====

/**
 * Get field visibility from FormModule's visibility settings.
 *
 * by Claude - migrated to use FormModule.effectiveVisibility for admin settings support
 */
fun SerializableProperty<*, *>.fieldVisibility(module: FormModule): FieldVisibility {
    @Suppress("UNCHECKED_CAST")
    val context = RenderContext(serializer as KSerializer<Any?>, serializableAnnotations)
    return module.effectiveVisibility(context)
}

val SerializableProperty<*, *>.displayName: String
    get() = serializableAnnotations.find { it.fqn == Annotations.DisplayName }
        ?.values?.get("text")
        ?.let { it as? SerializableAnnotationValue.StringValue }?.value
        ?: if (name == "_id") "ID" else name.titleCase()

val SerializableProperty<*, *>.doesNotNeedLabel: Boolean
    get() = serializableAnnotations.any { it.fqn == Annotations.DoesNotNeedLabel }

val SerializableProperty<*, *>.sentence: String?
    get() = serializableAnnotations.find { it.fqn == Annotations.Sentence }
        ?.values?.values?.firstOrNull()
        ?.let { it as? SerializableAnnotationValue.StringValue }?.value

val SerializableProperty<*, *>.importance: Int
    get() = serializableAnnotations.find { it.fqn == Annotations.Importance }
        ?.values?.values?.firstOrNull()
        ?.let { it as? SerializableAnnotationValue.ByteValue }?.value?.toInt()
        ?: when (name) {
            "_id" -> if (serializer.descriptor.serialName.contains("Uuid")) 8 else 1
            "title", "subject" -> 1
            "name", "email", "phone" -> 2
            else -> 7
        }

/** Get the @Group annotation value, if present - by Claude */
val SerializableProperty<*, *>.group: String?
    get() = serializableAnnotations.find { it.fqn == Annotations.Group }
        ?.values?.values?.firstOrNull()
        ?.let { it as? SerializableAnnotationValue.StringValue }?.value

/**
 * Groups properties by their @Group annotation, keeping them side-by-side if they fit.
 *
 * Fields with the same @Group value are placed together.
 * If their combined width exceeds MAX_GROUP_WIDTH, they're split into separate rows.
 * Ungrouped fields (no @Group) are rendered individually.
 *
 * by Claude
 */
private fun groupProperties(
    properties: List<SerializableProperty<*, *>>,
    module: FormModule
): List<List<SerializableProperty<*, *>>> {
    val result = mutableListOf<List<SerializableProperty<*, *>>>()
    val processed = mutableSetOf<SerializableProperty<*, *>>()

    for (prop in properties) {
        if (prop in processed) continue

        val groupName = prop.group
        if (groupName == null) {
            // No group - render individually
            result.add(listOf(prop))
            processed.add(prop)
        } else {
            // Find all properties with the same group name
            val groupMembers = properties.filter { it.group == groupName && it !in processed }

            // Calculate combined width
            val totalWidth = groupMembers.sumOf { p ->
                @Suppress("UNCHECKED_CAST")
                val ctx = RenderContext(p.serializer as KSerializer<Any?>, p.serializableAnnotations)
                val renderer = module.select(ctx)
                renderer.columnWidth(ctx, module) ?: 10.0
            }

            if (totalWidth <= MAX_GROUP_WIDTH && groupMembers.size <= 4) {
                // Fits - render as a single row
                result.add(groupMembers)
                processed.addAll(groupMembers)
            } else {
                // Too wide - render each individually
                for (member in groupMembers) {
                    result.add(listOf(member))
                    processed.add(member)
                }
            }
        }
    }

    return result
}

fun FormModule.registerObject() {
    register(Selector(kind = StructureKind.CLASS), DataClassRenderer)
}
