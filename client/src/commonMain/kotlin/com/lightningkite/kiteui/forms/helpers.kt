@file:OptIn(ExperimentalSerializationApi::class)

package com.lightningkite.kiteui.forms

import com.lightningkite.kiteui.models.px
import com.lightningkite.kiteui.models.rem
import com.lightningkite.kiteui.views.*
import com.lightningkite.kiteui.views.direct.col
import com.lightningkite.kiteui.views.direct.row
import com.lightningkite.kiteui.views.direct.subtext
import com.lightningkite.kiteui.views.direct.text
import com.lightningkite.services.database.SerializableProperty
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder


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
    Block(approximateWidthBound = 100.0, approximateHeightBound = 12.0),
    Field(approximateWidthBound = 12.0, approximateHeightBound = 2.0),
    Unbound(approximateWidthBound = null, approximateHeightBound = null),
    ScreenBound(approximateWidthBound = 100.0, approximateHeightBound = null),
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
        atBottom.row {
            gap = 0.3.rem
            if(before.isNotBlank()) {
                centered.text(before)
            }
            if(before.isBlank() || after.isBlank()) expanding
            inner()
            if(after.isNotBlank()) {
                centered.text(after)
            }
        }
    } ?: col {
        gap = 0.px
        subtext(field.displayName)
        inner()
    }
}