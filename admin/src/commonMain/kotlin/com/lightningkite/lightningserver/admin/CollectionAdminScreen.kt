package com.lightningkite.lightningserver.admin

import com.lightningkite.IsRawString
import com.lightningkite.TrimmedString
import com.lightningkite.kiteui.Blob
import com.lightningkite.kiteui.DownloadLocation
import com.lightningkite.kiteui.ExternalServices
import com.lightningkite.kiteui.QueryParameter
import com.lightningkite.kiteui.Routable
import com.lightningkite.kiteui.fetch
import com.lightningkite.kiteui.forms.*
import com.lightningkite.kiteui.models.Icon
import com.lightningkite.kiteui.models.SelectedSemantic
import com.lightningkite.kiteui.models.rem
import com.lightningkite.kiteui.navigation.DefaultJson
import com.lightningkite.kiteui.navigation.Page
import com.lightningkite.kiteui.navigation.UrlProperties
import com.lightningkite.kiteui.navigation.encodeToString
import com.lightningkite.readable.*
import com.lightningkite.kiteui.requestFile
import com.lightningkite.kiteui.text
import com.lightningkite.kiteui.toBlob
import com.lightningkite.kiteui.views.ViewModifiable
import com.lightningkite.kiteui.views.ViewWriter
import com.lightningkite.kiteui.views.card
import com.lightningkite.kiteui.views.centered
import com.lightningkite.kiteui.views.dialog
import com.lightningkite.kiteui.views.direct.*
import com.lightningkite.kiteui.views.dynamicTheme
import com.lightningkite.kiteui.views.expanding
import com.lightningkite.kiteui.views.fieldTheme
import com.lightningkite.kiteui.views.important
import com.lightningkite.kiteui.views.l2.dialog
import com.lightningkite.kiteui.views.l2.icon
import com.lightningkite.kotlinx.serialization.csv.CsvFormat
import com.lightningkite.kotlinx.serialization.csv.StringDeferringConfig
import com.lightningkite.lightningdb.*
import com.lightningkite.lightningserver.db.ModelCache
import com.lightningkite.serialization.*
import kotlinx.serialization.builtins.ListSerializer
import com.lightningkite.lightningdb.simplify
import com.lightningkite.lightningserver.files.ServerFile
import com.lightningkite.lightningserver.files.ServerFileSerializer
import kotlinx.serialization.builtins.nullable


@Routable("collections/{collectionName}")
class CollectionAdminPage(val collectionName: String) : Page {

    @QueryParameter("query")
    val textSearch: Property<String> = Property("")

    @QueryParameter("condition")
    val conditionString: Property<String?> = Property(null)

    @QueryParameter("sort")
    val sortString: Property<String?> = Property(null)

    @QueryParameter("columns")
    val columnsString: Property<String?> = Property(null)

    val mc = shared {
        adminServer().models[collectionName]?.cache(adminAuthentication())!!
                as ModelCache<UnknownModel, UnknownId>
    }

    fun ViewWriter.exportDialog() = dialog { close ->
        col {
            h2("Export")
            important - button {
                centered - text("Direct CSV")
                action = Action("Download", Icon.download) {
                    val csv = CsvFormat(StringDeferringConfig(DefaultJson.serializersModule, ignoreUnknownKeys = true))
                    val mc = mc()
                    val data = mc.query(
                        queryReadable(mc)().copy(limit = 100_000)
                    )()
                    val items = csv.encodeToString(ListSerializer(mc().serializer), data)
                    ExternalServices.download("data.csv", items.toBlob("text/csv"), DownloadLocation.Downloads)
                    close()
                }
            }
            important - button {
                val success = Property(false)
                row {
                    expanding - stack()
                    centered - text("Copy CSV to Clipboard")
                    onlyWhen { success() } - icon(Icon.done, "Done")
                    expanding - stack()
                }
                action = Action("Download", Icon.download) {
                    val csv = CsvFormat(StringDeferringConfig(DefaultJson.serializersModule, ignoreUnknownKeys = true))
                    val mc = mc()
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

    fun ViewWriter.importDialog() = dialog { close ->
        col {
            h2("Import")
            important - button {
                centered - text("Upload Direct CSV")
                action = Action("Upload", Icon.upload) {
                    val file = ExternalServices.requestFile(listOf("text/csv")) ?: return@Action
                    val csv = CsvFormat(StringDeferringConfig(DefaultJson.serializersModule, ignoreUnknownKeys = true))
                    val text = file.text()
                    println("Text is $text")
                    val items = csv.decodeFromString(ListSerializer(mc().serializer), text)
                    confirmDanger("Upload ${items.size} items?", "Are you sure you want to upload these items?") {
                        mc().insert(items)
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
                val mc = mc()
                val condition = conditionWritable(mc)
                val itemCount = sharedSuspending {
                    val c = condition()
                    mc.skipCache.count(c)
                }
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
                important - button {
                    centered - text("Delete All Matching Items")
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

    override fun ViewWriter.render(): ViewModifiable {
        return col {
            reactive<Unit> {
                clearChildren()
                val mc = mc()
                val forms = adminFormModule()
                renderContents(mc, forms)
            }
        }
    }

    private fun RowOrCol.renderContents(mc: ModelCache<UnknownModel, UnknownId>, forms: FormModule) {
        val condition = conditionWritable(mc)
        val sort = sortWritable(mc)

        @Suppress("UNCHECKED_CAST")
        val columns: ImmediateWritable<List<DataClassPath<UnknownModel, *>>> =
            columnsWritable(mc)
        val query = queryReadable(mc)
        row {
            expanding - fieldTheme - textInput {
                content bind textSearch
            }
            menuButton {
                dynamicTheme { if (condition() != Condition.Always) SelectedSemantic else null }
                icon(Icon.filterList, "Filter")
                requireClick = true
                opensMenu {
                    form(forms, Condition.serializer(mc.serializer), condition)
                }
            }
            menuButton {
                dynamicTheme { if (sort().isNotEmpty()) SelectedSemantic else null }
                icon(Icon.sort, "Sort")
                requireClick = true
                opensMenu {
                    form(forms, ListSerializer(SortPartSerializer(mc.serializer)), sort)
                }
            }
            link {
                icon(Icon.info, "Statistics")
                to = { CollectionStatsPage(collectionName) }
            }
            menuButton {
                icon(Icon.moreVert, "Bulk Actions")
                requireClick = true
                opensMenu {
                    col {
                        important - button {
                            text("Export...")
                            onClick { exportDialog() }
                        }
                        important - button {
                            text("Import...")
                            onClick { importDialog() }
                        }
                        important - button {
                            text("Bulk Delete...")
                            onClick { bulkDeleteDialog() }
                        }
                        col {
                            h3("My Permissions")
                            val p = shared { loadedPermissions().get(collectionName) ?: ModelPermissions() }
                            fun ViewWriter.kv(
                                key: String,
                                visibleIf: ReactiveContext.() -> Boolean = { true },
                                value: ReactiveContext.() -> String
                            ) {
                                row {
                                    ::exists { visibleIf() }
                                    expanding - text {
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
                            kv("Restricted fields", visibleIf = { p().readMask.pairs.isNotEmpty() }) {
                                p().updateRestrictions.fields
                                    .joinToString(", ") { it.path.properties.joinToString("'s ") { it.displayName } }
                            }
                            kv("Delete") { p().delete.simplify().friendly() }
                        }
                    }
                }
            }
            link {
                icon(Icon.add, "Add New")
                to = {
                    NewItemAdminPage(collectionName).apply {
                        conditionString.value = this@CollectionAdminPage.conditionString.value
                    }
                }
            }
        }
        subtext {
            val itemCount = sharedSuspending {
                val c = condition()
                mc.skipCache.count(c)
            }
            ::content {
                buildString {
                    val c = condition()
                    when (c) {
                        Condition.Always -> append("Showing all ${itemCount()} items ")
                        Condition.Never -> append("Showing NO ITEMS ")
                        else -> append("Showing ${itemCount()} items where $c ")
                    }
                    val s = sort()
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
        expanding - TableRenderer.view<UnknownModel>(
            formModule = forms,
            writer = this@renderContents,
            innerSer = mc.serializer,
            columns = columns,
            readable = shared {
                mc.watch(query())
            },
            linkTo = {
                val id = UrlProperties.encodeToString(mc.serializer._id().serializer, it._id)
                return@view { DetailAdminPage(collectionName, id) }
            }
        )
    }

    private fun columnsWritable(mc: ModelCache<UnknownModel, UnknownId>): ImmediateWritable<List<DataClassPath<UnknownModel, *>>> =
        columnsString.lens(
            get = {
                it?.let {
                    try {
                        DefaultJson.decodeFromString(
                            ListSerializer(DataClassPathSerializer(mc.serializer)),
                            it
                        ) as List<DataClassPath<UnknownModel, *>>
                    } catch (e: Exception) {
                        null
                    }
                } ?: mc.serializer.defaultColumns()
            },
            set = {
                DefaultJson.encodeToString(
                    ListSerializer(DataClassPathSerializer(mc.serializer)),
                    it as List<DataClassPathPartial<UnknownModel>>
                )
            }
        )

    private fun sortWritable(mc: ModelCache<UnknownModel, UnknownId>): ImmediateWritable<List<SortPart<UnknownModel>>> =
        sortString.lens(
            get = {
                it?.let {
                    try {
                        DefaultJson.decodeFromString(ListSerializer(SortPartSerializer(mc.serializer)), it)
                    } catch (e: Exception) {
                        null
                    }
                } ?: mc.serializer.naturalSort()
            },
            set = { DefaultJson.encodeToString(ListSerializer(SortPartSerializer(mc.serializer)), it) }
        )

    private fun conditionWritable(mc: ModelCache<UnknownModel, UnknownId>): ImmediateWritable<Condition<UnknownModel>> =
        conditionString.lens(
            get = {
                it?.let {
                    try {
                        DefaultJson.decodeFromString(Condition.serializer(mc.serializer), it)
                    } catch (e: Exception) {
                        null
                    }
                } ?: Condition.Always
            },
            set = { DefaultJson.encodeToString(Condition.serializer(mc.serializer), it) }
        )

    private fun queryReadable(mc: ModelCache<UnknownModel, UnknownId>): Readable<Query<UnknownModel>> {
        val sort = sortWritable(mc)
        val condition = conditionWritable(mc)
        val columns = columnsWritable(mc)
        val hasTextIndex = mc.serializer.serializableAnnotations.any { it.fqn.endsWith("TextIndex") }
        return shared {
            Query(
                Condition.And<UnknownModel>(
                    listOfNotNull(
                        textSearch.debounce(500)().takeUnless { it.isBlank() }?.let {
                            if (hasTextIndex) Condition.FullTextSearch(it)
                            else {
                                it.split(' ').map { term ->
                                    columns().mapNotNull {
                                        val s = it.serializer.let {
                                            it.nullElement() ?: it
                                        }.descriptor.serialName.substringBefore('/')
                                        val p =
                                            if (it.serializer.descriptor.isNullable) DataClassPathNotNull(it as DataClassPath<UnknownModel, Any?>) else it
                                        if (s == "kotlin.String") {
                                            p.mapCondition(
                                                Condition.StringContains(
                                                    term,
                                                    true
                                                ) as Condition<Any?>
                                            )
                                        } else if (s in IsRawString.serialNames) {
                                            p.mapCondition(
                                                Condition.RawStringContains<TrimmedString>(
                                                    term,
                                                    true
                                                ) as Condition<Any?>
                                            )
                                        } else null
                                    }.takeUnless { it.isEmpty() }?.let { Condition.Or(it) } ?: Condition.Always
                                }.let { Condition.And(it) }
                            }
                        },
                        condition.debounce(500)()
                    )
                ), sort.debounce(500)()
            )
        }
    }
}

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
//    val conditionString: Property<String?> = Property(null)
//
//    val mc = shared {
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
//                        writable = condition
//                    )
//                }
//                card - col {
//                    h2("Card")
//                    form(
//                        context = forms,
//                        serializer = Condition.serializer(mc.serializer),
//                        writable = condition
//                    )
//                }
//            }
//        }
//    }
//
//    private fun conditionWritable(mc: ModelCache<UnknownModel, UnknownId>): ImmediateWritable<Condition<UnknownModel>> =
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
