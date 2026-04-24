@file:OptIn(ExperimentalSerializationApi::class)

package com.lightningkite.kiteui.forms

import com.lightningkite.kiteui.models.Icon
import com.lightningkite.kiteui.models.ListSemantic
import com.lightningkite.kiteui.models.SubtextSemantic
import com.lightningkite.kiteui.models.px
import com.lightningkite.kiteui.models.rem
import com.lightningkite.kiteui.views.ViewWriter
import com.lightningkite.kiteui.views.card
import com.lightningkite.kiteui.views.centered
import com.lightningkite.kiteui.views.direct.*
import com.lightningkite.kiteui.views.expanding
import com.lightningkite.kiteui.views.forEachUpdating
import com.lightningkite.kiteui.views.l2.icon
import com.lightningkite.reactive.context.invoke
import com.lightningkite.reactive.context.reactive
import com.lightningkite.reactive.core.Constant
import com.lightningkite.reactive.core.MutableReactive
import com.lightningkite.reactive.core.Reactive
import com.lightningkite.reactive.core.Signal
import com.lightningkite.reactive.extensions.flatten
import com.lightningkite.reactive.lensing.lens
import com.lightningkite.reactive.lensing.lensByElementAssumingSetNeverManipulates
import com.lightningkite.services.database.default
import com.lightningkite.services.database.listElement
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.StructureKind

/**
 * Renderer for Set<T> types.
 *
 * Shows items vertically with add/remove buttons.
 * Each item is rendered using the element type's renderer.
 * Internally converts Set to List for UI rendering since Sets lack stable ordering.
 *
 * by Claude
 */
public object SetRenderer : Renderer<Set<Any?>> {
    override val name: String = "Set"  // by Claude

    override fun priority(context: RenderContext<Set<Any?>>, module: FormModule): Float {
        val descriptor = context.serializer.descriptor
        // Match sets specifically - they have StructureKind.LIST but serialName contains "Set"
        return if (descriptor.kind == StructureKind.LIST && descriptor.serialName.contains("Set")) 0.85f else -1f
    }

    @Suppress("UNCHECKED_CAST")
    override fun form(context: RenderContext<Set<Any?>>, value: MutableReactive<Set<Any?>>, module: FormModule): ViewWriter.() -> Unit = {
        val elementSerializer = context.serializer.listElement() as KSerializer<Any?>
        val elementContext = RenderContext(elementSerializer)

        // Get all compatible renderers for element type - by Claude
        val elementRenderers = module.selectAll(elementContext)
        val selectedRenderer = Signal(module.selectWithOverride(elementContext))

        // Lens to convert Set<T> to List<T> for UI
        val listValue = value.lens(
            get = { it.toList() },
            set = { it.toSet() }
        )

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

            // Set items using lensByElement for proper reactive tracking
            themed(ListSemantic).col {
                forEachUpdating(listValue.lensByElementAssumingSetNeverManipulates()) { itemLens ->
                    card.row {
                        // Item content - uses selected renderer for all items - by Claude
                        expanding.frame {
                            reactive {
                                clearChildren()
                                selectedRenderer().form(elementContext, itemLens.flatten(), module)()
                            }
                        }
                        // Remove button - uses item equality for removal
                        button {
                            icon(Icon.close.copy(width = 1.rem, height = 1.rem), "Remove")
                            onClick {
                                val item = itemLens()
                                value set (value() - item)
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
    override fun view(context: RenderContext<Set<Any?>>, value: Reactive<Set<Any?>>, module: FormModule): ViewWriter.() -> Unit = {
        val elementSerializer = context.serializer.listElement() as KSerializer<Any?>
        val elementContext = RenderContext(elementSerializer)

        // Get all compatible renderers for element type - by Claude
        val elementRenderers = module.selectAll(elementContext)
        val selectedRenderer = Signal(module.selectWithOverride(elementContext))

        // Lens to convert Set<T> to List<T> for UI
        val listValue = value.lens { it.toList() }

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
                forEachUpdating(listValue) { itemValue ->
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

    override fun cellView(context: RenderContext<Set<Any?>>, value: Reactive<Set<Any?>>, module: FormModule): ViewWriter.() -> Unit = {
        // Show count in cell view
        text { ::content { "${value().size} items" } }
    }

    // cellForm uses default dialog behavior

    override fun columnWidth(context: RenderContext<Set<Any?>>, module: FormModule): Double = 10.0

    // by Claude - Sets use section header instead of field() wrapper to avoid nesting
    override fun labeledForm(
        context: RenderContext<Set<Any?>>,
        value: MutableReactive<Set<Any?>>,
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
        context: RenderContext<Set<Any?>>,
        value: Reactive<Set<Any?>>,
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


public fun FormModule.registerSet() {
    register(Selector(kind = StructureKind.LIST), SetRenderer)
}
