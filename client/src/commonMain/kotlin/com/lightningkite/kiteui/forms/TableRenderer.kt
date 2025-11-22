package com.lightningkite.kiteui.forms

import com.lightningkite.kiteui.models.*
import com.lightningkite.kiteui.navigation.Page
import com.lightningkite.kiteui.views.*
import com.lightningkite.kiteui.views.direct.*
import com.lightningkite.kiteui.views.l2.children
import com.lightningkite.kiteui.views.l2.icon
import com.lightningkite.services.database.HasId
import com.lightningkite.lightningserver.db.LimitReadable
import com.lightningkite.reactive.context.invoke
import com.lightningkite.reactive.context.reactive
import com.lightningkite.reactive.core.*
import com.lightningkite.serialization.lensPath
import com.lightningkite.services.database.*
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer

/**
 * Renders collections as tables with configurable columns, ideal for displaying many items compactly.
 *
 * Key features:
 * - Dynamic column selection with add/remove controls
 * - Automatic width calculation based on column renderers
 * - Virtualized rendering via recyclerView for large datasets
 * - Support for LimitReadable with automatic pagination (loads more as user scrolls)
 * - Optional click actions or navigation links per row
 * - Uses defaultColumns() to determine initial visible columns
 *
 * Table structure:
 * - Header row with column names and remove buttons
 * - Add column menu button for selecting additional fields (uses DataClassPath)
 * - RecyclerView body for efficient rendering of many rows
 * - Each cell uses the appropriate ViewRenderer for the column type
 *
 * IMPORTANT: Only matches when inner element has serializableProperties (i.e., is a data class).
 * Primitive collections won't use TableRenderer.
 *
 * Performance considerations:
 * - Renderer cache prevents recreating ViewRenderers for each cell
 * - Width calculation sums all column widths dynamically for horizontal scrolling
 * - LimitReadable integration increases limit as user scrolls near the end (50 items before, load 100 more)
 *
 * Limitations:
 * - Form (editable) mode shows "TODO" - not yet implemented
 * - View-only mode is fully functional
 */
object TableRenderer : FormRenderer.Generator, ViewRenderer.Generator {
    override val name: String = "Table"
    override val type: String = ListSerializer(Unit.serializer()).descriptor.serialName

    /**
     * Always takes up full block size due to table layout.
     * Tables need vertical and horizontal space, so they always request Block sizing.
     */
    override fun size(module: FormModule, selector: FormSelector<*>): FormSize = FormSize.Block

    /** Adds an item to the end of the list */
    fun add(collection: List<Any?>, item: Any?): List<Any?> = collection + item

    /**
     * Removes an item by index from the list.
     * Creates a mutable copy to avoid modifying the original list.
     */
    fun remove(collection: List<Any?>, item: Any?, index: Int): List<Any?> =
        collection.toMutableList().apply { this.removeAt(index) }

    /**
     * Extracts the element serializer from the List serializer.
     * Uses !! operator - will throw if not a List serializer (should be guarded by matches()).
     */
    fun inner(serializer: KSerializer<*>): KSerializer<Any?> = serializer.listElement()!! as KSerializer<Any?>

    /**
     * Only matches collections where the inner element is a data class with serializable properties.
     * This prevents trying to render tables for primitive collections like List<String>.
     */
    override fun matches(module: FormModule, selector: FormSelector<*>): Boolean {
        return super<FormRenderer.Generator>.matches(
            module,
            selector
        ) && inner(selector.serializer).serializableProperties != null
    }

    /** Field layout preference for column renderers */
    val flp = FormLayoutPreferences.Field

    /**
     * Adjusts priority based on inner element size.
     *
     * Higher priority (1.1x) if inner renderer is Block size (complex nested objects benefit from table view).
     * Lower priority (0.6x) if inner renderer is Field size (simple data doesn't need table).
     */
    override fun priority(module: FormModule, selector: FormSelector<*>): Float {
        val innerSer = inner(selector.serializer)
        val inner = module.form(selector.copy(innerSer, desiredSize = flp)) as FormRenderer<Any?>
        return super<FormRenderer.Generator>.priority(
            module,
            selector
        ) * (if (inner.size == FormSize.Block) 1.1f else 0.6f)
    }

    // TODO: Implement editable table form with inline editing, add/remove row controls
    // Current implementation is a placeholder. Editable tables are complex and need:
    // - Inline editing per cell or per row
    // - Add/remove row buttons
    // - Validation feedback per cell
    // - Consider modal editing for complex rows vs inline editing
    @Suppress("UNCHECKED_CAST")
    override fun <T> form(module: FormModule, selector: FormSelector<T>): FormRenderer<T> {
        val innerSer = selector.serializer.listElement()!!
        val inner = module.form(selector.copy(innerSer, desiredSize = flp)) as FormRenderer<Any?>
        return FormRenderer(module, this, selector as FormSelector<List<Any?>>) { _, mutable ->
            text("TODO")
        } as FormRenderer<T>
    }

    /**
     * Creates a read-only table view with column customization.
     *
     * Wraps the reusable view() function with size constraints.
     */
    @Suppress("UNCHECKED_CAST")
    override fun <T> view(module: FormModule, selector: FormSelector<T>): ViewRenderer<T> {
        val innerSer = inner(selector.serializer as KSerializer<List<Any?>>)
        return ViewRenderer(module, this, selector as FormSelector<List<Any?>>) { _, readable ->
            view(sizeConstraints(height = 30.rem), module, innerSer, Constant(readable))
        } as ViewRenderer<T>
    }

    /**
     * Reusable table view function that can be called independently.
     *
     * @param writer ViewWriter context for building UI
     * @param formModule FormModule for accessing renderers
     * @param innerSer Serializer for the element type T
     * @param readable Reactive containing the list data (supports LimitReadable for pagination)
     * @param columns Mutable list of visible columns (defaults to defaultColumns())
     * @param linkTo Optional function to make rows clickable links to pages
     * @param action Optional suspend function called when rows are clicked
     *
     * Features:
     * - Dynamic column management via UI (add/remove columns)
     * - Renderer cache for performance
     * - Automatic width calculation
     * - LimitReadable pagination support
     * - RecyclerView for efficient rendering
     * - Uses HasId._id for stable item identity if available
     *
     * GOTCHA: The readable parameter is Reactive<Reactive<List<T>>> (double-wrapped) to support
     * scenarios where the entire list reference changes, not just its contents.
     */
    fun <T> view(
        writer: ViewWriter,
        formModule: FormModule,
        innerSer: KSerializer<T>,
        readable: Reactive<Reactive<List<T>>>,
        columns: MutableReactiveValue<List<DataClassPath<T, *>>> = Signal(innerSer.defaultColumns()),
        linkTo: ((T) -> () -> Page)? = null,
        action: (suspend (T) -> Unit)? = null,
    ) = with(writer) {
        val properties = innerSer.serializableProperties!! as Array<SerializableProperty<T, Any?>>
        // Cache renderers to avoid recreating them for each cell
        // Without this cache, every cell would create a new renderer instance, causing massive overhead
        val rendererCache = HashMap<DataClassPath<T, Any?>, ViewRenderer<Any?>>()
        val anyCols = columns as MutableReactiveValue<List<DataClassPath<T, Any?>>>

        /**
         * Gets or creates a ViewRenderer for the given column path.
         * Uses rendererCache to ensure we only create one renderer per unique column.
         * The renderer is configured with:
         * - Field layout preference (compact rendering for tables)
         * - Annotations from the property (for @References, @Multiline, etc.)
         * - handlesField=true to prevent double-wrapping in field containers
         */
        fun renderer(path: DataClassPath<T, Any?>) = rendererCache.getOrPut(path) {
            formModule.view(
                FormSelector(
                    serializer = path.serializer,
                    annotations = path.properties.lastOrNull()?.serializableAnnotations ?: listOf(),
                    desiredSize = flp,
                    handlesField = true
                )
            )
        }

        scrollsHorizontally.col {
            // Dynamically calculate total table width based on column renderers
            // This ensures the table scrolls horizontally when columns exceed viewport width
            // Width = sum of (column width + 2rem padding) + 5rem for margins
            expanding.changingSizeConstraints {
                SizeConstraints(width = anyCols().sumOf { renderer(it).size.approximateWidth.coerceAtLeast(5.0) + 2.0 }
                    .plus(5.0).rem)
            }.col {
                // Header row with column names and controls
                padded.row {
                    row {
                        forEach(anyCols) {
                            sizeConstraints(width = renderer(it).size.approximateWidth.coerceAtLeast(5.0).rem).important.row {
                                centered.expanding.text(it.properties.joinToString(" ") { it.displayName })
                                button {
                                    gap = 0.px
                                    centered.icon(Icon.close.copy(width = 1.rem, height = 1.rem), "Remove Column")
                                    onClick {
                                        anyCols.value -= it
                                    }
                                }
                            }
                        }
                    }
                    // Add column menu button - allows selecting additional fields to display
                    menuButton {
                        gap = 0.px
                        centered.icon(Icon.add.copy(width = 1.rem, height = 1.rem), "Add")
                        preferredDirection = PopoverPreferredDirection.belowLeft
                        requireClick = true
                        opensMenu {
                            // Use DataClassPathSerializer for selecting nested field paths
                            // This allows adding columns like "address.city" for nested fields
                            val newField = Signal<DataClassPathPartial<T>>(DataClassPathSelf(innerSer))
                            col {
                                form(formModule, DataClassPathSerializer(innerSer), newField)
                                button {
                                    text("OK")
                                    onClick {
                                        @Suppress("UNCHECKED_CAST")
                                        anyCols.value += newField.value as DataClassPath<T, Any?>
                                        closePopovers()
                                    }
                                }
                            }
                        }
                    }
                }
                // Virtualized table body with infinite scroll pagination
                expanding.onNext(ListSemantic).recyclerView {
                    // Automatically increase limit for LimitReadable as user scrolls
                    // This implements "infinite scroll" - as user approaches the end, more data is loaded
                    reactive {
                        val inner = readable()
                        if (inner is LimitReadable<T>) {
                            // When within 50 items of the end, load 100 more
                            // This threshold prevents loading too early (poor UX) or too late (scroll jank)
                            if (inner.limit < lastIndex() + 50) {
                                inner.limit = lastIndex() + 100
                            }
                        }
                    }
                    // Use HasId._id for stable identity if available, otherwise use object equality
                    // Stable identity prevents unnecessary re-renders when data updates
                    // GOTCHA: If items don't have HasId and are mutable, identity tracking may break on updates
                    children(remember { readable()() }, id = { (it as? HasId<*>)?._id ?: it }) {
                        // Row content builder - renders each cell using lensPath for reactive data binding
                        fun ViewWriter.content() = row {
                            forEach(anyCols) { col ->
                                val render = renderer(col)
                                // Match column width to header width for proper alignment

                                // lensPath creates a reactive lens to the nested field specified by col
                                // E.g., if col is "address.city", this creates a lens that extracts
                                // and reactively tracks just that nested field from the row data
                                render.render(
                                    padded.sizeConstraints(
                                        width = render.size.approximateWidth.coerceAtLeast(
                                            5.0
                                        ).rem
                                    ), null, it.lensPath(col)
                                )
                            }
                        }

                        // Wrap content based on interaction mode
                        if (linkTo != null) {
                            // Clickable row that navigates to a page
                            card.link {
                                content()
                                ::to { linkTo(it()) }
                            }
                        } else if (action != null) {
                            // Clickable row that executes an action
                            card.button {
                                content()
                                onClick { action(it()) }
                            }
                        } else {
                            // Non-interactive row
                            card.content()
                        }

                    }
                }
            }
        }
    }
}

/*
 * ========================================
 * API IMPROVEMENT RECOMMENDATIONS
 * ========================================
 *
 * 1. EDITABLE TABLE IMPLEMENTATION (HIGH PRIORITY)
 *    - Currently form() shows "TODO" - implement inline editing
 *    - Options: edit entire row in modal vs edit cells inline
 *    - Add/remove row controls with proper validation
 *    - Consider "Edit Mode" toggle for complex tables
 *
 * 2. COLUMN SORTING
 *    - Add clickable column headers to sort by that column
 *    - Support ascending/descending toggle
 *    - Visual indicator (arrow icon) for current sort column
 *    - For remote data (LimitReadable), propagate sort to backend query
 *
 * 3. COLUMN FILTERING
 *    - Add filter row beneath headers with appropriate inputs per column type
 *    - Text search for strings, range selectors for numbers/dates
 *    - Integrate with backend query conditions for LimitReadable
 *    - Show active filter count badge
 *
 * 4. COLUMN REORDERING
 *    - Drag-and-drop column headers to reorder
 *    - Persist column order in local storage or user preferences
 *    - Reset to default columns button
 *
 * 5. COLUMN RESIZING
 *    - Allow manual column width adjustment via drag handles
 *    - Auto-fit column to content width
 *    - Fixed vs flexible width modes
 *
 * 6. COLUMN PERSISTENCE
 *    - Save selected columns, widths, order to localStorage or server
 *    - Per-user table configurations
 *    - Export/import column configurations
 *
 * 7. ROW SELECTION
 *    - Checkbox column for multi-select
 *    - Bulk actions on selected rows (delete, export, etc.)
 *    - Select all/none controls
 *    - Keyboard shortcuts (Shift+Click for range select)
 *
 * 8. EXPORT FUNCTIONALITY
 *    - Export visible rows to CSV/Excel
 *    - Export all data (not just visible in LimitReadable)
 *    - Include/exclude columns in export
 *    - Format cells appropriately for export (dates, numbers)
 *
 * 9. FIXED HEADER/COLUMNS
 *    - Sticky header row when scrolling vertically
 *    - Freeze first N columns when scrolling horizontally
 *    - Helps with large tables
 *
 * 10. PAGINATION IMPROVEMENTS
 *     - Current infinite scroll is good, but add optional page numbers
 *     - Jump to page input
 *     - Page size selector (25/50/100/500 rows)
 *     - Show total count if available
 *
 * 11. RESPONSIVE DESIGN
 *     - On mobile, consider card-based view instead of table
 *     - Horizontal scroll works but not ideal for small screens
 *     - Collapsible columns on narrow viewports
 *
 * 12. PERFORMANCE OPTIMIZATION
 *     - rendererCache is good, but could be scoped at FormModule level
 *     - Measure and optimize width calculation (currently recalculates on every column change)
 *     - Consider virtual columns for very wide tables (>50 columns)
 *
 * 13. EMPTY STATE
 *     - Show helpful message when table has no data
 *     - Distinguish between "loading", "no results", and "no data exists"
 *     - Provide action buttons in empty state (e.g., "Add First Item")
 *
 * 14. LOADING STATE
 *     - Show loading indicators during data fetch
 *     - Skeleton rows for better UX
 *     - Loading spinner or progress bar
 *
 * 15. ERROR HANDLING
 *     - Display errors from LimitReadable gracefully
 *     - Retry mechanisms for failed loads
 *     - Partial data display with error notification
 *
 * 16. NESTED/EXPANDABLE ROWS
 *     - For hierarchical data, allow row expansion to show child rows
 *     - Tree table support
 *     - Indentation to show hierarchy
 *
 * 17. COLUMN GROUPS
 *     - Multi-level headers for grouped columns
 *     - E.g., "Address" group containing "Street", "City", "Zip"
 *     - Collapse/expand column groups
 *
 * 18. CELL FORMATTING
 *     - More control over cell rendering (alignment, text wrap, truncation)
 *     - Conditional formatting (highlight cells based on value)
 *     - Custom cell renderers via annotation or config
 *
 * 19. ACCESSIBILITY
 *     - ARIA table semantics
 *     - Keyboard navigation (arrow keys between cells)
 *     - Screen reader announcements for sort/filter changes
 *     - Focus management for inline editing
 */
