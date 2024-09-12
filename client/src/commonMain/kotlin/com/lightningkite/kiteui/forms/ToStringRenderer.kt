package com.lightningkite.kiteui.forms

import com.lightningkite.kiteui.reactive.Readable
import com.lightningkite.kiteui.reactive.invoke
import com.lightningkite.kiteui.views.ViewWriter
import com.lightningkite.kiteui.views.direct.text
import com.lightningkite.serialization.SerializableProperty

object ToStringRenderer: ViewRenderer.Generator {
    override fun <T> view(selector: FormSelector<T>): ViewRenderer<T> = ViewRenderer(this, selector) { _, readable ->
        text { ::content { readable.invoke().toString() } }
    }
    override val name: String
        get() = "As Text"
    override fun size(selector: FormSelector<*>): FormSize = FormSize.Inline
    override val basePriority: Float get() = 0.2f
}