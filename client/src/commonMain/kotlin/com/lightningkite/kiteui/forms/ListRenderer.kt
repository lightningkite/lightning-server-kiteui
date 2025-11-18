package com.lightningkite.kiteui.forms

import com.lightningkite.kiteui.models.Icon
import com.lightningkite.kiteui.models.px
import com.lightningkite.kiteui.models.rem
import com.lightningkite.kiteui.views.card
import com.lightningkite.kiteui.views.centered
import com.lightningkite.kiteui.views.direct.*
import com.lightningkite.kiteui.views.expanding
import com.lightningkite.kiteui.views.forEachUpdating
import com.lightningkite.kiteui.views.l2.icon
import com.lightningkite.reactive.context.invoke
import com.lightningkite.reactive.core.MutableReactive
import com.lightningkite.reactive.core.Reactive
import com.lightningkite.reactive.extensions.flatten
import com.lightningkite.reactive.lensing.lens
import com.lightningkite.reactive.lensing.lensByElementAssumingSetNeverManipulates
import com.lightningkite.services.database.SerializableAnnotation
import com.lightningkite.services.database.default
import com.lightningkite.services.database.listElement
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.SetSerializer
import kotlinx.serialization.builtins.serializer

/**
 * Abstract base class for rendering collections (List and Set) in both vertical and horizontal layouts.
 *
 * This renderer provides interactive collection editing with add/remove functionality. Each item is rendered
 * using a nested renderer and displayed in a card with a close button for removal. An "Add" button allows
 * creating new items with default values.
 *
 * @param C The collection type (e.g., List<Any?>, Set<Any?>)
 *
 * Key features:
 * - Supports both vertical (stacked) and horizontal (scrollable row) layouts
 * - Automatic handling of @MultipleReferences annotation conversion to @References for inner items
 * - Size estimation multiplies inner renderer size by 10 to account for multiple items
 * - Uses lensByElementAssumingSetNeverManipulates for efficient reactive tracking per item
 * - Shows "Empty" text when collection has no items
 *
 * IMPORTANT: Set renderers convert to/from List internally via lens for UI rendering, as Sets don't have
 * stable indices. This means Set removal uses the item reference, not index.
 */
abstract class ListRenderer<C> : FormRenderer.Generator, ViewRenderer.Generator {
    /** Whether to layout items vertically (true) or horizontally (false) */
    abstract val vertical: Boolean

    /** Display name for the collection type (e.g., "List", "Set") */
    abstract val typeName: String

    /** Extracts the inner element serializer from the collection serializer */
    abstract fun inner(serializer: KSerializer<C>): KSerializer<*>

    /** Lens to convert between collection type and List for mutable reactive binding */
    abstract fun lens(mutable: MutableReactive<C>): MutableReactive<List<Any?>>

    /** Lens to convert between collection type and List for readable reactive binding */
    abstract fun lens(readable: Reactive<C>): Reactive<List<Any?>>

    /**
     * Removes an item from the collection.
     * @param index Used for List (direct index removal), ignored for Set (uses item equality)
     */
    abstract fun remove(collection: C, item: Any?, index: Int): C

    /** Adds an item to the collection */
    abstract fun add(collection: C, item: Any?): C

    override val name: String get() = if (vertical) "Vertical $typeName" else "Horizontal $typeName"
    abstract override val type: String

    /**
     * Creates a FormSelector for the inner element type.
     *
     * Handles special annotation conversion: @MultipleReferences on the collection is converted to
     * @References on the inner element, allowing proper foreign key rendering for collection items.
     */
    private fun inner(module: FormModule, selector: FormSelector<*>): FormSelector<out Any?> {
        val innerSer = inner(selector.serializer as KSerializer<C>)
        val inner = selector.copy(
            innerSer,
            desiredSize = if (vertical) FormLayoutPreferences.Block else FormLayoutPreferences.Field,
            // Convert @MultipleReferences annotation on collection to @References on element
            // This allows collections of foreign keys to render with proper lookup/autocomplete
            annotations = selector.annotations?.find { it.fqn == "com.lightningkite.lightningdb.MultipleReferences" }
                ?.let { selector.annotations + SerializableAnnotation("com.lightningkite.lightningdb.References", it.values) }
                ?: selector.annotations
        )
        return inner
    }

    /**
     * Calculates the approximate size needed for rendering the collection.
     *
     * Multiplies the inner element size by 10 as a rough estimate, assuming collections typically
     * contain multiple items. Adds 3 units of padding for margins and the add button.
     */
    override fun size(module: FormModule, selector: FormSelector<*>): FormSize {
        val inner = module.form(inner(module, selector)) as FormRenderer<Any?>
        return if (vertical)
            inner.size.copy(approximateHeight = (inner.size.approximateHeight) * 10 + 3)
        else
            inner.size.copy(approximateWidth = (inner.size.approximateWidth + 3) * 10 + 3)
    }

    /**
     * Creates an editable form renderer for collections.
     *
     * UI Structure:
     * - "Empty" text shown when collection is empty
     * - Each item rendered in a card with:
     *   - The inner form renderer for editing the item
     *   - Close button (X) for removal
     * - "Add" button at the bottom to create new items with default values
     *
     * Reactive behavior:
     * - Uses lensByElementAssumingSetNeverManipulates() for efficient per-item tracking
     * - flatten() unwraps the nested reactive structure (Reactive<Reactive<T>> -> Reactive<T>)
     * - Removal passes both item value and index to support both List (by index) and Set (by value)
     *
     * Layout:
     * - Vertical: items stacked, expanding width, scrollable if needed
     * - Horizontal: items in row, expanding container with horizontal scroll
     */
    @Suppress("UNCHECKED_CAST")
    override fun <T> form(module: FormModule, selector: FormSelector<T>): FormRenderer<T> {
        val inner = module.form(inner(module, selector)) as FormRenderer<Any?>
        return FormRenderer(module, this, selector as FormSelector<C>) { _, mutable ->
            row {
                vertical = this@ListRenderer.vertical
                if (!vertical) expanding.scrollsHorizontally
                text {
                    ::exists { (mutable() as Collection<*>).isEmpty() }
                    content = "Empty"
                }
                row {
                    vertical = this@ListRenderer.vertical
                    // lensByElementAssumingSetNeverManipulates creates a lens for each item with stable tracking
                    // IMPORTANT: This assumes the Set/List itself is never structurally modified outside our control
                    // It tracks items by reference, so modifications to item contents are detected, but
                    // external reordering or replacement of the collection would not trigger proper updates
                    forEachUpdating(lens(mutable).lensByElementAssumingSetNeverManipulates()) {
                        card.row {
                            gap = 0.px
                            if (this@ListRenderer.vertical) expanding
                            // flatten() converts Reactive<Reactive<T>> to Reactive<T>
                            // The outer Reactive tracks which element this is (by index/reference)
                            // The inner Reactive tracks changes to the element's contents
                            inner.render(this, null, it.flatten())
                            centered.button {
                                icon(Icon.close.copy(width = 1.rem, height = 1.rem), "Delete")
                                onClick {
                                    // Pass both item value and index for removal
                                    // List uses index, Set uses item equality
                                    mutable set remove(mutable(), it()(), it().index.value)
                                }
                            }
                        }
                    }
                }
                button {
                    if (this@ListRenderer.vertical) {
                        centered.row {
                            centered.text("Add")
                            centered.icon(Icon.add.copy(width = 1.rem, height = 1.rem), "")
                        }
                    } else {
                        centered.icon(Icon.add.copy(width = 1.rem, height = 1.rem), "Add")
                    }
                    onClick {
                        // Create new item with default value based on serializer
                        mutable set (add(mutable(), inner.selector.serializer.default()))
                    }
                }
            }
        } as FormRenderer<T>
    }

    /**
     * Creates a read-only view renderer for collections.
     *
     * Similar to form() but without edit/remove/add controls. Simply displays each item
     * in a card using the inner view renderer.
     *
     * Shows "Empty" text when collection has no items.
     */
    @Suppress("UNCHECKED_CAST")
    override fun <T> view(module: FormModule, selector: FormSelector<T>): ViewRenderer<T> {
        val inner = module.view(inner(module, selector)) as ViewRenderer<Any?>
        return ViewRenderer(module, this, selector as FormSelector<C>) { _, readable ->
            row {
                vertical = this@ListRenderer.vertical
                text {
                    ::exists { (readable() as Collection<*>).isEmpty() }
                    content = "Empty"
                }
                row {
                    vertical = this@ListRenderer.vertical
                    forEachUpdating(lens(readable)) {
                        inner.render(card, null, it)
                    }
                }
            }
        } as ViewRenderer<T>
    }
}

/**
 * Renders List<T> in a horizontal scrollable row.
 *
 * Each item is displayed side-by-side with a close button. Useful for tags, chips,
 * or small collections where items should be visible at a glance.
 */
object HorizontalListRenderer : ListRenderer<List<Any?>>() {
    override val vertical: Boolean = false
    override val typeName: String = "List"
    override val type: String = ListSerializer(Unit.serializer()).descriptor.serialName
    override fun add(collection: List<Any?>, item: Any?): List<Any?> = collection + item
    override fun remove(collection: List<Any?>, item: Any?, index: Int): List<Any?> = collection.toMutableList().apply { this.removeAt(index) }
    override fun lens(readable: Reactive<List<Any?>>): Reactive<List<Any?>> = readable
    override fun lens(mutable: MutableReactive<List<Any?>>): MutableReactive<List<Any?>> = mutable
    override fun inner(serializer: KSerializer<List<Any?>>): KSerializer<*> = serializer.listElement()!!
}

/**
 * Renders List<T> in a vertical stacked layout.
 *
 * Each item is displayed one per row, taking full width. Preferred for forms with complex
 * inner elements or when vertical scrolling is more natural.
 */
object VerticalListRenderer : ListRenderer<List<Any?>>() {
    override val vertical: Boolean = true
    override val typeName: String = "List"
    override val type: String = ListSerializer(Unit.serializer()).descriptor.serialName
    override fun add(collection: List<Any?>, item: Any?): List<Any?> = collection + item
    override fun remove(collection: List<Any?>, item: Any?, index: Int): List<Any?> = collection.toMutableList().apply { this.removeAt(index) }
    override fun lens(readable: Reactive<List<Any?>>): Reactive<List<Any?>> = readable
    override fun lens(mutable: MutableReactive<List<Any?>>): MutableReactive<List<Any?>> = mutable
    override fun inner(serializer: KSerializer<List<Any?>>): KSerializer<*> = serializer.listElement()!!
}

/**
 * Renders Set<T> in a horizontal scrollable row.
 *
 * Internally converts Set to List for UI rendering since Sets lack stable ordering.
 * The lens() methods handle bidirectional conversion. Removal uses item equality, not index.
 *
 * IMPORTANT: Set ordering may appear unstable across renders. Consider VerticalSetRenderer for
 * larger sets where order visibility is less critical.
 *
 * GOTCHA: If the Set contains duplicate items according to equals() but with different object identities,
 * toSet() may lose items during lens conversion. This is generally not an issue since Sets enforce uniqueness.
 */
object HorizontalSetRenderer : ListRenderer<Set<Any?>>() {
    override val vertical: Boolean = false
    override val typeName: String = "Set"
    override val type: String = SetSerializer(Unit.serializer()).descriptor.serialName
    override fun add(collection: Set<Any?>, item: Any?): Set<Any?> = collection + item
    // Removal uses item equality, not index (index parameter ignored)
    override fun remove(collection: Set<Any?>, item: Any?, index: Int): Set<Any?> = collection.toMutableSet().apply { this.remove(item) }
    // Convert Set to List for UI rendering
    override fun lens(readable: Reactive<Set<Any?>>): Reactive<List<Any?>> = readable.lens { it.toList() }
    override fun lens(mutable: MutableReactive<Set<Any?>>): MutableReactive<List<Any?>> = mutable.lens(get = { it.toList() }, set = { it.toSet() })
    override fun inner(serializer: KSerializer<Set<Any?>>): KSerializer<*> = serializer.listElement()!!
}

/**
 * Renders Set<T> in a vertical stacked layout.
 *
 * Internally converts Set to List for UI rendering since Sets lack stable ordering.
 * The lens() methods handle bidirectional conversion. Removal uses item equality, not index.
 */
object VerticalSetRenderer : ListRenderer<Set<Any?>>() {
    override val vertical: Boolean = true
    override val typeName: String = "Set"
    override val type: String = SetSerializer(Unit.serializer()).descriptor.serialName
    override fun add(collection: Set<Any?>, item: Any?): Set<Any?> = collection + item
    // Removal uses item equality, not index (index parameter ignored)
    override fun remove(collection: Set<Any?>, item: Any?, index: Int): Set<Any?> = collection.toMutableSet().apply { this.remove(item) }
    // Convert Set to List for UI rendering
    override fun lens(readable: Reactive<Set<Any?>>): Reactive<List<Any?>> = readable.lens { it.toList() }
    override fun lens(mutable: MutableReactive<Set<Any?>>): MutableReactive<List<Any?>> = mutable.lens(get = { it.toList() }, set = { it.toSet() })
    override fun inner(serializer: KSerializer<Set<Any?>>): KSerializer<*> = serializer.listElement()!!
}

/*
 * ========================================
 * API IMPROVEMENT RECOMMENDATIONS
 * ========================================
 *
 * 1. REORDERING SUPPORT
 *    - Add drag-and-drop reordering for List renderers (especially vertical)
 *    - Provide optional up/down arrow buttons for manual reordering
 *    - Consider adding a "Reorder Mode" toggle for complex forms
 *
 * 2. DUPLICATE HANDLING IN SETS
 *    - When adding to a Set, check if default() value already exists to prevent silent no-ops
 *    - Show user feedback when attempting to add duplicate items to a Set
 *    - Consider incrementing numeric/string defaults to make them unique
 *
 * 3. SIZE ESTIMATION REFINEMENT
 *    - Current "* 10 + 3" multiplier is arbitrary
 *    - Consider making this configurable per collection via annotation (e.g., @EstimatedSize(5))
 *    - Could query actual collection size from reactive value for more accurate sizing
 *
 * 4. EMPTY STATE IMPROVEMENTS
 *    - "Empty" text is simplistic; consider showing more helpful guidance
 *    - Add optional placeholder property to show "Click Add to create first item"
 *    - Support custom empty state renderers
 *
 * 5. BULK OPERATIONS
 *    - Add "Clear All" button with confirmation
 *    - Support "Add Multiple" for batch creation
 *    - Allow copy/paste of collection items (serialization-based)
 *
 * 6. PERFORMANCE OPTIMIZATION
 *    - For very large collections, consider virtualization/windowing
 *    - Add pagination or "Load More" for collections beyond a threshold (e.g., 50 items)
 *    - Cache inner renderers more aggressively to avoid recreation
 *
 * 7. VALIDATION FEEDBACK
 *    - No current mechanism to show validation errors at collection level
 *    - Add support for collection-level constraints (min/max size, uniqueness rules)
 *    - Show per-item validation errors inline with better visual indicators
 *
 * 8. ACCESSIBILITY
 *    - Add ARIA labels for add/remove buttons
 *    - Improve keyboard navigation (Tab through items, Delete key for removal)
 *    - Announce collection changes to screen readers
 *
 * 9. CONDITIONAL ADD BUTTON
 *    - Allow disabling add button based on max size constraint
 *    - Support async validation before allowing adds (e.g., "max 5 items")
 *
 * 10. ANIMATION SUPPORT
 *     - Add optional enter/exit animations for items being added/removed
 *     - Consider slide-in effect for new items, fade-out for removed items
 *
 * 11. SET ORDERING STABILITY
 *     - Current Set->List conversion may show inconsistent ordering
 *     - Consider sorted rendering option for Sets with Comparable elements
 *     - Allow custom comparator injection for deterministic ordering
 *
 * 12. INLINE EDITING OPTIMIZATION
 *     - When inner renderer is complex (FormSize.Block), consider collapse/expand per item
 *     - Add "Edit in Modal" option for very complex nested forms
 *     - Support compact preview mode with expand-on-click
 */