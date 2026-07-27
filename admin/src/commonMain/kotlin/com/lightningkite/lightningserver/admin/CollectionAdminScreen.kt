package com.lightningkite.lightningserver.admin

import com.lightningkite.services.data.*
import com.lightningkite.kiteui.*
import com.lightningkite.kiteui.forms.defaultColumns
import com.lightningkite.kiteui.forms.displayName
import com.lightningkite.kiteui.forms.naturalSort
import com.lightningkite.kiteui.forms.*
import com.lightningkite.kiteui.models.Icon
import com.lightningkite.kiteui.models.SelectedSemantic
import com.lightningkite.kiteui.models.rem
import com.lightningkite.kiteui.navigation.DefaultJson
import com.lightningkite.kiteui.navigation.Page
import com.lightningkite.kiteui.navigation.UrlProperties
import com.lightningkite.kiteui.navigation.encodeToString
import com.lightningkite.kiteui.navigation.pageNavigator
import com.lightningkite.kiteui.reactive.Action
import com.lightningkite.kiteui.views.*
import com.lightningkite.kiteui.views.direct.*
import com.lightningkite.kiteui.views.l2.dialog
import com.lightningkite.kiteui.views.direct.icon
import com.lightningkite.kotlinx.serialization.csv.CsvFormat
import com.lightningkite.kotlinx.serialization.csv.StringDeferringConfig
import com.lightningkite.services.database.*
import com.lightningkite.lightningserver.db.ModelCache
import com.lightningkite.reactive.context.ReactiveContext
import com.lightningkite.reactive.context.invoke
import com.lightningkite.reactive.context.reactive
import com.lightningkite.reactive.core.*
import com.lightningkite.reactive.extensions.debounce
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
// by Claude - refactored to use inner CollectionContents class for cleaner reactive bindings
@Routable("collections/{collectionName}")
class CollectionAdminPage(val collectionName: String) : Page {

    override val title: Reactive<String> = remember { collectionName }

    companion object {
        private const val DEBOUNCE_MS = 500L
        private const val EXPORT_LIMIT = 100_000
    }

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
     * The ModelCache instance for this collection (nullable for existence check).
     * Returns null if the collection doesn't exist in the schema.
     */
    private val mcOrNull = remember {
        adminServer().models[collectionName]?.cache(adminAuthentication())
                as? ModelCache<UnknownModel, UnknownId>
    }

    override fun ElementWriter.CanAddTheme.render() {
        col {
            reactive<Unit> {
                clearChildren()
                val mc = mcOrNull()
                if (mc == null) {
                    renderNotFound()
                    return@reactive
                }
                CollectionContents(mc, adminFormModule()).run { render() }
            }
        }
    }

    private fun ViewWriter.renderNotFound() {
        centered.col {
            h2("Collection Not Found")
            text("The collection '$collectionName' does not exist or is not accessible.")
            button {
                text("Go Home")
                onClick { pageNavigator.reset(HomePage()) }
            }
        }
    }

    /**
     * Inner component with ModelCache and FormModule as fixed constructor params.
     * All reactive bindings become simple properties instead of functions.
     */
    private inner class CollectionContents(
        val mc: ModelCache<UnknownModel, UnknownId>,
        val forms: FormModule
    ) {
        // by Claude - converted from functions to properties since mc/forms are now in scope
        val condition: MutableReactiveValue<Condition<UnknownModel>> =
            conditionString.lensJson(Condition.serializer(mc.serializer)) { Condition.Always }

        val sort: MutableReactiveValue<List<SortPart<UnknownModel>>> =
            sortString.lensJson(ListSerializer(SortPartSerializer(mc.serializer))) {
                mc.serializer.naturalSort()
            }

        @Suppress("UNCHECKED_CAST")
        val columns: MutableReactiveValue<List<ColumnInfo<UnknownModel>>> =
            columnsString.lensJson(ListSerializer(ColumnInfo.serializer(mc.serializer))) {
                mc.serializer.defaultColumns().map { ColumnInfo(it, forms) }
            } as MutableReactiveValue<List<ColumnInfo<UnknownModel>>>

        val hasTextIndex: Boolean =
            mc.serializer.serializableAnnotations.any { it.fqn.endsWith("TextIndex") }

        private val conditionD = condition.debounce(DEBOUNCE_MS, mc.scope)
        private val sortD = sort.debounce(DEBOUNCE_MS, mc.scope)

        /**
         * Builds a complete Query from text search, condition, sort, and columns.
         * All parts are debounced to prevent query spam during rapid UI changes.
         */
        val query: Reactive<Query<UnknownModel>> = remember {
            Query(
                condition = Condition.And(
                    listOfNotNull(
                        buildTextSearchCondition(),
                        conditionD()
                    )
                ),
                orderBy = sortD()
            )

        }

        private val textSearchD = textSearch.debounce(DEBOUNCE_MS, mc.scope)
        /**
         * Builds a text search condition from the current search text.
         * Uses full-text search if available, otherwise searches visible string columns.
         */
        private fun ReactiveContext.buildTextSearchCondition(): Condition<UnknownModel>? {
            val text = textSearchD().takeUnless { it.isBlank() } ?: return null
            return if (hasTextIndex) {
                Condition.FullTextSearch(text)
            } else {
                buildColumnSearchCondition(text)
            }
        }

        /**
         * Fallback text search: searches each word across all visible string columns.
         * Each word must match at least one column (AND of ORs).
         */
        private fun ReactiveContext.buildColumnSearchCondition(text: String): Condition<UnknownModel> {
            val cols = columns()
            return text.split(' ').map { term ->
                cols.mapNotNull { c ->
                    @Suppress("UNCHECKED_CAST")
                    val path = c.path as DataClassPath<UnknownModel, Any?>
                    val baseSerialName = path.serializer
                        .let { it.nullElement() ?: it }
                        .descriptor.serialName.substringBefore('/')

                    val searchPath = if (path.serializer.descriptor.isNullable) {
                        DataClassPathNotNull(path)
                    } else {
                        path
                    }

                    @Suppress("UNCHECKED_CAST")
                    when {
                        baseSerialName == "kotlin.String" -> searchPath.mapCondition(
                            Condition.StringContains(term, ignoreCase = true) as Condition<Any?>
                        )
                        baseSerialName in IsRawString.serialNames -> searchPath.mapCondition(
                            Condition.RawStringContains<TrimmedString>(term, ignoreCase = true) as Condition<Any?>
                        )
                        else -> null
                    }
                }.takeUnless { it.isEmpty() }?.let { Condition.Or(it) } ?: Condition.Always
            }.let { Condition.And(it) }
        }

        fun ViewWriter.render() {
            renderToolbar()
            renderSummary()
            renderTable()
        }

        private fun ViewWriter.renderToolbar() {
            rowCollapsingToColumn(50.rem) {
                expanding.fieldTheme.textInput {
                    content bind textSearch
                }

                row {
                    renderFilterButton()
                    renderSortButton()

                    link {
                        icon(Icon.info, "Statistics")
                        to = { CollectionStatsPage(collectionName) }
                    }

                    renderBulkActionsMenu()

                    link {
                        icon(Icon.add, "Add New")
                        to = {
                            NewItemAdminPage(collectionName).apply {
                                conditionString.value = this@CollectionAdminPage.conditionString.value
                            }
                        }
                    }
                }
            }
        }

        private fun ViewWriter.renderFilterButton() {
            menuButton {
                dynamicTheme { if (condition() != Condition.Always) SelectedSemantic else null }
                icon(Icon.filterList, "Filter")
                requireClick = true
                opensMenu {
                    form(forms, Condition.And.serializer(mc.serializer), condition.lens(
                        get = { it as? Condition.And ?: Condition.And(listOf(it)) },
                        set = { it }
                    ))
                }
            }
        }

        private fun ViewWriter.renderSortButton() {
            menuButton {
                dynamicTheme { if (sort().isNotEmpty()) SelectedSemantic else null }
                icon(Icon.sort, "Sort")
                requireClick = true
                opensMenu {
                    form(forms, ListSerializer(SortPartSerializer(mc.serializer)), sort)
                }
            }
        }

        private fun ViewWriter.renderBulkActionsMenu() {
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
                        renderPermissionsSection()
                    }
                }
            }
        }

        private fun ViewWriter.renderPermissionsSection() {
            col {
                h3("My Permissions")
                val permissions = remember { loadedPermissions().get(collectionName) ?: ModelPermissions() }

                permissionRow("Read") { permissions().read.simplify().friendly() }
                permissionRow(
                    "Restricted fields",
                    visibleIf = { permissions().readMask.pairs.isNotEmpty() }
                ) {
                    permissions().readMask.pairs
                        .flatMap { it.first.readPaths() }
                        .joinToString(", ") { it.properties.joinToString("'s ") { it.displayName } }
                }
                permissionRow("Create") { permissions().create.simplify().friendly() }
                permissionRow("Update") { permissions().update.simplify().friendly() }
                permissionRow(
                    "Restricted fields",
                    visibleIf = { permissions().updateRestrictions.perField.keys.isNotEmpty() }
                ) {
                    permissions().updateRestrictions.perField.keys
                        .joinToString(", ") { it.properties.joinToString("'s ") { it.displayName } }
                }
                permissionRow("Delete") { permissions().delete.simplify().friendly() }
            }
        }

        private fun ViewWriter.permissionRow(
            key: String,
            visibleIf: ReactiveContext.() -> Boolean = { true },
            value: ReactiveContext.() -> String
        ) {
            row {
                ::shown { visibleIf() }
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

        private fun ViewWriter.renderSummary() {
            row {
                subtext {
                    val itemCount = rememberSuspending {
                        val q = query()
                        mc.skipCache.count(q.condition)
                    }
                    ::content {
                        buildString {
                            val c = condition()
                            val ts = textSearch()
                            val hasTextSearch = ts.isNotBlank()
                            val hasFilter = c != Condition.Always

                            when {
                                c == Condition.Never -> append("Showing NO ITEMS ")
                                !hasTextSearch && !hasFilter -> append("Showing all ${itemCount()} items")
                                hasTextSearch && !hasFilter -> append("Showing ${itemCount()} items matching \"$ts\"")
                                !hasTextSearch && hasFilter -> append("Showing ${itemCount()} items where ${c.friendly()}")
                                else -> append("Showing ${itemCount()} items matching \"$ts\" where ${c.friendly()}")
                            }

                            val s = sort()
                            if (s.isNotEmpty()) {
                                append(", sorted by ")
                                s.forEach {
                                    append(it.field.properties.joinToString("'s ") { it.displayName })
                                    if (it.ascending) append(" ascending")
                                    else append(" descending")
                                }
                            }
                        }
                    }
                }
                unpadded.button {
                    centered.icon(Icon.close.copy(width = 0.75.rem, height = 0.75.rem), "Close")
                    ::visible { condition() != Condition.Always || sort() != mc.serializer.naturalSort() }
                    onClick { condition.value = Condition.Always; sort.value = mc.serializer.naturalSort() }
                }
            }
        }

        @Suppress("UNCHECKED_CAST")
        private fun ViewWriter.renderTable() {
            expanding.renderTable(
                module = forms,
                innerSerializer = mc.serializer,
                items = remember { mc.watch(query()) },
                columns = columns as MutableReactive<List<ColumnInfo<UnknownModel>>>,
                linkTo = {
                    val id = UrlProperties.encodeToString(mc.serializer._id().serializer, it._id)
                    return@renderTable { DetailAdminPage(collectionName, id) }
                }
            )
        }

        fun ViewWriter.exportDialog() = dialog { close ->
            col {
                h2("Export")

                important.button {
                    centered.text("Direct CSV")
                    action = Action("Download", Icon.download) {
                        val csv = createCsvFormat()
                        val data = mc.query(query().copy(limit = EXPORT_LIMIT))()
                        val content = csv.encodeToString(ListSerializer(mc.serializer), data)
                        ExternalServices.download("data.csv", content.toBlob("text/csv"), DownloadLocation.Downloads)
                        close()
                    }
                }

                important.button {
                    val success = Signal(false)
                    row {
                        expanding.stack()
                        centered.text("Copy CSV to Clipboard")
                        onlyWhen { success() }.icon(Icon.done, "Done")
                        expanding.stack()
                    }
                    action = Action("Copy", Icon.download) {
                        val csv = createCsvFormat()
                        val data = mc.query(query().copy(limit = EXPORT_LIMIT))()
                        val content = csv.encodeToString(ListSerializer(mc.serializer), data)
                        ExternalServices.setClipboardText(content)
                        success.value = true
                        close()
                    }
                }
            }
        }

        fun ViewWriter.importDialog() = dialog { close ->
            col {
                h2("Import")
                important.button {
                    centered.text("Upload Direct CSV")
                    action = Action("Upload", Icon.upload) {
                        val file = ExternalServices.requestFile(listOf("text/csv")) ?: return@Action
                        val csv = createCsvFormat()
                        val text = file.text()
                        val items = csv.decodeFromString(ListSerializer(mc.serializer), text)

                        confirmDanger("Upload ${items.size} items?", "Are you sure you want to upload these items?") {
                            mc.insert(items)
                        }
                        close()
                    }
                }
            }
        }

        fun ViewWriter.bulkDeleteDialog() = dialog { close ->
            col {
                h2("Bulk Delete")
                reactive<Unit> {
                    clearChildren()

                    val itemCount = rememberSuspending {
                        val c = condition()
                        mc.skipCache.count(c)
                    }

                    subtext {
                        ::content {
                            val c = condition()
                            when (c) {
                                Condition.Always -> "This will delete ALL ${itemCount()} items in the collection."
                                Condition.Never -> "No items will be deleted."
                                else -> "This will delete ${itemCount()} items where $c"
                            }
                        }
                    }

                    important.button {
                        centered.text("Delete All Matching Items")
                        action = Action("Delete", Icon.deleteForever) {
                            val c = condition()
                            confirmDanger(
                                "Delete all matching items?",
                                "Are you sure you want to delete all items matching the current query? This action cannot be undone."
                            ) {
                                mc.skipCache.bulkDelete(c)
                                mc.totallyInvalidate()
                            }
                            close()
                        }
                    }
                }
            }
        }

        private fun createCsvFormat() = CsvFormat(StringDeferringConfig(DefaultJson.serializersModule, ignoreUnknownKeys = true))
    }
}
