package com.lightningkite.kiteui.forms

import com.lightningkite.reactive.lensing.lens
import com.lightningkite.services.database.WrappingSerializer

/**
 * View renderer for value classes (inline classes) and other wrapped types.
 *
 * This is the read-only counterpart to WrapperFormRenderer. It handles displaying
 * value classes by unwrapping them and delegating to the inner type's view renderer.
 *
 * ## How It Works
 * Similar to WrapperFormRenderer, but for read-only views:
 * 1. Detects WrappingSerializer types
 * 2. Gets the inner serializer
 * 3. Creates a read-only lens to convert wrapper -> inner
 * 4. Delegates to the inner type's view renderer
 * 5. Passes through the inner renderer's size and handlesField properties
 *
 * ## Matching Rules
 * - Matches any type with a WrappingSerializer
 * - No priority override (uses default 1.0)
 * - Works for both nullable and non-nullable wrappers
 *
 * ## Examples
 * - `value class UserId(val value: String)` - displays as String
 * - `value class Price(val cents: Int)` - displays as Int
 * - Custom wrapper types with WrappingSerializer implementations
 *
 * ## Gotchas
 * - Only needs the unwrap direction (get = inner), not the wrap direction
 * - The inner renderer's characteristics (size, handlesField) are used directly
 * - Completely transparent - user sees the inner type's view
 */
object WrapperViewRenderer: ViewRenderer.Generator {
    override val name: String = "Wrapper"

    override fun matches(module: FormModule, selector: FormSelector<*>): Boolean {
        return selector.serializer is WrappingSerializer<*, *>
    }

    /**
     * Creates a view renderer that delegates to the inner type's view renderer.
     *
     * Uses a read-only lens to unwrap the value before passing to the inner renderer.
     * The resulting view is identical to the inner type's view.
     */
    override fun <T> view(module: FormModule, selector: FormSelector<T>): ViewRenderer<T> {
        @Suppress("UNCHECKED_CAST")
        val innerSer = (selector.serializer as WrappingSerializer<T, Any?>)
        val inner = module.view(selector.copy(serializer = innerSer.getDeferred()))
        return ViewRenderer<T>(module, this, selector, size = inner.size, handlesField = inner.handlesField) { field, mutable ->
            // Create a read-only lens that unwraps: outer -> inner
            inner.render(this, field, mutable.lens(get = innerSer::inner))
        }
    }
}

/*
 * TODO: API Improvement Recommendations for WrapperViewRenderer
 *
 * 1. Custom Display: Some value classes might want custom display formatting. Consider:
 *    - @DisplayFormat annotation for wrapper types
 *    - Custom toString() rendering option
 *    - Type-specific formatting (e.g., currency formatting for Price)
 *
 * 2. Type Hints: Show wrapper type information in views. Consider:
 *    - Optional type badge/label (e.g., "User ID: 12345")
 *    - Tooltip showing wrapper type name
 *    - Different styling based on wrapper semantics
 *
 * 3. Error Display: If inner() throws during unwrapping, no error handling. Consider:
 *    - Try-catch with fallback display
 *    - Error indicator for invalid wrapped values
 *    - Logging for debugging
 *
 * 4. Semantic Display: Value classes often have semantic meaning. Consider:
 *    - Email wrapper -> clickable mailto: link
 *    - URL wrapper -> clickable link
 *    - Phone wrapper -> clickable tel: link
 *
 * 5. Consistency: Ensure parity with WrapperFormRenderer. Consider:
 *    - Shared base logic for both form and view
 *    - Consistent error handling
 *    - Consistent size calculations
 *
 * 6. Testing: Add unit tests for:
 *    - Various value class types
 *    - Nullable wrappers
 *    - Nested wrappers
 *    - Unwrapping edge cases
 *    - Comparison with WrapperFormRenderer behavior
 */