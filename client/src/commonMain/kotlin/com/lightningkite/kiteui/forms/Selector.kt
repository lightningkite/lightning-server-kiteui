@file:OptIn(ExperimentalSerializationApi::class)

package com.lightningkite.kiteui.forms

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.descriptors.SerialKind

/**
 * Describes criteria for matching a [Renderer] to a type.
 *
 * A Selector matches a [RenderContext] if ALL specified criteria match:
 * - [annotation]: If set, context must have an annotation with this FQN
 * - [type]: If set, context's serializer descriptor name must match (prefix before '/')
 * - [kind]: If set, context's serializer descriptor kind must match
 *
 * Null criteria are ignored (match anything).
 *
 * by Claude
 */
data class Selector(
    /** Match only if this annotation FQN is present (field or type level) */
    val annotation: String? = null,
    /** Match only this fully-qualified type name (e.g., "kotlin.String") */
    val type: String? = null,
    /** Match only this serialization kind (PRIMITIVE, CLASS, LIST, etc.) */
    val kind: SerialKind? = null,
) {
    /**
     * Check if this selector matches the given context.
     *
     *
     * All non-null criteria must match. Null criteria are wildcards.
     */
    fun matches(context: RenderContext<*>): Boolean {
        val descriptor = context.serializer.descriptor
//        println("DEBUG description ${descriptor.serialName}")
        if (annotation != null && !context.hasAnnotation(annotation)) return false
//        println("DEBUG after annotation check")
        if (type != null && descriptor.serialName.substringBefore('/') != type) return false
//        println("DEBUG pe != null && descriptor.serialName.substringBefore('/') != typ")
//        println("DEBUG description.kind ${descriptor.kind}")
//        println("DEBUG kind ${kind}")
        if (kind != null && descriptor.kind != kind) return false
//        println("DEBUG kind != null && descriptor.kind != kind ${kind != null && descriptor.kind != kind}")
        return true
    }
}
