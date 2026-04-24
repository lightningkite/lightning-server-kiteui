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
import com.lightningkite.services.database.Condition
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
public fun ViewWriter.subrendererSelector(
    selectedRenderer: MutableReactive<Renderer<Any?>>,
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
public inline fun ViewWriter.fieldWithDescription(label: String, description: String?, content: ViewWriter.() -> Unit) {
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
public fun <T> ViewWriter.view(
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
public inline fun <reified T> ViewWriter.form(module: FormModule, value: MutableReactive<T>): Unit = form(module, serializer(), value)

/**
 * Render a read-only view of a value.
 */
public inline fun <reified T> ViewWriter.view(module: FormModule, value: Reactive<T>): Unit = view(module, serializer(), value)

/**
 * Render a compact editable cell (for tables).
 */
public inline fun <reified T> ViewWriter.cellForm(module: FormModule, value: MutableReactive<T>): Unit = cellForm(module, serializer(), value)

/**
 * Render a compact read-only cell (for tables).
 */
public inline fun <reified T> ViewWriter.cellView(module: FormModule, value: Reactive<T>): Unit = cellView(module, serializer(), value)

/**
 * Render an editable form with its label.
 *
 * When [FormModule.enableRendererSwitching] is true, shows a dropdown to switch renderers.
 *
 * @param description Optional description text shown via info icon popover
 */
public inline fun <reified T> ViewWriter.labeledForm(module: FormModule, value: MutableReactive<T>, label: String, description: String? = null): Unit = labeledForm(module, serializer(), value, label, description)

/**
 * Render a read-only view with its label.
 *
 * When [FormModule.enableRendererSwitching] is true, shows a dropdown to switch renderers.
 *
 * @param description Optional description text shown via info icon popover
 */
public inline fun <reified T> ViewWriter.labeledView(module: FormModule, value: Reactive<T>, label: String, description: String? = null): Unit = labeledView(module, serializer(), value, label, description)


/**
 * Render an editable form for a value.
 */
public fun <T> ViewWriter.form(module: FormModule, serializer: KSerializer<T>, value: MutableReactive<T>) {
    val context = RenderContext(serializer)
    module.form(context, value)(this)
}

/**
 * Render a read-only view of a value.
 */
public fun <T> ViewWriter.view(module: FormModule, serializer: KSerializer<T>, value: Reactive<T>) {
    val context = RenderContext(serializer)
    module.view(context, value)(this)
}

/**
 * Render a compact editable cell (for tables).
 */
public fun <T> ViewWriter.cellForm(module: FormModule, serializer: KSerializer<T>, value: MutableReactive<T>) {
    val context = RenderContext(serializer)
    module.cellForm(context, value)(this)
}

/**
 * Render a compact read-only cell (for tables).
 */
public fun <T> ViewWriter.cellView(module: FormModule, serializer: KSerializer<T>, value: Reactive<T>) {
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
public fun <T> ViewWriter.labeledForm(module: FormModule, serializer: KSerializer<T>, value: MutableReactive<T>, label: String, description: String? = null) {
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
public fun <T> ViewWriter.labeledView(module: FormModule, serializer: KSerializer<T>, value: Reactive<T>, label: String, description: String? = null) {
    val context = RenderContext(serializer)
    module.labeledViewWithSwitcher(context, value, label, description ?: context.description)(this)
}


/**
 * Converts a Condition into a human-readable string for display.
 * Used in the UI to show users what filters are currently applied.
 */
public fun Condition<*>.friendly(): String {
    return when (this) {
        Condition.Always -> "All"
        is Condition.And<*> -> conditions.joinToString(" and ") { it.friendly() }
        is Condition.Or<*> -> conditions.joinToString(" or ") { it.friendly() }
        Condition.Never -> "None"
        is Condition.OnField<*, *> -> this.key.displayName.lowercase() + " " + condition.friendly()
        is Condition.Equal<*> -> "is $value"
        is Condition.NotEqual<*> -> "isn't $value"
        is Condition.GreaterThan<*> -> "> $value"
        is Condition.GreaterThanOrEqual<*> -> ">= $value"
        is Condition.LessThan<*> -> "< $value"
        is Condition.LessThanOrEqual<*> -> "<= $value"
        is Condition.Inside<*> -> "is ${values.joinToString(" or ")}"
        is Condition.NotInside<*> -> "isn't ${values.joinToString(" or ")}"
        is Condition.StringContains -> "contains $value"
        is Condition.GeoDistance -> "is within ${greaterThanKilometers} km and ${lessThanKilometers} km"
        is Condition.IfNotNull<*> -> condition.friendly()
        is Condition.Exists<*> -> "has key $key"
        is Condition.FullTextSearch<*> -> "matches ${this.value}"
//        is Condition.IntBitsAnyClear -> TODO()
//        is Condition.IntBitsAnySet -> TODO()
//        is Condition.IntBitsClear -> TODO()
//        is Condition.IntBitsSet -> TODO()
        is Condition.ListAllElements<*> -> "all items are ${this.condition.friendly()}"
        is Condition.ListAnyElements<*> -> "has any item that ${this.condition.friendly()}"
        is Condition.ListSizesEquals<*> -> "size is $count"
        is Condition.Not<*> -> "not ${condition.friendly()}"
        is Condition.OnKey<*> -> "${this.key} ${this.condition.friendly()}"
        is Condition.RawStringContains<*> -> "contains $value"
        is Condition.RegexMatches -> "matches regex ${this.pattern}"
        is Condition.SetAllElements<*> -> "all items are ${this.condition.friendly()}"
        is Condition.SetAnyElements<*> -> "has any item that ${this.condition.friendly()}"
        is Condition.SetSizesEquals<*> -> "size is $count"
        else -> toString()
    }
}
