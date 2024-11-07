package com.lightningkite.lightningserver.admin

import com.lightningkite.IsRawString
import com.lightningkite.TrimmedString
import com.lightningkite.kiteui.QueryParameter
import com.lightningkite.kiteui.Routable
import com.lightningkite.kiteui.forms.TableRenderer
import com.lightningkite.kiteui.forms.form
import com.lightningkite.kiteui.forms.importance
import com.lightningkite.kiteui.models.Icon
import com.lightningkite.kiteui.models.SelectedSemantic
import com.lightningkite.kiteui.navigation.DefaultJson
import com.lightningkite.kiteui.navigation.Screen
import com.lightningkite.kiteui.navigation.UrlProperties
import com.lightningkite.kiteui.navigation.encodeToString
import com.lightningkite.kiteui.reactive.*
import com.lightningkite.kiteui.views.ViewWriter
import com.lightningkite.kiteui.views.direct.*
import com.lightningkite.kiteui.views.dynamicTheme
import com.lightningkite.kiteui.views.expanding
import com.lightningkite.kiteui.views.fieldTheme
import com.lightningkite.kiteui.views.l2.icon
import com.lightningkite.lightningdb.*
import com.lightningkite.lightningserver.db.ModelCache
import com.lightningkite.serialization.*
import kotlinx.serialization.builtins.ListSerializer


@Routable("collections/{collectionName}")
class CollectionAdminScreen(val collectionName: String) : Screen {

    @QueryParameter("query")
    val textSearch: Property<String> = Property("")

    @QueryParameter("condition")
    val conditionString: Property<String?> = Property(null)

    @QueryParameter("sort")
    val sortString: Property<String?> = Property(null)

    @QueryParameter("columns")
    val columnsString: Property<String?> = Property(null)

    override fun ViewWriter.render() {
        val mc = shared { adminServer().models[collectionName]?.cache(adminAuthentication())!! }
        col {
            reactive {
                clearChildren()
                val mc = mc() as ModelCache<HasId<Comparable<Comparable<*>>>, Comparable<Comparable<*>>>
                val forms = adminFormModule()
                val condition = conditionString.lens(
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
                val sort = sortString.lens(
                    get = {
                        it?.let {
                            try {
                                DefaultJson.decodeFromString(ListSerializer(SortPartSerializer(mc.serializer)), it)
                            } catch (e: Exception) {
                                null
                            }
                        } ?: listOf()
                    },
                    set = { DefaultJson.encodeToString(ListSerializer(SortPartSerializer(mc.serializer)), it) }
                )
                val columns: ImmediateWritable<List<DataClassPath<HasId<Comparable<Comparable<*>>>, *>>> = columnsString.lens(
                    get = {
                        (it?.let {
                            try {
                                DefaultJson.decodeFromString(ListSerializer(DataClassPathSerializer(mc.serializer)), it)
                            } catch (e: Exception) {
                                null
                            }
                        } ?: mc.serializer.serializableProperties!!.sortedBy {
                            it.importance
                        }.take(5).map {
                            DataClassPathAccess(DataClassPathSelf(mc.serializer), it)
                        }) as List<DataClassPath<HasId<Comparable<Comparable<*>>>, *>>
                    },
                    set = { DefaultJson.encodeToString(ListSerializer(DataClassPathSerializer(mc.serializer)), it) }
                )
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
                        icon(Icon.add, "Add New")
                        to = { NewItemAdminScreen(collectionName).apply { conditionString.value = this@CollectionAdminScreen.conditionString.value } }
                    }
                }
                val hasTextIndex = mc.serializer.serializableAnnotations.any { it.fqn.endsWith("TextIndex") }
                expanding - TableRenderer.view<HasId<Comparable<Comparable<*>>>>(
                    formModule = forms,
                    writer = this@col,
                    innerSer = mc.serializer,
                    columns = columns,
                    readable = shared {
                        mc.watch(
                            Query(
                                Condition.And<HasId<Comparable<Comparable<*>>>>(
                                    listOfNotNull(
                                        textSearch.debounce(500)().takeUnless { it.isBlank() }?.let {
                                            if (hasTextIndex) Condition.FullTextSearch(it)
                                            else {
                                                it.split(' ').map { term ->
                                                    columns().mapNotNull {
                                                        val s = it.serializer.let { it.nullElement() ?: it }.descriptor.serialName.substringBefore('/')
                                                        val p = if (it.serializer.descriptor.isNullable) DataClassPathNotNull(it as DataClassPath<HasId<Comparable<Comparable<*>>>, Any?>) else it
                                                        if (s == "kotlin.String") {
                                                            p.mapCondition(Condition.StringContains(term, true) as Condition<Any?>)
                                                        } else if (s in IsRawString.serialNames) {
                                                            p.mapCondition(Condition.RawStringContains<TrimmedString>(term, true) as Condition<Any?>)
                                                        } else null
                                                    }.takeUnless { it.isEmpty() }?.let { Condition.Or(it) } ?: Condition.Always
                                                }.let { Condition.And(it) }
                                            }
                                        },
                                        condition.debounce(500)()
                                    )
                                ), sort.debounce(500)()
                            )
                        )
                    },
                    link = {
                        val id = UrlProperties.encodeToString(mc.serializer._id().serializer, it._id)
                        return@view { DetailAdminScreen(collectionName, id) }
                    }
                )
            }
        }
    }
}
