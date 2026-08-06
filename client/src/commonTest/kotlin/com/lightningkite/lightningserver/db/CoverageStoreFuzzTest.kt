package com.lightningkite.lightningserver.db

import com.lightningkite.services.database.*
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * [CoverageStore] driven by every operation it has, against a server that is told the same things.
 *
 * `CoverageStoreTest` already fuzzes the everyday mix - insert, modify, delete, look up, query.  What
 * is left over is precisely where the interesting failures live: pagination extending somebody else's
 * claim, a socket removal that is not a deletion, a server that states totality rather than implying
 * it, a read mask that makes two sorts two different questions, and an answer that lands after a
 * change it was assembled before.  Those interact, and the states worth checking are reached by
 * accident rather than by being written down, so they are all in one mixed sequence here.
 *
 * Everything is checked against an [Oracle] built the way `ClientModelRestEndpointsMock` builds its
 * answers, mask and all, so "what the server would have returned" means the same thing on both sides.
 *
 * The guarantee under test only applies when the store was told about every change, so the sequence
 * below never changes anything behind its back: a local change is reported directly, and a remote one
 * is only made to a row the socket subscription actually covers.
 */
class CoverageStoreFuzzTest {
    private val serializer = LargeTestModel.serializer()

    /** Sorts arrive at the store already total; [ModelCache.list] guarantees it. */
    private val byInt: List<SortPart<LargeTestModel>> =
        sort<LargeTestModel> { it.int.ascending() }.ensureTotal(serializer)
    private val byIntDesc: List<SortPart<LargeTestModel>> =
        sort<LargeTestModel> { it.int.descending() }.ensureTotal(serializer)
    private val byShortThenInt: List<SortPart<LargeTestModel>> =
        sort<LargeTestModel> { it.short.ascending(); it.int.ascending() }.ensureTotal(serializer)

    /** Reads a field the others do not, so neither sort's fields contain the other's. */
    private val byShort: List<SortPart<LargeTestModel>> =
        sort<LargeTestModel> { it.short.ascending() }.ensureTotal(serializer)

    private fun query(
        condition: Condition<LargeTestModel> = Condition.Always,
        orderBy: List<SortPart<LargeTestModel>> = byInt,
        limit: Int = 100,
    ) = Query(condition, orderBy, limit = limit)

    /**
     * Deliberately overlapping: nested ranges, ranges that only overlap, a set membership, and four
     * sorts whose field sets are equal, nested and unrelated.  Every pair here is a chance for the
     * implication and re-sorting rules to claim more than they can support.
     */
    private val panel: List<Query<LargeTestModel>> = listOf(
        query(Condition.Always, byInt, limit = 3),
        query(Condition.Always, byInt, limit = 10),
        query(Condition.Always, byIntDesc, limit = 4),
        query(Condition.Always, byShort, limit = 5),
        query(Condition.Always, byShortThenInt, limit = 5),
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
        query(condition { it.int gt 3 }, byShortThenInt, limit = 4),
        query(condition { it.short gte 1 }, byShort, limit = 4),
    )

    /** The condition a socket subscribes to, when it is not simply everything. */
    private val socketHigh: Condition<LargeTestModel> = condition { it.int gt 5 }

    // =========================================================================
    // The server
    // =========================================================================

    /**
     * A stand-in for the server, faithful down to the read mask.
     *
     * A query is not run as asked: the server ANDs in the mask entries covering any field being
     * sorted on and any field being filtered on, and masks the values on the way out.  Getting that
     * wrong here would either hide a real disagreement or invent one, so it is spelled the same way
     * `ClientModelRestEndpointsMock.effectiveCondition` spells it.
     */
    private class Oracle(val mask: Mask<LargeTestModel>) {
        val rows = HashMap<Uuid, LargeTestModel>()

        /** Every row the query would return, in order and masked, before the limit is applied. */
        fun matching(q: Query<LargeTestModel>): List<LargeTestModel> {
            val effective = q.condition and mask.permitSort(q.orderBy) and mask(q.condition)
            return rows.values
                .filter { effective(it) }
                .sortedWith(q.orderBy.comparator!!)
                .map { mask(it) }
        }

        fun query(q: Query<LargeTestModel>): List<LargeTestModel> = matching(q).take(q.limit)

        /** The rows a cursor page would return: the same list, resumed after a row. */
        fun page(q: Query<LargeTestModel>, after: LargeTestModel): List<LargeTestModel> {
            val comparator = q.orderBy.comparator!!
            return matching(q).filter { comparator.compare(it, after) > 0 }
        }

        fun detail(id: Uuid): LargeTestModel? = rows[id]?.let { mask(it) }
    }

    // =========================================================================
    // Instrumentation
    // =========================================================================

    /**
     * Which paths the random mix actually reached.  A fuzz that quietly stops exercising pagination
     * or socket removals still passes, which is worse than not having it, so every path this test
     * exists to cover is counted and the count is asserted.
     */
    private class Counters {
        var complete = 0
        var partial = 0
        var subsumedCondition = 0
        var subsumedOrder = 0
        var boundedAnswers = 0
        var pages = 0
        var pagesThatRanOut = 0
        var socketChanges = 0
        var socketRemovals = 0
        var socketRemovalsKeepingAClaim = 0
        var socketRemovalsDroppingAClaim = 0
        var invalidateAll = 0
        var invalidateOne = 0
        var declaredEnd = 0
        var declaredMore = 0
        var inFlightAnswers = 0
        var inFlightAnswersThatRacedARow = 0
        var maskedSortAnswers = 0
    }

    // =========================================================================
    // One randomised run
    // =========================================================================

    private inner class Run(
        val seed: Int,
        val mask: Mask<LargeTestModel>,
        val socketCondition: Condition<LargeTestModel>,
        val live: (Condition<LargeTestModel>, Instant) -> Boolean,
        val counters: Counters,
    ) {
        val random = Random(seed)
        val oracle = Oracle(mask)
        val store = CoverageStore<LargeTestModel, Uuid>(serializer)
        val ids = ArrayList<Uuid>()
        val asked = HashSet<Query<LargeTestModel>>()

        /** The freshness each query last reported, so a purely local change can be shown not to move it. */
        val lastAt = HashMap<Query<LargeTestModel>, Instant>()

        /** Two ticks per step, so an answer can be stamped strictly between two steps' changes. */
        var clock = 0L

        init {
            store.useMasking(mask)
        }

        fun t(seconds: Long): Instant = Instant.fromEpochSeconds(seconds)

        fun newRow() = LargeTestModel(
            int = random.nextInt(0, 12),
            short = random.nextInt(0, 3).toShort(),
            boolean = random.nextBoolean(),
        )

        fun existing(): LargeTestModel? {
            if (ids.isEmpty()) return null
            return oracle.rows[ids[random.nextInt(ids.size)]]
        }

        fun run(steps: Int) {
            repeat(steps) { step(it) }
        }

        fun step(index: Int) {
            clock += 2
            val now = t(clock)
            // Whether this step only told the store about a change we made, which is not news about
            // any list and so must not move any list's freshness.
            var localOnly = false
            when (random.nextInt(100)) {
                in 0..19 -> { insert(now); localOnly = true }
                in 20..34 -> { modify(now); localOnly = true }
                in 35..44 -> { delete(now); localOnly = true }
                in 45..54 -> { lookUp(now); localOnly = true }
                in 55..73 -> runQuery(now)
                in 74..81 -> runPage(now)
                in 82..91 -> socketStep(now)
                in 92..94 -> invalidateOne(now)
                95 -> { store.invalidate(); counters.invalidateAll++ }
                else -> inFlightQuery(now)
            }
            check(index, now, localOnly)
        }

        fun insert(now: Instant) {
            val row = newRow()
            oracle.rows[row._id] = row
            ids.add(row._id)
            // What comes back from an insert is the server's copy, which is masked like any other.
            store.mutated(listOf(mask(row)), now)
        }

        fun modify(now: Instant) {
            val existing = existing() ?: return
            val row = existing.copy(int = random.nextInt(0, 12), boolean = random.nextBoolean())
            oracle.rows[row._id] = row
            store.mutated(listOf(mask(row)), now)
        }

        fun delete(now: Instant) {
            val existing = existing() ?: return
            oracle.rows.remove(existing._id)
            store.deleted(setOf(existing._id), now)
        }

        fun lookUp(now: Instant) {
            if (ids.isEmpty()) return
            val id = ids[random.nextInt(ids.size)]
            val found = oracle.detail(id)
            store.identified(listOfNotNull(found), setOfNotNull(id.takeIf { found == null }), now)
        }

        /**
         * A query, sometimes answered by a server that caps how many rows it will return at once and
         * says so.  That cap is the only truthful way to reach "shorter than the limit, but not the
         * end": inference alone cannot produce it, and lying about it would break the precondition
         * the whole property rests on.
         */
        fun runQuery(now: Instant) {
            val q = panel[random.nextInt(panel.size)]
            asked.add(q)
            val all = oracle.matching(q)
            val cap = if (random.nextInt(4) == 0) random.nextInt(1, q.limit + 1) else q.limit
            val result = all.take(cap)
            val ended = all.size <= cap
            // A capped answer has to be declared, since the length inference would read it as the end.
            val declared = if (cap < q.limit || random.nextBoolean()) ended else null
            if (declared == true && result.size == q.limit) counters.declaredEnd++
            if (declared == false && result.size < q.limit) counters.declaredMore++
            store.queried(q, result, now, reachedEnd = declared)
        }

        /** Extends a claim that has a boundary to page from - the store rejects anything else. */
        fun runPage(now: Instant) {
            val candidates = panel.filter { store.boundary(it) != null }
            if (candidates.isEmpty()) return
            val q = candidates[random.nextInt(candidates.size)]
            val after = store.boundary(q)!!
            val pageLimit = random.nextInt(1, 4)
            val rest = oracle.page(q, after)
            val result = rest.take(pageLimit)
            val ended = rest.size <= pageLimit
            val declared = if (random.nextBoolean()) ended else null
            counters.pages++
            if (ended) counters.pagesThatRanOut++
            store.paged(q, after, result, pageLimit, now, reachedEnd = declared)
        }

        /**
         * A change made on the server and learned only from the socket.
         *
         * Confined to rows the subscription covers and to values that keep them there, because
         * anything else would reach the store by no route at all - and the guarantee says nothing
         * about a store that was not told.  That leaves deletion as the one thing the socket reports
         * as a removal here, which is the case that must never be read as one row's disappearance
         * from everything.
         */
        fun socketStep(now: Instant) {
            val existing = existing() ?: return
            if (!socketCondition(existing)) return
            if (random.nextInt(4) == 0) {
                oracle.rows.remove(existing._id)
                counters.socketRemovals++
                countRemovalEffect(existing)
                store.socketDelta(setOf(), setOf(existing._id), now, live)
                return
            }
            val row = generateSequence { existing.copy(int = random.nextInt(0, 12), boolean = random.nextBoolean()) }
                .take(8).firstOrNull { socketCondition(it) } ?: return
            oracle.rows[row._id] = row
            counters.socketChanges++
            store.socketDelta(setOf(mask(row)), setOf(), now, live)
        }

        /** Which claims holding the removed row the socket could and could not speak for. */
        fun countRemovalEffect(old: LargeTestModel) {
            if (store.cachedItems().none { it._id == old._id }) return
            for (q in panel) {
                if (store.known(q) == null || !q.condition(old)) continue
                if (live(q.condition, t(0))) counters.socketRemovalsKeepingAClaim++
                else counters.socketRemovalsDroppingAClaim++
            }
        }

        fun invalidateOne(now: Instant) {
            if (ids.isEmpty()) return
            counters.invalidateOne++
            store.invalidate(ids[random.nextInt(ids.size)])
        }

        /**
         * An answer assembled before a change that has already been applied - the race a request-time
         * timestamp exists to bound.  The answer is stamped between the previous step and the change
         * it lost to, which is exactly where a real in-flight request sits.
         */
        fun inFlightQuery(now: Instant) {
            val q = panel[random.nextInt(panel.size)]
            asked.add(q)
            val all = oracle.matching(q)
            val result = all.take(q.limit)
            val ended = all.size <= q.limit

            // The change lands while the answer is out.
            val before = result.map { it._id }.toSet()
            when (random.nextInt(3)) {
                0 -> insert(now)
                1 -> modify(now)
                else -> delete(now)
            }
            counters.inFlightAnswers++
            if (before.any { oracle.rows[it] !in result }) counters.inFlightAnswersThatRacedARow++

            store.queried(q, result, t(clock - 1), reachedEnd = if (random.nextBoolean()) ended else null)
        }

        /** Whether this query's sort makes the server impose mask terms, so it asks a stricter question. */
        fun maskedSort(q: Query<LargeTestModel>): Boolean = mask.permitSort(q.orderBy) !is Condition.Always

        fun check(index: Int, now: Instant, localOnly: Boolean) {
            for (q in panel) {
                val answer = store.known(q)
                if (answer == null) {
                    lastAt.remove(q)
                    continue
                }
                val where = "seed $seed step $index\nquery: $q"
                assertTrue(answer.items.size <= q.limit, "$where\nmore rows than the limit asked for")
                val comparator = q.orderBy.comparator!!
                for (i in 1 until answer.items.size) {
                    assertTrue(
                        comparator.compare(answer.items[i - 1], answer.items[i]) < 0,
                        "$where\nrows out of order, or the same row twice",
                    )
                }
                assertTrue(answer.at <= now, "$where\nknowledge dated in the future")
                if (localOnly) lastAt[q]?.let {
                    assertEquals(it, answer.at, "$where\na change we made made the list look freshly retrieved")
                }
                if (answer.partial) {
                    counters.partial++
                    assertNotNull(store.boundary(q), "$where\npartial, but nothing says where knowledge stops")
                } else {
                    counters.complete++
                    if (q !in asked) counters.subsumedCondition++
                    if (asked.none { it.orderBy == q.orderBy }) counters.subsumedOrder++
                    if (store.boundary(q) != null) counters.boundedAnswers++
                    if (maskedSort(q)) counters.maskedSortAnswers++
                    assertTrue(
                        answer.items.size == q.limit || answer.items.size == oracle.matching(q).size,
                        "$where\ncomplete, yet neither a full page nor everything there is",
                    )
                    assertEquals(
                        oracle.query(q),
                        answer.items,
                        "$where\nthe store called this complete, but the server disagrees",
                    )
                }
                lastAt[q] = answer.at
            }
            // Reads by ID take the other path through `known`, so they are checked too.
            repeat(minOf(5, ids.size)) {
                val id = ids[random.nextInt(ids.size)]
                val answer = store.known(store.idQuery(id)) ?: return@repeat
                assertTrue(answer.items.size <= 1, "seed $seed step $index: more than one row for one ID")
                if (!answer.partial) {
                    assertEquals(
                        listOfNotNull(oracle.detail(id)),
                        answer.items,
                        "seed $seed step $index: the store claims to know row $id exactly",
                    )
                }
            }
        }
    }

    // =========================================================================
    // The properties
    // =========================================================================

    /**
     * The whole promise, under every operation at once:
     *
     * > if every change was reported to the store, then whenever [CoverageStore.known] answers without
     * > [CoverageStore.Answer.partial], its answer is what the server would have returned.
     *
     * Staleness is deliberately out of scope - a claim is "complete as of [CoverageStore.Answer.at]",
     * and judging whether that is recent enough is [ModelCache]'s job - so the sequence only makes
     * changes the store is told about.
     *
     * The mask is empty rather than absent, which is a claim in its own right: with the masks known
     * to impose nothing, a complete claim may be re-sorted into any order at all, including one over
     * fields it never read.
     */
    @Test fun everyOperationTogetherKeepsCompleteAnswersHonest() {
        val counters = Counters()
        val noMask = Mask<LargeTestModel>(listOf())
        // Fixed seeds rather than a fresh Random, so a failure is reproducible.  The socket varies by
        // seed across the three answers `live` can give, since each drives a different branch.
        for (seed in 1..24) {
            val (socket, live) = when (seed % 3) {
                // Subscribed to everything, and honestly live: a removal can only be a deletion.
                0 -> Condition.Always to { _: Condition<LargeTestModel>, _: Instant -> true }
                // Subscribed to a slice, so a removal is a row leaving it and only that slice's own
                // claim may survive.  Answering `live` this narrowly is always safe; answering it any
                // wider than the subscription is what would serve a list short a row.
                1 -> socketHigh to { c: Condition<LargeTestModel>, _: Instant -> c == socketHigh }
                // A socket that admits it cannot speak for anything.
                else -> Condition.Always to { _: Condition<LargeTestModel>, _: Instant -> false }
            }
            Run(seed, noMask, socket, live, counters).run(140)
        }
        counters.assertEveryPathReached(masked = false)
    }

    /**
     * The same property with a real read mask, which changes what "what the server would have
     * returned" means: sorting by a masked field makes the server AND in the mask's term, and the
     * values that come back have the masked fields rewritten.
     *
     * A single socket subscribed to everything here, because membership of a narrower subscription is
     * decided server-side on unmasked values and the client would have no way to mirror that.
     */
    @Test fun aReadMaskDoesNotLetTheStoreOverclaim() {
        val counters = Counters()
        for (seed in 101..118) {
            Run(seed, maskOnInt, Condition.Always, { _, _ -> true }, counters).run(140)
        }
        counters.assertEveryPathReached(masked = true)
    }

    /**
     * A mask over `int`, so a sort touching `int` carries the extra condition the server imposes and
     * a sort that does not, does not.  That asymmetry is the whole reason the store tracks masks.
     */
    private val maskOnInt = Mask<LargeTestModel>(
        listOf(condition<LargeTestModel> { it.boolean eq true } to modification<LargeTestModel> { it.int assign 0 })
    )

    private fun Counters.assertEveryPathReached(masked: Boolean) {
        println(
            "COUNTERS masked=$masked complete=$complete partial=$partial bounded=$boundedAnswers " +
                    "subCond=$subsumedCondition subOrder=$subsumedOrder pages=$pages pagesOut=$pagesThatRanOut " +
                    "sockChg=$socketChanges sockRem=$socketRemovals keep=$socketRemovalsKeepingAClaim " +
                    "drop=$socketRemovalsDroppingAClaim invAll=$invalidateAll invOne=$invalidateOne " +
                    "declEnd=$declaredEnd declMore=$declaredMore inFlight=$inFlightAnswers " +
                    "inFlightRaced=$inFlightAnswersThatRacedARow maskedSort=$maskedSortAnswers"
        )
        assertTrue(complete > 15000, "only $complete complete answers were checked")
        assertTrue(partial > 1000, "only $partial partial answers were seen")
        assertTrue(boundedAnswers > 1000, "only $boundedAnswers answers came from a bounded claim")
        assertTrue(subsumedCondition > 5000, "only $subsumedCondition answers came from a broader condition")
        assertTrue(subsumedOrder > 1000, "only $subsumedOrder answers were re-sorted from another order")
        assertTrue(pages > 50, "only $pages pages were recorded")
        assertTrue(pagesThatRanOut > 25, "only $pagesThatRanOut pages ran off the end")
        assertTrue(socketChanges > 50, "only $socketChanges socket changes were applied")
        assertTrue(socketRemovals > 10, "only $socketRemovals socket removals were applied")
        assertTrue(invalidateAll > 5, "invalidate() ran only $invalidateAll times")
        assertTrue(invalidateOne > 30, "only $invalidateOne single-ID invalidations ran")
        assertTrue(declaredEnd > 5, "only $declaredEnd answers had their end declared by the server")
        assertTrue(declaredMore > 20, "only $declaredMore short answers were declared to continue")
        assertTrue(inFlightAnswers > 50, "only $inFlightAnswers answers landed out of order")
        assertTrue(
            inFlightAnswersThatRacedARow > 5,
            "only $inFlightAnswersThatRacedARow out-of-order answers actually contradicted a later change",
        )
        if (masked) {
            assertTrue(maskedSortAnswers > 10000, "only $maskedSortAnswers answers were served under a masked sort")
        } else {
            assertTrue(
                socketRemovalsKeepingAClaim > 50,
                "only $socketRemovalsKeepingAClaim removals arrived with a claim the socket could speak for",
            )
            assertTrue(
                socketRemovalsDroppingAClaim > 50,
                "only $socketRemovalsDroppingAClaim removals arrived with a claim the socket could not speak for",
            )
        }
    }
}
