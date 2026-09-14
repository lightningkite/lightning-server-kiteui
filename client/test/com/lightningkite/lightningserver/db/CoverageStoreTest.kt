package com.lightningkite.lightningserver.db

import com.lightningkite.services.database.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * [CoverageStore] on its own terms.
 *
 * These exist because the store is where the one invariant that must never break lives - *a list
 * this cache calls complete really is complete* - and testing it through [ModelCache] means testing
 * it through coroutines, polling loops and a mock server, none of which are the thing under test.
 * The store takes its timestamps as parameters and touches nothing else, so all of this is
 * straight-line code.
 *
 * The last section is the important one: rather than asserting hand-picked expected lists, it drives
 * random operation sequences and checks every answer against a server that has been told about the
 * same changes.  That property is what makes it safe to loosen [CoverageStore.known]'s claim lookup
 * later, because it is the *complete* statement of what the store promises.
 */
class CoverageStoreTest {
    private val serializer = LargeTestModel.serializer()

    private fun store() = CoverageStore<LargeTestModel, Uuid>(serializer)

    /** Sorts always reach the store already total; [ModelCache.list] guarantees it. */
    private val byInt: List<SortPart<LargeTestModel>> =
        sort<LargeTestModel> { it.int.ascending() }.ensureTotal(serializer)
    private val byIntDesc: List<SortPart<LargeTestModel>> =
        sort<LargeTestModel> { it.int.descending() }.ensureTotal(serializer)
    private val byShortThenInt: List<SortPart<LargeTestModel>> =
        sort<LargeTestModel> { it.short.ascending(); it.int.ascending() }.ensureTotal(serializer)

    /** Reads a field [byInt] does not, so neither sort's fields contain the other's. */
    private val byShort: List<SortPart<LargeTestModel>> =
        sort<LargeTestModel> { it.short.ascending() }.ensureTotal(serializer)

    private fun t(seconds: Long): Instant = Instant.fromEpochSeconds(seconds)

    /** Rows with distinct `int`, so the sort is unambiguous and assertions can read as numbers. */
    private fun rows(vararg ints: Int): List<LargeTestModel> = ints.map { LargeTestModel(int = it) }

    private fun List<LargeTestModel>.ints(): List<Int> = map { it.int }

    private fun query(
        condition: Condition<LargeTestModel> = Condition.Always,
        orderBy: List<SortPart<LargeTestModel>> = byInt,
        limit: Int = 100,
    ) = Query(condition, orderBy, limit = limit)

    // =========================================================================
    // Claims made from a query's answer
    // =========================================================================

    /** Nothing has been asked, so nothing can be answered - the caller has to fetch. */
    @Test fun anUnaskedQueryIsNotKnown() {
        assertNull(store().known(query()))
    }

    /**
     * The inference the whole design rests on: an answer shorter than the limit ran off the end of
     * the results, so the condition is known completely.
     */
    @Test fun anAnswerShorterThanItsLimitIsCompleteKnowledge() {
        val store = store()
        val data = rows(1, 2, 3)
        val q = query(limit = 10)
        store.queried(q, data, t(1))

        val answer = assertNotNull(store.known(q))
        assertEquals(listOf(1, 2, 3), answer.items.ints())
        assertEquals(t(1), answer.at)
        assertFalse(answer.partial, "a short answer has seen the end")
        assertNull(store.boundary(q), "complete knowledge has no boundary")
    }

    /** An answer that filled its limit knows only as far as its last row. */
    @Test fun anAnswerThatFilledItsLimitKnowsOnlyAsFarAsItsLastRow() {
        val store = store()
        val data = rows(1, 2, 3)
        val q = query(limit = 3)
        store.queried(q, data, t(1))

        val answer = assertNotNull(store.known(q))
        assertEquals(listOf(1, 2, 3), answer.items.ints())
        assertFalse(answer.partial, "it has everything it was asked for")
        assertEquals(data[2], store.boundary(q), "but only through its last row")
    }

    /** An empty answer is still an answer: nothing matches, and we know it. */
    @Test fun anEmptyAnswerIsCompleteKnowledgeOfNothing() {
        val store = store()
        val q = query(condition { it.int gt 100 }, limit = 10)
        store.queried(q, listOf(), t(1))

        val answer = assertNotNull(store.known(q))
        assertTrue(answer.items.isEmpty())
        assertFalse(answer.partial)
    }

    /** Limit is not part of a claim's identity, so a smaller read of the same extent is answerable. */
    @Test fun aSmallerLimitIsAnsweredFromTheSameClaim() {
        val store = store()
        store.queried(query(limit = 10), rows(1, 2, 3, 4, 5), t(1))

        val answer = assertNotNull(store.known(query(limit = 2)))
        assertEquals(listOf(1, 2), answer.items.ints())
        assertFalse(answer.partial)
    }

    /** Raising the limit past what a bounded claim covers is exactly what [Answer.partial] reports. */
    @Test fun aLimitBeyondABoundedClaimIsPartial() {
        val store = store()
        store.queried(query(limit = 3), rows(1, 2, 3), t(1))

        val answer = assertNotNull(store.known(query(limit = 10)))
        assertEquals(listOf(1, 2, 3), answer.items.ints())
        assertTrue(answer.partial, "knowledge runs out before the limit does")
    }

    /** Raising the limit past a *complete* claim asks for nothing, because there is nothing more. */
    @Test fun aLimitBeyondACompleteClaimIsNotPartial() {
        val store = store()
        store.queried(query(limit = 10), rows(1, 2, 3), t(1))

        val answer = assertNotNull(store.known(query(limit = 50)))
        assertEquals(listOf(1, 2, 3), answer.items.ints())
        assertFalse(answer.partial, "the end of the results was already proved")
    }

    /** A held row past the claim's boundary must not be spliced into the answer. */
    @Test fun aRowPastTheBoundaryIsNotServedFromWithinIt() {
        val store = store()
        store.queried(query(limit = 3), rows(1, 2, 3), t(1))
        // Learned some other way - an ID lookup, say - so it is held but not covered.
        store.mutated(rows(99), t(2))

        val answer = assertNotNull(store.known(query(limit = 3)))
        assertEquals(listOf(1, 2, 3), answer.items.ints(), "99 is past what this claim covers")
    }

    /** A row that arrives *inside* a covered range does belong in the answer. */
    @Test fun aRowInsideTheBoundaryIsServedFromWithinIt() {
        val store = store()
        store.queried(query(limit = 3), rows(10, 20, 30), t(1))
        store.mutated(rows(15), t(2))

        val answer = assertNotNull(store.known(query(limit = 3)))
        assertEquals(listOf(10, 15, 20), answer.items.ints())
        assertFalse(answer.partial, "still a full three rows")
    }

    /**
     * Asserts [refused] is not answered - having first proved, via [control], that the claim it
     * might have been answered from actually exists.
     *
     * Without that control a test of the form "this is refused" also passes when the setup quietly
     * made no claim at all, which is how a test comes to pass for a reason that has nothing to do
     * with what it is named after.
     */
    private fun CoverageStore<LargeTestModel, Uuid>.assertRefuses(
        refused: Query<LargeTestModel>,
        control: Query<LargeTestModel>,
        message: String? = null,
    ) {
        assertNotNull(known(control), "the claim under test was never made, so this test proves nothing")
        assertNull(known(refused), message)
    }

    // =========================================================================
    // Extent identity: what does and does not share a claim
    // =========================================================================

    /**
     * A *bounded* claim is about a stretch of one particular order, and that stretch names an
     * entirely different set of rows in any other order.  Nothing rescues this.
     */
    @Test fun aBoundedClaimNeverAnswersAnotherOrder() {
        val store = store()
        store.queried(query(orderBy = byInt, limit = 3), rows(1, 2, 3), t(1))

        val control = query(orderBy = byInt, limit = 3)
        store.assertRefuses(query(orderBy = byIntDesc, limit = 3), control)
        store.assertRefuses(query(orderBy = byShortThenInt, limit = 3), control)
    }

    /**
     * A complete claim has no stretch to reinterpret - it holds every match - so the sort only still
     * matters for the read mask it imposes.  A sort reading fewer fields picks up fewer mask terms,
     * so its claim is the broader one and can be re-sorted into any sort that reads at least those
     * fields.  See [CoverageStore].
     */
    @Test fun aCompleteClaimCanBeResortedIntoASortReadingAtLeastItsFields() {
        val store = store()
        store.queried(query(orderBy = byInt, limit = 10), rows(1, 2, 3), t(1))

        // Same fields, other direction.
        assertEquals(listOf(3, 2, 1), assertNotNull(store.known(query(orderBy = byIntDesc, limit = 10))).items.ints())
        // A superset of those fields.
        assertEquals(
            listOf(1, 2, 3),
            assertNotNull(store.known(query(orderBy = byShortThenInt, limit = 10))).items.ints(),
        )
    }

    /**
     * But not into a sort reading fields it never touched: that sort's mask could exclude rows this
     * claim happily holds, and the client cannot see the masks to tell.
     */
    @Test fun aCompleteClaimIsNotResortedIntoASortOverUnrelatedFields() {
        val store = store()
        store.queried(query(orderBy = byInt, limit = 10), rows(1, 2, 3), t(1))

        store.assertRefuses(query(orderBy = byShort, limit = 10), control = query(orderBy = byInt, limit = 10))
    }

    /** A claim says nothing about a condition it does not contain. */
    @Test fun aClaimForOneConditionDoesNotAnswerABroaderOne() {
        val store = store()
        store.queried(query(condition { it.int gt 1 }, limit = 10), rows(2, 3), t(1))

        store.assertRefuses(
            query(Condition.Always, limit = 10),
            control = query(condition { it.int gt 1 }, limit = 10),
            message = "rows at or below 1 were never in evidence",
        )
    }

    /** Nor about one that merely overlaps it. */
    @Test fun aClaimForOneConditionDoesNotAnswerAnOverlappingOne() {
        val store = store()
        store.queried(query(condition { it.int gt 1 }, limit = 10), rows(2, 3), t(1))

        val control = query(condition { it.int gt 1 }, limit = 10)
        store.assertRefuses(query(condition { it.int gt 0 }, limit = 10), control)
        store.assertRefuses(query(condition { it.short gt 0 }, limit = 10), control)
    }

    /** Descending order works the same way; the boundary is just the other end. */
    @Test fun aDescendingClaimIsBoundedByItsLastRow() {
        val store = store()
        val data = rows(30, 20, 10)
        val q = query(orderBy = byIntDesc, limit = 3)
        store.queried(q, data, t(1))

        val answer = assertNotNull(store.known(q))
        assertEquals(listOf(30, 20, 10), answer.items.ints())
        // A row below the boundary is outside what this claim covers.
        store.mutated(rows(5), t(2))
        assertEquals(listOf(30, 20, 10), assertNotNull(store.known(q)).items.ints())
        // One inside it is not.
        store.mutated(rows(25), t(3))
        assertEquals(listOf(30, 25, 20), assertNotNull(store.known(q)).items.ints())
    }

    // =========================================================================
    // Claims earned by a broader condition
    // =========================================================================

    /**
     * The point of the whole thing: knowing every match of a condition means knowing every match of
     * anything stricter, so a narrower read is answered without a request.
     */
    @Test fun aCompleteClaimAnswersAStricterQuery() {
        val store = store()
        store.queried(query(Condition.Always, limit = 10), rows(1, 2, 3, 6, 7), t(1))

        val answer = assertNotNull(store.known(query(condition { it.int gt 5 }, limit = 10)))
        assertEquals(listOf(6, 7), answer.items.ints())
        assertFalse(answer.partial, "knowing all of them is knowing all of the ones that match")
        assertEquals(t(1), answer.at, "and it is exactly as old as the claim it came from")
    }

    /** The same for a bounded claim, as far as its boundary reaches. */
    @Test fun aBoundedClaimAnswersAStricterQueryWithinItsReach() {
        val store = store()
        // Knows every row through int = 8, and no further.
        store.queried(query(Condition.Always, limit = 4), rows(1, 6, 7, 8), t(1))
        val narrow = condition<LargeTestModel> { it.int gt 5 }

        // Two rows fit inside the boundary, so a limit of two is fully answered.
        val answered = assertNotNull(store.known(query(narrow, limit = 2)))
        assertEquals(listOf(6, 7), answered.items.ints())
        assertFalse(answered.partial)

        // A limit of four cannot be, because the boundary runs out first.
        assertTrue(assertNotNull(store.known(query(narrow, limit = 4))).partial)
    }

    /** Only within its reach: rows past the boundary are not evidence for the narrower query. */
    @Test fun aBoundedClaimDoesNotAnswerAStricterQueryPastItsBoundary() {
        val store = store()
        store.queried(query(Condition.Always, limit = 2), rows(1, 2), t(1))
        // Held, but from beyond what the claim covers.
        store.mutated(rows(9), t(2))

        val answer = assertNotNull(store.known(query(condition { it.int gt 5 }, limit = 3)))
        assertTrue(answer.items.isEmpty(), "row 9 is past the boundary, so it is not in evidence here")
        assertTrue(answer.partial, "which means this has to be fetched")
    }

    /** A refinement of an existing filter is the everyday shape of this. */
    @Test fun addingAFilterToAnExistingListDoesNotRefetch() {
        val store = store()
        val broad = condition<LargeTestModel> { it.int gt 5 }
        store.queried(query(broad, limit = 10), rows(6, 7, 8), t(1))

        val refined = Condition.And(listOf(broad, condition<LargeTestModel> { it.int lt 8 }))
        val answer = assertNotNull(store.known(query(refined, limit = 10)))
        assertEquals(listOf(6, 7), answer.items.ints())
        assertFalse(answer.partial)
    }

    /** Implication is structural, so equivalent spellings answer each other in both directions. */
    @Test fun equivalentSpellingsOfAConditionShareAClaim() {
        val store = store()
        val a = condition<LargeTestModel> { it.int gt 5 }
        val b = condition<LargeTestModel> { it.int lt 9 }
        store.queried(query(Condition.And(listOf(a, b)), limit = 10), rows(6, 7, 8), t(1))

        assertNotNull(store.known(query(Condition.And(listOf(b, a)), limit = 10)), "order of terms")
        assertNotNull(store.known(query(Condition.And(listOf(a, b, a)), limit = 10)), "a repeated term")
    }

    @Test fun aSingletonAndIsTheSameClaimAsItsTerm() {
        val store = store()
        val a = condition<LargeTestModel> { it.int gt 5 }
        store.queried(query(a, limit = 10), rows(6, 7), t(1))

        assertNotNull(store.known(query(Condition.And(listOf(a)), limit = 10)))
    }

    /** Either branch of an `Or` claim covers a query that lands inside one of them. */
    @Test fun anOrClaimAnswersAQueryInsideOneOfItsBranches() {
        val store = store()
        val low = condition<LargeTestModel> { it.int lt 3 }
        val high = condition<LargeTestModel> { it.int gt 7 }
        store.queried(query(Condition.Or(listOf(low, high)), limit = 10), rows(1, 2, 8, 9), t(1))

        val answer = assertNotNull(store.known(query(high, limit = 10)))
        assertEquals(listOf(8, 9), answer.items.ints())
        assertFalse(answer.partial)
    }

    /** An exact match is preferred, so its own freshness is what gets reported. */
    @Test fun anExactClaimWinsOverABroaderOne() {
        val store = store()
        val data = rows(1, 6, 7)
        val narrow = condition<LargeTestModel> { it.int gt 5 }
        store.queried(query(Condition.Always, limit = 10), data, t(1))
        store.queried(query(narrow, limit = 10), data.drop(1), t(9))

        assertEquals(t(9), assertNotNull(store.known(query(narrow, limit = 10))).at)
    }

    /** Between two claims that both cover it, complete knowledge is worth more than a boundary. */
    @Test fun aCompleteClaimIsPreferredOverABoundedOne() {
        val store = store()
        val data = rows(1, 6, 7, 8)
        val narrow = condition<LargeTestModel> { it.int gt 5 }
        // Complete, and older.
        store.queried(query(Condition.Always, limit = 10), data, t(1))
        // Bounded, and newer - but it reaches less far, so it is worth less.
        store.queried(query(Condition.And(listOf(narrow)), limit = 2), listOf(data[1], data[2]), t(9))

        val answer = assertNotNull(store.known(query(narrow, limit = 10)))
        assertEquals(listOf(6, 7, 8), answer.items.ints())
        assertFalse(answer.partial, "the complete claim answers this outright")
    }

    /** Condition and order subsumption compose: a broader claim, re-sorted. */
    @Test fun aBroaderCompleteClaimAnswersAStricterQueryInAnotherOrder() {
        val store = store()
        store.queried(query(Condition.Always, byInt, limit = 10), rows(1, 6, 7), t(1))

        val answer = assertNotNull(store.known(query(condition { it.int gt 5 }, byIntDesc, limit = 10)))
        assertEquals(listOf(7, 6), answer.items.ints())
        assertFalse(answer.partial)
    }

    /** A broader but *bounded* claim still cannot cross orders. */
    @Test fun aBroaderBoundedClaimDoesNotAnswerAnotherOrder() {
        val store = store()
        store.queried(query(Condition.Always, byInt, limit = 3), rows(1, 6, 7), t(1))

        store.assertRefuses(
            query(condition { it.int gt 5 }, byIntDesc, limit = 10),
            control = query(Condition.Always, byInt, limit = 3),
        )
    }

    /** Paging extends into the narrow extent, inheriting the age of the head it was built on. */
    @Test fun aPageCanExtendAClaimEarnedByABroaderCondition() {
        val store = store()
        val data = rows(6, 7, 8, 9)
        store.queried(query(Condition.Always, limit = 2), data.take(2), t(1))
        val narrow = condition<LargeTestModel> { it.int gt 5 }

        // The broad claim covers the head; the page covers from its boundary on.
        store.paged(query(narrow, limit = 4), after = data[1], result = data.drop(2), pageLimit = 2, at = t(100))

        val answer = assertNotNull(store.known(query(narrow, limit = 4)))
        assertEquals(listOf(6, 7, 8, 9), answer.items.ints())
        assertEquals(t(1), answer.at, "only as fresh as the head it was built on")
    }

    /** A nested range is stricter than the range containing it. */
    @Test fun aNarrowerRangeIsAnsweredByAWiderOne() {
        val store = store()
        store.queried(query(condition { it.int gt 3 }, limit = 10), rows(4, 5, 6, 7), t(1))

        val answer = assertNotNull(store.known(query(condition { it.int gt 5 }, limit = 10)))
        assertEquals(listOf(6, 7), answer.items.ints())
        assertFalse(answer.partial)
    }

    /** Inclusive and exclusive bounds are compared on their actual endpoints, not their spelling. */
    @Test fun boundInclusivityIsRespected() {
        val store = store()
        store.queried(query(condition { it.int gt 5 }, limit = 10), rows(6, 7), t(1))

        // (5, inf) contains [6, inf) but not [5, inf).
        assertNotNull(store.known(query(condition { it.int gte 6 }, limit = 10)))
        assertNull(store.known(query(condition { it.int gte 5 }, limit = 10)))
    }

    /** Bounds on opposite sides say nothing about each other, however the numbers line up. */
    @Test fun anUpperBoundIsNotAnsweredByALowerOne() {
        val store = store()
        store.queried(query(condition { it.int gt 3 }, limit = 10), rows(4, 5), t(1))

        val control = query(condition { it.int gt 3 }, limit = 10)
        store.assertRefuses(query(condition { it.int lt 3 }, limit = 10), control)
        store.assertRefuses(query(condition { it.int lt 9 }, limit = 10), control)
    }

    /** Merely overlapping ranges are not enough; the narrow one has to sit inside. */
    @Test fun anOverlappingRangeIsNotEnough() {
        val store = store()
        store.queried(query(condition { it.int gt 5 }, limit = 10), rows(6, 7), t(1))

        store.assertRefuses(
            query(condition { it.int gt 3 }, limit = 10),
            control = query(condition { it.int gt 5 }, limit = 10),
            message = "3..5 was never covered",
        )
    }

    /**
     * A condition satisfied by a known set of values implies anything all of them satisfy, whatever
     * kind of condition that is - which is one rule covering every leaf at once.
     */
    @Test fun aFixedSetOfValuesImpliesWhateverThoseValuesSatisfy() {
        val store = store()
        store.queried(query(condition { it.int gt 3 }, limit = 10), rows(4, 7, 9), t(1))

        // A single value inside the range.
        assertEquals(listOf(7), assertNotNull(store.known(query(condition { it.int eq 7 }, limit = 10))).items.ints())
        // A set of values, all inside it.
        assertEquals(
            listOf(4, 7),
            assertNotNull(store.known(query(condition { it.int inside setOf(4, 7) }, limit = 10))).items.ints(),
        )
        // One value outside it is enough to disqualify the set.
        assertNull(store.known(query(condition { it.int inside setOf(2, 7) }, limit = 10)))
    }

    /** Excluding more is stricter than excluding less, which is the same rule read backwards. */
    @Test fun excludingMoreIsStricterThanExcludingLess() {
        val store = store()
        val broad = condition<LargeTestModel> { it.int notInside setOf(1, 2) }
        store.queried(query(broad, limit = 10), rows(3, 4), t(1))

        assertNotNull(store.known(query(condition { it.int notInside setOf(1, 2, 3) }, limit = 10)))
        assertNull(store.known(query(condition { it.int notInside setOf(1) }, limit = 10)))
    }

    /** Implication reaches inside a field, which is what makes any of the value rules reachable. */
    @Test fun implicationReachesInsideNestedFields() {
        val store = store()
        store.queried(query(condition { it.embedded.value2 gt 3 }, limit = 10), listOf(), t(1))

        assertNotNull(store.known(query(condition { it.embedded.value2 gt 5 }, limit = 10)))
        assertNull(store.known(query(condition { it.embedded.value1 eq "x" }, limit = 10)))
    }

    // =========================================================================
    // Reads by ID
    // =========================================================================

    /** A row named exactly by any answer settles the one-row query for its ID. */
    @Test fun anyAnswerSettlesTheIdQueriesOfTheRowsItNames() {
        val store = store()
        val data = rows(1, 2, 3)
        store.queried(query(limit = 10), data, t(1))

        val answer = assertNotNull(store.known(store.idQuery(data[1]._id)))
        assertEquals(listOf(data[1]), answer.items)
        assertFalse(answer.partial)
    }

    /** A limited list says nothing about rows past its end, including whether they exist. */
    @Test fun aBoundedListDoesNotSettleIdQueriesBeyondIt() {
        val store = store()
        val data = rows(1, 2, 3)
        store.queried(query(limit = 2), data.take(2), t(1))

        // The control is a row the list *did* return, so a setup that made no claim cannot pass this.
        store.assertRefuses(
            store.idQuery(data[2]._id),
            control = store.idQuery(data[0]._id),
            message = "must be fetched, not reported missing",
        )
    }

    /** A confirmed-missing ID is complete knowledge that the row is not there. */
    @Test fun aMissingIdIsKnownToBeMissing() {
        val store = store()
        val absent = Uuid.random()
        store.identified(listOf(), setOf(absent), t(1))

        val answer = assertNotNull(store.known(store.idQuery(absent)))
        assertTrue(answer.items.isEmpty())
        assertFalse(answer.partial, "'not there' is an answer, not a gap")
    }

    /** A found ID is recorded with its value. */
    @Test fun aFoundIdIsKnownWithItsValue() {
        val store = store()
        val row = rows(7).single()
        store.identified(listOf(row), setOf(), t(1))

        assertEquals(listOf(row), assertNotNull(store.known(store.idQuery(row._id))).items)
    }

    /** Forgetting one ID leaves the rest alone. */
    @Test fun invalidatingOneIdDoesNotDisturbTheOthers() {
        val store = store()
        val data = rows(1, 2)
        store.identified(data, setOf(), t(1))

        store.invalidate(data[0]._id)

        assertNull(store.known(store.idQuery(data[0]._id)))
        assertEquals(listOf(data[1]), assertNotNull(store.known(store.idQuery(data[1]._id))).items)
    }

    // =========================================================================
    // Changes we watched happen
    // =========================================================================

    /** A mutation is a change we saw, so the claim it lands in survives it. */
    @Test fun aMutationDoesNotCostAClaim() {
        val store = store()
        val data = rows(1, 2, 3)
        store.queried(query(limit = 10), data, t(1))

        store.mutated(listOf(data[1].copy(short = 9)), t(2))

        val answer = assertNotNull(store.known(query(limit = 10)))
        assertEquals(listOf(1, 2, 3), answer.items.ints())
        assertEquals<Short>(9, answer.items[1].short)
        assertEquals(t(1), answer.at, "one row's value is not news about the list")
    }

    /** A deletion inside a bounded range leaves the list a row short, and says so. */
    @Test fun aDeletionInsideABoundedRangeMakesItPartial() {
        val store = store()
        val data = rows(1, 2, 3)
        store.queried(query(limit = 3), data, t(1))

        store.deleted(setOf(data[1]._id), t(2))

        val answer = assertNotNull(store.known(query(limit = 3)))
        assertEquals(listOf(1, 3), answer.items.ints())
        assertTrue(answer.partial, "a row moved up to fill this and has to be fetched")
    }

    /** A deletion from a complete claim leaves it complete - there is nothing to pull up. */
    @Test fun aDeletionFromACompleteClaimLeavesItComplete() {
        val store = store()
        val data = rows(1, 2, 3)
        store.queried(query(limit = 10), data, t(1))

        store.deleted(setOf(data[1]._id), t(2))

        val answer = assertNotNull(store.known(query(limit = 10)))
        assertEquals(listOf(1, 3), answer.items.ints())
        assertFalse(answer.partial, "we knew every match, and now there is one fewer")
    }

    /** A deletion is authoritative about the row itself, everywhere. */
    @Test fun aDeletionSettlesTheIdQueryAsMissing() {
        val store = store()
        val data = rows(1, 2)
        store.queried(query(limit = 10), data, t(1))

        store.deleted(setOf(data[0]._id), t(2))

        assertTrue(assertNotNull(store.known(store.idQuery(data[0]._id))).items.isEmpty())
    }

    /** Everything goes, because after this nothing here can be trusted. */
    @Test fun invalidateForgetsEverything() {
        val store = store()
        val data = rows(1, 2, 3)
        store.queried(query(limit = 10), data, t(1))

        store.invalidate()

        assertNull(store.known(query(limit = 10)))
        assertNull(store.known(store.idQuery(data[0]._id)))
        assertTrue(store.cachedItems().isEmpty())
    }

    // =========================================================================
    // Reconciliation - a fresh answer contradicting what we hold
    // =========================================================================

    /**
     * The server just listed every row of this condition in this stretch.  A row we hold that
     * belongs in the stretch but is absent from the answer is a stale copy, and every claim built on
     * it goes too - otherwise those claims keep serving a row the server says is not there.
     */
    @Test fun aRowOmittedFromItsOwnRangeIsForgotten() {
        val store = store()
        val data = rows(1, 2, 3)
        store.queried(query(limit = 10), data, t(1))

        // Deleted behind our back; the next answer simply does not mention it.
        store.queried(query(limit = 10), listOf(data[0], data[2]), t(2))

        assertEquals(listOf(1, 3), assertNotNull(store.known(query(limit = 10))).items.ints())
        assertNull(store.known(store.idQuery(data[1]._id)), "the claim about that row went with it")
        assertFalse(store.cachedItems().contains(data[1]))
    }

    /** A row outside the answered stretch was never in evidence, so it survives. */
    @Test fun aRowBeyondTheAnsweredStretchIsNotForgotten() {
        val store = store()
        val far = rows(99).single()
        store.identified(listOf(far), setOf(), t(1))

        // Covers only as far as row 3; says nothing about row 99.
        store.queried(query(limit = 3), rows(1, 2, 3), t(2))

        assertEquals(listOf(far), assertNotNull(store.known(store.idQuery(far._id))).items)
    }

    /** A row that does not match the answered condition is not in evidence either. */
    @Test fun aRowOutsideTheAnsweredConditionIsNotForgotten() {
        val store = store()
        val low = rows(1).single()
        store.identified(listOf(low), setOf(), t(1))

        store.queried(query(condition { it.int gt 5 }, limit = 10), rows(6, 7), t(2))

        assertEquals(listOf(low), assertNotNull(store.known(store.idQuery(low._id))).items)
    }

    /**
     * An answer was assembled before it was sent, so it is not evidence against a row we heard about
     * while it was in flight.  Without this, an optimistic insert would blink out again the moment an
     * already-running poll returned.
     */
    @Test fun aRowLearnedAfterTheRequestWentOutSurvivesTheAnswer() {
        val store = store()
        val late = rows(2).single()
        // The request goes out at t(1)...
        store.mutated(listOf(late), t(3)) // ...and we hear about this row at t(3)...
        store.queried(query(limit = 10), rows(1, 3), t(1)) // ...before the t(1) answer lands.

        assertEquals(listOf(late), assertNotNull(store.known(store.idQuery(late._id))).items)
        assertTrue(store.cachedItems().contains(late))
    }

    /**
     * The same race, about a row's *value* rather than its existence.  An answer assembled before a
     * change we have since been told about must not write the row back to how it used to look.
     */
    @Test fun anInFlightAnswerDoesNotRevertARowChangedWhileItWasOut() {
        val store = store()
        val before = rows(1).single()
        store.queried(query(limit = 10), listOf(before), t(1))

        // The request goes out at t(2); the row changes at t(4); the t(2) answer lands afterwards.
        val after = before.copy(short = 9)
        store.mutated(listOf(after), t(4))
        store.queried(query(limit = 10), listOf(before), t(2))

        assertEquals<Short>(9, assertNotNull(store.known(query(limit = 10))).items.single().short)
    }

    /** Reconciliation drops the claims that relied on the stale row, not merely the row. */
    @Test fun forgettingARowDropsEveryClaimThatReliedOnIt() {
        val store = store()
        val data = rows(1, 2, 3)
        val broad = query(Condition.Always, limit = 10)
        val narrow = query(condition { it.int lt 3 }, limit = 10)
        store.queried(broad, data, t(1))
        store.queried(narrow, listOf(data[0], data[1]), t(1))

        // A fresh narrow answer no longer mentions row 2.
        store.queried(narrow, listOf(data[0]), t(2))

        assertNull(store.known(broad), "the broad claim contained the row that just vanished")
        assertEquals(listOf(1), assertNotNull(store.known(narrow)).items.ints())
    }

    // =========================================================================
    // Socket deltas
    // =========================================================================

    private val alwaysLive: (Condition<LargeTestModel>, Instant) -> Boolean = { _, _ -> true }
    private val neverLive: (Condition<LargeTestModel>, Instant) -> Boolean = { _, _ -> false }

    /** An ordinary socket change is a change we watched, so it threatens no claim. */
    @Test fun aSocketChangeDoesNotCostAClaim() {
        val store = store()
        val data = rows(1, 2, 3)
        store.queried(query(limit = 10), data, t(1))

        store.socketDelta(setOf(data[1].copy(short = 4)), setOf(), t(2), alwaysLive)

        val answer = assertNotNull(store.known(query(limit = 10)))
        assertEquals(listOf(1, 2, 3), answer.items.ints())
        assertEquals<Short>(4, answer.items[1].short)
    }

    /** For a subscription the socket is actively serving, a removal is the whole truth. */
    @Test fun aSocketRemovalIsAuthoritativeForALiveClaim() {
        val store = store()
        val data = rows(1, 2, 3)
        store.queried(query(limit = 10), data, t(1))

        store.socketDelta(setOf(), setOf(data[1]._id), t(2), alwaysLive)

        val answer = assertNotNull(store.known(query(limit = 10)))
        assertEquals(listOf(1, 3), answer.items.ints(), "the row left the condition we subscribed to")
    }

    /**
     * The failure this must never have.  A removal means "this row's new value no longer matches
     * what you subscribed to" - for a claim the socket is not keeping alive, that is not evidence the
     * row is gone, so the claim is dropped rather than served without it.
     */
    @Test fun aSocketRemovalDropsClaimsItCannotSpeakFor() {
        val store = store()
        val data = rows(1, 2, 3)
        store.queried(query(limit = 10), data, t(1))

        store.socketDelta(setOf(), setOf(data[1]._id), t(2), neverLive)

        assertNull(store.known(query(limit = 10)), "must refetch rather than serve a list short a row")
    }

    /** And it never leaves behind a claim that the row does not exist. */
    @Test fun aSocketRemovalIsNeverReadAsADeletion() {
        val store = store()
        val data = rows(1, 2, 3)
        store.queried(query(limit = 10), data, t(1))

        store.socketDelta(setOf(), setOf(data[1]._id), t(2), alwaysLive)

        assertNull(
            store.known(store.idQuery(data[1]._id)),
            "leaving a filter is not being deleted; this has to be fetched to find out",
        )
    }

    /** A removal of a row we never held is simply nothing to do. */
    @Test fun aSocketRemovalOfAnUnknownRowChangesNothing() {
        val store = store()
        val data = rows(1, 2, 3)
        store.queried(query(limit = 10), data, t(1))

        store.socketDelta(setOf(), setOf(Uuid.random()), t(2), neverLive)

        assertEquals(listOf(1, 2, 3), assertNotNull(store.known(query(limit = 10))).items.ints())
    }

    // =========================================================================
    // Pagination
    // =========================================================================

    /** A page extends the claim's reach rather than replacing it. */
    @Test fun aPageExtendsTheBoundary() {
        val store = store()
        val data = rows(1, 2, 3, 4, 5, 6)
        val q = query(limit = 3)
        store.queried(q, data.take(3), t(1))

        val grown = query(limit = 6)
        store.paged(grown, after = data[2], result = data.drop(3), pageLimit = 3, at = t(2))

        val answer = assertNotNull(store.known(grown))
        assertEquals(listOf(1, 2, 3, 4, 5, 6), answer.items.ints())
        assertFalse(answer.partial)
        assertEquals(data[5], store.boundary(grown))
    }

    /**
     * The head of the list was not re-read, so the extended claim is only as fresh as its oldest
     * part.  Stamping it now would let a reader page forever while the top quietly went stale.
     */
    @Test fun aPagedClaimKeepsTheAgeOfItsOldestSegment() {
        val store = store()
        val data = rows(1, 2, 3, 4, 5, 6)
        store.queried(query(limit = 3), data.take(3), t(1))
        store.paged(query(limit = 6), after = data[2], result = data.drop(3), pageLimit = 3, at = t(100))

        assertEquals(t(1), assertNotNull(store.known(query(limit = 6))).at)
    }

    /** A page shorter than it asked for ran off the end, which ends the claim. */
    @Test fun aShortPageCompletesTheClaim() {
        val store = store()
        val data = rows(1, 2, 3, 4)
        store.queried(query(limit = 3), data.take(3), t(1))
        store.paged(query(limit = 6), after = data[2], result = data.drop(3), pageLimit = 3, at = t(2))

        val answer = assertNotNull(store.known(query(limit = 6)))
        assertEquals(listOf(1, 2, 3, 4), answer.items.ints())
        assertFalse(answer.partial)
        assertNull(store.boundary(query(limit = 6)), "the end of the results was reached")
    }

    /** A page reconciles too: a row omitted from the stretch it covers is a stale copy. */
    @Test fun aPageForgetsARowItOmitsFromItsOwnStretch() {
        val store = store()
        val data = rows(1, 2, 3, 4, 5, 6)
        store.queried(query(limit = 3), data.take(3), t(1))
        store.identified(listOf(data[4]), setOf(), t(1)) // row 5, held but not covered

        // The page covers rows after 3, through 6, and does not mention 5.
        store.paged(query(limit = 6), after = data[2], result = listOf(data[3], data[5]), pageLimit = 3, at = t(2))

        assertFalse(store.cachedItems().contains(data[4]), "the page proved that row is not there")
    }

    // =========================================================================
    // Inputs the store cannot make a claim from
    // =========================================================================

    /** Every claim here is "shorter than the limit means the end", which a limit of zero breaks. */
    @Test fun nonPositiveLimitsAreRejected() {
        val store = store()
        assertFails { store.known(query(limit = 0)) }
        assertFails { store.known(query(limit = -1)) }
        assertFails { store.queried(query(limit = 0), listOf(), t(1)) }
        assertFails {
            store.paged(query(limit = 3), after = rows(1).single(), result = listOf(), pageLimit = 0, at = t(1))
        }
    }

    /**
     * A skipped query says nothing about the rows before it, so it cannot support the only claim
     * this store knows how to make.  [ModelCache] rejects these at the door; the store is public and
     * has to reject them too rather than silently file them under the unskipped extent.
     */
    @Test fun skipIsRejected() {
        val store = store()
        val skipped = Query(Condition.Always, byInt, skip = 2, limit = 3)
        assertFails { store.known(skipped) }
        assertFails { store.queried(skipped, rows(3, 4, 5), t(1)) }
    }

    /**
     * A page claims coverage from the start of the order, which is only true if something already
     * covered the head.  Paging into an extent nothing has read would assert knowledge of rows that
     * were never fetched.
     */
    @Test fun pagingWithoutAClaimToExtendIsRejected() {
        val store = store()
        assertFails {
            store.paged(query(limit = 6), after = rows(3).single(), result = rows(4, 5, 6), pageLimit = 3, at = t(1))
        }
    }

    // =========================================================================
    // Knowing what the server actually filtered by
    // =========================================================================

    /**
     * A mask over `int`, so any sort touching `int` carries the extra condition the server imposes
     * and any sort that does not, does not.  That asymmetry is the whole point of tracking it.
     */
    private val maskOnInt = Mask<LargeTestModel>(
        listOf(condition<LargeTestModel> { it.boolean eq true } to modification<LargeTestModel> { it.int assign 0 })
    )

    /** With no mask at all, the sort imposes nothing, so a complete claim re-sorts into any order. */
    @Test fun anEmptyMaskLetsACompleteClaimResortFreely() {
        val store = store()
        store.queried(query(orderBy = byInt, limit = 10), rows(1, 2, 3), t(1))
        store.useMasking(Mask(listOf()))

        // Unrelated fields, which the conservative fallback refused.  Compared as a set because
        // these rows share a `short`, so their order under this sort is settled by `_id`.
        val answer = assertNotNull(store.known(query(orderBy = byShort, limit = 10)))
        assertEquals(setOf(1, 2, 3), answer.items.ints().toSet())
        assertFalse(answer.partial)
    }

    /** Learning of a real mask discards claims filed while we assumed there was none. */
    @Test fun learningOfAMaskDiscardsWhatWasAssumedWithoutIt() {
        val store = store()
        store.queried(query(orderBy = byInt, limit = 10), rows(1, 2, 3), t(1))
        assertNotNull(store.known(query(orderBy = byInt, limit = 10)), "it was answerable before")

        store.useMasking(maskOnInt)

        assertNull(store.known(query(orderBy = byInt, limit = 10)))
    }

    /** An empty mask changes nothing, so nothing is thrown away for it. */
    @Test fun learningThereIsNoMaskDiscardsNothing() {
        val store = store()
        store.queried(query(orderBy = byInt, limit = 10), rows(1, 2, 3), t(1))

        store.useMasking(Mask(listOf()))

        assertEquals(listOf(1, 2, 3), assertNotNull(store.known(query(orderBy = byInt, limit = 10))).items.ints())
    }

    /**
     * A sort over an unmasked field is evidence about the plain condition; a sort over a masked one
     * is evidence only about that condition *and* the mask's term.  So one answers the other and not
     * the reverse - which no amount of guessing at field names could have worked out.
     */
    @Test fun anUnmaskedSortAnswersAMaskedOneButNotTheReverse() {
        val store = store()
        store.useMasking(maskOnInt)
        val unmaskedSort = query(orderBy = byShort, limit = 10)
        val maskedSort = query(orderBy = byInt, limit = 10)

        store.queried(unmaskedSort, rows(1, 2, 3), t(1))
        assertNotNull(store.known(maskedSort), "the stricter, masked query is covered by the broader one")

        store.invalidate()
        store.queried(maskedSort, rows(1, 2, 3), t(1))
        store.assertRefuses(unmaskedSort, control = maskedSort, message = "the masked list may be missing rows this one wants")
    }

    /** The mask's terms filter the answer too, not just decide which claims apply. */
    @Test fun aMaskedSortOnlyServesRowsTheMaskAdmits() {
        val store = store()
        store.useMasking(maskOnInt)
        val hidden = LargeTestModel(int = 2, boolean = false)
        val shown = LargeTestModel(int = 3, boolean = true)
        store.queried(query(orderBy = byShort, limit = 10), listOf(hidden, shown), t(1))

        // Sorting by `int` imposes `boolean == true`, so the row failing it is not in that answer.
        assertEquals(listOf(shown), assertNotNull(store.known(query(orderBy = byInt, limit = 10))).items)
    }

    // =========================================================================
    // Totality the server states outright
    // =========================================================================

    /**
     * The length inference cannot tell a query that exactly filled its limit from one that ran out
     * on the last row.  A server that says so is believed.
     */
    @Test fun aServerDeclaredEndCompletesAClaimThatFilledItsLimit() {
        val store = store()
        store.queried(query(limit = 3), rows(1, 2, 3), t(1), reachedEnd = true)

        assertNull(store.boundary(query(limit = 3)), "the server said there is no more")
        assertFalse(assertNotNull(store.known(query(limit = 10))).partial, "so a bigger limit asks for nothing")
    }

    /** And the reverse: a short answer that the server says is not the end stays bounded. */
    @Test fun aServerDeclaredContinuationKeepsAShortAnswerBounded() {
        val store = store()
        val data = rows(1, 2)
        store.queried(query(limit = 10), data, t(1), reachedEnd = false)

        assertEquals(data[1], store.boundary(query(limit = 10)))
        assertTrue(assertNotNull(store.known(query(limit = 10))).partial)
    }

    /** Pagination takes the same word for it. */
    @Test fun aServerDeclaredEndCompletesAPage() {
        val store = store()
        val data = rows(1, 2, 3, 4)
        store.queried(query(limit = 2), data.take(2), t(1))
        store.paged(query(limit = 4), after = data[1], result = data.drop(2), pageLimit = 2, at = t(2), reachedEnd = true)

        assertNull(store.boundary(query(limit = 4)))
        assertEquals(listOf(1, 2, 3, 4), assertNotNull(store.known(query(limit = 10))).items.ints())
    }

    // =========================================================================
    // Telling anyone that something changed
    // =========================================================================

    /**
     * Every reader in [ModelCache] recomputes off this one signal, so anything that changes what
     * [known] would answer and *does not* fire it leaves the UI showing stale data with nothing to
     * notice.  That failure is invisible from the store's own return values, which is why it is
     * worth asserting separately for every entry point.
     */
    @Test fun everyChangeAnnouncesItself() {
        val store = store()
        var fired = 0
        store.updates.addListener { fired++ }

        val data = rows(1, 2, 3)
        val checks = listOf<Pair<String, () -> Unit>>(
            "queried" to { store.queried(query(limit = 10), data, t(1)) },
            "mutated" to { store.mutated(listOf(data[0].copy(short = 1)), t(2)) },
            "identified" to { store.identified(listOf(data[1]), setOf(), t(3)) },
            "socketDelta" to { store.socketDelta(setOf(data[2]), setOf(), t(4)) { _, _ -> true } },
            "paged" to {
                store.queried(query(limit = 3), data, t(5))
                store.paged(query(limit = 6), after = data[2], result = rows(4), pageLimit = 3, at = t(6))
            },
            "useMasking" to { store.useMasking(maskOnInt) },
            "deleted" to { store.deleted(setOf(data[0]._id), t(7)) },
            "invalidate" to { store.invalidate() },
            "invalidate(id)" to { store.invalidate(data[1]._id) },
        )
        for ((name, action) in checks) {
            val before = fired
            action()
            assertTrue(fired > before, "$name changed what is known without saying so")
        }
    }

    // =========================================================================
    // The oracle property
    // =========================================================================

    /**
     * A stand-in for the server: the same filter/sort/limit a `Database` would apply.
     *
     * Its whole job is to be the thing the store's answers are checked against, so it is deliberately
     * the dumbest possible implementation.
     */
    private class Oracle {
        val rows = HashMap<Uuid, LargeTestModel>()
        fun query(q: Query<LargeTestModel>): List<LargeTestModel> = rows.values
            .filter { q.condition(it) }
            .sortedWith(q.orderBy.comparator!!)
            .take(q.limit)
    }

    /**
     * The complete statement of what [CoverageStore] promises:
     *
     * > if every change was reported to the store, then whenever [CoverageStore.known] answers
     * > without [CoverageStore.Answer.partial], its answer is what the server would have returned.
     *
     * Staleness is deliberately out of scope - a claim is "complete as of [CoverageStore.Answer.at]",
     * and deciding whether that is recent enough belongs to [ModelCache].  So this drives changes the
     * store is *told* about, which is the condition under which its guarantee applies.
     *
     * Random sequences rather than hand-picked ones because the interesting states here are reached
     * by accident - a claim bounded at a row that then moves, a reconcile that fires while another
     * extent depends on the row it drops - and those are exactly the ones nobody thinks to write down.
     */
    @Test fun aCompleteAnswerIsAlwaysWhatTheServerWouldReturn() {
        // Deliberately overlapping: nested ranges, ranges that only overlap, a set membership, and
        // three sorts whose field sets are equal, nested and unrelated.  Every one of these pairs is
        // a chance for the implication rules to claim more than they can support.
        val panel = listOf(
            query(Condition.Always, byInt, limit = 3),
            query(Condition.Always, byInt, limit = 10),
            query(Condition.Always, byIntDesc, limit = 4),
            query(condition { it.int gt 5 }, byInt, limit = 3),
            query(condition { it.int gt 3 }, byInt, limit = 6),
            query(condition { it.int gte 5 }, byInt, limit = 4),
            query(condition { it.int lte 5 }, byInt, limit = 5),
            query(condition { it.int lt 9 }, byInt, limit = 6),
            query(condition { it.int eq 7 }, byInt, limit = 2),
            query(condition { it.int inside setOf(2, 4, 6) }, byInt, limit = 4),
            query(condition { it.boolean eq true }, byInt, limit = 4),
            query(Condition.And(listOf(condition { it.int gt 3 }, condition { it.int lt 9 })), byInt, limit = 4),
            query(condition { it.int gt 5 }, byIntDesc, limit = 3),
            query(Condition.Always, byShortThenInt, limit = 5),
            query(condition { it.int gt 3 }, byShortThenInt, limit = 4),
        )

        // Answers that came from a claim some *other* query earned, which is the path this is really
        // here to police.  Counted so a change that quietly stopped reaching it would be caught -
        // separately per capability, since one could regress while the other kept the total up.
        var subsumedCondition = 0
        var subsumedOrder = 0

        // Fixed seeds rather than a fresh Random, so a failure is reproducible.
        for (seed in 1..40) {
            val random = kotlin.random.Random(seed)
            val oracle = Oracle()
            val store = store()
            val ids = ArrayList<Uuid>()
            val asked = HashSet<Query<LargeTestModel>>()
            var clock = 0L

            repeat(60) {
                clock++
                val now = t(clock)
                when (random.nextInt(6)) {
                    // Insert a row, and tell the store about it.
                    0, 1 -> {
                        val row = LargeTestModel(
                            int = random.nextInt(0, 12),
                            short = random.nextInt(0, 3).toShort(),
                            boolean = random.nextBoolean(),
                        )
                        oracle.rows[row._id] = row
                        ids.add(row._id)
                        store.mutated(listOf(row), now)
                    }
                    // Modify one, and tell the store about it.
                    2 -> {
                        val id = ids.randomOrNull(random) ?: return@repeat
                        val existing = oracle.rows[id] ?: return@repeat
                        val row = existing.copy(
                            int = random.nextInt(0, 12),
                            boolean = random.nextBoolean(),
                        )
                        oracle.rows[id] = row
                        store.mutated(listOf(row), now)
                    }
                    // Delete one, and tell the store about it.
                    3 -> {
                        val id = ids.randomOrNull(random) ?: return@repeat
                        oracle.rows.remove(id)
                        store.deleted(setOf(id), now)
                    }
                    // Look one up by ID.
                    4 -> {
                        val id = ids.randomOrNull(random) ?: return@repeat
                        val found = oracle.rows[id]
                        store.identified(listOfNotNull(found), setOfNotNull(id.takeIf { found == null }), now)
                    }
                    // Run one of the panel's queries against the server.
                    5 -> {
                        val q = panel.random(random)
                        asked.add(q)
                        store.queried(q, oracle.query(q), now)
                    }
                }

                for (q in panel) {
                    val answer = store.known(q) ?: continue
                    if (q !in asked) subsumedCondition++
                    // Nothing in this order was ever asked, so this can only have been re-sorted.
                    if (asked.none { it.orderBy == q.orderBy }) subsumedOrder++
                    if (answer.partial) continue
                    assertEquals(
                        oracle.query(q),
                        answer.items,
                        "seed $seed step $it: store called this complete but the server disagrees\nquery: $q",
                    )
                }
            }
        }

        assertTrue(subsumedCondition > 100, "only $subsumedCondition answers came from a broader condition")
        assertTrue(subsumedOrder > 100, "only $subsumedOrder answers were re-sorted from another order")
    }

    /**
     * The same property for reads by ID, which take a different path through [CoverageStore.known]
     * and so would not be covered by the panel above.
     */
    @Test fun aCompleteIdAnswerIsAlwaysWhatTheServerWouldReturn() {
        for (seed in 1..40) {
            val random = kotlin.random.Random(seed)
            val oracle = Oracle()
            val store = store()
            val ids = ArrayList<Uuid>()
            var clock = 0L
            val listQuery = query(Condition.Always, byInt, limit = 4)

            repeat(60) {
                clock++
                val now = t(clock)
                when (random.nextInt(5)) {
                    0, 1 -> {
                        val row = LargeTestModel(int = random.nextInt(0, 8))
                        oracle.rows[row._id] = row
                        ids.add(row._id)
                        store.mutated(listOf(row), now)
                    }
                    2 -> {
                        val id = ids.randomOrNull(random) ?: return@repeat
                        oracle.rows.remove(id)
                        store.deleted(setOf(id), now)
                    }
                    3 -> {
                        val id = ids.randomOrNull(random) ?: return@repeat
                        val found = oracle.rows[id]
                        store.identified(listOfNotNull(found), setOfNotNull(id.takeIf { found == null }), now)
                    }
                    4 -> store.queried(listQuery, oracle.query(listQuery), now)
                }

                for (id in ids) {
                    val answer = store.known(store.idQuery(id)) ?: continue
                    if (answer.partial) continue
                    assertEquals(
                        listOfNotNull(oracle.rows[id]),
                        answer.items,
                        "seed $seed step $it: store claims to know row $id exactly",
                    )
                }
            }
        }
    }
}

private fun <T> List<T>.randomOrNull(random: kotlin.random.Random): T? =
    if (isEmpty()) null else this[random.nextInt(size)]
