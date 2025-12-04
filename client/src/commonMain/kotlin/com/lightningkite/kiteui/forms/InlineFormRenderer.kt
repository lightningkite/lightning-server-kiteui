package com.lightningkite.kiteui.forms

import com.lightningkite.reactive.lensing.lens
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.SerialKind
import kotlinx.serialization.descriptors.StructureKind
import kotlinx.serialization.internal.GeneratedSerializer

/**
 * Extension function to safely extract child serializers from a KSerializer.
 *
 * Returns null if the serializer is not a GeneratedSerializer (e.g., for primitives or custom serializers).
 * Used primarily by InlineFormRenderer to access the wrapped serializer in inline value classes.
 *
 * IMPORTANT: Uses @OptIn(InternalSerializationApi::class) - this is an unstable API that may change
 * in future kotlinx.serialization versions.
 */
@OptIn(InternalSerializationApi::class)
fun KSerializer<*>.tryChildSerializers(): Array<KSerializer<*>>? = (this as? GeneratedSerializer<*>)?.childSerializers()

/**
 * Renderer for Kotlin inline value classes (value classes with @JvmInline).
 *
 * Inline value classes are a zero-overhead wrapper around a single value. This renderer "unwraps" the
 * inline class to render the underlying value directly, making the wrapper transparent in the UI.
 *
 * Example:
 * ```
 * @Serializable
 * @JvmInline
 * value class UserId(val value: String)
 * ```
 * This renderer will render UserId as if it were just a String field.
 *
 * Key behaviors:
 * - Matches only serializers where descriptor.isInline == true
 * - Extracts the single child serializer (inline classes have exactly one property)
 * - Creates a lens to convert between the wrapper type and inner type
 * - Delegates rendering to the inner type's renderer
 * - Size calculation passed through from inner renderer
 *
 * Priority: 0.4f (lower priority) to allow more specific renderers to match first.
 *
 * GOTCHA: Uses InternalSerializationApi which may break in future kotlinx.serialization updates.
 * Consider migrating to stable API when available.
 */
object InlineFormRenderer : FormRenderer.Generator, ViewRenderer.Generator {
    override val name: String get() = "Inline Wrapper"

    /** Lower priority to allow more specific renderers to match first */
    override val basePriority: Float
        get() = 0.4f

    override val kind: SerialKind? = StructureKind.CLASS

    /**
     * Matches only inline value classes.
     * Checks descriptor.isInline flag set by Kotlin compiler for inline value classes.
     */
    override fun matches(module: FormModule, selector: FormSelector<*>): Boolean {
        return selector.serializer.descriptor.isInline && super<FormRenderer.Generator>.matches(module, selector)
    }

    /**
     * Returns the size of the inner renderer, since inline classes are transparent wrappers.
     *
     * GOTCHA: Uses double !! operator which will throw NPE if:
     * - tryChildSerializers() returns null (not a GeneratedSerializer)
     * - childSerializers array is empty (invalid inline class)
     * These should be prevented by matches() check, but worth noting.
     */
    override fun size(module: FormModule, selector: FormSelector<*>): FormSize {
        val innerSerializer = selector.serializer.tryChildSerializers()!![0]!! as KSerializer<Any>
        val innerSelector = selector.copy(innerSerializer)
        val inner = module.form(innerSelector)
        return inner.size
    }

    /**
     * Creates an editable form renderer that unwraps the inline class.
     *
     * Uses serializationCast to convert between the wrapper type and inner type safely.
     * The lens handles bidirectional conversion:
     * - get: unwraps wrapper -> inner value
     * - set: wraps inner value -> wrapper
     *
     * GOTCHA: Assumes inline class has exactly one property (childSerializers()[0]).
     * This is guaranteed by Kotlin's inline class semantics.
     *
     * GOTCHA: serializationCast can throw if the serializers are incompatible, though
     * this should never happen for valid inline classes.
     */
    @Suppress("UNCHECKED_CAST")
    override fun <T> form(module: FormModule, selector: FormSelector<T>): FormRenderer<T> {
        val innerSerializer = selector.serializer.tryChildSerializers()!![0]!! as KSerializer<Any>
        val innerSelector = selector.copy(innerSerializer)
        val inner = module.form(innerSelector)
        return FormRenderer(module, this, selector as FormSelector<Any?>) { field, mutable ->
            inner.render(
                this,
                field,
                // Lens unwraps and rewraps the inline class transparently
                // The user edits the inner value, but the mutable stores the wrapper type
                mutable.lens(
                    get = { v -> selector.serializer.serializationCast(v, innerSerializer) },
                    set = { v ->
                        innerSerializer.serializationCast(v, selector.serializer)
                    },
                ),
            )
        } as FormRenderer<T>
    }

    /**
     * Creates a read-only view renderer that unwraps the inline class.
     *
     * Similar to form() but only needs a one-way lens (get) since views are read-only.
     *
     * GOTCHA: Like form(), uses double !! operator on tryChildSerializers().
     * Should be safe due to matches() guard, but worth noting for debugging.
     */
    @Suppress("UNCHECKED_CAST")
    override fun <T> view(module: FormModule, selector: FormSelector<T>): ViewRenderer<T> {
        val innerSerializer = selector.serializer.tryChildSerializers()!![0]!! as KSerializer<Any>
        val innerSelector = selector.copy(innerSerializer)
        val inner = module.view(innerSelector)
        return (ViewRenderer(module, this, selector as FormSelector<Any?>) { field, readable ->
            inner.render(
                this,
                field,
                // One-way lens for read-only view - only unwraps, no rewrapping needed
                readable.lens { v ->
                    selector.serializer.serializationCast(v, innerSerializer)
                },
            )
        } as ViewRenderer<T>)
    }
}

/*
 * ========================================
 * API IMPROVEMENT RECOMMENDATIONS
 * ========================================
 *
 * 1. INTERNAL API STABILITY
 *    - Currently uses InternalSerializationApi.childSerializers() which is unstable
 *    - Monitor kotlinx.serialization releases for stable API alternatives
 *    - Add version-specific fallbacks if API changes
 *    - Consider contributing to kotlinx.serialization for stable inline class support
 *
 * 2. NULL SAFETY IMPROVEMENTS
 *    - tryChildSerializers()!![0]!! uses double !! which could throw NPE
 *    - Add explicit error handling with descriptive messages if childSerializers is null/empty
 *    - Validate that inline class has exactly one child before accessing [0]
 *
 * 3. VALIDATION SUPPORT
 *    - Inline classes are often used for validated types (e.g., PositiveInt, EmailAddress)
 *    - Add mechanism to associate validation rules with inline classes
 *    - Consider annotation-based validation for inline class wrappers
 *    - Show validation errors in context of the wrapper type, not just inner type
 *
 * 4. CUSTOM RENDERING HINTS
 *    - Allow inline classes to specify custom rendering via annotation
 *    - E.g., @InlineRenderer("currency") to use specialized renderer
 *    - Useful for domain-specific inline classes (UserId, OrderNumber, etc.)
 *
 * 5. MULTI-PROPERTY INLINE CLASSES
 *    - Current code assumes exactly one property (childSerializers()[0])
 *    - Future Kotlin versions might support multi-property inline classes
 *    - Add detection and appropriate handling for multi-property case
 *
 * 6. SERIALIZATION CAST ERROR HANDLING
 *    - serializationCast can fail if types are incompatible
 *    - Wrap in try-catch with user-friendly error messages
 *    - Log serializer type mismatches for debugging
 *
 * 7. PERFORMANCE OPTIMIZATION
 *    - Cache childSerializers() result to avoid repeated reflection
 *    - Cache innerSerializer at FormRenderer level, not per-render call
 *    - Measure impact on form rendering performance
 *
 * 8. DOCUMENTATION GENERATION
 *    - Generate user-facing docs explaining inline class rendering behavior
 *    - Help developers understand why UserId field looks like String field
 *    - Document best practices for inline class usage in forms
 *
 * 9. DEBUG MODE INDICATORS
 *    - In debug/development mode, show visual indicator that field is inline wrapped
 *    - Helpful for developers to understand data structure
 *    - E.g., tooltip showing "UserId (wraps String)"
 *
 * 10. BACKWARDS COMPATIBILITY
 *     - If InternalSerializationApi changes, provide fallback rendering
 *     - Gracefully degrade to showing wrapper as regular class
 *     - Log warnings when using fallback mode
 */