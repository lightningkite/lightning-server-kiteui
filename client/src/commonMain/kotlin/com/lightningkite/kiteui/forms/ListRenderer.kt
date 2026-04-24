@file:OptIn(ExperimentalSerializationApi::class)

package com.lightningkite.kiteui.forms

import com.lightningkite.kiteui.models.DragData
import com.lightningkite.kiteui.models.DragEvent
import com.lightningkite.kiteui.models.Icon
import com.lightningkite.kiteui.models.ListSemantic
import com.lightningkite.kiteui.models.px
import com.lightningkite.kiteui.models.rem
import com.lightningkite.kiteui.navigation.DefaultJson
import com.lightningkite.kiteui.views.DropTargetDelegate
import com.lightningkite.kiteui.views.ViewWriter
import com.lightningkite.kiteui.views.card
import com.lightningkite.kiteui.views.centered
import com.lightningkite.kiteui.views.direct.*
import com.lightningkite.kiteui.views.expanding
import com.lightningkite.kiteui.views.forEachUpdating
import com.lightningkite.kiteui.views.l2.DragDropReordering
import com.lightningkite.kiteui.views.l2.icon
import com.lightningkite.reactive.context.invoke
import com.lightningkite.reactive.context.reactive
import com.lightningkite.reactive.core.MutableReactive
import com.lightningkite.reactive.core.Reactive
import com.lightningkite.reactive.core.Signal
import com.lightningkite.reactive.extensions.flatten
import com.lightningkite.reactive.lensing.lensByElementAssumingSetNeverManipulates
import com.lightningkite.services.database.DataClassPath
import com.lightningkite.services.database.DataClassPathSerializer
import com.lightningkite.services.database.default
import com.lightningkite.services.database.listElement
import kotlinx.coroutines.launch
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.StructureKind

/**
 * Renderer for List<T> types.
 *
 * Shows items vertically with add/remove buttons.
 * Each item is rendered using the element type's renderer.
 *
 * by Claude
 */
public object ListRenderer : Renderer<List<Any?>> {
    override val name: String = "List"  // by Claude

    override fun priority(context: RenderContext<List<Any?>>, module: FormModule): Float {
        return if (context.serializer.descriptor.kind == StructureKind.LIST) 0.8f else -1f
    }

    @Suppress("UNCHECKED_CAST")
    override fun form(
        context: RenderContext<List<Any?>>,
        value: MutableReactive<List<Any?>>,
        module: FormModule
    ): ViewWriter.() -> Unit = {
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

            // List items using lensByElement for proper reactive tracking
            themed(ListSemantic).col {
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
                                                t
                                            }
                                    }
                                    true
                                } ?: false
                            }
                        }
                        // Item content - uses selected renderer for all items - by Claude
                        expanding.frame {
                            reactive {
                                clearChildren()
                                selectedRenderer().form(elementContext, itemLens.flatten(), module)()
                            }
                        }
                        // Remove button
                        button {
                            centered.icon(Icon.close.copy(width = 1.rem, height = 1.rem), "Remove")
                            onClick {
                                val i = itemLens().index()
                                value set value().filterIndexed { index, _ -> index != i }
                            }
                        }
                    }
                }
            }

            // Add button
            button {
                centered.icon(Icon.add.copy(width = 1.rem, height = 1.rem), "Add")
                onClick {
                    value set (value() + elementSerializer.default())
                }
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    override fun view(
        context: RenderContext<List<Any?>>,
        value: Reactive<List<Any?>>,
        module: FormModule
    ): ViewWriter.() -> Unit = {
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
            themed(ListSemantic).col {
                forEachUpdating(value) { itemValue ->
                    card.frame {
                        reactive {
                            clearChildren()
                            selectedRenderer().view(elementContext, itemValue, module)()
                        }
                    }
                }
            }
        }
    }

    override fun cellView(
        context: RenderContext<List<Any?>>,
        value: Reactive<List<Any?>>,
        module: FormModule
    ): ViewWriter.() -> Unit = {
        // Show count in cell view
        text { ::content { "${value().size} items" } }
    }

    // cellForm uses default dialog behavior

    override fun columnWidth(context: RenderContext<List<Any?>>, module: FormModule): Double = 10.0

    // by Claude - Lists use section header instead of field() wrapper to avoid nesting
    override fun labeledForm(
        context: RenderContext<List<Any?>>,
        value: MutableReactive<List<Any?>>,
        module: FormModule,
        label: String,
        description: String?
    ): ViewWriter.() -> Unit = {
        col {
            row {
                h4(label)
                description?.let { desc ->
                    centered.textPopover(desc).icon(Icon.info.copy(width = 1.rem, height = 1.rem), "Info")
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
    ): ViewWriter.() -> Unit = {
        col {
            row {
                h4(label)
                description?.let { desc ->
                    centered.textPopover(desc).icon(Icon.info.copy(width = 1.rem, height = 1.rem), "Info")
                }
            }
            view(context, value, module)()
        }
    }
}

public fun FormModule.registerCollections() {
    register(Selector(kind = StructureKind.LIST), ListRenderer)
    register(Selector(kind = StructureKind.LIST), HorizontalListRenderer)
}

