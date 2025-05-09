package com.lightningkite.lightningserver.db

import com.lightningkite.UUID
import com.lightningkite.default
import com.lightningkite.kiteui.ConsoleRoot
import com.lightningkite.kiteui.Platform
import com.lightningkite.kiteui.current
import com.lightningkite.kiteui.forms.prepareModelsClient
import com.lightningkite.lightningdb.Condition
import com.lightningkite.lightningdb.Query
import com.lightningkite.lightningdb.condition
import com.lightningkite.lightningdb.gt
import com.lightningkite.lightningdb.lt
import com.lightningkite.lightningdb.modification
import com.lightningkite.lightningdb.sort
import com.lightningkite.prepareModelsClientTest
import com.lightningkite.prepareModelsShared
import com.lightningkite.readable.reactive
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class ModelCacheTest {
    val testLog = if(Platform.current == Platform.Desktop) ConsoleRoot else null
    init {
        prepareModelsShared()
        prepareModelsClient()
        prepareModelsClientTest()
    }

    @Test fun listLimitIncrease() = runTest2 {
        val mock = ClientModelRestEndpointsMock<LargeTestModel, UUID>(this)
//        val mock = ClientModelRestEndpointsPlusUpdatesWebsocketMock<LargeTestModel, UUID>(this)
        val dataToInsert = listOf(
            LargeTestModel(int = 1),
            LargeTestModel(int = 2),
            LargeTestModel(int = 3),
            LargeTestModel(int = 4),
            LargeTestModel(int = 5),
        )
        mock.data.putAll(dataToInsert.associateBy { it._id })
        val cache = ModelCache<LargeTestModel, UUID>(
            mock,
            LargeTestModel.serializer(),
            scope = backgroundScope,
            log = testLog
        )

        var lastRead: List<LargeTestModel> = listOf()
        val ref = cache.list(Query(Condition.Always, sort { it.int.ascending() }, limit = 2))
        reactive {
            lastRead = ref()
        }

        delay(5.seconds)
        assertEquals(dataToInsert.take(2), lastRead)
        ref.limit = 10
        delay(1.seconds)
        assertEquals(dataToInsert.take(10), lastRead)
    }

    @Test
    fun listChangesWs() = runTest2 {
//        val mock = ClientModelRestEndpointsMock<LargeTestModel, UUID>(this)
        val mock = ClientModelRestEndpointsPlusUpdatesWebsocketMock<LargeTestModel, UUID>(this)
        val dataToInsert = listOf(
            LargeTestModel(int = 1),
            LargeTestModel(int = 2),
            LargeTestModel(int = 3),
            LargeTestModel(int = 4),
            LargeTestModel(int = 5),
        )
        mock.data.putAll(dataToInsert.associateBy { it._id })
        val cache = ModelCache<LargeTestModel, UUID>(
            mock,
            LargeTestModel.serializer(),
            scope = backgroundScope,
            log = testLog
        )

        var currentValue = dataToInsert[2]

        val ref = cache.list(Query(condition { it.int gt 2 }, sort { it.int.ascending() }))
        reactive {
            assertContains(
                ref().also(::println),
                currentValue,
            )
        }
        delay(7.seconds)

        val mod = modification<LargeTestModel> { it.short assign 2 }
        currentValue = mod(currentValue)
        mock.modify(currentValue._id, mod)
        delay(5.seconds)
    }

    @Test
    fun listChangesPull() = runTest2 {
        val mock = ClientModelRestEndpointsMock<LargeTestModel, UUID>(this)
//        val mock = ClientModelRestEndpointsPlusUpdatesWebsocketMock<LargeTestModel, UUID>(this)
        val dataToInsert = listOf(
            LargeTestModel(int = 1),
            LargeTestModel(int = 2),
            LargeTestModel(int = 3),
            LargeTestModel(int = 4),
            LargeTestModel(int = 5),
        )
        mock.data.putAll(dataToInsert.associateBy { it._id })
        val cache = ModelCache<LargeTestModel, UUID>(
            mock,
            LargeTestModel.serializer(),
            scope = backgroundScope,
            log = testLog
        )

        var currentValue = dataToInsert[2]
        var lastRead: List<LargeTestModel>? = null
        reactive {
            val ref = cache.list(Query(condition { it.int gt 2 }, sort { it.int.ascending() }))
            lastRead = ref()
        }
        delay(30.seconds)
        assertContains(
            lastRead ?: setOf(),
            currentValue,
        )

        val mod = modification<LargeTestModel> { it.short assign 2 }
        currentValue = mod(currentValue)
        mock.modify(currentValue._id, mod)
        delay(80.seconds)
        assertContains(
            lastRead ?: setOf(),
            currentValue,
        )
    }

    @Test
    fun individualChangesWs() = runTest2 {
//        val mock = ClientModelRestEndpointsMock<LargeTestModel, UUID>(this)
        val mock = ClientModelRestEndpointsPlusUpdatesWebsocketMock<LargeTestModel, UUID>(this)
        var currentValue = LargeTestModel(int = 0)
        mock.data[currentValue._id] = currentValue
        val cache = ModelCache<LargeTestModel, UUID>(
            mock,
            LargeTestModel.serializer(),
            scope = backgroundScope,
            log = testLog
        )

        reactive {
            val ref = cache.item(currentValue._id)
            assertEquals(
                currentValue,
                ref().also(::println)
            )
        }
        delay(5.seconds)

        val mod = modification<LargeTestModel> { it.short assign 2 }
        currentValue = mod(currentValue)
        mock.modify(currentValue._id, mod)
        delay(5.seconds)
    }

    @Test
    fun individualChangesPull() = runTest2 {
        val mock = ClientModelRestEndpointsMock<LargeTestModel, UUID>(this)
//        val mock = ClientModelRestEndpointsPlusUpdatesWebsocketMock<LargeTestModel, UUID>(this)
        var currentValue = LargeTestModel(int = 0)
        var lastRead: LargeTestModel? = null
        mock.data[currentValue._id] = currentValue
        val cache = ModelCache<LargeTestModel, UUID>(
            mock,
            LargeTestModel.serializer(),
            scope = backgroundScope,
            log = testLog
        )

        reactive {
            val ref = cache.item(currentValue._id)
            val read = ref().also(::println)
            lastRead = read
        }
        delay(30.seconds)
        assertEquals(
            currentValue,
            lastRead
        )

        val mod = modification<LargeTestModel> { it.short assign 2 }
        currentValue = mod(currentValue)
        mock.modify(currentValue._id, mod)
        delay(80.seconds)
        assertEquals(
            currentValue,
            lastRead
        )
    }

    @Test
    fun reactiveKeepId() = runTest2 {
        val mock = ClientModelRestEndpointsMock<LargeTestModel, UUID>(this)
//        val mock = ClientModelRestEndpointsPlusUpdatesWebsocketMock<LargeTestModel, UUID>(this)
        val dataToInsert = listOf(
            LargeTestModel(int = 1),
            LargeTestModel(int = 2),
            LargeTestModel(int = 3),
            LargeTestModel(int = 4),
            LargeTestModel(int = 5),
        )
        mock.data.putAll(dataToInsert.associateBy { it._id })
        val cache = ModelCache<LargeTestModel, UUID>(
            mock,
            LargeTestModel.serializer(),
            scope = backgroundScope,
            log = testLog
        )

        var reactions = 0
        reactive {
            reactions++
            val ref = cache.item(dataToInsert[0]._id)
            assertEquals(
                dataToInsert[0],
                ref().also(::println)
            )
        }
        advanceTimeBy(30.seconds)
        assertEquals(reactions, 2) // One for startup, one for initial data
    }

    @Test
    fun repullingGet() = runTest2 {
        val mock = ClientModelRestEndpointsMock<LargeTestModel, UUID>(this)
//        val mock = ClientModelRestEndpointsPlusUpdatesWebsocketMock<LargeTestModel, UUID>(this)
        val dataToInsert = listOf(
            LargeTestModel(int = 1),
            LargeTestModel(int = 2),
            LargeTestModel(int = 3),
            LargeTestModel(int = 4),
            LargeTestModel(int = 5),
        )
        mock.data.putAll(dataToInsert.associateBy { it._id })
        val cache = ModelCache<LargeTestModel, UUID>(
            mock,
            LargeTestModel.serializer(),
            scope = backgroundScope,
            log = testLog
        )

        val ref = cache.item(dataToInsert[0]._id)
        reactive {
            assertEquals(
                dataToInsert[0],
                ref().also(::println)
            )
        }
        advanceTimeBy(30.seconds)
    }

    @Test
    fun wsGet() = runTest2 {
//        val mock = ClientModelRestEndpointsMock<LargeTestModel, UUID>(this)
        val mock = ClientModelRestEndpointsPlusUpdatesWebsocketMock<LargeTestModel, UUID>(this)
        val dataToInsert = listOf(
            LargeTestModel(int = 1),
            LargeTestModel(int = 2),
            LargeTestModel(int = 3),
            LargeTestModel(int = 4),
            LargeTestModel(int = 5),
        )
        mock.data.putAll(dataToInsert.associateBy { it._id })
        val cache = ModelCache<LargeTestModel, UUID>(
            mock,
            LargeTestModel.serializer(),
            scope = backgroundScope,
            log = testLog
        )

        val ref = cache.item(dataToInsert[0]._id)
        reactive {
            assertEquals(
                dataToInsert[0],
                ref().also(::println)
            )
        }
        advanceTimeBy(30.seconds)
    }

    @Test
    fun repullingList() = runTest2 {
        val mock = ClientModelRestEndpointsMock<LargeTestModel, UUID>(this)
//        val mock = ClientModelRestEndpointsPlusUpdatesWebsocketMock<LargeTestModel, UUID>(this)
        val dataToInsert = listOf(
            LargeTestModel(int = 1),
            LargeTestModel(int = 2),
            LargeTestModel(int = 3),
            LargeTestModel(int = 4),
            LargeTestModel(int = 5),
        )
        mock.data.putAll(dataToInsert.associateBy { it._id })
        val cache = ModelCache<LargeTestModel, UUID>(
            mock,
            LargeTestModel.serializer(),
            scope = backgroundScope,
            log = testLog
        )

        val ref = cache.list(Query(condition { it.int lt 4 }, orderBy = sort { it.int.ascending() }))
        reactive {
            assertEquals(
                dataToInsert.filter { it.int < 4 }.sortedBy { it.int },
                ref().also(::println)
            )
        }
        advanceTimeBy(30.seconds)
    }

    @Test
    fun reactivityKeepList() = runTest2 {
        val mock = ClientModelRestEndpointsMock<LargeTestModel, UUID>(this)
//        val mock = ClientModelRestEndpointsPlusUpdatesWebsocketMock<LargeTestModel, UUID>(this)
        val dataToInsert = listOf(
            LargeTestModel(int = 1),
            LargeTestModel(int = 2),
            LargeTestModel(int = 3),
            LargeTestModel(int = 4),
            LargeTestModel(int = 5),
        )
        mock.data.putAll(dataToInsert.associateBy { it._id })
        val cache = ModelCache<LargeTestModel, UUID>(
            mock,
            LargeTestModel.serializer(),
            scope = backgroundScope,
            log = testLog
        )

        var reactions = 0
        reactive {
            reactions++
            val ref = cache.list(Query(condition { it.int lt 4 }, orderBy = sort { it.int.ascending() }))
            assertEquals(
                dataToInsert.filter { it.int < 4 }.sortedBy { it.int },
                ref().also(::println)
            )
        }
        advanceTimeBy(30.seconds)
        assertEquals(reactions, 2) // One for startup, one for initial data
    }

    @Test
    fun wsList() = runTest2 {
//        val mock = ClientModelRestEndpointsMock<LargeTestModel, UUID>(this)
        val mock = ClientModelRestEndpointsPlusUpdatesWebsocketMock<LargeTestModel, UUID>(this)
        val dataToInsert = listOf(
            LargeTestModel(int = 1),
            LargeTestModel(int = 2),
            LargeTestModel(int = 3),
            LargeTestModel(int = 4),
            LargeTestModel(int = 5),
        )
        mock.data.putAll(dataToInsert.associateBy { it._id })
        val cache = ModelCache<LargeTestModel, UUID>(
            mock,
            LargeTestModel.serializer(),
            scope = backgroundScope,
            log = testLog
        )

        val ref = cache.list(Query(condition { it.int lt 4 }, orderBy = sort { it.int.ascending() }))
        reactive {
            assertEquals(
                dataToInsert.filter { it.int < 4 }.sortedBy { it.int },
                ref().also(::println)
            )
        }
        advanceTimeBy(30.seconds)
    }

    @Test
    fun getGone() = runTest2 {
        val mock = ClientModelRestEndpointsMock<LargeTestModel, UUID>(this)
//        val mock = ClientModelRestEndpointsPlusUpdatesWebsocketMock<LargeTestModel, UUID>(this)
        val dataToInsert = listOf(
            LargeTestModel(int = 1),
            LargeTestModel(int = 2),
            LargeTestModel(int = 3),
            LargeTestModel(int = 4),
            LargeTestModel(int = 5),
        )
        mock.data.putAll(dataToInsert.associateBy { it._id })
        val cache = ModelCache<LargeTestModel, UUID>(
            mock,
            LargeTestModel.serializer(),
            scope = backgroundScope,
            log = testLog
        )

        var runs = 0
        val ref = cache.item(UUID.random())
        reactive {
            assertEquals(
                null,
                ref().also(::println)
            )
            runs++
        }
        advanceTimeBy(30.seconds)
        assertTrue(runs >= 1)
    }

    /*
    TO TEST:
    Listen, stop, add, reconnect
     */
}