package com.lightningkite.kiteui.forms

import com.lightningkite.kiteui.reactive.Readable
import com.lightningkite.kiteui.reactive.lens
import com.lightningkite.kiteui.views.ViewWriter
import com.lightningkite.serialization.SerializableProperty
import com.lightningkite.serialization.WrappingSerializer


object WrapperViewRenderer: ViewRenderer.Generator {
    override val name: String = "Wrapper"
    override fun matches(selector: FormSelector<*>): Boolean {
        return selector.serializer is WrappingSerializer<*, *>
    }
    override fun <T> view(selector: FormSelector<T>): ViewRenderer<T> {
        val innerSer = (selector.serializer as WrappingSerializer<T, Any?>)
        val inner = ViewRenderer[selector.copy(serializer = innerSer.getDeferred())]
        return ViewRenderer<T>(this, selector, size = inner.size, handlesField = inner.handlesField) { field, writable ->
            inner.render(this, field, writable.lens(get = innerSer::inner))
        }
    }
}