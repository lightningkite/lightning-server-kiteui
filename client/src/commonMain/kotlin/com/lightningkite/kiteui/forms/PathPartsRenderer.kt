package com.lightningkite.kiteui.forms

import com.lightningkite.kiteui.views.direct.col
import com.lightningkite.kiteui.views.direct.row
import com.lightningkite.kiteui.views.direct.select
import com.lightningkite.kiteui.views.fieldTheme
import com.lightningkite.kiteui.views.forEachUpdating
import com.lightningkite.reactive.context.invoke
import com.lightningkite.reactive.core.Constant
import com.lightningkite.reactive.core.ReactiveValue
import com.lightningkite.reactive.core.remember
import com.lightningkite.reactive.extensions.flatten
import com.lightningkite.reactive.extensions.withWrite
import com.lightningkite.reactive.lensing.MutableReactiveElement
import com.lightningkite.reactive.lensing.lens
import com.lightningkite.reactive.lensing.lensByElementAssumingSetNeverManipulates
import com.lightningkite.services.database.*
import kotlinx.serialization.builtins.serializer

object PathPartsRenderer : FormRenderer.Generator {
    override val name: String = "Path Parts"
    override val type: String? = DataClassPathSerializer(Unit.serializer()).descriptor.serialName

    @Suppress("UNCHECKED_CAST")
    override fun <T> form(module: FormModule, selector: FormSelector<T>): FormRenderer<T> {
        val serializer = selector.serializer as DataClassPathSerializer<Any??>
        return FormRenderer(module, this, selector as FormSelector<DataClassPathPartial<Any?>>){ field, mutable ->
            val properties = mutable.lens(
                get = { it.properties },
                set = {
                    var out: DataClassPath<Any?, Any?> = DataClassPathSelf(serializer.inner)
                    var lastNullable = false
                    for (prop in it) {
                        if (lastNullable) {
                            @Suppress("UNCHECKED_CAST")
                            out = DataClassPathAccess<Any?, Any, Any?>(
                                DataClassPathNotNull(out),
                                prop as SerializableProperty<Any, Any?>
                            )
                        } else {
                            @Suppress("UNCHECKED_CAST")
                            out = DataClassPathAccess<Any?, Any, Any?>(
                                out as DataClassPath<Any?, Any>,
                                prop as SerializableProperty<Any, Any?>
                            )
                        }
                    }
                    out
                }
            )
            col {
//                    text { ::content { properties().joinToString(", ") { it.name} }}
                row {
                    forEachUpdating(properties.lensByElementAssumingSetNeverManipulates().lens {
                        it + object : MutableReactiveElement<SerializableProperty<*, *>?> {
                            override val index: ReactiveValue<Int> get() = Constant(it.size)
                            override val value: SerializableProperty<*, *>? = null
                            override fun addListener(listener: () -> Unit): () -> Unit = {}
                            override suspend fun set(value: SerializableProperty<*, *>?) {
                                if (value != null) {
                                    properties.set(properties() + value)
                                }
                            }
                        }
                    }) {
                        fieldTheme - select {
                            val options = remember {
                                (properties().getOrNull(it().index() - 1)
                                    ?.let {
                                        it.serializer.let { it.nullElement() ?: it }.serializableProperties
                                            ?: arrayOf()
                                    }
                                    ?: serializer.inner.serializableProperties ?: arrayOf()).toList().let {
                                    listOf(null) + it
                                }
                            }
                            ::opacity {
                                if (options().size == 1) 0.0 else 1.0
                            }
                            bind(
                                edits = it.flatten().withWrite { value ->
                                    properties.set(properties().take(it().index()) + (value?.let { listOf(it) }
                                        ?: listOf()))
                                },
                                data = options,
                                render = { it?.displayName ?: "+" }
                            )
                        }
                    }
                }
            }
        } as FormRenderer<T>
    }
}