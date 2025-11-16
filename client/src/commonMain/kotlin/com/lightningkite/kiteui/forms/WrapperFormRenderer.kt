package com.lightningkite.kiteui.forms

import com.lightningkite.services.database.WrappingSerializer

/**
 * Renderer for value classes (inline classes) and other wrapped types.
 *
 * This renderer handles Kotlin value classes and custom wrapper types by unwrapping
 * them to their inner type, delegating to the inner type's renderer, and then wrapping
 * the results back.
 *
 * ## How It Works
 * Value classes like `@JvmInline value class UserId(val value: String)` use a
 * WrappingSerializer that knows how to convert between the wrapper (UserId) and
 * the inner type (String).
 *
 * This renderer:
 * 1. Detects WrappingSerializer types
 * 2. Gets the inner serializer (e.g., String.serializer)
 * 3. Creates a lens to convert wrapper <-> inner
 * 4. Delegates to the inner type's form renderer
 * 5. Passes through the inner renderer's size and handlesField properties
 *
 * ## Matching Rules
 * - Matches any type with a WrappingSerializer
 * - No priority override (uses default 1.0)
 * - Works for both nullable and non-nullable wrappers
 *
 * ## Examples
 * - `value class UserId(val value: String)` - renders as String input
 * - `value class Price(val cents: Int)` - renders as Int input
 * - Custom wrapper types with WrappingSerializer implementations
 *
 * ## Gotchas
 * - Relies on WrappingSerializer.inner() and outer() functions for conversion
 * - The inner renderer's characteristics (size, handlesField) are used directly
 * - No additional UI is added - completely transparent wrapper
 */
object WrapperFormRenderer: FormRenderer.Generator {
    override val name: String = "Wrapper"

    override fun matches(module: FormModule, selector: FormSelector<*>): Boolean {
        return selector.serializer is WrappingSerializer<*, *>
    }

    /**
     * Creates a form renderer that delegates to the inner type's renderer.
     *
     * Uses a lens to convert between the wrapper type and inner type transparently.
     * The resulting renderer is identical to the inner renderer from the user's perspective.
     */
    override fun <T> form(module: FormModule, selector: FormSelector<T>): FormRenderer<T> {
        val innerSer = (selector.serializer as WrappingSerializer<T, Any?>)
        val inner = module.form(selector.copy(serializer = innerSer.getDeferred()))
        return FormRenderer<T>(module, this, selector, size = inner.size, handlesField = inner.handlesField) { field, mutable ->
            // Create a lens that converts: outer <-> inner
            inner.render(this, field, mutable.lens(get = innerSer::inner, set = innerSer::outer))
        }
    }
}

/*
 * TODO: API Improvement Recommendations for WrapperFormRenderer
 *
 * 1. Custom Rendering: Some value classes might want custom rendering despite being wrappers.
 *    Consider:
 *    - Annotation to override wrapper behavior (@CustomRenderer)
 *    - Priority system to allow specialized renderers to take precedence
 *
 * 2. Validation: Value classes often have validation constraints. Consider:
 *    - Supporting @Validated annotation on value classes
 *    - Running validation when converting from inner to outer
 *    - Showing validation errors specific to the wrapper type
 *
 * 3. Display Name: Wrapper type name is lost in rendering. Consider:
 *    - Option to show wrapper type name in labels/hints
 *    - Type-specific hints (e.g., "User ID must be alphanumeric")
 *
 * 4. Error Handling: No handling for conversion failures. Consider:
 *    - Try-catch around inner() and outer() calls
 *    - User-friendly error messages for invalid conversions
 *
 * 5. Multi-Field Wrappers: Currently assumes single-value wrappers. Consider:
 *    - Support for wrappers with multiple fields
 *    - Composite wrappers (e.g., Range(start, end))
 *
 * 6. Documentation: Add examples and guidance for:
 *    - Creating custom WrappingSerializer implementations
 *    - When to use value classes vs regular classes
 *    - Performance implications of wrapping
 *
 * 7. Testing: Add unit tests for:
 *    - Various value class types (String, Int, custom classes)
 *    - Nullable wrappers
 *    - Nested wrappers (wrapper of wrapper)
 *    - Conversion edge cases
 */