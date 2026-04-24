@file:OptIn(ExperimentalSerializationApi::class)

package com.lightningkite.kiteui.forms

import com.lightningkite.kiteui.models.*
import com.lightningkite.kiteui.views.*
import com.lightningkite.kiteui.views.direct.*
import com.lightningkite.kiteui.views.l2.icon
import com.lightningkite.reactive.context.invoke
import com.lightningkite.reactive.context.reactive
import com.lightningkite.reactive.core.*
import com.lightningkite.services.database.*
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.StructureKind

/**
 * Renderer for Map<K, V> types.
 *
 * Shows key-value pairs with add/remove buttons.
 * Each entry is rendered in a row with key and value side by side.
 *
 * by Claude
 */
public object MapRenderer : Renderer<Map<Any?, Any?>> {
    override val name: String = "Key-Value Pairs"  // by Claude

    override fun priority(context: RenderContext<Map<Any?, Any?>>, module: FormModule): Float {
        return if (context.serializer.descriptor.kind == StructureKind.MAP) 0.8f else -1f
    }

    @Suppress("UNCHECKED_CAST")
    override fun form(
        context: RenderContext<Map<Any?, Any?>>,
        value: MutableReactive<Map<Any?, Any?>>,
        module: FormModule,
    ): ViewWriter.() -> Unit = {
        // by Claude
        val keySerializer = context.serializer.mapKeyElement() as KSerializer<Any?>
        val valueSerializer = context.serializer.mapValueElement() as KSerializer<Any?>
        val keyContext = RenderContext(keySerializer)
        val valueContext = RenderContext(valueSerializer)

        // Get all compatible renderers for key and value types - by Claude
        val keyRenderers = module.selectAll(keyContext)
        val valueRenderers = module.selectAll(valueContext)
        val selectedKeyRenderer = Signal(module.selectWithOverride(keyContext))
        val selectedValueRenderer = Signal(module.selectWithOverride(valueContext))

        col {
            gap = 0.px
            // Renderer switchers (if enabled and multiple options) - by Claude
            if (module.enableRendererSwitching) {
                row {
                    expanding.subrendererSelector(selectedKeyRenderer, keyRenderers)
                    expanding.subrendererSelector(selectedValueRenderer, valueRenderers)
                }
            }

            // Show empty text
            text {
                ::shown { value.invoke().isEmpty() }
                content = "Empty"
            }

            // Map entries - using reactive for dynamic rendering
            themed(ListSemantic).col {
                reactive {
                    clearChildren()
                    for ((entryKey, entryValue) in value.invoke()) {
                        // Lens for this entry's key
                        val keyLens = value.lens(
                            get = { entryKey },
                            modify = { original, newKey ->
                                if (newKey != entryKey) {
                                    val result = original.toMutableMap()
                                    result.remove(entryKey)
                                    result[newKey] = original[entryKey] ?: entryValue
                                    result
                                } else original
                            }
                        )

                        // Lens for this entry's value
                        val valueLens = value.lens(
                            get = { entryValue },
                            modify = { original, newValue ->
                                val result = original.toMutableMap()
                                result[entryKey] = newValue
                                result
                            }
                        )

                        card.row {
                            // Key field - uses selected key renderer - by Claude
                            expanding.col {
                                subtext("Key")
                                reactive {
                                    clearChildren()
                                    selectedKeyRenderer().form(keyContext, keyLens, module)()
                                }
                            }

                            // Value field - uses selected value renderer - by Claude
                            expanding.col {
                                subtext("Value")
                                reactive {
                                    clearChildren()
                                    selectedValueRenderer().form(valueContext, valueLens, module)()
                                }
                            }

                            // Remove button - capture entryKey for closure
                            val keyToRemove = entryKey
                            centered.button {
                                icon(Icon.close.copy(width = 1.rem, height = 1.rem), "Remove")
                                onClick {
                                    val result = value.invoke().toMutableMap()
                                    result.remove(keyToRemove)
                                    value.set(result)
                                }
                            }
                        }
                    }
                }
            }

            // Add button
            button {
                centered.icon(Icon.add.copy(width = 1.rem, height = 1.rem), "Add")
                onClick {
                    val defaultKey = keySerializer.default()
                    val defaultValue = valueSerializer.default()
                    val newMap = value.invoke().toMutableMap()
                    newMap[defaultKey] = defaultValue
                    value.set(newMap)
                }
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    override fun view(
        context: RenderContext<Map<Any?, Any?>>,
        value: Reactive<Map<Any?, Any?>>,
        module: FormModule,
    ): ViewWriter.() -> Unit = {
        val keySerializer = context.serializer.mapKeyElement() as KSerializer<Any?>
        val valueSerializer = context.serializer.mapValueElement() as KSerializer<Any?>
        val keyContext = RenderContext(keySerializer)
        val valueContext = RenderContext(valueSerializer)

        // Get all compatible renderers for key and value types - by Claude
        val keyRenderers = module.selectAll(keyContext)
        val valueRenderers = module.selectAll(valueContext)
        val selectedKeyRenderer = Signal(module.selectWithOverride(keyContext))
        val selectedValueRenderer = Signal(module.selectWithOverride(valueContext))

        col {
            gap = 0.px
            // Renderer switchers (if enabled and multiple options) - by Claude
            if (module.enableRendererSwitching) {
                row {
                    expanding.subrendererSelector(selectedKeyRenderer, keyRenderers)
                    expanding.subrendererSelector(selectedValueRenderer, valueRenderers)
                }
            }

            text {
                ::shown { value().isEmpty() }
                content = "Empty"
            }

            themed(ListSemantic).col {
                reactive {
                    clearChildren()
                    for ((key, entryValue) in value()) {
                        card.row {
                            // Uses selected renderers - by Claude
                            expanding.col {
                                subtext("Key")
                                reactive {
                                    clearChildren()
                                    selectedKeyRenderer().view(keyContext, Constant(key), module)()
                                }
                            }
                            expanding.col {
                                subtext("Value")
                                reactive {
                                    clearChildren()
                                    selectedValueRenderer().view(valueContext, Constant(entryValue), module)()
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    override fun cellView(
        context: RenderContext<Map<Any?, Any?>>,
        value: Reactive<Map<Any?, Any?>>,
        module: FormModule,
    ): ViewWriter.() -> Unit = {
        // Show count in cell view
        text { ::content { "${value().size} entries" } }
    }

    override fun columnWidth(context: RenderContext<Map<Any?, Any?>>, module: FormModule): Double = 10.0

    // by Claude - Maps use section header instead of field() wrapper to avoid nesting
    override fun labeledForm(
        context: RenderContext<Map<Any?, Any?>>,
        value: MutableReactive<Map<Any?, Any?>>,
        module: FormModule,
        label: String,
        description: String?,
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
        context: RenderContext<Map<Any?, Any?>>,
        value: Reactive<Map<Any?, Any?>>,
        module: FormModule,
        label: String,
        description: String?,
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

public fun FormModule.registerMap() {
    register(Selector(kind = StructureKind.MAP), MapRenderer)
}
