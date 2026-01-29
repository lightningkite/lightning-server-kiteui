//
// CODE REVIEW SUMMARY
// ===================
// Extension properties for reading serializable annotations on properties/serializers.
// These power the form rendering system's annotation-driven behavior.
//
// IMPROVEMENT SUGGESTIONS:
//
// 1. FORCE UNWRAP (Line 75): `visibilitySettings["com.lightningkite.services.data.AdminHidden"]!!`
//    will throw if AdminHidden annotation is not registered. Consider defensive approach.
//
// 2. HARDCODED FQNs: Annotation fully-qualified names are hardcoded strings throughout.
//    Consider extracting to constants for maintainability and IDE navigation.
//
// 3. MAGIC NUMBERS (Lines 54-57): Importance priority values (1, 2, 7, 8) are undocumented.
//    Add documentation explaining the priority scale and why these defaults.
//
// 4. MISSING KDOC: Extensions like `displayName`, `description`, `importance` lack
//    documentation explaining their purpose and fallback behavior.
//
// 5. INCONSISTENT ANNOTATION ACCESS: Some use `.values?.get("text")` while others use
//    `.values?.values?.first()`. The difference should be documented.
//
// 6. FALLBACK LOGIC: displayName falls back to titleCase(name) - this is good but should
//    be documented as the default behavior users can expect.
//
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
        "_id" -> if (serializer.descriptor.serialName == "com.lightningkite.Uuid") 1 else 8
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