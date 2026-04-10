package com.lightningkite.kiteui.forms

import com.lightningkite.kiteui.models.DragData
import com.lightningkite.kiteui.models.DragEvent
import com.lightningkite.kiteui.models.Icon
import com.lightningkite.kiteui.models.px
import com.lightningkite.kiteui.models.rem
import com.lightningkite.kiteui.views.DropTargetDelegate
import com.lightningkite.kiteui.views.ElementWriter
import com.lightningkite.kiteui.views.card
import com.lightningkite.kiteui.views.centered
import com.lightningkite.kiteui.views.direct.button
import com.lightningkite.kiteui.views.direct.col
import com.lightningkite.kiteui.views.direct.frame
import com.lightningkite.kiteui.views.direct.h4
import com.lightningkite.kiteui.views.direct.onClick
import com.lightningkite.kiteui.views.direct.row
import com.lightningkite.kiteui.views.direct.scrollingHorizontally
import com.lightningkite.kiteui.views.direct.text
import com.lightningkite.kiteui.views.direct.textPopover
import com.lightningkite.kiteui.views.forEachUpdating
import com.lightningkite.kiteui.views.direct.icon
import com.lightningkite.reactive.context.invoke
import com.lightningkite.reactive.context.reactive
import com.lightningkite.reactive.core.MutableReactive
import com.lightningkite.reactive.core.Reactive
import com.lightningkite.reactive.core.Signal
import com.lightningkite.reactive.extensions.flatten
import com.lightningkite.reactive.lensing.lensByElementAssumingSetNeverManipulates
import com.lightningkite.services.database.SortPartSerializer
import com.lightningkite.services.database.default
import com.lightningkite.services.database.listElement
import kotlinx.coroutines.launch
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.StructureKind
import kotlin.collections.plus

/**
 * Renderer for List<T> types that displays items horizontally.
 *
 * Best suited for lists of compact items like SortPart, enums, or primitives.
 * Uses a wrapping row layout with inline add/remove buttons.
 *
 * Higher priority than vertical ListRenderer when:
 * - List element type is SortPart (sort configuration)
 * - Field has @HorizontalLayout annotation
 *
 * by Claude
 */
object HorizontalListRenderer : Renderer<List<Any?>> {
    override val name: String = "Horizontal List"  // by Claude

    override fun priority(context: RenderContext<List<Any?>>, module: FormModule): Float {
        if (context.serializer.descriptor.kind != StructureKind.LIST) return -1f

        // Higher priority for explicit annotation - by Claude
        if (context.hasAnnotation(Annotations.HorizontalLayout)) return 1.5f

        // Higher priority for SortPart lists - by Claude
        val elementSerializer = context.serializer.listElement()
        if (elementSerializer is SortPartSerializer<*>) return 1.2f

        // Otherwise lower priority than vertical list - by Claude
        return 0.5f
    }

    @Suppress("UNCHECKED_CAST")
    override fun form(context: RenderContext<List<Any?>>, value: MutableReactive<List<Any?>>, module: FormModule): ElementWriter.CanAddTheme.() -> Unit = {
        val elementSerializer = context.serializer.listElement() as KSerializer<Any?>
        val elementContext = RenderContext(elementSerializer)

        // Get all compatible renderers for element type - by Claude
        val elementRenderers = module.selectAll(elementContext)
        val selectedRenderer = Signal(module.selectWithOverride(elementContext))

        col {
            gap = 0.px
            // Element renderer switcher (if enabled and multiple options) - by Claude
            if (module.enableRendererSwitching) {
                subrendererSelector(selectedRenderer, elementRenderers)
            }

            // Show empty text
            text {
                ::shown { value().isEmpty() }
                content = "Empty"
            }

            // Horizontal wrapping row for items - by Claude
            scrollingHorizontally.row {
                row {
                    forEachUpdating(value.lensByElementAssumingSetNeverManipulates()) { itemLens ->
                        card.row {
                            val mimeType = "x-lskui/${elementSerializer.descriptor.serialName.lowercase()}"
                            centered.text("::")
                            ::dragData {
                                val index = itemLens().index()
                                DragData(label = "Item ${index + 1}", mimeType = mimeType, data = index.toString())
                            }
                            dropTargetDelegate = object: DropTargetDelegate {
                                override fun drop(event: DragEvent): Boolean {
                                    return event.data[mimeType]?.let { colStr ->
                                        val otherOldIndex = colStr.toInt()
                                        launch {
                                            val myOldIndex = itemLens().index()
                                            value set value()
                                                .let {
                                                    val t = it.toMutableList()
                                                    val otherCol = t.removeAt(otherOldIndex)
                                                    if (myOldIndex < otherOldIndex) t.add(myOldIndex, otherCol)
                                                    else t.add(myOldIndex, otherCol)
                                                    println("Rewrote $it to $t")
                                                    t
                                                }
                                        }
                                        true
                                    } ?: false
                                }
                            }
                            // Item content - uses selected renderer for all items - by Claude
                            frame {
                                reactive {
                                    clearChildren()
                                    selectedRenderer().cellForm(elementContext, itemLens.flatten(), module)()
                                }
                            }
                            // Remove button - by Claude
                            button {
                                centered.icon(Icon.Companion.close.copy(width = 1.rem, height = 1.rem), "Remove")
                                onClick {
                                    val i = itemLens().index()
                                    value set value().filterIndexed { index, _ -> index != i }
                                }
                            }
                        }
                    }
                }

                // Add button inline - by Claude
                button {
                    centered.icon(Icon.Companion.add.copy(width = 1.rem, height = 1.rem), "Add")
                    onClick {
                        value set (value() + elementSerializer.default())
                    }
                }
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    override fun view(context: RenderContext<List<Any?>>, value: Reactive<List<Any?>>, module: FormModule): ElementWriter.CanAddTheme.() -> Unit = {
        val elementSerializer = context.serializer.listElement() as KSerializer<Any?>
        val elementContext = RenderContext(elementSerializer)

        // Get all compatible renderers for element type - by Claude
        val elementRenderers = module.selectAll(elementContext)
        val selectedRenderer = Signal(module.selectWithOverride(elementContext))

        col {
            gap = 0.px
            // Element renderer switcher (if enabled and multiple options) - by Claude
            if (module.enableRendererSwitching) {
                subrendererSelector(selectedRenderer, elementRenderers)
            }

            text {
                ::shown { value().isEmpty() }
                content = "Empty"
            }

            // Horizontal wrapping row for items - by Claude
            scrollingHorizontally.row {
                forEachUpdating(value) { itemValue ->
                    card.frame {
                        reactive {
                            clearChildren()
                            selectedRenderer().cellView(elementContext, itemValue, module)()
                        }
                    }
                }
            }
        }
    }

    override fun cellView(context: RenderContext<List<Any?>>, value: Reactive<List<Any?>>, module: FormModule): ElementWriter.CanAddTheme.() -> Unit = {
        // Show count in cell view - by Claude
        text { ::content { "${value().size} items" } }
    }

    override fun columnWidth(context: RenderContext<List<Any?>>, module: FormModule) = 15.0

    // by Claude - Horizontal lists use section header instead of field() wrapper
    override fun labeledForm(
        context: RenderContext<List<Any?>>,
        value: MutableReactive<List<Any?>>,
        module: FormModule,
        label: String,
        description: String?
    ): ElementWriter.CanAddTheme.() -> Unit = {
        col {
            row {
                h4(label)
                description?.let { desc ->
                    centered.textPopover(desc).icon(Icon.Companion.info.copy(width = 1.rem, height = 1.rem), "Info")
                }
            }
            form(context, value, module)()
        }
    }

    override fun labeledView(
        context: RenderContext<List<Any?>>,
        value: Reactive<List<Any?>>,
        module: FormModule,
        label: String,
        description: String?
    ): ElementWriter.CanAddTheme.() -> Unit = {
        col {
            row {
                h4(label)
                description?.let { desc ->
                    centered.textPopover(desc).icon(Icon.Companion.info.copy(width = 1.rem, height = 1.rem), "Info")
                }
            }
            view(context, value, module)()
        }
    }
}