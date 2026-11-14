package com.lightningkite.lightningserver.db

import com.lightningkite.kiteui.LogRoot
import com.lightningkite.kiteui.Platform
import com.lightningkite.kiteui.current
import com.lightningkite.reactive.core.Reactive
import com.lightningkite.services.database.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.Uuid

/** [ModelCache.count] through the cache, against a mock server that records every count it answers. */
class ModelCacheCountTest {
    val testLog = if (Platform.current == Platform.Desktop) LogRoot else null

    private class CountingMock(scope: CoroutineScope) : ClientModelRestEndpointsMock<LargeTestModel, Uuid>(scope) {
        val counts: MutableList<Condition<LargeTestModel>> = mutableListOf()
        override suspend fun count(input: Condition<LargeTestModel>): Int {
            counts.add(input)
            return super.count(input)
        }
    }

    private class CountingSocketMock(scope: CoroutineScope) :
        ClientModelRestEndpointsPlusUpdatesWebSocketMock<LargeTestModel, Uuid>(scope) {
        val counts: MutableList<Condition<LargeTestModel>> = mutableListOf()
        override suspend fun count(input: Condition<LargeTestModel>): Int {
            counts.add(input)
            return super.count(input)
        }
    }

    private class Fixture(val mock: CountingMock, val data: List<LargeTestModel>, val cache: ModelCache<LargeTestModel, Uuid>)

    /** A mock holding rows with `int` 1 through [rows], plus a cache over it. */
    private fun CoroutineScope.fixture(rows: Int = 3): Fixture {
        val mock = CountingMock(this)
        val data = (1..rows).map { LargeTestModel(int = it) }
        mock.data.putAll(data.associateBy { it._id })
        return Fixture(mock, data, ModelCache(mock, LargeTestModel.serializer(), scope = this, log = testLog))
    }

    private val above1: Condition<LargeTestModel> = Condition { it.int gt 1 }

    /** A count that neither expires nor polls, so only what the test does causes a request. */
    private fun ModelCache<LargeTestModel, Uuid>.quietCount(condition: Condition<LargeTestModel>) =
        count(condition, maximumAge = 10.minutes, pullFrequency = 10.minutes)

    private fun ModelCache<LargeTestModel, Uuid>.quietList(condition: Condition<LargeTestModel>, limit: Int = 100) =
        query(Query(condition, sort { it.int.ascending() }, limit = limit), maximumAge = 10.minutes, pullFrequency = 10.minutes)

    private fun ModelCache<LargeTestModel, Uuid>.quietItem(id: Uuid) =
        detail(id, maximumAge = 10.minutes, pullFrequency = 10.minutes)

    private val <V> Reactive<V>.value: V? get() = state.getOrNull()

    private fun <V> Reactive<V>.watch(): () -> Unit = addListener { }

    // =========================================================================
    // Fetching
    // =========================================================================

    @Test fun aCountIsFetchedOnceAndAnswered() = runTest2 {
        val f = backgroundScope.fixture()
        val count = f.cache.quietCount(above1)
        count.watch()
        delay(1.seconds)
        assertEquals(2, count.value)
        assertEquals(listOf(above1), f.mock.counts)

        delay(5.minutes)
        assertEquals(1, f.mock.counts.size, "a quiet count polled anyway")
    }

    @Test fun aCountIsNotReadyUntilItsFirstAnswer() = runTest2 {
        val f = backgroundScope.fixture()
        val count = f.cache.quietCount(above1)
        count.watch()
        assertFalse(count.state.ready)
        delay(1.seconds)
        assertTrue(count.state.ready)
    }

    @Test fun aSecondReaderReusesAFreshCount() = runTest2 {
        val f = backgroundScope.fixture()
        f.cache.quietCount(above1).watch()
        delay(1.seconds)

        val second = f.cache.count(above1, maximumAge = 1.minutes, pullFrequency = 10.minutes)
        second.watch()
        delay(1.seconds)
        assertEquals(2, second.value)
        assertEquals(1, f.mock.counts.size)
    }

    @Test fun differentConditionsAreCountedSeparately() = runTest2 {
        val f = backgroundScope.fixture()
        val a = f.cache.quietCount(above1)
        val b = f.cache.quietCount(Condition { it.int gt 2 })
        a.watch(); b.watch()
        delay(1.seconds)
        assertEquals(2, a.value)
        assertEquals(1, b.value)
        assertEquals(2, f.mock.counts.size)
    }

    @Test fun aReaderOpeningOnACountOlderThanItsMaximumAgeFetches() = runTest2 {
        val f = backgroundScope.fixture()
        f.cache.quietCount(above1).watch()
        delay(1.seconds)
        f.mock.data.values.first { it.int == 1 }.let { f.mock.data[it._id] = it.copy(int = 9) }

        delay(2.minutes)
        val strict = f.cache.count(above1, maximumAge = 1.minutes, pullFrequency = 10.minutes)
        strict.watch()
        assertFalse(strict.state.ready, "opened on a count older than it accepts")
        delay(1.seconds)
        assertEquals(3, strict.value)
        assertEquals(2, f.mock.counts.size)
    }

    @Test fun aCountPollsAtItsPullFrequency() = runTest2 {
        val f = backgroundScope.fixture()
        val count = f.cache.count(above1, maximumAge = 10.minutes, pullFrequency = 1.minutes)
        count.watch()
        delay(1.seconds)
        assertEquals(2, count.value)

        // Claude: Changed behind the cache's back, so only polling can notice.
        f.mock.data[Uuid.random()] = LargeTestModel(int = 5)
        delay(30.seconds)
        assertEquals(2, count.value)
        delay(1.minutes)
        assertEquals(3, count.value)
    }

    @Test fun equalCountsAreEqualReaders() = runTest2 {
        val f = backgroundScope.fixture()
        assertEquals(f.cache.quietCount(above1), f.cache.quietCount(Condition { it.int gt 1 }))
        assertEquals(f.cache.quietCount(above1).hashCode(), f.cache.quietCount(Condition { it.int gt 1 }).hashCode())
        assertFalse(f.cache.quietCount(above1) == f.cache.quietCount(Condition { it.int gt 2 }))
        assertFalse(f.cache.quietCount(above1) == f.cache.count(above1, maximumAge = 1.minutes))
    }

    // =========================================================================
    // Errors
    // =========================================================================

    @Test fun aFailedFirstFetchIsReportedAsAnError() = runTest2 {
        val f = backgroundScope.fixture()
        f.mock.connectivityFailure = true
        val count = f.cache.quietCount(above1)
        count.watch()
        delay(1.seconds)
        assertNotNull(count.state.exception)
    }

    @Test fun aFailedRefreshKeepsShowingAFreshCount() = runTest2 {
        val f = backgroundScope.fixture()
        val count = f.cache.quietCount(above1)
        count.watch()
        delay(1.seconds)
        f.mock.connectivityFailure = true
        f.cache.localUpsert(LargeTestModel(int = 7))
        delay(1.seconds)
        assertEquals(2, count.value, "a failed refresh blanked a count still within its maximum age")
    }

    @Test fun aFailedRefreshRetriesAndRecovers() = runTest2 {
        val f = backgroundScope.fixture()
        val count = f.cache.quietCount(above1)
        count.watch()
        delay(1.seconds)
        f.mock.connectivityFailure = true
        val added = LargeTestModel(int = 7)
        f.mock.data[added._id] = added
        f.cache.localUpsert(added)
        delay(1.seconds)
        f.mock.connectivityFailure = false
        assertEquals(2, f.mock.counts.size, "one attempt, which failed")

        // Claude: A failure waits out the pull frequency before retrying, rather than spinning.
        delay(9.minutes)
        assertEquals(2, f.mock.counts.size)
        assertEquals(2, count.value)
        delay(2.minutes)
        assertEquals(3, count.value)
        assertEquals(3, f.mock.counts.size)
    }

    // =========================================================================
    // Writes through the cache
    // =========================================================================

    @Test fun insertingARowRefetchesTheCount() = runTest2 {
        val f = backgroundScope.fixture()
        val count = f.cache.quietCount(above1)
        count.watch()
        delay(1.seconds)

        f.cache.insert(LargeTestModel(int = 5))
        assertEquals(2, count.value, "the old count should keep showing while the new one loads")
        delay(1.seconds)
        assertEquals(3, count.value)
        assertEquals(2, f.mock.counts.size)
    }

    @Test fun insertingARowStaysCorrectEvenIfItDoesNotMatch() = runTest2 {
        val f = backgroundScope.fixture()
        val count = f.cache.quietCount(above1)
        count.watch()
        delay(1.seconds)

        f.cache.insert(LargeTestModel(int = 0))
        delay(1.seconds)
        assertEquals(2, count.value)
        // Claude: The cache can't tell a new row from one it never saw, which might have left the condition.
        assertEquals(2, f.mock.counts.size, "a write to a row never seen should be re-asked")
    }

    @Test fun changingAHeldRowWithoutMovingItInOrOutDoesNotRefetch() = runTest2 {
        val f = backgroundScope.fixture()
        val count = f.cache.quietCount(above1)
        count.watch()
        delay(1.seconds)
        f.data.forEach { f.cache.quietItem(it._id).watch() }
        delay(1.seconds)

        f.cache.modify(f.data[0]._id, Modification { it.short assign 5 })
        f.cache.modify(f.data[1]._id, Modification { it.short assign 5 })
        f.cache.modify(f.data[2]._id, Modification { it.int assign 10 })
        delay(1.seconds)
        assertEquals(2, count.value)
        assertEquals(1, f.mock.counts.size, "refetched for changes that could not move the count")
    }

    @Test fun movingAHeldRowIntoTheConditionRefetches() = runTest2 {
        val f = backgroundScope.fixture()
        val count = f.cache.quietCount(above1)
        count.watch()
        delay(1.seconds)
        f.data.forEach { f.cache.quietItem(it._id).watch() }
        delay(1.seconds)

        f.cache.modify(f.data[0]._id, Modification { it.int assign 10 })
        delay(1.seconds)
        assertEquals(3, count.value)
        assertEquals(2, f.mock.counts.size)
    }

    @Test fun movingAHeldRowOutOfTheConditionRefetches() = runTest2 {
        val f = backgroundScope.fixture()
        val count = f.cache.quietCount(above1)
        count.watch()
        delay(1.seconds)
        f.data.forEach { f.cache.quietItem(it._id).watch() }
        delay(1.seconds)

        f.cache.modify(f.data[2]._id, Modification { it.int assign 0 })
        delay(1.seconds)
        assertEquals(1, count.value)
    }

    @Test fun changingARowNeverSeenRefetches() = runTest2 {
        val f = backgroundScope.fixture()
        val count = f.cache.quietCount(above1)
        count.watch()
        delay(1.seconds)

        f.cache.modify(f.data[2]._id, Modification { it.int assign 0 })
        delay(1.seconds)
        assertEquals(1, count.value)
    }

    @Test fun deletingAMatchingRowRefetches() = runTest2 {
        val f = backgroundScope.fixture()
        val count = f.cache.quietCount(above1)
        count.watch()
        delay(1.seconds)

        f.cache.delete(f.data[1]._id)
        delay(1.seconds)
        assertEquals(1, count.value)
    }

    @Test fun deletingAHeldRowOutsideTheConditionDoesNotRefetch() = runTest2 {
        val f = backgroundScope.fixture()
        val count = f.cache.quietCount(above1)
        count.watch()
        delay(1.seconds)
        f.cache.quietItem(f.data[0]._id).watch()
        delay(1.seconds)

        f.cache.delete(f.data[0]._id)
        delay(1.seconds)
        assertEquals(2, count.value)
        assertEquals(1, f.mock.counts.size)
    }

    @Test fun aBulkModifyOfAFieldTheConditionReadsRefetches() = runTest2 {
        val f = backgroundScope.fixture()
        val count = f.cache.quietCount(above1)
        count.watch()
        delay(1.seconds)

        f.cache.bulkModify(MassModification(Condition.Always, Modification { it.int assign 0 }))
        delay(1.seconds)
        assertEquals(0, count.value)
    }

    @Test fun aBulkModifyOfAnUnrelatedFieldDoesNotRefetch() = runTest2 {
        val f = backgroundScope.fixture()
        val count = f.cache.quietCount(above1)
        count.watch()
        delay(1.seconds)

        f.cache.bulkModify(MassModification(Condition.Always, Modification { it.short assign 4 }))
        delay(1.seconds)
        assertEquals(2, count.value)
        assertEquals(1, f.mock.counts.size)
    }

    @Test fun aBulkModifyByIdOfRowsNeverSeenRefetches() = runTest2 {
        val f = backgroundScope.fixture()
        val count = f.cache.quietCount(above1)
        count.watch()
        delay(1.seconds)

        f.cache.bulkModify(f.data.map { it._id }.toSet(), Modification { it.int assign 9 })
        delay(1.seconds)
        assertEquals(3, count.value)
    }

    @Test fun aBulkDeleteRefetches() = runTest2 {
        val f = backgroundScope.fixture()
        val count = f.cache.quietCount(above1)
        count.watch()
        delay(1.seconds)

        assertEquals(1, f.cache.bulkDelete(Condition { it.int eq 3 }))
        delay(1.seconds)
        assertEquals(1, count.value)
    }

    @Test fun invalidateAllForgetsAndRefetchesTheCount() = runTest2 {
        val f = backgroundScope.fixture()
        val count = f.cache.quietCount(above1)
        count.watch()
        delay(1.seconds)

        f.mock.data[Uuid.random()] = LargeTestModel(int = 5)
        f.cache.invalidateAll()
        delay(1.seconds)
        assertEquals(3, count.value)
        assertEquals(2, f.mock.counts.size)
    }

    // =========================================================================
    // Local changes
    // =========================================================================

    // Claude: Steps are 6s apart because a summary refetches at most every 5s.
    @Test fun localChangesRefetchTheCount() = runTest2 {
        val f = backgroundScope.fixture()
        val count = f.cache.quietCount(above1)
        count.watch()
        delay(6.seconds)

        val added = LargeTestModel(int = 5)
        f.mock.data[added._id] = added
        f.cache.localUpsert(added)
        delay(6.seconds)
        assertEquals(3, count.value)

        f.mock.data.remove(added._id)
        f.cache.localDelete(added._id)
        delay(6.seconds)
        assertEquals(2, count.value)

        f.mock.data.replaceAll { _, it -> it.copy(int = it.int + 10) }
        f.cache.localBulkModify(MassModification(Condition.Always, Modification { it.int += 10 }))
        delay(6.seconds)
        assertEquals(3, count.value)

        f.mock.data.clear()
        f.cache.localBulkDelete(Condition.Always)
        delay(6.seconds)
        assertEquals(0, count.value)
    }

    // =========================================================================
    // Changes noticed by reading
    // =========================================================================

    @Test fun readingAHeldRowThatMovedRefetches() = runTest2 {
        val f = backgroundScope.fixture()
        f.cache.quietItem(f.data[0]._id).watch()
        val count = f.cache.quietCount(above1)
        count.watch()
        delay(1.seconds)

        // Claude: Someone else moved row 1 into the condition; re-reading the row is how we find out.
        f.mock.data[f.data[0]._id] = f.data[0].copy(int = 8)
        f.cache.invalidate(f.data[0]._id)
        delay(1.seconds)
        assertEquals(3, count.value)
    }

    @Test fun reReadingAListThatLostARowRefetches() = runTest2 {
        val f = backgroundScope.fixture()
        f.cache.quietList(Condition { it.int gt 2 }).watch()
        val count = f.cache.quietCount(above1)
        count.watch()
        delay(1.seconds)
        assertEquals(1, f.mock.counts.size)

        f.mock.data.remove(f.data[2]._id)
        f.cache.query(Query(Condition { it.int gt 2 }, sort { it.int.ascending() }), maximumAge = Duration.ZERO).watch()
        delay(1.seconds)
        assertEquals(1, count.value)
    }

    @Test fun readingRowsNeverSeenBeforeDoesNotRefetch() = runTest2 {
        val f = backgroundScope.fixture()
        val count = f.cache.quietCount(above1)
        count.watch()
        delay(1.seconds)

        f.cache.quietList(Condition { it.int gt 2 }).watch()
        f.data.forEach { f.cache.quietItem(it._id).watch() }
        delay(1.seconds)
        assertEquals(1, f.mock.counts.size)
    }

    @Test fun aChangeWhileTheCountIsInFlightIsNotLost() = runTest2 {
        val f = backgroundScope.fixture()
        val count = f.cache.quietCount(above1)
        count.watch()
        // Claude: The mock answers after 100ms, so this lands after the request went out.
        delay(50.milliseconds)
        val added = LargeTestModel(int = 5)
        f.mock.data[added._id] = added
        f.cache.localUpsert(added)
        delay(10.seconds)
        assertEquals(3, count.value)
        assertEquals(2, f.mock.counts.size)
    }

    @Test fun anAnswerInFlightDuringInvalidateAllIsNotTrusted() = runTest2 {
        val f = backgroundScope.fixture()
        val count = f.cache.quietCount(above1)
        count.watch()
        delay(50.milliseconds)
        f.mock.data[Uuid.random()] = LargeTestModel(int = 5)
        f.cache.invalidateAll()
        delay(10.seconds)
        assertEquals(3, count.value)
    }

    @Test fun aRowKnownMissingThatReappearsAfterInvalidateRefetches() = runTest2 {
        val f = backgroundScope.fixture()
        val ghost = LargeTestModel(int = 5)
        f.cache.quietItem(ghost._id).watch()
        delay(1.seconds)
        val count = f.cache.quietCount(above1)
        count.watch()
        delay(1.seconds)

        f.mock.data[ghost._id] = ghost
        f.cache.invalidate(ghost._id)
        delay(1.seconds)
        assertEquals(3, count.value)
    }

    @Test fun aChangeWhileARefetchIsInFlightIsNotLost() = runTest2 {
        val f = backgroundScope.fixture()
        val count = f.cache.quietCount(above1)
        count.watch()
        delay(1.seconds)

        val first = LargeTestModel(int = 5)
        f.mock.data[first._id] = first
        f.cache.localUpsert(first)
        delay(50.milliseconds)
        val second = LargeTestModel(int = 6)
        f.mock.data[second._id] = second
        f.cache.localUpsert(second)
        delay(10.seconds)
        assertEquals(4, count.value)
        assertEquals(3, f.mock.counts.size)
    }

    // =========================================================================
    // Answering from rows held
    // =========================================================================

    @Test fun aCompleteListAnswersTheCountWithoutARequest() = runTest2 {
        val f = backgroundScope.fixture()
        f.cache.quietList(Condition.Always).watch()
        delay(1.seconds)

        val count = f.cache.quietCount(above1)
        count.watch()
        assertEquals(2, count.value)
        delay(1.seconds)
        assertEquals(0, f.mock.counts.size)

        f.cache.localUpsert(LargeTestModel(int = 4))
        assertEquals(3, count.value, "a count answered from rows held should follow them immediately")
        delay(1.seconds)
        assertEquals(0, f.mock.counts.size)
    }

    @Test fun aMaskedCountIsNotAnsweredFromRowsHeld() = runTest2 {
        val mock = CountingMock(backgroundScope)
        // Claude: Rows with `boolean` false have `int` masked to 0.
        mock.modelPermissions = ModelPermissions.allowAll<LargeTestModel>().copy(
            readMask = Mask(listOf(Condition<LargeTestModel> { it.boolean eq true } to Modification<LargeTestModel> { it.int assign 0 }))
        )
        val data = listOf(
            LargeTestModel(int = 1, boolean = true),
            LargeTestModel(int = 2, boolean = false),
            LargeTestModel(int = 3, boolean = false),
        )
        mock.data.putAll(data.associateBy { it._id })
        val cache = ModelCache(mock, LargeTestModel.serializer(), scope = backgroundScope, log = testLog)
        delay(1.seconds)
        cache.query(Query(Condition.Always, listOf(), limit = 100), 10.minutes, 10.minutes).watch()
        delay(1.seconds)

        val count = cache.quietCount(Condition { it.int eq 0 })
        count.watch()
        delay(1.seconds)
        assertEquals(0, count.value)
        assertEquals(1, mock.counts.size)
    }

    @Test fun anIncompleteListDoesNotAnswerTheCount() = runTest2 {
        val f = backgroundScope.fixture(rows = 5)
        f.cache.quietList(Condition.Always, limit = 2).watch()
        delay(1.seconds)

        val count = f.cache.quietCount(above1)
        count.watch()
        delay(1.seconds)
        assertEquals(4, count.value)
        assertEquals(1, f.mock.counts.size)
    }

    @Test fun aNarrowerListDoesNotAnswerABroaderCount() = runTest2 {
        val f = backgroundScope.fixture()
        f.cache.quietList(Condition { it.int gt 2 }).watch()
        delay(1.seconds)

        val count = f.cache.quietCount(above1)
        count.watch()
        delay(1.seconds)
        assertEquals(2, count.value)
        assertEquals(1, f.mock.counts.size)
    }

    @Test fun aServerCountNewerThanTheRowsHeldIsPreferred() = runTest2 {
        val f = backgroundScope.fixture()
        f.cache.quietList(Condition.Always).watch()
        delay(1.seconds)
        f.mock.data[Uuid.random()] = LargeTestModel(int = 5)

        delay(1.minutes)
        val count = f.cache.count(above1, maximumAge = 30.seconds, pullFrequency = 10.minutes)
        count.watch()
        delay(1.seconds)
        assertEquals(3, count.value, "rows held were too old, so the server should have been asked")
    }

    // =========================================================================
    // Sockets
    // =========================================================================

    private fun CoroutineScope.socketFixture(): Triple<CountingSocketMock, List<LargeTestModel>, ModelCache<LargeTestModel, Uuid>> {
        val mock = CountingSocketMock(this)
        val data = (1..3).map { LargeTestModel(int = it) }
        mock.data.putAll(data.associateBy { it._id })
        return Triple(mock, data, ModelCache(mock, LargeTestModel.serializer(), scope = this, log = testLog))
    }

    @Test fun aLiveCountRefetchesWhenTheSocketReportsAChange() = runTest2 {
        val (mock, _, cache) = backgroundScope.socketFixture()
        val count = cache.count(above1, maximumAge = 10.minutes, pullFrequency = Duration.ZERO)
        count.watch()
        delay(10.seconds)
        assertEquals(2, count.value)
        val before = mock.counts.size

        // Claude: Another client's insert, which only the socket tells us about.
        mock.change(CollectionUpdates(updates = setOf(LargeTestModel(int = 6))))
        delay(1.seconds)
        assertEquals(3, count.value)
        assertEquals(before + 1, mock.counts.size)
    }

    @Test fun aLiveCountRefetchesWhenARowLeavesTheCondition() = runTest2 {
        val (mock, data, cache) = backgroundScope.socketFixture()
        val count = cache.count(above1, maximumAge = 10.minutes, pullFrequency = Duration.ZERO)
        count.watch()
        delay(10.seconds)

        mock.change(CollectionUpdates(updates = setOf(data[2].copy(int = 0))))
        delay(1.seconds)
        assertEquals(1, count.value)
    }

    @Test fun aLiveCountDoesNotPoll() = runTest2 {
        val (mock, _, cache) = backgroundScope.socketFixture()
        val count = cache.count(above1, maximumAge = 10.minutes, pullFrequency = Duration.ZERO)
        count.watch()
        delay(10.seconds)
        val before = mock.counts.size
        delay(5.minutes)
        assertEquals(before, mock.counts.size, "a count the socket keeps live polled anyway")

        mock.change(CollectionUpdates(updates = setOf(LargeTestModel(int = 6))))
        delay(1.seconds)
        assertEquals(3, count.value)
        assertEquals(before + 1, mock.counts.size)
    }

    @Test fun aLiveCountRefetchesWhenAHeldRowItCountedLeaves() = runTest2 {
        val (mock, data, cache) = backgroundScope.socketFixture()
        val count = cache.count(above1, maximumAge = 10.minutes, pullFrequency = Duration.ZERO)
        count.watch()
        delay(10.seconds)
        cache.detail(data[2]._id, 10.minutes, 10.minutes).watch()
        delay(1.seconds)
        val before = mock.counts.size

        mock.change(CollectionUpdates(updates = setOf(data[2].copy(int = 0))))
        delay(1.seconds)
        assertEquals(1, count.value)
        assertEquals(before + 1, mock.counts.size)
    }

    @Test fun aLiveCountIgnoresAHeldRowChangingInPlace() = runTest2 {
        val (mock, data, cache) = backgroundScope.socketFixture()
        val count = cache.count(above1, maximumAge = 10.minutes, pullFrequency = Duration.ZERO)
        count.watch()
        delay(10.seconds)
        // Claude: Read after the count was requested, so it is the version the count saw.
        cache.detail(data[2]._id, 10.minutes, 10.minutes).watch()
        delay(1.seconds)
        val before = mock.counts.size

        mock.change(CollectionUpdates(updates = setOf(data[2].copy(short = 9))))
        delay(1.seconds)
        assertEquals(before, mock.counts.size, "a row that stayed inside the condition can't change the count")
    }

    @Test fun aLiveCountFollowsARowHeldFromBeforeItsRequest() = runTest2 {
        val (mock, data, cache) = backgroundScope.socketFixture()
        cache.detail(data[0]._id, 10.minutes, 10.minutes).watch()
        delay(6.seconds)
        // Claude: Changed while nothing was subscribed, so the held copy is out of date.
        mock.data[data[0]._id] = data[0].copy(int = 5)

        val count = cache.count(above1, maximumAge = 10.minutes, pullFrequency = Duration.ZERO)
        count.watch()
        delay(10.seconds)
        assertEquals(3, count.value)

        mock.change(CollectionUpdates(updates = setOf(data[0].copy(int = 5, short = 1))))
        delay(6.seconds)
        mock.change(CollectionUpdates(updates = setOf(data[0].copy(int = 0))))
        delay(6.seconds)
        assertEquals(2, count.value, "the live count kept counting a row the socket said left")
    }

    @Test fun releasingTheLastListenerStopsRefreshing() = runTest2 {
        val f = backgroundScope.fixture()
        val count = f.cache.count(above1, maximumAge = 10.minutes, pullFrequency = 1.minutes)
        val release = count.watch()
        delay(1.seconds)
        release()
        delay(10.minutes)
        assertEquals(1, f.mock.counts.size)
    }

    @Test fun manyChangesAtOnceCauseOneRefetch() = runTest2 {
        val f = backgroundScope.fixture()
        val count = f.cache.quietCount(above1)
        count.watch()
        delay(1.seconds)

        repeat(20) {
            val added = LargeTestModel(int = 10 + it)
            f.mock.data[added._id] = added
            f.cache.localUpsert(added)
        }
        delay(1.seconds)
        assertEquals(22, count.value)
        assertEquals(2, f.mock.counts.size)
    }

    @Test fun concurrentWritesAllEndUpCounted() = runTest2 {
        val f = backgroundScope.fixture()
        val count = f.cache.quietCount(above1)
        count.watch()
        delay(1.seconds)

        val jobs = (1..10).map { i -> backgroundScope.launch { delay((i * 37).milliseconds); f.cache.insert(LargeTestModel(int = 100 + i)) } }
        jobs.forEach { it.join() }
        delay(10.seconds)
        assertEquals(12, count.value)
    }
}

/** The summary half of [CoverageIndex], with timestamps passed in by hand. */
class CoverageIndexSummaryTest {
    private val serializer = LargeTestModel.serializer()
    private val t0 = Instant0
    private fun at(seconds: Int) = t0 + seconds.seconds
    private val above1: Condition<LargeTestModel> = Condition { it.int gt 1 }
    private val idOrder = listOf<SortPart<LargeTestModel>>().ensureTotal(serializer)

    private fun index() = CoverageIndex<LargeTestModel, Uuid>(serializer)

    @Test fun aSummaryIsStoredAndFresh() {
        val i = index()
        i.summarized("k", above1, emptyList(), 2, at(1), at(1))
        val s = assertNotNull(i.summary("k"))
        assertEquals(2, s.value)
        assertEquals(at(1), s.at)
        assertFalse(s.stale)
    }

    @Test fun aChangeAfterTheRequestMakesItStale() {
        val i = index()
        i.summarized("k", above1, emptyList(), 2, at(1), at(1))
        i.mutated(listOf(LargeTestModel(int = 5)), at(2))
        assertTrue(i.summary("k")!!.stale)
    }

    @Test fun aChangeBeforeTheRequestDoesNot() {
        val i = index()
        i.summarized("k", above1, emptyList(), 2, at(5), at(5))
        i.mutated(listOf(LargeTestModel(int = 5)), at(2))
        assertFalse(i.summary("k")!!.stale)
    }

    @Test fun aChangeAtTheSameInstantAsTheRequestDoesNot() {
        val i = index()
        i.summarized("k", above1, emptyList(), 2, at(1), at(1))
        i.mutated(listOf(LargeTestModel(int = 5)), at(1))
        assertFalse(i.summary("k")!!.stale)
    }

    @Test fun aNewerAnswerClearsStaleness() {
        val i = index()
        i.summarized("k", above1, emptyList(), 2, at(1), at(1))
        i.mutated(listOf(LargeTestModel(int = 5)), at(2))
        i.summarized("k", above1, emptyList(), 3, at(3), at(3))
        assertFalse(i.summary("k")!!.stale)
    }

    @Test fun anAnswerRequestedBeforeTheChangeStaysStale() {
        val i = index()
        i.summarized("k", above1, emptyList(), 2, at(1), at(1))
        i.mutated(listOf(LargeTestModel(int = 5)), at(3))
        i.summarized("k", above1, emptyList(), 2, at(2), at(4))
        assertTrue(i.summary("k")!!.stale)
    }

    @Test fun aChangeDuringTheFirstRequestMakesItStale() {
        val i = index()
        i.mutated(listOf(LargeTestModel(int = 5)), at(2))
        i.summarized("k", above1, emptyList(), 2, at(1), at(3))
        assertTrue(i.summary("k")!!.stale)
    }

    @Test fun aChangeBeforeTheFirstRequestDoesNot() {
        val i = index()
        i.mutated(listOf(LargeTestModel(int = 5)), at(0))
        i.summarized("k", above1, emptyList(), 2, at(1), at(1))
        assertFalse(i.summary("k")!!.stale)
    }

    @Test fun readingANeverSeenRowLeavesItFresh() {
        val i = index()
        i.summarized("k", above1, emptyList(), 2, at(1), at(1))
        val seen = LargeTestModel(int = 5)
        i.identified(listOf(seen), emptySet(), at(2))
        i.queried(Query(above1, idOrder, limit = 10), listOf(seen, LargeTestModel(int = 6)).sortedBy { it._id }, at(2))
        assertFalse(i.summary("k")!!.stale)
    }

    @Test fun readingAHeldRowThatMovedMakesItStale() {
        val i = index()
        val row = LargeTestModel(int = 1)
        i.identified(listOf(row), emptySet(), at(0))
        i.summarized("k", above1, emptyList(), 2, at(1), at(1))
        i.identified(listOf(row.copy(int = 5)), emptySet(), at(2))
        assertTrue(i.summary("k")!!.stale)
    }

    @Test fun readingAHeldRowThatMovedBeforeTheSummaryDoesNot() {
        val i = index()
        val row = LargeTestModel(int = 1)
        i.identified(listOf(row), emptySet(), at(0))
        i.summarized("k", above1, emptyList(), 2, at(3), at(3))
        i.identified(listOf(row.copy(int = 5)), emptySet(), at(2))
        assertFalse(i.summary("k")!!.stale)
    }

    @Test fun aKnownMissingRowAppearingMakesItStale() {
        val i = index()
        val row = LargeTestModel(int = 5)
        i.identified(emptyList(), setOf(row._id), at(0))
        i.summarized("k", above1, emptyList(), 2, at(1), at(1))
        i.identified(listOf(row), emptySet(), at(2))
        assertTrue(i.summary("k")!!.stale)
    }

    @Test fun aHeldRowChangingOutsideTheConditionLeavesItFresh() {
        val i = index()
        val row = LargeTestModel(int = 0)
        i.summarized("k", above1, emptyList(), 2, at(1), at(1))
        i.identified(listOf(row), emptySet(), at(2))
        i.mutated(listOf(row.copy(short = 3)), at(3))
        i.deleted(setOf(row._id), at(4))
        assertFalse(i.summary("k")!!.stale)
    }

    @Test fun aHeldMatchingRowChangingAWatchedFieldMakesItStale() {
        val i = index()
        val row = LargeTestModel(int = 5)
        val short = LargeTestModel.short
        i.summarized("plain", above1, emptyList(), 2, at(1), at(1))
        i.summarized("watching", above1, listOf(short), 2, at(1), at(1))
        i.identified(listOf(row), emptySet(), at(2))
        i.mutated(listOf(row.copy(short = 3)), at(3))
        assertFalse(i.summary("plain")!!.stale)
        assertTrue(i.summary("watching")!!.stale)
    }

    @Test fun deletingANeverSeenRowMakesItStale() {
        val i = index()
        i.summarized("k", above1, emptyList(), 2, at(1), at(1))
        i.deleted(setOf(Uuid.random()), at(2))
        assertTrue(i.summary("k")!!.stale)
    }

    @Test fun aBulkModifyMarksOnlySummariesReadingWhatItWrites() {
        val i = index()
        i.summarized("int", above1, emptyList(), 2, at(1), at(1))
        i.summarized("short", Condition { it.short gt 1 }, emptyList(), 2, at(1), at(1))
        i.summarized("watching", above1, listOf(LargeTestModel.short), 2, at(1), at(1))
        i.rowsModified(Modification { it.short assign 2 }, at(2))
        assertFalse(i.summary("int")!!.stale)
        assertTrue(i.summary("short")!!.stale)
        assertTrue(i.summary("watching")!!.stale)
    }

    @Test fun aBulkDeleteMarksEverySummary() {
        val i = index()
        i.summarized("a", above1, emptyList(), 2, at(1), at(1))
        i.summarized("b", Condition { it.short gt 1 }, emptyList(), 2, at(1), at(1))
        i.bulkDeleted(Condition { it.int eq 99 }, at(2))
        assertTrue(i.summary("a")!!.stale)
        assertTrue(i.summary("b")!!.stale)
    }

    @Test fun aListReReadThatDropsAMatchingRowMakesItStale() {
        val i = index()
        val row = LargeTestModel(int = 5)
        val listQuery = Query(Condition<LargeTestModel> { it.int gt 2 }, idOrder, limit = 10)
        i.queried(listQuery, listOf(row), at(0))
        i.summarized("k", above1, emptyList(), 2, at(1), at(1))
        i.queried(listQuery, emptyList(), at(2))
        assertTrue(i.summary("k")!!.stale)
    }

    @Test fun invalidatingForgetsSummaries() {
        val i = index()
        i.summarized("k", above1, emptyList(), 2, at(1), at(1))
        i.invalidate(at(2))
        assertEquals(null, i.summary("k"))
    }

    @Test fun aKnownMissingRowReappearingAfterInvalidateMakesItStale() {
        val i = index()
        val row = LargeTestModel(int = 5)
        i.identified(emptyList(), setOf(row._id), at(0))
        i.summarized("k", above1, emptyList(), 2, at(1), at(1))
        i.invalidate(row._id)
        i.identified(listOf(row), emptySet(), at(2))
        assertTrue(i.summary("k")!!.stale)
    }

    @Test fun anAnswerRequestedBeforeInvalidateAndStoredAfterIsStale() {
        val i = index()
        i.invalidate(at(2))
        i.summarized("k", above1, emptyList(), 2, at(1), at(3))
        assertTrue(i.summary("k")!!.stale)
        i.summarized("k", above1, emptyList(), 2, at(3), at(3))
        assertFalse(i.summary("k")!!.stale)
    }

    @Test fun aChangeToARowHeldSinceBeforeTheRequestIsComparedToo() {
        // Claude: The held copy could be older than what the summary saw. Trusting it anyway is a chosen
        // trade: otherwise, once a summary refetches, every change to any row would refetch it.
        val i = index()
        val row = LargeTestModel(int = 0)
        i.identified(listOf(row), emptySet(), at(0))
        i.summarized("k", above1, emptyList(), 2, at(1), at(1))
        i.mutated(listOf(row.copy(short = 3)), at(2))
        assertFalse(i.summary("k")!!.stale)
    }

    @Test fun aClockThatWentBackwardsDoesNotKeepItStale() {
        val i = index()
        i.summarized("k", above1, emptyList(), 2, at(10), at(10))
        i.mutated(listOf(LargeTestModel(int = 5)), at(11))
        assertTrue(i.summary("k")!!.stale)
        // Claude: The clock stepped back to 5; one refetch is stale, the next is trusted.
        i.summarized("k", above1, emptyList(), 2, at(5), at(6))
        assertTrue(i.summary("k")!!.stale)
        i.summarized("k", above1, emptyList(), 2, at(7), at(8))
        assertFalse(i.summary("k")!!.stale)
    }

    @Test fun aListReReadDroppingARowOutsideTheConditionMarksItToo() {
        val i = index()
        val row = LargeTestModel(int = 5)
        val listQuery = Query(Condition<LargeTestModel> { it.int gt 2 }, idOrder, limit = 10)
        i.queried(listQuery, listOf(row), at(0))
        i.summarized("k", Condition { it.int lt 3 }, emptyList(), 0, at(1), at(1))
        // Claude: The row left the list for somewhere unknown, possibly into the summary's condition.
        i.queried(listQuery, emptyList(), at(2))
        assertTrue(i.summary("k")!!.stale)
    }

    @Test fun learningTheMaskKeepsSummaries() {
        val i = index()
        i.summarized("k", above1, emptyList(), 2, at(1), at(1))
        i.useMasking(Mask(listOf(Condition<LargeTestModel> { it.boolean eq true } to Modification<LargeTestModel> { it.short assign 0 })))
        val s = assertNotNull(i.summary("k"))
        assertEquals(2, s.value)
        assertFalse(s.stale)
    }

    @Test fun aChangeToARowHeldSinceTheRequestIsCompared() {
        val i = index()
        val row = LargeTestModel(int = 0)
        i.summarized("k", above1, emptyList(), 2, at(1), at(1))
        i.identified(listOf(row), emptySet(), at(2))
        i.mutated(listOf(row.copy(short = 3)), at(3))
        assertFalse(i.summary("k")!!.stale)
        i.mutated(listOf(row.copy(int = 9)), at(4))
        assertTrue(i.summary("k")!!.stale)
    }

    @Test fun aSocketRemovalMarksEverySummary() {
        val i = index()
        val row = LargeTestModel(int = 0)
        i.summarized("k", above1, emptyList(), 2, at(1), at(1))
        i.identified(listOf(row), emptySet(), at(2))
        // Claude: Where the row went is unknown; it may have entered the condition.
        i.socketDelta(emptySet(), setOf(row._id), at(3)) { _, _ -> false }
        assertTrue(i.summary("k")!!.stale)
    }

    @Test fun invalidatingOneRowKeepsSummaries() {
        val i = index()
        val row = LargeTestModel(int = 5)
        i.identified(listOf(row), emptySet(), at(0))
        i.summarized("k", above1, emptyList(), 2, at(1), at(1))
        i.invalidate(row._id)
        assertFalse(i.summary("k")!!.stale)
    }
}

private val Instant0 = kotlin.time.Instant.fromEpochSeconds(1_000_000)
