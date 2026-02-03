package com.lightningkite.kiteui.forms2

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
import com.lightningkite.reactive.core.Signal
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
        sizeConstraints(width = 8.rem).select {
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
