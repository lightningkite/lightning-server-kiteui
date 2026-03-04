package com.lightningkite.lightningserver.db

import kotlin.uuid.Uuid
import com.lightningkite.kiteui.Log
import com.lightningkite.kiteui.Platform
import com.lightningkite.kiteui.current
import com.lightningkite.services.ClockContextElement
import com.lightningkite.services.database.*
import com.lightningkite.services.default
import kotlin.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Clock

class NaiveListReconstructionCalculatorTest {
    val testLog = if(Platform.current == Platform.Desktop) Log else null

    @Test
    fun basic() = runTest2 {
        val mock = ClientModelRestEndpointsMock<LargeTestModel, Uuid>(this)
//        val mock = ClientModelRestEndpointsPlusUpdatesWebsocketMock<LargeTestModel, Uuid>()
        val dataToInsert = listOf(
            LargeTestModel(int = 1),
            LargeTestModel(int = 2),
            LargeTestModel(int = 3),
            LargeTestModel(int = 4),
            LargeTestModel(int = 5),
        ).sortedBy { it._id }
        mock.data.putAll(dataToInsert.associateBy { it._id })
        val cache = NaiveListReconstructionCalculator(LargeTestModel.serializer(), clock = Clock.default())

        val query = Query(condition { it.int gt 4 })
        val recommended = cache.recommendQuery(query)
        val result = mock.query(recommended)
        cache.update(CacheUpdate.QueryResult(recommended, result))
        assertEquals(result, cache.cached(query)!!.item)
    }

    @Test fun socketChange() = runTest2 {
        val cache = NaiveListReconstructionCalculator(LargeTestModel.serializer(), clock = Clock.default())

        val before = LargeTestModel(int = 1)
        val after = before.copy(int = 2)
        val query = Query<LargeTestModel>(Condition.Always)
        val activeReq = object: CacheUpdate.SocketChanges.ConditionAndTimestamp<LargeTestModel> {
            override val condition: Condition<LargeTestModel> = query.condition
            override val activatedAt: Instant? = this@runTest2.coroutineContext[ClockContextElement]?.clock?.now() ?: Clock.System.now()
        }
        cache.update(CacheUpdate.QueryResult(query, listOf(before)))
        cache.update(CacheUpdate.SocketChanges(setOf(after), setOf(), query.condition, setOf(activeReq)))
        assertEquals(listOf(after), cache.cached(query)!!.item)
    }

    @Test fun socketChangeRemove() = runTest2 {
        val cache = NaiveListReconstructionCalculator(LargeTestModel.serializer(), clock = Clock.default())

        val before = LargeTestModel(int = 1)
        val after = before.copy(int = 2)
        val query = Query<LargeTestModel>(condition { it.int.lt(2) })
        val activeReq = object: CacheUpdate.SocketChanges.ConditionAndTimestamp<LargeTestModel> {
            override val condition: Condition<LargeTestModel> = query.condition
            override val activatedAt: Instant? = this@runTest2.coroutineContext[ClockContextElement]?.clock?.now() ?: Clock.System.now()
        }
        cache.update(CacheUpdate.QueryResult(query, listOf(before)))
        cache.update(CacheUpdate.SocketChanges(setOf(after), setOf(), query.condition, setOf(activeReq)))
        assertEquals(listOf(), cache.cached(query)!!.item)
    }

    @Test fun deletionResult() = runTest2 {
        val cache = NaiveListReconstructionCalculator(LargeTestModel.serializer(), clock = Clock.default())

        val model1 = LargeTestModel(int = 1)
        val model2 = LargeTestModel(int = 2)
        val model3 = LargeTestModel(int = 3)
        val query = Query<LargeTestModel>(Condition.Always, sort { it.int.ascending() })

        // Add initial data
        cache.update(CacheUpdate.QueryResult(query, listOf(model1, model2, model3)))
        assertEquals(listOf(model1, model2, model3), cache.cached(query)!!.item)

        // Delete model2
        cache.update(CacheUpdate.DeletionResult(setOf(model2._id)))

        // Check that model2 is removed (order may vary)
        val result = cache.cached(query)!!.item
        assertEquals(2, result.size)
        assertEquals(setOf(model1._id, model3._id), result.map { it._id }.toSet())
    }

    @Test fun multiGetResult() = runTest2 {
        val cache = NaiveListReconstructionCalculator(LargeTestModel.serializer(), clock = Clock.default())

        val model1 = LargeTestModel(int = 1)
        val model2 = LargeTestModel(int = 2)
        val model3 = LargeTestModel(int = 3)
        val query = Query<LargeTestModel>(Condition.Always, sort { it.int.ascending() })

        // Add initial data
        cache.update(CacheUpdate.QueryResult(query, listOf(model1)))
        assertEquals(listOf(model1), cache.cached(query)!!.item)

        // Add more data via MultiGetResult
        cache.update(CacheUpdate.MultiGetResult(setOf(), listOf(model2, model3)))

        // Check that all models are included (order should be by int)
        val result = cache.cached(query)!!.item
        assertEquals(3, result.size)
        assertEquals(listOf(1, 2, 3), result.map { it.int })
    }

    @Test fun mutationResult() = runTest2 {
        val cache = NaiveListReconstructionCalculator(LargeTestModel.serializer(), clock = Clock.default())

        val model1 = LargeTestModel(int = 1)
        val model2 = LargeTestModel(int = 2)
        val updatedModel1 = model1.copy(int = 10)
        val query = Query<LargeTestModel>(Condition.Always, sort { it.int.ascending() })

        // Add initial data
        cache.update(CacheUpdate.QueryResult(query, listOf(model1, model2)))
        assertEquals(listOf(model1, model2), cache.cached(query)!!.item)

        // Update model1 via MutationResult
        cache.update(CacheUpdate.MutationResult(listOf(updatedModel1)))

        // Check that model1 is updated (order should be by int)
        val result = cache.cached(query)!!.item
        assertEquals(2, result.size)
        assertEquals(setOf(model2._id, updatedModel1._id), result.map { it._id }.toSet())
        assertEquals(setOf(2, 10), result.map { it.int }.toSet())

        // Verify the order (model2 should be first since it has a lower int value)
        assertEquals(2, result[0].int)
        assertEquals(10, result[1].int)
    }

    @Test fun socketOverload() = runTest2 {
        val cache = NaiveListReconstructionCalculator(LargeTestModel.serializer(), clock = Clock.default())

        val model1 = LargeTestModel(int = 1)
        val query = Query<LargeTestModel>(Condition.Always)

        // Add initial data
        cache.update(CacheUpdate.QueryResult(query, listOf(model1)))
        assertEquals(listOf(model1), cache.cached(query)!!.item)

        // Socket overload should clear the cache
        cache.update(CacheUpdate.SocketOverload())
        assertNull(cache.cached(query))
    }

    @Test fun queryWithLimit() = runTest2 {
        val mock = ClientModelRestEndpointsMock<LargeTestModel, Uuid>(this)
        val dataToInsert = listOf(
            LargeTestModel(int = 1),
            LargeTestModel(int = 2),
            LargeTestModel(int = 3),
            LargeTestModel(int = 4),
            LargeTestModel(int = 5),
        ).sortedBy { it._id }
        mock.data.putAll(dataToInsert.associateBy { it._id })
        val cache = NaiveListReconstructionCalculator(LargeTestModel.serializer(), clock = Clock.default())

        // Query with limit 2
        val query = Query<LargeTestModel>(Condition.Always, limit = 2)
        val recommended = cache.recommendQuery(query)
        val result = mock.query(recommended)
        cache.update(CacheUpdate.QueryResult(recommended, result))

        // Should only have 2 items
        assertEquals(2, cache.cached(query)!!.item.size)
    }

    @Test fun queryWithSorting() = runTest2 {
        val mock = ClientModelRestEndpointsMock<LargeTestModel, Uuid>(this)
        val dataToInsert = listOf(
            LargeTestModel(int = 5),
            LargeTestModel(int = 3),
            LargeTestModel(int = 1),
            LargeTestModel(int = 4),
            LargeTestModel(int = 2),
        )
        mock.data.putAll(dataToInsert.associateBy { it._id })
        val cache = NaiveListReconstructionCalculator(LargeTestModel.serializer(), clock = Clock.default())

        // Query with sorting by int ascending
        val query = Query<LargeTestModel>(
            Condition.Always, 
            orderBy = sort { it.int.ascending() }
        )
        val recommended = cache.recommendQuery(query)
        val result = mock.query(recommended)
        cache.update(CacheUpdate.QueryResult(recommended, result))

        // Should be sorted by int
        val cached = cache.cached(query)!!.item
        assertEquals(listOf(1, 2, 3, 4, 5), cached.map { it.int })
    }

    @Test fun handleDuplicateItems() = runTest2 {
        val cache = NaiveListReconstructionCalculator(LargeTestModel.serializer(), clock = Clock.default())

        val model1 = LargeTestModel(int = 1)
        val model2 = LargeTestModel(int = 2)
        val query = Query<LargeTestModel>(Condition.Always, sort { it.int.ascending() })

        // Add initial data
        cache.update(CacheUpdate.QueryResult(query, listOf(model1, model2)))
        assertEquals(listOf(model1, model2), cache.cached(query)!!.item)

        // Add duplicate data
        cache.update(CacheUpdate.MutationResult(listOf(model1)))

        // Should not have duplicates
        val cached = cache.cached(query)!!.item
        assertEquals(2, cached.size)
        assertEquals(setOf(model1._id, model2._id), cached.map { it._id }.toSet())

        // Verify the order (sorted by int)
        assertEquals(1, cached[0].int)
        assertEquals(2, cached[1].int)
    }

    @Test fun totalityIssue() = runTest2 {
        val cache = NaiveListReconstructionCalculator(LargeTestModel.serializer(), clock = Clock.default())

        // Create models with sequential int values
        val model1 = LargeTestModel(int = 1)
        val model2 = LargeTestModel(int = 2)
        val model3 = LargeTestModel(int = 3)
        val model4 = LargeTestModel(int = 4)
        val model5 = LargeTestModel(int = 5)

        // Query for int > 2
        val query = Query<LargeTestModel>(
            condition { it.int gt 2 },
            orderBy = sort { it.int.ascending() }
        )

        // Add initial data for int > 2
        cache.update(CacheUpdate.QueryResult(query, listOf(model3, model4, model5)))

        // Verify initial data
        val initialResult = cache.cached(query)!!.item
        assertEquals(3, initialResult.size)
        assertEquals(setOf(3, 4, 5), initialResult.map { it.int }.toSet())

        // Verify order
        assertEquals(3, initialResult[0].int)
        assertEquals(4, initialResult[1].int)
        assertEquals(5, initialResult[2].int)

        // Add a new model with int = 6 via MutationResult
        val model6 = LargeTestModel(int = 6)
        cache.update(CacheUpdate.MutationResult(listOf(model6)))

        // Verify model6 is added
        val resultWithModel6 = cache.cached(query)!!.item
        assertEquals(4, resultWithModel6.size)
        assertEquals(setOf(3, 4, 5, 6), resultWithModel6.map { it.int }.toSet())

        // Verify order
        assertEquals(3, resultWithModel6[0].int)
        assertEquals(4, resultWithModel6[1].int)
        assertEquals(5, resultWithModel6[2].int)
        assertEquals(6, resultWithModel6[3].int)

        // Add a new model with int = 25 (representing 2.5) via MutationResult
        // This should be properly sorted based on its int value
        val model2_5 = LargeTestModel(int = 25, string = "2.5")
        cache.update(CacheUpdate.MutationResult(listOf(model2_5)))

        // Verify all models are included
        val finalResult = cache.cached(query)!!.item
        assertEquals(5, finalResult.size)
        assertEquals(setOf(3, 4, 5, 6, 25), finalResult.map { it.int }.toSet())

        // Verify the order - models should be sorted by int value
        // Since model2_5 has int=25, it should be last in the sorted list
        assertEquals(3, finalResult[0].int)
        assertEquals(4, finalResult[1].int)
        assertEquals(5, finalResult[2].int)
        assertEquals(6, finalResult[3].int)
        assertEquals(25, finalResult[4].int)
    }
}
