package com.lightningkite.kiteui.forms

import com.lightningkite.services.database.SerializableAnnotationValue
import com.lightningkite.services.database.SerializableProperty
import com.lightningkite.services.database.serializableAnnotations
import com.lightningkite.titleCase
import kotlinx.serialization.KSerializer

val SerializableProperty<*, *>.displayName: String
    get() = this.serializableAnnotations.find { it.fqn == "com.lightningkite.services.data.DisplayName" }?.values?.get(
        "text"
    )?.let { it as? SerializableAnnotationValue.StringValue }?.value ?: if (name == "_id") "ID" else name.titleCase()
val KSerializer<*>.displayName: String
    get() = serializableAnnotations.find { it.fqn == "com.lightningkite.services.data.DisplayName" }?.values?.get(
        "text"
    )?.let { it as? SerializableAnnotationValue.StringValue }?.value ?: descriptor.serialName.substringBefore('<')
        .substringAfterLast('.').titleCase()

val SerializableProperty<*, *>.description: String?
    get() = this.serializableAnnotations.find { it.fqn == "com.lightningkite.services.data.Description" }?.values?.get(
        "text"
    )?.let { it as? SerializableAnnotationValue.StringValue }?.value
val KSerializer<*>.description: String?
    get() = serializableAnnotations.find { it.fqn == "com.lightningkite.services.data.Description" }?.values?.get(
        "text"
    )?.let { it as? SerializableAnnotationValue.StringValue }?.value

val SerializableProperty<*, *>.descriptionOrDisplayName get() = description ?: displayName
val SerializableProperty<*, *>.hint
    get() = serializableAnnotations.find { it.fqn == "com.lightningkite.services.data.Hint" }?.values?.get(
        "text"
    )?.let { it as? SerializableAnnotationValue.StringValue }?.value
        ?: description
        ?: displayName

val SerializableProperty<*, *>.group
    get() = serializableAnnotations.find {
        it.fqn == "com.lightningkite.services.data.Group"
    }?.values?.values?.first()?.let {
        it as? SerializableAnnotationValue.StringValue
    }?.value
val SerializableProperty<*, *>.sentence
    get() = serializableAnnotations.find {
        it.fqn == "com.lightningkite.services.data.Sentence"
    }?.values?.values?.first()?.let {
        it as? SerializableAnnotationValue.StringValue
    }?.value
val SerializableProperty<*, *>.importance
    get() = serializableAnnotations.find {
        it.fqn == "com.lightningkite.services.data.Importance"
    }?.values?.values?.first()?.let {
        it as? SerializableAnnotationValue.ByteValue
    }?.value?.toInt() ?: when (name) {
        "_id" -> if (serializer.descriptor.serialName == "com.lightningkite.Uuid") 8 else 1
        "title", "subject" -> 1
        "name", "email", "phone" -> 2
        else -> 7
    }
val SerializableProperty<*, *>.doesNotNeedLabel
    get() = serializableAnnotations.any {
        it.fqn == "com.lightningkite.services.data.DoesNotNeedLabel"
    }
val SerializableProperty<*, *>.indexed
    get() = serializableAnnotations.any {
        it.fqn == "com.lightningkite.services.data.Index"
    }

fun SerializableProperty<*, *>.visibility(module: FormModule): FieldVisibility =
    serializableAnnotations.mapNotNull { module.visibilitySettings[it.fqn] }.minOrNull()
        ?: when {
            name == "_id" &&
                    serializer.descriptor.serialName.substringBefore('/') == ("com.lightningkite.Uuid") &&
                    serializableAnnotations.none { it.fqn == "com.lightningkite.services.data.References" } &&
                    serializableAnnotations.none { it.fqn == "com.lightningkite.services.data.MultipleReferences" }
                     -> module.visibilitySettings["com.lightningkite.services.data.AdminHidden"]!!
            else -> FieldVisibility.EDIT
        }