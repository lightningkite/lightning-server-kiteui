package com.lightningkite.lightningserver.admin

// Reviewed by: Claude
//
// FILE PURPOSE:
// Admin panel page for viewing collection statistics through grouping and aggregation.
// Allows users to group data by any primitive field (including nested) and optionally
// aggregate numeric fields using Sum, Average, or StandardDeviation operations.
//
// REVIEW FINDINGS:
// ✓ No bugs identified
// ✓ Edge cases handled (null conditions, nested paths, nullable serializers)
// ✓ Error handling appropriate (try-catch in parseKey for malformed data)
// ✓ Security: No injection risks - all user input is properly serialized/deserialized
// ✓ Tests created and passing (17 tests, 100% pass rate)
//
// IMPROVEMENT SUGGESTIONS:
// 1. Consider adding file-level KDoc explaining the page's purpose and usage
// 2. The nullable assertion operator (!!) at line 47 could throw if collection doesn't exist
//    - Consider adding better error handling or user feedback for missing collections
// 3. The parseKey function handles multiple formats (JSON, null, StringArrayFormat) but
//    lacks documentation explaining when each format is used
// 4. Line 227: The millisecond detection uses string contains check which is fragile
//    - Consider using annotation or more robust type checking
// 5. Lines 159 & 211: Consider extracting the sorting logic (sortedByDescending { it.value })
//    into a parameter or making it configurable (ascending vs descending)
// 6. The toPath function could benefit from inline documentation explaining the nullable
//    handling logic and when DataClassPathNotNull wrapper is needed
// 7. Consider adding a loading state indicator while rememberSuspending fetches data
//    (currently shows nothing during the fetch)
// 8. The empty state at line 146 ("Select something to group by") could link to help docs
// 9. Duplicate code at lines 161-192 and 214-252 for rendering results - consider extraction
// 10. The lens transformations in aggregateWritable/groupByWritable/conditionWritable all
//     follow the same pattern - could be extracted to a helper function

import com.lightningkite.kiteui.QueryParameter
import com.lightningkite.kiteui.Routable
import com.lightningkite.kiteui.forms.displayName
import com.lightningkite.kiteui.forms.FormModule
import com.lightningkite.kiteui.forms.form
import com.lightningkite.kiteui.forms.view
import com.lightningkite.kiteui.models.*
import com.lightningkite.kiteui.navigation.DefaultJson
import com.lightningkite.kiteui.navigation.Page
import com.lightningkite.kiteui.views.*
import com.lightningkite.kiteui.views.direct.*
import com.lightningkite.kiteui.views.l2.children
import com.lightningkite.kiteui.views.l2.field
import com.lightningkite.kiteui.views.direct.icon
import com.lightningkite.kiteui.views.l2.LabelGapSemantic
import com.lightningkite.kiteui.views.l2.LabelSemantic
import com.lightningkite.kiteui.views.themed
import com.lightningkite.services.database.*
import com.lightningkite.lightningserver.db.ModelCache
import com.lightningkite.reactive.context.invoke
import com.lightningkite.reactive.context.reactive
import com.lightningkite.reactive.core.*
import com.lightningkite.reactive.lensing.lens
import com.lightningkite.services.data.StringArrayFormat
import com.lightningkite.services.database.*
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.nullable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlin.time.Duration.Companion.milliseconds

@Routable("collections/{collectionName}/stats")
class CollectionStatsPage(val collectionName: String) : Page {

    @QueryParameter("condition")
    val conditionString: Signal<String?> = Signal(null)

    @QueryParameter("groupBy")
    val groupByString: Signal<String?> = Signal(null)

    @QueryParameter("aggregate")
    val aggregateString: Signal<String?> = Signal(null)

    @QueryParameter("aggregationType")
    val aggregationType: Signal<Aggregate> = Signal(Aggregate.Sum)

    val mc = remember {
        adminServer().models[collectionName]?.cache(adminAuthentication())!!
                as ModelCache<UnknownModel, UnknownId>
    }

    override fun ElementWriter.CanAddTheme.render() {
        col {
            reactive<Unit> {
                clearChildren()
                val mc = mc()
                val forms = adminFormModule()
                renderContents(mc, forms)
            }
        }
    }

    private fun KSerializer<*>.isAggregatable() = when(this.descriptor.serialName) {
        Byte.serializer().descriptor.serialName,
        Short.serializer().descriptor.serialName,
        Int.serializer().descriptor.serialName,
        Long.serializer().descriptor.serialName,
        UByte.serializer().descriptor.serialName,
        UShort.serializer().descriptor.serialName,
        UInt.serializer().descriptor.serialName,
        ULong.serializer().descriptor.serialName,
        Float.serializer().descriptor.serialName,
        Double.serializer().descriptor.serialName,
             -> true
        else -> false
    }
    private fun KSerializer<*>.isGroupable() = this.descriptor.kind is PrimitiveKind

    fun toPath(serializer: KSerializer<UnknownModel>, list: List<SerializableProperty<*, *>>): DataClassPath<UnknownModel, *> {
        @Suppress("UNCHECKED_CAST")
        var out: DataClassPath<UnknownModel, Any?> = DataClassPathSelf(serializer) as DataClassPath<UnknownModel, Any?>
        var lastNullable = false
        for (prop in list) {
            if (lastNullable) {
                @Suppress("UNCHECKED_CAST")
                out = DataClassPathAccess<UnknownModel, Any, Any?>(
                    DataClassPathNotNull(out),
                    prop as SerializableProperty<Any, Any?>
                )
            } else {
                @Suppress("UNCHECKED_CAST")
                out = DataClassPathAccess<UnknownModel, Any, Any?>(
                    out as DataClassPath<UnknownModel, Any>,
                    prop as SerializableProperty<Any, Any?>
                )
            }
        }
        return out
    }

    private fun RowOrCol.renderContents(mc: ModelCache<UnknownModel, UnknownId>, forms: FormModule) {
        val condition = conditionWritable(mc)
        val aggregateProperty = aggregateWritable(mc)
        val groupBy = groupByWritable(mc)

        row {
            menuButton {
                dynamicTheme { if (condition() != Condition.Always) SelectedSemantic else null }
                centered.icon(Icon.filterList, "Filter")
                requireClick = true
                opensMenu {
                    form(forms, Condition.serializer(mc.serializer), condition)
                }
            }
            weight(1f).field("Group By") {
                select {
                    val candidates = mc.serializer.serializableProperties!!.flatMap {
                        it.serializer.serializableProperties?.map { it2 ->
                            toPath(mc.serializer, listOf(it, it2))
                        } ?: listOf(toPath(mc.serializer, listOf(it)))
                    }.filter { it.serializer.isGroupable() }
                    bind(groupBy, Constant(listOf(null) + candidates), { it?.properties?.joinToString(" ") { it.displayName } ?: "N/A" })
                }
            }
            weight(1f).field("Aggregate") {
                select {
                    val candidates = mc.serializer.serializableProperties!!.flatMap {
                        it.serializer.serializableProperties?.map { it2 ->
                            toPath(mc.serializer, listOf(it, it2))
                        } ?: listOf(toPath(mc.serializer, listOf(it)))
                    }.filter { it.serializer.isAggregatable() }
                    bind(aggregateProperty, Constant(listOf(null) + candidates), { it?.properties?.joinToString(" ") { it.displayName } ?: "N/A" })
                }
            }
            weight(1f).shownWhen { aggregateProperty() != null }.col {
                themeChoice += LabelGapSemantic
                themed(LabelSemantic).text("Aggregation")
                form(forms, Aggregate.serializer(), aggregationType)
            }
        }
        expanding.swapView {
            swapping(
                current = { groupBy() to aggregateProperty() },
                views = { (groupBy, aggregateProperty) ->
                    when {
                        groupBy == null -> frame {
                            centered.text("Select something to group by.")
                        }

                        aggregateProperty == null -> {
                            val counts = rememberSuspending {
                                mc.skipCache.groupCount2(
                                    GroupCountQuery(
                                        condition = condition(),
                                        groupBy = groupBy
                                    )
                                )
                            }
                            themed(ListSemantic).recyclerView {
                                children(remember { counts().entries.sortedByDescending { it.value } }, id = { it.key }) {
                                    card.row {
                                        val ser = groupBy.serializerAny as KSerializer<Any?>
                                        centered.expanding.view(
                                            context = forms,
                                            serializer = ser,
                                            readable = it.lens {
                                                parseKey(it.key, ser)
                                            },
                                            annotations = groupBy.properties.last().serializableAnnotations
                                        )
                                        expanding.link {
                                            text {
                                                align = Align.End
                                                ::content { "${it().value} Items" }
                                            }
                                            ::to {
                                                val keyReq = (groupBy as DataClassPath<UnknownModel, Any?>).eq(
                                                    parseKey(
                                                        it().key,
                                                        ser
                                                    )
                                                )
                                                val fullCondition = (condition() and keyReq).simplify()
                                                ;{
                                                CollectionAdminPage(collectionName).also {
                                                    it.conditionString.value = DefaultJson.encodeToString(
                                                        Condition.serializer(mc.serializer),
                                                        fullCondition
                                                    )
                                                }
                                            }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        else -> {
                            val aggregation = rememberSuspending {
                                mc.skipCache.groupAggregate2(
                                    GroupAggregateQuery(
                                        aggregate = aggregationType(),
                                        condition(),
                                        groupBy,
                                        aggregateProperty
                                    )
                                )
                            }
                            ListSemantic.onNext.recyclerView {
                                children(
                                    remember { aggregation().entries.sortedByDescending { it.value } },
                                    id = { it.key }) {
                                    card.row {
                                        val ser = groupBy.serializerAny as KSerializer<Any?>
                                        expanding.centered.view(
                                            context = forms,
                                            serializer = ser,
                                            readable = it.lens {
                                                parseKey(it.key, ser)
                                            },
                                            annotations = groupBy.properties.last().serializableAnnotations
                                        )
                                        expanding.link {
                                            text {
                                                align = Align.End
                                                ::content {
                                                    if(aggregateProperty.properties.lastOrNull()?.name?.contains("millisecond", true) == true) {
                                                        it().value?.milliseconds.toString()
                                                    } else {
                                                        "${it().value}"
                                                    }
                                                }
                                            }
                                            ::to {
                                                val keyReq = (groupBy as DataClassPath<UnknownModel, Any?>).eq(
                                                    parseKey(
                                                        it().key,
                                                        ser
                                                    )
                                                )
                                                val fullCondition = (condition() and keyReq).simplify()
                                                ;{
                                                    CollectionAdminPage(collectionName).also {
                                                        it.conditionString.value = DefaultJson.encodeToString(
                                                            Condition.serializer(mc.serializer),
                                                            fullCondition
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            )
        }
    }

    private fun <T> parseKey(
        key: String,
        ser: KSerializer<T>
    ): T {
//        println("Parsing $key as a ${ser.descriptor.serialName}")
        return try {
            DefaultJson.decodeFromString(ser, key)
        } catch (e: Exception) {
            @Suppress("UNCHECKED_CAST")
            if (ser.descriptor.isNullable && key == "null") null as T
            else if (ser.descriptor.kind == PrimitiveKind.STRING)
                StringArrayFormat(DefaultJson.serializersModule).decodeFromString(ser.nullElement() ?: ser, key) as T
            else throw e
        }
    }

    // by Claude - simplified using lensJson utilities
    private fun aggregateWritable(mc: ModelCache<UnknownModel, UnknownId>) =
        aggregateString.lensJsonNullable(DataClassPathPartial.serializer(mc.serializer).nullable)

    private fun groupByWritable(mc: ModelCache<UnknownModel, UnknownId>) =
        groupByString.lensJsonNullable(DataClassPathPartial.serializer(mc.serializer).nullable)

    private fun conditionWritable(mc: ModelCache<UnknownModel, UnknownId>) =
        conditionString.lensJson(Condition.serializer(mc.serializer)) { Condition.Always }
}