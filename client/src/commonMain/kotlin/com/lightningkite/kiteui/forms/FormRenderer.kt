@file:OptIn(ExperimentalSerializationApi::class)

package com.lightningkite.kiteui.forms

import com.lightningkite.kiteui.models.ErrorSemantic
import com.lightningkite.kiteui.navigation.Page
import com.lightningkite.kiteui.navigation.UrlProperties
import com.lightningkite.kiteui.navigation.decodeFromString
import com.lightningkite.kiteui.views.ViewModifiable
import com.lightningkite.kiteui.views.ViewWriter
import com.lightningkite.kiteui.views.centered
import com.lightningkite.kiteui.views.direct.stack
import com.lightningkite.kiteui.views.direct.text
import com.lightningkite.services.database.HasId
import com.lightningkite.services.database.SortPart
import com.lightningkite.lightningserver.db.ModelCache
import com.lightningkite.reactive.context.ReactiveContext
import com.lightningkite.reactive.core.MutableReactive
import com.lightningkite.reactive.core.Reactive
import com.lightningkite.services.database.*
import kotlin.time.Instant
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialKind
import kotlin.js.JsName
import kotlin.jvm.JvmName

/**
 * Base interface for renderer generators.
 *
 * A Generator is a factory that creates [FormRenderer] or [ViewRenderer] instances for specific types.
 * It defines matching criteria ([matches]) and priority scoring ([priority]) to determine which
 * generator should be used for a given type.
 *
 * ## Matching Strategy
 * Generators can match types by:
 * - [type]: Fully-qualified type name (e.g., "kotlin.String")
 * - [kind]: Serialization kind (PRIMITIVE, CLASS, LIST, MAP, etc.)
 * - [annotation]: Specific annotation FQN (e.g., "com.lightningkite.lightningdb.Multiline")
 * - [nullable]: Whether the type is nullable
 *
 * ## Priority Scoring
 * When multiple generators match, [priority] determines which is selected (highest wins).
 * Priority is calculated from:
 * - [basePriority]: Base score (typically 1.0, higher for more specific renderers)
 * - Size fit: Penalty for renderers that exceed [FormSelector.desiredSize] constraints
 * - Field handling bonus: 20% boost if renderer [handlesField] and selector requests it
 *
 * ## Size Constraints
 * The [size] method returns approximate dimensions of the rendered UI. This is used to:
 * - Penalize oversized renderers (reduce priority)
 * - Choose compact renderers for inline contexts
 * - Choose detailed renderers for full-screen contexts
 */
interface RendererGenerator {
    /** Human-readable name for debugging (shown in type picker) */
    val name: String

    /** Match only this serialization kind, or null to match any kind */
    val kind: SerialKind? get() = null

    /** Match only this fully-qualified type name, or null to match any type */
    val type: String? get() = null

    /** Match only nullable or non-nullable types (must match exactly) */
    val nullable: Boolean get() = false

    /** Match only types with this annotation FQN, or null to match any */
    val annotation: String? get() = null

    /**
     * Whether this renderer handles field-level concerns (labels, validation messages).
     * If true and [FormSelector.handlesField] is true, priority is boosted by 20%.
     */
    val handlesField: Boolean get() = false

    /** Returns the approximate size of the rendered UI for priority calculation */
    fun size(module: FormModule, selector: FormSelector<*>): FormSize = FormSize.Inline

    /** Base priority before adjustments (typically 1.0, higher for more specific renderers) */
    val basePriority: Float get() = 1f

    /**
     * Calculates priority for this generator given a selector.
     *
     * Higher priority = more preferred. Priority is calculated as:
     * 1. Start with [basePriority]
     * 2. If [handlesField] matches selector: multiply by 1.2
     * 3. Apply size penalty: divide by (1 + overage%) for width and height separately
     *
     * The size penalty ensures compact renderers are preferred for constrained spaces
     * and detailed renderers for large spaces.
     */
    fun priority(module: FormModule, selector: FormSelector<*>): Float {
        var amount = basePriority
        // Boost priority if field handling matches
        if (handlesField && selector.handlesField) amount *= 1.2f
        // Penalize for size overage
        size(module, selector).let { size ->
            val widthOverage = ((size.approximateWidth - (selector.desiredSize.approximateWidthBound ?: 1000.0)) / (selector.desiredSize.approximateWidthBound ?: 1000.0)).coerceAtLeast(0.0)
            val heightOverage = ((size.approximateHeight - (selector.desiredSize.approximateHeightBound ?: 1000.0)) / (selector.desiredSize.approximateHeightBound ?: 1000.0)).coerceAtLeast(0.0)
            amount *= 1 / (1f + widthOverage.toFloat())
            amount *= 1 / (1f + heightOverage.toFloat())
        }
        return amount
    }

    /**
     * Returns true if this generator can render the given selector.
     *
     * Checks:
     * - Nullability must match exactly
     * - If [type] is set, type name must match
     * - If [kind] is set, serialization kind must match
     * - If [annotation] is set, at least one matching annotation must be present
     */
    fun matches(module: FormModule, selector: FormSelector<*>): Boolean {
        if (nullable != selector.serializer.descriptor.isNullable) return false
        if (type != null && selector.serializer.descriptor.serialName.substringBefore('/') != type) return false
        if (kind != null && selector.serializer.descriptor.kind != kind) return false
        if (annotation != null && selector.annotations.none { it.fqn == annotation }) return false
        return true
    }
}

/**
 * Base interface for all renderers (forms and views).
 * Contains metadata about the renderer and its capabilities.
 */
interface Renderer<T> {
    /** The generator that created this renderer, or null for synthetic renderers */
    val generator: RendererGenerator?

    /** The type selector describing what this renderer handles */
    val selector: FormSelector<T>

    /** Approximate UI size for layout purposes */
    val size: FormSize

    /** Whether this renderer handles field-level concerns (labels, error messages) */
    val handlesField: Boolean
}

/**
 * Renders a type as a read-only view.
 *
 * ViewRenderers display data without allowing editing. They receive a [Reactive] source
 * and render it to the UI, typically updating automatically when the value changes.
 *
 * @param T The type being rendered
 */
interface ViewRenderer<T> : Renderer<T> {
    val module: FormModule

    /**
     * Renders the value to UI.
     *
     * @param field Optional field metadata (name, annotations) if rendering a data class field
     * @param readable The reactive source of values to display
     * @return A view modifier for chaining (typically the root view created)
     */
    val render: ViewWriter.(field: SerializableProperty<*, *>?, readable: Reactive<T>) -> ViewModifiable

    /**
     * Generator interface for creating [ViewRenderer] instances.
     * Implementations define matching criteria and priority, then create renderers via [view].
     */
    interface Generator : RendererGenerator {
        fun <T> view(module: FormModule, selector: FormSelector<T>): ViewRenderer<T>
    }

    companion object {
        /**
         * Convenience constructor for creating [Standard] view renderers.
         * Most generators use this to create renderers with a simple render lambda.
         */
        operator fun <T> invoke(
            module: FormModule,
            generator: ViewRenderer.Generator?,
            selector: FormSelector<T>,
            size: FormSize = generator!!.size(module, selector),
            handlesField: Boolean = generator!!.handlesField,
            render: ViewWriter.(field: SerializableProperty<*, *>?, readable: Reactive<T>) -> ViewModifiable
        ) = Standard(module, generator, selector, size, handlesField, render)
    }

    /**
     * Standard implementation of [ViewRenderer].
     * Holds all metadata and delegates rendering to the provided lambda.
     */
    data class Standard<T>(
        override val module: FormModule,
        override val generator: ViewRenderer.Generator?,
        override val selector: FormSelector<T>,
        override val size: FormSize = generator!!.size(module, selector),
        override val handlesField: Boolean = generator!!.handlesField,
        override val render: ViewWriter.(field: SerializableProperty<*, *>?, readable: Reactive<T>) -> ViewModifiable
    ) : ViewRenderer<T>

    /**
     * Placeholder renderer shown when no suitable renderer is found.
     * Displays an error message with the type name.
     */
    data class Blank<T>(
        override val module: FormModule,
        override val selector: FormSelector<T>
    ) : ViewRenderer<T> {
        override val generator: RendererGenerator? = null
        override val handlesField: Boolean = false
        override val size: FormSize = FormSize.Block
        override val render: ViewWriter.(field: SerializableProperty<*, *>?, readable: Reactive<T>) -> ViewModifiable = { _, _ ->
            ErrorSemantic.onNext - stack {
                centered - text("Blank for ${selector.serializer.displayName}")
            }
        }
    }

    /**
     * Placeholder for recursive types during renderer construction.
     *
     * When building a renderer for a type that references itself (e.g., TreeNode contains TreeNode),
     * this placeholder is returned on recursive calls and later filled with the real renderer.
     *
     * All properties delegate to [current], which starts as [Blank] and is replaced with
     * the real renderer once construction completes.
     *
     * @property used Tracks whether this placeholder was referenced during construction
     * @property overridden True once [current] has been set to the real renderer
     * @property current The actual renderer (starts as Blank, replaced with real renderer)
     */
    class Placeholder<T>(
        override val module: FormModule,
        start: ViewRenderer<T>
    ) : ViewRenderer<T> {
        constructor(module: FormModule, selector: FormSelector<T>) : this(module, Blank(module, selector))

        var used: Boolean = false

        var overridden: Boolean = false
            private set

        var current: ViewRenderer<T> = start
            set(value) {
                overridden = true
                field = value
            }

        override val generator: RendererGenerator? get() = current.generator
        override val handlesField: Boolean get() = current.handlesField
        override val selector: FormSelector<T> get() = current.selector
        override val size: FormSize get() = current.size
        override val render: ViewWriter.(field: SerializableProperty<*, *>?, readable: Reactive<T>) -> ViewModifiable
            get() = current.render
    }
}

/**
 * Renders a type as an editable form.
 *
 * FormRenderers allow users to edit data. They receive a [MutableReactive] source
 * and render input controls that update the value when changed.
 *
 * ## Usage
 * Form renderers are obtained via [FormModule.form] which selects the best renderer
 * based on type, annotations, and desired size. The renderer's [render] function is then
 * invoked to create the UI.
 *
 * ## Implementation Notes
 * - Most implementations use [FormRenderer.Standard] with a render lambda
 * - Renderers should respect [FormSize] constraints for proper layout
 * - Field-aware renderers ([handlesField]=true) include labels and validation messages
 * - Recursive types use [Placeholder] to break circular dependencies
 *
 * @param T The type being edited
 */
interface FormRenderer<T> : Renderer<T> {
    val module: FormModule

    /**
     * Renders the editable form to UI.
     *
     * This lambda is invoked within a [ViewWriter] context to create form controls.
     * The implementation should:
     * 1. Create appropriate input controls (textInput, numberInput, select, etc.)
     * 2. Bind the controls to [mutable] using reactive bindings
     * 3. Optionally display [field] name/label if [handlesField] is true
     * 4. Optionally show validation errors if [handlesField] is true
     *
     * @param field Optional field metadata (name, annotations) if rendering a data class field
     * @param mutable The reactive mutable source to read from and write to
     * @return A view modifier for chaining (typically the root view created)
     */
    val render: ViewWriter.(field: SerializableProperty<*, *>?, mutable: MutableReactive<T>) -> ViewModifiable

    /**
     * Generator interface for creating [FormRenderer] instances.
     * Implementations define matching criteria and priority, then create renderers via [form].
     */
    interface Generator : RendererGenerator {
        fun <T> form(module: FormModule, selector: FormSelector<T>): FormRenderer<T>
    }

    companion object {
        /**
         * Convenience constructor for creating [Standard] form renderers.
         *
         * Most generators use this factory method rather than constructing [Standard] directly.
         * It provides sensible defaults by pulling [size] and [handlesField] from the generator.
         *
         * @param module The form module for accessing other renderers
         * @param generator The generator that created this renderer (can be null for synthetic renderers)
         * @param selector Type information and rendering constraints
         * @param size Approximate UI size (defaults to generator's size calculation)
         * @param handlesField Whether this renderer includes labels/validation (defaults to generator's setting)
         * @param render Lambda that creates the form UI
         */
        operator fun <T> invoke(
            module: FormModule,
            generator: FormRenderer.Generator?,
            selector: FormSelector<T>,
            size: FormSize = generator!!.size(module, selector),
            handlesField: Boolean = generator!!.handlesField,
            render: ViewWriter.(field: SerializableProperty<*, *>?, mutable: MutableReactive<T>) -> ViewModifiable
        ) = Standard(module, generator, selector, size, handlesField, render)
    }

    /**
     * Standard implementation of [FormRenderer].
     *
     * This is the primary implementation used by most form generators. It holds all
     * the renderer metadata and delegates the actual rendering to the provided [render] lambda.
     *
     * The data class equality is based on all properties, enabling effective caching
     * of renderers in [FormModule].
     *
     * @property module The form module for accessing other renderers (e.g., for nested objects)
     * @property generator The generator that created this renderer, or null for synthetic renderers
     * @property selector Type information including serializer, annotations, and size constraints
     * @property size Approximate dimensions of the rendered UI
     * @property handlesField Whether this renderer includes field labels and validation messages
     * @property render Lambda that creates the form UI when invoked
     */
    data class Standard<T>(
        override val module: FormModule,
        override val generator: FormRenderer.Generator?,
        override val selector: FormSelector<T>,
        override val size: FormSize = generator!!.size(module, selector),
        override val handlesField: Boolean = generator!!.handlesField,
        override val render: ViewWriter.(field: SerializableProperty<*, *>?, mutable: MutableReactive<T>) -> ViewModifiable
    ) : FormRenderer<T>

    /**
     * No-op fallback renderer shown when no suitable renderer is found.
     *
     * This renderer is used as a last resort when [FormModule] cannot find any registered
     * generator that matches the requested type. It displays an error message with the type name
     * to help debug missing renderer registrations.
     *
     * ## When You See This
     * If you see "Blank for {TypeName}" in the UI, it means:
     * 1. No generator matched the type's serializer name
     * 2. No generator matched the serialization kind (PRIMITIVE, CLASS, LIST, etc.)
     * 3. The required annotation-based generator wasn't registered
     *
     * ## How to Fix
     * - For primitive types: Ensure [FormModule.defaults] was called
     * - For custom types: Register a generator via [FormModule.register] or [FormModule.plusAssign]
     * - For annotated fields: Ensure the annotation-specific generator is registered
     *
     * @property module The form module (used for consistency with other renderers)
     * @property selector The type selector that couldn't be matched
     */
    data class Blank<T>(
        override val module: FormModule,
        override val selector: FormSelector<T>
    ) : FormRenderer<T> {
        override val generator: RendererGenerator? = null
        override val handlesField: Boolean = false
        override val size: FormSize = FormSize.Block
        override val render: ViewWriter.(field: SerializableProperty<*, *>?, mutable: MutableReactive<T>) -> ViewModifiable = { _, _ ->
            ErrorSemantic.onNext - stack {
                centered - text("Blank for ${selector.serializer.displayName}")
            }
        }
    }

    /**
     * Placeholder for recursive types during renderer construction.
     *
     * When building a renderer for a recursive type (e.g., `TreeNode` contains `List<TreeNode>`),
     * this placeholder breaks the circular dependency. During construction, the placeholder is
     * returned for recursive references, then filled with the real renderer once construction completes.
     *
     * ## How It Works
     * 1. [FormModule.form] detects a type is already being constructed (cycle detected)
     * 2. Returns a [Placeholder] that initially wraps a [Blank] renderer
     * 3. Child renderers receive and store the placeholder
     * 4. Once the parent renderer is fully constructed, [current] is set to the real renderer
     * 5. All child renderers now delegate to the real renderer via the placeholder
     *
     * ## State Tracking
     * - [used]: Set to true when the placeholder is returned to a caller (tracks if recursion occurred)
     * - [overridden]: Set to true once [current] is replaced with the real renderer
     * - [current]: The actual renderer (starts as [Blank], replaced when construction completes)
     *
     * ## Gotcha
     * All properties delegate to [current], so the placeholder dynamically reflects the
     * real renderer's metadata once it's been set.
     *
     * @property module The form module for accessing other renderers
     * @param start Initial renderer (typically [Blank]) used until real renderer is ready
     */
    class Placeholder<T>(
        override val module: FormModule,
        start: FormRenderer<T>
    ) : FormRenderer<T> {
        /**
         * Secondary constructor that creates a placeholder starting with a [Blank] renderer.
         * This is the most common usage pattern.
         */
        constructor(module: FormModule, selector: FormSelector<T>) : this(module, Blank(module, selector))

        /**
         * Tracks whether this placeholder was returned during renderer construction.
         * If false, the type wasn't actually recursive and the placeholder was unused.
         */
        var used: Boolean = false

        /**
         * True once [current] has been set to the real renderer.
         * Used to verify the placeholder was properly resolved after construction.
         */
        var overridden: Boolean = false
            private set

        /**
         * The actual renderer to delegate to.
         * Starts as [start] (typically [Blank]), then replaced with the real renderer.
         * Setting this property automatically sets [overridden] to true.
         */
        var current: FormRenderer<T> = start
            set(value) {
                overridden = true
                field = value
            }

        // All properties delegate to the current renderer for transparent placeholder behavior
        override val generator: RendererGenerator? get() = current.generator
        override val handlesField: Boolean get() = current.handlesField
        override val selector: FormSelector<T> get() = current.selector
        override val size: FormSize get() = current.size
        override val render: ViewWriter.(field: SerializableProperty<*, *>?, mutable: MutableReactive<T>) -> ViewModifiable
            get() = current.render
    }
}

/**
 * Describes what type of renderer is needed, including type information and rendering constraints.
 *
 * A [FormSelector] is passed to [RendererGenerator.matches] and [RendererGenerator.priority]
 * to determine which renderer should be used. It contains:
 * - Type information ([serializer])
 * - Field annotations (e.g., @Multiline, @MaxLength)
 * - Size preferences ([desiredSize])
 * - Field handling preference ([handlesField])
 *
 * ## Usage Pattern
 * ```kotlin
 * val selector = FormSelector(
 *     serializer = String.serializer(),
 *     annotations = listOf(MultilineAnnotation),
 *     desiredSize = FormLayoutPreferences.Block
 * )
 * val renderer = formModule.form(selector)
 * ```
 *
 * @param T The type being rendered
 * @property serializer Kotlinx.serialization serializer for the type
 * @property annotations List of annotations from the field or type (used for matching annotation-based renderers)
 * @property desiredSize Size constraints for the renderer (inline, block, or screen-bound)
 * @property handlesField Whether the renderer should include field labels and validation (boosts priority for field-aware renderers)
 */
data class FormSelector<T>(
    val serializer: KSerializer<T>,
    val annotations: List<SerializableAnnotation>,
    val desiredSize: FormLayoutPreferences = FormLayoutPreferences.Block,
    val handlesField: Boolean = false,
) {
    override fun toString(): String = serializer.descriptor.serialName

    /**
     * Creates a copy with a different type parameter.
     *
     * This is used when transforming selectors for related types (e.g., T to T? for nullable renderers).
     * The type-changing copy requires explicit naming to avoid ambiguity with the data class copy method.
     */
    @Suppress("UNCHECKED_CAST")
    @JvmName("copyChangingType")
    @JsName("copyChangingType")
    fun <O> copy(
        serializer: KSerializer<O>,
        annotations: List<SerializableAnnotation> = this.annotations,
        desiredSize: FormLayoutPreferences = this.desiredSize,
        handlesField: Boolean = this.handlesField,
    ) = FormSelector<O>(
        serializer = serializer,
        annotations = annotations,
        desiredSize = desiredSize,
        handlesField = handlesField,
    )
}

/**
 * Determines the natural sort order for a type.
 *
 * This function examines the type's metadata to determine how instances should be sorted
 * by default. The priority order is:
 * 1. @NaturalSort annotation (explicit sort configuration)
 * 2. String-based _id field (ascending, for named identifiers)
 * 3. Instant/timestamp field (descending, for time-based data)
 * 4. First available field (ascending, as a fallback)
 *
 * ## Usage
 * Used by admin panels and collection views to determine default sort order when displaying lists.
 *
 * @return List of sort parts defining the natural sort order, or empty list if no sortable fields exist
 */
fun <T> KSerializer<T>.naturalSort(): List<SortPart<T>> {
    return serializableAnnotations.find {
        it.fqn == "com.lightningkite.lightningdb.NaturalSort"
    }?.values?.entries?.firstOrNull()?.let { it.value as? SerializableAnnotationValue.ArrayValue }?.value?.mapNotNull {
        (it as? SerializableAnnotationValue.StringValue)?.value?.let {
            UrlProperties.decodeFromString(SortPart.serializer(this@naturalSort), it)
        }
    } ?: serializableProperties?.find { it.name == "_id" && it.serializer == String.serializer() }?.let {
        // Named IDs (e.g., usernames, slugs) - sort ascending
        listOf(SortPart(DataClassPathAccess(DataClassPathSelf(this), it as SerializableProperty<T, String>), ascending = true))
    } ?: serializableProperties?.find { !it.serializer.descriptor.isNullable &&  it.serializer.descriptor.serialName.substringBefore('/') == "kotlinx.datetime.Instant" }?.let {
        // Timestamps - sort descending (most recent first)
        listOf(SortPart(DataClassPathAccess(DataClassPathSelf(this), it as SerializableProperty<T, Instant>), ascending = false))
    } ?: serializableProperties?.firstOrNull()?.let {
        // Fallback: use first field, whatever it is
        listOf(
            SortPart(
                DataClassPathAccess(
                    DataClassPathSelf(this),
                    it
                ), ascending = true
            )
        )
    } ?: listOf()
}
/**
 * Determines which fields should be shown as columns in a table view.
 *
 * Returns a list of data class paths representing the fields to display as columns.
 * The selection strategy is:
 * 1. Use @AdminTableColumns annotation if present (explicit configuration)
 * 2. Otherwise, select the 5 most important fields based on [SerializableProperty.importance]
 *
 * ## Usage
 * Used by admin panels to configure which fields are visible in list/table views of this type.
 *
 * @return List of paths to fields that should be displayed as columns
 */
fun <T> KSerializer<T>.defaultColumns(): List<DataClassPath<T, *>> {
    return serializableAnnotations.find {
        it.fqn == "com.lightningkite.lightningdb.AdminTableColumns"
    }?.values?.entries?.firstOrNull()?.let { it.value as? SerializableAnnotationValue.ArrayValue }?.value?.mapNotNull {
        (it as? SerializableAnnotationValue.StringValue)?.value?.let {
            UrlProperties.decodeFromString(DataClassPathPartial.serializer(this), it) as DataClassPath<T, *>
        }
    } ?: serializableProperties!!.sortedBy {
        it.importance // Lower importance values = more important fields
    }.take(5).map {
        DataClassPathAccess(DataClassPathSelf(this), it)
    }
}
/**
 * Determines which fields should be used to display a human-readable title for this type.
 *
 * Returns a list of data class paths whose values will be combined to form a display title.
 * This is used when showing references to this type (e.g., in dropdowns, foreign key displays).
 *
 * ## Selection Strategy
 * 1. @AdminTitleFields annotation (explicit configuration)
 * 2. Field named "name"
 * 3. Field named "title"
 * 4. Field named "subject"
 * 5. Field named "label"
 * 6. Non-UUID _id field
 * 7. First 3 fields (as a last resort)
 *
 * ## Usage
 * ```kotlin
 * // For a User with fields: _id, username, email, createdAt
 * // Would return: [username] (matched by "name"-like convention)
 * val titleFields = User.serializer().defaultTitleFields()
 * ```
 *
 * @return List of paths to fields that should be used to display a title/label for instances
 */
fun <T> KSerializer<T>.defaultTitleFields(): List<DataClassPath<T, *>> {
    val serializer = this
    val it = serializer.serializableProperties!!
    val dcps = DataClassPathSerializer(serializer)
    val nameFields: List<DataClassPath<T, *>> = serializer.serializableAnnotations.find {
        it.fqn == "com.lightningkite.lightningdb.AdminTitleFields"
    }?.values?.get("fields")?.let { it as? SerializableAnnotationValue.ArrayValue }
        ?.value
        ?.mapNotNull { it as? SerializableAnnotationValue.StringValue }
        ?.map { it.value }
        ?.toSet()
        ?.let { matching ->
            matching.map { dcps.fromString(it) as DataClassPath<T, *> }
        }
        ?: it.find { it.name == "name" }?.let { DataClassPathAccess(DataClassPathSelf(serializer), it) }?.let(::listOf)
        ?: it.find { it.name == "title" }?.let { DataClassPathAccess(DataClassPathSelf(serializer), it) }?.let(::listOf)
        ?: it.find { it.name == "subject" }?.let { DataClassPathAccess(DataClassPathSelf(serializer), it) }?.let(::listOf)
        ?: it.find { it.name == "label" }?.let { DataClassPathAccess(DataClassPathSelf(serializer), it) }?.let(::listOf)
        ?: it.find { it.name == "_id" && !it.serializer.descriptor.serialName.contains("Uuid") }?.let { DataClassPathAccess(DataClassPathSelf(serializer), it) }?.let(::listOf)
        ?: it.map { DataClassPathAccess(DataClassPathSelf(serializer), it) }.take(3) // Fallback: first 3 fields
    return nameFields
}

/**
 * Metadata for rendering a database-backed type with foreign key support.
 *
 * This class bundles together all the information needed to render foreign key relationships
 * for a Lightning Server model type. It's used by [ForeignKeyRenderer] to display and edit
 * references to other entities.
 *
 * @param T The model type (must have an ID)
 * @param ID The ID type (must be comparable for sorting)
 * @property serializer Kotlinx.serialization serializer for the type
 * @property cache Function to access the ModelCache for this type in a reactive context
 * @property page Function to get a navigation page for viewing an instance by ID
 * @property titleFields Fields to use when displaying a title for an instance (defaults to [defaultTitleFields])
 * @property renderToString Function to convert an ID to a display string (for showing selected values)
 */
class FormTypeInfo<T : HasId<ID>, ID : Comparable<ID>>(
    val serializer: KSerializer<T>,
    val cache: ReactiveContext.() -> ModelCache<T, ID>,
    val page: ReactiveContext.(ID) -> (() -> Page)?,
    val titleFields: List<DataClassPath<T, *>> = serializer.defaultTitleFields(),
    val renderToString: (suspend (ID) -> String)
)

/**
 * Convenience function to render an editable form for a value.
 *
 * This is a shorthand for creating a [FormSelector] and rendering it. It's the primary
 * entry point for creating forms from user code.
 *
 * ## Example
 * ```kotlin
 * viewWriter.form(
 *     context = formModule,
 *     serializer = User.serializer(),
 *     mutable = userSignal
 * )
 * ```
 *
 * @param context The form module containing registered renderers
 * @param serializer Kotlinx.serialization serializer for the type
 * @param mutable The mutable reactive value to edit
 * @param annotations Optional annotations (defaults to serializer's annotations)
 * @param desiredSize Size preferences for the form layout
 * @param field Optional field metadata if rendering a data class field
 * @return The root view created by the renderer
 */
fun <T> ViewWriter.form(
    context: FormModule,
    serializer: KSerializer<T>,
    mutable: MutableReactive<T>,
    annotations: List<SerializableAnnotation> = serializer.serializableAnnotations,
    desiredSize: FormLayoutPreferences = FormLayoutPreferences.ScreenBound,
    field: SerializableProperty<*, *>? = null,
): ViewModifiable {
    val sel = FormSelector<T>(serializer, annotations, desiredSize)
    return context.form(sel).render(this, field, mutable)
}

/**
 * Convenience function to render a read-only view for a value.
 *
 * This is a shorthand for creating a [FormSelector] and rendering a view. It's the primary
 * entry point for creating read-only displays from user code.
 *
 * ## Example
 * ```kotlin
 * viewWriter.view(
 *     context = formModule,
 *     serializer = User.serializer(),
 *     readable = userReadable
 * )
 * ```
 *
 * @param context The form module containing registered renderers
 * @param serializer Kotlinx.serialization serializer for the type
 * @param readable The reactive value to display
 * @param annotations Optional annotations (defaults to serializer's annotations)
 * @param desiredSize Size preferences for the view layout
 * @param field Optional field metadata if rendering a data class field
 * @return The root view created by the renderer
 */
fun <T> ViewWriter.view(
    context: FormModule,
    serializer: KSerializer<T>,
    readable: Reactive<T>,
    annotations: List<SerializableAnnotation> = serializer.serializableAnnotations,
    desiredSize: FormLayoutPreferences = FormLayoutPreferences.ScreenBound,
    field: SerializableProperty<*, *>? = null,
): ViewModifiable {
    val sel = FormSelector<T>(serializer, annotations, desiredSize)
    return context.view(sel).render(this, field, readable)
}

/*
 * TODO: API Improvement Recommendations
 *
 * 1. **Type Safety for Placeholder Resolution**
 *    - Add compile-time or runtime verification that Placeholder.current is always set before use
 *    - Consider making Placeholder.current a lazy delegate that throws if accessed before being set
 *    - Add debug logging when placeholders are created and resolved to aid troubleshooting
 *
 * 2. **Renderer Selection Transparency**
 *    - Add a debug mode that logs which generators were considered and why they were/weren't selected
 *    - Include a helper function to explain why a specific renderer was chosen for a type
 *    - Consider adding a FormModule.explain(selector) function that returns selection reasoning
 *
 * 3. **Generator Priority Calculation**
 *    - The size penalty calculation could be more intuitive - consider using a simpler formula
 *    - Document the exact priority values returned by common generators for reference
 *    - Add a way to visualize generator priorities for a given selector (dev tool)
 *
 * 4. **Error Messages**
 *    - Blank renderer error message could include suggestions for how to fix (e.g., "Did you call defaults()?")
 *    - Add serializer.displayName fallback for types without a friendly name
 *    - Include the selector's desired size and annotations in the error message for debugging
 *
 * 5. **FormSelector Improvements**
 *    - Consider adding a builder DSL for FormSelector construction
 *    - Add validation that desiredSize constraints are reasonable (e.g., no negative dimensions)
 *    - Provide named factory functions like FormSelector.inline(), FormSelector.block(), etc.
 *
 * 6. **Annotation Matching**
 *    - The FQN-based annotation matching is fragile across package refactors
 *    - Consider using annotation class references instead of string FQNs where possible
 *    - Add support for annotation inheritance (e.g., @MyCustomMultiline extends @Multiline)
 *
 * 7. **Default Field Selection Heuristics**
 *    - naturalSort/defaultColumns/defaultTitleFields have a lot of duplication
 *    - Extract common field importance/relevance logic into reusable helpers
 *    - Consider making these strategies pluggable/customizable per-application
 *
 * 8. **Performance Optimizations**
 *    - Cache the results of naturalSort/defaultColumns/defaultTitleFields per serializer
 *    - Consider caching renderer instances more aggressively (currently via FormModule)
 *    - Profile generator matching performance for types with many annotations
 *
 * 9. **Documentation**
 *    - Add a comprehensive guide showing how to create custom generators
 *    - Include examples of common generator patterns (annotation-based, size-based, etc.)
 *    - Document the lifecycle of a renderer from generation to rendering
 *
 * 10. **Testing Utilities**
 *     - Add test helpers to assert which renderer was selected for a type
 *     - Provide mock generators for unit testing UI code
 *     - Add a way to dump the entire generator registry for inspection
 */