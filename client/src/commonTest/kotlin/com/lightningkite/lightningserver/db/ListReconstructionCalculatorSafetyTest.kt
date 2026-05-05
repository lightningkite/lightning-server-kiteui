package com.lightningkite.lightningserver.db

import kotlin.uuid.Uuid
import com.lightningkite.services.database.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail
import kotlin.time.Clock

/**
 * Comprehensive safety tests for ListReconstructionCalculator implementations.
 * These tests verify correctness, consistency, and edge case handling.
 *
 * Each test runs against BOTH Naive and Optimized implementations to ensure
 * they produce identical results.
 */
class ListReconstructionCalculatorSafetyTest {

    /**
     * Helper to verify both implementations produce identical results
     */
    private inline fun <reified T : HasId<ID>, ID : Comparable<ID>> verifyBothImplementations(
        serializer: kotlinx.serialization.KSerializer<T>,
        noinline test: (ListReconstructionCalculator<T, ID>) -> Unit
    ) {
        val calculators = listOf(
            "Naive" to NaiveListReconstructionCalculator(serializer, clock = Clock.System),
            "Optimized" to OptimizedListReconstructionCalculator(serializer, clock = Clock.System)
        )
        calculators.forEach { (name, calc) ->
            try {
                test(calc)
            } catch (e: AssertionError) {
                throw AssertionError("$name implementation failed: ${e.message}", e)
            } catch (e: Exception) {
                throw AssertionError("$name implementation threw exception: ${e.message}", e)
            }
        }
    }

    @Test
    fun testNoDuplicatesAfterMultipleUpdates() = runTest2 {
        verifyBothImplementations(LargeTestModel.serializer()) { cache ->
            val model1 = LargeTestModel(int = 1)
            val model2 = LargeTestModel(int = 2)
            val model3 = LargeTestModel(int = 3)
            val query = Query<LargeTestModel>(Condition.Always, sort { it.int.ascending() })

            // Initial query result
            cache.update(CacheUpdate.QueryResult(query, listOf(model1, model2, model3)))

            // Multiple updates of the same items
            cache.update(CacheUpdate.MutationResult(listOf(model1)))
            cache.update(CacheUpdate.MutationResult(listOf(model2)))
            cache.update(CacheUpdate.MutationResult(listOf(model1, model3)))
            cache.update(CacheUpdate.MutationResult(listOf(model2)))

            val result = cache.cached(query)!!.item

            // Verify no duplicates
            val ids = result.map { it._id }
            assertEquals(ids.size, ids.toSet().size, "Found duplicate IDs in result")

            // Verify all items present
            assertEquals(3, result.size)
            assertEquals(setOf(model1._id, model2._id, model3._id), ids.toSet())
        }
    }

    @Test
    fun testQueryLimitConsistency() = runTest2 {
        verifyBothImplementations(LargeTestModel.serializer()) { cache ->
            val models = (1..10).map { LargeTestModel(int = it) }.sortedBy { it.int }
            val query = Query<LargeTestModel>(Condition.Always, sort { it.int.ascending() }, limit = 3)

            // Initial limited query
            cache.update(CacheUpdate.QueryResult(query, models.take(3)))

            var result = cache.cached(query)!!.item
            assertEquals(3, result.size, "Initial limit not respected")
            assertEquals(listOf(1, 2, 3), result.map { it.int })

            // Update an item in the result
            val updated = models[1].copy(int = 100)
            cache.update(CacheUpdate.MutationResult(listOf(updated)))

            result = cache.cached(query)!!.item
            assertTrue(result.size <= 3, "Limit exceeded after mutation: ${result.size}")

            // Add new items via multi-get
            cache.update(CacheUpdate.MultiGetResult(setOf(), models.slice(3..5)))

            result = cache.cached(query)!!.item
            assertTrue(result.size <= 3, "Limit exceeded after multi-get: ${result.size}")
        }
    }

    @Test
    fun testSortingConsistency() = runTest2 {
        verifyBothImplementations(LargeTestModel.serializer()) { cache ->
            val models = listOf(5, 2, 8, 1, 9, 3, 7, 4, 6).map { LargeTestModel(int = it) }
            val query = Query<LargeTestModel>(Condition.Always, sort { it.int.ascending() })

            // QueryResult should contain data as it would come from the server (already sorted)
            cache.update(CacheUpdate.QueryResult(query, models.sortedBy { it.int }))

            var result = cache.cached(query)!!.item
            val sorted = result.map { it.int }
            assertEquals(sorted, sorted.sorted(), "Initial result not sorted")

            // Update multiple items
            cache.update(CacheUpdate.MutationResult(listOf(
                models[0].copy(int = 50),
                models[3].copy(int = 10),
                models[5].copy(int = 30)
            )))

            result = cache.cached(query)!!.item
            val newSorted = result.map { it.int }
            assertEquals(newSorted, newSorted.sorted(), "Result not sorted after mutations")
        }
    }

    @Test
    fun testConditionFilteringAfterMutations() = runTest2 {
        verifyBothImplementations(LargeTestModel.serializer()) { cache ->
            val models = (1..10).map { LargeTestModel(int = it) }
            val queryLow = Query<LargeTestModel>(condition { it.int lt 6 }, sort { it.int.ascending() })
            val queryHigh = Query<LargeTestModel>(condition { it.int gte 6 }, sort { it.int.ascending() })

            // Initialize both queries
            cache.update(CacheUpdate.QueryResult(queryLow, models.filter { it.int < 6 }))
            cache.update(CacheUpdate.QueryResult(queryHigh, models.filter { it.int >= 6 }))

            assertEquals(listOf(1, 2, 3, 4, 5), cache.cached(queryLow)!!.item.map { it.int })
            assertEquals(listOf(6, 7, 8, 9, 10), cache.cached(queryHigh)!!.item.map { it.int })

            // Mutate item to move it from low to high
            val movedItem = models[2].copy(int = 100) // was 3, now 100
            cache.update(CacheUpdate.MutationResult(listOf(movedItem)))

            // Verify it's removed from low query
            val lowResult = cache.cached(queryLow)!!.item
            assertTrue(lowResult.none { it._id == movedItem._id },
                "Item with int=100 should not be in query for int<6")

            // Verify all items in low query satisfy the condition
            assertTrue(lowResult.all { it.int < 6 },
                "All items in low query should have int<6: ${lowResult.map { it.int }}")

            // Verify it's added to high query
            val highResult = cache.cached(queryHigh)!!.item
            assertTrue(highResult.any { it._id == movedItem._id },
                "Item with int=100 should be in query for int>=6")

            // Verify all items in high query satisfy the condition
            assertTrue(highResult.all { it.int >= 6 },
                "All items in high query should have int>=6: ${highResult.map { it.int }}")
        }
    }

    @Test
    fun testDeletionConsistency() = runTest2 {
        verifyBothImplementations(LargeTestModel.serializer()) { cache ->
            val models = (1..5).map { LargeTestModel(int = it) }
            val query1 = Query<LargeTestModel>(Condition.Always)
            val query2 = Query<LargeTestModel>(condition { it.int gt 2 })

            cache.update(CacheUpdate.QueryResult(query1, models))
            cache.update(CacheUpdate.QueryResult(query2, models.filter { it.int > 2 }))

            assertEquals(5, cache.cached(query1)!!.item.size)
            assertEquals(3, cache.cached(query2)!!.item.size)

            // Delete some items
            val deletedIds = setOf(models[1]._id, models[3]._id) // int=2 and int=4
            cache.update(CacheUpdate.DeletionResult(deletedIds))

            // Verify deleted items are gone from all queries
            val result1 = cache.cached(query1)!!.item
            val result2 = cache.cached(query2)!!.item

            assertTrue(result1.none { it._id in deletedIds },
                "Deleted items still present in query1")
            assertTrue(result2.none { it._id in deletedIds },
                "Deleted items still present in query2")

            assertEquals(3, result1.size, "Query1 should have 3 items after deletion")
            assertEquals(2, result2.size, "Query2 should have 2 items after deletion")
        }
    }

    @Test
    fun testMultipleQueriesIndependence() = runTest2 {
        verifyBothImplementations(LargeTestModel.serializer()) { cache ->
            val models = (1..10).map { LargeTestModel(int = it) }

            val queries = listOf(
                Query<LargeTestModel>(condition { it.int lt 4 }),
                Query<LargeTestModel>(condition { it.int gte 4 and (it.int lt 8) }),
                Query<LargeTestModel>(condition { it.int gte 8 })
            )

            // Initialize queries
            queries.forEachIndexed { index, query ->
                val items = when (index) {
                    0 -> models.filter { it.int < 4 }
                    1 -> models.filter { it.int >= 4 && it.int < 8 }
                    else -> models.filter { it.int >= 8 }
                }
                cache.update(CacheUpdate.QueryResult(query, items))
            }

            // Update one item
            val updated = models[5].copy(int = 50)
            cache.update(CacheUpdate.MutationResult(listOf(updated)))

            // Verify each query maintains its own filter
            queries.forEachIndexed { index, query ->
                val result = cache.cached(query)!!.item
                when (index) {
                    0 -> assertTrue(result.all { it.int < 4 }, "Query 0 violated")
                    1 -> assertTrue(result.all { it.int >= 4 && it.int < 8 }, "Query 1 violated")
                    2 -> assertTrue(result.all { it.int >= 8 }, "Query 2 violated")
                }
            }
        }
    }

    @Test
    fun testSocketUpdateTimestampHandling() = runTest2 {
        verifyBothImplementations(LargeTestModel.serializer()) { cache ->
            val model = LargeTestModel(int = 1)
            val query = Query<LargeTestModel>(Condition.Always)

            // Initial query
            cache.update(CacheUpdate.QueryResult(query, listOf(model)))
            val initialTimestamp = cache.cached(query)!!.at

            // Socket update with matching condition and recent activation
            val activeReq = object: CacheUpdate.SocketChanges.ConditionAndTimestamp<LargeTestModel> {
                override val condition = query.condition
                override val activatedAt = this@runTest2.coroutineContext[ClockContextElement]?.clock?.now()
                    ?: Clock.System.now()
            }

            val updated = model.copy(int = 2)
            cache.update(CacheUpdate.SocketChanges(
                changed = setOf(updated),
                removed = setOf(),
                fromCondition = query.condition,
                fromRequirements = setOf(activeReq)
            ))

            val newTimestamp = cache.cached(query)!!.at
            assertTrue(newTimestamp >= initialTimestamp,
                "Timestamp should update for complete socket changes")
        }
    }

    @Test
    fun testInterleavedComplexOperations() = runTest2 {
        verifyBothImplementations(LargeTestModel.serializer()) { cache ->
            val models = (1..20).map { LargeTestModel(int = it) }
            val query = Query<LargeTestModel>(Condition.Always, sort { it.int.ascending() }, limit = 10)

            // Step 1: Initial query
            cache.update(CacheUpdate.QueryResult(query, models.take(10)))
            assertEquals(10, cache.cached(query)!!.item.size)

            // Step 2: Multi-get adds more items
            cache.update(CacheUpdate.MultiGetResult(setOf(), models.slice(10..14)))
            var result = cache.cached(query)!!.item
            assertTrue(result.size <= 10, "Limit violated after multi-get")

            // Step 3: Mutation changes ordering
            cache.update(CacheUpdate.MutationResult(listOf(models[0].copy(int = 25))))
            result = cache.cached(query)!!.item
            assertTrue(result.size <= 10, "Limit violated after mutation")
            assertEquals(result.map { it.int }, result.map { it.int }.sorted(), "Sort violated")

            // Step 4: Delete some items
            cache.update(CacheUpdate.DeletionResult(setOf(models[5]._id, models[7]._id)))
            result = cache.cached(query)!!.item
            assertTrue(result.none { it._id == models[5]._id || it._id == models[7]._id })

            // Step 5: Socket change
            val activeReq = object: CacheUpdate.SocketChanges.ConditionAndTimestamp<LargeTestModel> {
                override val condition = query.condition
                override val activatedAt = this@runTest2.coroutineContext[ClockContextElement]?.clock?.now()
                    ?: Clock.System.now()
            }
            cache.update(CacheUpdate.SocketChanges(
                changed = setOf(models[2].copy(int = 30)),
                removed = setOf(),
                fromCondition = query.condition,
                fromRequirements = setOf(activeReq)
            ))

            // Final verification
            result = cache.cached(query)!!.item
            assertTrue(result.size <= 10, "Limit violated after all operations")
            assertEquals(result.map { it.int }, result.map { it.int }.sorted(), "Sort violated after all operations")

            // Verify no duplicates
            val ids = result.map { it._id }
            assertEquals(ids.size, ids.toSet().size, "Duplicates found after complex operations")
        }
    }

    @Test
    fun testEmptyQueryResults() = runTest2 {
        verifyBothImplementations(LargeTestModel.serializer()) { cache ->
            val query = Query<LargeTestModel>(condition { it.int gt 1000 })

            // Empty initial result
            cache.update(CacheUpdate.QueryResult(query, emptyList()))
            assertEquals(0, cache.cached(query)!!.item.size)

            // Add items that don't match
            val models = (1..5).map { LargeTestModel(int = it) }
            cache.update(CacheUpdate.MultiGetResult(setOf(), models))
            assertEquals(0, cache.cached(query)!!.item.size, "Non-matching items leaked into query")

            // Add item that matches
            val matching = LargeTestModel(int = 1001)
            cache.update(CacheUpdate.MutationResult(listOf(matching)))
            assertEquals(1, cache.cached(query)!!.item.size)
            assertEquals(1001, cache.cached(query)!!.item[0].int)
        }
    }

    @Test
    fun testBoundaryConditionsInLimits() = runTest2 {
        verifyBothImplementations(LargeTestModel.serializer()) { cache ->
            val models = (1..10).map { LargeTestModel(int = it) }

            // Test limit = 0 (should mean no limit)
            val queryNoLimit = Query<LargeTestModel>(Condition.Always, sort { it.int.ascending() }, limit = 0)
            cache.update(CacheUpdate.QueryResult(queryNoLimit, models))
            assertEquals(10, cache.cached(queryNoLimit)!!.item.size, "Limit=0 should return all items")

            // Test limit = 1
            val queryOne = Query<LargeTestModel>(Condition.Always, sort { it.int.ascending() }, limit = 1)
            cache.update(CacheUpdate.QueryResult(queryOne, listOf(models[0])))
            assertEquals(1, cache.cached(queryOne)!!.item.size)

            // Test limit > available items
            val queryHuge = Query<LargeTestModel>(Condition.Always, sort { it.int.ascending() }, limit = 1000)
            cache.update(CacheUpdate.QueryResult(queryHuge, models))
            assertEquals(10, cache.cached(queryHuge)!!.item.size, "Should return all available items when limit > available")
        }
    }

    @Test
    fun testItemMovingBetweenMultipleQueriesSimultaneously() = runTest2 {
        verifyBothImplementations(LargeTestModel.serializer()) { cache ->
            val model = LargeTestModel(int = 5)

            val queryLow = Query<LargeTestModel>(condition { it.int lt 5 })
            val queryMid = Query<LargeTestModel>(condition { it.int gte 5 and (it.int lt 10) })
            val queryHigh = Query<LargeTestModel>(condition { it.int gte 10 })

            // Item starts in mid query
            cache.update(CacheUpdate.QueryResult(queryLow, emptyList()))
            cache.update(CacheUpdate.QueryResult(queryMid, listOf(model)))
            cache.update(CacheUpdate.QueryResult(queryHigh, emptyList()))

            assertEquals(0, cache.cached(queryLow)!!.item.size)
            assertEquals(1, cache.cached(queryMid)!!.item.size)
            assertEquals(0, cache.cached(queryHigh)!!.item.size)

            // Move item to low query
            cache.update(CacheUpdate.MutationResult(listOf(model.copy(int = 3))))
            assertEquals(1, cache.cached(queryLow)!!.item.size)
            assertEquals(0, cache.cached(queryMid)!!.item.size)
            assertEquals(0, cache.cached(queryHigh)!!.item.size)

            // Move item to high query
            cache.update(CacheUpdate.MutationResult(listOf(model.copy(int = 15))))
            assertEquals(0, cache.cached(queryLow)!!.item.size)
            assertEquals(0, cache.cached(queryMid)!!.item.size)
            assertEquals(1, cache.cached(queryHigh)!!.item.size)

            // Move back to mid
            cache.update(CacheUpdate.MutationResult(listOf(model.copy(int = 7))))
            assertEquals(0, cache.cached(queryLow)!!.item.size)
            assertEquals(1, cache.cached(queryMid)!!.item.size)
            assertEquals(0, cache.cached(queryHigh)!!.item.size)
        }
    }

    @Test
    fun testMissingItemsInMultiGet() = runTest2 {
        verifyBothImplementations(LargeTestModel.serializer()) { cache ->
            val model1 = LargeTestModel(int = 1)
            val model2 = LargeTestModel(int = 2)
            val model3 = LargeTestModel(int = 3)
            val query = Query<LargeTestModel>(Condition.Always, sort { it.int.ascending() })

            cache.update(CacheUpdate.QueryResult(query, listOf(model1, model2, model3)))
            assertEquals(3, cache.cached(query)!!.item.size)

            // Multi-get reports model2 as missing
            cache.update(CacheUpdate.MultiGetResult(
                missing = setOf(model2._id),
                result = listOf(model1, model3)
            ))

            val result = cache.cached(query)!!.item
            assertEquals(2, result.size, "Missing item should be removed")
            assertTrue(result.none { it._id == model2._id }, "Missing item still present")
            assertEquals(setOf(model1._id, model3._id), result.map { it._id }.toSet())
        }
    }

    @Test
    fun testStressTestManyItems() = runTest2 {
        verifyBothImplementations(LargeTestModel.serializer()) { cache ->
            val models = (1..100).map { LargeTestModel(int = it) }
            val query = Query<LargeTestModel>(
                condition { it.int gte 20 and (it.int lt 80) },
                sort { it.int.ascending() }
            )

            // Initial query
            val filtered = models.filter { it.int >= 20 && it.int < 80 }
            cache.update(CacheUpdate.QueryResult(query, filtered))

            assertEquals(60, cache.cached(query)!!.item.size)

            // Perform many mutations
            val mutations = (0..99 step 10).map { idx ->
                models[idx].copy(int = models[idx].int + 1000)
            }
            cache.update(CacheUpdate.MutationResult(mutations))

            val result = cache.cached(query)!!.item

            // Verify all items satisfy the condition
            assertTrue(result.all { it.int >= 20 && it.int < 80 },
                "Some items don't match query condition after mutations")

            // Verify sorted
            assertEquals(result.map { it.int }, result.map { it.int }.sorted())

            // Verify no duplicates
            val ids = result.map { it._id }
            assertEquals(ids.size, ids.toSet().size)
        }
    }

    @Test
    fun testMultipleIdenticalMutations() = runTest2 {
        verifyBothImplementations(LargeTestModel.serializer()) { cache ->
            val model = LargeTestModel(int = 1)
            val query = Query<LargeTestModel>(Condition.Always)

            cache.update(CacheUpdate.QueryResult(query, listOf(model)))

            // Apply same mutation multiple times
            val updated = model.copy(int = 2)
            repeat(5) {
                cache.update(CacheUpdate.MutationResult(listOf(updated)))
            }

            val result = cache.cached(query)!!.item
            assertEquals(1, result.size, "Should still have exactly one item")
            assertEquals(2, result[0].int)
            assertEquals(model._id, result[0]._id)
        }
    }

    @Test
    fun testDescendingSortOrder() = runTest2 {
        verifyBothImplementations(LargeTestModel.serializer()) { cache ->
            val models = (1..10).map { LargeTestModel(int = it) }
            val query = Query<LargeTestModel>(
                Condition.Always,
                sort { it.int.descending() }
            )

            cache.update(CacheUpdate.QueryResult(query, models.sortedByDescending { it.int }))

            val result = cache.cached(query)!!.item
            assertEquals(listOf(10, 9, 8, 7, 6, 5, 4, 3, 2, 1), result.map { it.int })

            // Update and verify still descending
            cache.update(CacheUpdate.MutationResult(listOf(models[0].copy(int = 15))))

            val newResult = cache.cached(query)!!.item
            val ints = newResult.map { it.int }
            assertEquals(ints, ints.sortedDescending(), "Should maintain descending order after mutation")
        }
    }

    @Test
    fun testSocketChangesWithPartialKnowledge() = runTest2 {
        verifyBothImplementations(LargeTestModel.serializer()) { cache ->
            val models = (1..5).map { LargeTestModel(int = it) }
            val query = Query<LargeTestModel>(Condition.Always)

            cache.update(CacheUpdate.QueryResult(query, models))

            // Socket update without matching requirement (partial knowledge)
            val updated = models[2].copy(int = 30)
            cache.update(CacheUpdate.SocketChanges(
                changed = setOf(updated),
                removed = setOf(),
                fromCondition = query.condition,
                fromRequirements = emptySet() // No matching requirement
            ))

            val result = cache.cached(query)!!.item

            // Should still include the updated item
            assertTrue(result.any { it._id == models[2]._id })

            // Updated value should be reflected
            val updatedInResult = result.first { it._id == models[2]._id }
            assertEquals(30, updatedInResult.int)
        }
    }

    @Test
    fun testSocketRemovalWithCompleteUpdate() = runTest2 {
        verifyBothImplementations(LargeTestModel.serializer()) { cache ->
            val models = (1..5).map { LargeTestModel(int = it) }
            val query = Query<LargeTestModel>(Condition.Always)

            cache.update(CacheUpdate.QueryResult(query, models))
            assertEquals(5, cache.cached(query)!!.item.size)

            // Complete socket update with removal
            val activeReq = object: CacheUpdate.SocketChanges.ConditionAndTimestamp<LargeTestModel> {
                override val condition = query.condition
                override val activatedAt = this@runTest2.coroutineContext[ClockContextElement]?.clock?.now()
                    ?: Clock.System.now()
            }

            cache.update(CacheUpdate.SocketChanges(
                changed = setOf(),
                removed = setOf(models[1]._id, models[3]._id),
                fromCondition = query.condition,
                fromRequirements = setOf(activeReq)
            ))

            val result = cache.cached(query)!!.item
            assertEquals(3, result.size)
            assertTrue(result.none { it._id == models[1]._id || it._id == models[3]._id })
        }
    }

    @Test
    fun testClearAndRebuild() = runTest2 {
        verifyBothImplementations(LargeTestModel.serializer()) { cache ->
            val models = (1..10).map { LargeTestModel(int = it) }
            val query = Query<LargeTestModel>(Condition.Always)

            // Build up cache
            cache.update(CacheUpdate.QueryResult(query, models))
            assertEquals(10, cache.cached(query)!!.item.size)

            // Clear
            cache.clear()
            assertEquals(null, cache.cached(query))

            // Rebuild
            cache.update(CacheUpdate.QueryResult(query, models.take(5)))
            assertEquals(5, cache.cached(query)!!.item.size)
        }
    }

    @Test
    fun testComplexConditionWithMultipleFields() = runTest2 {
        verifyBothImplementations(LargeTestModel.serializer()) { cache ->
            val models = listOf(
                LargeTestModel(int = 1, string = "a"),
                LargeTestModel(int = 2, string = "b"),
                LargeTestModel(int = 3, string = "a"),
                LargeTestModel(int = 4, string = "b"),
                LargeTestModel(int = 5, string = "a")
            )

            val query = Query<LargeTestModel>(
                condition { (it.int gt 2) and (it.string eq "a") },
                sort { it.int.ascending() }
            )

            cache.update(CacheUpdate.QueryResult(query, models.filter { it.int > 2 && it.string == "a" }))

            val initial = cache.cached(query)!!.item
            assertEquals(2, initial.size)
            assertEquals(listOf(3, 5), initial.map { it.int })

            // Mutate to remove from query (change string)
            cache.update(CacheUpdate.MutationResult(listOf(models[2].copy(string = "b"))))

            var result = cache.cached(query)!!.item
            assertEquals(1, result.size)
            assertTrue(result.all { it.int > 2 && it.string == "a" })

            // Mutate to add to query
            cache.update(CacheUpdate.MutationResult(listOf(models[1].copy(int = 10, string = "a"))))

            result = cache.cached(query)!!.item
            assertEquals(2, result.size)
            assertTrue(result.all { it.int > 2 && it.string == "a" })
        }
    }
}
