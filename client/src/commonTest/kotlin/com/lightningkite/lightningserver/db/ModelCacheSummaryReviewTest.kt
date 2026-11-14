package com.lightningkite.lightningserver.db

import com.lightningkite.reactive.core.Reactive
import com.lightningkite.services.database.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.Uuid
import kotlin.test.assertNotNull

/**
 * Summary behaviours found by adversarial review: rows read in the usual order, request rates under
 * streams of changes and failures, and the mask arriving late.
 */
class ModelCacheSummaryReviewTest {
    private class CountingMock(scope: CoroutineScope) : ClientModelRestEndpointsMock<LargeTestModel, Uuid>(scope) {
        var attempts = 0
        var failing = false
        var permissionsDelay: Duration = Duration.ZERO
        override suspend fun count(input: Condition<LargeTestModel>): Int {
            attempts++
            if (failing) throw Exception("count endpoint broken")
            return super.count(input)
        }
        override suspend fun permissions(): ModelPermissions<LargeTestModel> {
            delay(permissionsDelay)
            return super.permissions()
        }
    }

    private class CountingSocketMock(scope: CoroutineScope) :
        ClientModelRestEndpointsPlusUpdatesWebSocketMock<LargeTestModel, Uuid>(scope) {
        var attempts = 0
        override suspend fun count(input: Condition<LargeTestModel>): Int {
            attempts++
            return super.count(input)
        }
    }

    private val above1: Condition<LargeTestModel> = Condition { it.int gt 1 }
    private val below3: Condition<LargeTestModel> = Condition { it.int lt 3 }
    private val <V> Reactive<V>.value: V? get() = state.getOrNull()
    private fun <V> Reactive<V>.watch(): () -> Unit = addListener { }

    private fun CoroutineScope.setup(mock: ClientModelRestEndpointsMock<LargeTestModel, Uuid>): Pair<List<LargeTestModel>, ModelCache<LargeTestModel, Uuid>> {
        val data = (1..3).map { LargeTestModel(int = it) }
        mock.data.putAll(data.associateBy { it._id })
        return data to ModelCache(mock, LargeTestModel.serializer(), scope = this)
    }

    /** A list re-read drops a row that moved into the count; the count must notice. */
    @Test fun aRowMovingIntoACountIsNoticedThroughAListReRead() = runTest2 {
        val mock = CountingMock(backgroundScope)
        val (data, cache) = backgroundScope.setup(mock)
        cache.query(Query(Condition { it.int gt 2 }, sort { it.int.ascending() }, limit = 100), 10.minutes, 10.minutes).watch()
        delay(1.seconds)
        val count = cache.count(below3, 10.minutes, 10.minutes)
        count.watch()
        delay(1.seconds)
        assertEquals(2, count.value)

        mock.data[data[2]._id] = data[2].copy(int = 0)
        cache.query(Query(Condition { it.int gt 2 }, sort { it.int.ascending() }), maximumAge = Duration.ZERO).watch()
        delay(1.seconds)
        cache.detail(data[2]._id, Duration.ZERO, 10.minutes).watch()
        delay(1.seconds)
        assertEquals(3, count.value, "count missed a row that moved into it")
    }

    // Claude: Rows are usually read before a summary opens; edits to them must still be compared, not distrusted.
    @Test fun anUnrelatedEditToARowLoadedBeforeTheCountDoesNotRefetch() = runTest2 {
        val mock = CountingMock(backgroundScope)
        val (data, cache) = backgroundScope.setup(mock)
        cache.query(Query(Condition { it.int eq 1 }, sort { it.int.ascending() }, limit = 100), 10.minutes, 10.minutes).watch()
        delay(1.seconds)
        val count = cache.count(above1, 10.minutes, 10.minutes)
        count.watch()
        delay(1.seconds)
        cache.modify(data[0]._id, Modification { it.short assign 5 })
        delay(1.seconds)
        assertEquals(1, mock.attempts, "a note edit on a row outside the count refetched it")
    }

    @Test fun aLocalBulkModifyOfAnUnrelatedFieldDoesNotRefetchWhenRowsAreHeld() = runTest2 {
        val mock = CountingMock(backgroundScope)
        val (_, cache) = backgroundScope.setup(mock)
        cache.query(Query(Condition { it.int eq 1 }, sort { it.int.ascending() }, limit = 100), 10.minutes, 10.minutes).watch()
        delay(1.seconds)
        val count = cache.count(above1, 10.minutes, 10.minutes)
        count.watch()
        delay(1.seconds)
        cache.localBulkModify(MassModification(Condition.Always, Modification { it.short assign 4 }))
        delay(1.seconds)
        assertEquals(1, mock.attempts, "rowsModified spared the count, then mutated() marked it anyway")
    }

    @Test fun aLiveCountRespectsTheFloorUnderAStreamOfChanges() = runTest2 {
        val mock = CountingSocketMock(backgroundScope)
        val (_, cache) = backgroundScope.setup(mock)
        val count = cache.count(above1, 10.minutes, Duration.ZERO)
        count.watch()
        delay(10.seconds)
        val before = mock.attempts
        repeat(30) {
            val added = LargeTestModel(int = 5)
            mock.data[added._id] = added
            mock.change(CollectionUpdates(updates = setOf(added)))
            delay(1.seconds)
        }
        val sent = mock.attempts - before
        assertTrue(sent <= 30 / 5 + 2, "sent $sent counts in 30s despite a 5s floor")
        delay(10.seconds)
        assertEquals(32, count.value, "the last change in the stream was never fetched")
    }

    @Test fun aFailingRefreshDoesNotRetryOnEveryUpdate() = runTest2 {
        val mock = CountingMock(backgroundScope)
        val (_, cache) = backgroundScope.setup(mock)
        val count = cache.count(above1, 10.minutes, 10.minutes)
        count.watch()
        delay(1.seconds)
        mock.failing = true
        val before = mock.attempts
        repeat(20) {
            cache.localUpsert(LargeTestModel(int = 0))
            delay(100.milliseconds)
        }
        val sent = mock.attempts - before
        assertTrue(sent <= 2, "a failing count was retried $sent times in 2s")
        assertNotNull(count.value, "a count within its maximum age keeps showing while refreshes fail")
    }

    @Test fun learningTheMaskDoesNotRefetchSummaries() = runTest2 {
        val mock = CountingMock(backgroundScope)
        mock.permissionsDelay = 1.seconds
        mock.modelPermissions = ModelPermissions.allowAll<LargeTestModel>().copy(
            readMask = Mask(listOf(Condition<LargeTestModel> { it.boolean eq true } to Modification<LargeTestModel> { it.short assign 0 }))
        )
        val (_, cache) = backgroundScope.setup(mock)
        val count = cache.count(above1, 10.minutes, 10.minutes)
        count.watch()
        delay(5.seconds)
        assertEquals(1, mock.attempts, "the server's count does not depend on the client knowing the mask")
    }
}
