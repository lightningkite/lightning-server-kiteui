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
) {
    companion object {
        val Inline = FormSize(12.0, 1.0)
        val Block = FormSize(100.0, 5.0)
    }
}
data class FormLayoutPreferences(
    val approximateWidthBound: Double? = null,
    val approximateHeightBound: Double? = null,
) {
    companion object {
        val Inline = FormLayoutPreferences(approximateWidthBound = 12.0, approximateHeightBound = null)
        val Block = FormLayoutPreferences(approximateWidthBound = null, approximateHeightBound = 12.0)
        val Bound = FormLayoutPreferences(approximateWidthBound = 12.0, approximateHeightBound = 2.0)
        val Unbound = FormLayoutPreferences(approximateWidthBound = null, approximateHeightBound = null)
    }
}

val SerializableProperty<*, *>.displayName: String
    get() = this.serializableAnnotations.find { it.fqn == "com.lightningkite.lightningdb.DisplayName" }?.values?.get(
        "text"
    )?.let { it as? SerializableAnnotationValue.StringValue }?.value ?: name.titleCase()
val KSerializer<*>.displayName: String
    get() = serializableAnnotations.find { it.fqn == "com.lightningkite.lightningdb.DisplayName" }?.values?.get(
        "text"
    )?.let { it as? SerializableAnnotationValue.StringValue }?.value ?: descriptor.serialName.substringBefore('<').substringAfterLast('.').titleCase()

val SerializableProperty<*, *>.description: String?
    get() = this.serializableAnnotations.find { it.fqn == "com.lightningkite.lightningdb.Description" }?.values?.get(
        "text"
    )?.let { it as? SerializableAnnotationValue.StringValue }?.value
val KSerializer<*>.description: String?
    get() = serializableAnnotations.find { it.fqn == "com.lightningkite.lightningdb.Description" }?.values?.get(
        "text"
    )?.let { it as? SerializableAnnotationValue.StringValue }?.value

val SerializableProperty<*, *>.descriptionOrDisplayName get() = description ?: displayName
val SerializableProperty<*, *>.hint
    get() = serializableAnnotations.find { it.fqn == "com.lightningkite.lightningdb.Hint" }?.values?.get(
        "text"
    )?.let { it as? SerializableAnnotationValue.StringValue }?.value
        ?: description
        ?: displayName

val SerializableProperty<*, *>.group get() = serializableAnnotations.find {
    it.fqn == "com.lightningkite.lightningdb.Group"
}?.values?.values?.first()?.let {
    it as? SerializableAnnotationValue.StringValue
}?.value
val SerializableProperty<*, *>.sentence get() = serializableAnnotations.find {
    it.fqn == "com.lightningkite.lightningdb.Sentence"
}?.values?.values?.first()?.let {
    it as? SerializableAnnotationValue.StringValue
}?.value
val SerializableProperty<*, *>.importance get() = serializableAnnotations.find {
    it.fqn == "com.lightningkite.lightningdb.Importance"
}?.values?.values?.first()?.let {
    it as? SerializableAnnotationValue.ByteValue
}?.value?.toInt() ?: if(name == "title") 1 else 7
val SerializableProperty<*, *>.visibility get() = when {
    serializableAnnotations.any {
        it.fqn == "com.lightningkite.lightningdb.AdminHidden"
    } -> FieldVisibility.HIDDEN
    serializableAnnotations.any {
        it.fqn == "com.lightningkite.lightningdb.Denormalized"
    } -> FieldVisibility.READ
    serializableAnnotations.any {
        it.fqn == "com.lightningkite.lightningdb.AdminViewOnly"
    } -> FieldVisibility.READ
    else -> FieldVisibility.EDIT
}
val SerializableProperty<*, *>.doesNotNeedLabel get() = serializableAnnotations.any {
    it.fqn == "com.lightningkite.lightningdb.DoesNotNeedLabel"
}
val SerializableProperty<*, *>.indexed get() = serializableAnnotations.any {
    it.fqn == "com.lightningkite.lightningdb.Index"
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
    field?.importance?.let {
        when(it) {
            in 1..6 -> HeaderSizeSemantic(it).onNext
            7 -> {}
            8 -> SubtextSemantic.onNext
            else -> {}
        }
    }
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
