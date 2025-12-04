package com.lightningkite.lightningserver.db

import com.lightningkite.services.database.Condition
import com.lightningkite.services.database.SortPart
import com.lightningkite.services.database.DataClassPath
import com.lightningkite.services.database.DataClassPathNotNull

/**
 * Converts a list of [SortPart]s into a [Condition] that matches all items that come "after" the given item
 * according to the sort order.
 *
 * This is primarily used for cursor-based pagination, where you need to fetch the next page of results
 * after a specific item. The resulting condition ensures that items are retrieved in the correct order
 * relative to the provided cursor item.
 *
 * ## Algorithm Overview:
 * For a sort order like `[field1 ASC, field2 DESC, field3 ASC]`, this generates an OR condition that matches:
 * - Items where field1 > cursor.field1, OR
 * - Items where field1 == cursor.field1 AND field2 < cursor.field2, OR
 * - Items where field1 == cursor.field1 AND field2 == cursor.field2 AND field3 > cursor.field3
 *
 * ## Null Handling:
 * - For ascending sorts: NULL is treated as "less than" any value, so NULL values come first
 * - For descending sorts: NULL is treated as "less than" any value, so NULL values come last
 * - This matches standard SQL NULL ordering behavior
 *
 * @param after The cursor item to compare against. Items matching the returned condition will come "after" this item.
 * @return A [Condition] that matches all items that should appear after the cursor in the sort order.
 * @throws Error if a non-nullable field path has a null value in the cursor item (should not occur in practice)
 *
 * @see SortPart
 * @see Condition
 */
fun <T> List<SortPart<T>>.after(after: T): Condition<T> {
    // Generate one OR branch for each prefix of the sort list
    // For [A, B, C], this creates conditions for [A], [A, B], and [A, B, C]
    return Condition.Or<T>((1..this.size).map { count ->
        // Each branch is an AND of conditions for the prefix
        Condition.And(this.take(count).mapIndexed { index, it ->
            val isLast = index == count - 1
            val f = it.field

            // Handle nullable fields (wrapped in DataClassPathNotNull)
            if(f is DataClassPathNotNull<*, *>) {
                f as DataClassPathNotNull<T, Comparable<Comparable<*>>>
                val v = f.get(after)

                f.wraps.mapCondition(
                    if (v == null) {
                        // Cursor value is null - need to match based on sort direction
                        if (it.ascending) {
                            // ASC: null comes first, so "after null" means any non-null
                            if (isLast) Condition.NotEqual(null)
                            else Condition.Always // For equality check in prefix
                        } else {
                            // DESC: null comes last, so nothing comes after null
                            if (isLast) Condition.Never
                            else Condition.Equal(null) // For equality check in prefix
                        }
                    } else {
                        // Cursor value is non-null
                        if (it.ascending) {
                            // ASC: want values > cursor (or >= for equality prefix)
                            if (isLast) Condition.IfNotNull(Condition.GreaterThan(v))
                            else Condition.IfNotNull(Condition.GreaterThanOrEqual(v))
                        } else {
                            // DESC: want values < cursor (or <= for equality prefix)
                            // Also include nulls since they sort last in descending
                            if (isLast) Condition.Or(listOf(Condition.IfNotNull(Condition.LessThan(v)), Condition.Equal(null)))
                            else Condition.Or(listOf(Condition.IfNotNull(Condition.LessThanOrEqual(v)), Condition.Equal(null)))
                        }
                    }
                )
            } else {
                // Handle non-nullable fields
                f as DataClassPath<T, Comparable<Comparable<*>>>
                val v = f.get(after)
                // TODO: This error handling is inconsistent - nullable paths can have null values but this throws.
                // Consider whether this should return Condition.Never or handle gracefully instead of throwing.
                if (v == null) throw Error("Value to compare against is null; this should not be possible")

                f.mapCondition(
                    if (it.ascending) {
                        // ASC: simple comparison, no null handling needed
                        if (isLast) Condition.GreaterThan(v)
                        else Condition.GreaterThanOrEqual(v)
                    } else {
                        // DESC: simple comparison, no null handling needed
                        if (isLast) Condition.LessThan(v)
                        else Condition.LessThanOrEqual(v)
                    }
                )
            }
        })
    })
}

/*
 * API IMPROVEMENT RECOMMENDATIONS:
 *
 * 1. Add `before()` complement function for backward pagination
 *    - Would enable bi-directional cursor-based pagination
 *    - Logic would be inverse of `after()` (flip comparison operators)
 *
 * 2. Consider adding validation for empty sort lists
 *    - Currently returns Condition.Or with empty list which may have unexpected behavior
 *    - Could throw IllegalArgumentException or return Condition.Never
 *
 * 3. Add comprehensive null ordering configuration
 *    - SQL supports NULLS FIRST/NULLS LAST independently of ASC/DESC
 *    - Current implementation hardcodes null ordering behavior
 *    - Consider adding NullOrdering enum to SortPart
 *
 * 4. Improve type safety of Comparable casting
 *    - The unchecked cast to Comparable<Comparable<*>> is necessary but risky
 *    - Consider adding compile-time verification or runtime checks
 *
 * 5. Add unit tests for edge cases
 *    - All nulls in cursor
 *    - Mixed null/non-null values
 *    - Single-field sorts
 *    - Complex nested field paths
 *
 * 6. Consider performance optimization
 *    - For large sort lists, the current implementation generates O(n²) conditions
 *    - Could potentially be optimized with better condition tree structure
 *
 * 7. Add documentation examples
 *    - Include example input/output for common use cases
 *    - Show generated SQL-like pseudo-code for clarity
 *
 * 8. Inconsistent error handling between nullable and non-nullable paths
 *    - DataClassPath throws Error on null, DataClassPathNotNull handles gracefully
 *    - Should unify approach - either both throw or both handle gracefully
 *    - Consider using Result<Condition<T>> return type instead of throwing
 */