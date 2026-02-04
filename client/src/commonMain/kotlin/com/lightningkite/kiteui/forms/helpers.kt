package com.lightningkite.kiteui.forms

import com.lightningkite.kiteui.models.FieldLabelSemantic
import com.lightningkite.kiteui.models.Icon
import com.lightningkite.kiteui.models.SubtextSemantic
import com.lightningkite.kiteui.models.px
import com.lightningkite.kiteui.models.rem
import com.lightningkite.kiteui.views.ViewDsl
import com.lightningkite.kiteui.views.ViewWriter
import com.lightningkite.kiteui.views.centered
import com.lightningkite.kiteui.views.direct.*
import com.lightningkite.kiteui.views.expanding
import com.lightningkite.kiteui.views.fieldTheme
import com.lightningkite.kiteui.views.l2.errorText
import com.lightningkite.kiteui.views.l2.field
import com.lightningkite.kiteui.views.l2.icon
import com.lightningkite.reactive.core.Constant
import com.lightningkite.reactive.core.MutableReactive
import com.lightningkite.reactive.core.Reactive
import com.lightningkite.reactive.core.Signal
import com.lightningkite.services.database.SerializableAnnotation
import kotlinx.serialization.KSerializer
import kotlinx.serialization.serializer
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

// by Claude
@ViewDsl
@OptIn(ExperimentalContracts::class)
internal inline fun ViewWriter.fieldWithoutBorder(label: String, description: String? = null, content: ViewWriter.() -> Unit): Unit {
    contract { callsInPlace(content, InvocationKind.EXACTLY_ONCE) }
    col {
        gap = 0.px
        row {
            gap = 0.25.rem
            centered.themed(FieldLabelSemantic).text(label)
            if (description != null) {
                centered.textPopover(description).icon(Icon.info.copy(width = 1.rem, height = 1.rem), "Info")
            }
        }
        content()
        SubtextSemantic.onNext.errorText()
    }
}
fun ViewWriter.subrendererSelector(
    selectedRenderer: Signal<Renderer<Any?>>,
    elementRenderers: List<Renderer<Any?>>
) {
    themed(SubtextSemantic).row {
        expanding.space()
        sizeConstraints(width = 0.75.rem).select {
            bind(selectedRenderer, Constant(elementRenderers)) { it.name }
        }
    }
}
/**
 * Renders a labeled field with an optional description info icon.
 *
 * When description is provided, shows a small info icon next to the label
 * that displays the description in a popover when clicked.
 *
 * by Claude
 */
inline fun ViewWriter.fieldWithDescription(label: String, description: String?, content: ViewWriter.() -> Unit) {
    if (description == null) {
        // No description - use standard field layout
        field(label, content)
    } else {
        // Has description - show info icon next to label
        col {
            gap = 0.px
            row {
                gap = 0.25.rem
                FieldLabelSemantic.onNext.text(label)
                textPopover(description).icon(Icon.info.copy(width = 1.rem, height = 1.rem), "Info")
            }
            fieldTheme.content()
        }
    }
}


/**
 * Render a read-only view of the given type.
 *
 * @param context The FormModule containing renderer configuration (named 'context' for API compatibility)
 * @param serializer The KSerializer for the type being displayed
 * @param readable The reactive data source
 * @param annotations Optional field annotations to pass to the renderer
 */
fun <T> ViewWriter.view(
    context: FormModule,
    serializer: KSerializer<T>,
    readable: Reactive<T>,
    annotations: List<SerializableAnnotation> = emptyList()
) {
    val renderContext = RenderContext(serializer, annotations)
    context.view(renderContext, readable)()
}

/**
 * Render an editable form for a value.
 */
inline fun <reified T> ViewWriter.form(module: FormModule, value: MutableReactive<T>) = form(module, serializer(), value)

/**
 * Render a read-only view of a value.
 */
inline fun <reified T> ViewWriter.view(module: FormModule, value: Reactive<T>) = view(module, serializer(), value)

/**
 * Render a compact editable cell (for tables).
 */
inline fun <reified T> ViewWriter.cellForm(module: FormModule, value: MutableReactive<T>) = cellForm(module, serializer(), value)

/**
 * Render a compact read-only cell (for tables).
 */
inline fun <reified T> ViewWriter.cellView(module: FormModule, value: Reactive<T>) = cellView(module, serializer(), value)

/**
 * Render an editable form with its label.
 *
 * When [FormModule.enableRendererSwitching] is true, shows a dropdown to switch renderers.
 *
 * @param description Optional description text shown via info icon popover
 */
inline fun <reified T> ViewWriter.labeledForm(module: FormModule, value: MutableReactive<T>, label: String, description: String? = null) = labeledForm(module, serializer(), value, label, description)

/**
 * Render a read-only view with its label.
 *
 * When [FormModule.enableRendererSwitching] is true, shows a dropdown to switch renderers.
 *
 * @param description Optional description text shown via info icon popover
 */
inline fun <reified T> ViewWriter.labeledView(module: FormModule, value: Reactive<T>, label: String, description: String? = null) = labeledView(module, serializer(), value, label, description)


/**
 * Render an editable form for a value.
 */
fun <T> ViewWriter.form(module: FormModule, serializer: KSerializer<T>, value: MutableReactive<T>) {
    val context = RenderContext(serializer)
    module.form(context, value)(this)
}

/**
 * Render a read-only view of a value.
 */
fun <T> ViewWriter.view(module: FormModule, serializer: KSerializer<T>, value: Reactive<T>) {
    val context = RenderContext(serializer)
    module.view(context, value)(this)
}

/**
 * Render a compact editable cell (for tables).
 */
fun <T> ViewWriter.cellForm(module: FormModule, serializer: KSerializer<T>, value: MutableReactive<T>) {
    val context = RenderContext(serializer)
    module.cellForm(context, value)(this)
}

/**
 * Render a compact read-only cell (for tables).
 */
fun <T> ViewWriter.cellView(module: FormModule, serializer: KSerializer<T>, value: Reactive<T>) {
    val context = RenderContext(serializer)
    module.cellView(context, value)(this)
}

/**
 * Render an editable form with its label.
 *
 * When [FormModule.enableRendererSwitching] is true, shows a dropdown to switch renderers.
 *
 * @param description Optional description text shown via info icon popover
 */
fun <T> ViewWriter.labeledForm(module: FormModule, serializer: KSerializer<T>, value: MutableReactive<T>, label: String, description: String? = null) {
    val context = RenderContext(serializer)
    module.labeledFormWithSwitcher(context, value, label, description ?: context.description)(this)
}

/**
 * Render a read-only view with its label.
 *
 * When [FormModule.enableRendererSwitching] is true, shows a dropdown to switch renderers.
 *
 * @param description Optional description text shown via info icon popover
 */
fun <T> ViewWriter.labeledView(module: FormModule, serializer: KSerializer<T>, value: Reactive<T>, label: String, description: String? = null) {
    val context = RenderContext(serializer)
    module.labeledViewWithSwitcher(context, value, label, description ?: context.description)(this)
}

