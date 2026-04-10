@file:OptIn(ExperimentalSerializationApi::class)

package com.lightningkite.kiteui.forms

import com.lightningkite.IsRawString
import com.lightningkite.TrimmedString
import com.lightningkite.kiteui.models.*
import com.lightningkite.kiteui.reactive.Action
import com.lightningkite.kiteui.views.*
import com.lightningkite.kiteui.views.direct.*
import com.lightningkite.kiteui.views.l2.children
import com.lightningkite.kiteui.views.direct.icon
import com.lightningkite.reactive.context.invoke
import com.lightningkite.reactive.context.reactiveSuspending
import com.lightningkite.reactive.core.MutableReactive
import com.lightningkite.reactive.core.Reactive
import com.lightningkite.reactive.core.Signal
import com.lightningkite.reactive.core.remember
import com.lightningkite.services.database.*
import kotlinx.serialization.ExperimentalSerializationApi

/**
 * Renderer for foreign key fields annotated with @References.
 *
 * Form mode shows:
 * - Current selection (or "None")
 * - Dropdown menu with search and selection list
 * - Link to open the referenced item
 *
 * View mode shows:
 * - Link to the referenced item with its display name
 *
 * Requires FormModule.typeInfo to be configured to resolve referenced types.
 *
 * by Claude
 */
object ForeignKeyRenderer : Renderer<Any?> {
    override val name: String = "Reference"  // by Claude

    private const val REFERENCES_FQN = "com.lightningkite.services.data.References"
    private const val MULTIPLE_REFERENCES_FQN = "com.lightningkite.services.data.MultipleReferences"

    override fun priority(context: RenderContext<Any?>, module: FormModule): Float {
        // Only match if we have a @References annotation and typeInfo is available
        val anno = context.fieldAnnotations.find {
            it.fqn == REFERENCES_FQN || it.fqn == MULTIPLE_REFERENCES_FQN
        } ?: return -1f

        val typeName = anno.values["references"]
            ?.let { it as? SerializableAnnotationValue.ClassValue }
            ?.fqn ?: return -1f

        // Check that we can resolve this type
        if (module.typeInfo(typeName) == null) return -1f

        return 2f  // Higher priority than default object renderer
    }

    @Suppress("UNCHECKED_CAST")
    override fun form(context: RenderContext<Any?>, value: MutableReactive<Any?>, module: FormModule): ElementWriter.CanAddTheme.() -> Unit {
        val anno = context.fieldAnnotations.find {
            it.fqn == REFERENCES_FQN || it.fqn == MULTIPLE_REFERENCES_FQN
        }!!

        val typeName = anno.values["references"]!!
            .let { it as SerializableAnnotationValue.ClassValue }.fqn

        val typeInfo = module.typeInfo(typeName)!!
            as TypeInfo<HasId<Comparable<Comparable<*>>>, Comparable<Comparable<*>>>

        return {
            fieldTheme.row {
                gap = 0.px
                expanding.menuButton {
                    requireClick = true
                    align(Align.Start, Align.Center).text {
                        reactiveSuspending {
                            content = (value() as? Comparable<Comparable<*>>)
                                ?.let { typeInfo.renderToString(it) }
                                ?: "None"
                        }
                    }

                    preferredDirection = PopoverPreferredDirection.belowLeft

                    opensMenu {
                        // Allow null selection if serializer is nullable
                        if (context.serializer.descriptor.isNullable) {
                            button {
                                text("Clear selection")
                                onClick {
                                    value set null
                                    closePopovers()
                                }
                            }
                        }

                        sizeConstraints(width = 25.rem, height = 25.rem).col {
                            val textSearch = Signal("")
                            val hasTextIndex = typeInfo.serializer.serializableAnnotations
                                .any { it.fqn.endsWith("TextIndex") }

                            // Build query based on text search
                            val items = remember {
                                val cache = typeInfo.cache()
                                val searchText = textSearch.debounce(500)()

                                if (searchText.isBlank()) {
                                    cache.watch(Query(Condition.Always, listOf()))()
                                } else {
                                    val condition = if (hasTextIndex) {
                                        Condition.FullTextSearch<HasId<Comparable<Comparable<*>>>>(searchText)
                                    } else {
                                        // Search in string fields
                                        val props = typeInfo.serializer.serializableProperties
                                            ?: return@remember cache.watch(Query(Condition.Always, listOf()))()

                                        val stringConditions = props.mapNotNull { prop ->
                                            val serialName = prop.serializer.let {
                                                it.nullElement() ?: it
                                            }.descriptor.serialName.substringBefore('/')

                                            when {
                                                serialName == "kotlin.String" -> {
                                                    val path = DataClassPathAccess(
                                                        DataClassPathSelf(typeInfo.serializer),
                                                        prop as SerializableProperty<HasId<Comparable<Comparable<*>>>, Any?>
                                                    )
                                                    path.mapCondition(
                                                        Condition.StringContains(searchText, true) as Condition<Any?>
                                                    )
                                                }
                                                serialName in IsRawString.serialNames -> {
                                                    val path = DataClassPathAccess(
                                                        DataClassPathSelf(typeInfo.serializer),
                                                        prop as SerializableProperty<HasId<Comparable<Comparable<*>>>, Any?>
                                                    )
                                                    path.mapCondition(
                                                        Condition.RawStringContains<TrimmedString>(searchText, true)
                                                            as Condition<Any?>
                                                    )
                                                }
                                                else -> null
                                            }
                                        }

                                        if (stringConditions.isNotEmpty()) {
                                            Condition.Or(stringConditions)
                                        } else {
                                            Condition.Always
                                        }
                                    }

                                    cache.watch(Query(condition, listOf()))()
                                }
                            }

                            // Search input
                            fieldTheme.textInput {
                                hint = "Search..."
                                content bind textSearch
                            }

                            // Results list
                            expanding.recyclerView {
                                children(items, id = { it._id }) { itemReactive ->
                                    card.button {
                                        text {
                                            content = "..."
                                            reactiveSuspending {
                                                content = typeInfo.renderToString(itemReactive()._id)
                                            }
                                        }
                                        action = Action("Select", Icon.done) {
                                            value set itemReactive()._id
                                            closePopovers()
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Link to open the referenced item
                link {
                    icon(Icon.externalLink.copy(width = 1.rem, height = 1.rem), "Open")
                    ::to label@{
                        val id = value() as? Comparable<Comparable<*>> ?: return@label null
                        typeInfo.page(id)
                    }
                }
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    override fun view(context: RenderContext<Any?>, value: Reactive<Any?>, module: FormModule): ElementWriter.CanAddTheme.() -> Unit {
        val anno = context.fieldAnnotations.find {
            it.fqn == REFERENCES_FQN || it.fqn == MULTIPLE_REFERENCES_FQN
        }!!

        val typeName = anno.values["references"]!!
            .let { it as SerializableAnnotationValue.ClassValue }.fqn

        val typeInfo = module.typeInfo(typeName)!!
            as TypeInfo<HasId<Comparable<Comparable<*>>>, Comparable<Comparable<*>>>

        return {
            link {
                ::to label@{
                    val id = value() as? Comparable<Comparable<*>> ?: return@label null
                    typeInfo.page(id)
                }
                text {
                    reactiveSuspending {
                        content = (value() as? Comparable<Comparable<*>>)
                            ?.let { typeInfo.renderToString(it) }
                            ?: "None"
                    }
                }
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    override fun cellView(context: RenderContext<Any?>, value: Reactive<Any?>, module: FormModule): ElementWriter.CanAddTheme.() -> Unit {
        val anno = context.fieldAnnotations.find {
            it.fqn == REFERENCES_FQN || it.fqn == MULTIPLE_REFERENCES_FQN
        }!!

        val typeName = anno.values["references"]!!
            .let { it as SerializableAnnotationValue.ClassValue }.fqn

        val typeInfo = module.typeInfo(typeName)!!
            as TypeInfo<HasId<Comparable<Comparable<*>>>, Comparable<Comparable<*>>>

        return {
            text {
                reactiveSuspending {
                    content = (value() as? Comparable<Comparable<*>>)
                        ?.let { typeInfo.renderToString(it) }
                        ?: "—"
                }
            }
        }
    }

    override fun columnWidth(context: RenderContext<Any?>, module: FormModule): Double = 16.0
    override fun labeledForm(
        context: RenderContext<Any?>,
        value: MutableReactive<Any?>,
        module: FormModule,
        label: String,
        description: String?
    ): ElementWriter.CanAddTheme.() -> Unit = {
        fieldWithoutBorder(label, description) { form(context, value, module)() }
    }

    override fun labeledView(
        context: RenderContext<Any?>,
        value: Reactive<Any?>,
        module: FormModule,
        label: String,
        description: String?
    ): ElementWriter.CanAddTheme.() -> Unit = {
        fieldWithoutBorder(label, description) { view(context, value, module)() }
    }
}

private const val REFERENCES_FQN = "com.lightningkite.services.data.References"
private const val MULTIPLE_REFERENCES_FQN = "com.lightningkite.services.data.MultipleReferences"

fun FormModule.registerForeignKey() {
    register(Selector(annotation = REFERENCES_FQN), ForeignKeyRenderer)
    register(Selector(annotation = MULTIPLE_REFERENCES_FQN), ForeignKeyRenderer)
}
