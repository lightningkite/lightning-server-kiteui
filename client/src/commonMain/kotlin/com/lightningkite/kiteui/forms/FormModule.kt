@file:OptIn(ExperimentalSerializationApi::class)

package com.lightningkite.kiteui.forms

import com.lightningkite.kiteui.FileReference
import com.lightningkite.kiteui.models.Icon
import com.lightningkite.kiteui.models.SubtextSemantic
import com.lightningkite.kiteui.models.rem
import com.lightningkite.kiteui.views.ElementWriter
import com.lightningkite.kiteui.views.atTopEnd
import com.lightningkite.kiteui.views.direct.*
import com.lightningkite.kiteui.views.expanding
import com.lightningkite.kiteui.views.l2.dialog
import com.lightningkite.kiteui.views.direct.icon
import com.lightningkite.kiteui.views.themed
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
public class FormModule {

    private val renderers = mutableListOf<Pair<Selector, Renderer<*>>>()

    // ===== External Service Configuration =====

    /**
     * File upload handler. Set this to enable file upload functionality.
     *
     * When set, ServerFileRenderer will show an upload button that calls this function.
     * The function should upload the file and return the resulting ServerFile.
     */
    public var fileUpload: (suspend (FileReference) -> ServerFile)? = null

    /**
     * Type info resolver for foreign keys. Set this to enable foreign key rendering.
     *
     * When set, ForeignKeyRenderer will use this to resolve @References annotations
     * and provide selection UI for related entities.
     *
     * @param typeName The fully qualified class name of the referenced type
     * @return TypeInfo for the type, or null if not available
     */
    public var typeInfo: (typeName: String) -> TypeInfo<*, *>? = { null }

    /**
     * Kotlinx serialization module for resolving contextual serializers.
     *
     * Used by renderers that need to resolve @Contextual types, such as SortPathRenderer.
     *
     * by Claude
     */
    public var serializersModule: SerializersModule = EmptySerializersModule()

    // ===== Field Visibility Control =====
    // by Claude - migrated from forms for admin panel support

    /**
     * Maps annotation FQNs to visibility overrides.
     *
     * Used to control how fields with certain annotations are rendered:
     * - HIDDEN: Field is not rendered at all
     * - READ: Field is rendered read-only (view mode even in forms)
     * - EDIT: Field is rendered normally (editable in forms)
     *
     * Default settings hide admin-only fields and make denormalized fields read-only.
     */
    public val visibilitySettings: MutableMap<String, FieldVisibility> = mutableMapOf(
        "com.lightningkite.services.data.AdminHidden" to FieldVisibility.HIDDEN,
        "com.lightningkite.services.data.Denormalized" to FieldVisibility.READ,
        "com.lightningkite.services.data.AdminViewOnly" to FieldVisibility.READ
    )

    /**
     * Determine the effective visibility for a context based on annotations and overrides.
     *
     * Checks annotations against [visibilitySettings] and returns the minimum visibility found.
     * Also handles special case of UUID _id fields which are typically hidden.
     */
    public fun effectiveVisibility(context: RenderContext<*>): FieldVisibility {
        val annotationVisibility = context.allAnnotations
            .mapNotNull { visibilitySettings[it.fqn] }
            .minOrNull()

        // Special case: UUID _id fields are hidden by default (unless they have @References)
        val isUuidId = context.serializer.descriptor.serialName.substringBefore('/') == "com.lightningkite.Uuid"
                && !context.hasAnnotation("com.lightningkite.services.data.References")
                && !context.hasAnnotation("com.lightningkite.services.data.MultipleReferences")

        return annotationVisibility
            ?: if (isUuidId) visibilitySettings["com.lightningkite.services.data.AdminHidden"] ?: FieldVisibility.HIDDEN
            else FieldVisibility.EDIT
    }

    // ===== Renderer Switching =====
    // by Claude

    /**
     * Enable renderer switching UI.
     *
     * When true, labeled forms/views show a small dropdown to switch between
     * compatible renderers. Useful for debugging or when multiple renderers
     * are valid for a type.
     */
    public var enableRendererSwitching: Boolean = false

    /**
     * Stores user-selected renderers, keyed by [selectionKey].
     * Only used when [enableRendererSwitching] is true.
     */
    internal val rendererSelections: MutableMap<String, Renderer<*>> = mutableMapOf()

    /**
     * Generate a unique key for storing renderer selection.
     * Based on serializer name and field annotations.
     */
    public fun <T> selectionKey(context: RenderContext<T>): String {
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
    public fun <T> selectAll(context: RenderContext<T>): List<Renderer<T>> {
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
    public fun <T> selectWithOverride(context: RenderContext<T>): Renderer<T> {
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
    public fun <T> register(selector: Selector, renderer: Renderer<T>) {
        renderers.add(selector to renderer)
    }

    /**
     * Get all registered renderers with their selectors.
     *
     * Useful for debugging/testing to see what renderers are available.
     * by Claude
     */
    public val allRenderers: List<Pair<Selector, Renderer<*>>>
        get() = renderers.toList()

    /**
     * Select the best renderer for the given context.
     *
     * Filters by [Selector.matches], then picks the highest [Renderer.priority].
     *
     * @throws IllegalStateException if no renderer matches
     */
    @Suppress("UNCHECKED_CAST")
    public fun <T> select(context: RenderContext<T>): Renderer<T> {
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
    public fun <T> context(serializer: KSerializer<T>): RenderContext<T> =
        RenderContext(serializer)

    /**
     * Create a [RenderContext] for a field with its annotations.
     */
    public fun <T> context(
        serializer: KSerializer<T>,
        fieldAnnotations: List<SerializableAnnotation>
    ): RenderContext<T> = RenderContext(serializer, fieldAnnotations)

    // ===== Rendering Entry Points =====

    /**
     * Render an editable form for the value.
     */
    public fun <T> form(context: RenderContext<T>, value: MutableReactive<T>): ElementWriter.CanAddTheme.() -> Unit =
        select(context).form(context, value, this)

    /**
     * Render a read-only view of the value.
     */
    public fun <T> view(context: RenderContext<T>, value: Reactive<T>): ElementWriter.CanAddTheme.() -> Unit =
        select(context).view(context, value, this)

    /**
     * Render a compact editable cell.
     */
    public fun <T> cellForm(context: RenderContext<T>, value: MutableReactive<T>): ElementWriter.CanAddTheme.() -> Unit =
        select(context).cellForm(context, value, this)

    /**
     * Render a compact read-only cell.
     */
    public fun <T> cellView(context: RenderContext<T>, value: Reactive<T>): ElementWriter.CanAddTheme.() -> Unit =
        select(context).cellView(context, value, this)

    /**
     * Get the suggested column width for a type.
     */
    public fun <T> columnWidth(context: RenderContext<T>): Double? =
        select(context).columnWidth(context, this)

    // ===== Default Cell Form Implementation =====

    /**
     * Default cellForm implementation: shows cellView with edit button that opens dialog.
     *
     * This is used by [Renderer.cellForm] default implementation. Renderers for primitive
     * types should override cellForm to provide inline editing instead.
     */
    public fun <T> defaultCellForm(
        context: RenderContext<T>,
        value: MutableReactive<T>,
        renderer: Renderer<T>
    ): ElementWriter.CanAddTheme.() -> Unit = {
        row {
            expanding.frame { renderer.cellView(context, value, this@FormModule)() }
            button {
                icon(Icon.settings, "Edit")
                onClick {
                    this.context.dialog { _ ->
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
    public fun <T> labeledFormWithSwitcher(
        context: RenderContext<T>,
        value: MutableReactive<T>,
        label: String,
        description: String?
    ): ElementWriter.CanAddTheme.() -> Unit {
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
                atTopEnd.sizeConstraints(width = 0.75.rem).themed(SubtextSemantic).select {
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
    public fun <T> labeledViewWithSwitcher(
        context: RenderContext<T>,
        value: Reactive<T>,
        label: String,
        description: String?
    ): ElementWriter.CanAddTheme.() -> Unit {
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
