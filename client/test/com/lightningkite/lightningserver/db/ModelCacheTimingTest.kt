package com.lightningkite.lightningserver.db

import com.lightningkite.kiteui.ConnectionException
import com.lightningkite.services.database.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.Uuid

/**
 * What [ModelCache] does when things overlap in time: a fetch that outlives the listener that asked
 * for it, listeners that come and go while one is in flight, limits that move while an earlier move
 * is still loading, sockets that connect late or come and go, and refreshes that fail.
 *
 * Everything here is asserted through what a caller can see - `state`, `lastUpdatedAt`, and the
 * requests that actually reached the server - because that is the whole contract; how the polling
 * loop arranges itself to keep it is not.
 *
 * Counting requests is what most of these turn on, so nothing is logged: the mocks record every
 * query instead.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ModelCacheTimingTest {

    /** Records every query reaching the server, and can be made to fail without closing a socket. */
    private class QueryRecordingMock(scope: CoroutineScope) : ClientModelRestEndpointsMock<LargeTestModel, Uuid>(scope) {
        val queries: MutableList<Query<LargeTestModel>> = mutableListOf()
        var queryFails: Boolean = false
        override suspend fun query(input: Query<LargeTestModel>): List<LargeTestModel> {
            queries.add(input)
            if (queryFails) throw ConnectionException("query is down")
            return super.query(input)
        }
    }

    /** The same, over a socket.  Failing queries here leave the socket connected. */
    private class SocketRecordingMock(scope: CoroutineScope) :
        ClientModelRestEndpointsPlusUpdatesWebSocketMock<LargeTestModel, Uuid>(scope) {
        val queries: MutableList<Query<LargeTestModel>> = mutableListOf()
        var queryFails: Boolean = false
        override suspend fun query(input: Query<LargeTestModel>): List<LargeTestModel> {
            queries.add(input)
            if (queryFails) throw ConnectionException("query is down")
            return super.query(input)
        }
    }

    private fun CoroutineScope.fixture(count: Int): Triple<QueryRecordingMock, List<LargeTestModel>, ModelCache<LargeTestModel, Uuid>> {
        val mock = QueryRecordingMock(this)
        val data = (1..count).map { LargeTestModel(int = it) }
        mock.data.putAll(data.associateBy { it._id })
        return Triple(mock, data, ModelCache(mock, LargeTestModel.serializer(), scope = this))
    }

    private fun CoroutineScope.socketFixture(count: Int): Triple<SocketRecordingMock, List<LargeTestModel>, ModelCache<LargeTestModel, Uuid>> {
        val mock = SocketRecordingMock(this)
        val data = (1..count).map { LargeTestModel(int = it) }
        mock.data.putAll(data.associateBy { it._id })
        return Triple(mock, data, ModelCache(mock, LargeTestModel.serializer(), scope = this))
    }

    private val order get() = sort<LargeTestModel> { it.int.ascending() }
    private fun everything(limit: Int = 100) = Query<LargeTestModel>(Condition.Always, order, limit = limit)

    /** A list that neither expires nor polls, so assertions see only what the test caused. */
    private fun ModelCache<LargeTestModel, Uuid>.quietList(limit: Int = 100) =
        list(everything(limit), maximumAge = 10.minutes, pullFrequency = 10.minutes)

    private fun ModelCache<LargeTestModel, Uuid>.quietItem(id: Uuid) =
        item(id, maximumAge = 10.minutes, pullFrequency = 10.minutes)

    // =========================================================================
    // Listeners arriving and leaving around a fetch
    // =========================================================================

    /**
     * A retrieve belongs to the cache, not to whoever happened to trigger it, so abandoning it
     * halfway does not throw the answer away - and the next reader is served from it rather than
     * asking again.
     */
    @Test fun aFetchInFlightOutlivesTheListenerThatStartedIt() = runTest2 {
        val (mock, data, cache) = backgroundScope.fixture(3)
        val ref = cache.quietItem(data[0]._id)

        val release = ref.addListener { }
        delay(150.milliseconds) // the batch has gone out; the mock has not answered yet
        assertEquals(1, mock.queries.size, "the request should be in flight at this point")
        release()

        delay(1.seconds)
        val second = ref.addListener { }
        delay(1.seconds)

        assertEquals(data[0], ref.state.getOrNull(), "the abandoned answer should still have landed")
        assertEquals(1, mock.queries.size, "and should have answered the new listener too")
        second()
    }

    /**
     * A view that mounts and unmounts a few times before settling - a list re-rendering, say - must
     * not turn into a request each time.
     */
    @Test fun listenersThatComeAndGoInQuickSuccessionCostOneRequest() = runTest2 {
        val (mock, data, cache) = backgroundScope.fixture(3)
        val ref = cache.quietItem(data[0]._id)

        repeat(5) {
            val release = ref.addListener { }
            delay(10.milliseconds) // long enough for the loop to start retrieving
            release()
        }
        val keep = ref.addListener { }
        delay(2.seconds)

        assertEquals(1, mock.queries.size, "every attempt should have joined the same batch")
        assertEquals(data[0], ref.state.getOrNull())
        keep()
    }

    /**
     * The background work is owned by the set of listeners, not by the first one, so a listener that
     * arrives mid-fetch keeps it running when the original leaves - and it stops when the last one
     * does.
     */
    @Test fun aListenerArrivingMidFetchKeepsThePollingAliveWhenTheFirstOneLeaves() = runTest2 {
        val (mock, data, cache) = backgroundScope.fixture(3)
        val ref = cache.list(everything(), maximumAge = 10.minutes, pullFrequency = 5.seconds)

        val first = ref.addListener { }
        delay(50.milliseconds)
        val second = ref.addListener { }
        first()

        delay(12.seconds)
        assertEquals(data, ref.state.getOrNull())
        assertTrue(mock.queries.size >= 3, "polling should have continued; it made ${mock.queries.size} requests")

        second()
        val settled = mock.queries.size
        delay(30.seconds)
        assertEquals(settled, mock.queries.size, "nothing should poll once the last listener is gone")
    }

    // =========================================================================
    // Polling rhythm
    // =========================================================================

    /** Asking for a very short pull frequency does not get you one; five seconds is the floor. */
    @Test fun pollingNeverRunsFasterThanTheFiveSecondFloor() = runTest2 {
        val (mock, _, cache) = backgroundScope.fixture(3)
        val ref = cache.list(everything(), maximumAge = 10.minutes, pullFrequency = 100.milliseconds)

        val release = ref.addListener { }
        delay(30.seconds)
        release()

        assertTrue(mock.queries.size in 5..8, "expected roughly six polls in thirty seconds, got ${mock.queries.size}")
    }

    /**
     * A failure has to wait out a full interval like anything else.  Retrying as fast as the loop
     * can go would turn a server having a bad minute into a client hammering it.
     */
    @Test fun aFailingFetchWaitsAFullIntervalBeforeRetrying() = runTest2 {
        val (mock, _, cache) = backgroundScope.fixture(3)
        mock.queryFails = true
        val ref = cache.list(everything(), maximumAge = 10.minutes, pullFrequency = 10.seconds)

        val release = ref.addListener { }
        delay(60.seconds)
        release()

        assertNotNull(ref.state.exception, "the failure should be reported")
        assertTrue(mock.queries.size in 5..8, "expected roughly six attempts in sixty seconds, got ${mock.queries.size}")
    }

    /**
     * A refresh failing does not make what we already hold any older, so it keeps being served for
     * as long as the caller said it would accept.  Past that point the failure is all there is.
     */
    @Test fun freshCachedDataOutranksAFailedRefreshUntilItGoesStale() = runTest2 {
        val (mock, data, cache) = backgroundScope.fixture(3)
        val ref = cache.list(everything(), maximumAge = 30.seconds, pullFrequency = 5.seconds)
        val release = ref.addListener { }
        delay(1.seconds)
        assertEquals(data, ref.state.getOrNull())

        mock.queryFails = true
        delay(20.seconds)
        assertTrue(mock.queries.size >= 4, "the refreshes should really have been attempted and failed")
        assertEquals(data, ref.state.getOrNull(), "still inside the maximumAge the caller asked for")
        assertNull(ref.state.exception)

        delay(20.seconds)
        assertNotNull(ref.state.exception, "past its maximumAge, the failure is the honest answer")
        release()
    }

    // =========================================================================
    // Two readers of the same thing
    // =========================================================================

    /** Two references to the same row are the same reference, and cost one request between them. */
    @Test fun twoEqualReadablesAreEqualAndShareTheirRequest() = runTest2 {
        val (mock, data, cache) = backgroundScope.fixture(3)
        val item = cache.quietItem(data[0]._id)
        val sameItem = cache.quietItem(data[0]._id)
        val list = cache.quietList()
        val sameList = cache.quietList()

        assertEquals(item, sameItem)
        assertEquals(item.hashCode(), sameItem.hashCode())
        assertEquals(list, sameList)
        assertEquals(list.hashCode(), sameList.hashCode())

        val releases = listOf(item, sameItem, list, sameList).map { it.addListener { } }
        delay(2.seconds)

        assertEquals(2, mock.queries.size, "one lookup and one query, not two of each")
        assertEquals(data[0], sameItem.state.getOrNull())
        assertEquals(data, sameList.state.getOrNull())
        releases.forEach { it() }
    }

    /**
     * Two readers of one query with different limits share a single claim about it, which is only
     * ever as long as the last answer.  The shorter reader is therefore free, and the longer one
     * notices its knowledge runs out early and fetches the rest.
     */
    @Test fun aShorterReaderIsFreeAndALongerOneFetchesTheRest() = runTest2 {
        val (mock, data, cache) = backgroundScope.fixture(10)

        val short = cache.quietList(limit = 3)
        val shortRelease = short.addListener { }
        delay(1.seconds)
        assertEquals(data.take(3), short.state.getOrNull())
        assertEquals(1, mock.queries.size)

        // Knowledge stops at the third row, which is short of what this one asked for.
        val long = cache.quietList(limit = 8)
        val longRelease = long.addListener { }
        delay(1.seconds)
        assertEquals(data.take(8), long.state.getOrNull(), "it should have noticed and fetched the rest")
        assertEquals(2, mock.queries.size)

        // And now knowledge reaches past what a third, shorter reader wants, so it asks for nothing.
        val alsoShort = cache.quietList(limit = 5)
        val alsoShortRelease = alsoShort.addListener { }
        delay(1.seconds)
        assertEquals(data.take(5), alsoShort.state.getOrNull())
        assertEquals(2, mock.queries.size, "the long reader's answer already covered this")

        listOf(shortRelease, longRelease, alsoShortRelease).forEach { it() }
    }

    /**
     * The same two readers, both polling.  Each poll shortens or lengthens the shared claim under the
     * other one, so this is where they could sit refetching each other forever; they must not.
     */
    @Test fun pollingReadersOfOneQueryWithDifferentLimitsSettleInsteadOfFighting() = runTest2 {
        val (mock, data, cache) = backgroundScope.fixture(10)
        val short = cache.list(everything(3), maximumAge = 10.minutes, pullFrequency = 10.seconds)
        val long = cache.list(everything(8), maximumAge = 10.minutes, pullFrequency = 10.seconds)
        val releases = listOf(short.addListener { }, long.addListener { })
        delay(3.seconds)
        assertEquals(data.take(3), short.state.getOrNull())
        assertEquals(data.take(8), long.state.getOrNull())

        mock.queries.clear()
        delay(30.seconds) // three polling intervals

        assertTrue(
            mock.queries.size <= 9,
            "three intervals should be about six requests, not a fight; got ${mock.queries.size}",
        )
        releases.forEach { it() }
    }

    // =========================================================================
    // Moving the limit around
    // =========================================================================

    /**
     * Rows that fall outside a lowered limit are still held, so putting the limit back does not go
     * and read them again.
     */
    @Test fun aLimitRaisedThenLoweredThenRaisedAgainAsksForNothingItAlreadyHas() = runTest2 {
        val (mock, data, cache) = backgroundScope.fixture(10)
        val ref = cache.quietList(limit = 3)
        val release = ref.addListener { }
        delay(1.seconds)
        mock.queries.clear()

        ref.limit(6)
        assertEquals(data.take(6), ref.state.getOrNull())
        assertEquals(1, mock.queries.size, "one page forward")

        ref.limit(2)
        assertEquals(data.take(2), ref.state.getOrNull())
        assertEquals(1, mock.queries.size, "shrinking asks for nothing")

        ref.limit(6)
        assertEquals(data.take(6), ref.state.getOrNull(), "the rows it stopped showing were still held")
        assertEquals(1, mock.queries.size, "and re-raising asks for nothing either")
        release()
    }

    /**
     * The cursor a page is taken from is a position in the order, not a row that has to still be
     * there, so deleting the row it names does not strand the list.
     */
    @Test fun pagingForwardWorksFromACursorWhoseRowWasDeleted() = runTest2 {
        val (mock, data, cache) = backgroundScope.fixture(10)
        val ref = cache.quietList(limit = 3)
        val release = ref.addListener { }
        delay(1.seconds)
        assertEquals(data.take(3), ref.state.getOrNull())
        // Stop listening first, so the deletion's wake-up doesn't move the boundary for us.
        release()

        cache.quietItem(data[2]._id).delete()
        mock.queries.clear()

        ref.limit(6)
        assertEquals(
            listOf(1, 2, 4, 5, 6, 7),
            ref.state.getOrNull()?.map { it.int },
            "the deleted row's position is still a valid place to page from",
        )
        assertEquals(1, mock.queries.size)
        assertEquals(4, mock.queries.single().limit, "it should ask only for the four rows it lacks")
    }

    /**
     * Two overlapping extensions both page from the boundary as it stood when they started, so the
     * later one re-reads what the earlier one is already fetching.  Wasteful, but the list that comes
     * out is the whole list, in order, and matches the limit that was set last.
     */
    @Test fun twoOverlappingExtensionsBothPageFromTheBoundaryTheyStartedFrom() = runTest2 {
        val (mock, data, cache) = backgroundScope.fixture(20)
        val ref = cache.quietList(limit = 3)
        val release = ref.addListener { }
        delay(1.seconds)
        mock.queries.clear()

        val first = backgroundScope.launch { ref.limit(6) }
        delay(1.milliseconds)
        val second = backgroundScope.launch { ref.limit(9) }
        first.join()
        second.join()

        assertEquals(data.take(9), ref.state.getOrNull())
        assertEquals(9, ref.limit)
        assertEquals(
            listOf(3, 6),
            mock.queries.map { it.limit },
            "both pages started from the same boundary, so the second re-read the first's rows",
        )
        release()
    }

    // =========================================================================
    // Sockets
    // =========================================================================

    /**
     * Anything that changed while the socket was down was missed, so every reconnection has to
     * refetch - once, not once per flap.
     */
    @Test fun everyReconnectionRefetchesWhatItMightHaveMissed() = runTest2 {
        val (mock, data, cache) = backgroundScope.socketFixture(3)
        val ref = cache.list(everything(), maximumAge = 10.minutes, pullFrequency = 10.seconds)
        val release = ref.addListener { }
        delay(7.seconds)
        assertEquals(data, ref.state.getOrNull())
        mock.queries.clear()

        val addedInTheDark = LargeTestModel(int = 4)
        repeat(3) { attempt ->
            mock.connectivityFailure = true
            if (attempt == 2) mock.data[addedInTheDark._id] = addedInTheDark
            delay(1.seconds)
            mock.connectivityFailure = false
            delay(2.seconds)
        }

        assertEquals(data + addedInTheDark, ref.state.getOrNull(), "the row added during the outage should be here")
        assertTrue(mock.queries.size in 3..6, "one refetch per reconnection, got ${mock.queries.size}")
        release()
    }

    /**
     * A subscription is a promise to be told about changes, so a list it covers neither goes stale on
     * a clock nor needs polling to stay that way.  Not polling is the whole point of preferring a
     * socket, so it is asserted here rather than left implied.
     */
    @Test fun aListTheSocketCoversStaysReadyWithoutPolling() = runTest2 {
        val (mock, data, cache) = backgroundScope.socketFixture(3)
        val ref = cache.list(everything(), maximumAge = 20.seconds, pullFrequency = 5.seconds)
        val release = ref.addListener { }
        delay(7.seconds)
        assertEquals(data, ref.state.getOrNull())
        val settled = mock.queries.size

        // Any refresh from here would fail; only the queries are broken, the socket stays up.
        mock.queryFails = true
        delay(60.seconds)

        assertEquals(settled, mock.queries.size, "a covered list has nothing to poll for")
        assertEquals(data, ref.state.getOrNull(), "and nothing to expire for, three maximumAges later")
        assertNull(ref.state.exception)
        release()
    }

    /**
     * The other half: a subscription that does not match the row promises nothing about it, so a
     * change to that row goes unseen - which is the difference being covered actually makes, rather
     * than anything to do with how old the value is allowed to get.
     */
    @Test fun anItemIsNotKeptFreshBySubscriptionsThatDoNotCoverIt() = runTest2 {
        val (mock, data, cache) = backgroundScope.socketFixture(3)
        // Subscribed, and matching nothing in the collection.
        val elsewhere = cache.list(
            Query(condition { it.int gt 100 }, order),
            maximumAge = 10.minutes,
            pullFrequency = 5.seconds,
        )
        val elsewhereRelease = elsewhere.addListener { }
        // Too slow to subscribe on its own, so its only hope of hearing anything is the other one.
        val item = cache.item(data[0]._id, maximumAge = 20.seconds, pullFrequency = 10.minutes)
        val itemRelease = item.addListener { }
        delay(7.seconds)
        assertEquals(data[0], item.state.getOrNull())

        // Changed on the server, with nothing subscribed that would carry the news.
        mock.modify(data[0]._id, modification { it.short assign 7 })
        delay(30.seconds)
        assertEquals(data[0], item.state.getOrNull(), "nothing was promising to tell us about this row")

        itemRelease()
        elsewhereRelease()
    }

    // =========================================================================
    // Waking everything at once
    // =========================================================================

    /**
     * A deletion wakes every polling loop in the collection, because a limited list may now be a row
     * short.  The ones with nothing to do have to go straight back to sleep rather than all
     * refetching at once - a screen showing fifty rows deletes one all the time.
     */
    @Test fun aDeletionWakesEveryReaderWithoutSettingOffARefetchStorm() = runTest2 {
        val (mock, data, cache) = backgroundScope.fixture(50)
        val refs = data.map { cache.quietItem(it._id) }
        val releases = refs.map { it.addListener { } }
        delay(2.seconds)
        assertEquals(1, mock.queries.size, "the fifty lookups should have been one batch")

        cache.quietItem(data[0]._id).delete()
        delay(3.seconds)

        assertTrue(refs[0].state.ready, "the deletion should have been applied...")
        assertNull(refs[0].state.getOrNull(), "...as a confirmed absence")
        assertEquals(1, mock.queries.size, "and the other forty-nine had no reason to fetch")
        releases.forEach { it() }
    }

    /**
     * An overloaded socket has admitted it fell behind, so everything is suspect and gets dropped.
     * The readers then have to be woken rather than left loading until their next poll, which for a
     * socket-backed reader is deliberately a long way off.
     */
    @Test fun anOverloadedSocketMakesItsReadersRefetchPromptly() = runTest2 {
        val (mock, data, cache) = backgroundScope.socketFixture(3)
        val ref = cache.list(everything(), maximumAge = 10.minutes, pullFrequency = 60.seconds)
        val release = ref.addListener { }
        delay(7.seconds)
        assertEquals(data, ref.state.getOrNull())

        // Changed behind the cache's back, which is what an overload is admitting may have happened.
        val addedWhileOverloaded = LargeTestModel(int = 4)
        mock.data[addedWhileOverloaded._id] = addedWhileOverloaded
        mock.updatesWs.listeners.forEach { it(CollectionUpdates(overload = true)) }

        delay(2.seconds) // far short of the sixty seconds it would otherwise sleep for
        assertEquals(data + addedWhileOverloaded, ref.state.getOrNull())
        release()
    }

    // =========================================================================
    // Arriving on a screen, and staying on it
    // =========================================================================
    //
    // These two are different questions and the parameters answer one each.  `maximumAge` is asked
    // once, on arrival: is what we inherited fresh enough to show, or do we make them wait?  Opening
    // a screen onto stale data betrays what people expect a page transition to do.  `pullFrequency`
    // is the whole of the second question: how often this screen refreshes itself while it is up.

    /**
     * The defaults ask for nothing: `maximumAge` of [kotlin.time.Duration.INFINITE] says the value
     * never expires, and a `pullFrequency` of zero says do not poll.  Between them there is no reason
     * to ever go back to the server, and a reader that goes back anyway costs a request every few
     * seconds for as long as the screen is open.
     */
    @Test fun anItemThatNeitherExpiresNorPollsIsFetchedOnce() = runTest2 {
        val (mock, data, cache) = backgroundScope.fixture(3)
        val ref = cache.item(data[0]._id)
        val keep = ref.addListener { }

        delay(1.seconds)
        assertEquals(1, mock.queries.size, "the first read has to fetch")
        assertEquals(data[0], ref.state.getOrNull())

        delay(5.minutes)
        assertEquals(1, mock.queries.size, "nothing expired and nothing was asked for, so nothing should have been sent")
        keep()
    }

    /** The same for a list, which is where the cost multiplies with everything else on screen. */
    @Test fun aListThatNeitherExpiresNorPollsIsFetchedOnce() = runTest2 {
        val (mock, data, cache) = backgroundScope.fixture(3)
        val ref = cache.list(everything())
        val keep = ref.addListener { }

        delay(1.seconds)
        assertEquals(1, mock.queries.size, "the first read has to fetch")
        assertEquals(data, ref.state.getOrNull())

        delay(5.minutes)
        assertEquals(1, mock.queries.size, "nothing expired and nothing was asked for, so nothing should have been sent")
        keep()
    }

    /**
     * A maximum age is a tolerance, not a schedule.  Reading it as a schedule is how "I will accept
     * data ten seconds old" becomes a request every ten seconds for as long as the screen is up.
     */
    @Test fun aMaximumAgeAloneDoesNotPoll() = runTest2 {
        val (mock, _, cache) = backgroundScope.fixture(3)
        val ref = cache.list(everything(), maximumAge = 10.seconds)
        val keep = ref.addListener { }

        delay(1.seconds)
        assertEquals(1, mock.queries.size, "the first read has to fetch")

        delay(2.minutes)
        assertEquals(1, mock.queries.size, "no polling was asked for, so twelve maximum ages later there is still nothing to send")
        keep()
    }

    /** Arriving next to data older than the caller will accept means waiting for a fresh one. */
    @Test fun arrivingOnDataOlderThanTheMaximumAgeRefreshesAtOnce() = runTest2 {
        val (mock, data, cache) = backgroundScope.fixture(3)

        // Someone else already loaded this, then left.
        val first = cache.list(everything()).addListener { }
        delay(1.seconds)
        assertEquals(1, mock.queries.size)
        first()

        delay(3.minutes) // long enough that what they left behind is well past the tolerance below

        val arriving = cache.list(everything(), maximumAge = 10.seconds, pullFrequency = 60.seconds)
        val keep = arriving.addListener { }
        // Too old to show, so the screen waits rather than opening on it - the whole point of saying
        // how old is acceptable when you arrive somewhere.
        assertFalse(arriving.state.ready, "the inherited rows should not be shown while they are being replaced")

        delay(1.seconds)
        assertEquals(2, mock.queries.size, "the inherited rows were too old to open a screen on")
        assertEquals(data, arriving.state.getOrNull())
        keep()
    }

    /**
     * Once shown, data is not taken away again by getting older.  It is replaced when something
     * replaces it, and until then it is still the best answer there is.
     */
    @Test fun dataAlreadyShownIsNotWithdrawnWhenItPassesTheMaximumAge() = runTest2 {
        val (_, data, cache) = backgroundScope.fixture(3)
        val ref = cache.list(everything(), maximumAge = 10.seconds)
        val keep = ref.addListener { }

        delay(1.seconds)
        assertEquals(data, ref.state.getOrNull())

        delay(5.minutes)
        assertEquals(data, ref.state.getOrNull(), "thirty maximum ages later, and nothing has replaced it")
        keep()
    }

    /**
     * The same arrival next to data still within the tolerance shows it immediately and waits out
     * what is left of the interval - not a whole fresh one, which would let a screen opened often
     * enough put off refreshing forever.
     */
    @Test fun arrivingOnDataInsideTheMaximumAgeShowsItAndFinishesTheInterval() = runTest2 {
        val (mock, data, cache) = backgroundScope.fixture(3)

        val first = cache.list(everything()).addListener { }
        delay(1.seconds)
        assertEquals(1, mock.queries.size)
        first()

        // Half a minute on: still inside the tolerance below, but half way through the interval.
        delay(30.seconds)

        val arriving = cache.list(everything(), maximumAge = 60.seconds, pullFrequency = 60.seconds)
        val keep = arriving.addListener { }
        delay(1.seconds)
        assertEquals(1, mock.queries.size, "what was already here was fresh enough to show at once")
        assertEquals(data, arriving.state.getOrNull())

        delay(20.seconds)
        assertEquals(1, mock.queries.size, "the interval has not run out yet")

        // Thirty seconds after arrival, sixty after the rows were fetched.  Counting from arrival
        // instead would put this at ninety, and would let a screen opened often enough put off
        // refreshing forever.
        delay(20.seconds)
        assertEquals(2, mock.queries.size, "the interval runs from when the rows were fetched, not from arrival")
        keep()
    }
}
