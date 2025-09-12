package com.lightningkite.kiteui.forms

import com.lightningkite.services.database.WrappingSerializer

object WrapperFormRenderer: FormRenderer.Generator {
    override val name: String = "Wrapper"
    override fun matches(module: FormModule, selector: FormSelector<*>): Boolean {
        return selector.serializer is WrappingSerializer<*, *>
    }
    override fun <T> form(module: FormModule, selector: FormSelector<T>): FormRenderer<T> {
        val innerSer = (selector.serializer as WrappingSerializer<T, Any?>)
        val inner = module.form(selector.copy(serializer = innerSer.getDeferred()))
        return FormRenderer<T>(module, this, selector, size = inner.size, handlesField = inner.handlesField) { field, mutable ->
            inner.render(this, field, mutable.lens(get = innerSer::inner, set = innerSer::outer))
        }
    }
}