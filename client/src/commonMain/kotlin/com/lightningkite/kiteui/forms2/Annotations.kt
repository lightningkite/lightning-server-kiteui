package com.lightningkite.kiteui.forms2

/**
 * Constants for commonly-used annotation fully-qualified names.
 *
 * Using these constants instead of raw strings provides:
 * - IDE navigation support
 * - Compile-time typo detection
 * - Single point of change if packages are refactored
 *
 * by Claude
 */
object Annotations {
    // Display & Labeling
    const val DisplayName = "com.lightningkite.services.data.DisplayName"
    const val Description = "com.lightningkite.services.data.Description"
    const val Hint = "com.lightningkite.services.data.Hint"
    const val Sentence = "com.lightningkite.services.data.Sentence"
    const val DoesNotNeedLabel = "com.lightningkite.services.data.DoesNotNeedLabel"

    // Field Organization
    const val Group = "com.lightningkite.services.data.Group"
    const val Importance = "com.lightningkite.services.data.Importance"

    // Visibility & Access
    const val AdminHidden = "com.lightningkite.services.data.AdminHidden"
    const val AdminViewOnly = "com.lightningkite.services.data.AdminViewOnly"
    const val Denormalized = "com.lightningkite.services.data.Denormalized"

    // String Constraints
    const val MaxLength = "com.lightningkite.services.data.MaxLength"
    const val Multiline = "com.lightningkite.services.data.Multiline"

    // References
    const val References = "com.lightningkite.services.data.References"
    const val MultipleReferences = "com.lightningkite.services.data.MultipleReferences"

    // Indexing
    const val Index = "com.lightningkite.services.data.Index"

    // Admin Configuration
    const val NaturalSort = "com.lightningkite.services.data.NaturalSort"
    const val AdminTableColumns = "com.lightningkite.services.data.AdminTableColumns"
    const val AdminTitleFields = "com.lightningkite.services.data.AdminTitleFields"

    // Layout Control - by Claude
    const val HorizontalLayout = "com.lightningkite.services.data.HorizontalLayout"
}

// ===== RenderContext Annotation Helpers =====

/** Get the display name, falling back to context-appropriate defaults */
val RenderContext<*>.displayName: String
    get() = annotationString(Annotations.DisplayName, "text")
        ?: serializer.descriptor.serialName
            .substringBefore('<')
            .substringAfterLast('.')
            .titleCase()

/** Get the description text, if any */
val RenderContext<*>.description: String?
    get() = annotationString(Annotations.Description, "text")

/** Get hint text for input fields, falling back to description or display name */
val RenderContext<*>.hint: String?
    get() = annotationString(Annotations.Hint, "text")
        ?: description

/** Get the max length constraint, if specified */
val RenderContext<*>.maxLength: Int?
    get() = annotationInt(Annotations.MaxLength, "size")?.takeIf { it != -1 }

/** Get the average length hint for sizing, if specified */
val RenderContext<*>.averageLength: Int?
    get() = annotationInt(Annotations.MaxLength, "average")?.takeIf { it != -1 } ?: maxLength?.div(4)

/** Check if this should be rendered as multiline */
val RenderContext<*>.isMultiline: Boolean
    get() = hasAnnotation(Annotations.Multiline)

/** Check if this field should be hidden */
val RenderContext<*>.isHidden: Boolean
    get() = hasAnnotation(Annotations.AdminHidden)

/** Check if this field is view-only (not editable) */
val RenderContext<*>.isViewOnly: Boolean
    get() = hasAnnotation(Annotations.AdminViewOnly) || hasAnnotation(Annotations.Denormalized)

// ===== Utility =====

/**
 * Convert a camelCase or PascalCase string to Title Case with spaces.
 */
private fun String.titleCase(): String = buildString {
    this@titleCase.forEachIndexed { index, c ->
        if (index > 0 && c.isUpperCase()) append(' ')
        append(if (index == 0) c.uppercaseChar() else c)
    }
}
