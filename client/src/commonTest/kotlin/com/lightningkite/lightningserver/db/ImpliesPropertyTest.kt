package com.lightningkite.lightningserver.db

import com.lightningkite.services.database.*
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * An attack on [CoverageStore]'s condition subsumption.
 *
 * The rules that decide *"does a claim about this condition cover a query for that one?"* are the one
 * place in this library where being wrong is silent: a claim accepted on a false implication serves a
 * list that is short a row, and every layer above it believes the list.  So this file's job is not to
 * demonstrate that the rules work, it is to try to catch them claiming something they cannot support.
 *
 * The implication function itself is private, so everything here goes through
 * [CoverageStore.known] - which is the honest place to test it anyway, since that is where a wrong
 * answer would actually escape.  The shape is always the same: give the store complete knowledge of
 * one condition, ask it a different one, and compare whatever it says against a brute-force filter
 * over rows it was never told about.  A store that refuses to answer is always allowed; a store that
 * answers with anything other than the whole truth is not.
 */
class ImpliesPropertyTest {
    private val serializer = LargeTestModel.serializer()
    private val p = path(serializer)

    /** Total, as every sort reaching the store is; ties break on `_id` so the order never wobbles. */
    private val byInt = sort<LargeTestModel> { it.int.ascending() }.ensureTotal(serializer)
    private val order = byInt.comparator!!

    private fun t(seconds: Long): Instant = Instant.fromEpochSeconds(seconds)

    private fun store() = CoverageStore<LargeTestModel, Uuid>(serializer)

    private fun query(condition: Condition<LargeTestModel>, limit: Int) = Query(condition, byInt, limit = limit)

    /**
     * Whether complete knowledge of [broad] is allowed to answer a query for [narrow] - the raw
     * verdict of the implication rules.
     *
     * Seeded from an empty result, so the store holds no rows at all and the verdict is the only
     * thing the answer can depend on.
     */
    private fun covers(broad: Condition<LargeTestModel>, narrow: Condition<LargeTestModel>): Boolean {
        val store = store()
        store.queried(query(broad, limit = 1), listOf(), t(1))
        return store.known(query(narrow, limit = 10)) != null
    }

    /**
     * The soundness check, and the thing every test here ultimately makes.
     *
     * The store is told, completely, about the rows of [world] that match [broad] - and nothing else,
     * so any row matching [narrow] but not [broad] is a row it has never heard of.  If it then answers
     * a query for [narrow], the answer has to be the whole list; missing that row is exactly the
     * failure a false implication causes.
     *
     * @return whether the store answered at all, so callers can tell a real check from a refusal
     */
    private fun assertServesEveryMatch(
        broad: Condition<LargeTestModel>,
        narrow: Condition<LargeTestModel>,
        world: List<LargeTestModel>,
        note: String = "",
    ): Boolean {
        val store = store()
        val seen = world.filter { broad(it) }.sortedWith(order)
        // One row of headroom, so the answer is shorter than its limit and the claim is complete.
        store.queried(query(broad, limit = seen.size + 1), seen, t(1))

        val ask = query(narrow, limit = world.size + 1)
        val answer = store.known(ask) ?: return false
        if (answer.partial) return true
        assertEquals(
            world.filter { narrow(it) }.sortedWith(order),
            answer.items,
            "$note\na claim for\n  $broad\nwas used to answer\n  $narrow\nand came up with a list the server would never have returned",
        )
        return true
    }

    // =========================================================================
    // The property
    // =========================================================================

    /**
     * Leaves picked so that pairs of them *nearly* imply one another: the same bound at the same
     * value with the other inclusivity, the same value one field over, a set with a single member
     * changed, `all` against `any` over the same elements.  Composing these at random makes near
     * misses the common case rather than a rare one, which is where a subsumption rule goes wrong if
     * it is going to.
     */
    private val leafPool: List<Condition<LargeTestModel>> = listOf(
        Condition.Always,
        Condition.Never,
        p.int gt 3, p.int gte 3, p.int lt 3, p.int lte 3,
        p.int gt 4, p.int gte 4, p.int lt 4, p.int lte 4,
        p.int eq 3, p.int neq 3,
        p.int inside setOf(1, 3), p.int inside setOf(1, 3, 5),
        p.int notInside setOf(1, 3), p.int notInside setOf(1, 3, 5),
        p.int inside setOf<Int>(), p.int notInside setOf<Int>(),
        p.short gt 1.toShort(), p.short gte 1.toShort(), p.short lt 2.toShort(), p.short eq 1.toShort(),
        p.boolean eq true, p.boolean eq false,
        p.string eq "cat", p.string eq "cattle", p.string contains "cat",
        p.string.mapCondition(Condition.RegexMatches("c.*")),
        p.string inside setOf("cat", "dog"),
        p.intNullable eq null, p.intNullable neq null, p.intNullable eq 4,
        p.intNullable.notNull gt 3, p.intNullable.notNull gte 3,
        p.embedded.value2 gt 3, p.embedded.value2 gte 3, p.embedded.value1 eq "cat",
        p.list.elements gt 1, p.list.elements gt 2,
        p.list.mapCondition(Condition.ListAnyElements(Condition.GreaterThan(1))),
        p.list.mapCondition(Condition.ListAnyElements(Condition.GreaterThan(2))),
        p.set.elements gt 1,
        p.set.mapCondition(Condition.SetAnyElements(Condition.GreaterThan(1))),
        p.map.mapCondition(Condition.OnKey("k", Condition.GreaterThan(1))),
        p.map.mapCondition(Condition.OnKey("k", Condition.GreaterThan(2))),
        p.map.mapCondition(Condition.OnKey("j", Condition.GreaterThan(1))),
    )

    /** Empty `And` and `Or` come out of this too - they are the degenerate cases worth reaching. */
    private fun randomCondition(random: Random, depth: Int): Condition<LargeTestModel> =
        if (depth == 0) leafPool.random(random)
        else when (random.nextInt(9)) {
            0 -> Condition.And(List(random.nextInt(0, 3)) { randomCondition(random, depth - 1) })
            1 -> Condition.Or(List(random.nextInt(0, 3)) { randomCondition(random, depth - 1) })
            2 -> Condition.Not(randomCondition(random, depth - 1))
            else -> leafPool.random(random)
        }

    private val strings = listOf("", "cat", "cattle", "dog")

    /** Values sit on and around every constant in [leafPool], so boundaries are actually straddled. */
    private fun randomRow(random: Random) = LargeTestModel(
        int = random.nextInt(0, 7),
        short = random.nextInt(0, 4).toShort(),
        boolean = random.nextBoolean(),
        string = strings.random(random),
        intNullable = if (random.nextInt(4) == 0) null else random.nextInt(0, 7),
        embedded = ClassUsedForEmbedding(value1 = strings.random(random), value2 = random.nextInt(0, 7)),
        list = List(random.nextInt(0, 3)) { random.nextInt(0, 5) },
        set = List(random.nextInt(0, 3)) { random.nextInt(0, 5) }.toSet(),
        map = if (random.nextInt(3) == 0) mapOf() else mapOf("k" to random.nextInt(0, 5)),
    )

    /**
     * The whole promise of subsumption, checked against brute force:
     *
     * > whenever a claim about one condition is used to answer another, the answer is every row the
     * > server would have returned.
     *
     * Half the pairs are deliberate refinements of the claim, since those are the ones that get
     * answered and so the ones that can be wrong; the rest are unrelated, which is what proves the
     * rules refuse the pairs they should.  Seeds are fixed so any failure comes back on the next run.
     */
    @Test fun aClaimNeverAnswersAQueryItCannotAccountFor() {
        var answered = 0
        for (seed in 1..600) {
            val random = Random(seed)
            val broad = randomCondition(random, 2)
            val narrow = when (random.nextInt(4)) {
                0 -> randomCondition(random, 2)
                1 -> Condition.And(listOf(broad, randomCondition(random, 1)))
                2 -> Condition.And(listOf(randomCondition(random, 1), broad))
                else -> randomCondition(random, 2)
            }
            repeat(4) { worldIndex ->
                val world = List(random.nextInt(3, 10)) { randomRow(random) }
                val served = assertServesEveryMatch(broad, narrow, world, "seed $seed world $worldIndex")
                // Only the pairs that had to be *reasoned* about count; an identical pair is a
                // dictionary lookup and proves nothing about the rules under test.
                if (served && narrow != broad) answered++
            }
        }
        // A rule set that answered nothing would pass everything above without doing any work.
        assertTrue(answered > 1000, "only $answered of 2400 attempts were answered from a broader claim")
    }

    /**
     * The same property where the claim stops part way, which is the other half of [CoverageStore.known].
     *
     * A broad claim bounded at one of its own rows covers a narrower query only as far as that row
     * reaches, and the truncation is done in the *query's* terms rather than the claim's.  Getting
     * that wrong looks identical from the outside to getting the implication wrong, so it is worth
     * driving separately: small limits, so answers land inside the boundary and come back complete
     * rather than partial.
     */
    @Test fun aBoundedClaimNeverAnswersPastWhatItReached() {
        var answered = 0
        for (seed in 1..600) {
            val random = Random(seed)
            val broad = randomCondition(random, 2)
            val narrow = if (random.nextBoolean()) Condition.And(listOf(broad, randomCondition(random, 1)))
            else randomCondition(random, 2)
            repeat(4) { worldIndex ->
                val world = List(random.nextInt(3, 10)) { randomRow(random) }
                val limit = random.nextInt(1, 4)
                val store = store()
                val seen = world.filter { broad(it) }.sortedWith(order)
                // Exactly as many rows as asked for, so the claim ends at its last row rather than
                // at the end of the results.
                store.queried(query(broad, limit = maxOf(1, seen.size)), seen, t(1))

                val answer = store.known(query(narrow, limit)) ?: return@repeat
                if (answer.partial) return@repeat
                answered++
                assertEquals(
                    world.filter { narrow(it) }.sortedWith(order).take(limit),
                    answer.items,
                    "seed $seed world $worldIndex\na claim for\n  $broad\nbounded at its last row was used to answer\n  $narrow",
                )
            }
        }
        assertTrue(answered > 500, "only $answered of 2400 attempts came back complete")
    }

    // =========================================================================
    // Endpoints, where inclusive and exclusive meet
    // =========================================================================

    /** `(5, inf)` contains `[6, inf)` and `(5, inf)`, and does not contain `[5, inf)`. */
    @Test fun anExclusiveLowerBoundDoesNotCoverTheInclusiveOneAtItsOwnEndpoint() {
        assertFalse(covers(p.int gt 5, p.int gte 5), "5 itself was never covered")
        assertTrue(covers(p.int gt 5, p.int gt 5))
        assertTrue(covers(p.int gt 5, p.int gte 6))
        assertServesEveryMatch(
            broad = p.int gt 5,
            narrow = p.int gte 5,
            world = listOf(LargeTestModel(int = 5), LargeTestModel(int = 6)),
        )
    }

    /** And the other way: `[5, inf)` does contain `(5, inf)`, so that pair is answerable. */
    @Test fun anInclusiveLowerBoundCoversTheExclusiveOneAtItsOwnEndpoint() {
        assertTrue(covers(p.int gte 5, p.int gt 5))
        assertTrue(covers(p.int gte 5, p.int gte 5))
        assertFalse(covers(p.int gte 5, p.int gte 4), "4 was never covered")
    }

    /** The upper side is the same rule read the other way round, and gets it wrong just as easily. */
    @Test fun exclusiveAndInclusiveUpperBoundsMeetAtTheirEndpointToo() {
        assertFalse(covers(p.int lt 5, p.int lte 5), "5 itself was never covered")
        assertTrue(covers(p.int lt 5, p.int lte 4))
        assertTrue(covers(p.int lte 5, p.int lt 5))
        assertFalse(covers(p.int lte 5, p.int lte 6))
        assertServesEveryMatch(
            broad = p.int lt 5,
            narrow = p.int lte 5,
            world = listOf(LargeTestModel(int = 4), LargeTestModel(int = 5)),
        )
    }

    /** A bound says nothing whatever about the other side, however the numbers happen to line up. */
    @Test fun boundsOnOppositeSidesNeverCoverEachOther() {
        assertFalse(covers(p.int gt 3, p.int lt 3))
        assertFalse(covers(p.int gt 3, p.int lt 9))
        assertFalse(covers(p.int lte 9, p.int gte 9))
        assertServesEveryMatch(
            broad = p.int gt 3,
            narrow = p.int lt 9,
            world = listOf(LargeTestModel(int = 1), LargeTestModel(int = 5)),
        )
    }

    /**
     * Values of two types can only meet on one field by way of erasure, but a condition arriving
     * from anywhere but a compiler could carry one.  Comparing them would either crash or invent an
     * ordering, and neither is an answer.
     */
    @Suppress("UNCHECKED_CAST")
    @Test fun boundsOfDifferentTypesOnTheSameFieldAreNeverCompared() {
        val longBound = p.int.mapCondition(Condition.GreaterThan(5L) as Condition<Int>)
        val shortBound = p.int.mapCondition(Condition.GreaterThan(5.toShort()) as Condition<Int>)

        assertFalse(covers(longBound, p.int gt 6))
        assertFalse(covers(p.int gt 6, longBound))
        assertFalse(covers(shortBound, p.int gt 6))
        assertFalse(covers(p.int gt 4, shortBound))
    }

    // =========================================================================
    // Negation
    // =========================================================================

    /** Excluding more is stricter than excluding less; excluding less is not stricter than anything. */
    @Test fun exclusionsCompareInTheOppositeDirectionToTheThingsTheyExclude() {
        assertTrue(covers(p.int neq 1, p.int notInside setOf(1, 2)))
        assertFalse(covers(p.int notInside setOf(1, 2), p.int neq 1))
        assertServesEveryMatch(
            broad = p.int notInside setOf(1, 2),
            narrow = p.int neq 1,
            world = listOf(LargeTestModel(int = 2), LargeTestModel(int = 3)),
        )
    }

    /** A negation of a negation is the thing itself, and has to compare as the thing itself. */
    @Test fun doubleNegationCollapsesInBothDirections() {
        val low = p.int gt 3
        val high = p.int gt 5
        assertTrue(covers(Condition.Not(Condition.Not(low)), Condition.Not(Condition.Not(high))))
        assertFalse(covers(Condition.Not(Condition.Not(high)), Condition.Not(Condition.Not(low))))
        // One negation deep, where the direction of the comparison flips exactly once.
        assertTrue(covers(Condition.Not(high), Condition.Not(low)))
        assertFalse(covers(Condition.Not(low), Condition.Not(high)))
        assertServesEveryMatch(
            broad = Condition.Not(low),
            narrow = Condition.Not(high),
            world = listOf(LargeTestModel(int = 2), LargeTestModel(int = 4), LargeTestModel(int = 6)),
        )
    }

    /** Negating a composite still reverses: `¬(a ∨ b)` is stricter than `¬b`, and not the reverse. */
    @Test fun negatingACompositeStillReversesTheComparison() {
        val a = p.int lt 1
        val b = p.int gt 9
        assertTrue(covers(Condition.Not(b), Condition.Not(Condition.Or(listOf(a, b)))))
        assertFalse(covers(Condition.Not(Condition.Or(listOf(a, b))), Condition.Not(b)))
    }

    // =========================================================================
    // Degenerate shapes
    // =========================================================================

    /**
     * An empty `And` matches everything and an empty `Or` matches nothing, so each covers in exactly
     * one direction.  Reading either as the other way round is a straight loss of rows.
     */
    @Test fun emptyAndIsEverythingAndEmptyOrIsNothing() {
        val everything = Condition.And<LargeTestModel>(listOf())
        val nothing = Condition.Or<LargeTestModel>(listOf())

        assertTrue(covers(everything, p.int gt 3), "knowing all of them is knowing the ones over 3")
        assertFalse(covers(p.int gt 3, everything), "rows at or below 3 were never covered")
        assertTrue(covers(p.int gt 3, nothing), "an unsatisfiable query needs nothing to answer it")
        assertFalse(covers(nothing, p.int gt 3), "knowing nothing is not knowing something")
        assertServesEveryMatch(
            broad = p.int gt 3,
            narrow = everything,
            world = listOf(LargeTestModel(int = 1), LargeTestModel(int = 5)),
        )
    }

    /** `Inside` of nothing matches nothing; the two directions again differ. */
    @Test fun anEmptyValueSetMatchesNothingAndAnEmptyExclusionMatchesEverything() {
        assertTrue(covers(p.int gt 3, p.int inside setOf<Int>()))
        assertFalse(covers(p.int inside setOf<Int>(), p.int gt 3))
        assertFalse(covers(p.int gt 3, p.int notInside setOf<Int>()), "excluding nothing excludes nothing")
        assertServesEveryMatch(
            broad = p.int inside setOf<Int>(),
            narrow = p.int gt 3,
            world = listOf(LargeTestModel(int = 5)),
        )
    }

    /** `Never` is stricter than anything and broader than nothing, wherever it is buried. */
    @Test fun neverIsCoveredByEverythingAndCoversOnlyItself() {
        val unsatisfiable = Condition.And(listOf(p.int gt 5, Condition.Never))
        assertTrue(covers(p.int gt 9, unsatisfiable))
        assertTrue(covers(p.int gt 9, Condition.Or(listOf(Condition.Never, p.int gt 9))))
        assertFalse(covers(unsatisfiable, p.int gt 7), "a claim about nothing covers nothing")
        assertServesEveryMatch(
            broad = unsatisfiable,
            narrow = p.int gt 7,
            world = listOf(LargeTestModel(int = 8)),
        )
    }

    // =========================================================================
    // Wrappers: null, elements, keys, fields
    // =========================================================================

    /**
     * `IfNotNull` is where a condition on a nullable field lives, and the null rows are precisely the
     * ones it never speaks for - so a claim built from one must not answer for them.
     */
    @Test fun aClaimAboutNonNullValuesSaysNothingAboutTheNullOnes() {
        assertFalse(covers(p.intNullable.notNull gt 3, p.intNullable eq null))
        assertTrue(covers(p.intNullable.notNull gt 3, p.intNullable.notNull gt 5))
        assertTrue(covers(p.intNullable.notNull gt 3, p.intNullable eq 7), "7 is inside the claim")
        assertFalse(covers(p.intNullable.notNull gt 3, p.intNullable eq 2), "2 is not")
        assertServesEveryMatch(
            broad = p.intNullable.notNull gt 3,
            narrow = p.intNullable eq null,
            world = listOf(LargeTestModel(int = 1, intNullable = null), LargeTestModel(int = 2, intNullable = 9)),
        )
    }

    /**
     * `all` over an empty collection is true and `any` over one is false, so the two are not versions
     * of each other however alike their insides look.  An empty list is the row that proves it.
     */
    @Test fun allElementsAndAnyElementsAreNotInterchangeable() {
        val all = p.list.elements gt 1
        val any = p.list.mapCondition(Condition.ListAnyElements(Condition.GreaterThan(1)))

        assertFalse(covers(any, all), "an empty list matches all and not any")
        assertFalse(covers(all, any), "a list of [0, 2] matches any and not all")
        assertTrue(covers(all, p.list.elements gt 2), "stricter insides do carry through")
        assertServesEveryMatch(
            broad = any,
            narrow = all,
            world = listOf(
                LargeTestModel(int = 1, list = listOf()),
                LargeTestModel(int = 2, list = listOf(2, 3)),
            ),
        )
        assertServesEveryMatch(
            broad = all,
            narrow = any,
            world = listOf(
                LargeTestModel(int = 1, list = listOf(0, 2)),
                LargeTestModel(int = 2, list = listOf(2, 3)),
            ),
        )
    }

    /** Two map keys are two different questions, however similar the conditions under them. */
    @Test fun conditionsUnderDifferentMapKeysDoNotCoverEachOther() {
        val onK = p.map.mapCondition(Condition.OnKey("k", Condition.GreaterThan(1)))
        val onJ = p.map.mapCondition(Condition.OnKey("j", Condition.GreaterThan(2)))
        assertFalse(covers(onK, onJ))
        assertTrue(covers(onK, p.map.mapCondition(Condition.OnKey("k", Condition.GreaterThan(2)))))
        assertServesEveryMatch(
            broad = onK,
            narrow = onJ,
            world = listOf(
                LargeTestModel(int = 1, map = mapOf("j" to 5)),
                LargeTestModel(int = 2, map = mapOf("k" to 5, "j" to 5)),
            ),
        )
    }

    /** The same numbers on two fields are two unrelated facts, nested fields included. */
    @Test fun matchingValuesOnDifferentFieldsProveNothingAboutEachOther() {
        assertFalse(covers(p.int gt 3, p.short gt 3.toShort()))
        assertFalse(covers(p.int gt 3, p.embedded.value2 gt 3))
        assertFalse(covers(p.embedded.value2 gt 3, p.embedded.value1 eq "cat"))
        assertTrue(covers(p.embedded.value2 gt 3, p.embedded.value2 gt 5), "the same field does carry")
        assertServesEveryMatch(
            broad = p.int gt 3,
            narrow = p.embedded.value2 gt 3,
            world = listOf(
                LargeTestModel(int = 1, embedded = ClassUsedForEmbedding(value2 = 9)),
                LargeTestModel(int = 5, embedded = ClassUsedForEmbedding(value2 = 9)),
            ),
        )
    }

    // =========================================================================
    // Strings, where a value set is the only handle there is
    // =========================================================================

    /**
     * A search has no structure to compare against another search, but a known value can simply be
     * run through it - which is what lets an equality land inside a `contains` or a regex.
     */
    @Test fun aKnownStringIsMeasuredAgainstSearchesByRunningThem() {
        assertTrue(covers(p.string contains "cat", p.string eq "cattle"))
        assertFalse(covers(p.string contains "cat", p.string eq "dog"))
        assertTrue(covers(p.string.mapCondition(Condition.RegexMatches("c.*")), p.string eq "cat"))
        assertFalse(covers(p.string.mapCondition(Condition.RegexMatches("c.*")), p.string eq "dog"))
        assertTrue(covers(p.string contains "cat", p.string inside setOf("cat", "cattle")))
        assertFalse(covers(p.string contains "cat", p.string inside setOf("cat", "dog")), "one miss is enough")
        assertServesEveryMatch(
            broad = p.string contains "cat",
            narrow = p.string inside setOf("cat", "dog"),
            world = listOf(
                LargeTestModel(int = 1, string = "cat"),
                LargeTestModel(int = 2, string = "dog"),
            ),
        )
    }
}
