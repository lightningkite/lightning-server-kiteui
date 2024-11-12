@file:OptIn(ExperimentalSerializationApi::class)

package com.lightningkite.kiteui.forms

import com.lightningkite.*
import com.lightningkite.kiteui.models.*
import kotlinx.serialization.KSerializer
import com.lightningkite.kiteui.views.*
import com.lightningkite.kiteui.views.direct.*
import com.lightningkite.serialization.*
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.*


data class FormSize(
    val approximateWidth: Double,
    val approximateHeight: Double,
    val widthGrowWillingness: Double = 0.5,
    val heightGrowWillingness: Double = 0.5,
) {
    init {
        if(approximateWidth < 0.0) throw Exception("WAT")
    }
    companion object {
        val Inline = FormSize(12.0, 1.0)
        val Block = FormSize(20.0, 5.0)
    }
}
enum class FormLayoutPreferences(
    val approximateWidthBound: Double? = null,
    val approximateHeightBound: Double? = null,
) {
    Inline(approximateWidthBound = 12.0, approximateHeightBound = null),
    Block(approximateWidthBound = null, approximateHeightBound = 12.0),
    Field(approximateWidthBound = 12.0, approximateHeightBound = 2.0),
    Unbound(approximateWidthBound = null, approximateHeightBound = null),
}

object GenericPlaceholderSerializer: KSerializer<Any?> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("Placeholder", PrimitiveKind.STRING)
    override fun deserialize(decoder: Decoder): Any? = null
    override fun serialize(encoder: Encoder, value: Any?) {}
}
object GenericNotNullPlaceholderSerializer: KSerializer<Any> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("Placeholder", PrimitiveKind.STRING)
    override fun deserialize(decoder: Decoder): Any = 0
    override fun serialize(encoder: Encoder, value: Any) {}
}

@ViewDsl
fun ViewWriter.defaultFieldWrapper(field: SerializableProperty<*, *>? = null, inner: ViewWriter.() -> Unit) {
//    field?.importance?.let {
//        when(it) {
//            in 1..6 -> HeaderSizeSemantic(it).onNext
//            7 -> {}
//            8 -> SubtextSemantic.onNext
//            else -> {}
//        }
//    }
    if (field == null || field.doesNotNeedLabel) inner()
    else field.sentence?.let {
        val before = it.substringBefore('_')
        val after = it.substringAfter('_')
        atBottom - row {
            spacing = 0.3.rem
            if(before.isNotBlank()) {
                centered - text(before)
            }
            if(before.isBlank() || after.isBlank()) expanding
            inner()
            if(after.isNotBlank()) {
                centered - text(after)
            }
        }
    } ?: col {
        spacing = 0.px
        subtext(field.displayName)
        inner()
    }
}
