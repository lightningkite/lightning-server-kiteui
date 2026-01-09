package com.lightningkite.kiteui.forms

import com.lightningkite.kiteui.views.atTopStart
import com.lightningkite.kiteui.views.direct.*
import com.lightningkite.kiteui.views.direct.frame
import com.lightningkite.kiteui.views.expanding
import com.lightningkite.reactive.context.reactive
import com.lightningkite.reactive.context.reactiveScope
import com.lightningkite.reactive.core.remember
import com.lightningkite.reactive.lensing.lens
import com.lightningkite.services.database.default
import com.lightningkite.services.database.nullElement
import kotlinx.serialization.KSerializer

/**
 * Wrapper renderer that adds nullable support to any other renderer.
 *
 * This renderer intercepts nullable types and adds a checkbox to control null state.
 * When checked, the inner renderer is shown for editing the non-null value.
 * When unchecked, the value is set to null and the inner renderer is hidden.
 *
 * ## Form Behavior
 * - Checkbox on the left controls null state
 * - When checked: shows the inner form renderer for the non-null type
 * - When unchecked: value is null, inner renderer is hidden
 * - Preserves the last non-null value when toggling back to checked
 *
 * ## View Behavior
 * - Shows "N/A" when value is null
 * - Shows the inner view renderer when value is non-null
 *
 * ## Matching Rules
 * - Matches only nullable types (isNullable descriptor)
 * - Base priority is 0.4, very low to allow other renderers to take precedence
 * - The nullable property is set to true to identify this as a nullable handler
 *
 * ## Size Calculation
 * Adds 3.0 units to the inner renderer's approximateWidth to account for the checkbox.
 *
 * ## Gotchas
 * - When toggling to null and back, the renderer attempts to restore the last non-null value
 * - Uses serializer.default() as the initial value when toggling from null to non-null
 * - The ifNotNull variable capture pattern is subtle but important for preserving values
 */
object NullableFormRenderer : FormRenderer.Generator, ViewRenderer.Generator {
    override val name: String get() = "Null Wrapper"
    override val basePriority: Float
        get() = 0.4f
    override val nullable: Boolean get() = true

    override fun matches(module: FormModule, selector: FormSelector<*>): Boolean {
        return selector.serializer.descriptor.isNullable
    }

    override fun size(module: FormModule, selector: FormSelector<*>): FormSize {
        @Suppress("UNCHECKED_CAST")
        val innerSerializer = selector.serializer.nullElement()!! as KSerializer<Any>
        val innerSelector = selector.copy(innerSerializer)
        val inner = module.form(innerSelector)
        // Add 3.0 units for the checkbox width
        return inner.size.copy(approximateWidth = inner.size.approximateWidth + 3.0)
    }

    /**
     * Creates a form renderer with checkbox-controlled null state.
     *
     * Layout: [Checkbox] [Inner Form]
     *
     * The checkbox is bound to the null state:
     * - Checked = non-null, show inner form
     * - Unchecked = null, hide inner form
     *
     * Preserves the last non-null value in the `ifNotNull` variable so that
     * unchecking and re-checking restores the previous value.
     */
    @Suppress("UNCHECKED_CAST")
    override fun <T> form(module: FormModule, selector: FormSelector<T>): FormRenderer<T> {
        val innerSerializer = selector.serializer.nullElement()!! as KSerializer<Any>
        val innerSelector by lazy { selector.copy(innerSerializer) }
        val inner by lazy { module.form(innerSelector) }
        return FormRenderer(module, this, selector as FormSelector<Any?>) { field, mutable ->
            row {
                // Capture the last non-null value to restore when checkbox is re-checked
                var ifNotNull: Any = mutable.state.getOrNull() ?: innerSerializer.default()
                padded.frame {
                    atTopStart.checkbox {
                        checked bind mutable.lens(
                            get = { v -> v != null },
                            modify = { e, v ->
                                // If checking the box, restore the last non-null value
                                if (v) ifNotNull else null
                            },
                        )
                    }
                }
                expanding.frame {
                    // Remember the initial null state
                    val isNull = remember { mutable() == null }
                    reactive {
                        clearChildren()
                        if (!isNull()) inner.render(
                            this@frame,
                            field,
                            mutable.lens(
                                get = { v -> v ?: innerSerializer.default() },
                                modify = { e, v ->
                                    // Update the captured value whenever inner form changes
                                    ifNotNull = v
                                    // Only update the outer mutable if not currently null
                                    if (e == null) null else v
                                },
                            ),
                        )
                    }
                }
            }
        } as FormRenderer<T>
    }

    /**
     * Creates a view renderer that shows "N/A" for null or the inner view for non-null.
     *
     * Reactively switches between showing "N/A" text and the inner view renderer
     * based on the current value's null state.
     */
    @Suppress("UNCHECKED_CAST")
    override fun <T> view(module: FormModule, selector: FormSelector<T>): ViewRenderer<T> {
        val innerSerializer = selector.serializer.nullElement()!! as KSerializer<Any>
        val innerSelector by lazy { selector.copy(innerSerializer) }
        val inner by lazy { module.view(innerSelector) }
        return ViewRenderer(module, this, selector as FormSelector<Any?>) { field, readable ->
            frame {
                val isNull = remember { readable() == null }
                reactiveScope {
                    clearChildren()
                    if (isNull()) {
                        text("N/A")
                    } else {
                        inner.render(
                            this@frame,
                            field,
                            // Provide default value if somehow null (defensive)
                            readable.lens { it ?: innerSerializer.default() }
                        )
                    }
                }
            }
        } as ViewRenderer<T>
    }
}

/*
 * TODO: API Improvement Recommendations for NullableFormRenderer
 *
 * 1. Null Text Customization: "N/A" is hardcoded. Consider:
 *    - Making it configurable via FormModule
 *    - Different text for different contexts (None, Empty, Not Set, etc.)
 *    - Supporting blank/empty display instead of text
 *
 * 2. Checkbox Positioning: Checkbox is always on the left. Consider:
 *    - Configurable positioning (left, right, top)
 *    - Using a toggle switch instead of checkbox
 *    - Different UI for different inner types (e.g., inline for primitives)
 *
 * 3. Value Restoration: The ifNotNull variable pattern is subtle and error-prone. Consider:
 *    - More explicit state management
 *    - Option to disable value restoration (always use default when checking)
 *    - Warning when restored value might be stale
 *
 * 4. Size Calculation: Hardcoded +3.0 for checkbox width. Consider:
 *    - Measuring actual checkbox size
 *    - Different sizes for different layouts
 *    - Accounting for padding/gaps
 *
 * 5. Accessibility: No ARIA labels or accessible descriptions. Consider:
 *    - Adding screen reader support
 *    - Keyboard navigation
 *    - Focus management between checkbox and inner form
 *
 * 6. Validation: How should validation work when null? Consider:
 *    - Should required fields allow null?
 *    - Should validation errors be shown when unchecked?
 *    - Clear validation state when toggling to null
 *
 * 7. Performance: clearChildren() and re-render on every toggle. Consider:
 *    - Hiding/showing instead of destroying/recreating
 *    - Caching the inner renderer UI
 *
 * 8. Default Value Strategy: Uses serializer.default() which may not always be appropriate.
 *    Consider:
 *    - Custom default value providers
 *    - User-configurable defaults
 *    - Remember last value per type, not per instance
 *
 * 9. Visual Feedback: No indication that field is nullable. Consider:
 *    - Optional "nullable" badge or icon
 *    - Different styling for nullable fields
 *    - Tooltip explaining checkbox behavior
 *
 * 10. Testing: Add unit tests for:
 *     - Value preservation when toggling
 *     - Default value initialization
 *     - Reactive updates when underlying value changes externally
 *     - View behavior with null and non-null values
 */