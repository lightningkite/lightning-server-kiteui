package com.lightningkite.kiteui.forms

import com.lightningkite.kiteui.models.Align
import com.lightningkite.kiteui.models.Icon
import com.lightningkite.kiteui.models.rem
import com.lightningkite.kiteui.views.ViewWriter
import com.lightningkite.kiteui.views.centered
import com.lightningkite.kiteui.views.direct.*
import com.lightningkite.kiteui.views.fieldTheme
import com.lightningkite.kiteui.views.l2.icon
import com.lightningkite.reactive.core.MutableReactive
import com.lightningkite.reactive.core.Reactive
import com.lightningkite.reactive.core.Constant
import com.lightningkite.reactive.lensing.lens
import kotlinx.serialization.descriptors.PrimitiveKind

// ===== Boolean Renderers =====
// by Claude

object BooleanRenderer : Renderer<Boolean> {
    override val name: String = "Checkbox"  // by Claude
    override fun form(context: RenderContext<Boolean>, value: MutableReactive<Boolean>, module: FormModule): ViewWriter.() -> Unit = {
        checkbox { checked bind value }
    }

    override fun view(context: RenderContext<Boolean>, value: Reactive<Boolean>, module: FormModule): ViewWriter.() -> Unit = {
        text { ::content { if (value()) "✓" else "✗" } }
    }

    override fun cellForm(context: RenderContext<Boolean>, value: MutableReactive<Boolean>, module: FormModule) = form(context, value, module)
    override fun columnWidth(context: RenderContext<Boolean>, module: FormModule) = 3.0

    // by Claude - Boolean renders better as inline row with checkbox + label
    override fun labeledForm(
        context: RenderContext<Boolean>,
        value: MutableReactive<Boolean>,
        module: FormModule,
        label: String,
        description: String?
    ): ViewWriter.() -> Unit = {
        row {
            centered.checkbox { checked bind value }
            centered.text(label)
            description?.let { desc ->
                centered.textPopover(desc).icon(Icon.info.copy(width = 1.rem, height = 1.rem), "Info")
            }
        }
    }

    override fun labeledView(
        context: RenderContext<Boolean>,
        value: Reactive<Boolean>,
        module: FormModule,
        label: String,
        description: String?
    ): ViewWriter.() -> Unit = {
        row {
            centered.text { ::content { if (value()) "✓" else "✗" } }
            centered.text(label)
            description?.let { desc ->
                centered.textPopover(desc).icon(Icon.info.copy(width = 1.rem, height = 1.rem), "Info")
            }
        }
    }
}

object NullableBooleanRenderer : Renderer<Boolean?> {
    override val name: String = "Yes/No/N/A"  // by Claude
    override fun form(context: RenderContext<Boolean?>, value: MutableReactive<Boolean?>, module: FormModule): ViewWriter.() -> Unit = {
        fieldTheme.select {
            bind(value, Constant(listOf(null, true, false))) {
                when (it) {
                    true -> "Yes"
                    false -> "No"
                    null -> "N/A"
                }
            }
        }
    }

    override fun view(context: RenderContext<Boolean?>, value: Reactive<Boolean?>, module: FormModule): ViewWriter.() -> Unit = {
        text {
            ::content {
                when (value()) {
                    true -> "✓"
                    false -> "✗"
                    null -> "—"
                }
            }
        }
    }

    override fun cellForm(context: RenderContext<Boolean?>, value: MutableReactive<Boolean?>, module: FormModule) = form(context, value, module)
    override fun columnWidth(context: RenderContext<Boolean?>, module: FormModule) = 5.0
}

// ===== String Renderers =====

object StringRenderer : Renderer<String> {
    override val name: String = "Text"  // by Claude
    override fun priority(context: RenderContext<String>, module: FormModule): Float {
        // Lower priority if @Multiline present (let MultilineStringRenderer win)
        return if (context.isMultiline) 0.5f else 1f
    }

    override fun form(context: RenderContext<String>, value: MutableReactive<String>, module: FormModule): ViewWriter.() -> Unit = {
        fieldTheme.textInput {
            content bind value
            context.hint?.let { hint = it }
        }
    }

    override fun view(context: RenderContext<String>, value: Reactive<String>, module: FormModule): ViewWriter.() -> Unit = {
        text { ::content { value() } }
    }

    override fun cellForm(context: RenderContext<String>, value: MutableReactive<String>, module: FormModule) = form(context, value, module)

    override fun columnWidth(context: RenderContext<String>, module: FormModule): Double {
        val maxLen = context.averageLength ?: 20
        return (maxLen.coerceAtMost(50) * 0.6).coerceAtLeast(8.0)
    }
}

object MultilineStringRenderer : Renderer<String> {
    override val name: String = "Multiline Text"  // by Claude
    override fun priority(context: RenderContext<String>, module: FormModule) = 2f

    override fun form(context: RenderContext<String>, value: MutableReactive<String>, module: FormModule): ViewWriter.() -> Unit = {
        fieldTheme.textArea {
            content bind value
            context.hint?.let { hint = it }
        }
    }

    override fun view(context: RenderContext<String>, value: Reactive<String>, module: FormModule): ViewWriter.() -> Unit = {
        text {
            ::content { value() }
            wraps = true
        }
    }

    // Cell view shows truncated first line
    override fun cellView(context: RenderContext<String>, value: Reactive<String>, module: FormModule): ViewWriter.() -> Unit = {
        text {
            ::content { value().substringBefore('\n') }
            wraps = false
            ellipsis = true
        }
    }

    override fun columnWidth(context: RenderContext<String>, module: FormModule) = 30.0
}

// ===== Number Renderers =====

object IntRenderer : Renderer<Int> {
    override val name: String = "Number"  // by Claude
    override fun form(context: RenderContext<Int>, value: MutableReactive<Int>, module: FormModule): ViewWriter.() -> Unit = {
        fieldTheme.numberInput {
            align = Align.End
            content bind value.lens(
                get = { it.toDouble() },
                modify = { old, new -> new?.toInt() ?: old }
            )
        }
    }

    override fun view(context: RenderContext<Int>, value: Reactive<Int>, module: FormModule): ViewWriter.() -> Unit = {
        text { ::content { value().toString() } }
    }

    override fun cellForm(context: RenderContext<Int>, value: MutableReactive<Int>, module: FormModule) = form(context, value, module)
    override fun columnWidth(context: RenderContext<Int>, module: FormModule) = 10.0
}

object NullableIntRenderer : Renderer<Int?> {
    override val name: String = "Number"  // by Claude
    override fun form(context: RenderContext<Int?>, value: MutableReactive<Int?>, module: FormModule): ViewWriter.() -> Unit = {
        fieldTheme.numberInput {
            align = Align.End
            content bind value.lens(
                get = { it?.toDouble() },
                set = { it?.toInt() }
            )
        }
    }

    override fun view(context: RenderContext<Int?>, value: Reactive<Int?>, module: FormModule): ViewWriter.() -> Unit = {
        text { ::content { value()?.toString() ?: "—" } }
    }

    override fun cellForm(context: RenderContext<Int?>, value: MutableReactive<Int?>, module: FormModule) = form(context, value, module)
    override fun columnWidth(context: RenderContext<Int?>, module: FormModule) = 10.0
}

object LongRenderer : Renderer<Long> {
    override val name: String = "Number"  // by Claude
    override fun form(context: RenderContext<Long>, value: MutableReactive<Long>, module: FormModule): ViewWriter.() -> Unit = {
        fieldTheme.numberInput {
            align = Align.End
            content bind value.lens(
                get = { it.toDouble() },
                modify = { old, new -> new?.toLong() ?: old }
            )
        }
    }

    override fun view(context: RenderContext<Long>, value: Reactive<Long>, module: FormModule): ViewWriter.() -> Unit = {
        text { ::content { value().toString() } }
    }

    override fun cellForm(context: RenderContext<Long>, value: MutableReactive<Long>, module: FormModule) = form(context, value, module)
    override fun columnWidth(context: RenderContext<Long>, module: FormModule) = 12.0
}

object NullableLongRenderer : Renderer<Long?> {
    override val name: String = "Number"  // by Claude
    override fun form(context: RenderContext<Long?>, value: MutableReactive<Long?>, module: FormModule): ViewWriter.() -> Unit = {
        fieldTheme.numberInput {
            align = Align.End
            content bind value.lens(
                get = { it?.toDouble() },
                set = { it?.toLong() }
            )
        }
    }

    override fun view(context: RenderContext<Long?>, value: Reactive<Long?>, module: FormModule): ViewWriter.() -> Unit = {
        text { ::content { value()?.toString() ?: "—" } }
    }

    override fun cellForm(context: RenderContext<Long?>, value: MutableReactive<Long?>, module: FormModule) = form(context, value, module)
    override fun columnWidth(context: RenderContext<Long?>, module: FormModule) = 12.0
}

object DoubleRenderer : Renderer<Double> {
    override val name: String = "Decimal"  // by Claude
    override fun form(context: RenderContext<Double>, value: MutableReactive<Double>, module: FormModule): ViewWriter.() -> Unit = {
        fieldTheme.numberInput {
            align = Align.End
            content bind value.lens(
                get = { it },
                modify = { old, new -> new ?: old }
            )
        }
    }

    override fun view(context: RenderContext<Double>, value: Reactive<Double>, module: FormModule): ViewWriter.() -> Unit = {
        text { ::content { value().toString() } }
    }

    override fun cellForm(context: RenderContext<Double>, value: MutableReactive<Double>, module: FormModule) = form(context, value, module)
    override fun columnWidth(context: RenderContext<Double>, module: FormModule) = 12.0
}

object NullableDoubleRenderer : Renderer<Double?> {
    override val name: String = "Decimal"  // by Claude
    override fun form(context: RenderContext<Double?>, value: MutableReactive<Double?>, module: FormModule): ViewWriter.() -> Unit = {
        fieldTheme.numberInput {
            align = Align.End
            content bind value
        }
    }

    override fun view(context: RenderContext<Double?>, value: Reactive<Double?>, module: FormModule): ViewWriter.() -> Unit = {
        text { ::content { value()?.toString() ?: "—" } }
    }

    override fun cellForm(context: RenderContext<Double?>, value: MutableReactive<Double?>, module: FormModule) = form(context, value, module)
    override fun columnWidth(context: RenderContext<Double?>, module: FormModule) = 12.0
}

// ===== Registration =====

fun FormModule.registerPrimitives() {
    // Boolean
    register(Selector(type = "kotlin.Boolean"), BooleanRenderer)
    register(Selector(type = "kotlin.Boolean?"), NullableBooleanRenderer)

    // String
    register(Selector(type = "kotlin.String"), StringRenderer)
    register(Selector(type = "kotlin.String", annotation = Annotations.Multiline), MultilineStringRenderer)

    // Numbers
    register(Selector(type = "kotlin.Int"), IntRenderer)
    register(Selector(type = "kotlin.Int?"), NullableIntRenderer)
    register(Selector(type = "kotlin.Long"), LongRenderer)
    register(Selector(type = "kotlin.Long?"), NullableLongRenderer)
    register(Selector(type = "kotlin.Double"), DoubleRenderer)
    register(Selector(type = "kotlin.Double?"), NullableDoubleRenderer)
}
