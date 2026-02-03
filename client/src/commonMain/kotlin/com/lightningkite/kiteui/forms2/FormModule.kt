@file:OptIn(ExperimentalSerializationApi::class)

package com.lightningkite.kiteui.forms2

import com.lightningkite.kiteui.FileReference
import com.lightningkite.kiteui.models.Icon
import com.lightningkite.kiteui.models.SubtextSemantic
import com.lightningkite.kiteui.models.rem
import com.lightningkite.kiteui.views.ViewWriter
import com.lightningkite.kiteui.views.atTopEnd
import com.lightningkite.kiteui.views.direct.*
import com.lightningkite.kiteui.views.expanding
import com.lightningkite.kiteui.views.l2.dialog
import com.lightningkite.kiteui.views.l2.field
import com.lightningkite.kiteui.views.l2.icon
import com.lightningkite.reactive.context.reactive
import com.lightningkite.reactive.core.Constant
import com.lightningkite.reactive.core.MutableReactive
import com.lightningkite.reactive.core.Reactive
import com.lightningkite.reactive.core.Signal
import com.lightningkite.services.database.SerializableAnnotation
import com.lightningkite.services.files.ServerFile
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.modules.EmptySerializersModule
import kotlinx.serialization.modules.SerializersModule

/**
 * Central registry for type renderers.
 *
 * FormModule manages renderer registration and selection. Register renderers with
 * [register], then use [form], [view], [cellForm], [cellView] to render values.
 *
 * ## Selection Algorithm
 * 1. Filter renderers where [Selector.matches] returns true
 * 2. For each matching renderer, compute [Renderer.priority]
 * 3. Select the renderer with highest priority
 *
 * ## Usage
 * ```kotlin
 * val module = FormModule().apply {
 *     register(Selector(type = "kotlin.String"), StringRenderer)
 *     register(Selector(kind = SerialKind.ENUM), EnumRenderer)
 *     // ... more registrations
 * }
 *
 * // Render a form
 * viewWriter.apply(module.form(context, mutableValue))
 * ```
 *
 * by Claude
 */
class FormModule {

    private val renderers = mutableListOf<Pair<Selector, Renderer<*>>>()

    // ===== External Service Configuration =====

    /**
     * File upload handler. Set this to enable file upload functionality.
     *
     * When set, ServerFileRenderer will show an upload button that calls this function.
     * The function should upload the file and return the resulting ServerFile.
     */
    var fileUpload: (suspend (FileReference) -> ServerFile)? = null

    /**
     * Type info resolver for foreign keys. Set this to enable foreign key rendering.
     *
     * When set, ForeignKeyRenderer will use this to resolve @References annotations
     * and provide selection UI for related entities.
     *
     * @param typeName The fully qualified class name of the referenced type
     * @return TypeInfo for the type, or null if not available
     */
    var typeInfo: (typeName: String) -> TypeInfo<*, *>? = { null }

    /**
     * Kotlinx serialization module for resolving contextual serializers.
     *
     * Used by renderers that need to resolve @Contextual types, such as SortPathRenderer.
     *
     * by Claude
     */
    var serializersModule: SerializersModule = EmptySerializersModule()

    // ===== Renderer Switching =====
    // by Claude

    /**
     * Enable renderer switching UI.
     *
     * When true, labeled forms/views show a small dropdown to switch between
     * compatible renderers. Useful for debugging or when multiple renderers
     * are valid for a type.
     */
    var enableRendererSwitching: Boolean = false

    /**
     * Stores user-selected renderers, keyed by [selectionKey].
     * Only used when [enableRendererSwitching] is true.
     */
    internal val rendererSelections: MutableMap<String, Renderer<*>> = mutableMapOf()

    /**
     * Generate a unique key for storing renderer selection.
     * Based on serializer name and field annotations.
     */
    fun <T> selectionKey(context: RenderContext<T>): String {
        val typeName = context.serializer.descriptor.serialName
        val annotations = context.fieldAnnotations.joinToString(",") { it.fqn }
        return if (annotations.isEmpty()) typeName else "$typeName|$annotations"
    }

    /**
     * Get all compatible renderers for a context, sorted by priority (highest first).
     *
     * Only includes renderers with priority >= 0.
     */
    @Suppress("UNCHECKED_CAST")
    fun <T> selectAll(context: RenderContext<T>): List<Renderer<T>> {
        return renderers
            .filter { (selector, _) -> selector.matches(context) }
            .map { (_, renderer) -> renderer as Renderer<T> }
            .filter { it.priority(context, this) >= 0f }
            .sortedByDescending { it.priority(context, this) }
    }

    /**
     * Get the currently selected renderer for a context.
     *
     * If [enableRendererSwitching] is true and user has made a selection,
     * returns that selection. Otherwise returns the highest priority renderer.
     */
    @Suppress("UNCHECKED_CAST")
    fun <T> selectWithOverride(context: RenderContext<T>): Renderer<T> {
        if (!enableRendererSwitching) return select(context)
        val key = selectionKey(context)
        return rendererSelections[key] as? Renderer<T> ?: select(context)
    }

    /**
     * Register a renderer with its matching selector.
     *
     * Renderers are evaluated in the order that produces highest priority for each context.
     * Multiple renderers can match; the one with highest [Renderer.priority] wins.
     */
    fun <T> register(selector: Selector, renderer: Renderer<T>) {
        renderers.add(selector to renderer)
    }

    /**
     * Select the best renderer for the given context.
     *
     * Filters by [Selector.matches], then picks the highest [Renderer.priority].
     *
     * @throws IllegalStateException if no renderer matches
     */
    @Suppress("UNCHECKED_CAST")
    fun <T> select(context: RenderContext<T>): Renderer<T> {
        return renderers
            .filter { (selector, _) -> selector.matches(context) }
            .maxByOrNull { (_, renderer) ->
                (renderer as Renderer<T>).priority(context, this)
            }
            ?.second as? Renderer<T>
            ?: error("No renderer for ${context.serializer.descriptor.serialName}")
    }

    // ===== Convenience Methods =====

    /**
     * Create a [RenderContext] for a top-level value (no field annotations).
     */
    fun <T> context(serializer: KSerializer<T>): RenderContext<T> =
        RenderContext(serializer)

    /**
     * Create a [RenderContext] for a field with its annotations.
     */
    fun <T> context(
        serializer: KSerializer<T>,
        fieldAnnotations: List<SerializableAnnotation>
    ): RenderContext<T> = RenderContext(serializer, fieldAnnotations)

    // ===== Rendering Entry Points =====

    /**
     * Render an editable form for the value.
     */
    fun <T> form(context: RenderContext<T>, value: MutableReactive<T>): ViewWriter.() -> Unit =
        select(context).form(context, value, this)

    /**
     * Render a read-only view of the value.
     */
    fun <T> view(context: RenderContext<T>, value: Reactive<T>): ViewWriter.() -> Unit =
        select(context).view(context, value, this)

    /**
     * Render a compact editable cell.
     */
    fun <T> cellForm(context: RenderContext<T>, value: MutableReactive<T>): ViewWriter.() -> Unit =
        select(context).cellForm(context, value, this)

    /**
     * Render a compact read-only cell.
     */
    fun <T> cellView(context: RenderContext<T>, value: Reactive<T>): ViewWriter.() -> Unit =
        select(context).cellView(context, value, this)

    /**
     * Get the suggested column width for a type.
     */
    fun <T> columnWidth(context: RenderContext<T>): Double? =
        select(context).columnWidth(context, this)

    // ===== Default Cell Form Implementation =====

    /**
     * Default cellForm implementation: shows cellView with edit button that opens dialog.
     *
     * This is used by [Renderer.cellForm] default implementation. Renderers for primitive
     * types should override cellForm to provide inline editing instead.
     */
    fun <T> defaultCellForm(
        context: RenderContext<T>,
        value: MutableReactive<T>,
        renderer: Renderer<T>
    ): ViewWriter.() -> Unit = {
        row {
            expanding.frame { renderer.cellView(context, value, this@FormModule)() }
            button {
                icon(Icon.settings, "Edit")
                onClick {
                    dialog { _ ->
                        renderer.form(context, value, this@FormModule)()
                    }
                }
            }
        }
    }

    // ===== Default Labeled Implementations =====
    // by Claude

    // ===== Labeled Form/View with Switcher =====
    // by Claude

    /**
     * Render a labeled form, optionally with renderer switcher.
     *
     * When [enableRendererSwitching] is true and multiple renderers match,
     * shows a small dropdown to switch between them.
     */
    fun <T> labeledFormWithSwitcher(
        context: RenderContext<T>,
        value: MutableReactive<T>,
        label: String,
        description: String?
    ): ViewWriter.() -> Unit {
        val renderers = selectAll(context)
        val selectedRenderer = selectWithOverride(context)

        // No switching if disabled or only one renderer
        if (!enableRendererSwitching) {
            return selectedRenderer.labeledForm(context, value, this, label, description)
        }

        // With switcher
        val key = selectionKey(context)
        return {
            val selected = Signal(selectedRenderer)
            frame {
                frame {
                    reactive {
                        clearChildren()
                        selected().labeledForm(context, value, this@FormModule, label, description)()
                    }
                }
                atTopEnd.sizeConstraints(width = 8.rem).themed(SubtextSemantic).select {
                    bind(selected, Constant(renderers)) { it.name }
                    selected.addListener {
                        rendererSelections[key] = selected.value
                    }
                }
            }
        }
    }

    /**
     * Render a labeled view, optionally with renderer switcher.
     *
     * When [enableRendererSwitching] is true and multiple renderers match,
     * shows a small dropdown to switch between them.
     */
    fun <T> labeledViewWithSwitcher(
        context: RenderContext<T>,
        value: Reactive<T>,
        label: String,
        description: String?
    ): ViewWriter.() -> Unit {
        val renderers = selectAll(context)
        val selectedRenderer = selectWithOverride(context)

        // No switching if disabled or only one renderer
        if (!enableRendererSwitching || renderers.size <= 1) {
            return selectedRenderer.labeledView(context, value, this, label, description)
        }

        // With switcher
        val key = selectionKey(context)
        return {
            val selected = Signal(selectedRenderer)
            frame {
                frame {
                    reactive {
                        clearChildren()
                        selected().labeledView(context, value, this@FormModule, label, description)()
                    }
                }
                atTopEnd.sizeConstraints(width = 8.rem).themed(SubtextSemantic).select {
                    bind(selected, Constant(renderers)) { it.name }
                    selected.addListener {
                        rendererSelections[key] = selected.value
                    }
                }
            }
        }
    }

}

// ===== ViewWriter Extension Functions =====
// by Claude
//
// Simple reified extensions for rendering forms and views.
// Usage: form(module, myValue) or view(module, myValue)

/**
 * Render an editable form for a value.
 */
inline fun <reified T> ViewWriter.form(module: FormModule, value: MutableReactive<T>) {
    val context = RenderContext(kotlinx.serialization.serializer<T>())
    module.form(context, value)(this)
}

/**
 * Render a read-only view of a value.
 */
inline fun <reified T> ViewWriter.view(module: FormModule, value: Reactive<T>) {
    val context = RenderContext(kotlinx.serialization.serializer<T>())
    module.view(context, value)(this)
}

/**
 * Render a compact editable cell (for tables).
 */
inline fun <reified T> ViewWriter.cellForm(module: FormModule, value: MutableReactive<T>) {
    val context = RenderContext(kotlinx.serialization.serializer<T>())
    module.cellForm(context, value)(this)
}

/**
 * Render a compact read-only cell (for tables).
 */
inline fun <reified T> ViewWriter.cellView(module: FormModule, value: Reactive<T>) {
    val context = RenderContext(kotlinx.serialization.serializer<T>())
    module.cellView(context, value)(this)
}

/**
 * Render an editable form with its label.
 *
 * When [FormModule.enableRendererSwitching] is true, shows a dropdown to switch renderers.
 *
 * @param description Optional description text shown via info icon popover
 */
inline fun <reified T> ViewWriter.labeledForm(module: FormModule, value: MutableReactive<T>, label: String, description: String? = null) {
    val context = RenderContext(kotlinx.serialization.serializer<T>())
    module.labeledFormWithSwitcher(context, value, label, description ?: context.description)(this)
}

/**
 * Render a read-only view with its label.
 *
 * When [FormModule.enableRendererSwitching] is true, shows a dropdown to switch renderers.
 *
 * @param description Optional description text shown via info icon popover
 */
inline fun <reified T> ViewWriter.labeledView(module: FormModule, value: Reactive<T>, label: String, description: String? = null) {
    val context = RenderContext(kotlinx.serialization.serializer<T>())
    module.labeledViewWithSwitcher(context, value, label, description ?: context.description)(this)
}
