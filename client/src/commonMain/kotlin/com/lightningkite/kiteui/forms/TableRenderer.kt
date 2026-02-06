@file:OptIn(ExperimentalSerializationApi::class)

package com.lightningkite.kiteui.forms

import com.lightningkite.kiteui.models.*
import com.lightningkite.kiteui.navigation.Page
import com.lightningkite.kiteui.views.*
import com.lightningkite.kiteui.views.direct.*
import com.lightningkite.kiteui.views.l2.children
import com.lightningkite.kiteui.views.l2.icon
import com.lightningkite.reactive.context.invoke
import com.lightningkite.reactive.context.reactive
import com.lightningkite.reactive.core.*
import com.lightningkite.serialization.lensPath
import com.lightningkite.kiteui.navigation.DefaultJson
import com.lightningkite.lightningserver.db.LimitReactiveList
import com.lightningkite.reactive.extensions.withWrite
import com.lightningkite.services.database.*
import kotlinx.coroutines.launch
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
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
                columns = Signal(innerSerializer.defaultColumns().map { ColumnInfo(it, module) }),
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

@Serializable
data class ColumnInfo<T>(
    val path: DataClassPathPartial<T>,
    val rendererSelected: String? = null
) {
    constructor(path: DataClassPathPartial<T>, renderer: Renderer<*>): this(path, renderer.name)
    constructor(path: DataClassPathPartial<T>, formModule: FormModule): this(path, formModule.select(RenderContext(path.serializerAny, path.properties.lastOrNull()?.serializableAnnotations ?: listOf())))
    @Transient private var cached: Renderer<Any?>? = null
    @Suppress("UNCHECKED_CAST")
    @Transient val ctx = RenderContext(
        path.serializerAny as KSerializer<Any?>,
        path.properties.lastOrNull()?.serializableAnnotations ?: listOf()
    )
    fun renderer(formModule: FormModule): Renderer<Any?> {
        return cached ?: run {
            val n = formModule.selectAll(ctx).find { it.name == rendererSelected }
                ?: formModule.select(ctx)
            cached = n
            n
        }
    }
    fun columnWidth(formModule: FormModule): Double = renderer(formModule).columnWidth(ctx, formModule) ?: 8.0
}

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
    columns: MutableReactive<List<ColumnInfo<T>>>,
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

    scrollingHorizontally.col {
        // Dynamic width based on columns and selected renderers - by Claude
        expanding.changingSizeConstraints {
            val totalWidth = columns().sumOf { it.columnWidth(module) } + 5.0
            SizeConstraints(width = totalWidth.rem)
        }.col {
            // Header row - by Claude
            row {
                themed(ListSemantic).row {
                    forEach(columns) { col ->

                        sizeConstraints(width = col.columnWidth(module).rem).important.row {
                            val mimeType = "x-lskui/column"
                            val colSer = ColumnInfo.serializer(innerSerializer)
                            dragData = DragData(label = col.toString(), mimeType = mimeType, data = DefaultJson.encodeToString(colSer, col))
                            dropTargetDelegate = object: DropTargetDelegate {
                                override fun drop(event: DragEvent): Boolean {
                                    return event.data[mimeType]?.let { colStr ->
                                        val otherCol = DefaultJson.decodeFromString(colSer, colStr)
                                        launch {
                                            columns set columns()
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
                            centered.expanding.text(col.path.properties.joinToString(" ") { it.displayName })

                            centered.row {
                                gap = 0.px
                                // Renderer switcher (if enabled and multiple options) - by Claude
                                if (module.enableRendererSwitching) {
                                    subrendererSelector(
                                        selectedRenderer = Constant(col.renderer(module)).withWrite { v ->
                                            columns set columns().map {
                                                if(it == col) col.copy(rendererSelected = v.name)
                                                else it
                                            }
                                        },
                                        elementRenderers = module.selectAll(col.ctx)
                                    )
                                }

                                button {
                                    centered.icon(Icon.close.copy(width = 1.rem, height = 1.rem), "Remove Column")
                                    onClick {
                                        columns set (columns() - col)
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
                                        val current = columns()
                                        if (path !in current.map { it.path }) {
                                            columns set (current + ColumnInfo(path, module))
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
                    fun ViewWriter.rowContent() = themed(ListSemantic).row {
                        forEach(columns) { col ->
                            col.renderer(module).cellView(col.ctx, itemReactive.lensPath(col.path as DataClassPath<Any?, Any?>), module)(
                                sizeConstraints(width = col.columnWidth(module).rem)
                            )
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
