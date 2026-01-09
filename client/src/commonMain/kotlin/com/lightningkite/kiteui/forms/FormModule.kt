package com.lightningkite.kiteui.forms

import com.lightningkite.kiteui.FileReference
import com.lightningkite.kiteui.models.SubtextSemantic
import com.lightningkite.kiteui.models.px
import com.lightningkite.kiteui.models.rem
import com.lightningkite.kiteui.views.atTopEnd
import com.lightningkite.kiteui.views.direct.frame
import com.lightningkite.kiteui.views.direct.row
import com.lightningkite.kiteui.views.direct.select
import com.lightningkite.kiteui.views.direct.sizeConstraints
import com.lightningkite.kiteui.views.expanding
import com.lightningkite.reactive.context.reactive
import com.lightningkite.reactive.core.Constant
import com.lightningkite.reactive.core.Signal
import com.lightningkite.services.files.ServerFile
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.descriptors.SerialKind
import kotlinx.serialization.modules.EmptySerializersModule

/**
 * Central registry and configuration for the pluggable form/view rendering system.
 *
 * FormModule manages how different data types are rendered as forms (editable) and views (read-only).
 * It uses a plugin-based architecture where [FormRenderer.Generator] and [ViewRenderer.Generator]
 * implementations register themselves and are selected based on:
 * - Type matching (serializer name)
 * - Kind matching (serialization kind: primitive, class, list, etc.)
 * - Annotation matching (e.g., @Multiline, @References, @AdminHidden)
 * - Priority scoring
 *
 * ## Architecture
 *
 * ### Selection Process
 * When rendering a type T:
 * 1. Create a [FormSelector] describing T (serializer, annotations, size constraints)
 * 2. Gather candidates from registered generators (by type, kind, annotation, or "other")
 * 3. Filter candidates by [Generator.matches]
 * 4. Sort by [Generator.priority] (highest first)
 * 5. Instantiate the highest-priority renderer
 * 6. Cache the result for reuse
 *
 * ### Caching Strategy
 * Renderers are cached by [FormSelector] to:
 * - Avoid recomputing which generator to use
 * - Handle recursive/nested data structures (e.g., tree nodes referencing themselves)
 * - Ensure consistent rendering for the same type throughout the app
 *
 * When building a renderer for a recursive type, a [Placeholder] is used to break the cycle.
 * The placeholder is later filled with the actual renderer once construction completes.
 *
 * ### Registration
 * Generators are registered via [plusAssign]:
 * ```kotlin
 * formModule += MyCustomRenderer.Generator
 * ```
 *
 * They're stored in maps indexed by:
 * - [form_type] / [view_type]: By fully-qualified type name
 * - [form_kind] / [view_kind]: By Kotlin serialization kind (CLASS, PRIMITIVE, LIST, etc.)
 * - [form_annotation] / [view_annotation]: By annotation FQN
 * - [form_others] / [view_others]: Fallback for unindexed generators
 *
 * ## Configuration
 *
 * @property module Kotlinx serialization module for polymorphic type resolution
 * @property showTypePicker Debug mode: shows dropdown to select between candidate renderers
 * @property fileUpload Function to upload files to server, used by file upload renderers
 * @property typeInfo Function to get metadata about a type (for foreign key rendering, etc.)
 * @property visibilitySettings Maps annotation FQNs to visibility rules (hidden, read-only, etc.)
 *
 * ## Usage Example
 * ```kotlin
 * val formModule = FormModule().apply {
 *     // Register custom renderer
 *     this += MyCustomDateRenderer
 *
 *     // Configure file upload
 *     fileUpload = { file -> api.uploadFile(file) }
 *
 *     // Hide fields with @Internal
 *     visibilitySettings["com.myapp.Internal"] = FieldVisibility.HIDDEN
 * }
 *
 * // Render a form for User type
 * val userRenderer = formModule.form(FormSelector(User.serializer()))
 * userRenderer.render(viewWriter, field, mutableUser)
 * ```
 */
@OptIn(ExperimentalSerializationApi::class)
class FormModule {
    /** Kotlinx serialization module for polymorphic type information */
    var module = EmptySerializersModule()

    /**
     * Debug mode: when true, shows a dropdown to select between candidate renderers.
     * Useful for testing which renderers match a type and their priorities.
     */
    var showTypePicker = false

    /**
     * Function to upload files to the server.
     * Used by [ServerFileRenderer] and other file upload renderers.
     * Should return a [ServerFile] with the uploaded file's URL/ID.
     */
    var fileUpload: (suspend (FileReference) -> ServerFile)? = null

    /**
     * Function to get type metadata for rendering foreign keys and references.
     * Returns [FormTypeInfo] with display name, icon, and field accessors.
     * Used by [ForeignKeyRenderer] to show referenced objects.
     */
    var typeInfo: (type: String) -> FormTypeInfo<*, *>? = { _ -> println("WARN: Empty form context"); null }

    /**
     * Maps annotation fully-qualified names to visibility rules.
     *
     * Controls whether fields with specific annotations should be:
     * - [FieldVisibility.HIDDEN]: Not rendered at all
     * - [FieldVisibility.READ]: Rendered read-only (even in forms)
     * - [FieldVisibility.WRITE]: Normal editable rendering
     *
     * Default settings hide admin-only or denormalized fields.
     */
    val visibilitySettings: MutableMap<String, FieldVisibility> = mutableMapOf(
        "com.lightningkite.lightningdb.AdminHidden" to FieldVisibility.HIDDEN,
        "com.lightningkite.lightningdb.Denormalized" to FieldVisibility.READ,
        "com.lightningkite.lightningdb.AdminViewOnly" to FieldVisibility.READ
    )

    // ============================================================================
    // Form Renderer Registration Storage
    // ============================================================================

    /** Generators that don't specify type/kind/annotation (lowest priority fallback) */
    private val form_others: ArrayList<FormRenderer.Generator> = ArrayList()

    /** Generators indexed by fully-qualified type name (e.g., "com.example.User") */
    private val form_type: HashMap<String, ArrayList<FormRenderer.Generator>> = HashMap()

    /** Generators indexed by serialization kind (PRIMITIVE, CLASS, LIST, etc.) */
    private val form_kind: HashMap<SerialKind, ArrayList<FormRenderer.Generator>> = HashMap()

    /** Generators indexed by annotation FQN (e.g., "com.lightningkite.lightningdb.Multiline") */
    private val form_annotation: HashMap<String, ArrayList<FormRenderer.Generator>> = HashMap()

    /** All registered form generators (flattened from all indices) */
    val allForms get() = form_others + form_type.values.flatten() + form_kind.values.flatten() + form_annotation.values.flatten()

    /**
     * Gathers candidate form generators for a given type.
     *
     * Order of yielding:
     * 1. Type-specific generators (highest specificity)
     * 2. Kind-specific generators
     * 3. Annotation-specific generators (all matching annotations)
     * 4. Generic generators (lowest specificity)
     *
     * Callers then filter by [matches] and sort by [priority].
     */
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

    // ============================================================================
    // View Renderer Registration Storage (parallel structure to forms)
    // ============================================================================

    private val view_others: ArrayList<ViewRenderer.Generator> = ArrayList()
    private val view_type: HashMap<String, ArrayList<ViewRenderer.Generator>> = HashMap()
    private val view_kind: HashMap<SerialKind, ArrayList<ViewRenderer.Generator>> = HashMap()
    private val view_annotation: HashMap<String, ArrayList<ViewRenderer.Generator>> = HashMap()

    /** All registered view generators (flattened from all indices) */
    val allViews get() = view_others + view_type.values.flatten() + view_kind.values.flatten() + view_annotation.values.flatten()

    /** Gathers candidate view generators for a given type. See [formCandidates] for details. */
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

    // ============================================================================
    // View Renderer Caching (handles recursive types)
    // ============================================================================

    /** Cache of fully-constructed view renderers by selector */
    private val viewCache = HashMap<FormSelector<*>, ViewRenderer<*>>()

    /** Tracks renderers currently being constructed (for cycle detection) */
    private val currentlyMakingViews = HashMap<FormSelector<*>, ViewRenderer.Placeholder<*>>()

    /**
     * Gets or creates a cached view renderer, handling recursive types.
     *
     * ## Recursive Type Handling
     * When generating a renderer for a recursive type (e.g., TreeNode has TreeNode children):
     * 1. First call: Create [ViewRenderer.Placeholder] and add to [currentlyMakingViews]
     * 2. During construction: Recursive call returns the placeholder
     * 3. After construction: Replace placeholder's [current] with real renderer
     * 4. Cache the result (either placeholder if used, or real renderer if not)
     *
     * The placeholder tracks [ViewRenderer.Placeholder.used] to determine if it was actually
     * referenced during construction. If not used, we return the real renderer directly.
     *
     * @param key The type selector
     * @param generate Function to construct the renderer (only called if not cached)
     * @return Cached or newly-constructed renderer
     */
    @Suppress("UNCHECKED_CAST")
    private fun <T> viewCache(key: FormSelector<T>, generate: ()->ViewRenderer<T>): ViewRenderer<T> {
        return when (key) {
            // Already fully constructed - return cached
            in viewCache.keys -> viewCache[key] as ViewRenderer<T>
            // Currently being constructed - return placeholder to break cycle
            in currentlyMakingViews.keys -> currentlyMakingViews[key]!!.also { it.used = true } as ViewRenderer<T>
            // New renderer - construct with cycle detection
            else -> {
                // Create placeholder in case of recursive reference
                currentlyMakingViews[key] = ViewRenderer.Placeholder(this@FormModule, key)
                // Generate the actual renderer (may recursively reference this type)
                val result = generate()
                // Cache the result
                viewCache[key] = result
                // If placeholder was used (recursive reference detected), fill it with real renderer
                (currentlyMakingViews.remove(key) as? ViewRenderer.Placeholder<T>)?.let {
                    if (it.used) {
                        it.current = result
                        it // Return placeholder (which now delegates to real renderer)
                    } else result // Return real renderer directly (no recursion)
                } ?: result
            }
        }
    }

    /**
     * Gets a view renderer for the given type selector.
     *
     * ## Process
     * 1. Check cache - return if available
     * 2. Gather candidates via [viewCandidates]
     * 3. Filter by [Generator.matches]
     * 4. Sort by [Generator.priority] descending
     * 5. If [showTypePicker] is false: return highest priority renderer
     * 6. If [showTypePicker] is true: return renderer with dropdown to select between options
     * 7. Cache and return result
     *
     * @param key Type selector describing the type to render
     * @return View renderer for the type
     */
    fun <T> view(key: FormSelector<T>): ViewRenderer<T> = viewCache(key) {
        val options = viewCandidates(key).filter { it.matches(this, key) }.sortedByDescending { it.priority(this, key) }.map { it.view(this, key) }.toList()
        if (!showTypePicker) options.first()
        else ViewRenderer(this, null, key, size = options.first().size, handlesField = options.first().handlesField) { field, mutable ->
            val selected = Signal(options.first())
            row {
//                gap = 0.px
                expanding.frame {
                    reactive {
                        val sel = selected()
                        clearChildren()
                        sel.render(this@frame, field, mutable)
                    }
                }
                sizeConstraints(width = 0.75.rem, height = 0.75.rem).atTopEnd.themed(SubtextSemantic).select {
                    gap = 0.px
                    bind(selected, Constant(options)) {
                        (it.generator?.name ?: "-") + " (${
                            it.generator?.priority(
                                this@FormModule,
                                key
                            )
                        }, ${it.size.approximateWidth} x ${it.size.approximateHeight})"
                    }
                }
            }
        }
    }

    // ============================================================================
    // Form Renderer Caching (parallel structure to view caching)
    // ============================================================================

    /** Cache of fully-constructed form renderers by selector */
    private val formCache = HashMap<FormSelector<*>, FormRenderer<*>>()

    /** Tracks form renderers currently being constructed (for cycle detection) */
    private val currentlyMakingForms = HashMap<FormSelector<*>, FormRenderer.Placeholder<*>>()

    /**
     * Gets or creates a cached form renderer, handling recursive types.
     * See [viewCache] for detailed explanation - this works identically but for forms.
     */
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

    /**
     * Gets a form renderer for the given type selector.
     * See [view] for detailed explanation - this works identically but for editable forms.
     *
     * @param key Type selector describing the type to render
     * @return Form renderer for the type
     */
    fun <T> form(key: FormSelector<T>): FormRenderer<T> = formCache(key) {
        val options = formCandidates(key).filter { it.matches(this, key) }.sortedByDescending { it.priority(this, key) }.map { it.form(this, key) }.toList()
        if (!showTypePicker) options.first()
        else FormRenderer(this, null, key, size = options.first().size, handlesField = options.first().handlesField) { field, mutable ->
            val selected = Signal(options.first())
            row {
//                gap = 0.px
                expanding.frame {
                    reactive {
                        val sel = selected()
                        clearChildren()
                        sel.render(this@frame, field, mutable)
                    }
                }
                sizeConstraints(width = 0.75.rem, height = 0.75.rem).themed(SubtextSemantic).atTopEnd.select {
                    gap = 0.px
                    bind(selected, Constant(options)) { (it.generator?.name ?: "-") + " (${it.generator?.priority(this@FormModule, key)}, ${it.size.approximateWidth} x ${it.size.approximateHeight})" }
                }
            }
        }
    }

    // ============================================================================
    // Generator Registration
    // ============================================================================

    /**
     * Registers a form renderer generator.
     *
     * The generator is stored in the most specific index:
     * 1. If [Generator.annotation] is set: index by annotation FQN
     * 2. Else if [Generator.type] is set: index by type name
     * 3. Else if [Generator.kind] is set: index by serialization kind
     * 4. Else: add to generic fallback list
     *
     * Usage:
     * ```kotlin
     * formModule += MyCustomRenderer.Generator
     * ```
     */
    operator fun plusAssign(generator: FormRenderer.Generator) {
        generator.annotation?.let { form_annotation.getOrPut(it) { ArrayList() }.add(generator) }
            ?: generator.type?.let { form_type.getOrPut(it) { ArrayList() }.add(generator) }
            ?: generator.kind?.let { form_kind.getOrPut(it) { ArrayList() }.add(generator) }
            ?: form_others.add(generator)
    }

    /**
     * Registers a view renderer generator.
     * See [plusAssign] (FormRenderer.Generator overload) for details.
     */
    operator fun plusAssign(generator: ViewRenderer.Generator) {
        generator.annotation?.let { view_annotation.getOrPut(it) { ArrayList() }.add(generator) }
            ?: generator.type?.let { view_type.getOrPut(it) { ArrayList() }.add(generator) }
            ?: generator.kind?.let { view_kind.getOrPut(it) { ArrayList() }.add(generator) }
            ?: view_others.add(generator)
    }

    init {
        // Register all built-in renderers (primitives, collections, nullables, etc.)
        // See builtinRenderers.kt for implementations
        defaults()
    }
}

/*
 * TODO: API Improvement Recommendations
 *
 * 1. Generator Priority System
 *    Current: Generators define their own priority as an Int
 *    Problem: No central documentation of priority ranges, conflicts possible
 *    Suggestion: Define priority constants (HIGH=1000, MEDIUM=500, LOW=100) and document ranges
 *
 * 2. Cache Invalidation
 *    Current: Caches are never cleared, grow unbounded
 *    Problem: Memory leaks if many different FormSelectors are created dynamically
 *    Suggestion: Add cache clearing API, weak references, or LRU eviction
 *
 * 3. Generator Unregistration
 *    Current: No way to remove a registered generator
 *    Suggestion: Add minusAssign operator or unregister() method
 *
 * 4. Multiple Module Support
 *    Current: Single global-ish FormModule per app
 *    Problem: Hard to have different rendering styles in different parts of the app
 *    Suggestion: Support scoped/nested FormModules or renderer overrides
 *
 * 5. Generator Conflict Detection
 *    Current: If multiple generators have same priority, first one wins (order-dependent)
 *    Problem: Non-deterministic behavior if registration order changes
 *    Suggestion: Warn or error on equal priorities for same selector
 *
 * 6. Type Picker Performance
 *    Current: showTypePicker instantiates ALL candidate renderers even if not selected
 *    Problem: Expensive if many candidates exist
 *    Suggestion: Lazy instantiation - only create renderer when selected
 *
 * 7. Recursive Type Depth Limit
 *    Current: No depth limit on recursive rendering
 *    Problem: Deep recursion could overflow stack or create huge UI
 *    Suggestion: Add max depth parameter to form()/view(), use placeholder after limit
 *
 * 8. Field Visibility API
 *    Current: visibilitySettings is mutable map, no type safety
 *    Suggestion: Structured API with type-safe annotation references
 *
 * 9. Generator Debugging
 *    Current: Limited visibility into why a generator was/wasn't selected
 *    Suggestion: Add debug mode that logs matching process and priority calculations
 *
 * 10. Annotation Inheritance
 *     Current: Only direct annotations on fields are checked
 *     Problem: Can't use meta-annotations or annotation inheritance
 *     Suggestion: Support annotation inheritance chains
 */