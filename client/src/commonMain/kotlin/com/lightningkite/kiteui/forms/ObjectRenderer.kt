package com.lightningkite.kiteui.forms

import com.lightningkite.kiteui.reactive.Readable
import com.lightningkite.kiteui.reactive.Writable
import com.lightningkite.kiteui.views.ViewWriter
import com.lightningkite.kiteui.views.direct.stack
import com.lightningkite.serialization.SerializableProperty
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.StructureKind

object ObjectRenderer: FormRenderer.Generator, ViewRenderer.Generator {
    override val kind = StructureKind.OBJECT
    override val name: String = "Object"

    override fun <T> view(module: FormModule, selector: FormSelector<T>): ViewRenderer<T> = ViewRenderer<T>(module, this, selector) { _, _ -> stack {} }
    override fun <T> form(module: FormModule, selector: FormSelector<T>): FormRenderer<T> = FormRenderer<T>(module, this, selector) { _, _ -> stack {} }
}