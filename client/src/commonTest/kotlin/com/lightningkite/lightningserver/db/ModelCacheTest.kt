package com.lightningkite.lightningserver.db

import com.lightningkite.UUID
import com.lightningkite.kiteui.ConsoleRoot
import com.lightningkite.kiteui.Platform
import com.lightningkite.kiteui.current
import com.lightningkite.kiteui.forms.prepareModelsClient
import com.lightningkite.lightningdb.*
import com.lightningkite.prepareModelsClientTest
import com.lightningkite.prepareModelsShared
import com.lightningkite.reactive.context.reactive
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.advanceTimeBy
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class ModelCacheTest {
    val testLog = if(Platform.current == Platform.Desktop) ConsoleRoot else null
    init {
        prepareModelsShared()
        prepareModelsClient()
        prepareModelsClientTest()
    }

    @Test fun connectivityIssue() = runTest2 {
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
                ref().also(::println)//.also { lastReceived = it }
            )
        }
        delay(5.seconds)

        // Start out as working
        val mod = modification<LargeTestModel> { it.short assign 2 }
        currentValue = mod(currentValue)
        mock.modify(currentValue._id, mod)
        delay(5.seconds)

        println("Break!")
        mock.connectivityFailure = true
        delay(5.minutes)

        println("Reconnect")
        mock.connectivityFailure = false
        delay(5.seconds)
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
        println("Increasing limit...")
        ref.limit = 10
        println("Limit Increased.")
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

        @Test
    fun listenStopAddReconnect() = runTest2 {
        val mock = ClientModelRestEndpointsPlusUpdatesWebsocketMock<LargeTestModel, UUID>(this)
        val dataToInsert = listOf(
            LargeTestModel(int = 1),
            LargeTestModel(int = 2),
            LargeTestModel(int = 3),
        )
        mock.data.putAll(dataToInsert.associateBy { it._id })
        val cache = ModelCache<LargeTestModel, UUID>(
            mock,
            LargeTestModel.serializer(),
            scope = backgroundScope,
            log = testLog
        )

        // Start listening
        var lastRead: List<LargeTestModel> = listOf()
        val ref = cache.list(Query(Condition.Always, sort { it.int.ascending() }))
        reactive {
            lastRead = ref()
        }
        delay(5.seconds)
        assertEquals(dataToInsert.sortedBy { it.int }, lastRead)

        // Stop listening (simulate disconnection)
        mock.connectivityFailure = true
        delay(5.seconds)

        // Add new data while disconnected
        val newItem = LargeTestModel(int = 4)
        mock.data[newItem._id] = newItem

        // Reconnect
        mock.connectivityFailure = false
        delay(5.seconds)

        // Verify new data is received
        assertEquals(dataToInsert.plus(newItem).sortedBy { it.int }, lastRead)
    }

    @Test
    fun localModifications() = runTest2 {
        val mock = ClientModelRestEndpointsMock<LargeTestModel, UUID>(this)
        val dataToInsert = listOf(
            LargeTestModel(int = 1),
            LargeTestModel(int = 2),
            LargeTestModel(int = 3),
        )
        mock.data.putAll(dataToInsert.associateBy { it._id })
        val cache = ModelCache<LargeTestModel, UUID>(
            mock,
            LargeTestModel.serializer(),
            scope = backgroundScope,
            log = testLog
        )

        // Get initial data
        var lastRead: List<LargeTestModel> = listOf()
        val ref = cache.list(Query(Condition.Always, sort { it.int.ascending() }))
        reactive {
            lastRead = ref()
        }
        delay(5.seconds)
        assertEquals(dataToInsert.sortedBy { it.int }, lastRead)

        // Test localSignalUpdate - also update the mock data to match
        val itemToUpdate = dataToInsert.first { it.int == 2 }
        val updatedItem = itemToUpdate.copy(short = 99)
        mock.data[itemToUpdate._id] = updatedItem

        cache.localSignalUpdate(
            matching = { it.int == 2 },
            modify = { it.copy(short = 99) }
        )
        delay(5.seconds)

        // Verify the local update is reflected
        val expectedAfterUpdate = dataToInsert.map { 
            if (it.int == 2) it.copy(short = 99) else it 
        }.sortedBy { it.int }
        assertEquals(expectedAfterUpdate, lastRead)

        // Test localInsert - also add to mock data
        val newItem = LargeTestModel(int = 4)
        mock.data[newItem._id] = newItem

        cache.localInsert(newItem)
        delay(5.seconds)

        // Verify the local insert is reflected
        assertEquals(expectedAfterUpdate.plus(newItem).sortedBy { it.int }, lastRead)
    }

    @Test
    fun totalInvalidation() = runTest2 {
        val mock = ClientModelRestEndpointsMock<LargeTestModel, UUID>(this)
        val initialItem = LargeTestModel(int = 1)
        mock.data[initialItem._id] = initialItem
        val cache = ModelCache<LargeTestModel, UUID>(
            mock,
            LargeTestModel.serializer(),
            scope = backgroundScope,
            log = testLog
        )

        // Get initial data
        var lastRead: LargeTestModel? = null
        val ref = cache.item(initialItem._id)
        reactive {
            lastRead = ref()
        }
        delay(5.seconds)
        assertEquals(initialItem, lastRead)

        // Modify the data in the mock but not in the cache
        val updatedItem = initialItem.copy(short = 99)
        mock.data[initialItem._id] = updatedItem

        // Total invalidation should force a refresh
        cache.totallyInvalidate()
        delay(5.seconds)

        // Verify the updated data is received
        assertEquals(updatedItem, lastRead)
    }

    @Test
    fun upsertTest() = runTest2 {
        val mock = ClientModelRestEndpointsMock<LargeTestModel, UUID>(this)
        val cache = ModelCache<LargeTestModel, UUID>(
            mock,
            LargeTestModel.serializer(),
            scope = backgroundScope,
            log = testLog
        )

        // Create a new item
        val newItem = LargeTestModel(int = 1)

        // Upsert the item
        val ref = cache.upsert(newItem)
        var lastRead: LargeTestModel? = null
        reactive {
            lastRead = ref()
        }
        delay(5.seconds)

        // Verify the item was inserted
        assertEquals(newItem, lastRead)
        assertTrue(mock.data.containsKey(newItem._id))

        // Update the item
        val updatedItem = newItem.copy(short = 99)
        cache.upsert(updatedItem)
        delay(5.seconds)

        // Verify the item was updated
        assertEquals(updatedItem, lastRead)
    }

    @Test
    fun bulkModifyTest() = runTest2 {
        val mock = ClientModelRestEndpointsMock<LargeTestModel, UUID>(this)
        val dataToInsert = listOf(
            LargeTestModel(int = 1),
            LargeTestModel(int = 2),
            LargeTestModel(int = 3),
        )
        mock.data.putAll(dataToInsert.associateBy { it._id })
        val cache = ModelCache<LargeTestModel, UUID>(
            mock,
            LargeTestModel.serializer(),
            scope = backgroundScope,
            log = testLog
        )

        // Get initial data
        var lastRead: List<LargeTestModel> = listOf()
        val ref = cache.list(Query(Condition.Always, sort { it.int.ascending() }))
        reactive {
            lastRead = ref()
        }
        delay(5.seconds)
        assertEquals(dataToInsert.sortedBy { it.int }, lastRead)

        // Perform bulk modification
        val bulkUpdate = MassModification(
            condition = condition { it.int gt 1 },
            modification = modification { it.short assign 99 }
        )
        cache.bulkModify(bulkUpdate)
        delay(5.seconds)

        // Verify the bulk update is reflected
        val expectedAfterUpdate = dataToInsert.map { 
            if (it.int > 1) it.copy(short = 99) else it 
        }.sortedBy { it.int }
        assertEquals(expectedAfterUpdate, lastRead)
    }

    @Test
    fun emptyDataAddElements() = runTest2 {
        // Start with an empty mock data source
        val mock = ClientModelRestEndpointsMock<LargeTestModel, UUID>(this)
        val cache = ModelCache<LargeTestModel, UUID>(
            mock,
            LargeTestModel.serializer(),
            scope = backgroundScope,
            log = testLog
        )

        // Set up reactive listener for the list
        var lastRead: List<LargeTestModel> = listOf()
        val ref = cache.list(Query(Condition.Always, sort { it.int.ascending() }))
        reactive {
            lastRead = ref()
        }
        delay(5.seconds)

        // Initially, the list should be empty
        assertEquals(emptyList(), lastRead)

        // Add first element
        val item1 = LargeTestModel(int = 1)
        cache.add(item1)
        delay(5.seconds)

        // Verify the first element is in the list
        assertEquals(listOf(item1), lastRead)

        // Add second element
        val item2 = LargeTestModel(int = 2)
        cache.add(item2)
        delay(5.seconds)

        // Verify both elements are in the list, sorted by int
        assertEquals(listOf(item1, item2).sortedBy { it.int }, lastRead)

        // Add third element
        val item3 = LargeTestModel(int = 3)
        cache.add(item3)
        delay(5.seconds)

        // Verify all three elements are in the list, sorted by int
        assertEquals(listOf(item1, item2, item3).sortedBy { it.int }, lastRead)

        // Verify the mock data source contains all added items
        assertEquals(3, mock.data.size)
        assertTrue(mock.data.containsKey(item1._id))
        assertTrue(mock.data.containsKey(item2._id))
        assertTrue(mock.data.containsKey(item3._id))
    }

    @Test
    fun emptyDataAddElementsSocket() = runTest2 {
        // Start with an empty mock data source using websocket
        val mock = ClientModelRestEndpointsPlusUpdatesWebsocketMock<LargeTestModel, UUID>(this)
        val cache = ModelCache<LargeTestModel, UUID>(
            mock,
            LargeTestModel.serializer(),
            scope = backgroundScope,
            log = testLog
        )

        // Set up reactive listener for the list
        var lastRead: List<LargeTestModel> = listOf()
        val ref = cache.list(Query(Condition.Always, sort { it.int.ascending() }))
        reactive {
            lastRead = ref()
        }
        delay(5.seconds)

        // Initially, the list should be empty
        assertEquals(emptyList(), lastRead)

        // Add first element through the socket
        val item1 = LargeTestModel(int = 1)
        mock.insert(item1)
        delay(5.seconds)

        // Verify the first element is in the list
        assertEquals(listOf(item1), lastRead)

        // Add second element through the socket
        val item2 = LargeTestModel(int = 2)
        mock.insert(item2)
        delay(5.seconds)

        // Verify both elements are in the list, sorted by int
        assertEquals(listOf(item1, item2).sortedBy { it.int }, lastRead)

        // Add third element through the socket
        val item3 = LargeTestModel(int = 3)
        mock.insert(item3)
        delay(5.seconds)

        // Verify all three elements are in the list, sorted by int
        assertEquals(listOf(item1, item2, item3).sortedBy { it.int }, lastRead)

        // Verify the mock data source contains all added items
        assertEquals(3, mock.data.size)
        assertTrue(mock.data.containsKey(item1._id))
        assertTrue(mock.data.containsKey(item2._id))
        assertTrue(mock.data.containsKey(item3._id))
    }

    @Test
    fun individualItemDeletionViaWebSocket() = runTest2 {
        // Test that ModelCacheItemReadableImpl properly notifies listeners when
        // an item is deleted via websocket update (without relying on pulls)
        val mock = ClientModelRestEndpointsPlusUpdatesWebsocketMock<LargeTestModel, UUID>(this)
        var currentValue = LargeTestModel(int = 1)
        mock.data[currentValue._id] = currentValue
        val cache = ModelCache<LargeTestModel, UUID>(
            mock,
            LargeTestModel.serializer(),
            scope = backgroundScope,
            log = testLog
        )

        // Set up reactive listener for the individual item
        // Use very short pullFrequency to ensure socket is used (< 30 seconds)
        // but with a listener that tracks notifications
        var notificationCount = 0
        var lastRead: LargeTestModel? = currentValue
        val ref = cache.item(currentValue._id, maximumAge = 1000.seconds, pullFrequency = 10.seconds)
        reactive {
            notificationCount++
            lastRead = ref()
        }
        delay(5.seconds)

        // Verify we received the initial value
        assertEquals(currentValue, lastRead)
        val initialNotifications = notificationCount

        // Delete the item via the mock (simulating server deletion via websocket)
        // This should trigger a websocket notification
        mock.delete(currentValue._id)
        delay(2.seconds)  // Short delay, not enough for a full pull cycle

        // The listener should have been notified and lastRead should now be null
        // If the bug exists, lastRead will still be currentValue because
        // SocketChanges.removed is not processed in the init block
        assertEquals(null, lastRead, "Item should be null after websocket deletion")
        assertTrue(notificationCount > initialNotifications, "Listener should have been notified of deletion")
    }
}
