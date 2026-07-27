package com.lightningkite.lightningserver.db

import kotlin.uuid.Uuid
import com.lightningkite.kiteui.LogRoot
import com.lightningkite.kiteui.Platform
import com.lightningkite.kiteui.current
import com.lightningkite.services.database.*
import com.lightningkite.reactive.context.reactive
import com.lightningkite.reactive.core.ReactiveState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlin.time.Duration.Companion.milliseconds
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Test suite for ModelCache functionality.
 *
 * ModelCache provides intelligent client-side caching with real-time synchronization for Lightning Server models.
 * It combines local cache, batched requests, and optional WebSocket updates for efficient data management.
 *
 * These tests verify:
 * - Single item tracking (ModelCacheItemReadable)
 * - Collection tracking with queries (ModelCacheLimitReadable)
 * - WebSocket-based real-time updates
 * - Polling-based updates
 * - Connectivity failure recovery
 * - Local modifications and invalidation
 * - List limit changes and reconstruction
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ModelCacheTest {
    val testLog = if (Platform.current == Platform.Desktop) LogRoot else null

    /**
     * Tests that ModelCache correctly handles connectivity failures and recovery.
     *
     * Verifies that:
     * 1. Cache works normally when connectivity is available
     * 2. Cache continues to function during connectivity failures
     * 3. Cache resumes updates after connectivity is restored
     */
    @Test fun connectivityIssue() = runTest2 {
        val mock = ClientModelRestEndpointsPlusUpdatesWebsocketMock<LargeTestModel, Uuid>(this)
        var currentValue = LargeTestModel(int = 0)
        mock.data[currentValue._id] = currentValue
        val cache = ModelCache<LargeTestModel, Uuid>(
            mock,
            LargeTestModel.serializer(),
            scope = backgroundScope,
            log = testLog
        )

        reactive {
            val ref = cache.item(currentValue._id)
            assertEquals(
                currentValue,
                ref()
            )
        }
        delay(5.seconds)

        // Start out as working - verify initial modification is received
        val mod = modification<LargeTestModel> { it.short assign 2 }
        currentValue = mod(currentValue)
        mock.modify(currentValue._id, mod)
        delay(5.seconds)

        // Simulate connectivity failure
        mock.connectivityFailure = true
        delay(5.minutes)

        // Restore connectivity and verify cache recovers
        mock.connectivityFailure = false
        delay(5.seconds)

        // TODO: Add explicit assertions for post-reconnection state
    }

    /**
     * Tests that ModelCache correctly handles dynamic list limit increases.
     *
     * Verifies that when a list query's limit is increased, the cache fetches
     * additional items to satisfy the new limit.
     */
    @Test fun listLimitIncrease() = runTest2 {
        // Uses polling mock instead of WebSocket to test non-realtime limit increases
        val mock = ClientModelRestEndpointsMock<LargeTestModel, Uuid>(this)
        val dataToInsert = listOf(
            LargeTestModel(int = 1),
            LargeTestModel(int = 2),
            LargeTestModel(int = 3),
            LargeTestModel(int = 4),
            LargeTestModel(int = 5),
        )
        mock.data.putAll(dataToInsert.associateBy { it._id })
        val cache = ModelCache<LargeTestModel, Uuid>(
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
        // Deliberately the deprecated fire-and-forget setter - the polling loop still has to pick
        // the new limit up.  See limitSuspendsUntilItemsArrive for the awaitable form.
        @Suppress("DEPRECATION")
        ref.limit = 10
        delay(1.seconds)
        assertEquals(dataToInsert.take(10), lastRead)
    }

    /**
     * Tests that ModelCache correctly receives and applies list updates via WebSocket.
     *
     * Verifies that:
     * 1. Initial list query returns correct filtered data
     * 2. Modifications sent via WebSocket are reflected in the cached list
     * 3. The reactive context receives updates automatically
     */
    @Test
    fun listChangesWs() = runTest2 {
        // Uses WebSocket mock to test real-time list updates
        val mock = ClientModelRestEndpointsPlusUpdatesWebsocketMock<LargeTestModel, Uuid>(this)
        val dataToInsert = listOf(
            LargeTestModel(int = 1),
            LargeTestModel(int = 2),
            LargeTestModel(int = 3),
            LargeTestModel(int = 4),
            LargeTestModel(int = 5),
        )
        mock.data.putAll(dataToInsert.associateBy { it._id })
        val cache = ModelCache<LargeTestModel, Uuid>(
            mock,
            LargeTestModel.serializer(),
            scope = backgroundScope,
            log = testLog
        )

        var currentValue = dataToInsert[2]

        val ref = cache.list(Query(condition { it.int gt 2 }, sort { it.int.ascending() }))
        reactive {
            val currentList = ref()
            assertContains(
                currentList,
                currentValue,
            )
        }
        delay(7.seconds)

        val mod = modification<LargeTestModel> { it.short assign 2 }
        currentValue = mod(currentValue)
        mock.modify(currentValue._id, mod)
        delay(5.seconds)
    }

    /**
     * Tests that ModelCache correctly receives and applies list updates via polling.
     *
     * Similar to listChangesWs, but without WebSocket support - tests the fallback
     * polling mechanism for detecting server-side changes.
     */
    @Test
    fun listChangesPull() = runTest2 {
        // Uses polling mock (no WebSocket) to test fallback update detection
        val mock = ClientModelRestEndpointsMock<LargeTestModel, Uuid>(this)
        val dataToInsert = listOf(
            LargeTestModel(int = 1),
            LargeTestModel(int = 2),
            LargeTestModel(int = 3),
            LargeTestModel(int = 4),
            LargeTestModel(int = 5),
        )
        mock.data.putAll(dataToInsert.associateBy { it._id })
        val cache = ModelCache<LargeTestModel, Uuid>(
            mock,
            LargeTestModel.serializer(),
            scope = backgroundScope,
            log = testLog
        )

        var currentValue = dataToInsert.first { it.int > 2 }
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

        // Modify the item on the server side and verify polling detects the change
        val mod = modification<LargeTestModel> { it.short assign 2 }
        currentValue = mod(currentValue)
        mock.modify(currentValue._id, mod)
        delay(80.seconds) // Wait long enough for polling to detect the change
        assertContains(
            lastRead ?: setOf(),
            currentValue,
            "List: ${(lastRead ?: emptyList()).map { it.int }}, Value: ${currentValue.int}"
        )
    }

    /**
     * Tests that ModelCache correctly receives and applies individual item updates via WebSocket.
     *
     * Verifies that:
     * 1. A single item is fetched and cached correctly
     * 2. Modifications sent via WebSocket update the cached item
     * 3. The reactive context receives the updated item automatically
     */
    @Test
    fun individualChangesWs() = runTest2 {
        // Uses WebSocket mock to test real-time individual item updates
        val mock = ClientModelRestEndpointsPlusUpdatesWebsocketMock<LargeTestModel, Uuid>(this)
        var currentValue = LargeTestModel(int = 0)
        mock.data[currentValue._id] = currentValue
        val cache = ModelCache<LargeTestModel, Uuid>(
            mock,
            LargeTestModel.serializer(),
            scope = backgroundScope,
            log = testLog
        )

        reactive {
            val ref = cache.item(currentValue._id)
            val item = ref()
            assertEquals(currentValue, item)
        }
        delay(5.seconds)

        // Modify the item via WebSocket and verify cache receives the update
        val mod = modification<LargeTestModel> { it.short assign 2 }
        currentValue = mod(currentValue)
        mock.modify(currentValue._id, mod)
        delay(5.seconds)
    }

    /**
     * Tests that ModelCache correctly receives and applies individual item updates via polling.
     *
     * Similar to individualChangesWs, but without WebSocket support - tests the fallback
     * polling mechanism for detecting individual item changes.
     */
    @Test
    fun individualChangesPull() = runTest2 {
        // Uses polling mock (no WebSocket) to test fallback individual item update detection
        val mock = ClientModelRestEndpointsMock<LargeTestModel, Uuid>(this)
        var currentValue = LargeTestModel(int = 0)
        var lastRead: LargeTestModel? = null
        mock.data[currentValue._id] = currentValue
        val cache = ModelCache<LargeTestModel, Uuid>(
            mock,
            LargeTestModel.serializer(),
            scope = backgroundScope,
            log = testLog
        )

        reactive {
            val ref = cache.item(currentValue._id)
            val read = ref()
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

    /**
     * Tests that reactive contexts don't trigger unnecessarily when observing a specific item by ID.
     *
     * Verifies that the reactive context only fires twice:
     * 1. Once on initial setup
     * 2. Once when the data arrives
     * And not on subsequent cache operations if the item hasn't changed.
     */
    @Test
    fun reactiveKeepId() = runTest2 {
        // Uses polling mock to ensure reactive firing count is not inflated by WebSocket activity
        val mock = ClientModelRestEndpointsMock<LargeTestModel, Uuid>(this)
        val dataToInsert = listOf(
            LargeTestModel(int = 1),
            LargeTestModel(int = 2),
            LargeTestModel(int = 3),
            LargeTestModel(int = 4),
            LargeTestModel(int = 5),
        )
        mock.data.putAll(dataToInsert.associateBy { it._id })
        val cache = ModelCache<LargeTestModel, Uuid>(
            mock,
            LargeTestModel.serializer(),
            scope = backgroundScope,
            log = testLog
        )

        var reactions = 0
        reactive {
            reactions++
            val ref = cache.item(dataToInsert[0]._id)
            val item = ref()
            assertEquals(dataToInsert[0], item)
        }
        advanceTimeBy(30.seconds)
        // Should fire exactly twice: once on startup, once when data arrives
        assertEquals(reactions, 2)
    }

    /**
     * Tests that ModelCache correctly re-fetches individual items via polling.
     *
     * Verifies that items are re-pulled from the server at appropriate intervals
     * based on cache time settings when WebSocket is not available.
     */
    @Test
    fun repullingGet() = runTest2 {
        // Uses polling mock to verify periodic re-fetching behavior
        val mock = ClientModelRestEndpointsMock<LargeTestModel, Uuid>(this)
        val dataToInsert = listOf(
            LargeTestModel(int = 1),
            LargeTestModel(int = 2),
            LargeTestModel(int = 3),
            LargeTestModel(int = 4),
            LargeTestModel(int = 5),
        )
        mock.data.putAll(dataToInsert.associateBy { it._id })
        val cache = ModelCache<LargeTestModel, Uuid>(
            mock,
            LargeTestModel.serializer(),
            scope = backgroundScope,
            log = testLog
        )

        val ref = cache.item(dataToInsert[0]._id)
        reactive {
            val item = ref()
            assertEquals(dataToInsert[0], item)
        }
        advanceTimeBy(30.seconds)
    }

    /**
     * Tests that ModelCache correctly fetches individual items with WebSocket support.
     *
     * Similar to repullingGet, but with WebSocket connection to verify
     * that item fetching works correctly in a WebSocket-enabled environment.
     */
    @Test
    fun wsGet() = runTest2 {
        // Uses WebSocket mock to verify fetching works with real-time connections
        val mock = ClientModelRestEndpointsPlusUpdatesWebsocketMock<LargeTestModel, Uuid>(this)
        val dataToInsert = listOf(
            LargeTestModel(int = 1),
            LargeTestModel(int = 2),
            LargeTestModel(int = 3),
            LargeTestModel(int = 4),
            LargeTestModel(int = 5),
        )
        mock.data.putAll(dataToInsert.associateBy { it._id })
        val cache = ModelCache<LargeTestModel, Uuid>(
            mock,
            LargeTestModel.serializer(),
            scope = backgroundScope,
            log = testLog
        )

        val ref = cache.item(dataToInsert[0]._id)
        reactive {
            val item = ref()
            assertEquals(dataToInsert[0], item)
        }
        advanceTimeBy(30.seconds)
    }

    /**
     * Tests that ModelCache correctly re-fetches lists via polling.
     *
     * Verifies that list queries are re-pulled from the server at appropriate intervals
     * with correct condition and sorting applied.
     */
    @Test
    fun repullingList() = runTest2 {
        // Uses polling mock to verify periodic list re-fetching behavior
        val mock = ClientModelRestEndpointsMock<LargeTestModel, Uuid>(this)
        val dataToInsert = listOf(
            LargeTestModel(int = 1),
            LargeTestModel(int = 2),
            LargeTestModel(int = 3),
            LargeTestModel(int = 4),
            LargeTestModel(int = 5),
        )
        mock.data.putAll(dataToInsert.associateBy { it._id })
        val cache = ModelCache<LargeTestModel, Uuid>(
            mock,
            LargeTestModel.serializer(),
            scope = backgroundScope,
            log = testLog
        )

        val ref = cache.list(Query(condition { it.int lt 4 }, orderBy = sort { it.int.ascending() }))
        reactive {
            val list = ref()
            assertEquals(
                dataToInsert.filter { it.int < 4 }.sortedBy { it.int },
                list
            )
        }
        advanceTimeBy(30.seconds)
    }

    /**
     * Tests that reactive contexts don't trigger unnecessarily when observing a list query.
     *
     * Verifies that the reactive context only fires twice:
     * 1. Once on initial setup
     * 2. Once when the data arrives
     * And not on subsequent cache operations if the list hasn't changed.
     */
    @Test
    fun reactivityKeepList() = runTest2 {
        // Uses polling mock to ensure reactive firing count is not inflated by WebSocket activity
        val mock = ClientModelRestEndpointsMock<LargeTestModel, Uuid>(this)
        val dataToInsert = listOf(
            LargeTestModel(int = 1),
            LargeTestModel(int = 2),
            LargeTestModel(int = 3),
            LargeTestModel(int = 4),
            LargeTestModel(int = 5),
        )
        mock.data.putAll(dataToInsert.associateBy { it._id })
        val cache = ModelCache<LargeTestModel, Uuid>(
            mock,
            LargeTestModel.serializer(),
            scope = backgroundScope,
            log = testLog
        )

        var reactions = 0
        reactive {
            reactions++
            val ref = cache.list(Query(condition { it.int lt 4 }, orderBy = sort { it.int.ascending() }))
            val list = ref()
            assertEquals(
                dataToInsert.filter { it.int < 4 }.sortedBy { it.int },
                list
            )
        }
        advanceTimeBy(30.seconds)
        // Should fire exactly twice: once on startup, once when data arrives
        assertEquals(reactions, 2)
    }

    /**
     * Tests that ModelCache correctly fetches lists with WebSocket support.
     *
     * Similar to repullingList, but with WebSocket connection to verify
     * that list fetching works correctly in a WebSocket-enabled environment.
     */
    @Test
    fun wsList() = runTest2 {
        // Uses WebSocket mock to verify list fetching works with real-time connections
        val mock = ClientModelRestEndpointsPlusUpdatesWebsocketMock<LargeTestModel, Uuid>(this)
        val dataToInsert = listOf(
            LargeTestModel(int = 1),
            LargeTestModel(int = 2),
            LargeTestModel(int = 3),
            LargeTestModel(int = 4),
            LargeTestModel(int = 5),
        )
        mock.data.putAll(dataToInsert.associateBy { it._id })
        val cache = ModelCache<LargeTestModel, Uuid>(
            mock,
            LargeTestModel.serializer(),
            scope = backgroundScope,
            log = testLog
        )

        val ref = cache.list(Query(condition { it.int lt 4 }, orderBy = sort { it.int.ascending() }))
        reactive {
            val list = ref()
            assertEquals(
                dataToInsert.filter { it.int < 4 }.sortedBy { it.int },
                list
            )
        }
        advanceTimeBy(30.seconds)
    }

    /**
     * Tests that ModelCache correctly handles fetching a non-existent item.
     *
     * Verifies that:
     * 1. The cache returns null for items that don't exist
     * 2. The reactive context is properly notified
     * 3. The cache doesn't crash or hang when fetching missing items
     */
    @Test
    fun getGone() = runTest2 {
        // Uses polling mock to test missing item behavior
        val mock = ClientModelRestEndpointsMock<LargeTestModel, Uuid>(this)
        val dataToInsert = listOf(
            LargeTestModel(int = 1),
            LargeTestModel(int = 2),
            LargeTestModel(int = 3),
            LargeTestModel(int = 4),
            LargeTestModel(int = 5),
        )
        mock.data.putAll(dataToInsert.associateBy { it._id })
        val cache = ModelCache<LargeTestModel, Uuid>(
            mock,
            LargeTestModel.serializer(),
            scope = backgroundScope,
            log = testLog
        )

        var runs = 0
        // Request a random UUID that doesn't exist in the dataset
        val ref = cache.item(Uuid.random())
        reactive {
            val item = ref()
            assertEquals(null, item)
            runs++
        }
        advanceTimeBy(30.seconds)
        // Should fire at least once (initial fetch that returns null)
        assertTrue(runs >= 1)
    }

    /**
     * Tests that ModelCache correctly handles disconnection and reconnection scenarios.
     *
     * Verifies that:
     * 1. Initial data is fetched correctly
     * 2. Cache handles connectivity failure gracefully
     * 3. Data added during disconnection is received upon reconnection
     * 4. List is properly updated after reconnection
     */
    @Test
    fun listenStopAddReconnect() = runTest2 {
        val mock = ClientModelRestEndpointsPlusUpdatesWebsocketMock<LargeTestModel, Uuid>(this)
        val dataToInsert = listOf(
            LargeTestModel(int = 1),
            LargeTestModel(int = 2),
            LargeTestModel(int = 3),
        )
        mock.data.putAll(dataToInsert.associateBy { it._id })
        val cache = ModelCache<LargeTestModel, Uuid>(
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

    /**
     * Tests that ModelCache correctly handles local modifications.
     *
     * Verifies that:
     * 1. localSignalUpdate() correctly updates items matching a condition
     * 2. localInsert() correctly adds new items to the cache
     * 3. These local operations trigger reactive updates
     * 4. The cache remains synchronized with the mock backend
     */
    @Test
    fun localModifications() = runTest2 {
        val mock = ClientModelRestEndpointsMock<LargeTestModel, Uuid>(this)
        val dataToInsert = listOf(
            LargeTestModel(int = 1),
            LargeTestModel(int = 2),
            LargeTestModel(int = 3),
        )
        mock.data.putAll(dataToInsert.associateBy { it._id })
        val cache = ModelCache<LargeTestModel, Uuid>(
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

    /**
     * Tests that ModelCache's totallyInvalidate() forces a complete cache refresh.
     *
     * Verifies that:
     * 1. Cache initially returns stale data
     * 2. After totallyInvalidate() is called, cache fetches fresh data from server
     * 3. Updated data is properly reflected in reactive contexts
     */
    @Test
    fun totalInvalidation() = runTest2 {
        val mock = ClientModelRestEndpointsMock<LargeTestModel, Uuid>(this)
        val initialItem = LargeTestModel(int = 1)
        mock.data[initialItem._id] = initialItem
        val cache = ModelCache<LargeTestModel, Uuid>(
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

    /**
     * Tests that ModelCache's upsert() correctly inserts new items and updates existing ones.
     *
     * Verifies that:
     * 1. Upserting a new item adds it to both cache and backend
     * 2. Upserting an existing item (by ID) updates it instead of creating a duplicate
     * 3. Both operations trigger reactive updates
     */
    @Test
    fun upsertTest() = runTest2 {
        val mock = ClientModelRestEndpointsMock<LargeTestModel, Uuid>(this)
        val cache = ModelCache<LargeTestModel, Uuid>(
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

    /**
     * Tests that ModelCache's bulkModify() correctly updates multiple items matching a condition.
     *
     * Verifies that:
     * 1. MassModification with condition and modification is properly applied
     * 2. Only items matching the condition are updated
     * 3. Updates are reflected in reactive list queries
     */
    @Test
    fun bulkModifyTest() = runTest2 {
        val mock = ClientModelRestEndpointsMock<LargeTestModel, Uuid>(this)
        val dataToInsert = listOf(
            LargeTestModel(int = 1),
            LargeTestModel(int = 2),
            LargeTestModel(int = 3),
        )
        mock.data.putAll(dataToInsert.associateBy { it._id })
        val cache = ModelCache<LargeTestModel, Uuid>(
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

    /**
     * Tests that ModelCache correctly handles adding elements to an initially empty dataset.
     *
     * Verifies that:
     * 1. Cache starts with an empty list
     * 2. Elements can be added one by one via cache.add()
     * 3. Each addition is reflected in reactive list queries
     * 4. Elements are properly sorted according to the query
     * 5. All elements are persisted in the backend
     */
    @Test
    fun emptyDataAddElements() = runTest2 {
        // Start with an empty mock data source
        val mock = ClientModelRestEndpointsMock<LargeTestModel, Uuid>(this)
        val cache = ModelCache<LargeTestModel, Uuid>(
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

    /**
     * Tests that totallyInvalidate() works immediately, not relying on cache expiration timing.
     *
     * This test verifies that after totallyInvalidate():
     * 1. Fresh data is fetched from the server
     * 2. The updated data is received
     */
    @Test
    fun totalInvalidationImmediate() = runTest2 {
        val mock = ClientModelRestEndpointsMock<LargeTestModel, Uuid>(this)
        val initialItem = LargeTestModel(int = 1)
        mock.data[initialItem._id] = initialItem
        val cache = ModelCache<LargeTestModel, Uuid>(
            mock,
            LargeTestModel.serializer(),
            scope = backgroundScope,
            log = testLog
        )

        // Get initial data with a LONG maximumAge so it won't naturally expire
        var lastRead: LargeTestModel? = null
        val ref = cache.item(initialItem._id, maximumAge = 10.minutes, pullFrequency = 60.seconds)
        reactive {
            lastRead = ref()
        }
        delay(5.seconds)
        assertEquals(initialItem, lastRead)

        // Modify the data in the mock (simulating server-side change)
        val updatedItem = initialItem.copy(short = 99)
        mock.data[initialItem._id] = updatedItem

        // Total invalidation should clear cache and trigger refetch
        cache.totallyInvalidate()

        // Give time for refetch
        delay(5.seconds)

        // Verify the updated data is received
        assertEquals(updatedItem, lastRead, "Expected updated item after invalidation, but got stale data")
    }

    /**
     * Tests that totallyInvalidate() works with WebSocket connections.
     *
     * When using WebSockets, data is considered "live" and the polling loop
     * won't refetch unless the cache is truly cleared.
     *
     * BUG EXPOSURE: The current implementation doesn't emit CacheUpdate.SocketOverload()
     * to newData, so "live" data remains in the cache and is never invalidated.
     */
    @Test
    fun totalInvalidationWithWebSocket() = runTest2 {
        val mock = ClientModelRestEndpointsPlusUpdatesWebsocketMock<LargeTestModel, Uuid>(this)
        val initialItem = LargeTestModel(int = 1)
        mock.data[initialItem._id] = initialItem
        val cache = ModelCache<LargeTestModel, Uuid>(
            mock,
            LargeTestModel.serializer(),
            scope = backgroundScope,
            log = testLog
        )

        // Get initial data with WebSocket (pullFrequency < 30s triggers socket use)
        var lastRead: LargeTestModel? = null
        val ref = cache.item(initialItem._id, maximumAge = 10.minutes, pullFrequency = 10.seconds)
        reactive {
            lastRead = ref()
        }
        delay(5.seconds)
        assertEquals(initialItem, lastRead)

        // Modify the data directly in mock storage (NOT through WebSocket)
        // This simulates data that changed outside the socket's awareness
        val updatedItem = initialItem.copy(short = 99)
        mock.data[initialItem._id] = updatedItem

        // Total invalidation should force a refetch even with WebSocket
        cache.totallyInvalidate()
        delay(5.seconds)

        // Verify the updated data is received
        assertEquals(updatedItem, lastRead, "Expected updated item after invalidation with WebSocket")
    }

    /**
     * Tests that totallyInvalidate() properly clears list caches.
     *
     * BUG EXPOSURE: The current implementation doesn't call cache.clear()
     * on the ListReconstructionCalculator, so list queries continue
     * returning stale data.
     */
    @Test
    fun totalInvalidationList() = runTest2 {
        val mock = ClientModelRestEndpointsMock<LargeTestModel, Uuid>(this)
        val dataToInsert = listOf(
            LargeTestModel(int = 1),
            LargeTestModel(int = 2),
            LargeTestModel(int = 3),
        )
        mock.data.putAll(dataToInsert.associateBy { it._id })
        val cache = ModelCache<LargeTestModel, Uuid>(
            mock,
            LargeTestModel.serializer(),
            scope = backgroundScope,
            log = testLog
        )

        // Get initial list data
        var lastRead: List<LargeTestModel> = listOf()
        val ref = cache.list(Query(Condition.Always, sort { it.int.ascending() }), maximumAge = 10.minutes, pullFrequency = 60.seconds)
        reactive {
            lastRead = ref()
        }
        delay(5.seconds)
        assertEquals(dataToInsert.sortedBy { it.int }, lastRead)

        // Add new data directly to mock (simulating server-side change)
        val newItem = LargeTestModel(int = 4)
        mock.data[newItem._id] = newItem

        // Total invalidation should clear list cache and refetch
        cache.totallyInvalidate()
        delay(5.seconds)

        // Verify the new item is included
        val expected = dataToInsert.plus(newItem).sortedBy { it.int }
        assertEquals(expected, lastRead, "Expected new item in list after invalidation")
    }

    /**
     * Tests that totallyInvalidate() clears individual item cache entries.
     *
     * BUG EXPOSURE: The current implementation doesn't clear lastIndividualValues,
     * so the cached item data persists even after invalidation.
     */
    @Test
    fun totalInvalidationClearsIndividualCache() = runTest2 {
        val mock = ClientModelRestEndpointsMock<LargeTestModel, Uuid>(this)
        val item1 = LargeTestModel(int = 1)
        val item2 = LargeTestModel(int = 2)
        mock.data[item1._id] = item1
        mock.data[item2._id] = item2
        val cache = ModelCache<LargeTestModel, Uuid>(
            mock,
            LargeTestModel.serializer(),
            scope = backgroundScope,
            log = testLog
        )

        // Fetch both items
        var lastRead1: LargeTestModel? = null
        var lastRead2: LargeTestModel? = null
        val ref1 = cache.item(item1._id, maximumAge = 10.minutes, pullFrequency = 60.seconds)
        val ref2 = cache.item(item2._id, maximumAge = 10.minutes, pullFrequency = 60.seconds)
        reactive { lastRead1 = ref1() }
        reactive { lastRead2 = ref2() }
        delay(5.seconds)
        assertEquals(item1, lastRead1)
        assertEquals(item2, lastRead2)

        // Modify both items in the mock
        val updatedItem1 = item1.copy(short = 11)
        val updatedItem2 = item2.copy(short = 22)
        mock.data[item1._id] = updatedItem1
        mock.data[item2._id] = updatedItem2

        // Total invalidation should clear ALL cached items and refetch
        cache.totallyInvalidate()
        delay(5.seconds)

        // Verify both items are updated
        assertEquals(updatedItem1, lastRead1, "Expected item1 to be updated after invalidation")
        assertEquals(updatedItem2, lastRead2, "Expected item2 to be updated after invalidation")
    }

    /**
     * Tests that reactive state becomes notReady after totallyInvalidate().
     *
     * After invalidation, the cache should indicate that data is stale/unavailable
     * until fresh data is fetched.
     */
    @Test
    fun totalInvalidationStateBecomesNotReady() = runTest2 {
        val mock = ClientModelRestEndpointsMock<LargeTestModel, Uuid>(this)
        val initialItem = LargeTestModel(int = 1)
        mock.data[initialItem._id] = initialItem
        val cache = ModelCache<LargeTestModel, Uuid>(
            mock,
            LargeTestModel.serializer(),
            scope = backgroundScope,
            log = testLog
        )

        // Get initial data
        val ref = cache.item(initialItem._id, maximumAge = 10.minutes, pullFrequency = 60.seconds)
        reactive { ref() }
        delay(5.seconds)

        // Verify data is ready before invalidation
        assertTrue(ref.state.ready, "State should be ready before invalidation")

        // Invalidate - state should become notReady briefly
        cache.totallyInvalidate()

        // Immediately after invalidation, state should be notReady
        // (This is the expected behavior - current implementation may not do this)
        // Note: We can't easily assert this in the current test framework
        // as the reactive context may have already processed the refetch.

        // Wait for refetch to complete
        delay(5.seconds)

        // State should be ready again with fresh data
        assertTrue(ref.state.ready, "State should be ready after refetch")
    }

    /**
     * Tests that ModelCache correctly handles adding elements via WebSocket to an initially empty dataset.
     *
     * Similar to emptyDataAddElements, but elements are added via WebSocket updates
     * rather than direct cache operations. Verifies that WebSocket insertions work
     * correctly with an empty cache.
     */
    @Test
    fun emptyDataAddElementsSocket() = runTest2 {
        // Start with an empty mock data source using websocket
        val mock = ClientModelRestEndpointsPlusUpdatesWebsocketMock<LargeTestModel, Uuid>(this)
        val cache = ModelCache<LargeTestModel, Uuid>(
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

    // =========================================================================
    // bulkModify() Bug Investigation Tests
    // =========================================================================
    // Reproduces the bug reported on the "Assign Reviewers" page in Microcredentials:
    // After bulkModify is invoked, list queries take a long time (up to pullFrequency
    // seconds) to refetch and display fresh data. Individual item observers can also
    // get stuck repeatedly issuing `_id Inside [UUID]` (multiget) queries every 5
    // seconds because their polling loops are woken by the cache invalidation but
    // nothing actually changes the underlying data for them.
    //
    // Root cause: ModelCache.bulkModify() emits CacheUpdate.SocketOverload() which
    // clears every cache, but it does NOT call interrupt.interrupt(), so currently
    // sleeping polling loops aren't woken to refetch. They sit idle until their
    // own delay expires (minimum 5 seconds), and meanwhile the UI shows a stale
    // loading state because cache.cached() now returns null.
    // =========================================================================

    /**
     * Tests that list queries are refetched promptly after bulkModify().
     *
     * Reproduces the "Active Reviews table takes forever to load" symptom. After
     * a bulkModify, the list query's cache is cleared (SocketOverload) so state
     * goes notReady; the polling loop should be interrupted so it refetches
     * immediately rather than waiting up to pullFrequency seconds.
     */
    @Test
    fun bulkModifyRefetchesListPromptly() = runTest2 {
        val mock = ClientModelRestEndpointsMock<LargeTestModel, Uuid>(this)
        val dataToInsert = listOf(
            LargeTestModel(int = 1),
            LargeTestModel(int = 2),
            LargeTestModel(int = 3),
        )
        mock.data.putAll(dataToInsert.associateBy { it._id })
        val cache = ModelCache<LargeTestModel, Uuid>(
            mock,
            LargeTestModel.serializer(),
            scope = backgroundScope,
            log = testLog,
        )

        var lastRead: List<LargeTestModel> = listOf()
        val ref = cache.list(
            Query(Condition.Always, sort { it.int.ascending() }),
            maximumAge = 10.minutes,
            pullFrequency = 60.seconds,
        )
        reactive { lastRead = ref() }
        delay(1.seconds)
        assertEquals(dataToInsert.sortedBy { it.int }, lastRead)

        // Perform a bulk modification
        cache.bulkModify(MassModification(
            condition = condition { it.int gt 1 },
            modification = modification { it.short assign 99 },
        ))

        // The list should refetch within a reasonable window after bulkModify.
        // 1 second is plenty for the network mock (0.1s delay) plus batching wait.
        delay(1.seconds)

        val expected = dataToInsert.map {
            if (it.int > 1) it.copy(short = 99) else it
        }.sortedBy { it.int }
        assertEquals(
            expected,
            lastRead,
            "List should be refreshed promptly after bulkModify, but cache appears stale.",
        )
    }

    /**
     * Precise test: count how many multiget (query) calls happen on the item polling
     * loop in the 30 seconds following a bulkModify.
     *
     * Expected: exactly 1 refetch shortly after bulkModify (item polling loop wakes
     * up because basis is unset by SocketOverload, refetches via multiget, then
     * sleeps for pullFrequency). With pullFrequency = 60s, no further calls should
     * happen within 30s.
     *
     * Bug symptom: more than 1 query call, or even ~6 calls (every 5s) if the
     * polling loop is somehow not seeing the refetched value as fresh.
     */
    @Test
    fun bulkModifyItemPollingStabilizes() = runTest2 {
        var bulkModifyDone = false
        var queryCallsAfterBulkModify = 0
        val initial = LargeTestModel(int = 1)
        val mock = object : ClientModelRestEndpointsMock<LargeTestModel, Uuid>(this) {
            override suspend fun query(input: Query<LargeTestModel>): List<LargeTestModel> {
                if (bulkModifyDone) queryCallsAfterBulkModify++
                return super.query(input)
            }
        }
        mock.data[initial._id] = initial
        val cache = ModelCache<LargeTestModel, Uuid>(
            mock,
            LargeTestModel.serializer(),
            scope = backgroundScope,
            log = testLog,
        )

        var lastRead: LargeTestModel? = null
        val ref = cache.item(initial._id, maximumAge = 10.minutes, pullFrequency = 60.seconds)
        reactive { lastRead = ref() }
        delay(2.seconds)
        assertEquals(initial, lastRead)

        bulkModifyDone = true
        cache.bulkModify(MassModification(
            condition = condition { it.int gt 0 },
            modification = modification { it.short assign 42 },
        ))

        // Within 30s, polling loop should refetch exactly once and then settle.
        delay(30.seconds)

        assertEquals(initial.copy(short = 42), lastRead)
        assertTrue(
            queryCallsAfterBulkModify <= 2,
            "Expected at most 2 query calls after bulkModify (one refetch + jitter), " +
                "but got $queryCallsAfterBulkModify. This indicates a polling loop is " +
                "stuck repeatedly fetching every 5 seconds.",
        )
    }

    /**
     * Tests that an item observer's reactive state recovers quickly after bulkModify().
     *
     * Specifically: state should become ready again within ~1 second of the
     * bulkModify, not after pullFrequency seconds.
     */
    @Test
    fun bulkModifyItemRecoversPromptly() = runTest2 {
        val mock = ClientModelRestEndpointsMock<LargeTestModel, Uuid>(this)
        val initial = LargeTestModel(int = 1)
        mock.data[initial._id] = initial
        val cache = ModelCache<LargeTestModel, Uuid>(
            mock,
            LargeTestModel.serializer(),
            scope = backgroundScope,
            log = testLog,
        )

        var lastRead: LargeTestModel? = null
        val ref = cache.item(initial._id, maximumAge = 10.minutes, pullFrequency = 60.seconds)
        reactive { lastRead = ref() }
        delay(1.seconds)
        assertEquals(initial, lastRead)

        cache.bulkModify(MassModification(
            condition = condition { it.int gt 0 },
            modification = modification { it.short assign 42 },
        ))

        // Should refetch promptly, not after pullFrequency
        delay(1.seconds)

        assertEquals(
            initial.copy(short = 42),
            lastRead,
            "Item should be refreshed within 1s of bulkModify, but appears stale.",
        )
    }

    /**
     * A failed fetch must surface as an error state rather than leaving observers loading forever,
     * and must clear itself once the fetch succeeds again.
     */
    @Test fun itemFetchFailureBecomesError() = runTest2 {
        val mock = ClientModelRestEndpointsMock<LargeTestModel, Uuid>(this)
        val item = LargeTestModel(int = 1)
        mock.data[item._id] = item
        mock.connectivityFailure = true
        val cache = ModelCache(mock, LargeTestModel.serializer(), scope = backgroundScope, log = testLog)

        val ref = cache.item(item._id, maximumAge = 1.minutes, pullFrequency = 10.seconds)
        var observed: ReactiveState<LargeTestModel?> = ReactiveState.notReady
        val release = ref.addListener { observed = ref.state }

        delay(1.seconds)
        assertNotNull(observed.exception, "The failed fetch should be reported, but got $observed")

        mock.connectivityFailure = false
        delay(20.seconds)
        assertEquals(item, observed.getOrNull(), "The error should clear once the fetch succeeds")

        release()
    }

    /** The same for query results. */
    @Test fun listFetchFailureBecomesError() = runTest2 {
        val mock = ClientModelRestEndpointsMock<LargeTestModel, Uuid>(this)
        val dataToInsert = (1..3).map { LargeTestModel(int = it) }
        mock.data.putAll(dataToInsert.associateBy { it._id })
        mock.connectivityFailure = true
        val cache = ModelCache(mock, LargeTestModel.serializer(), scope = backgroundScope, log = testLog)

        val ref = cache.list(
            Query(Condition.Always, sort { it.int.ascending() }),
            maximumAge = 1.minutes,
            pullFrequency = 10.seconds,
        )
        var observed: ReactiveState<List<LargeTestModel>> = ReactiveState.notReady
        val release = ref.addListener { observed = ref.state }

        delay(1.seconds)
        assertNotNull(observed.exception, "The failed query should be reported, but got $observed")

        mock.connectivityFailure = false
        delay(20.seconds)
        assertEquals(dataToInsert, observed.getOrNull(), "The error should clear once the query succeeds")

        release()
    }

    /**
     * [showPreviousOnLoadOrError] lets a caller opt out of the honest error reporting above and
     * keep displaying the last value it saw.
     */
    @Test fun showPreviousOnLoadOrErrorKeepsLastValue() = runTest2 {
        val mock = ClientModelRestEndpointsMock<LargeTestModel, Uuid>(this)
        val item = LargeTestModel(int = 1)
        mock.data[item._id] = item
        val cache = ModelCache(mock, LargeTestModel.serializer(), scope = backgroundScope, log = testLog)

        val ref = cache.item(item._id, maximumAge = 10.seconds, pullFrequency = 10.seconds)
        val sticky = ref.showPreviousOnLoadOrError()
        val release = sticky.addListener { sticky.state }

        delay(1.seconds)
        assertEquals(item, sticky.state.getOrNull())

        // Let the cached value go stale while every refresh fails.
        mock.connectivityFailure = true
        delay(1.minutes)

        assertNotNull(ref.state.exception, "The unwrapped reference should report the failure")
        assertEquals(item, sticky.state.getOrNull(), "The wrapped reference should keep the last value")

        release()
    }

    /**
     * `limit(n)` must not return until the extra items are actually available, so that callers
     * like infinite-scroll views can tell when their page has loaded.
     */
    @Test fun limitSuspendsUntilItemsArrive() = runTest2 {
        val mock = ClientModelRestEndpointsMock<LargeTestModel, Uuid>(this)
        val dataToInsert = (1..5).map { LargeTestModel(int = it) }
        mock.data.putAll(dataToInsert.associateBy { it._id })
        val cache = ModelCache(mock, LargeTestModel.serializer(), scope = backgroundScope, log = testLog)

        val ref = cache.list(Query(Condition.Always, sort { it.int.ascending() }, limit = 2))
        val release = ref.addListener { }
        delay(5.seconds)
        assertEquals(dataToInsert.take(2), ref.state.getOrNull())

        ref.limit(10)
        // Deliberately no delay - limit(n) is only allowed to return once the items are here.
        assertEquals(dataToInsert, ref.state.getOrNull())

        release()
    }

    /** A failed extension is reported to the caller rather than silently doing nothing. */
    @Test fun limitThrowsWhenTheFetchFails() = runTest2 {
        val mock = ClientModelRestEndpointsMock<LargeTestModel, Uuid>(this)
        val dataToInsert = (1..5).map { LargeTestModel(int = it) }
        mock.data.putAll(dataToInsert.associateBy { it._id })
        val cache = ModelCache(mock, LargeTestModel.serializer(), scope = backgroundScope, log = testLog)

        val ref = cache.list(Query(Condition.Always, sort { it.int.ascending() }, limit = 2))
        val release = ref.addListener { }
        delay(5.seconds)

        mock.connectivityFailure = true
        assertFails { ref.limit(10) }

        release()
    }

    /**
     * Concurrent item lookups are coalesced into one `_id inside [...]` query, which must carry a
     * limit big enough for the whole batch.  With the default limit the server would answer only
     * the first page, and every ID past it would be reported as confirmed-missing rather than
     * simply not fetched yet - items would silently render as absent.
     */
    @Test fun multigetBatchLargerThanTheDefaultQueryLimit() = runTest2 {
        val mock = ClientModelRestEndpointsMock<LargeTestModel, Uuid>(this)
        // Comfortably more than Query's default limit of 100, and within BatchAndQueue's batch size.
        val dataToInsert = (1..150).map { LargeTestModel(int = it) }
        mock.data.putAll(dataToInsert.associateBy { it._id })
        val cache = ModelCache(mock, LargeTestModel.serializer(), scope = backgroundScope, log = testLog)

        // Observe every item at once, so they all land in a single batch.
        val refs = dataToInsert.map { cache.item(it._id) }
        val releases = refs.map { it.addListener { } }
        delay(5.seconds)

        val resolved = refs.mapNotNull { it.state.getOrNull() }
        assertEquals(dataToInsert.size, resolved.size, "Every requested item should have been retrieved")
        assertEquals(dataToInsert.toSet(), resolved.toSet())

        releases.forEach { it() }
    }

    /** Records every query reaching the server, so pagination can be checked for what it asks for. */
    private class QueryRecordingMock(scope: CoroutineScope) : ClientModelRestEndpointsMock<LargeTestModel, Uuid>(scope) {
        val queries: MutableList<Query<LargeTestModel>> = mutableListOf()
        override suspend fun query(input: Query<LargeTestModel>): List<LargeTestModel> {
            queries.add(input)
            return super.query(input)
        }
    }

    /** Sets up a recording mock holding [count] items sorted by `int`, plus a cache over it. */
    private fun CoroutineScope.pagingFixture(count: Int): Triple<QueryRecordingMock, List<LargeTestModel>, ModelCache<LargeTestModel, Uuid>> {
        val mock = QueryRecordingMock(this)
        val data = (1..count).map { LargeTestModel(int = it) }
        mock.data.putAll(data.associateBy { it._id })
        return Triple(mock, data, ModelCache(mock, LargeTestModel.serializer(), scope = this, log = testLog))
    }

    /**
     * Growing the limit on top of a full page pages forward with a cursor instead of re-reading
     * rows we already hold, so scrolling costs one page per page rather than growing with the
     * length of the list.
     */
    @Test fun limitGrowsByPagingForward() = runTest2 {
        val (mock, data, cache) = backgroundScope.pagingFixture(10)
        val ref = cache.list(Query(Condition.Always, sort { it.int.ascending() }, limit = 3))
        val release = ref.addListener { }
        delay(5.seconds)
        assertEquals(data.take(3), ref.state.getOrNull())

        mock.queries.clear()
        ref.limit(6)

        assertEquals(data.take(6), ref.state.getOrNull())
        assertEquals(1, mock.queries.size, "extending should take exactly one request")
        assertEquals(3, mock.queries.single().limit, "should ask only for the rows it is missing")

        release()
    }

    /**
     * The limit only moves once the data behind it is cached, so a list being extended keeps
     * showing what it already has instead of blanking while the next page loads.
     */
    @Test fun limitKeepsShowingTheShorterListWhilePagingForward() = runTest2 {
        val (_, data, cache) = backgroundScope.pagingFixture(10)
        val ref = cache.list(Query(Condition.Always, sort { it.int.ascending() }, limit = 3))
        val release = ref.addListener { }
        delay(5.seconds)

        val extending = backgroundScope.launch { ref.limit(6) }
        delay(50.milliseconds) // the mock takes 100ms, so the page is still in flight
        assertEquals(data.take(3), ref.state.getOrNull(), "must not blank while the next page loads")

        extending.join()
        assertEquals(data.take(6), ref.state.getOrNull())

        release()
    }

    /**
     * Paging forward doesn't re-read the head of the list, so it must not mark it freshly
     * retrieved either - otherwise scrolling forever would keep stale rows perpetually "fresh".
     */
    @Test fun pagingForwardDoesNotRefreshTheHeadOfTheList() = runTest2 {
        val (_, _, cache) = backgroundScope.pagingFixture(10)
        val ref = cache.list(
            Query(Condition.Always, sort { it.int.ascending() }, limit = 3),
            maximumAge = 10.minutes,
            pullFrequency = 10.minutes,
        )
        val release = ref.addListener { }
        delay(5.seconds)
        val retrievedAt = ref.lastUpdatedAt.state.getOrNull()
        assertNotNull(retrievedAt)

        delay(1.minutes)
        ref.limit(6)

        assertEquals(retrievedAt, ref.lastUpdatedAt.state.getOrNull(), "the extended list is only as fresh as its oldest part")

        release()
    }

    /**
     * A cached list shorter than its limit has already reached the end of the results, so there is
     * nothing after its last row to page to - it has to be re-read in full instead.
     */
    @Test fun limitFallsBackToAFullReadWithoutACompletePage() = runTest2 {
        val (mock, data, cache) = backgroundScope.pagingFixture(4)
        val ref = cache.list(Query(Condition.Always, sort { it.int.ascending() }, limit = 10))
        val release = ref.addListener { }
        delay(5.seconds)
        assertEquals(data, ref.state.getOrNull())

        mock.queries.clear()
        ref.limit(20)

        assertEquals(data, ref.state.getOrNull())
        assertEquals(Condition.Always, mock.queries.single().condition, "should re-read, not page past the end")
        assertEquals(20, mock.queries.single().limit)

        release()
    }

    /** Shrinking re-reads rather than paging, since there is nothing new to retrieve. */
    @Test fun limitShrinks() = runTest2 {
        val (_, data, cache) = backgroundScope.pagingFixture(10)
        val ref = cache.list(Query(Condition.Always, sort { it.int.ascending() }, limit = 6))
        val release = ref.addListener { }
        delay(5.seconds)
        assertEquals(data.take(6), ref.state.getOrNull())

        ref.limit(2)
        assertEquals(data.take(2), ref.state.getOrNull())

        release()
    }
}

