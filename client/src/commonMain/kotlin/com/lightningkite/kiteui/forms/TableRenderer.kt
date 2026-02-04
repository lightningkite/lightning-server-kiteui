@file:OptIn(ExperimentalSerializationApi::class)

package com.lightningkite.kiteui.forms

import com.lightningkite.kiteui.models.*
import com.lightningkite.kiteui.navigation.Page
import com.lightningkite.kiteui.views.*
import com.lightningkite.kiteui.views.direct.*
import com.lightningkite.kiteui.views.l2.children
import com.lightningkite.kiteui.views.l2.icon
import com.lightningkite.lightningserver.db.LimitReadable
import com.lightningkite.reactive.context.invoke
import com.lightningkite.reactive.context.reactive
import com.lightningkite.reactive.core.*
import com.lightningkite.serialization.lensPath
import com.lightningkite.kiteui.navigation.DefaultJson
import com.lightningkite.lightningserver.db.LimitReactiveList
import com.lightningkite.services.database.*
import kotlinx.coroutines.launch
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.StructureKind

/**
 * Renderer for List<T> as a table with columns.
 *
 * Only matches when inner element is a data class (has serializableProperties).
 * Primitive collections like List<String> will use the default ListRenderer.
 *
 * Features:
 * - Dynamic column selection
 * - Header row with column names
 * - RecyclerView for efficient rendering
 * - LimitReadable support for pagination
 * - Optional row click actions or navigation
 *
 * by Claude
 */
object TableRenderer : Renderer<List<Any?>> {
    override val name: String = "Table"  // by Claude

    override fun priority(context: RenderContext<List<Any?>>, module: FormModule): Float {
        // Only match lists where inner type has serializableProperties
        if (context.serializer.descriptor.kind != StructureKind.LIST) return -1f

        val innerSerializer = context.serializer.listElement() ?: return -1f
        val properties = innerSerializer.serializableProperties ?: return -1f

        // Higher priority than ListRenderer (0.8f) for data classes - tables are better for structured data
        // by Claude
        return 0.9f
    }

    @Suppress("UNCHECKED_CAST")
    override fun form(context: RenderContext<List<Any?>>, value: MutableReactive<List<Any?>>, module: FormModule): ViewWriter.() -> Unit = {
        // For now, table form just displays the view
        // TODO: Implement editable table with inline editing
        text("Table editing not yet implemented")
    }

    @Suppress("UNCHECKED_CAST")
    override fun view(context: RenderContext<List<Any?>>, value: Reactive<List<Any?>>, module: FormModule): ViewWriter.() -> Unit {
        val innerSerializer = context.serializer.listElement() as KSerializer<Any?>
        return {
            // by Claude - wrap value in Constant() to match double-wrapped reactive signature
            sizeConstraints(height = 30.rem).renderTable(
                module = module,
                innerSerializer = innerSerializer,
                items = Constant(value),
                columns = Signal(innerSerializer.defaultColumns() as List<DataClassPath<Any?, *>>),
                linkTo = null,
                action = null
            )
        }
    }

    override fun cellView(context: RenderContext<List<Any?>>, value: Reactive<List<Any?>>, module: FormModule): ViewWriter.() -> Unit = {
        text { ::content { "${value().size} items" } }
    }

    override fun columnWidth(context: RenderContext<List<Any?>>, module: FormModule): Double = 10.0

    // by Claude - Tables use section header instead of field() wrapper to avoid nesting
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
                    textPopover(desc).icon(Icon.info.copy(width = 1.rem, height = 1.rem), "Info")
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
                    textPopover(desc).icon(Icon.info.copy(width = 1.rem, height = 1.rem), "Info")
                }
            }
            view(context, value, module)()
        }
    }
}

data class ColumnInfo<T, V>(
    val path: DataClassPath<T, V>,
    val renderer: Renderer<V>
)

/**
 * Renders a table view of items.
 *
 * This is a reusable function that can be called directly for more control.
 * Supports both `Reactive<List<T>>` and `Reactive<LimitReadable<T>>` for pagination.
 *
 * @param module FormModule for renderer selection
 * @param innerSerializer Serializer for the element type
 * @param items Reactive containing a Reactive list of items. Can be `Reactive<Reactive<List<T>>>`
 *              or `Reactive<LimitReadable<T>>` for automatic pagination.
 * @param columns Mutable list of visible columns
 * @param linkTo Optional function to make rows clickable links
 * @param action Optional function called when rows are clicked
 *
 * by Claude - updated to support double-wrapped reactive pattern for LimitReadable support
 */
@Suppress("UNCHECKED_CAST")
fun <T> ViewWriter.renderTable(
    module: FormModule,
    innerSerializer: KSerializer<T>,
    items: Reactive<Reactive<List<T>>>,
    columns: MutableReactive<List<DataClassPath<T, *>>>, // TODO: We need to change this to store a data class path AND the renderer choice.
    linkTo: ((T) -> () -> Page)? = null,
    action: (suspend (T) -> Unit)? = null
) {
    val properties = innerSerializer.serializableProperties as? Array<SerializableProperty<T, Any?>>
    if (properties == null) {
        text("Not a data class")
        return
    }

    // Cache contexts for performance - by Claude
    val contextCache = HashMap<DataClassPath<T, Any?>, RenderContext<Any?>>()
    // Per-column renderer selections (keyed by column path string) - by Claude
    val columnRendererSelections = HashMap<String, Signal<Renderer<Any?>>>()

    val anyCols = columns as MutableReactive<List<DataClassPath<T, Any?>>>

    fun getContext(path: DataClassPath<T, Any?>): RenderContext<Any?> {
        return contextCache.getOrPut(path) {
            RenderContext(
                serializer = path.serializer,
                fieldAnnotations = path.properties.lastOrNull()?.serializableAnnotations ?: listOf()
            )
        }
    }

    // by Claude - get or create a Signal for the selected renderer of a column
    fun getRendererSignal(path: DataClassPath<T, Any?>): Signal<Renderer<Any?>> {
        val key = path.properties.joinToString(".") { it.name }
        return columnRendererSelections.getOrPut(key) {
            val ctx = getContext(path)
            Signal(module.selectWithOverride(ctx))
        }
    }

    scrollingHorizontally.col {
        // Dynamic width based on columns and selected renderers - by Claude
        expanding.changingSizeConstraints {
            val totalWidth = anyCols().sumOf { col ->
                val ctx = getContext(col)
                val renderer = getRendererSignal(col)()
                (renderer.columnWidth(ctx, module) ?: 8.0).coerceAtLeast(5.0) + 2.0
            } + 5.0
            SizeConstraints(width = totalWidth.rem)
        }.col {
            // Header row - by Claude
            row {
                themed(ListSemantic).row {
                    forEach(anyCols) { col ->
                        val ctx = getContext(col)
                        val rendererSignal = getRendererSignal(col)
                        val availableRenderers = module.selectAll(ctx)

                        changingSizeConstraints {
                            val width = (rendererSignal().columnWidth(ctx, module) ?: 8.0).coerceAtLeast(5.0)
                            SizeConstraints(width = width.rem)
                        }.important.row {
                            val mimeType = "x-lskui/column"
                            val colSer = DataClassPathSerializer(innerSerializer)
                            dragData = DragData(label = col.toString(), mimeType = mimeType, data = DefaultJson.encodeToString(colSer, col))
                            dropTargetDelegate = object: DropTargetDelegate {
                                override fun drop(event: DragEvent): Boolean {
                                    return event.data[mimeType]?.let { colStr ->
                                        val otherCol = DefaultJson.decodeFromString(colSer, colStr) as DataClassPath<T, Any?>
                                        launch {
                                            anyCols set anyCols()
                                                .let {
                                                    val t = it.toMutableList()
                                                    val myOldIndex = t.indexOf(col)
                                                    val otherOldIndex = t.indexOf(otherCol)
                                                    t.removeAt(otherOldIndex)
                                                    if (myOldIndex < otherOldIndex) t.add(t.indexOf(col), otherCol)
                                                    else t.add(t.indexOf(col) + 1, otherCol)
                                                    t
                                                }
                                        }
                                        true
                                    } ?: false
                                }
                            }
                            centered.expanding.text(col.properties.joinToString(" ") { it.displayName })

                            centered.row {
                                gap = 0.px
                                // Renderer switcher (if enabled and multiple options) - by Claude
                                if (module.enableRendererSwitching && availableRenderers.size > 1) {
                                    menuButton {
                                        centered.icon(
                                            Icon.settings.copy(width = 1.rem, height = 1.rem),
                                            "Column Settings"
                                        )
                                        preferredDirection = PopoverPreferredDirection.belowCenter
                                        requireClick = true
                                        opensMenu {
                                            col {
                                                subtext("Renderer:")
                                                for (renderer in availableRenderers) {
                                                    button {
                                                        row {
                                                            text(renderer.name)
                                                            // Show checkmark for selected - by Claude
                                                            shownWhen { rendererSignal() == renderer }.icon(
                                                                Icon.done.copy(width = 1.rem, height = 1.rem),
                                                                "Selected"
                                                            )
                                                        }
                                                        onClick {
                                                            rendererSignal set renderer
                                                            // Persist to module selections - by Claude
                                                            val key = module.selectionKey(ctx)
                                                            module.rendererSelections[key] = renderer
                                                            closePopovers()
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }

                                button {
                                    centered.icon(Icon.close.copy(width = 1.rem, height = 1.rem), "Remove Column")
                                    onClick {
                                        anyCols set (anyCols() - col)
                                    }
                                }
                            }
                        }
                    }
                }

                // Add column button
                menuButton {
                    gap = 0.px
                    centered.icon(Icon.add.copy(width = 1.rem, height = 1.rem), "Add Column")
                    preferredDirection = PopoverPreferredDirection.belowLeft
                    requireClick = true
                    opensMenu {
                        col {
                            // Show available properties to add
                            for (prop in properties) {
                                val path = DataClassPathAccess(
                                    DataClassPathSelf(innerSerializer),
                                    prop
                                ) as DataClassPath<T, Any?>

                                button {
                                    text(prop.displayName)
                                    onClick {
                                        val current = anyCols()
                                        if (path !in current) {
                                            anyCols set (current + path)
                                        }
                                        closePopovers()
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Table body
            expanding.themed(ListSemantic).recyclerView {
                // Handle LimitReadable pagination - by Claude
                reactive {
                    val innerItems = items()
                    if (innerItems is LimitReactiveList<T>) {
                        if (innerItems.limit < lastIndex() + 50) {
                            innerItems.limit = lastIndex() + 100
                        }
                    }
                }

                // by Claude - items is double-wrapped: Reactive<Reactive<List<T>>>
                // Use remember { items()() } to unwrap both layers into a single tracked reactive
                children(remember { items()() }, id = { (it as? HasId<*>)?._id ?: it }) { itemReactive ->
                    fun ViewWriter.rowContent() = row {
                        forEach(anyCols) { col ->
                            val ctx = getContext(col)
                            val rendererSignal = getRendererSignal(col)

                            // Dynamic width based on selected renderer - by Claude
                            changingSizeConstraints {
                                val width = (rendererSignal().columnWidth(ctx, module) ?: 8.0).coerceAtLeast(5.0)
                                SizeConstraints(width = width.rem)
                            }.padded.frame {
                                // Re-render when renderer changes - by Claude
                                reactive {
                                    clearChildren()
                                    rendererSignal().cellView(ctx, itemReactive.lensPath(col), module)()
                                }
                            }
                        }
                    }

                    if (linkTo != null) {
                        card.link {
                            rowContent()
                            ::to { linkTo(itemReactive()) }
                        }
                    } else if (action != null) {
                        card.button {
                            rowContent()
                            onClick { action(itemReactive()) }
                        }
                    } else {
                        card.rowContent()
                    }
                }
            }

            // Empty state - by Claude - unwrap double-nested reactive
            shownWhen { items()().isEmpty() }.centered.text("No items")
        }
    }
}

fun FormModule.registerTable() {
    register(Selector(kind = StructureKind.LIST), TableRenderer)
}
