package com.lightningkite.lightningserver.admin

//
// Code Review Summary:
// This file provides type aliases for type erasure when working with models of unknown types
// in the admin panel. The nested Comparable structure (Comparable<Comparable<*>>) allows
// any Comparable ID type to be represented at runtime through unsafe casting.
//
// Documentation:
// - File lacks documentation explaining the purpose and usage of these type aliases
// - The nested Comparable pattern is non-obvious and deserves explanation
//
// Tests:
// - Comprehensive test coverage added in ReadabilityTest.kt
// - Tests verify unsafe casting pattern used throughout the admin panel
// - Edge cases tested: empty IDs, negative numbers, Unicode strings, null values
// - All tests passing
//
// Code Quality:
// - Type aliases are correctly defined and working as intended
// - Used consistently throughout admin panel (CollectionAdminScreen, DetailAdminScreen, etc.)
// - No bugs identified
// - No security issues
//
// Improvement Suggestions:
// 1. Add KDoc comments explaining the purpose of each type alias and how they enable
//    type erasure for the admin panel's generic model handling. Example usage would help.
// 2. Consider adding a code example showing the typical unsafe cast pattern:
//    `adminServer().models[name]?.cache(auth()) as? ModelCache<UnknownModel, UnknownId>`
// 3. The nested Comparable structure (Comparable<Comparable<*>>) could be explained with
//    a comment clarifying why this enables type erasure at runtime (variance rules).
// 4. Consider adding a warning comment that these types should only be used with unsafe
//    casts (as?) and are not meant for type-safe direct assignment.
// 5. File name "readability.kt" doesn't clearly convey its purpose. Consider renaming to
//    something like "TypeErasure.kt" or "UnknownModelTypes.kt" for better discoverability.

import com.lightningkite.services.database.HasId

/**
 * Type alias representing a model with an unknown ID type.
 *
 * This is used throughout the admin panel to enable generic handling of collections
 * with different model types. It must be used with unsafe casting (as?) since the
 * nested Comparable structure doesn't allow direct type-safe assignment.
 *
 * Example usage:
 * ```
 * val mc = adminServer().models[collectionName]?.cache(auth())
 *     as? ModelCache<UnknownModel, UnknownId>
 * ```
 */
typealias UnknownModel = HasId<UnknownId>

/**
 * Type alias representing a comparable ID of unknown concrete type.
 *
 * The nested structure Comparable<Comparable<*>> enables runtime type erasure
 * for any Comparable ID type (String, Int, UUID, etc.) through Kotlin's variance rules.
 * This allows the admin panel to work with any model type without knowing its ID type at compile time.
 */
typealias UnknownId = Comparable<Comparable<*>>