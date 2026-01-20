package com.lightningkite.kiteui.forms

import com.lightningkite.IsRawString
import com.lightningkite.TrimmedString
import com.lightningkite.kiteui.load
import com.lightningkite.kiteui.models.*
import com.lightningkite.kiteui.reactive.Action
import com.lightningkite.kiteui.views.*
import com.lightningkite.kiteui.views.direct.*
import com.lightningkite.kiteui.views.l2.children
import com.lightningkite.kiteui.views.l2.icon
import com.lightningkite.services.database.*
import com.lightningkite.reactive.context.invoke
import com.lightningkite.reactive.context.reactiveSuspending
import com.lightningkite.reactive.core.MutableReactiveValue
import com.lightningkite.reactive.core.Signal
import com.lightningkite.reactive.core.remember
import com.lightningkite.reactive.extensions.debounce
import com.lightningkite.services.database.*
import kotlinx.coroutines.delay
import kotlinx.serialization.builtins.ListSerializer
import kotlin.time.Duration.Companion.milliseconds

object ForeignKeyRenderer : FormRenderer.Generator, ViewRenderer.Generator {
    override val name: String = "Foreign Key"
    override val annotation: String? get() = "com.lightningkite.services.data.References"  // by Claude - fixed package name
    override val basePriority: Float
        get() = 2f

    override fun size(module: FormModule, selector: FormSelector<*>): FormSize = FormSize(16.0, 1.0)
    override fun matches(module: FormModule, selector: FormSelector<*>): Boolean {
        val anno = selector.annotations.find {
            it.fqn == "com.lightningkite.services.data.References" ||
                    it.fqn == "com.lightningkite.services.data.MultipleReferences"
        }?.values ?: return false
        val typeName =
            anno.get("references")?.let { it as? SerializableAnnotationValue.ClassValue }?.fqn ?: return false

        @Suppress("UNCHECKED_CAST")
        val typeInfo =
            module.typeInfo(typeName) as? FormTypeInfo<HasId<Comparable<Comparable<*>>>, Comparable<Comparable<*>>>
                ?: return false
        return true
    }

    @Suppress("UNCHECKED_CAST")
    override fun <T> form(module: FormModule, selector: FormSelector<T>): FormRenderer<T> {
        val anno = selector.annotations.find {
            it.fqn == "com.lightningkite.services.data.References" ||
                    it.fqn == "com.lightningkite.services.data.MultipleReferences"
        }!!.values
        val typeName = anno.get("references")!!.let { it as SerializableAnnotationValue.ClassValue }.fqn
        val typeInfo =
            module.typeInfo(typeName)!! as FormTypeInfo<HasId<Comparable<Comparable<*>>>, Comparable<Comparable<*>>>
        return FormRenderer(module, this, selector as FormSelector<Comparable<Comparable<*>>?>) { field, mutable ->
            fieldTheme.row {
                gap = 0.px
                expanding.menuButton {
                    requireClick = true
                    align(Align.Start, Align.Center).text {
                        reactiveSuspending {
                            content = mutable()?.let { typeInfo.renderToString(it) } ?: "None"
                        }
                    }
                    opensMenu {
                        if (selector.serializer.descriptor.isNullable) {
                            load { mutable set null }
                        }
                        preferredDirection = PopoverPreferredDirection.belowLeft
                        val full = Signal(false)
                        sizeConstraints(width = 25.rem, height = 25.rem).col {
                            val textSearch = Signal("")
                            val condition = Signal<Condition<HasId<Comparable<Comparable<*>>>>>(Condition.Always)
                            val sort = Signal<List<SortPart<HasId<Comparable<Comparable<*>>>>>>(listOf())
                            val hasTextIndex =
                                typeInfo.serializer.serializableAnnotations.any { it.fqn.endsWith("TextIndex") }
                            val columns: MutableReactiveValue<List<DataClassPath<HasId<Comparable<Comparable<*>>>, *>>> =
                                Signal(run {
                                    typeInfo.serializer.defaultColumns()
                                })
                            val itemsMeta = remember {
                                typeInfo.cache().watch(
                                    Query(
                                        Condition.And<HasId<Comparable<Comparable<*>>>>(
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
                                                                    if (it.serializer.descriptor.isNullable) DataClassPathNotNull(
                                                                        it as DataClassPath<HasId<Comparable<Comparable<*>>>, Any?>
                                                                    ) else it
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
                                                            }.takeUnless { it.isEmpty() }
                                                                ?.let { Condition.Or(it) } ?: Condition.Always
                                                        }.let { Condition.And(it) }
                                                    }
                                                },
                                                condition.debounce(500)()
                                            )
                                        ), sort.debounce(500)()
                                    )
                                )
                            }
                            val items = remember { itemsMeta()() }
                            row {
                                expanding.fieldTheme.textInput {
                                    content bind textSearch
                                }
                                shownWhen { full() }.menuButton {
                                    dynamicTheme { if (condition() != Condition.Always) SelectedSemantic else null }
                                    icon(Icon.filterList, "Filter")
                                    requireClick = true
                                    opensMenu {
                                        form(module, Condition.serializer(typeInfo.serializer), condition)
                                    }
                                }
                                shownWhen { full() }.menuButton {
                                    dynamicTheme { if (sort().isNotEmpty()) SelectedSemantic else null }
                                    icon(Icon.sort, "Sort")
                                    requireClick = true
                                    opensMenu {
                                        form(module, ListSerializer(SortPartSerializer(typeInfo.serializer)), sort)
                                    }
                                }
                                toggleButton {
                                    checked bind full
                                    icon(Icon.moreVert, "Show More")
                                }
                            }
                            expanding.swapView {
                                swapping(
                                    current = { full() to typeInfo.cache() },
                                    views = { (full, cache) ->
                                        if(full) {
                                            TableRenderer.view<HasId<Comparable<Comparable<*>>>>(
                                                formModule = module,
                                                writer = this@swapping,
                                                innerSer = cache.serializer,
                                                readable = itemsMeta,
                                                linkTo = null,
                                                action = {
                                                    mutable.set(it._id)
                                                    closePopovers()
                                                }
                                            )
                                        } else {
                                            recyclerView {
                                                children(items, id = { it._id }) {
                                                    card.button {
                                                        text {
                                                            content = "..."
                                                            reactiveSuspending {
                                                                content = "..."
                                                                delay(1.milliseconds)
                                                                content = typeInfo.renderToString(it()._id)
                                                            }
                                                        }
                                                        action = Action("Select", Icon.done) {
                                                            mutable.set(it()._id)
                                                            closePopovers()
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
                link {
                    icon(Icon.externalLink.copy(width = 1.rem, height = 1.rem), "Open")
                    ::to label@{
                        val id = mutable() ?: return@label null
                        return@label typeInfo.page(id)
                    }
                }
            }
        } as FormRenderer<T>
    }

    @Suppress("UNCHECKED_CAST")
    override fun <T> view(module: FormModule, selector: FormSelector<T>): ViewRenderer<T> {
        val anno = selector.annotations.find {
            it.fqn == "com.lightningkite.services.data.References" ||
                    it.fqn == "com.lightningkite.services.data.MultipleReferences"
        }!!.values
        val typeName = anno.get("references")!!.let { it as SerializableAnnotationValue.ClassValue }.fqn
        val typeInfo =
            module.typeInfo(typeName)!! as FormTypeInfo<HasId<Comparable<Comparable<*>>>, Comparable<Comparable<*>>>
        return ViewRenderer(module, this, selector as FormSelector<Comparable<Comparable<*>>?>) { field, readable ->
            link {
                ::to label@{
                    val id = readable() ?: return@label null
                    return@label typeInfo.page(id)
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