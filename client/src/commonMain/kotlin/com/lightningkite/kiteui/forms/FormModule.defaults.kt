package com.lightningkite.kiteui.forms

/**
 * Registers all built-in renderers to this [FormModule].
 *
 * Call this to get a fully-functional form module with renderers for:
 * - Primitives: Boolean, Int, Long, Double, String
 * - Date/Time: Instant, LocalDateTime, LocalDate, LocalTime, TimeZone
 * - Special types: UUID, EmailAddress, PhoneNumber, GeoCoordinate
 * - Enums: Any enum type
 * - Nullable: Generic wrapper for T? types
 * - Collections: List<T>, Set<T>, Map<K, V>
 * - EnumSets: Set<EnumType> rendered as checkboxes
 * - Objects: Data classes with field-by-field rendering
 * - Wrappers: Value classes
 * - Alternative renderers: JSON (any type), CSV (list of data classes), Hex (numeric types)
 *
 * ## Usage
 * ```kotlin
 * val module = FormModule().apply { defaults() }
 * ```
 *
 * by Claude
 */
fun FormModule.defaults() {
    // Specific type renderers (higher priority)
    registerPrimitives()
    registerDateTimes()
    registerSpecialTypes()
    registerServerFile()       // by Claude - file upload support
    registerSortPath()         // by Claude - SortPart<T> for sorting config
    registerCondition()        // by Claude - fill-in-the-blank Condition<T> editor
    registerModification()     // by Claude - fill-in-the-blank Modification<T> editor

    // Annotation-based renderers
    registerForeignKey()       // by Claude - @References annotation support

    // Structural renderers (lower priority, match by kind/structure)
    registerEnum()
    registerWrapper()
    registerMySealed()         // by Claude - MySealedClassSerializerInterface support
    registerVirtualSealed()    // VirtualSealed.Concrete support for schema-generated sealed classes
    registerSingletonObject()  // by Claude - Kotlin object singletons
    registerCollections()
    registerSet()              // by Claude - Set<T> support
    registerEnumSet()          // by Claude - Set<EnumType> as checkboxes
    registerMap()              // by Claude - Map<K, V> support
    registerTable()            // by Claude - table rendering for lists
    registerObject()
    registerNullable()

    // Alternative renderers (lower priority, selectable via switcher) - by Claude
    registerJson()             // by Claude - JSON text for any type
    registerHex()              // by Claude - hex display for byte/short/int/long
    registerCsv()              // by Claude - CSV for lists of data classes
}
