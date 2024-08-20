@file:OptIn(ExperimentalSerializationApi::class)

package com.lightningkite.kiteui.forms

import com.lightningkite.*
import kotlinx.serialization.KSerializer
import com.lightningkite.kiteui.models.px
import com.lightningkite.kiteui.reactive.*
import com.lightningkite.kiteui.views.ViewWriter
import com.lightningkite.kiteui.views.card
import com.lightningkite.kiteui.views.direct.*
import com.lightningkite.kiteui.views.expanding
import com.lightningkite.kiteui.views.fieldTheme
import com.lightningkite.lightningdb.*
import kotlinx.datetime.*
import kotlinx.serialization.ContextualSerializer
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.*
import kotlinx.serialization.modules.EmptySerializersModule
import kotlinx.serialization.modules.SerializersModule
import kotlin.time.Duration.Companion.seconds

object FormRenderers {
    var module = ClientModule

    class Handler<T>(
        val default: T? = null,
        val render: ViewWriter.(annotations: Array<SerializableAnnotation>, inner: Array<KSerializer<*>>, prop: Writable<T>) -> Unit
    )

    val annotationOverrides = HashMap<Pair<String, String>, Handler<*>>()
    val overrides = HashMap<String, Handler<*>>()

    fun <T> override(
        serializer: KSerializer<T>,
        processor: ViewWriter.(annotations: Array<SerializableAnnotation>, inner: Array<KSerializer<*>>, prop: Writable<T>) -> Unit
    ) {
        val handler = Handler(render = processor)
        @Suppress("UNCHECKED_CAST")
        overrides[serializer.descriptor.serialName] = handler
    }

    init {
        override(String.serializer()) { annos, inner, prop ->
            fieldTheme - textField {
                content bind prop
            }
        }
        override(Int.serializer()) { annos, inner, prop ->
            fieldTheme - numberField {
                content bind prop.lens(get = { it: Int -> it.toDouble() }, set = { it: Double? -> it?.toInt() ?: 0 })
            }
        }
    }
}

fun <T> ViewWriter.form(serializer: KSerializer<T>, writable: Writable<T>, annotations: Array<SerializableAnnotation>) {
    @Suppress("UNCHECKED_CAST")
    if (serializer is ContextualSerializer<*>) return form<Any?>(
        serializer as KSerializer<Any?>,
        writable as Writable<Any?>,
        annotations
    )

    @Suppress("UNCHECKED_CAST")
    val override = annotations.asSequence().map { FormRenderers.annotationOverrides[serializer.descriptor.serialName to it.fqn] } as? FormRenderers.Handler<T>
        ?: FormRenderers.overrides[serializer.descriptor.serialName] as? FormRenderers.Handler<T>
    override?.let {
        it.render.invoke(this, annotations, serializer.tryTypeParameterSerializers3() ?: arrayOf(), writable)
        return
    }

    serializer.nullElement()?.let {
        row {
            var ifNotNull = writable.state.getOrNull() ?: override?.default ?: it.default()
            checkbox {
                checked bind writable.lens(
                    get = { v -> v != null },
                    modify = { e, v ->
                        if (v) (ifNotNull ?: e ?: override?.default ?: it.default()) as T else null as T
                    },
                )
            }
            expanding - onlyWhen { writable() != null } - form<Any>(
                it as KSerializer<Any>,
                writable.lens(
                    get = { v -> v ?: override?.default ?: it.default() },
                    modify = { e, v ->
                        ifNotNull = v
                        if (e == null) null as T else v as T
                    },
                ),
                annotations
            )
        }
        return
    }

    when (serializer.descriptor.kind) {
        PrimitiveKind.BOOLEAN -> TODO()
        PrimitiveKind.BYTE -> TODO()
        PrimitiveKind.CHAR -> TODO()
        PrimitiveKind.DOUBLE -> TODO()
        PrimitiveKind.FLOAT -> TODO()
        PrimitiveKind.INT -> TODO()
        PrimitiveKind.LONG -> TODO()
        PrimitiveKind.SHORT -> TODO()
        PrimitiveKind.STRING -> TODO()
        SerialKind.CONTEXTUAL -> TODO()
        SerialKind.ENUM -> TODO()
        StructureKind.CLASS -> {
            serializer.serializableProperties?.let {
                card - col {
                    text(serializer.serializableAnnotations.find { it.fqn == "com.lightningkite.lightningdb.Description" }?.values?.get("text")?.let { it as? SerializableAnnotationValue.StringValue }?.value ?: serializer.descriptor.serialName)
//                    text(serializer.descriptor.ser.filterIsInstance<Description>().firstOrNull()?.text ?: serializer.descriptor.serialName)
                    for (field in it) {
                        @Suppress("UNCHECKED_CAST")
                        val w = writable.lensPath(
                            DataClassPathAccess<T, T, Any?>(
                                DataClassPathSelf(serializer),
                                field as SerializableProperty<T, Any?>
                            )
                        )
                        col {
                            spacing = 0.px
                            subtext(field.name)
                            form(field.serializer, w, arrayOf())
                        }
                    }
                }
            } ?: run {
                text("???")
            }
        }

        StructureKind.LIST -> TODO()
        StructureKind.MAP -> TODO()
        else -> {}
    }
}