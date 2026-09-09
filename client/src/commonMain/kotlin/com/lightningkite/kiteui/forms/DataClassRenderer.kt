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
import com.lightningkite.services.data.titleCase
import com.lightningkite.services.database.SerializableAnnotation
import com.lightningkite.services.database.serializableAnnotations
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.StructureKind

/** Maximum combined width (in rem) of fields rendered side-by-side. */
private const val MAX_ROW_WIDTH = 50.0

/** Maximum number of fields rendered side-by-side. */
private const val MAX_ROW_FIELDS = 4

/** Width (in rem) assumed for a field whose renderer gives no width hint. */
private const val UNKNOWN_FIELD_WIDTH = 10.0

/** Extra rem of screen the page needs beyond the fields themselves before a row can stay horizontal. */
private const val ROW_SCREEN_MARGIN = 10.0

/**
 * Renderer for data classes (objects with serializable properties).
 *
 * Fields are laid out vertically, except that a set of fields may share a row when they fit:
 * - Fields sharing a `@Group` annotation.
 * - Every field of a short data class, when none of them declare a `@Group`.
 *
 * A shared row collapses back to a column on screens too narrow to hold it.
 *
 * by Claude
 */
public object DataClassRenderer : Renderer<Any> {
    override val name: String = "Fields"

    override fun priority(context: RenderContext<Any>, module: FormModule): Float {
        val descriptor = context.serializer.descriptor
        if (descriptor.kind != StructureKind.CLASS) return -1f
        if (context.serializer.serializableProperties.isNullOrEmpty()) return -1f
        // Lower priority to let more specific renderers win
        return 0.6f
    }

    override fun form(
        context: RenderContext<Any>,
        value: MutableReactive<Any>,
        module: FormModule
    ): ElementWriter.CanAddTheme.() -> Unit {
        val rows = fieldRows(context, module)
        return {
            renderRows(rows) { field ->
                if (field.property.fieldVisibility(module) == FieldVisibility.EDIT) renderFieldForm(field, value, module)
                else renderFieldView(field, value, module)
            }
        }
    }

    override fun view(
        context: RenderContext<Any>,
        value: Reactive<Any>,
        module: FormModule
    ): ElementWriter.CanAddTheme.() -> Unit {
        val rows = fieldRows(context, module)
        return { renderRows(rows) { field -> renderFieldView(field, value, module) } }
    }

    override fun cellView(
        context: RenderContext<Any>,
        value: Reactive<Any>,
        module: FormModule
    ): ElementWriter.CanAddTheme.() -> Unit {
        // In cell view, show only the "title" field (name, title, subject, etc.)
        val properties = context.serializer.serializableProperties
        val titleProp = properties?.let {
            it.find { it.name == "name" }
                ?: it.find { it.name == "title" }
                ?: it.find { it.name == "subject" }
                ?: it.find { it.name == "label" }
                ?: it.firstOrNull()
        } ?: return { text { ::content { value().toString() } } }

        val field = titleProp.asField(context.serializer.descriptor.serialName, module)
        return {
            module.cellView(field.context, value.lens { field.property.get(it) })()
        }
    }

    // cellForm uses default dialog behavior

    override fun columnWidth(context: RenderContext<Any>, module: FormModule): Double = 20.0

    // Data classes use a section header instead of the field() wrapper to avoid nesting labels
    override fun labeledForm(
        context: RenderContext<Any>,
        value: MutableReactive<Any>,
        module: FormModule,
        label: String,
        description: String?
    ): ElementWriter.CanAddTheme.() -> Unit = {
        col {
            sectionHeader(label, description)
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
            sectionHeader(label, description)
            view(context, value, module)()
        }
    }
}

// ===== Layout =====

/** A property together with everything needed to render and size it. */
private class Field(
    val property: SerializableProperty<Any, Any?>,
    val context: RenderContext<Any?>,
    val width: Double,
)

@Suppress("UNCHECKED_CAST")
private fun SerializableProperty<*, *>.asField(ownerSerialName: String, module: FormModule): Field {
    val property = this as SerializableProperty<Any, Any?>
    val context = RenderContext(property.serializer, property.serializableAnnotationsWithPkAnnotationIfId(ownerSerialName))
    return Field(property, context, module.columnWidth(context) ?: UNKNOWN_FIELD_WIDTH)
}

private fun List<Field>.fitsInOneRow(): Boolean =
    size <= MAX_ROW_FIELDS && sumOf { it.width } <= MAX_ROW_WIDTH

/**
 * Splits the visible fields into the rows they should be rendered in, most important first.
 *
 * A short data class becomes a single row; otherwise `@Group` members share a row when they fit.
 */
private fun fieldRows(context: RenderContext<Any>, module: FormModule): List<List<Field>> {
    val ownerSerialName = context.serializer.descriptor.serialName
    val fields = context.serializer.serializableProperties.orEmpty()
        .filter { it.fieldVisibility(module) != FieldVisibility.HIDDEN }
        .sortedBy { it.importance }
        .map { it.asField(ownerSerialName, module) }
    if (fields.isEmpty()) return emptyList()

    // A short class with no explicit grouping goes on one row; an explicit @Group is honored instead
    if (fields.none { it.property.group != null } && fields.fitsInOneRow()) return listOf(fields)

    val rows = mutableListOf<List<Field>>()
    val emittedGroups = mutableSetOf<String>()
    for (field in fields) {
        val groupName = field.property.group
        if (groupName == null) {
            rows.add(listOf(field))
            continue
        }
        if (!emittedGroups.add(groupName)) continue  // already emitted alongside the rest of its group
        val members = fields.filter { it.property.group == groupName }
        if (members.fitsInOneRow()) rows.add(members)
        else members.forEach { rows.add(listOf(it)) }
    }
    return rows
}

private fun ElementWriter.CanAddTheme.renderRows(rows: List<List<Field>>, renderField: ViewWriter.(Field) -> Unit) {
    col {
        for (fieldRow in rows) {
            if (fieldRow.size == 1) {
                renderField(fieldRow.single())
            } else {
                // Stack vertically once the screen can no longer hold the fields at their desired widths
                rowCollapsingToColumn((fieldRow.sumOf { it.width } + ROW_SCREEN_MARGIN).rem) {
                    for (field in fieldRow) expanding.col { renderField(field) }
                }
            }
        }
    }
}

// ===== Field Rendering =====

private fun ViewWriter.renderFieldForm(field: Field, parentValue: MutableReactive<Any>, module: FormModule) {
    val property = field.property
    // Use modify since we're updating one field of an object
    val fieldValue = parentValue.lens(
        get = { property.get(it) },
        modify = { parent, newFieldValue -> property.setCopy(parent, newFieldValue) }
    )
    val sentenceText = property.sentence
    when {
        property.doesNotNeedLabel -> module.form(field.context, fieldValue)()
        sentenceText != null -> withSentence(sentenceText) { module.form(field.context, fieldValue)() }
        else -> module.labeledFormWithSwitcher(field.context, fieldValue, property.displayName, property.description)()
    }
}

private fun ViewWriter.renderFieldView(field: Field, parentValue: Reactive<Any>, module: FormModule) {
    val property = field.property
    val fieldValue = parentValue.lens { property.get(it) }
    val sentenceText = property.sentence
    when {
        property.doesNotNeedLabel -> module.view(field.context, fieldValue)()
        sentenceText != null -> withSentence(sentenceText) { module.view(field.context, fieldValue)() }
        else -> module.labeledViewWithSwitcher(field.context, fieldValue, property.displayName, property.description)()
    }
}

/** Lays out a `@Sentence` field as "Before _ after" with the control inline. */
private fun ViewWriter.withSentence(sentence: String, control: ViewWriter.() -> Unit) {
    row {
        sentence.substringBefore('_').takeIf { it.isNotBlank() }?.let { text(it) }
        control()
        sentence.substringAfter('_').removePrefix("_").takeIf { it.isNotBlank() }?.let { text(it) }
    }
}

private fun ViewWriter.sectionHeader(label: String, description: String?) {
    row {
        h4(label)
        description?.let { centered.textPopover(it).icon(Icon.info.copy(width = 1.rem, height = 1.rem), "Info") }
    }
}

// ===== Field Helpers =====

/**
 * Get field visibility from FormModule's visibility settings.
 *
 * by Claude - migrated to use FormModule.effectiveVisibility for admin settings support
 */
public fun SerializableProperty<*, *>.fieldVisibility(module: FormModule): FieldVisibility {
    @Suppress("UNCHECKED_CAST")
    val context = RenderContext(serializer as KSerializer<Any?>, serializableAnnotations)
    return module.effectiveVisibility(context)
}

public val SerializableProperty<*, *>.displayName: String
    get() = serializableAnnotations.find { it.fqn == Annotations.DisplayName }
        ?.values?.get("text")
        ?.let { it as? SerializableAnnotationValue.StringValue }?.value
        ?: if (name == "_id") "ID" else name.titleCase()

public val SerializableProperty<*, *>.doesNotNeedLabel: Boolean
    get() = serializableAnnotations.any { it.fqn == Annotations.DoesNotNeedLabel }

public val SerializableProperty<*, *>.sentence: String?
    get() = serializableAnnotations.find { it.fqn == Annotations.Sentence }
        ?.values?.values?.firstOrNull()
        ?.let { it as? SerializableAnnotationValue.StringValue }?.value

public val SerializableProperty<*, *>.importance: Int
    get() = serializableAnnotations.find { it.fqn == Annotations.Importance }
        ?.values?.values?.firstOrNull()
        ?.let { it as? SerializableAnnotationValue.ByteValue }?.value?.toInt()
        ?: when (name) {
            "_id" -> if (serializer.descriptor.serialName.contains("Uuid")) 8 else 1
            "title", "subject" -> 1
            "name", "email", "phone" -> 2
            else -> 7
        }

/** Get the `@Group` annotation value, if present. */
public val SerializableProperty<*, *>.group: String?
    get() = serializableAnnotations.find { it.fqn == Annotations.Group }
        ?.values?.values?.firstOrNull()
        ?.let { it as? SerializableAnnotationValue.StringValue }?.value

public fun FormModule.registerObject() {
    register(Selector(kind = StructureKind.CLASS), DataClassRenderer)
}

internal fun SerializableProperty<*, *>.serializableAnnotationsWithPkAnnotationIfId(fqn: String) = if(this.name == "_id" && this.serializableAnnotations.any {
    it.fqn == "com.lightningkite.services.data.References" && (it.values["references"] as? SerializableAnnotationValue.ClassValue)?.fqn == fqn
}) serializableAnnotations + SerializableAnnotation(ForeignKeyRenderer.IS_PRIMARY_KEY_FQN, mapOf()) else serializableAnnotations
