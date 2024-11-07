package com.lightningkite.kiteui.forms

import com.lightningkite.*
import com.lightningkite.kiteui.ExternalServices
import com.lightningkite.kiteui.FileReference
import com.lightningkite.kiteui.load
import com.lightningkite.kiteui.models.*
import com.lightningkite.kiteui.navigation.Screen
import com.lightningkite.kiteui.navigation.UrlProperties
import com.lightningkite.kiteui.navigation.encodeToString
import com.lightningkite.kiteui.reactive.*
import com.lightningkite.kiteui.views.direct.*
import com.lightningkite.kiteui.views.*
import com.lightningkite.kiteui.views.l2.icon
import com.lightningkite.lightningdb.*
import com.lightningkite.lightningserver.db.ModelCache
import com.lightningkite.lightningserver.files.ServerFile
import com.lightningkite.serialization.*
import kotlinx.serialization.ContextualSerializer
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer

object ForeignKeyRenderer : FormRenderer.Generator, ViewRenderer.Generator {
    override val name: String = "Foreign Key"
    override val annotation: String? get() = "com.lightningkite.lightningdb.References"
    override val basePriority: Float
        get() = 2f

    override fun size(module: FormModule, selector: FormSelector<*>): FormSize = FormSize(16.0, 1.0)
    override fun matches(module: FormModule, selector: FormSelector<*>): Boolean {
        val anno = selector.annotations.find {
            it.fqn == "com.lightningkite.lightningdb.References" ||
                    it.fqn == "com.lightningkite.lightningdb.MultipleReferences"
        }?.values ?: return false
        val typeName = anno.get("references")?.let { it as? SerializableAnnotationValue.ClassValue }?.fqn ?: return false
        val typeInfo = module.typeInfo(typeName) as? FormTypeInfo<HasId<Comparable<Comparable<*>>>, Comparable<Comparable<*>>> ?: return false
        return true
    }

    @Suppress("UNCHECKED_CAST")
    override fun <T> form(module: FormModule, selector: FormSelector<T>): FormRenderer<T> {
        val anno = selector.annotations.find {
            it.fqn == "com.lightningkite.lightningdb.References" ||
                    it.fqn == "com.lightningkite.lightningdb.MultipleReferences"
        }!!.values
        val typeName = anno.get("references")!!.let { it as SerializableAnnotationValue.ClassValue }.fqn
        val typeInfo = module.typeInfo(typeName)!! as FormTypeInfo<HasId<Comparable<Comparable<*>>>, Comparable<Comparable<*>>>
        return FormRenderer(module, this, selector as FormSelector<Comparable<Comparable<*>>?>) { field, writable ->
            fieldTheme - row {
                spacing = 0.px
                expanding - menuButton {
                    requireClick = true
                    gravity(Align.Start, Align.Center) - text {
                        reactiveSuspending {
                            content = writable()?.let { typeInfo.renderToString(it) } ?: "None"
                        }
                    }
                    opensMenu {
                        if(selector.serializer.descriptor.isNullable) {
                            load { writable set null }
                        }
                        preferredDirection = PopoverPreferredDirection.belowLeft
                        sizeConstraints(width = 25.rem, height = 25.rem) - col {
                            val textSearch = Property("")
                            val condition = Property<Condition<HasId<Comparable<Comparable<*>>>>>(Condition.Always)
                            val sort = Property<List<SortPart<HasId<Comparable<Comparable<*>>>>>>(listOf())
                            row {
                                expanding - fieldTheme - textInput {
                                    content bind textSearch
                                }
                                menuButton {
                                    dynamicTheme { if (condition() != Condition.Always) SelectedSemantic else null }
                                    icon(Icon.filterList, "Filter")
                                    requireClick = true
                                    opensMenu {
                                        form(module, Condition.serializer(typeInfo.cache.serializer), condition)
                                    }
                                }
                                menuButton {
                                    dynamicTheme { if (sort().isNotEmpty()) SelectedSemantic else null }
                                    icon(Icon.sort, "Sort")
                                    requireClick = true
                                    opensMenu {
                                        form(module, ListSerializer(SortPartSerializer(typeInfo.cache.serializer)), sort)
                                    }
                                }
                            }
                            val hasTextIndex = typeInfo.cache.serializer.serializableAnnotations.any { it.fqn.endsWith("TextIndex") }
                            val columns: ImmediateWritable<List<DataClassPath<HasId<Comparable<Comparable<*>>>, *>>> = Property(run {
                                typeInfo.cache.serializer.serializableProperties!!.sortedBy {
                                    it.importance
                                }.take(5).map {
                                    DataClassPathAccess(DataClassPathSelf(typeInfo.cache.serializer), it)
                                }
                            })
                            expanding - TableRenderer.view<HasId<Comparable<Comparable<*>>>>(
                                formModule = module,
                                writer = this@col,
                                innerSer = typeInfo.cache.cache(null).serializer,
                                readable = shared {
                                    typeInfo.cache.cache(null).watch(
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
                                link = null,
                                action = {
                                    writable.set(it._id)
                                    closePopovers()
                                }
                            )
                        }
                    }
                }
                link {
                    icon(Icon.externalLink.copy(width = 1.rem, height = 1.rem), "Open")
                    ::to label@{
                        val id = writable() ?: return@label null
                        return@label typeInfo.screen(id)
                    }
                    newTab = true
                }
            }
        } as FormRenderer<T>
    }

    @Suppress("UNCHECKED_CAST")
    override fun <T> view(module: FormModule, selector: FormSelector<T>): ViewRenderer<T> {
        val anno = selector.annotations.find {
            it.fqn == "com.lightningkite.lightningdb.References" ||
                    it.fqn == "com.lightningkite.lightningdb.MultipleReferences"
        }!!.values
        val typeName = anno.get("references")!!.let { it as SerializableAnnotationValue.ClassValue }.fqn
        val typeInfo = module.typeInfo(typeName)!! as FormTypeInfo<HasId<Comparable<Comparable<*>>>, Comparable<Comparable<*>>>
        return ViewRenderer(module, this, selector as FormSelector<Comparable<Comparable<*>>?>) { field, readable ->
            link {
                ::to label@{
                    val id = readable() ?: return@label null
                    return@label typeInfo.screen(id)
                }
                text {
                    reactiveSuspending {
                        content = readable()?.let { typeInfo.renderToString(it) } ?: "None"
                    }
                }
            }
        } as ViewRenderer<T>
    }
}