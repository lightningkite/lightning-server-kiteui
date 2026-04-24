package com.lightningkite.kiteui.forms

import com.lightningkite.kiteui.navigation.DefaultUriFormat
import com.lightningkite.services.data.titleCase
import com.lightningkite.services.database.*
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.serializer
import kotlin.time.Instant

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
public object Annotations {
    // Display & Labeling
    public const val DisplayName: String = "com.lightningkite.services.data.DisplayName"
    public const val Description: String = "com.lightningkite.services.data.Description"
    public const val Hint: String = "com.lightningkite.services.data.Hint"
    public const val Sentence: String = "com.lightningkite.services.data.Sentence"
    public const val DoesNotNeedLabel: String = "com.lightningkite.services.data.DoesNotNeedLabel"

    // Field Organization
    public const val Group: String = "com.lightningkite.services.data.Group"
    public const val Importance: String = "com.lightningkite.services.data.Importance"

    // Visibility & Access
    public const val AdminHidden: String = "com.lightningkite.services.data.AdminHidden"
    public const val AdminViewOnly: String = "com.lightningkite.services.data.AdminViewOnly"
    public const val Denormalized: String = "com.lightningkite.services.data.Denormalized"

    // String Constraints
    public const val MaxLength: String = "com.lightningkite.services.data.MaxLength"
    public const val Multiline: String = "com.lightningkite.services.data.Multiline"

    // References
    public const val References: String = "com.lightningkite.services.data.References"
    public const val MultipleReferences: String = "com.lightningkite.services.data.MultipleReferences"

    // Indexing
    public const val Index: String = "com.lightningkite.services.data.Index"

    // Admin Configuration
    public const val NaturalSort: String = "com.lightningkite.services.data.NaturalSort"
    public const val AdminTableColumns: String = "com.lightningkite.services.data.AdminTableColumns"
    public const val AdminTitleFields: String = "com.lightningkite.services.data.AdminTitleFields"

    // Layout Control - by Claude
    public const val HorizontalLayout: String = "com.lightningkite.services.data.HorizontalLayout"
}

// ===== RenderContext Annotation Helpers =====

/** Get the display name, falling back to context-appropriate defaults */
public val RenderContext<*>.displayName: String
    get() = annotationString(Annotations.DisplayName, "text")
        ?: serializer.descriptor.serialName
            .substringBefore('<')
            .substringAfterLast('.')
            .titleCase()

/** Get the description text, if any */
public val RenderContext<*>.description: String?
    get() = annotationString(Annotations.Description, "text")

/** Get hint text for input fields, falling back to description or display name */
public val RenderContext<*>.hint: String?
    get() = annotationString(Annotations.Hint, "text")
        ?: description

/** Get the max length constraint, if specified */
public val RenderContext<*>.maxLength: Int?
    get() = annotationInt(Annotations.MaxLength, "size")?.takeIf { it != -1 }

/** Get the average length hint for sizing, if specified */
public val RenderContext<*>.averageLength: Int?
    get() = annotationInt(Annotations.MaxLength, "average")?.takeIf { it != -1 } ?: maxLength?.div(4)

/** Check if this should be rendered as multiline */
public val RenderContext<*>.isMultiline: Boolean
    get() = hasAnnotation(Annotations.Multiline)

/** Check if this field should be hidden */
public val RenderContext<*>.isHidden: Boolean
    get() = hasAnnotation(Annotations.AdminHidden)

/** Check if this field is view-only (not editable) */
public val RenderContext<*>.isViewOnly: Boolean
    get() = hasAnnotation(Annotations.AdminViewOnly) || hasAnnotation(Annotations.Denormalized)

public val KSerializer<*>.displayName: String
    get() = serializableAnnotations.find { it.fqn == Annotations.DisplayName }?.values?.get(
        "text"
    )?.let { it as? SerializableAnnotationValue.StringValue }?.value ?: descriptor.serialName.substringBefore('<')
        .substringAfterLast('.').titleCase()

public val SerializableProperty<*, *>.description: String?
    get() = this.serializableAnnotations.find { it.fqn == Annotations.Description }?.values?.get(
        "text"
    )?.let { it as? SerializableAnnotationValue.StringValue }?.value
public val KSerializer<*>.description: String?
    get() = serializableAnnotations.find { it.fqn == Annotations.Description }?.values?.get(
        "text"
    )?.let { it as? SerializableAnnotationValue.StringValue }?.value

public val SerializableProperty<*, *>.descriptionOrDisplayName: String get() = description ?: displayName
public val SerializableProperty<*, *>.hint: String
    get() = serializableAnnotations.find { it.fqn == Annotations.Hint }?.values?.get(
        "text"
    )?.let { it as? SerializableAnnotationValue.StringValue }?.value
        ?: description
        ?: displayName

public val SerializableProperty<*, *>.indexed: Boolean
    get() = serializableAnnotations.any {
        it.fqn == Annotations.Index
    }

public fun SerializableProperty<*, *>.visibility(module: FormModule): FieldVisibility =
    serializableAnnotations.mapNotNull { module.visibilitySettings[it.fqn] }.minOrNull()
        ?: when {
            name == "_id" &&
                    serializer.descriptor.serialName.substringBefore('/') == ("com.lightningkite.Uuid") &&
                    serializableAnnotations.none { it.fqn == Annotations.References } &&
                    serializableAnnotations.none { it.fqn == Annotations.MultipleReferences }
                -> module.visibilitySettings[Annotations.AdminHidden]!!

            else -> FieldVisibility.EDIT
        }


private val titleFieldNames = setOf("name", "title", "label", "email", "slug", "key")

/**
 * Determines the natural sort order for a type.
 *
 * This function examines the type's metadata to determine how instances should be sorted
 * by default. The priority order is:
 * 1. @NaturalSort annotation (explicit sort configuration)
 * 2. String-based _id field (ascending, for named identifiers)
 * 3. Instant/timestamp field (descending, for time-based data)
 * 4. First available field (ascending, as a fallback)
 *
 * ## Usage
 * Used by admin panels and collection views to determine default sort order when displaying lists.
 *
 * @return List of sort parts defining the natural sort order, or empty list if no sortable fields exist
 */
public fun <T> KSerializer<T>.naturalSort(): List<SortPart<T>> {
    return serializableAnnotations.find {
        it.fqn == "com.lightningkite.services.data.NaturalSort"
    }?.values?.entries?.firstOrNull()?.let { it.value as? SerializableAnnotationValue.ArrayValue }?.value?.mapNotNull {
        (it as? SerializableAnnotationValue.StringValue)?.value?.let {
            DefaultUriFormat.decodeFromString(SortPart.serializer(this@naturalSort), it)
        }
    } ?: serializableProperties?.find {
        !it.serializer.descriptor.isNullable && it.serializer.descriptor.serialName.substringBefore(
            '/'
        ) == "kotlinx.datetime.Instant"
    }?.let {
        // Timestamps - sort descending (most recent first)
        @Suppress("UNCHECKED_CAST")
        listOf(
            SortPart(
                DataClassPathAccess(DataClassPathSelf(this), it as SerializableProperty<T, Instant>),
                ascending = false
            )
        )
    } ?: serializableProperties?.find { it.name == "_id" && it.serializer == String.serializer() }?.let {
        // Named IDs (e.g., usernames, slugs) - sort ascending
        @Suppress("UNCHECKED_CAST")
        listOf(
            SortPart(
                DataClassPathAccess(DataClassPathSelf(this), it as SerializableProperty<T, String>),
                ascending = true
            )
        )
    } ?: serializableProperties?.find { it.name in titleFieldNames && it.serializer == String.serializer() }?.let {
        // Named IDs (e.g., usernames, slugs) - sort ascending
        @Suppress("UNCHECKED_CAST")
        listOf(
            SortPart(
                DataClassPathAccess(DataClassPathSelf(this), it as SerializableProperty<T, String>),
                ascending = true
            )
        )
    } ?: serializableProperties?.firstOrNull()?.let {
        // Fallback: use first field, whatever it is
        listOf(
            SortPart(
                DataClassPathAccess(
                    DataClassPathSelf(this),
                    it
                ), ascending = true
            )
        )
    } ?: listOf()
}

/**
 * Determines which fields should be shown as columns in a table view.
 *
 * Returns a list of data class paths representing the fields to display as columns.
 * The selection strategy is:
 * 1. Use @AdminTableColumns annotation if present (explicit configuration)
 * 2. Otherwise, select the 5 most important fields based on [importance]
 *
 * ## Usage
 * Used by admin panels to configure which fields are visible in list/table views of this type.
 *
 * @return List of paths to fields that should be displayed as columns
 */
public fun <T> KSerializer<T>.defaultColumns(): List<DataClassPath<T, *>> {
    return serializableAnnotations.find {
        it.fqn == "com.lightningkite.services.data.AdminTableColumns"
    }?.values?.entries?.firstOrNull()?.let { it.value as? SerializableAnnotationValue.ArrayValue }?.value?.mapNotNull {
        (it as? SerializableAnnotationValue.StringValue)?.value?.let {
            DefaultUriFormat.decodeFromString(DataClassPathPartial.serializer(this), it) as DataClassPath<T, *>
        }
    } ?: serializableProperties!!.sortedBy {
        it.importance // Lower importance values = more important fields
    }.take(5).map {
        DataClassPathAccess(DataClassPathSelf(this), it)
    }
}

/**
 * Determines which fields should be used to display a human-readable title for this type.
 *
 * Returns a list of data class paths whose values will be combined to form a display title.
 * This is used when showing references to this type (e.g., in dropdowns, foreign key displays).
 *
 * ## Selection Strategy
 * 1. @AdminTitleFields annotation (explicit configuration)
 * 2. Field named "name"
 * 3. Field named "title"
 * 4. Field named "subject"
 * 5. Field named "label"
 * 6. Non-UUID _id field
 * 7. First 3 fields (as a last resort)
 *
 * ## Usage
 * ```kotlin
 * // For a User with fields: _id, username, email, createdAt
 * // Would return: [username] (matched by "name"-like convention)
 * val titleFields = User.serializer().defaultTitleFields()
 * ```
 *
 * @return List of paths to fields that should be used to display a title/label for instances
 */
public fun <T> KSerializer<T>.defaultTitleFields(): List<DataClassPath<T, *>> {
    val serializer = this
    val it = serializer.serializableProperties!!
    val dcps = DataClassPathSerializer(serializer)
    val nameFields: List<DataClassPath<T, *>> = serializer.serializableAnnotations.find {
        it.fqn == "com.lightningkite.services.data.AdminTitleFields"
    }?.values?.get("fields")?.let { it as? SerializableAnnotationValue.ArrayValue }
        ?.value
        ?.mapNotNull { it as? SerializableAnnotationValue.StringValue }
        ?.map { it.value }
        ?.toSet()
        ?.let { matching ->
            matching.map { dcps.fromString(it) as DataClassPath<T, *> }
        }
        ?: it.find { it.name == "name" }?.let { DataClassPathAccess(DataClassPathSelf(serializer), it) }?.let(::listOf)
        ?: it.find { it.name == "title" }?.let { DataClassPathAccess(DataClassPathSelf(serializer), it) }?.let(::listOf)
        ?: it.find { it.name == "subject" }?.let { DataClassPathAccess(DataClassPathSelf(serializer), it) }
            ?.let(::listOf)
        ?: it.find { it.name == "label" }?.let { DataClassPathAccess(DataClassPathSelf(serializer), it) }?.let(::listOf)
        ?: it.find { it.name == "_id" && !it.serializer.descriptor.serialName.contains("Uuid") }
            ?.let { DataClassPathAccess(DataClassPathSelf(serializer), it) }?.let(::listOf)
        ?: it.map { DataClassPathAccess(DataClassPathSelf(serializer), it) }.take(3) // Fallback: first 3 fields
    return nameFields
}


// ===== Utility =====

/**
 * Convert a camelCase or PascalCase string to Title Case with spaces.
 */
public fun String.titleCase(): String = buildString {
    this@titleCase.forEachIndexed { index, c ->
        if (index > 0 && c.isUpperCase()) append(' ')
        append(if (index == 0) c.uppercaseChar() else c)
    }
}

private val pluralizeRules = listOf(
    // Irregular/Static mappings
    "person" to "people",
    "child" to "children",
    "mouse" to "mice",
    "tooth" to "teeth",
    "goose" to "geese",

    // Regex rules (Pattern to Replacement)
    "(quiz)$" to "$1zes",
    "^(ox)$" to "$1en",
    "([m|l])ouse$" to "$1ice",
    "(matr|vert|ind)ix|ex$" to "$1ices",
    "(x|ch|ss|sh)$" to "$1es",
    "([^aeiouy]|qu)y$" to "$1ies",
    "(hive)$" to "$1s",
    "(?:([^f])fe|([lr])f)$" to "$1$2ves",
    "sis$" to "ses",
    "([ti])um$" to "$1a",
    "(buffal|tomat)o$" to "$1oes",
    "(bu)s$" to "$1ses",
    "(alias|status)$" to "$1es",
    "(octop|vir)us$" to "$1i",
    "(ax|test)is$" to "$1es",
    "s$" to "s"
).map { (pattern, replacement) -> Regex(pattern, RegexOption.IGNORE_CASE) to replacement }

public fun String.pluralize(): String {
    val word = this
    if (word.isBlank()) return word

    // 1. Check for exact matches in our rules first (for things like "person")
    // 2. Otherwise, apply Regex rules
    for ((regex, replacement) in pluralizeRules) {
        if (regex.containsMatchIn(word)) {
            return word.replace(regex, replacement)
        }
    }

    // Default: just add 's'
    return "${word}s"
}

/**
 * Defines the visibility level for fields in forms.
 *
 * - HIDDEN: Field is completely hidden from the UI
 * - READ: Field is shown as read-only (uses view renderer even in forms)
 * - EDIT: Field is editable (uses form renderer)
 */
public enum class FieldVisibility { HIDDEN, READ, EDIT }