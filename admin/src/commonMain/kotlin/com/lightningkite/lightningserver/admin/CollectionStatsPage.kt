package com.lightningkite.lightningserver.admin

import com.lightningkite.kiteui.QueryParameter
import com.lightningkite.kiteui.Routable
import com.lightningkite.kiteui.forms.FormModule
import com.lightningkite.kiteui.forms.displayName
import com.lightningkite.kiteui.forms.form
import com.lightningkite.kiteui.forms.view
import com.lightningkite.kiteui.models.*
import com.lightningkite.kiteui.navigation.DefaultJson
import com.lightningkite.kiteui.navigation.Page
import com.lightningkite.kiteui.views.*
import com.lightningkite.kiteui.views.direct.*
import com.lightningkite.kiteui.views.l2.children
import com.lightningkite.kiteui.views.l2.field
import com.lightningkite.kiteui.views.l2.icon
import com.lightningkite.lightningdb.*
import com.lightningkite.lightningserver.StringArrayFormat
import com.lightningkite.lightningserver.db.ModelCache
import com.lightningkite.reactive.context.invoke
import com.lightningkite.reactive.context.reactive
import com.lightningkite.reactive.core.*
import com.lightningkite.reactive.lensing.lens
import com.lightningkite.serialization.*
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
                centered - icon(Icon.filterList, "Filter")
                requireClick = true
                opensMenu {
                    form(forms, Condition.serializer(mc.serializer), condition)
                }
            }
            weight(1f) - field("Group By") {
                select {
                    val candidates = mc.serializer.serializableProperties!!.flatMap {
                        it.serializer.serializableProperties?.map { it2 ->
                            toPath(mc.serializer, listOf(it, it2))
                        } ?: listOf(toPath(mc.serializer, listOf(it)))
                    }.filter { it.serializer.isGroupable() }
                    bind(groupBy, Constant(listOf(null) + candidates), { it?.properties?.joinToString(" ") { it.displayName } ?: "N/A" })
                }
            }
            weight(1f) - field("Aggregate") {
                select {
                    val candidates = mc.serializer.serializableProperties!!.flatMap {
                        it.serializer.serializableProperties?.map { it2 ->
                            toPath(mc.serializer, listOf(it, it2))
                        } ?: listOf(toPath(mc.serializer, listOf(it)))
                    }.filter { it.serializer.isAggregatable() }
                    bind(aggregateProperty, Constant(listOf(null) + candidates), { it?.properties?.joinToString(" ") { it.displayName } ?: "N/A" })
                }
            }
            weight(1f) - shownWhen { aggregateProperty() != null } - col {
                gap = 0.px
                FieldLabelSemantic.onNext - text("Aggregation")
                form(forms, Aggregate.serializer(), aggregationType)
            }
        }
        expanding - swapView {
            swapping(
                current = { groupBy() to aggregateProperty() },
                views = { (groupBy, aggregateProperty) ->
                    when {
                        groupBy == null -> frame {
                            centered - text("Select something to group by.")
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
                            ListSemantic.onNext - recyclerView {
                                children(remember { counts().entries.sortedByDescending { it.value } }, id = { it.key }) {
                                    card - row {
                                        val ser = groupBy.serializerAny as KSerializer<Any?>
                                        expanding - centered - view(
                                            context = forms,
                                            serializer = ser,
                                            readable = it.lens {
                                                parseKey(it.key, ser)
                                            },
                                            annotations = groupBy.properties.last().serializableAnnotations
                                        )
                                        expanding - link {
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
                            ListSemantic.onNext - recyclerView {
                                children(
                                    remember { aggregation().entries.sortedByDescending { it.value } },
                                    id = { it.key }) {
                                    card - row {
                                        val ser = groupBy.serializerAny as KSerializer<Any?>
                                        expanding - centered - view(
                                            context = forms,
                                            serializer = ser,
                                            readable = it.lens {
                                                parseKey(it.key, ser)
                                            },
                                            annotations = groupBy.properties.last().serializableAnnotations
                                        )
                                        expanding - link {
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

    private fun aggregateWritable(mc: ModelCache<UnknownModel, UnknownId>): MutableReactiveValue<DataClassPathPartial<UnknownModel>?> =
        aggregateString.lens(
            get = {
                it?.let {
                    try {
                        DefaultJson.decodeFromString(DataClassPathPartial.serializer(mc.serializer).nullable, it)
                    } catch (e: Exception) {
                        null
                    }
                }
            },
            set = { DefaultJson.encodeToString(DataClassPathPartial.serializer(mc.serializer).nullable, it) }
        )

    private fun groupByWritable(mc: ModelCache<UnknownModel, UnknownId>): MutableReactiveValue<DataClassPathPartial<UnknownModel>?> =
        groupByString.lens(
            get = {
                it?.let {
                    try {
                        DefaultJson.decodeFromString(DataClassPathPartial.serializer(mc.serializer).nullable, it)
                    } catch (e: Exception) {
                        null
                    }
                }
            },
            set = { DefaultJson.encodeToString(DataClassPathPartial.serializer(mc.serializer).nullable, it) }
        )

    private fun conditionWritable(mc: ModelCache<UnknownModel, UnknownId>): MutableReactiveValue<Condition<UnknownModel>> =
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
}