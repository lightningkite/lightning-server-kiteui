package com.lightningkite.lightningserver.db

import com.lightningkite.kiteui.LogRoot
import com.lightningkite.kiteui.Platform
import com.lightningkite.kiteui.current
import com.lightningkite.reactive.core.Reactive
import com.lightningkite.services.database.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.Uuid

/**
 * [ModelCache.groupCount], [ModelCache.aggregate] and [ModelCache.groupAggregate].
 *
 * Staleness is shared with [ModelCache.count] and tested there in depth; these cover what differs:
 * the group and value fields also matter, and nothing is ever answered from rows held.
 */
class ModelCacheAggregateTest {
    val testLog = if (Platform.current == Platform.Desktop) LogRoot else null

    private class RecordingMock(scope: CoroutineScope) : ClientModelRestEndpointsPlusUpdatesWebSocketMock<LargeTestModel, Uuid>(scope) {
        val requests: MutableList<Any> = mutableListOf()
        override suspend fun count(input: Condition<LargeTestModel>): Int = super.count(input).also { requests.add(input) }
        override suspend fun groupCount(input: GroupCountQuery<LargeTestModel>): Map<String, Int> =
            super.groupCount(input).also { requests.add(input) }
        override suspend fun aggregate(input: AggregateQuery<LargeTestModel>): Double? =
            super.aggregate(input).also { requests.add(input) }
        override suspend fun groupAggregate(input: GroupAggregateQuery<LargeTestModel>): Map<String, Double?> =
            super.groupAggregate(input).also { requests.add(input) }
    }

    private class Fixture(val mock: RecordingMock, val data: List<LargeTestModel>, val cache: ModelCache<LargeTestModel, Uuid>)

    /** Rows with `int` 1 through 4; even ones have `boolean` set. */
    private fun CoroutineScope.fixture(): Fixture {
        val mock = RecordingMock(this)
        val data = (1..4).map { LargeTestModel(int = it, boolean = it % 2 == 0) }
        mock.data.putAll(data.associateBy { it._id })
        return Fixture(mock, data, ModelCache(mock, LargeTestModel.serializer(), scope = this, log = testLog))
    }

    private val above1: Condition<LargeTestModel> = Condition { it.int gt 1 }
    private val byBoolean = GroupCountQuery(above1, LargeTestModel.boolean)
    private val sumOfInt = AggregateQuery(Aggregate.Sum, above1, LargeTestModel.int)
    private val sumOfIntByBoolean = GroupAggregateQuery(Aggregate.Sum, above1, LargeTestModel.boolean, LargeTestModel.int)

    private val quiet = 10.minutes

    private val <V> Reactive<V>.value: V? get() = state.getOrNull()
    private fun <V> Reactive<V>.watch(): () -> Unit = addListener { }

    // =========================================================================
    // Answers
    // =========================================================================

    @Test fun groupCountAnswers() = runTest2 {
        val f = backgroundScope.fixture()
        val r = f.cache.groupCount(byBoolean, quiet, quiet)
        r.watch()
        delay(1.seconds)
        assertEquals(mapOf("true" to 2, "false" to 1), r.value)
        assertEquals(listOf<Any>(byBoolean), f.mock.requests)
    }

    @Test fun aggregateAnswersEachKind() = runTest2 {
        val f = backgroundScope.fixture()
        val values = listOf(2.0, 3.0, 4.0)
        val mean = values.average()
        val squares = values.sumOf { (it - mean) * (it - mean) }
        val expected = mapOf(
            Aggregate.Sum to 9.0,
            Aggregate.Average to 3.0,
            Aggregate.StandardDeviationSample to sqrt(squares / 2),
            Aggregate.StandardDeviationPopulation to sqrt(squares / 3),
        )
        val readers = expected.keys.associateWith { f.cache.aggregate(AggregateQuery(it, above1, LargeTestModel.int), quiet, quiet) }
        readers.values.forEach { it.watch() }
        delay(1.seconds)
        expected.forEach { (kind, value) -> assertEquals(value, assertNotNull(readers[kind]!!.value), 1e-9, "$kind") }
        assertEquals(4, f.mock.requests.size, "each kind of aggregate is its own request")
    }

    @Test fun anAggregateOfNoRowsIsNull() = runTest2 {
        val f = backgroundScope.fixture()
        val r = f.cache.aggregate(AggregateQuery(Aggregate.Sum, Condition { it.int gt 100 }, LargeTestModel.int), quiet, quiet)
        r.watch()
        delay(1.seconds)
        assertNull(r.value)
        assertEquals(true, r.state.ready, "null is an answer, not a missing one")
    }

    @Test fun groupAggregateAnswers() = runTest2 {
        val f = backgroundScope.fixture()
        val r = f.cache.groupAggregate(sumOfIntByBoolean, quiet, quiet)
        r.watch()
        delay(1.seconds)
        assertEquals(mapOf("true" to 6.0, "false" to 3.0), r.value)
    }

    @Test fun summariesOfTheSameConditionDoNotShareAnswers() = runTest2 {
        val f = backgroundScope.fixture()
        val count = f.cache.count(above1, quiet, quiet)
        val groups = f.cache.groupCount(byBoolean, quiet, quiet)
        val sum = f.cache.aggregate(sumOfInt, quiet, quiet)
        val groupSum = f.cache.groupAggregate(sumOfIntByBoolean, quiet, quiet)
        listOf(count, groups, sum, groupSum).forEach { it.watch() }
        delay(1.seconds)
        assertEquals(3, count.value)
        assertEquals(mapOf("true" to 2, "false" to 1), groups.value)
        assertEquals(9.0, sum.value)
        assertEquals(mapOf("true" to 6.0, "false" to 3.0), groupSum.value)
        assertEquals(4, f.mock.requests.size)
    }

    @Test fun aCompleteListDoesNotAnswerAnAggregate() = runTest2 {
        val f = backgroundScope.fixture()
        f.cache.query(Query(Condition.Always, sort { it.int.ascending() }), quiet, quiet).watch()
        delay(1.seconds)
        val sum = f.cache.aggregate(sumOfInt, quiet, quiet)
        sum.watch()
        delay(1.seconds)
        assertEquals(9.0, sum.value)
        assertEquals(1, f.mock.requests.size)
    }

    @Test fun equalQueriesAreEqualReaders() = runTest2 {
        val f = backgroundScope.fixture()
        assertEquals(
            f.cache.aggregate(sumOfInt, quiet, quiet),
            f.cache.aggregate(AggregateQuery(Aggregate.Sum, Condition { it.int gt 1 }, LargeTestModel.int), quiet, quiet),
        )
        assertFalse(
            f.cache.aggregate(sumOfInt, quiet, quiet) ==
                    f.cache.aggregate(AggregateQuery(Aggregate.Average, above1, LargeTestModel.int), quiet, quiet)
        )
        assertEquals(f.cache.groupCount(byBoolean, quiet, quiet), f.cache.groupCount(GroupCountQuery(above1, LargeTestModel.boolean), quiet, quiet))
    }

    @Test fun aFailedFirstFetchIsReportedAsAnError() = runTest2 {
        val f = backgroundScope.fixture()
        f.mock.connectivityFailure = true
        val sum = f.cache.aggregate(sumOfInt, quiet, quiet)
        sum.watch()
        delay(1.seconds)
        assertNotNull(sum.state.exception)
    }

    // =========================================================================
    // What makes them stale
    // =========================================================================

    /** Watches every row by ID, so changes to them compare against a known old version. */
    private fun Fixture.holdEveryRow() = data.forEach { cache.detail(it._id, quiet, quiet).watch() }

    @Test fun changingTheGroupOfAHeldMatchingRowRefetchesGroupedSummaries() = runTest2 {
        val f = backgroundScope.fixture()
        val count = f.cache.count(above1, quiet, quiet)
        val groups = f.cache.groupCount(byBoolean, quiet, quiet)
        val sum = f.cache.aggregate(sumOfInt, quiet, quiet)
        val groupSum = f.cache.groupAggregate(sumOfIntByBoolean, quiet, quiet)
        listOf(count, groups, sum, groupSum).forEach { it.watch() }
        delay(1.seconds)
        // Claude: Held after the summaries were requested, so changes compare against what they saw.
        f.holdEveryRow()
        delay(1.seconds)
        f.mock.requests.clear()

        f.cache.modify(f.data[2]._id, Modification { it.boolean assign true })
        delay(1.seconds)
        assertEquals(mapOf("true" to 3), groups.value)
        assertEquals(mapOf("true" to 9.0), groupSum.value)
        assertEquals(listOf<Any>(byBoolean, sumOfIntByBoolean).toSet(), f.mock.requests.toSet(), "only the grouped summaries read that field")
    }

    @Test fun changingTheValueOfAHeldMatchingRowRefetchesAggregates() = runTest2 {
        val f = backgroundScope.fixture()
        val groups = f.cache.groupCount(byBoolean, quiet, quiet)
        val sum = f.cache.aggregate(sumOfInt, quiet, quiet)
        val groupSum = f.cache.groupAggregate(sumOfIntByBoolean, quiet, quiet)
        listOf(groups, sum, groupSum).forEach { it.watch() }
        delay(1.seconds)
        // Claude: Held after the summaries were requested, so changes compare against what they saw.
        f.holdEveryRow()
        delay(1.seconds)
        f.mock.requests.clear()

        f.cache.modify(f.data[3]._id, Modification { it.int assign 10 })
        delay(1.seconds)
        assertEquals(15.0, sum.value)
        assertEquals(mapOf("true" to 12.0, "false" to 3.0), groupSum.value)
        assertEquals(listOf<Any>(sumOfInt, sumOfIntByBoolean).toSet(), f.mock.requests.toSet())
    }

    @Test fun changingAHeldRowOutsideTheConditionRefetchesNothing() = runTest2 {
        val f = backgroundScope.fixture()
        val groups = f.cache.groupCount(byBoolean, quiet, quiet)
        val sum = f.cache.aggregate(sumOfInt, quiet, quiet)
        val groupSum = f.cache.groupAggregate(sumOfIntByBoolean, quiet, quiet)
        listOf(groups, sum, groupSum).forEach { it.watch() }
        delay(1.seconds)
        // Claude: Held after the summaries were requested, so changes compare against what they saw.
        f.holdEveryRow()
        delay(1.seconds)
        f.mock.requests.clear()

        f.cache.modify(f.data[0]._id, Modification { it.boolean assign true })
        f.cache.modify(f.data[0]._id, Modification { it.int assign 0 })
        delay(1.seconds)
        assertEquals(emptyList(), f.mock.requests)
    }

    @Test fun changingAnUnrelatedFieldOfAHeldMatchingRowRefetchesNothing() = runTest2 {
        val f = backgroundScope.fixture()
        val groups = f.cache.groupCount(byBoolean, quiet, quiet)
        val sum = f.cache.aggregate(sumOfInt, quiet, quiet)
        val groupSum = f.cache.groupAggregate(sumOfIntByBoolean, quiet, quiet)
        listOf(groups, sum, groupSum).forEach { it.watch() }
        delay(1.seconds)
        // Claude: Held after the summaries were requested, so changes compare against what they saw.
        f.holdEveryRow()
        delay(1.seconds)
        f.mock.requests.clear()

        f.cache.modify(f.data[3]._id, Modification { it.short assign 7 })
        delay(1.seconds)
        assertEquals(emptyList(), f.mock.requests)
    }

    @Test fun aBulkModifyRefetchesOnlySummariesReadingWhatItWrites() = runTest2 {
        val f = backgroundScope.fixture()
        val groups = f.cache.groupCount(byBoolean, quiet, quiet)
        val sum = f.cache.aggregate(sumOfInt, quiet, quiet)
        val sumOfShort = f.cache.aggregate(AggregateQuery(Aggregate.Sum, above1, LargeTestModel.short), quiet, quiet)
        listOf(groups, sum, sumOfShort).forEach { it.watch() }
        delay(1.seconds)
        // Claude: Held after the summaries were requested, so changes compare against what they saw.
        f.holdEveryRow()
        delay(1.seconds)
        f.mock.requests.clear()

        f.cache.bulkModify(MassModification(Condition.Always, Modification { it.short assign 2 }))
        delay(1.seconds)
        assertEquals(6.0, sumOfShort.value)
        assertEquals(1, f.mock.requests.size)

        f.cache.bulkModify(MassModification(Condition.Always, Modification { it.boolean assign false }))
        delay(1.seconds)
        assertEquals(mapOf("false" to 3), groups.value)
        assertEquals(2, f.mock.requests.size)
    }

    @Test fun insertingAndDeletingRefetchesEverySummary() = runTest2 {
        val f = backgroundScope.fixture()
        val groups = f.cache.groupCount(byBoolean, quiet, quiet)
        val sum = f.cache.aggregate(sumOfInt, quiet, quiet)
        val groupSum = f.cache.groupAggregate(sumOfIntByBoolean, quiet, quiet)
        listOf(groups, sum, groupSum).forEach { it.watch() }
        delay(6.seconds)

        val added = f.cache.insert(LargeTestModel(int = 10, boolean = true))
        delay(6.seconds)
        assertEquals(mapOf("true" to 3, "false" to 1), groups.value)
        assertEquals(19.0, sum.value)
        assertEquals(mapOf("true" to 16.0, "false" to 3.0), groupSum.value)

        f.cache.delete(added._id)
        delay(6.seconds)
        assertEquals(mapOf("true" to 2, "false" to 1), groups.value)
        assertEquals(9.0, sum.value)
        assertEquals(mapOf("true" to 6.0, "false" to 3.0), groupSum.value)

        f.cache.bulkDelete(Condition { it.boolean eq true })
        delay(6.seconds)
        assertEquals(mapOf("false" to 1), groups.value)
        assertEquals(3.0, sum.value)
        assertEquals(mapOf("false" to 3.0), groupSum.value)
    }

    @Test fun aLiveAggregateRefetchesWhenTheSocketReportsAChange() = runTest2 {
        val f = backgroundScope.fixture()
        val sum = f.cache.aggregate(sumOfInt, maximumAge = quiet, pullFrequency = Duration.ZERO)
        sum.watch()
        delay(10.seconds)
        assertEquals(9.0, sum.value)

        f.mock.change(CollectionUpdates(updates = setOf(f.data[3].copy(int = 100))))
        delay(1.seconds)
        assertEquals(105.0, sum.value)
    }

    @Test fun anAggregatePollsAtItsPullFrequency() = runTest2 {
        val f = backgroundScope.fixture()
        val sum = f.cache.aggregate(sumOfInt, maximumAge = quiet, pullFrequency = 1.minutes)
        sum.watch()
        delay(1.seconds)
        f.mock.data[f.data[3]._id] = f.data[3].copy(int = 100)
        delay(30.seconds)
        assertEquals(9.0, sum.value)
        delay(1.minutes)
        assertEquals(105.0, sum.value)
    }

    @Test fun aChangeWhileAnAggregateIsInFlightIsNotLost() = runTest2 {
        val f = backgroundScope.fixture()
        val sum = f.cache.aggregate(sumOfInt, quiet, quiet)
        sum.watch()
        delay(50.milliseconds)
        val moved = f.data[3].copy(int = 100)
        f.mock.data[moved._id] = moved
        f.cache.localUpsert(moved)
        delay(10.seconds)
        assertEquals(105.0, sum.value)
        assertEquals(2, f.mock.requests.size, "the first answer missed the change, so it is asked again")
    }
}
