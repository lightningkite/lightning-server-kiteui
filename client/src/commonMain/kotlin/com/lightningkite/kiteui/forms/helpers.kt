package com.lightningkite.kiteui.forms

import com.lightningkite.kiteui.models.Icon
import com.lightningkite.kiteui.models.SubtextSemantic
import com.lightningkite.kiteui.models.rem
import com.lightningkite.kiteui.views.ElementWriter
import com.lightningkite.kiteui.views.ViewWriter
import com.lightningkite.kiteui.views.centered
import com.lightningkite.kiteui.views.direct.*
import com.lightningkite.kiteui.views.expanding
import com.lightningkite.kiteui.views.fieldTheme
import com.lightningkite.kiteui.views.l2.errorText
import com.lightningkite.kiteui.views.l2.field
import com.lightningkite.kiteui.views.direct.icon
import com.lightningkite.kiteui.views.l2.LabelGapSemantic
import com.lightningkite.kiteui.views.l2.LabelSemantic
import com.lightningkite.kiteui.views.themed
import com.lightningkite.reactive.core.Constant
import com.lightningkite.reactive.core.MutableReactive
import com.lightningkite.reactive.core.Reactive
import com.lightningkite.services.database.Condition
import com.lightningkite.services.database.SerializableAnnotation
import kotlinx.serialization.KSerializer
import kotlinx.serialization.serializer
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

// by Claude
@OptIn(ExperimentalContracts::class)
internal inline fun ElementWriter.fieldWithoutBorder(label: String, description: String? = null, content: ViewWriter.() -> Unit) {
    contract { callsInPlace(content, InvocationKind.EXACTLY_ONCE) }
    col {
        themeChoice += LabelGapSemantic
        row {
            gap = 0.25.rem
            centered.themed(LabelSemantic).text(label)
            if (description != null) {
                centered.textPopover(description).icon(Icon.info.resize(1.rem), "Info")
            }
        }
        content()
        errorText()
    }
}

fun ElementWriter.CanAddTheme.subrendererSelector(
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
inline fun ElementWriter.fieldWithDescription(label: String, description: String?, content: ElementWriter.CanAddTheme.() -> Unit) {
    if (description == null) {
        // No description - use standard field layout
        field(label, content)
    } else {
        // Has description - show info icon next to label
        col {
            themeChoice += LabelGapSemantic
            row {
                gap = 0.25.rem
                themed(LabelSemantic).text(label)
                textPopover(description).icon(Icon.info.resize(1.rem), "Info")
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
fun <T> ElementWriter.CanAddTheme.view(
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
inline fun <reified T> ElementWriter.CanAddTheme.form(module: FormModule, value: MutableReactive<T>) = form(module, serializer(), value)

/**
 * Render a read-only view of a value.
 */
inline fun <reified T> ElementWriter.CanAddTheme.view(module: FormModule, value: Reactive<T>) = view(module, serializer(), value)

/**
 * Render a compact editable cell (for tables).
 */
inline fun <reified T> ElementWriter.CanAddTheme.cellForm(module: FormModule, value: MutableReactive<T>) = cellForm(module, serializer(), value)

/**
 * Render a compact read-only cell (for tables).
 */
inline fun <reified T> ElementWriter.CanAddTheme.cellView(module: FormModule, value: Reactive<T>) = cellView(module, serializer(), value)

/**
 * Render an editable form with its label.
 *
 * When [FormModule.enableRendererSwitching] is true, shows a dropdown to switch renderers.
 *
 * @param description Optional description text shown via info icon popover
 */
inline fun <reified T> ElementWriter.CanAddTheme.labeledForm(module: FormModule, value: MutableReactive<T>, label: String, description: String? = null) = labeledForm(module, serializer(), value, label, description)

/**
 * Render a read-only view with its label.
 *
 * When [FormModule.enableRendererSwitching] is true, shows a dropdown to switch renderers.
 *
 * @param description Optional description text shown via info icon popover
 */
inline fun <reified T> ElementWriter.CanAddTheme.labeledView(module: FormModule, value: Reactive<T>, label: String, description: String? = null) = labeledView(module, serializer(), value, label, description)


/**
 * Render an editable form for a value.
 */
fun <T> ElementWriter.CanAddTheme.form(module: FormModule, serializer: KSerializer<T>, value: MutableReactive<T>) {
    val context = RenderContext(serializer)
    module.form(context, value)(this)
}

/**
 * Render a read-only view of a value.
 */
fun <T> ElementWriter.CanAddTheme.view(module: FormModule, serializer: KSerializer<T>, value: Reactive<T>) {
    val context = RenderContext(serializer)
    module.view(context, value)(this)
}

/**
 * Render a compact editable cell (for tables).
 */
fun <T> ElementWriter.CanAddTheme.cellForm(module: FormModule, serializer: KSerializer<T>, value: MutableReactive<T>) {
    val context = RenderContext(serializer)
    module.cellForm(context, value)(this)
}

/**
 * Render a compact read-only cell (for tables).
 */
fun <T> ElementWriter.CanAddTheme.cellView(module: FormModule, serializer: KSerializer<T>, value: Reactive<T>) {
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
fun <T> ElementWriter.CanAddTheme.labeledForm(module: FormModule, serializer: KSerializer<T>, value: MutableReactive<T>, label: String, description: String? = null) {
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
fun <T> ElementWriter.CanAddTheme.labeledView(module: FormModule, serializer: KSerializer<T>, value: Reactive<T>, label: String, description: String? = null) {
    val context = RenderContext(serializer)
    module.labeledViewWithSwitcher(context, value, label, description ?: context.description)(this)
}


/**
 * Converts a Condition into a human-readable string for display.
 * Used in the UI to show users what filters are currently applied.
 */
fun Condition<*>.friendly(): String {
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
