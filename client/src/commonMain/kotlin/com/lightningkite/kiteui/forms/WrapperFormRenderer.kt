package com.lightningkite.kiteui.forms

import com.lightningkite.kiteui.reactive.Writable
import com.lightningkite.kiteui.reactive.lens
import com.lightningkite.kiteui.views.ViewWriter
import com.lightningkite.serialization.SerializableProperty
import com.lightningkite.serialization.WrappingSerializer

object WrapperFormRenderer: FormRenderer.Generator {
    override val name: String = "Wrapper"
    override fun matches(module: FormModule, selector: FormSelector<*>): Boolean {
        return selector.serializer is WrappingSerializer<*, *>
    }
    override fun <T> form(module: FormModule, selector: FormSelector<T>): FormRenderer<T> {
        val innerSer = (selector.serializer as WrappingSerializer<T, Any?>)
        val inner = module.form(selector.copy(serializer = innerSer.getDeferred()))
        return FormRenderer<T>(module, this, selector, size = inner.size, handlesField = inner.handlesField) { field, writable ->
            inner.render(this, field, writable.lens(get = innerSer::inner, set = innerSer::outer))
        }
    }
}