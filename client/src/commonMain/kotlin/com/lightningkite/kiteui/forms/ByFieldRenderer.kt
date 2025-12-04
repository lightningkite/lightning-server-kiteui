package com.lightningkite.kiteui.forms

import com.lightningkite.kiteui.models.px
import com.lightningkite.kiteui.models.rem
import com.lightningkite.kiteui.reactive.AppState
import com.lightningkite.kiteui.views.ViewWriter
import com.lightningkite.kiteui.views.card
import com.lightningkite.kiteui.views.centered
import com.lightningkite.kiteui.views.direct.*
import com.lightningkite.services.database.MySealedClassSerializerInterface
import com.lightningkite.reactive.core.MutableReactive
import com.lightningkite.reactive.core.Reactive
import com.lightningkite.reactive.lensing.lens
import com.lightningkite.serialization.lensPath
import com.lightningkite.services.database.*
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.StructureKind
import kotlinx.serialization.internal.GeneratedSerializer

/**
 * Defines the visibility level for fields in forms.
 *
 * - HIDDEN: Field is completely hidden from the UI
 * - READ: Field is shown as read-only (uses view renderer even in forms)
 * - EDIT: Field is editable (uses form renderer)
 */
enum class FieldVisibility { HIDDEN, READ, EDIT }

/**
 * Renderer that decomposes data classes into individual field-by-field forms/views.
 *
 * This is the primary renderer for structured data classes. It introspects the serializable
 * properties of a type and creates a form/view by rendering each field individually, with
 * intelligent layout based on field sizes.
 *
 * ## Features
 * - Field-by-field decomposition using serialization metadata
 * - Automatic label placement (inline or above field depending on field renderer)
 * - Support for sentence-style labels with underscores ("Enter _value_ here")
 * - Respects field visibility annotations (hidden, read-only, editable)
 * - Responsive row/column layout based on approximate field widths
 * - Groups related fields together when specified
 *
 * ## Matching Rules
 * - Matches only CLASS structures (not primitives, lists, etc.)
 * - Excludes nullable types (handled by NullableFormRenderer)
 * - Excludes inline/value classes (handled by WrapperFormRenderer)
 * - Excludes sealed classes (handled by MySealedFormRenderer)
 *
 * ## Layout Strategy
 * Fields are organized into rows. Multiple fields can share a row if their combined
 * approximateWidth fits. This creates a responsive form that adapts to screen size.
 *
 * ## Gotchas
 * - Base priority is 0.7, lower than more specialized renderers
 * - Relies on serializer.serializableProperties which may be null for some serializers
 * - Falls back to bestPropertiesAttempt() for GeneratedSerializer types
 */
object ByFieldRenderer : FormRenderer.Generator, ViewRenderer.Generator {
    override val name: String = "By Field"
    override val basePriority: Float
        get() = 0.7f
    override val kind = StructureKind.CLASS

    override fun matches(module: FormModule, selector: FormSelector<*>): Boolean {
        // Inline/value classes should use WrapperFormRenderer instead
        if(selector.serializer.descriptor.isInline) return false
        // Sealed classes should use MySealedFormRenderer instead
        if( selector.serializer is MySealedClassSerializerInterface<*>) return false
        return super<FormRenderer.Generator>.matches(module, selector) && !selector.serializer.descriptor.isNullable
    }

    override fun size(module: FormModule, selector: FormSelector<*>): FormSize {
        val info = TypeInfo(module, selector.serializer)
        return FormSize(
            FormSize.Block.approximateWidth,
            info.viewApproximateHeight
        )
    }
    /**
     * Creates an editable form renderer for a data class.
     *
     * Wraps the form in a card if this is a root-level field (field != null).
     * Each field group is rendered either as a single column item or a weighted row.
     */
    override fun <T> form(module: FormModule, selector: FormSelector<T>): FormRenderer<T> {
        val info = TypeInfo(module, selector.serializer)
        return FormRenderer<T>(module, this, selector) { field, mutable ->
            if (field != null) card
            col {
//                text("Available width: ${info.availableWidth} ${info.formGroup.map { it.size }}")
                info.formGroup.forEach {
                    if (it.size == 1) {
                        // Single field - render in column
                        it[0].form(this, mutable)
                    } else {
                        // Multiple fields - render in weighted row
                        row {
                            it.forEach {
                                weight(it.formSize.approximateWidth.toFloat())
                                it.form(this, mutable)
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Creates a read-only view renderer for a data class.
     *
     * Wraps the view in a card if this is a root-level field (field != null).
     * Each field group is rendered either as a single column item or a weighted row.
     */
    override fun <T> view(module: FormModule, selector: FormSelector<T>): ViewRenderer<T> {
        val info = TypeInfo(module, selector.serializer)
        return ViewRenderer<T>(module, this, selector) { field, readable ->
            if (field != null) card
            col {
//                text("Available width: ${info.availableWidth} ${info.viewGroup.map { it.size }}")
                info.viewGroup.forEach {
                    if (it.size == 1) {
                        // Single field - render in column
                        it[0].view(this, readable)
                    } else {
                        // Multiple fields - render in weighted row
                        row {
                            it.forEach {
                                weight(it.viewSize.approximateWidth.toFloat())
                                it.view(this, readable)
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Holds metadata about a data class type and its fields.
     *
     * Analyzes the serializer to extract fields, create renderers for each, and compute
     * layout groups. This is computed once and cached per TypeInfo instance.
     *
     * @property availableWidth The current window width in rem units, used for layout decisions
     */
    private class TypeInfo<T>(val module: FormModule, val serializer: KSerializer<T>, val availableWidth: Double = AppState.windowInfo.value.width.px / 1.rem.px) {
        /**
         * Represents a single field within the parent type T.
         *
         * Holds both the form and view renderers for this field, along with computed sizes
         * that account for label width.
         *
         * @property field The serializable property metadata
         * @property form The form renderer for this field type
         * @property view The view renderer for this field type
         */
        inner class Sub<S>(
            val field: SerializableProperty<T, S>,
            val form: FormRenderer<S>,
            val view: ViewRenderer<S>,
        ) {
            // TODO: Label width calculation uses hardcoded 3/4 character width ratio
            // Consider making this configurable or measuring actual label widths
            val formSize = field.sentence?.let {
                form.size.copy(approximateWidth = (form.size.approximateWidth + it.length * 3 / 4))
            } ?: form.size.copy(approximateWidth = (form.size.approximateWidth))
            val viewSize = field.sentence?.let {
                view.size.copy(approximateWidth = (view.size.approximateWidth + it.length * 3 / 4))
            } ?: view.size.copy(approximateWidth = (view.size.approximateWidth))

            /**
             * Wrapper function that handles label placement and field rendering.
             *
             * Determines whether to render a label based on:
             * - renderer.handlesField: If true, the renderer handles its own labeling
             * - field.doesNotNeedLabel: If true, skip labeling
             *
             * Label formats:
             * - Sentence format: "Enter _value_ here" splits into inline before/after text
             * - Standard format: Shows displayName as subtext above the field
             *
             * @param viewWriter The view writer context
             * @param render The renderer being used (form or view)
             * @param inner The function that renders the actual field UI
             */
            inline fun f(viewWriter: ViewWriter, render: Renderer<*>, inner: ViewWriter.()->Unit) = with(viewWriter) {
//                field.importance.let {
//                    when (it) {
//                        in 1..6 -> HeaderSizeSemantic(it).onNext
//                        7 -> {}
//                        8 -> SubtextSemantic.onNext
//                        else -> {}
//                    }
//                }
                if(render.handlesField || field.doesNotNeedLabel) {
                    // Renderer handles its own field labeling, or field doesn't need a label
                    inner(this)
                } else {
                    field.sentence?.let {
                        // Sentence format: "Before _field_ after" - render inline with field
                        val before = it.substringBefore('_')
                        val after = it.substringAfter('_')
                        row {
                            gap = 0.3.rem
                            if (before.isNotBlank()) {
                                centered - text(before)
                            }
//                            if (before.isBlank() || after.isBlank()) expanding
                            centered - inner(this)
                            if (after.isNotBlank()) {
                                centered - text(after)
                            }
                        }
                    } ?: col {
                        // Standard format: label above field
                        gap = 0.px
                        subtext(field.displayName)
                        inner(this)
                    }
                }
            }
            /**
             * Renders this field as an editable form element.
             *
             * Creates a lens to focus on this specific field within the parent object,
             * then delegates to either the view renderer (if field is READ visibility)
             * or the form renderer (if field is EDIT visibility).
             */
            fun form(writer: ViewWriter, mutable: MutableReactive<T>) {
                // Create a lens that focuses on this field within the parent object
                val w = mutable.lensPath(
                    DataClassPathAccess<T, T, S>(
                        DataClassPathSelf(serializer),
                        field
                    )
                )
                if (field.visibility(module) == FieldVisibility.READ)
                    // Field is read-only, use view renderer even in form context
                    f(writer, view) {
                        view.render(this, field, w)
                    }
                else
                    // Field is editable, use form renderer
                    f(writer, form) {
                        form.render(this, field, w)
                    }
            }

            /**
             * Renders this field as a read-only view element.
             *
             * Creates a lens to focus on this specific field within the parent object,
             * then delegates to the view renderer.
             */
            fun view(writer: ViewWriter, readable: Reactive<T>) {
                val r = readable.lens { field.get(it) }
                f(writer, view) { view.render(this@f, field, r) }
            }
        }

        /**
         * Fallback property extraction for serializers that don't provide serializableProperties.
         *
         * This is used as a backup when serializer.serializableProperties is null.
         * It only works for GeneratedSerializer types (kotlin serialization generated code).
         *
         * Note: This creates synthetic SerializableProperty objects that may not have
         * all the metadata that properly generated properties would have.
         */
        @Suppress("UNCHECKED_CAST")
        @OptIn(InternalSerializationApi::class, ExperimentalSerializationApi::class)
        fun bestPropertiesAttempt(): Array<SerializableProperty<T, *>> {
            val serializer = serializer
            if (serializer is GeneratedSerializer<*>) {
                return serializer.childSerializers().mapIndexed { index, it ->
                    object : SerializableProperty<T, Any?> {
                        override val name: String = serializer.descriptor.getElementName(index)
                        override val serializer: KSerializer<Any?> = it as KSerializer<Any?>
                        override fun setCopy(receiver: T, value: Any?): T = (serializer as KSerializer<T>).set(receiver, index, it as KSerializer<Any?>, value)
                        override fun get(receiver: T): Any? = (serializer as KSerializer<T>).get(receiver, index, it)
                        override val serializableAnnotations: List<SerializableAnnotation> = serializer.descriptor.getElementAnnotations(index).mapNotNull { SerializableAnnotation.parseOrNull(it) }
                    }
                }.toTypedArray()
            }
            // TODO: Log a warning when this fallback returns empty array?
            // This means the renderer matched but can't extract properties
            return arrayOf()
        }

        /**
         * List of Sub instances for each visible field in the data class.
         * Hidden fields (FieldVisibility.HIDDEN) are filtered out.
         */
        @Suppress("UNCHECKED_CAST")
        val subs = (serializer.serializableProperties ?: bestPropertiesAttempt()).map {
            val sel = FormSelector(it.serializer, it.serializableAnnotations, FormLayoutPreferences.Block) as FormSelector<Any?>
            Sub(
                it as SerializableProperty<T, Any?>,
                module.form(sel),
                module.view(sel),
            )
        }.filter { it.field.visibility(module) != FieldVisibility.HIDDEN }

        /**
         * Groups fields for form layout.
         *
         * Currently flattens all groups into individual fields (one field per row).
         * The grouping logic groups by field.group but then flattens back to single-item lists.
         *
         * TODO: The grouping logic appears incomplete - it collects groups but doesn't
         * use them effectively. Consider implementing proper multi-field row grouping
         * based on combined width constraints.
         */
        val formGroup: List<List<Sub<*>>> = run {
            val used = HashSet<Sub<*>>()
            val grouped = ArrayList<List<Sub<*>>>()
            subs.groupBy { it.field.group }.forEach { (group, fields) ->
                used += fields
                grouped += fields
            }
            // This flatMap effectively undoes the grouping above
            grouped.flatMap {
                it.map { listOf(it) }
            }.sortedBy { subs.indexOf(it[0]) }
        }

        /**
         * Groups fields for view layout.
         *
         * Currently flattens all groups into individual fields (one field per row).
         * The grouping logic is identical to formGroup.
         *
         * TODO: Same issue as formGroup - grouping logic appears incomplete.
         */
        val viewGroup: List<List<Sub<*>>> = run {
            val used = HashSet<Sub<*>>()
            val grouped = ArrayList<List<Sub<*>>>()
            subs.groupBy { it.field.group }.forEach { (group, fields) ->
                used += fields
                grouped += fields
            }
            // This flatMap effectively undoes the grouping above
            grouped.flatMap {
                it.map { listOf(it) }
            }.sortedBy { subs.indexOf(it[0]) }
        }

        /** Total approximate height for the form layout (sum of row heights) */
        val formApproximateHeight = formGroup.sumOf { it.maxOfOrNull { it.formSize.approximateHeight } ?: 0.0 }

        /** Total approximate height for the view layout (sum of row heights) */
        val viewApproximateHeight = viewGroup.sumOf { it.maxOfOrNull { it.viewSize.approximateHeight } ?: 0.0 }
    }
}

/*
 * TODO: API Improvement Recommendations for ByFieldRenderer
 *
 * 1. Field Grouping: The formGroup and viewGroup logic appears incomplete. It groups by
 *    field.group but then immediately flattens back to individual fields. Consider:
 *    - Implementing proper multi-field row packing based on width constraints
 *    - Respecting explicit field group annotations
 *    - Adding configurable grouping strategies (tight packing vs loose layout)
 *
 * 2. Label Width Calculation: The hardcoded "length * 3/4" character width estimation
 *    is approximate. Consider:
 *    - Making this configurable via FormModule
 *    - Using actual text measurement when available
 *    - Different ratios for different fonts/themes
 *
 * 3. Responsive Layout: The availableWidth is captured at TypeInfo construction time.
 *    Consider:
 *    - Reactive re-layout when window size changes
 *    - Different layouts for mobile vs desktop
 *    - Breakpoint-based field grouping
 *
 * 4. Field Importance: There's commented-out code for field.importance affecting
 *    semantic styling (HeaderSizeSemantic, SubtextSemantic). Consider:
 *    - Implementing or removing this feature completely
 *    - Documenting why it's commented out
 *
 * 5. Card Wrapping: The "if (field != null) card" logic is unclear. Consider:
 *    - Documenting when field is null vs non-null
 *    - Making card wrapping configurable
 *    - Using a more explicit parameter than field nullability
 *
 * 6. Error Handling: No explicit error handling for:
 *    - Serializers that match but can't extract properties
 *    - Circular/recursive type references
 *    - Missing or invalid renderers for field types
 *
 * 7. Performance: TypeInfo is created fresh for each form/view call. Consider:
 *    - Caching TypeInfo instances by serializer
 *    - Lazy initialization of rarely-used properties
 *
 * 8. Testing: Add unit tests for:
 *    - bestPropertiesAttempt() fallback logic
 *    - Field grouping and layout
 *    - Label width calculations
 *    - Sentence format label parsing
 */
