package com.lightningkite.lightningserver.db

import com.lightningkite.kiteui.LogRoot
import com.lightningkite.kiteui.Platform
import com.lightningkite.kiteui.current
import com.lightningkite.services.database.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.Uuid

/**
 * How [ModelCache] behaves now that items and lists share one [CoverageStore].
 *
 * These are the invariants that used to be asserted against `ListReconstructionCalculator` directly,
 * re-expressed through `item()` and `list()` where they belong, plus the ones the store's notion of
 * completeness makes newly testable: that knowing a row exactly is not the same as knowing a list is
 * whole, and that a socket telling us a row left a filter is not the same as it being deleted.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ModelCacheCoverageTest {
    val testLog = if (Platform.current == Platform.Desktop) LogRoot else null

    /** Records every query reaching the server, so what the cache asks for can be asserted on. */
    private class QueryRecordingMock(scope: CoroutineScope) : ClientModelRestEndpointsMock<LargeTestModel, Uuid>(scope) {
        val queries: MutableList<Query<LargeTestModel>> = mutableListOf()
        override suspend fun query(input: Query<LargeTestModel>): List<LargeTestModel> {
            queries.add(input)
            return super.query(input)
        }
    }

    /** A recording mock holding [count] items sorted by `int`, plus a cache over it. */
    private fun CoroutineScope.fixture(count: Int): Triple<QueryRecordingMock, List<LargeTestModel>, ModelCache<LargeTestModel, Uuid>> {
        val mock = QueryRecordingMock(this)
        val data = (1..count).map { LargeTestModel(int = it) }
        mock.data.putAll(data.associateBy { it._id })
        return Triple(mock, data, ModelCache(mock, LargeTestModel.serializer(), scope = this, log = testLog))
    }

    /** A list that neither expires nor polls, so assertions see only what the test caused. */
    private fun ModelCache<LargeTestModel, Uuid>.quietList(
        condition: Condition<LargeTestModel> = Condition.Always,
        limit: Int = 100,
    ) = list(
        Query(condition, sort { it.int.ascending() }, limit = limit),
        maximumAge = 10.minutes,
        pullFrequency = 10.minutes,
    )

    private fun ModelCache<LargeTestModel, Uuid>.quietItem(id: Uuid) =
        item(id, maximumAge = 10.minutes, pullFrequency = 10.minutes)

    // =========================================================================
    // Invariants carried over from the calculator safety tests
    // =========================================================================

    /** Repeated updates of the same rows must not duplicate them or leave the list out of order. */
    @Test fun repeatedMutationsKeepTheListDistinctAndSorted() = runTest2 {
        val (_, data, cache) = backgroundScope.fixture(3)
        val ref = cache.quietList()
        val release = ref.addListener { }
        delay(1.seconds)
        assertEquals(data, ref.state.getOrNull())

        repeat(3) {
            cache.localInsert(data[0])
            cache.localInsert(data[1])
        }
        cache.localInsert(data[2].copy(int = 0))

        val result = assertNotNull(ref.state.getOrNull())
        assertEquals(3, result.size)
        assertEquals(result.size, result.map { it._id }.distinct().size, "duplicated a row")
        assertEquals(listOf(0, 1, 2), result.map { it.int }, "lost the sort")

        release()
    }

    /** Rows arriving or moving must never push a limited list past the limit it asked for. */
    @Test fun aLimitedListNeverExceedsItsLimit() = runTest2 {
        val (_, data, cache) = backgroundScope.fixture(10)
        val ref = cache.quietList(limit = 3)
        val release = ref.addListener { }
        delay(1.seconds)
        assertEquals(data.take(3), ref.state.getOrNull())

        // A row inside the limit moves past the end of the list.
        cache.localInsert(data[1].copy(int = 100))
        assertTrue(assertNotNull(ref.state.getOrNull()).size <= 3)

        // A row from outside moves inside, and one is fetched by ID for good measure.
        cache.localInsert(data[8].copy(int = 0))
        cache.quietItem(data[7]._id).addListener { }
        delay(1.seconds)
        assertTrue(assertNotNull(ref.state.getOrNull()).size <= 3)

        release()
    }

    /** A row that stops matching one condition and starts matching another moves between lists. */
    @Test fun mutationsMoveRowsBetweenLists() = runTest2 {
        val (_, data, cache) = backgroundScope.fixture(10)
        val low = cache.quietList(condition { it.int lt 6 })
        val high = cache.quietList(condition { it.int gte 6 })
        val releases = listOf(low.addListener { }, high.addListener { })
        delay(1.seconds)
        assertEquals(listOf(1, 2, 3, 4, 5), low.state.getOrNull()?.map { it.int })
        assertEquals(listOf(6, 7, 8, 9, 10), high.state.getOrNull()?.map { it.int })

        cache.localInsert(data[2].copy(int = 100))

        assertEquals(listOf(1, 2, 4, 5), low.state.getOrNull()?.map { it.int }, "the row should have left")
        assertEquals(listOf(6, 7, 8, 9, 10, 100), high.state.getOrNull()?.map { it.int }, "and arrived here")

        releases.forEach { it() }
    }

    /** A deletion we performed has to disappear from every list, not just the one that saw it. */
    @Test fun aDeletionDisappearsFromEveryList() = runTest2 {
        val (_, data, cache) = backgroundScope.fixture(5)
        val all = cache.quietList()
        val some = cache.quietList(condition { it.int gt 2 })
        val releases = listOf(all.addListener { }, some.addListener { })
        delay(1.seconds)
        assertEquals(5, all.state.getOrNull()?.size)
        assertEquals(3, some.state.getOrNull()?.size)

        cache.quietItem(data[3]._id).delete()

        assertEquals(listOf(1, 2, 3, 5), all.state.getOrNull()?.map { it.int })
        assertEquals(listOf(3, 5), some.state.getOrNull()?.map { it.int })

        releases.forEach { it() }
    }

    /**
     * A mutation tells us about one row, not about the list, so it must not make the list look
     * freshly retrieved - that would let a stale list stay "fresh" forever under a busy screen.
     */
    @Test fun aMutationDoesNotMakeTheListLookFreshlyRetrieved() = runTest2 {
        val (_, _, cache) = backgroundScope.fixture(3)
        val ref = cache.quietList()
        val release = ref.addListener { }
        delay(1.seconds)
        val retrievedAt = assertNotNull(ref.lastUpdatedAt.state.getOrNull())

        delay(1.minutes)
        cache.localInsert(LargeTestModel(int = 9))

        assertEquals(retrievedAt, ref.lastUpdatedAt.state.getOrNull())

        release()
    }

    /** An ID lookup that comes back empty is a deletion, and has to be applied everywhere. */
    @Test fun anIdConfirmedMissingDisappearsFromEveryList() = runTest2 {
        val (mock, data, cache) = backgroundScope.fixture(3)
        val ref = cache.quietList()
        val release = ref.addListener { }
        delay(1.seconds)
        assertEquals(data, ref.state.getOrNull())

        // Removed behind the cache's back, so only a fresh lookup can reveal it.
        mock.data.remove(data[1]._id)
        cache.quietItem(data[1]._id).invalidate()

        assertEquals(listOf(data[0], data[2]), ref.state.getOrNull())

        release()
    }

    /**
     * A socket update is applied to everything it touches, even lists that are not themselves
     * subscribed - it is still a change we watched happen.
     */
    @Test fun aSocketUpdateReachesListsThatAreNotSubscribed() = runTest2 {
        val mock = ClientModelRestEndpointsPlusUpdatesWebsocketMock<LargeTestModel, Uuid>(this)
        val data = (1..5).map { LargeTestModel(int = it) }
        mock.data.putAll(data.associateBy { it._id })
        val cache = ModelCache(mock, LargeTestModel.serializer(), scope = backgroundScope, log = testLog)

        // Subscribed, so the socket's condition is this one.
        val subscribed = cache.list(Query(condition { it.int gt 2 }, sort { it.int.ascending() }), pullFrequency = 10.seconds)
        // Polls slowly instead of subscribing, so it can only learn from the other one's socket.
        val polling = cache.quietList()
        val releases = listOf(subscribed.addListener { }, polling.addListener { })
        delay(7.seconds)
        assertEquals(data, polling.state.getOrNull())

        val updated = mock.modify(data[4]._id, modification { it.short assign 7 })
        delay(1.seconds)

        assertEquals(updated, polling.state.getOrNull()?.last(), "the polling list should have the new value")

        releases.forEach { it() }
    }

    /** For a list the socket is actively serving, a removal is the whole truth about that row. */
    @Test fun aSocketRemovalDropsTheRowFromALiveList() = runTest2 {
        val mock = ClientModelRestEndpointsPlusUpdatesWebsocketMock<LargeTestModel, Uuid>(this)
        val data = (1..5).map { LargeTestModel(int = it) }
        mock.data.putAll(data.associateBy { it._id })
        val cache = ModelCache(mock, LargeTestModel.serializer(), scope = backgroundScope, log = testLog)

        val ref = cache.list(Query(condition { it.int gt 2 }, sort { it.int.ascending() }), pullFrequency = 10.seconds)
        val release = ref.addListener { }
        delay(7.seconds)
        assertEquals(listOf(3, 4, 5), ref.state.getOrNull()?.map { it.int })
        val retrievedAt = assertNotNull(ref.lastUpdatedAt.state.getOrNull())

        // Moves out of the subscribed condition; the server reports that as a removal.
        mock.modify(data[3]._id, modification { it.int assign 1 })
        delay(1.seconds)

        assertEquals(listOf(3, 5), ref.state.getOrNull()?.map { it.int })
        assertEquals(retrievedAt, ref.lastUpdatedAt.state.getOrNull(), "the list is still complete; it just lost a row")

        release()
    }

    // =========================================================================
    // What completeness tracking makes newly testable
    // =========================================================================

    /**
     * The one thing a socket removal must never be read as.  Server-side it means "this row's new
     * value no longer matches what you subscribed to" - so for anyone who was not subscribed to that
     * condition, the row may well still exist.
     */
    @Test fun aSocketRemovalIsNotADeletion() = runTest2 {
        val mock = ClientModelRestEndpointsPlusUpdatesWebsocketMock<LargeTestModel, Uuid>(this)
        val leaving = LargeTestModel(int = 10)
        val staying = LargeTestModel(int = 20)
        mock.data[leaving._id] = leaving
        mock.data[staying._id] = staying
        val cache = ModelCache(mock, LargeTestModel.serializer(), scope = backgroundScope, log = testLog)

        // Subscribed, so the socket's condition is this list's.
        val list = cache.list(Query(condition { it.int gt 5 }, sort { it.int.ascending() }), pullFrequency = 10.seconds)
        // Polls rather than subscribing, so the removal says nothing about this ID.
        val item = cache.item(leaving._id, maximumAge = 10.minutes, pullFrequency = 31.seconds)
        var reportedDeleted = false
        val releases = listOf(
            list.addListener { },
            item.addListener { if (item.state.ready && item.state.getOrNull() == null) reportedDeleted = true },
        )
        delay(7.seconds)
        assertEquals(leaving, item.state.getOrNull())

        val moved = mock.modify(leaving._id, modification { it.int assign 1 })
        delay(40.seconds) // long enough for the item's own poll to find out the truth

        assertEquals(listOf(staying), list.state.getOrNull(), "the row left the list")
        assertFalse(reportedDeleted, "leaving somebody else's filter is not being deleted")
        assertEquals(moved, item.state.getOrNull(), "the row still exists, with its new value")

        releases.forEach { it() }
    }

    /** A list answers a lookup of a row it returned: the server named that row's value exactly. */
    @Test fun aListAnswersAnItemLookupWithoutARequest() = runTest2 {
        val (mock, data, cache) = backgroundScope.fixture(3)
        val listRelease = cache.quietList().addListener { }
        delay(1.seconds)
        mock.queries.clear()

        val item = cache.quietItem(data[1]._id)
        val itemRelease = item.addListener { }
        delay(1.seconds)

        assertEquals(data[1], item.state.getOrNull())
        assertEquals(0, mock.queries.size, "the list already knew this row exactly")

        itemRelease()
        listRelease()
    }

    /**
     * The other half of that: a limited list says nothing about rows past its end, so a lookup of one
     * has to be fetched rather than answered "does not exist".
     */
    @Test fun aRowBeyondAListIsNotARowThatDoesNotExist() = runTest2 {
        val (_, data, cache) = backgroundScope.fixture(5)
        val listRelease = cache.quietList(limit = 2).addListener { }
        delay(1.seconds)

        val item = cache.quietItem(data[4]._id)
        val itemRelease = item.addListener { }
        delay(1.seconds)

        assertEquals(data[4], item.state.getOrNull(), "should have been fetched, not reported missing")

        itemRelease()
        listRelease()
    }

    /**
     * A limited list that loses a row from inside its covered range comes up short, and fetching the
     * row that moved up to fill the gap is exactly what a completeness claim is for.  The old design
     * left the hole there permanently.
     */
    @Test fun deletingInsideALimitedListPullsUpTheNextRow() = runTest2 {
        val (_, data, cache) = backgroundScope.fixture(10)
        val ref = cache.list(
            Query(Condition.Always, sort { it.int.ascending() }, limit = 3),
            maximumAge = 10.minutes,
            pullFrequency = 10.seconds,
        )
        val release = ref.addListener { }
        delay(1.seconds)
        assertEquals(listOf(1, 2, 3), ref.state.getOrNull()?.map { it.int })

        cache.quietItem(data[1]._id).delete()
        delay(1.seconds)

        assertEquals(listOf(1, 3, 4), ref.state.getOrNull()?.map { it.int }, "the gap should have been filled")

        release()
    }

    /** Paging again continues from the new edge of what is known rather than starting over. */
    @Test fun pagingForwardTwiceContinuesFromTheNewBoundary() = runTest2 {
        val (mock, data, cache) = backgroundScope.fixture(10)
        val ref = cache.quietList(limit = 3)
        val release = ref.addListener { }
        delay(1.seconds)
        mock.queries.clear()

        ref.limit(6)
        ref.limit(9)

        assertEquals(data.take(9), ref.state.getOrNull())
        assertEquals(2, mock.queries.size, "one request per page")
        assertTrue(mock.queries.all { it.limit == 3 }, "each page should ask only for the rows it lacks")

        release()
    }

    /**
     * A row we hold that a fresh answer leaves out of its own range is a stale copy, and every claim
     * that relied on it has to go with it - otherwise those lists keep serving a row the server says
     * is not there.
     */
    @Test fun aRowTheServerNoLongerReturnsIsForgotten() = runTest2 {
        val (mock, data, cache) = backgroundScope.fixture(3)
        val list = cache.list(
            Query(Condition.Always, sort { it.int.ascending() }),
            maximumAge = 10.minutes,
            pullFrequency = 10.seconds,
        )
        val listRelease = list.addListener { }
        delay(1.seconds)

        val item = cache.quietItem(data[1]._id)
        val itemRelease = item.addListener { }
        delay(1.seconds)
        assertEquals(data[1], item.state.getOrNull(), "the list should have settled this")

        // Deleted behind the cache's back; the list's next poll simply won't mention it.
        mock.data.remove(data[1]._id)
        delay(15.seconds)

        assertEquals(listOf(data[0], data[2]), list.state.getOrNull())
        assertNotEquals(data[1], item.state.getOrNull(), "the stale copy should have been dropped too")

        itemRelease()
        listRelease()
    }

    /**
     * A poll's answer was assembled before it was sent, so it is not evidence against a row we heard
     * about while it was in flight.  Without that, optimistically inserting a row would make it blink
     * out again the moment an already-running poll came back.
     */
    @Test fun aRowLearnedWhileAPollWasInFlightSurvivesItsAnswer() = runTest2 {
        var afterQuery: (() -> Unit)? = null
        val mock = object : ClientModelRestEndpointsMock<LargeTestModel, Uuid>(this) {
            override suspend fun query(input: Query<LargeTestModel>): List<LargeTestModel> =
                super.query(input).also { afterQuery?.invoke(); afterQuery = null }
        }
        val data = (1..2).map { LargeTestModel(int = it) }
        mock.data.putAll(data.associateBy { it._id })
        val cache = ModelCache(mock, LargeTestModel.serializer(), scope = backgroundScope, log = testLog)

        val ref = cache.list(
            Query(Condition.Always, sort { it.int.ascending() }),
            maximumAge = 10.minutes,
            pullFrequency = 10.seconds,
        )
        val release = ref.addListener { }
        delay(1.seconds)
        assertEquals(data, ref.state.getOrNull())

        // The next poll's rows are read from the server, and only then does the row appear.
        val late = LargeTestModel(int = 3)
        afterQuery = {
            mock.data[late._id] = late
            cache.localInsert(late)
        }
        delay(15.seconds)

        assertEquals(data + late, ref.state.getOrNull(), "the in-flight answer should not have undone this")

        release()
    }

    /**
     * A skipped query says nothing about the rows before it, so it cannot support the only kind of
     * claim this cache makes.  Better to say so than to cache it wrongly.
     */
    @Test fun skipIsRejected() = runTest2 {
        val (_, _, cache) = backgroundScope.fixture(3)
        assertFails { cache.list(Query(Condition.Always, sort { it.int.ascending() }, skip = 2)) }
    }

    /** A limit of zero or less asks the server for nothing; it is a mistake, not "unlimited". */
    @Test fun nonPositiveLimitsAreRejected() = runTest2 {
        val (_, _, cache) = backgroundScope.fixture(3)
        assertFails { cache.list(Query(Condition.Always, sort { it.int.ascending() }, limit = 0)) }
        val ref = cache.quietList()
        assertFails { ref.limit(0) }
    }

    /** Item lookups still coalesce into one `_id inside [...]` query rather than one request each. */
    @Test fun concurrentItemLookupsBecomeOneQuery() = runTest2 {
        val (mock, data, cache) = backgroundScope.fixture(5)
        val refs = data.map { cache.quietItem(it._id) }
        val releases = refs.map { it.addListener { } }
        delay(1.seconds)

        assertEquals(1, mock.queries.size, "five lookups should have been one request")
        assertEquals(data.toSet(), refs.mapNotNull { it.state.getOrNull() }.toSet())

        releases.forEach { it() }
    }
}
