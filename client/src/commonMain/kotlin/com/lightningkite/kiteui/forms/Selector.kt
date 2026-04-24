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
public data class Selector(
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
     * All non-null criteria must match. Null criteria are wildcards.
     */
    public fun matches(context: RenderContext<*>): Boolean {
        val descriptor = context.serializer.descriptor
        if (annotation != null && !context.hasAnnotation(annotation)) return false
        if (type != null && descriptor.serialName.substringBefore('/') != type) return false
        if (kind != null && descriptor.kind != kind) return false
        return true
    }
}
