package com.lightningkite.lightningserver.db

import com.lightningkite.services.database.*
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * [impliesCondition] on its own, with nothing else in the way.
 *
 * This is the one function in the library whose wrong answer is silent.  It decides whether a claim
 * about one condition covers a query for another; a true it cannot support makes the cache serve a
 * list that is short a row, and every layer above believes the list.  [ImpliesPropertyTest] attacks
 * it through [CoverageStore.known], which is where such a mistake would actually escape - this file
 * attacks the function directly, which is where the mistake would be *made*.
 *
 * The heart of it is one property: **if it says yes, then every row matching the stricter condition
 * really does match the looser one**, checked by evaluating both.  That is not an approximation of
 * the specification, it is the specification, so a randomised search over it is worth more than any
 * number of hand-picked pairs - the hand-picked ones below exist only to pin down the corners that
 * random generation reaches rarely.
 */
class ConditionImpliesTest {

    // =========================================================================
    // Generating conditions and rows to judge them against
    // =========================================================================

    private fun leaf(random: Random): Condition<LargeTestModel> {
        val n = random.nextInt(0, 6)
        val m = random.nextInt(0, 6)
        return when (random.nextInt(14)) {
            0 -> condition { it.int gt n }
            1 -> condition { it.int gte n }
            2 -> condition { it.int lt n }
            3 -> condition { it.int lte n }
            4 -> condition { it.int eq n }
            5 -> condition { it.int neq n }
            6 -> condition { it.int inside setOf(n, m) }
            7 -> condition { it.int notInside setOf(n, m) }
            8 -> condition { it.short gt n.toShort() }
            9 -> condition { it.short lte n.toShort() }
            10 -> condition { it.boolean eq (n % 2 == 0) }
            11 -> condition { it.string eq "s$n" }
            12 -> condition { it.embedded.value2 gt n }
            else -> if (n % 2 == 0) Condition.Always else Condition.Never
        }
    }

    /** Composites as well as leaves, since the And/Or/Not rules are where the subtlety lives. */
    private fun anyCondition(random: Random, depth: Int = 2): Condition<LargeTestModel> {
        if (depth == 0) return leaf(random)
        return when (random.nextInt(6)) {
            0 -> Condition.And((0..random.nextInt(0, 3)).map { anyCondition(random, depth - 1) })
            1 -> Condition.Or((0..random.nextInt(0, 3)).map { anyCondition(random, depth - 1) })
            2 -> Condition.Not(anyCondition(random, depth - 1))
            else -> leaf(random)
        }
    }

    private fun rows(random: Random, count: Int): List<LargeTestModel> = (1..count).map {
        LargeTestModel(
            int = random.nextInt(-1, 8),
            short = random.nextInt(-1, 8).toShort(),
            boolean = random.nextBoolean(),
            string = "s${random.nextInt(0, 6)}",
            embedded = ClassUsedForEmbedding(value2 = random.nextInt(-1, 8)),
        )
    }

    // =========================================================================
    // The property
    // =========================================================================

    /**
     * The specification itself: a yes must be backed by every row that could ever be involved.
     *
     * Counts how often it says yes as well, because a function that always said no would satisfy
     * this trivially - and would also be useless.
     */
    @Test fun sayingYesMeansEveryMatchingRowReallyMatches() {
        var yes = 0
        var pairs = 0
        for (seed in 1..250) {
            val random = Random(seed)
            val sample = rows(random, 40)
            repeat(20) {
                val narrow = anyCondition(random)
                val broad = anyCondition(random)
                pairs++
                if (!impliesCondition(narrow, broad)) return@repeat
                yes++
                for (row in sample) {
                    if (narrow(row) && !broad(row)) {
                        throw AssertionError(
                            "seed $seed: claimed\n  $narrow\nimplies\n  $broad\nbut this row matches only the first: $row"
                        )
                    }
                }
            }
        }
        // Not a threshold anyone tuned - just proof the search is not passing by always saying no.
        assertTrue(yes > 200, "only $yes of $pairs pairs were accepted; the property is nearly vacuous")
    }

    /** Anything implies itself, however it is spelled. */
    @Test fun everyConditionImpliesItself() {
        for (seed in 1..200) {
            val random = Random(seed)
            val c = anyCondition(random)
            assertTrue(impliesCondition(c, c), "did not recognise $c as implying itself")
        }
    }

    /** The two absolutes, against everything the generator can produce. */
    @Test fun neverImpliesAnythingAndEverythingImpliesAlways() {
        for (seed in 1..200) {
            val random = Random(seed)
            val c = anyCondition(random)
            assertTrue(impliesCondition(Condition.Never, c), "Never should imply $c")
            assertTrue(impliesCondition(c, Condition.Always), "$c should imply Always")
        }
    }

    /**
     * Implication composes: if a covers b and b covers c, a must cover c.  A rule set that breaks
     * this is not necessarily unsound, but it is inconsistent, and inconsistency here shows up as a
     * cache that reuses a claim or not depending on which equivalent spelling it was handed.
     */
    @Test fun acceptedImplicationsChainTogether() {
        var chains = 0
        // Shallower than elsewhere and sampled harder: three-deep composites almost never chain, so
        // a deep generator would leave this asserting nothing.
        for (seed in 1..4000) {
            val random = Random(seed)
            val a = anyCondition(random, depth = 1)
            val b = anyCondition(random, depth = 1)
            val c = anyCondition(random, depth = 1)
            if (impliesCondition(a, b) && impliesCondition(b, c)) {
                chains++
                assertTrue(impliesCondition(a, c), "a=>b and b=>c but not a=>c\na=$a\nb=$b\nc=$c")
            }
        }
        assertTrue(chains > 20, "only $chains chains found; transitivity was barely exercised")
    }

    // =========================================================================
    // Corners the generator reaches rarely
    // =========================================================================

    /** An empty conjunction constrains nothing; an empty disjunction admits nothing. */
    @Test fun emptyCompositesAreTheAbsolutes() {
        val nothing = condition<LargeTestModel> { it.int gt 3 }
        assertTrue(impliesCondition(nothing, Condition.And(listOf())), "an empty And is Always")
        assertTrue(impliesCondition(Condition.Or(listOf()), nothing), "an empty Or is Never")
        assertFalse(impliesCondition(Condition.And(listOf()), nothing), "Always implies almost nothing")
    }

    /** An empty value set matches nothing; an empty exclusion excludes nothing. */
    @Test fun emptyValueSetsSitAtTheExtremes() {
        val some = condition<LargeTestModel> { it.int gt 3 }
        assertTrue(impliesCondition(condition<LargeTestModel> { it.int inside setOf<Int>() }, some))
        assertTrue(impliesCondition(some, condition<LargeTestModel> { it.int notInside setOf<Int>() }))
        assertFalse(impliesCondition(condition<LargeTestModel> { it.int notInside setOf<Int>() }, some))
    }

    /** Endpoints are where inclusive and exclusive bounds actually differ. */
    @Test fun boundsAreComparedAtTheirEndpoints() {
        assertTrue(impliesCondition(condition { it.int gt 5 }, condition { it.int gte 5 }))
        assertFalse(impliesCondition(condition { it.int gte 5 }, condition { it.int gt 5 }))
        assertTrue(impliesCondition(condition { it.int lt 5 }, condition { it.int lte 5 }))
        assertFalse(impliesCondition(condition { it.int lte 5 }, condition { it.int lt 5 }))
        // Same side, strictly further in, either inclusivity.
        assertTrue(impliesCondition(condition { it.int gte 6 }, condition { it.int gt 5 }))
        assertTrue(impliesCondition(condition { it.int lte 4 }, condition { it.int lt 5 }))
    }

    /** Opposite sides never cover each other, however the numbers line up. */
    @Test fun oppositeBoundsNeverCoverEachOther() {
        assertFalse(impliesCondition(condition { it.int gt 5 }, condition { it.int lt 9 }))
        assertFalse(impliesCondition(condition { it.int lt 5 }, condition { it.int gt 1 }))
    }

    /** The same value on two different fields proves nothing. */
    @Test fun fieldsAreNotInterchangeable() {
        assertFalse(impliesCondition(condition { it.int gt 5 }, condition { it.short gt 5.toShort() }))
        assertFalse(impliesCondition(condition { it.int eq 3 }, condition { it.embedded.value2 eq 3 }))
    }

    /** Excluding more is stricter than excluding less - the rule read backwards. */
    @Test fun exclusionsReverse() {
        assertTrue(impliesCondition(condition { it.int notInside setOf(1, 2, 3) }, condition { it.int notInside setOf(1, 2) }))
        assertFalse(impliesCondition(condition { it.int notInside setOf(1, 2) }, condition { it.int notInside setOf(1, 2, 3) }))
        assertTrue(impliesCondition(Condition.Not(condition { it.int gt 3 }), Condition.Not(condition { it.int gt 5 })))
    }

    /**
     * A single known value is measured against the other condition by running it, so this works for
     * conditions the bound rules know nothing about.
     */
    @Test fun aKnownValueIsJudgedByEvaluation() {
        assertTrue(impliesCondition(condition { it.string eq "cat" }, condition { it.string.contains("at") }))
        assertFalse(impliesCondition(condition { it.string eq "dog" }, condition { it.string.contains("at") }))
        assertTrue(impliesCondition(condition { it.int inside setOf(6, 7) }, condition { it.int gt 5 }))
        assertFalse(impliesCondition(condition { it.int inside setOf(5, 7) }, condition { it.int gt 5 }))
    }

    /** Conjunctions are stronger than any of their terms; disjunctions weaker than all of theirs. */
    @Test fun compositesRelateToTheirTerms() {
        val a = condition<LargeTestModel> { it.int gt 3 }
        val b = condition<LargeTestModel> { it.boolean eq true }
        assertTrue(impliesCondition(Condition.And(listOf(a, b)), a))
        assertFalse(impliesCondition(a, Condition.And(listOf(a, b))))
        assertTrue(impliesCondition(a, Condition.Or(listOf(a, b))))
        assertFalse(impliesCondition(Condition.Or(listOf(a, b)), a))
        // Spelling does not matter.
        assertTrue(impliesCondition(Condition.And(listOf(a, b)), Condition.And(listOf(b, a))))
        assertTrue(impliesCondition(a, Condition.And(listOf(a))))
    }

    /**
     * The store's lookup and this function must agree, or the direct tests here would be describing
     * something the cache does not actually consult.
     */
    @Test fun theStoreConsultsExactlyThisFunction() {
        val serializer = LargeTestModel.serializer()
        var checked = 0
        for (seed in 1..60) {
            val random = Random(seed)
            val narrow = anyCondition(random)
            val broad = anyCondition(random)
            val store = CoverageStore<LargeTestModel, kotlin.uuid.Uuid>(serializer)
            val order = listOf<com.lightningkite.services.database.SortPart<LargeTestModel>>().ensureTotal(serializer)
            // An empty answer under the limit, so the claim is complete and holds no rows: whether
            // the store answers at all is then purely the implication verdict.
            store.queried(com.lightningkite.services.database.Query(broad, order, limit = 5), listOf(), t0)
            val answered = store.known(com.lightningkite.services.database.Query(narrow, order, limit = 5)) != null
            assertEquals(impliesCondition(narrow, broad), answered, "store and function disagree\nnarrow=$narrow\nbroad=$broad")
            checked++
        }
        assertTrue(checked > 0)
    }

    private val t0 = kotlin.time.Instant.fromEpochSeconds(1)
}
