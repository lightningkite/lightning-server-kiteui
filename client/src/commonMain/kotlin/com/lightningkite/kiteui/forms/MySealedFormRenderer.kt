package com.lightningkite.kiteui.forms

import com.lightningkite.kiteui.models.rem
import com.lightningkite.kiteui.reactive.Constant
import com.lightningkite.kiteui.reactive.Writable
import com.lightningkite.kiteui.reactive.lens
import com.lightningkite.kiteui.reactive.reactive
import com.lightningkite.kiteui.views.ViewWriter
import com.lightningkite.kiteui.views.atTop
import com.lightningkite.kiteui.views.direct.row
import com.lightningkite.kiteui.views.direct.select
import com.lightningkite.kiteui.views.direct.sizeConstraints
import com.lightningkite.kiteui.views.direct.stack
import com.lightningkite.kiteui.views.expanding
import com.lightningkite.kiteui.views.fieldTheme
import com.lightningkite.lightningdb.MySealedClassSerializerInterface
import com.lightningkite.serialization.SerializableProperty
import com.lightningkite.serialization.default
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.SerialKind
import kotlinx.serialization.descriptors.StructureKind

@Suppress("UNCHECKED_CAST")
object MySealedFormRenderer : FormRenderer.Generator {
    override val name: String = "Options"
    override val kind: SerialKind = StructureKind.CLASS
    override fun size(selector: FormSelector<*>): FormSize = FormSize.Block
    override fun matches(selector: FormSelector<*>): Boolean {
        return selector.serializer is MySealedClassSerializerInterface<*>
    }

    override fun <T> form(selector: FormSelector<T>): FormRenderer<T> {
        val serializer = selector.serializer as MySealedClassSerializerInterface<Any>
        return FormRenderer(this, selector as FormSelector<Any>) { _, writable ->
            val type = writable.lens(
                get = { serializer.options.find { o -> o.isInstance(it) } ?: serializer.options.first() },
                set = { it.serializer.default() }
            )
            row {
                atTop - sizeConstraints(width = 10.rem) - fieldTheme - select {
                    bind(type, Constant(serializer.options)) { it.serializer.displayName }
                }
                expanding - stack {
                    reactive {
                        val type = type()
                        clearChildren()
                        @Suppress("UNCHECKED_CAST")
                        (form(type.serializer as KSerializer<Any>, writable.lens(
                            get = { if (type.isInstance(it)) it else type.serializer.default() },
                            set = { it }
                        )))
                    }
                }
            }
        } as FormRenderer<T>
    }
}