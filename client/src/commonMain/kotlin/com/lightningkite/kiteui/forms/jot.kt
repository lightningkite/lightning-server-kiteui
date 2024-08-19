//package com.lightningkite.kiteui.forms
//
//import kotlinx.serialization.KSerializer
//import com.lightningkite.kiteui.*
//import com.lightningkite.kiteui.reactive.Writable
//import com.lightningkite.kiteui.reactive.bind
//import com.lightningkite.kiteui.reactive.lens
//import com.lightningkite.kiteui.views.ViewWriter
//import com.lightningkite.kiteui.views.direct.numberField
//import com.lightningkite.kiteui.views.fieldTheme
//import kotlinx.serialization.builtins.ListSerializer
//import kotlinx.serialization.builtins.serializer
//import kotlin.reflect.KClass
//
//
//typealias SerializableAnnotation = String
//
//object FormRenderers {
//    val overrides =
//        HashMap<String, ViewWriter.(annotations: List<SerializableAnnotation>, inner: List<KSerializer<*>>, prop: Writable<*>) -> Unit>()
//    fun <T> override(serializer: KSerializer<T>, processor: ViewWriter.(annotations: List<SerializableAnnotation>, inner: List<KSerializer<*>>, prop: Writable<T>) -> Unit) {
//        @Suppress("UNCHECKED_CAST")
//        overrides[serializer.descriptor.serialName] = processor as ViewWriter.(annotations: List<SerializableAnnotation>, inner: List<KSerializer<*>>, prop: Writable<*>) -> Unit
//    }
//
//    init {
////        genericOverrides[ListSerializer(Unit.serializer())::class] = {
////
////        }
//        override(String.serializer()) { annos, inner, prop ->
//            fieldTheme - numberField {
//                content bind prop.lens(get = { it.toDouble() }, set = { it?.toInt() ?: 0 })
//            }
//        }
//        override(Int.serializer()) { annos, inner, prop ->
//            fieldTheme - numberField {
//                content bind prop.lens(get = { it.toDouble() }, set = { it?.toInt() ?: 0 })
//            }
//        }
//    }
//}
//
//fun <T> ViewWriter.display(annotations: List<SerializableAnnotation>, serializer: KSerializer<T>) {
//
//}
//
