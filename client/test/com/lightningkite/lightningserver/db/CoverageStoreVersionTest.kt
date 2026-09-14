package com.lightningkite.lightningserver.db

import com.lightningkite.services.database.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * Settling conflicting accounts of the same row by version rather than by clock.
 *
 * Timestamps are a proxy for *which of these two happened later*, and a poor one: the client stamps a
 * request when it goes out, so an answer assembled from an older snapshot can arrive after a change
 * it does not know about, and two changes inside one tick are indistinguishable.  A version on the
 * row answers the question outright.
 *
 * The interesting cases are all the ones where version and timestamp *disagree* - anywhere they
 * agree, these tests would pass with the version support removed entirely and would prove nothing.
 */
class CoverageStoreVersionTest {
    private val serializer = LargeTestModel.serializer()

    /** `long` stands in for a version field; the model has no dedicated one. */
    private fun versionedStore() = CoverageStore<LargeTestModel, Uuid>(serializer) { it.long }

    /** The same store with no way to read a version, which is every model today. */
    private fun unversionedStore() = CoverageStore<LargeTestModel, Uuid>(serializer)

    private val byInt: List<SortPart<LargeTestModel>> =
        sort<LargeTestModel> { it.int.ascending() }.ensureTotal(serializer)

    private fun t(seconds: Long): Instant = Instant.fromEpochSeconds(seconds)

    private fun query(limit: Int = 100) = Query(Condition.Always, byInt, limit = limit)

    /** A row at version [version], carrying [marker] so which copy won is visible. */
    private fun row(version: Long, marker: Short, id: Uuid = Uuid.random()) =
        LargeTestModel(_id = id, int = 1, long = version, short = marker)

    private fun CoverageStore<LargeTestModel, Uuid>.only(): LargeTestModel =
        assertNotNull(known(query())).items.single()

    // =========================================================================
    // Version beats timestamp
    // =========================================================================

    /**
     * The case the whole thing exists for: an answer that is *newer by clock* but describes an older
     * state of the row.  A poll stamped after a change it was assembled before looks exactly like
     * this, and without a version there is nothing in the data that says so.
     */
    @Test fun anOlderVersionLosesEvenWhenItsTimestampIsNewer() {
        val store = versionedStore()
        val id = Uuid.random()
        store.queried(query(), listOf(row(version = 5, marker = 5, id = id)), t(1))

        store.mutated(listOf(row(version = 2, marker = 2, id = id)), t(99))

        assertEquals<Short>(5, store.only().short, "version 2 is an older account of the row than version 5")
    }

    /** And the converse: a newer version wins despite an older clock. */
    @Test fun aNewerVersionWinsEvenWhenItsTimestampIsOlder() {
        val store = versionedStore()
        val id = Uuid.random()
        store.queried(query(), listOf(row(version = 2, marker = 2, id = id)), t(99))

        store.mutated(listOf(row(version = 7, marker = 7, id = id)), t(1))

        assertEquals<Short>(7, store.only().short, "version 7 is the later account whatever the clocks said")
    }

    /**
     * Without a version reader nothing changes, which is what makes this safe to add to a model that
     * has not got one.  Same data as the test above, opposite outcome.
     */
    @Test fun withoutAVersionReaderTimestampsStillDecide() {
        val store = unversionedStore()
        val id = Uuid.random()
        store.queried(query(), listOf(row(version = 2, marker = 2, id = id)), t(99))

        store.mutated(listOf(row(version = 7, marker = 7, id = id)), t(1))

        assertEquals<Short>(2, store.only().short, "the t(99) account is newer by the only measure available")
    }

    /** Equal versions are the same account of the row, so the later write simply lands. */
    @Test fun equalVersionsDoNotBlockEachOther() {
        val store = versionedStore()
        val id = Uuid.random()
        store.queried(query(), listOf(row(version = 3, marker = 1, id = id)), t(1))

        store.mutated(listOf(row(version = 3, marker = 2, id = id)), t(2))

        assertEquals<Short>(2, store.only().short)
    }

    // =========================================================================
    // Where a version cannot help
    // =========================================================================

    /**
     * A row with no version yet is judged the old way rather than treated as version zero, which
     * would let any versioned row overwrite it regardless of order.
     */
    @Test fun aRowWithoutAVersionFallsBackToTimestamps() {
        val store = CoverageStore<LargeTestModel, Uuid>(serializer) { it.long.takeIf { v -> v != 0L } }
        val id = Uuid.random()
        store.queried(query(), listOf(row(version = 0, marker = 1, id = id)), t(99))

        // Version 7 would win on version alone, but the held row has none to compare against.
        store.mutated(listOf(row(version = 7, marker = 7, id = id)), t(1))

        assertEquals<Short>(1, store.only().short, "no version to compare, so the newer timestamp holds")
    }

    /**
     * A deletion carries no row, so there is no version to read from it and the timestamp still
     * decides.  Documented rather than worked around: "there is no account" has no version, and a
     * model wanting deletions ordered by version would need tombstones.
     */
    @Test fun aDeletionHasNoVersionSoTimestampsStillDecideIt() {
        val store = versionedStore()
        val id = Uuid.random()
        store.queried(query(), listOf(row(version = 9, marker = 9, id = id)), t(50))

        // Older by clock than what we hold, so it is ignored - the version is irrelevant here.
        store.deleted(setOf(id), t(10))
        assertEquals<Short>(9, store.only().short, "an older deletion does not undo a newer value")

        store.deleted(setOf(id), t(60))
        assertTrue(assertNotNull(store.known(query())).items.isEmpty(), "a newer deletion does apply")
    }

    /**
     * Likewise a row's *absence* from a query's results: there is no incoming row, so reconciliation
     * still falls back to the clock.
     */
    @Test fun absenceFromAnAnswerIsStillJudgedByTimestamp() {
        val store = versionedStore()
        val kept = row(version = 1, marker = 1)
        val vanished = row(version = 1, marker = 2)
        store.queried(query(), listOf(kept, vanished), t(50))

        // An answer assembled before we last heard about the row is not evidence against it.
        store.queried(query(), listOf(kept), t(10))
        assertEquals(2, assertNotNull(store.known(query())).items.size, "the older answer proves nothing")

        // A newer one is.
        store.queried(query(), listOf(kept), t(60))
        assertEquals(listOf(kept), assertNotNull(store.known(query())).items)
    }

    // =========================================================================
    // Reaching it through ModelCache
    // =========================================================================

    /** The version reader is wired all the way through, not just available on the store. */
    @Test fun modelCachePassesItsVersionReaderToTheStore() = runTest2 {
        val mock = ClientModelRestEndpointsMock<LargeTestModel, Uuid>(backgroundScope)
        val cache = ModelCache(mock, serializer, scope = backgroundScope, versionOf = { it.long })
        val id = Uuid.random()

        cache.store.queried(query(), listOf(row(version = 5, marker = 5, id = id)), t(1))
        cache.store.mutated(listOf(row(version = 2, marker = 2, id = id)), t(99))

        assertEquals<Short>(5, cache.store.only().short)
    }
}
