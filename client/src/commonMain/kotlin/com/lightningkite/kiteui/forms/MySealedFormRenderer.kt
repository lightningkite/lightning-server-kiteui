package com.lightningkite.kiteui.forms

import com.lightningkite.kiteui.models.rem
import com.lightningkite.kiteui.views.atTop
import com.lightningkite.kiteui.views.direct.frame
import com.lightningkite.kiteui.views.direct.row
import com.lightningkite.kiteui.views.direct.select
import com.lightningkite.kiteui.views.direct.sizeConstraints
import com.lightningkite.kiteui.views.expanding
import com.lightningkite.kiteui.views.fieldTheme
import com.lightningkite.services.database.MySealedClassSerializerInterface
import com.lightningkite.reactive.context.reactive
import com.lightningkite.reactive.core.Constant
import com.lightningkite.services.database.default
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.SerialKind
import kotlinx.serialization.descriptors.StructureKind

@Suppress("UNCHECKED_CAST")
object MySealedFormRenderer : FormRenderer.Generator {
    override val name: String = "Options"
    override val kind: SerialKind = StructureKind.CLASS
    override fun size(module: FormModule, selector: FormSelector<*>): FormSize = FormSize.Block
    override fun matches(module: FormModule, selector: FormSelector<*>): Boolean {
        return selector.serializer is MySealedClassSerializerInterface<*>
    }

    override fun <T> form(module: FormModule, selector: FormSelector<T>): FormRenderer<T> {
        val serializer = selector.serializer as MySealedClassSerializerInterface<Any>
        return FormRenderer(module, this, selector as FormSelector<Any>) { _, mutable ->
            val type = mutable.lens(
                get = { serializer.options.find { o -> o.isInstance(it) } ?: serializer.options.first() },
                set = { it.serializer.default() }
            )
            row {
                atTop.sizeConstraints(width = 10.rem).fieldTheme.select {
                    bind(type, Constant(serializer.options)) { it.serializer.displayName }
                }
                expanding.frame {
                    reactive {
                        val type = type()
                        clearChildren()
                        @Suppress("UNCHECKED_CAST")
                        (form(
                            module, type.serializer as KSerializer<Any>, mutable.lens(
                                get = { if (type.isInstance(it)) it else type.serializer.default() },
                                set = { it }
                            )))
                    }
                }
            }
        } as FormRenderer<T>
    }
}