@file:OptIn(ExperimentalSerializationApi::class)

package com.lightningkite.kiteui.forms2

import com.lightningkite.services.database.SerializableAnnotation
import com.lightningkite.services.database.SerializableAnnotationValue
import com.lightningkite.services.database.serializableAnnotations
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer

/**
 * Context for rendering a value, containing the serializer and combined annotations.
 *
 * Annotations come from two sources:
 * - Type-level: annotations on the class/type itself (from serializer.descriptor.annotations)
 * - Field-level: annotations on the property when rendering a field (from parent's getElementAnnotations)
 *
 * Field annotations take precedence when both are present (checked first in allAnnotations).
 *
 * @param T The type being rendered
 * @property serializer The kotlinx.serialization serializer for the type
 * @property fieldAnnotations Annotations from the field declaration (empty if rendering a top-level value)
 *
 * by Claude
 */
data class RenderContext<T>(
    val serializer: KSerializer<T>,
    val fieldAnnotations: List<SerializableAnnotation> = emptyList(),
) {
    /** Annotations declared on the type itself */
    val typeAnnotations: List<SerializableAnnotation>
        get() = serializer.serializableAnnotations

    /** All annotations, field-level first (higher precedence) then type-level */
    val allAnnotations: List<SerializableAnnotation>
        get() = fieldAnnotations + typeAnnotations

    /** Check if any annotation (field or type) has the given fully-qualified name */
    fun hasAnnotation(fqn: String): Boolean =
        allAnnotations.any { it.fqn == fqn }

    /** Get the first annotation with the given FQN, or null if not found */
    fun annotation(fqn: String): SerializableAnnotation? =
        allAnnotations.firstOrNull { it.fqn == fqn }

    /** Get a string value from an annotation parameter */
    fun annotationString(fqn: String, param: String = "value"): String? =
        annotation(fqn)?.values?.get(param)?.let { it as? SerializableAnnotationValue.StringValue }?.value

    /** Get an int value from an annotation parameter */
    fun annotationInt(fqn: String, param: String = "value"): Int? =
        annotation(fqn)?.values?.get(param)?.let { it as? SerializableAnnotationValue.IntValue }?.value

    /** Get a boolean value from an annotation parameter */
    fun annotationBoolean(fqn: String, param: String = "value"): Boolean? =
        annotation(fqn)?.values?.get(param)?.let { it as? SerializableAnnotationValue.BooleanValue }?.value
}
