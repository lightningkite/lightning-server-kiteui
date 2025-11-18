package com.lightningkite.lightningserver.admin

import com.lightningkite.IsRawString
import com.lightningkite.TrimmedString
import com.lightningkite.kiteui.*
import com.lightningkite.kiteui.forms.*
import com.lightningkite.kiteui.models.Icon
import com.lightningkite.kiteui.models.SelectedSemantic
import com.lightningkite.kiteui.navigation.DefaultJson
import com.lightningkite.kiteui.navigation.Page
import com.lightningkite.kiteui.navigation.UrlProperties
import com.lightningkite.kiteui.navigation.encodeToString
import com.lightningkite.kiteui.reactive.Action
import com.lightningkite.kiteui.views.*
import com.lightningkite.kiteui.views.direct.*
import com.lightningkite.kiteui.views.l2.dialog
import com.lightningkite.kiteui.views.l2.icon
import com.lightningkite.kotlinx.serialization.csv.CsvFormat
import com.lightningkite.kotlinx.serialization.csv.StringDeferringConfig
import com.lightningkite.services.database.*
import com.lightningkite.lightningserver.db.ModelCache
import com.lightningkite.reactive.context.ReactiveContext
import com.lightningkite.reactive.context.invoke
import com.lightningkite.reactive.context.reactive
import com.lightningkite.reactive.core.*
import com.lightningkite.reactive.extensions.debounce
import com.lightningkite.services.database.*
import kotlinx.serialization.builtins.ListSerializer


/**
 * Main CRUD screen for managing a collection in the admin panel.
 *
 * This screen provides a full-featured data table with:
 * - Text search across visible columns
 * - Advanced filtering via condition builder
 * - Multi-field sorting
 * - Customizable column selection
 * - Bulk operations (import/export CSV, bulk delete)
 * - Real-time updates via ModelCache
 * - Permission-aware display
 *
 * All filter/sort/column state is persisted in URL query parameters for bookmarkability.
 *
 * @param collectionName The collection identifier from the server schema
 */
@Routable("collections/{collectionName}")
class CollectionAdminPage(val collectionName: String) : Page {

    /** Text search query - searches across visible string columns */
    @QueryParameter("query")
    val textSearch: Signal<String> = Signal("")

    /** Serialized Condition for filtering - supports complex queries */
    @QueryParameter("condition")
    val conditionString: Signal<String?> = Signal(null)

    /** Serialized list of SortPart for ordering results */
    @QueryParameter("sort")
    val sortString: Signal<String?> = Signal(null)

    /** Serialized list of DataClassPath for visible columns */
    @QueryParameter("columns")
    val columnsString: Signal<String?> = Signal(null)

    /**
     * The ModelCache instance for this collection, initialized with current authentication.
     *
     * Provides real-time data synchronization via WebSockets when available, and smart polling otherwise.
     * Cached at the page level so data persists across reactive rebuilds.
     */
    // TODO: This force-unwraps with !! which will throw if collection doesn't exist. Add proper error handling.
    val mc = remember {
        adminServer().models[collectionName]?.cache(adminAuthentication())!!
                as ModelCache<UnknownModel, UnknownId>
    }

    /**
     * Shows export dialog with options to download or copy data as CSV.
     *
     * Exports respect the current filter/sort but are limited to 100,000 records.
     * Uses the current visible columns and query state.
     */
    fun ViewWriter.exportDialog() = dialog { close ->
        col {
            h2("Export")

            // Export to CSV file download
            // Respects current filter and sort but exports full result set (up to 100k items)
            important.button {
                centered.text("Direct CSV")
                action = Action("Download", Icon.download) {
                    // Configure CSV format to handle complex types by deferring to JSON serialization
                    val csv = CsvFormat(StringDeferringConfig(DefaultJson.serializersModule, ignoreUnknownKeys = true))
                    val mc = mc()
                    // TODO: 100,000 limit could cause memory issues with large records. Consider streaming or server-side export.
                    val data = mc.query(
                        queryReadable(mc)().copy(limit = 100_000)
                    )()
                    val items = csv.encodeToString(ListSerializer(mc().serializer), data)
                    ExternalServices.download("data.csv", items.toBlob("text/csv"), DownloadLocation.Downloads)
                    close()
                }
            }

            // Export to clipboard (useful for pasting into spreadsheets)
            // Shows success checkmark when copy completes
            important.button {
                val success = Signal(false)
                row {
                    expanding.stack()
                    centered.text("Copy CSV to Clipboard")
                    onlyWhen { success() }.icon(Icon.done, "Done")
                    expanding.stack()
                }
                action = Action("Download", Icon.download) {
                    val csv = CsvFormat(StringDeferringConfig(DefaultJson.serializersModule, ignoreUnknownKeys = true))
                    val mc = mc()
                    // TODO: Same 100k limit - consider streaming for large datasets
                    val data = mc.query(
                        queryReadable(mc)().copy(limit = 100_000)
                    )()
                    val items = csv.encodeToString(ListSerializer(mc().serializer), data)
                    ExternalServices.setClipboardText(items)
                    success.value = true
                    close()
                }
            }
        }

    }

    /**
     * Shows import dialog for uploading CSV data to the collection.
     *
     * Validates data before insert and requires user confirmation.
     * All items are inserted in bulk - partial failures may occur.
     */
    fun ViewWriter.importDialog() = dialog { close ->
        col {
            h2("Import")
            important.button {
                centered.text("Upload Direct CSV")
                action = Action("Upload", Icon.upload) {
                    // Request CSV file from user's filesystem
                    val file = ExternalServices.requestFile(listOf("text/csv")) ?: return@Action
                    val csv = CsvFormat(StringDeferringConfig(DefaultJson.serializersModule, ignoreUnknownKeys = true))
                    val text = file.text()

                    // TODO: CSV parsing errors are not caught - will crash if CSV is malformed or doesn't match schema
                    val items = csv.decodeFromString(ListSerializer(mc().serializer), text)

                    // Show confirmation with item count before inserting
                    confirmDanger("Upload ${items.size} items?", "Are you sure you want to upload these items?") {
                        // TODO: No error handling for bulk insert failures - partial failures may occur silently
                        // TODO: No progress indicator for large imports
                        mc().insert(items)
                    }
                    close()
                }
            }
        }
    }

    /**
     * Shows bulk delete dialog for removing all items matching the current filter.
     *
     * Displays item count preview and requires confirmation before deletion.
     * Uses skipCache.bulkDelete for direct server deletion bypassing cache.
     *
     * WARNING: This is a destructive operation with no undo.
     */
    fun ViewWriter.bulkDeleteDialog() = dialog { close ->
        col {
            h2("Bulk Delete")
            reactive<Unit> {
                clearChildren()
                val mc = mc()
                val condition = conditionWritable(mc)

                // Fetch item count matching the current condition for preview
                // Uses skipCache to get accurate server-side count
                val itemCount = rememberSuspending {
                    val c = condition()
                    mc.skipCache.count(c)
                }

                // Show preview of what will be deleted
                // Helps users understand the scope of the delete operation
                subtext {
                    ::content {
                        buildString {
                            val c = condition()
                            when (c) {
                                Condition.Always -> append("This will delete ALL ${itemCount()} items in the collection.")
                                Condition.Never -> append("No items will be deleted.")
                                else -> append("This will delete ${itemCount()} items where $c")
                            }
                        }
                    }
                }

                important.button {
                    centered.text("Delete All Matching Items")
                    action = Action("Delete", Icon.deleteForever) {
                        val c = condition()
                        // Double confirmation for destructive operation
                        confirmDanger(
                            "Delete all matching items?",
                            "Are you sure you want to delete all items matching the current query? This action cannot be undone."
                        ) {
                            // TODO: No error handling for bulk delete failures - user won't know if operation failed
                            // TODO: No progress indicator for large deletes
                            // Bypass cache and delete directly on server
                            mc.skipCache.bulkDelete(c)
                            // Force full cache refresh to reflect deletions
                            mc.totallyInvalidate()
                        }
                        close()
                    }
                }
            }
        }
    }

    /**
     * Renders the main collection screen.
     *
     * Rebuilds when the ModelCache or FormModule changes (e.g., auth changes).
     */
    override fun ViewWriter.render() {
        col {
            reactive<Unit> {
                clearChildren()
                val mc = mc()
                val forms = adminFormModule()
                renderContents(mc, forms)
            }
        }
    }

    /**
     * Renders the toolbar, filters, and data table for the collection.
     *
     * This includes:
     * - Search input with filter/sort/info buttons
     * - Item count summary with current query description
     * - Table renderer with customizable columns and live updates
     *
     * @param mc The ModelCache for accessing and watching collection data
     * @param forms The FormModule for rendering filters and table cells
     */
    private fun RowOrCol.renderContents(mc: ModelCache<UnknownModel, UnknownId>, forms: FormModule) {
        // Convert URL query parameters to reactive values for condition, sort, and columns
        // These are bidirectionally bound - changes update both UI and URL
        val condition = conditionWritable(mc)
        val sort = sortWritable(mc)

        @Suppress("UNCHECKED_CAST")
        val columns: MutableReactiveValue<List<DataClassPath<UnknownModel, *>>> =
            columnsWritable(mc)

        // Build the full query from all reactive inputs (text search + filter + sort)
        val query = queryReadable(mc)

        // Toolbar with search, filter, sort, and action buttons
        row {
            // Full-width text search input
            // Debounced in queryReadable to prevent excessive queries
            expanding.fieldTheme.textInput {
                content bind textSearch
            }

            // Filter button - highlights when filter is active (not Condition.Always)
            menuButton {
                dynamicTheme { if (condition() != Condition.Always) SelectedSemantic else null }
                icon(Icon.filterList, "Filter")
                requireClick = true
                opensMenu {
                    // Dynamically generated form for building Condition queries
                    // Form is type-safe and adapts to the collection's schema
                    form(forms, Condition.serializer(mc.serializer), condition)
                }
            }

            // Sort button - highlights when sort is applied (non-empty)
            menuButton {
                dynamicTheme { if (sort().isNotEmpty()) SelectedSemantic else null }
                icon(Icon.sort, "Sort")
                requireClick = true
                opensMenu {
                    // Form for building multi-field sort order
                    // Supports ascending/descending on any field
                    form(forms, ListSerializer(SortPartSerializer(mc.serializer)), sort)
                }
            }

            // Link to collection statistics/analytics page
            link {
                icon(Icon.info, "Statistics")
                to = { CollectionStatsPage(collectionName) }
            }

            // Bulk actions menu (export/import/delete) and permissions display
            menuButton {
                icon(Icon.moreVert, "Bulk Actions")
                requireClick = true
                opensMenu {
                    col {
                        important.button {
                            text("Export...")
                            onClick { exportDialog() }
                        }
                        important.button {
                            text("Import...")
                            onClick { importDialog() }
                        }
                        important.button {
                            text("Bulk Delete...")
                            onClick { bulkDeleteDialog() }
                        }

                        // Permissions display section
                        // Shows user's current permissions for this collection
                        col {
                            h3("My Permissions")
                            val p = remember { loadedPermissions().get(collectionName) ?: ModelPermissions() }

                            // Helper function for rendering key-value permission rows
                            // Allows conditional visibility for rows that only apply when restrictions exist
                            fun ViewWriter.kv(
                                key: String,
                                visibleIf: ReactiveContext.() -> Boolean = { true },
                                value: ReactiveContext.() -> String
                            ) {
                                row {
                                    ::exists { visibleIf() }
                                    expanding.text {
                                        wraps = false
                                        content = key
                                    }
                                    text {
                                        wraps = false
                                        ::content { value() }
                                    }
                                }
                            }
                            kv("Read") { p().read.simplify().friendly() }
                            kv("Restricted fields", visibleIf = { p().readMask.pairs.isNotEmpty() }) {
                                p().readMask.pairs.flatMap { it.first.readPaths() }
                                    .joinToString(", ") { it.properties.joinToString("'s ") { it.displayName } }
                            }
                            kv("Create") { p().create.simplify().friendly() }
                            kv("Update") { p().update.simplify().friendly() }
                            kv("Restricted fields", visibleIf = { p().updateRestrictions.fields.isNotEmpty() }) {
                                p().updateRestrictions.fields
                                    .joinToString(", ") { it.path.properties.joinToString("'s ") { it.displayName } }
                            }
                            kv("Delete") { p().delete.simplify().friendly() }
                        }
                    }
                }
            }

            // Add new item link - opens NewItemAdminPage
            // Preserves current filter condition for context
            link {
                icon(Icon.add, "Add New")
                to = {
                    NewItemAdminPage(collectionName).apply {
                        conditionString.value = this@CollectionAdminPage.conditionString.value
                    }
                }
            }
        }
        // Summary text showing current filter/sort state and total item count
        subtext {
            val itemCount = rememberSuspending {
                val c = condition()
                // Use skipCache to get accurate server-side count
                mc.skipCache.count(c)
            }
            ::content {
                buildString {
                    val c = condition()
                    // Generate human-readable description of current filter
                    when (c) {
                        Condition.Always -> append("Showing all ${itemCount()} items ")
                        Condition.Never -> append("Showing NO ITEMS ")
                        else -> append("Showing ${itemCount()} items where $c ")
                    }
                    val s = sort()
                    // Append sort description if sorting is active
                    if (s.isNotEmpty()) {
                        append("sorted by ")
                        s.forEach {
                            append(it.field.properties.joinToString("'s ") { it.displayName })
                            if (it.ascending) append(" ascending")
                            else append(" descending")
                        }
                    }
                }
            }
        }

        // Main data table with live updates
        // TableRenderer provides virtual scrolling, column customization, and sorting
        TableRenderer.view<UnknownModel>(
            formModule = forms,
            writer = this@renderContents.expanding,
            innerSer = mc.serializer,
            columns = columns,
            // Watch the query - automatically updates when query changes or data changes
            readable = remember {
                mc.watch(query())
            },
            // Each row links to detail page for editing
            linkTo = {
                val id = UrlProperties.encodeToString(mc.serializer._id().serializer, it._id)
                return@view { DetailAdminPage(collectionName, id) }
            }
        )
    }

    /**
     * Converts the columnsString query parameter to a reactive list of DataClassPath.
     *
     * Falls back to defaultColumns() if the string is null or cannot be parsed.
     * Writes changes back to the URL parameter as JSON, enabling bookmarkable column configurations.
     *
     * The lens pattern provides bidirectional transformation between URL string and typed list.
     */
    private fun columnsWritable(mc: ModelCache<UnknownModel, UnknownId>): MutableReactiveValue<List<DataClassPath<UnknownModel, *>>> =
        columnsString.lens(
            get = {
                it?.let {
                    try {
                        // Deserialize from JSON URL parameter
                        DefaultJson.decodeFromString(
                            ListSerializer(DataClassPathSerializer(mc.serializer)),
                            it
                        ) as List<DataClassPath<UnknownModel, *>>
                    } catch (e: Exception) {
                        // TODO: Log parsing errors for debugging
                        null
                    }
                } ?: mc.serializer.defaultColumns() // Fallback to schema-defined defaults
            },
            set = {
                // Serialize back to JSON for URL parameter
                DefaultJson.encodeToString(
                    ListSerializer(DataClassPathSerializer(mc.serializer)),
                    it as List<DataClassPathPartial<UnknownModel>>
                )
            }
        )

    /**
     * Converts the sortString query parameter to a reactive list of SortPart.
     *
     * Falls back to naturalSort() (typically by _id) if null or invalid.
     * Writes changes back to the URL parameter as JSON, enabling bookmarkable sort configurations.
     *
     * The lens pattern provides bidirectional transformation between URL string and typed list.
     */
    private fun sortWritable(mc: ModelCache<UnknownModel, UnknownId>): MutableReactiveValue<List<SortPart<UnknownModel>>> =
        sortString.lens(
            get = {
                it?.let {
                    try {
                        // Deserialize from JSON URL parameter
                        DefaultJson.decodeFromString(ListSerializer(SortPartSerializer(mc.serializer)), it)
                    } catch (e: Exception) {
                        // TODO: Log parsing errors for debugging
                        null
                    }
                } ?: mc.serializer.naturalSort() // Fallback to default sort (usually by ID)
            },
            set = {
                // Serialize back to JSON for URL parameter
                DefaultJson.encodeToString(ListSerializer(SortPartSerializer(mc.serializer)), it)
            }
        )

    /**
     * Converts the conditionString query parameter to a reactive Condition.
     *
     * Falls back to Condition.Always (no filter) if null or invalid.
     * Writes changes back to the URL parameter as JSON, enabling bookmarkable filter configurations.
     *
     * The lens pattern provides bidirectional transformation between URL string and typed Condition.
     */
    private fun conditionWritable(mc: ModelCache<UnknownModel, UnknownId>): MutableReactiveValue<Condition<UnknownModel>> =
        conditionString.lens(
            get = {
                it?.let {
                    try {
                        // Deserialize from JSON URL parameter
                        DefaultJson.decodeFromString(Condition.serializer(mc.serializer), it)
                    } catch (e: Exception) {
                        // TODO: Log parsing errors for debugging
                        null
                    }
                } ?: Condition.Always // Fallback to no filtering
            },
            set = {
                // Serialize back to JSON for URL parameter
                DefaultJson.encodeToString(Condition.serializer(mc.serializer), it)
            }
        )

    /**
     * Builds a complete Query from text search, condition, sort, and columns.
     *
     * Text search behavior:
     * - If model has @TextIndex annotation, uses Condition.FullTextSearch
     * - Otherwise, splits search into words and searches each across all visible string columns
     * - Debounced by 500ms to avoid excessive queries during typing
     *
     * The final query combines:
     * - Text search condition (if present)
     * - User-defined condition from filter UI
     * - Sort order from sort UI
     *
     * All parts are debounced to prevent query spam during rapid UI changes.
     *
     * @return A reactive Query that updates whenever inputs change
     */
    private fun queryReadable(mc: ModelCache<UnknownModel, UnknownId>): Reactive<Query<UnknownModel>> {
        val sort = sortWritable(mc)
        val condition = conditionWritable(mc)
        val columns = columnsWritable(mc)

        // Check if model supports full-text search via @TextIndex annotation
        // Full-text search is server-side and much more efficient than column-by-column search
        val hasTextIndex = mc.serializer.serializableAnnotations.any { it.fqn.endsWith("TextIndex") }

        return remember {
            Query(
                // Combine text search and filter conditions with AND
                Condition.And<UnknownModel>(
                    listOfNotNull(
                        // Text search processing - debounced for performance
                        // TODO: Extract magic number 500ms to a constant
                        textSearch.debounce(500)().takeUnless { it.isBlank() }?.let {
                            if (hasTextIndex) {
                                // Use server's full-text search if available (preferred)
                                Condition.FullTextSearch(it)
                            } else {
                                // Fallback: search each word across all visible string columns
                                // Each word must match at least one column (AND of ORs)
                                // This can be slow on large datasets
                                it.split(' ').map { term ->
                                    columns().mapNotNull {
                                        // Extract base serializer type, removing nullability and modifiers
                                        val s = it.serializer.let {
                                            it.nullElement() ?: it
                                        }.descriptor.serialName.substringBefore('/')

                                        // Handle nullable columns by wrapping in NotNull check
                                        // Prevents searching null values
                                        val p =
                                            if (it.serializer.descriptor.isNullable)
                                                DataClassPathNotNull(it as DataClassPath<UnknownModel, Any?>)
                                            else it

                                        // Apply appropriate contains condition based on type
                                        // Only searches string and raw string types
                                        when {
                                            s == "kotlin.String" -> {
                                                p.mapCondition(
                                                    Condition.StringContains(term, ignoreCase = true) as Condition<Any?>
                                                )
                                            }
                                            s in IsRawString.serialNames -> {
                                                p.mapCondition(
                                                    Condition.RawStringContains<TrimmedString>(term, ignoreCase = true) as Condition<Any?>
                                                )
                                            }
                                            else -> null // Skip non-string columns
                                        }
                                    }.takeUnless { it.isEmpty() }?.let { Condition.Or(it) } ?: Condition.Always
                                }.let { Condition.And(it) }
                            }
                        },
                        // User-defined filter condition from the filter UI
                        // TODO: Extract magic number 500ms to a constant
                        condition.debounce(500)()
                    )
                ),
                // Sort order from sort UI
                // TODO: Extract magic number 500ms to a constant
                sort.debounce(500)()
            )
        }
    }
}

/**
 * Converts a Condition into a human-readable string for display.
 *
 * Used in the UI to show users what filters are currently applied.
 * Recursively processes nested conditions (And, Or, OnField).
 *
 * Falls back to toString() for unrecognized condition types.
 */
fun Condition<*>.friendly(): String {
    return when (this) {
        Condition.Always -> "All"
        is Condition.And<*> -> conditions.joinToString(" and ") { it.friendly() }
        is Condition.Or<*> -> conditions.joinToString(" or ") { it.friendly() }
        Condition.Never -> "None"
        is Condition.OnField<*, *> -> this.key.displayName + " " + condition.friendly()
        is Condition.Equal<*> -> "is $value"
        is Condition.NotEqual<*> -> "isn't $value"
        is Condition.GreaterThan<*> -> "> $value"
        is Condition.GreaterThanOrEqual<*> -> ">= $value"
        is Condition.LessThan<*> -> "< $value"
        is Condition.LessThanOrEqual<*> -> "<= $value"
        is Condition.Inside<*> -> "is ${values.joinToString(" or ")}"
        is Condition.NotInside<*> -> "isn't ${values.joinToString(" or ")}"
        is Condition.StringContains -> "contains $value"
        is Condition.GeoDistance -> "is within ${greaterThanKilometers} km and ${lessThanKilometers} km"
        is Condition.IfNotNull<*> -> condition.friendly()
        else -> toString()
    }
}

//@Routable("collections/{collectionName}/report")
//class CollectionAdminReportPage(val collectionName: String) : Page {
//
//    @QueryParameter("condition")
//    val conditionString: Signal<String?> = Signal(null)
//
//    val mc = remember {
//        adminServer().models[collectionName]?.cache(adminAuthentication())!!
//                as ModelCache<UnknownModel, UnknownId>
//    }
//
//    override fun ViewWriter.render(): Any? {
//        return col {
//            reactive {
//                clearChildren()
//                val mc = mc()
//                val forms = adminFormModule()
//                val condition = conditionWritable(mc)
//                card - col {
//                    h2("Filter")
//                    form(
//                        context = forms,
//                        serializer = Condition.serializer(mc.serializer),
//                        mutable = condition
//                    )
//                }
//                card - col {
//                    h2("Card")
//                    form(
//                        context = forms,
//                        serializer = Condition.serializer(mc.serializer),
//                        mutable = condition
//                    )
//                }
//            }
//        }
//    }
//
//    private fun conditionWritable(mc: ModelCache<UnknownModel, UnknownId>): MutableReactiveValue<Condition<UnknownModel>> =
//        conditionString.lens(
//            get = {
//                it?.let {
//                    try {
//                        DefaultJson.decodeFromString(Condition.serializer(mc.serializer), it)
//                    } catch (e: Exception) {
//                        null
//                    }
//                } ?: Condition.Always
//            },
//            set = { DefaultJson.encodeToString(Condition.serializer(mc.serializer), it) }
//}

/*
 * TODO: API Improvement Recommendations for CollectionAdminScreen.kt
 *
 * BUGS FOUND:
 * 1. Line 359: Permission display bug - "Restricted fields" under Update checks `readMask` instead of
 *    `updateRestrictions.fields`. This is a copy-paste error that causes update restrictions to not be shown.
 *
 * 2. ModelCache initialization (line 74): Force-unwraps with !! which will throw if collection doesn't exist.
 *    Should add proper error handling and show user-friendly error screen.
 *
 * 3. CSV Import (line 154): CSV parsing errors are not caught - will crash if CSV is malformed or doesn't
 *    match schema. Needs try-catch with user-friendly error messages.
 *
 * 4. Bulk operations: No error handling for bulk insert/delete failures. Users won't know if operation failed
 *    partially or completely.
 *
 * IMPROVEMENT RECOMMENDATIONS:
 *
 * 1. Export/Import Limits: The 100,000 item limit for export/import could cause memory issues with large
 *    records or complex nested objects. Consider:
 *    - Streaming export/import for large datasets
 *    - Server-side export generation with download links
 *    - Progress indicators for large operations
 *    - Chunked processing to avoid browser memory limits
 *
 * 2. Magic Numbers: Multiple debounce times (500ms) and size constraints should be extracted to constants
 *    or configuration. Makes tuning and consistency easier.
 *
 * 3. Error Handling: Generic exception catching throughout should be replaced with specific error types
 *    and user-friendly error messages. Add proper error logging for debugging.
 *
 * 4. Debug Logging: Remove println("Text is $text") and other debug statements from production code.
 *
 * 5. Text Search Performance: Fallback text search (when no @TextIndex) can be very slow on large datasets
 *    as it searches multiple columns. Consider:
 *    - Warning users when full-text search is unavailable
 *    - Limiting search to fewer columns by default
 *    - Adding a "search all columns" toggle
 *
 * 6. URL Query Parameter Parsing: All URL parameter parsing swallows exceptions silently. Add logging
 *    to help debug issues with bookmarked/shared URLs.
 *
 * 7. Bulk Delete Confirmation: While there is confirmation, consider additional safety measures for
 *    dangerous operations like deleting ALL items (Condition.Always):
 *    - Type-to-confirm for bulk deletes
 *    - Separate confirmation level for "delete all"
 *    - Operation history/audit log
 *
 * 8. Virtual Scrolling: TableRenderer handles this, but ensure performance with very large result sets
 *    (10k+ items). May need server-side pagination hints.
 *
 * 9. Column Selection: No UI visible in this file for customizing columns. Should be accessible from
 *    the toolbar for discoverability.
 *
 * 10. Permissions Display: The helper function `kv()` is defined inside a menu callback. Consider
 *     extracting to a reusable component for permission display elsewhere.
 *
 * 11. Filter Condition Display: The `friendly()` function could be extended to handle more condition types
 *     and provide more user-friendly descriptions (e.g., date ranges, complex nested conditions).
 *
 * 12. Reactive Rebuilds: The entire contents rebuild when mc or forms change. Consider more granular
 *     reactivity to avoid unnecessary rebuilds of the toolbar when only data changes.
 *
 * 13. Item Count Queries: Multiple places call `mc.skipCache.count(c)` which is a separate query.
 *     Consider caching or batching count queries to reduce server load.
 *
 * 14. Export Filename: Hard-coded as "data.csv". Should include collection name and timestamp for
 *     better organization (e.g., "users_2025-01-15.csv").
 *
 * 15. Import Validation: No validation preview before import. Consider showing a preview of parsed
 *     data and validation errors before allowing insert.
 */
