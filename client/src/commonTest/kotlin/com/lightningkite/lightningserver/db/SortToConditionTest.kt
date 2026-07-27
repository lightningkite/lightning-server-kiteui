package com.lightningkite.lightningserver.db

import com.lightningkite.services.database.SortPart
import com.lightningkite.services.database.comparator
import com.lightningkite.services.database.path
import com.lightningkite.services.database.sort
import com.lightningkite.services.database.notNull
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * Test suite for Sort.after() functionality.
 *
 * Sort.after() generates a Condition that matches all items that would appear
 * after a given item according to the sort specification. This is critical for
 * cursor-based pagination in ModelCache.
 *
 * The function must correctly handle:
 * - Ascending and descending sorts
 * - Multiple sort fields
 * - Nullable fields with notNull operator
 * - Complex comparison logic
 *
 * This test verifies that for any item in a sorted list, Sort.after() produces
 * a condition that matches exactly the remaining items after that position.
 */
class SortToConditionTest {
    /**
     * Tests Sort.after() with various sort configurations.
     *
     * For each sort configuration, this test:
     * 1. Generates 100 random LargeTestModel items
     * 2. Sorts them according to the specification
     * 3. For each position in the sorted list, generates an "after" condition
     * 4. Verifies that the condition matches exactly the items after that position
     *
     * Tests ascending/descending sorts on both nullable and non-nullable fields.
     */
    @Test
    fun test() {
        // Generate random test data with both nullable and non-nullable fields
        val data = (1..100).map {
            LargeTestModel(
                int = Random.nextInt(),
                long = Random.nextLong(),
                float = Random.nextFloat(),
                double = Random.nextDouble(),
                intNullable = Random.nextInt().takeIf { Random.nextBoolean() },
                longNullable = Random.nextLong().takeIf { Random.nextBoolean() },
                floatNullable = Random.nextFloat().takeIf { Random.nextBoolean() },
                doubleNullable = Random.nextDouble().takeIf { Random.nextBoolean() },
            )
        }
        // Test various sort configurations
        val sorts = listOf(
            sort<LargeTestModel> {
                it.int.ascending()
                it._id.ascending()
            },
            sort<LargeTestModel> {
                it.intNullable.notNull.ascending()
                it._id.ascending()
            },
            sort<LargeTestModel> {
                it.intNullable.notNull.descending()
                it._id.ascending()
            },
        )
        for (sort in sorts) {
            // Sort the data using the comparator
            val sorted = data.sortedWith(sort.comparator!!)

            // For each position in the sorted list, test that Sort.after() produces
            // a condition that matches exactly the remaining items
            (1..sorted.lastIndex).forEach { index ->
                val firstHalf = sorted.take(index)
                val lastElement = firstHalf.last()

                // Generate the "after" condition for pagination
                val condition = sort.after(lastElement)

                val secondHalf = sorted.drop(index)
                val afterLast = sorted.filter { condition(it) }

                // Helper to display sort field values for debugging
                fun toString(model: LargeTestModel) = sort.joinToString("/") { it.field.getAny(model).toString() }

                // Verify that the condition matches exactly the items after this position
                assertEquals(secondHalf.joinToString("\n", transform = ::toString), afterLast.joinToString("\n", transform = ::toString), message = "Failed equality on ${sort} index $index.\n${toString(lastElement)}\n${condition}\nFull List: ${sorted.joinToString(transform = ::toString)}")
//                println("Success on ${sort} index $index.\n${toString(lastElement)}\n${condition}\nFull List: ${sorted.joinToString(transform = ::toString)}")
            }
        }
    }

    /**
     * A case-insensitive sort orders by the lowercased value, but [after] can only generate
     * case-sensitive comparisons, so the cursor would land in the wrong place.  It must refuse
     * rather than silently hand back a condition that skips and duplicates rows across pages.
     */
    @Test
    fun caseInsensitiveSortIsRefused() {
        val sort = listOf(SortPart(path<LargeTestModel>().string, ascending = true, ignoreCase = true))
        assertFailsWith<IllegalArgumentException> { sort.after(LargeTestModel(string = "a")) }
    }
}

/*
 * ============================================================================
 * TEST COVERAGE RECOMMENDATIONS
 * ============================================================================
 *
 * Missing Test Scenarios:
 *
 * 1. Additional Sort Configurations:
 *    - Test sorts with 3+ fields
 *    - Test sorts mixing ascending and descending across multiple fields
 *    - Test sorts on String fields (alphabetical ordering)
 *    - Test sorts on Boolean fields
 *    - Test sorts on Date/Timestamp fields
 *    - Test sorts on Enum fields
 *
 * 2. Edge Cases:
 *    - Test with all null values in a nullable field
 *    - Test with all non-null values in a nullable field
 *    - Test with duplicate values in the primary sort field
 *    - Test with completely identical items (all fields equal)
 *    - Test with single-item lists
 *    - Test with two-item lists
 *
 * 3. Boundary Conditions:
 *    - Test Sort.after() at the very first position (should match everything)
 *    - Test Sort.after() at the very last position (should match nothing)
 *    - Test with extreme numeric values (Int.MIN_VALUE, Int.MAX_VALUE, etc.)
 *    - Test with NaN and Infinity for Float/Double
 *
 * 4. Complex Comparisons:
 *    - Test nullable field handling when nulls come first vs last
 *    - Test tie-breaking behavior across multiple sort fields
 *    - Test that _id is used as final tie-breaker
 *
 * 5. Performance:
 *    - Test with very large datasets (10,000+ items)
 *    - Measure condition evaluation performance
 *    - Test memory usage of generated conditions
 *
 * 6. Condition Correctness:
 *    - Test that generated conditions are SQL-compatible (if applicable)
 *    - Test condition serialization/deserialization
 *    - Verify condition produces the same results when evaluated by different systems
 *
 * 7. Error Cases:
 *    - Test behavior when Sort has no comparator defined
 *    - Test with invalid or corrupt sort specifications
 *    - Test Sort.after() with an item that's not in the original dataset
 *
 * 8. Integration:
 *    - Test Sort.after() integration with actual database queries
 *    - Test with ModelCache pagination scenarios
 *    - Test round-trip: sort -> after -> query -> sort again
 */