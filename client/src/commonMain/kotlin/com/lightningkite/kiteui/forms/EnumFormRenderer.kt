package com.lightningkite.kiteui.forms

import com.lightningkite.kiteui.views.direct.select
import com.lightningkite.kiteui.views.direct.text
import com.lightningkite.kiteui.views.fieldTheme
import com.lightningkite.reactive.core.Constant
import com.lightningkite.services.database.SerializableAnnotationValue
import com.lightningkite.services.database.VirtualEnumValue
import com.lightningkite.services.database.getElementSerializableAnnotations
import com.lightningkite.services.database.nullElement
import com.lightningkite.titleCase
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.SerialKind

/**
 * Renderer for enum types, providing dropdown selection for forms and text display for views.
 *
 * ## Features
 * - Dropdown (select) for form editing
 * - Text display for read-only views
 * - Support for nullable enums (adds "N/A" option)
 * - Support for custom display names via @DisplayName annotation
 * - Support for both standard Kotlin enums and VirtualEnumValue (server-generated enums)
 * - Automatic title-casing of enum names when no display name is provided
 *
 * ## Display Name Resolution
 * Display names are resolved in order of priority:
 * 1. @DisplayName annotation on the enum value
 * 2. VirtualEnumValue name (for server-generated enums)
 * 3. Kotlin enum name with title-casing
 * 4. toString() with title-casing (fallback)
 *
 * ## Matching Rules
 * - Matches only SerialKind.ENUM types
 * - Handles both nullable and non-nullable enums
 *
 * ## Gotchas
 * - Base priority is 0.7, same as ByFieldRenderer
 * - Nullable enums show "N/A" for null value
 * - VirtualEnumValue is used for dynamically-generated enums from server schema
 */
object EnumFormRenderer: FormRenderer.Generator, ViewRenderer.Generator {
    override val name: String = "Enum"
    override val basePriority: Float
        get() = 0.7f
    override val kind = SerialKind.ENUM

    /**
     * Holds enum metadata and display name resolution logic.
     *
     * @property serializer The enum serializer (may be nullable)
     */
    class TypeInfo<T>(val serializer: KSerializer<T>) {
        /**
         * The list of available enum options.
         * For nullable enums, prepends null to the list of values.
         */
        @Suppress("UNCHECKED_CAST")
        val options =  Constant((serializer.nullElement() ?: serializer).enumValues().let {
            if (serializer.descriptor.isNullable) listOf(null) + it else it
        } as List<T>)

        /**
         * Converts an enum value to its display name.
         *
         * Resolution order:
         * 1. If null, returns "N/A"
         * 2. If VirtualEnumValue, checks for @DisplayName annotation, falls back to name
         * 3. If Kotlin Enum, checks for @DisplayName annotation, falls back to name
         * 4. Otherwise uses toString()
         *
         * All names are title-cased unless a custom @DisplayName is provided.
         */
        fun toDisplayName(it: T): String {
            return if (it == null) "N/A"
            // Handle VirtualEnumValue (server-generated enums)
            else (it as? VirtualEnumValue)?.let {
                it.enum.options[it.index].let {
                    it.annotations.find { it.fqn == "com.lightningkite.services.data.DisplayName" }?.values?.get(
                        "text"
                    )?.let { it as? SerializableAnnotationValue.StringValue }?.value ?: it.name.titleCase()
                }
            }
            // Handle standard Kotlin enums
            ?: (it as? Enum<*>)?.let {
                serializer.getElementSerializableAnnotations(it.ordinal)
                    .find { it.fqn == "com.lightningkite.services.data.DisplayName" }?.values?.get(
                        "text"
                    )?.let { it as? SerializableAnnotationValue.StringValue }?.value ?: it.name.titleCase()
            }
            // Fallback to toString
            ?: it.toString().titleCase()
        }
    }
    /**
     * Creates a form renderer with a dropdown (select) element.
     *
     * The select element is bound to the mutable value and displays all enum options
     * using their display names.
     */
    override fun <T> form(module: FormModule, selector: FormSelector<T>): FormRenderer<T> {
        val info = TypeInfo(selector.serializer)
        return FormRenderer(module, this, selector) { _, mutable ->
            fieldTheme.select {
                @Suppress("UNCHECKED_CAST")
                bind(
                    edits = mutable,
                    data = info.options,
                    render = info::toDisplayName
                )
            }
        }
    }

    /**
     * Creates a view renderer that displays the enum value as text.
     *
     * The text content is reactively bound to show the display name of the current value.
     */
    override fun <T> view(module: FormModule, selector: FormSelector<T>): ViewRenderer<T> {
        val info = TypeInfo(selector.serializer)
        return ViewRenderer(module, this, selector) { _, readable ->
            text {
                ::content { info.toDisplayName(readable.invoke()) }
            }
        }
    }
}

/*
 * TODO: API Improvement Recommendations for EnumFormRenderer
 *
 * 1. Radio Button Option: For enums with few values (2-4), consider offering radio buttons
 *    instead of dropdown. This would improve UX for boolean-like enums. Potential approach:
 *    - Add RadioButtonEnumRenderer with higher priority for small enums
 *    - Make selection configurable via annotation (@EnumStyle.DROPDOWN / .RADIO)
 *
 * 2. Search/Autocomplete: For enums with many values (>10), dropdown can be unwieldy.
 *    Consider:
 *    - Adding autocomplete/search functionality
 *    - Grouping related values
 *    - Pagination for very large enums
 *
 * 3. Display Name Caching: toDisplayName() is called repeatedly during rendering.
 *    Consider caching the display names in a map for performance.
 *
 * 4. Icon Support: Some enums might benefit from icons (e.g., status enums).
 *    Consider:
 *    - Adding @EnumIcon annotation
 *    - Supporting icon + text in dropdown
 *
 * 5. Color Coding: Status-like enums could use color coding.
 *    Consider:
 *    - @EnumColor annotation
 *    - Semantic color mapping (ERROR=red, SUCCESS=green, etc.)
 *
 * 6. Ordering: Enum options are shown in definition order. Consider:
 *    - @EnumOrder annotation for custom ordering
 *    - Alphabetical sorting option
 *    - Grouping by category
 *
 * 7. Deprecation Support: Handle deprecated enum values gracefully:
 *    - Show but mark as deprecated
 *    - Hide from new selections but show if currently selected
 *
 * 8. Null Handling: "N/A" is hardcoded for null. Consider:
 *    - Making this configurable
 *    - Different text for different contexts (N/A, None, Not Set, etc.)
 *
 * 9. Multi-Select: Consider adding support for Set<EnumType> with multi-select dropdown.
 *
 * 10. Testing: Add unit tests for:
 *     - Display name resolution logic
 *     - Nullable enum handling
 *     - VirtualEnumValue support
 *     - @DisplayName annotation parsing
 */