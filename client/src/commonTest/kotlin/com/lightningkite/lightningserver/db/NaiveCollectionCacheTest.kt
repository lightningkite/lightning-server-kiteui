package com.lightningkite.lightningserver.db

import com.lightningkite.UUID
import com.lightningkite.kiteui.ConsoleRoot
import com.lightningkite.kiteui.Platform
import com.lightningkite.kiteui.current
import com.lightningkite.kiteui.forms.prepareModelsClient
import com.lightningkite.lightningdb.Condition
import com.lightningkite.lightningdb.Query
import com.lightningkite.lightningdb.condition
import com.lightningkite.lightningdb.gt
import com.lightningkite.lightningdb.lt
import com.lightningkite.now
import com.lightningkite.prepareModelsClientTest
import com.lightningkite.prepareModelsShared
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

class NaiveCollectionCacheTest {
    val testLog = if(Platform.current == Platform.Desktop) ConsoleRoot else null

    init {
        prepareModelsShared()
        prepareModelsClient()
        prepareModelsClientTest()
    }

    @Test
    fun basic() = runTest2 {
        val mock = ClientModelRestEndpointsMock<LargeTestModel, UUID>(this)
//        val mock = ClientModelRestEndpointsPlusUpdatesWebsocketMock<LargeTestModel, UUID>()
        val dataToInsert = listOf(
            LargeTestModel(int = 1),
            LargeTestModel(int = 2),
            LargeTestModel(int = 3),
            LargeTestModel(int = 4),
            LargeTestModel(int = 5),
        ).sortedBy { it._id }
        mock.data.putAll(dataToInsert.associateBy { it._id })
        val cache = NaiveListReconstructionCalculator(LargeTestModel.serializer())

        val query = Query(condition { it.int gt 4 })
        val recommended = cache.recommendQuery(query)
        val result = mock.query(recommended)
        cache.update(CacheUpdate.QueryResult(recommended, result))
        assertEquals(result, cache.cached(query)!!.item)
    }

    @Test fun socketChange() = runTest2 {
        val cache = NaiveListReconstructionCalculator(LargeTestModel.serializer())

        val before = LargeTestModel(int = 1)
        val after = before.copy(int = 2)
        val query = Query<LargeTestModel>(Condition.Always)
        val activeReq = object: CacheUpdate.SocketChanges.ConditionAndTimestamp<LargeTestModel> {
            override val condition: Condition<LargeTestModel> = query.condition
            override val activatedAt: Instant? = now()
        }
        cache.update(CacheUpdate.QueryResult(query, listOf(before)))
        cache.update(CacheUpdate.SocketChanges(setOf(after), setOf(), query.condition, setOf(activeReq)))
        assertEquals(listOf(after), cache.cached(query)!!.item)
    }

    @Test fun socketChangeRemove() = runTest2 {
        val cache = NaiveListReconstructionCalculator(LargeTestModel.serializer())

        val before = LargeTestModel(int = 1)
        val after = before.copy(int = 2)
        val query = Query<LargeTestModel>(condition { it.int.lt(2) })
        val activeReq = object: CacheUpdate.SocketChanges.ConditionAndTimestamp<LargeTestModel> {
            override val condition: Condition<LargeTestModel> = query.condition
            override val activatedAt: Instant? = now()
        }
        cache.update(CacheUpdate.QueryResult(query, listOf(before)))
        cache.update(CacheUpdate.SocketChanges(setOf(after), setOf(), query.condition, setOf(activeReq)))
        assertEquals(listOf(), cache.cached(query)!!.item)
    }
}