package com.lightningkite.kiteui.forms2

import com.lightningkite.kiteui.models.FieldLabelSemantic
import com.lightningkite.kiteui.models.Icon
import com.lightningkite.kiteui.models.px
import com.lightningkite.kiteui.models.rem
import com.lightningkite.kiteui.views.ViewWriter
import com.lightningkite.kiteui.views.direct.*
import com.lightningkite.kiteui.views.fieldTheme
import com.lightningkite.kiteui.views.l2.field
import com.lightningkite.kiteui.views.l2.icon
import com.lightningkite.reactive.core.MutableReactive
import com.lightningkite.reactive.core.Reactive

/**
 * Renders values of type [T] in various modes: form, view, and cell variants.
 *
 * A Renderer provides four rendering modes:
 * - [form]: Full editable form
 * - [view]: Full read-only display
 * - [cellForm]: Compact editable (for table cells) - defaults to dialog fallback
 * - [cellView]: Compact read-only (for table cells) - defaults to [view]
 *
 * Plus metadata methods:
 * - [priority]: Determines selection when multiple renderers match
 * - [columnWidth]: Suggested width for table columns
 *
 * ## Implementing a Renderer
 *
 * At minimum, implement [form] and [view]. The cell variants have sensible defaults:
 * - [cellView] delegates to [view] (works for primitives)
 * - [cellForm] shows [cellView] with an edit button that opens a dialog with [form]
 *
 * For complex types, override [cellView] to show a compact summary (e.g., just the title).
 *
 * ## Example
 * ```kotlin
 * object StringRenderer : Renderer<String> {
 *     override fun priority(context: RenderContext<String>, module: FormModule) = 1f
 *
 *     override fun form(context: RenderContext<String>, value: MutableReactive<String>, module: FormModule) = {
 *         val maxLength = context.annotationInt("com.lightningkite.services.data.MaxLength", "size")
 *         textInput { content bind value; maxLength?.let { this.maxLength = it } }
 *     }
 *
 *     override fun view(context: RenderContext<String>, value: Reactive<String>, module: FormModule) = {
 *         text { ::content { value() } }
 *     }
 * }
 * ```
 *
 * by Claude
 */
interface Renderer<T> {

    // ===== Metadata =====

    /**
     * Human-readable name for this renderer.
     *
     * Used in the renderer switcher dropdown when [FormModule.enableRendererSwitching] is true.
     * Defaults to the class simple name.
     *
     * by Claude
     */
    val name: String get() = this::class.simpleName ?: "Unknown"

    /**
     * Priority for renderer selection. Higher values win.
     *
     * Called during selection to determine which renderer to use when multiple match.
     * Can use context to adjust priority based on annotations or type characteristics.
     *
     * @return Priority value, typically 1.0 for standard renderers, higher for specialized ones
     */
    fun priority(context: RenderContext<T>, module: FormModule): Float = 1f

    /**
     * Suggested column width for table display.
     *
     * Used by table renderers to size columns appropriately. Can use context
     * to adjust based on annotations like @MaxLength.
     *
     * @return Approximate character width, or null for auto-sizing
     */
    fun columnWidth(context: RenderContext<T>, module: FormModule): Double? = null

    // ===== Full Rendering =====

    /**
     * Render an editable form for the value.
     *
     * This is the primary editing UI. Should create appropriate input controls
     * bound to the mutable value.
     *
     * @param context Type and annotation information
     * @param value The mutable value to edit
     * @param module The form module for rendering nested types
     * @return A ViewWriter extension function that creates the UI
     */
    fun form(context: RenderContext<T>, value: MutableReactive<T>, module: FormModule): ViewWriter.() -> Unit

    /**
     * Render a read-only view of the value.
     *
     * This is the primary display UI. Should create appropriate display elements
     * that update reactively when the value changes.
     *
     * @param context Type and annotation information
     * @param value The reactive value to display
     * @param module The form module for rendering nested types
     * @return A ViewWriter extension function that creates the UI
     */
    fun view(context: RenderContext<T>, value: Reactive<T>, module: FormModule): ViewWriter.() -> Unit

    // ===== Cell Rendering (for tables) =====

    /**
     * Render a compact read-only view for table cells.
     *
     * Default implementation delegates to [view], which works for primitives.
     * Override for complex types to show a summary (e.g., title field only).
     *
     * @param context Type and annotation information
     * @param value The reactive value to display
     * @param module The form module for rendering nested types
     * @return A ViewWriter extension function that creates the UI
     */
    fun cellView(context: RenderContext<T>, value: Reactive<T>, module: FormModule): ViewWriter.() -> Unit =
        view(context, value, module)

    /**
     * Render a compact editable cell for tables.
     *
     * Default implementation shows [cellView] with an edit button that opens
     * a dialog containing the full [form]. Override for primitives to provide
     * inline editing.
     *
     * @param context Type and annotation information
     * @param value The mutable value to edit
     * @param module The form module for rendering nested types
     * @return A ViewWriter extension function that creates the UI
     */
    fun cellForm(context: RenderContext<T>, value: MutableReactive<T>, module: FormModule): ViewWriter.() -> Unit =
        module.defaultCellForm(context, value, this)

    // ===== Labeled Rendering =====

    /**
     * Render an editable form with its label.
     *
     * Default uses standard field() layout (label above, control below).
     * If description is provided, shows an info icon that displays the description on click.
     * Override for types that prefer different layouts (e.g., Boolean as inline row).
     *
     * @param context Type and annotation information
     * @param value The mutable value to edit
     * @param module The form module for rendering nested types
     * @param label The label text to display
     * @param description Optional description text shown via info icon popover
     * @return A ViewWriter extension function that creates the labeled UI
     *
     * by Claude
     */
    fun labeledForm(
        context: RenderContext<T>,
        value: MutableReactive<T>,
        module: FormModule,
        label: String,
        description: String? = context.description
    ): ViewWriter.() -> Unit = {
        fieldWithDescription(label, description) { form(context, value, module)() }
    }

    /**
     * Render a read-only view with its label.
     *
     * Default uses standard field() layout (label above, content below).
     * If description is provided, shows an info icon that displays the description on click.
     * Override for types that prefer different layouts.
     *
     * @param context Type and annotation information
     * @param value The reactive value to display
     * @param module The form module for rendering nested types
     * @param label The label text to display
     * @param description Optional description text shown via info icon popover
     * @return A ViewWriter extension function that creates the labeled UI
     *
     * by Claude
     */
    fun labeledView(
        context: RenderContext<T>,
        value: Reactive<T>,
        module: FormModule,
        label: String,
        description: String? = context.description
    ): ViewWriter.() -> Unit = {
        fieldWithDescription(label, description) { view(context, value, module)() }
    }
}

// ===== Helper Functions =====
