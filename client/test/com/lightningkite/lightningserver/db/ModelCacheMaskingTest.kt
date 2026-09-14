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
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.Uuid

/**
 * What [ModelCache] does against a server that enforces a read mask.
 *
 * A read mask is the one server-side rewrite a client can neither see nor ignore: sorting by a field
 * the caller may not read would leak it by position, so the server ANDs the mask entries covering
 * every sorted field into the condition.  Two lists differing only in their sort are therefore
 * answers to two different questions, and a cache that treats them as one serves short lists.
 *
 * [ClientModelRestEndpointsMock] applies exactly that rewrite, so every test here is against a server
 * that really does hide rows - none of them would prove anything otherwise.  The recurring assertion
 * is on how many queries reached that server, since the interesting failures are all about reuse:
 * reusing knowledge that was never gathered, or failing to reuse knowledge that was.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ModelCacheMaskingTest {
    val testLog = if (Platform.current == Platform.Desktop) LogRoot else null

    /** Records every query reaching the server, so what the cache asks for can be asserted on. */
    private open class QueryRecordingMock(scope: CoroutineScope) :
        ClientModelRestEndpointsMock<LargeTestModel, Uuid>(scope) {
        val queries: MutableList<Query<LargeTestModel>> = mutableListOf()
        override suspend fun query(input: Query<LargeTestModel>): List<LargeTestModel> {
            queries.add(input)
            return super.query(input)
        }
    }

    private open class SocketMock(scope: CoroutineScope) :
        ClientModelRestEndpointsPlusUpdatesWebSocketMock<LargeTestModel, Uuid>(scope)

    // =========================================================================
    // The masks under test
    // =========================================================================

    /** Hides `int` from anyone who cannot see it, so any `int`-sorted query loses those rows. */
    private val hidesInt = condition<LargeTestModel> { it.boolean eq true } to
            modification<LargeTestModel> { it.int assign 0 }

    /**
     * A second entry, over two fields at once, so a sort on *either* of them picks this term up.
     * The one-modification-one-field case would not tell the two apart.
     */
    private val hidesStringAndLong = condition<LargeTestModel> { it.short gt 0 } to
            modification<LargeTestModel> { it.string assign ""; it.long assign 0 }

    private val twoMasks = Mask(listOf(hidesInt, hidesStringAndLong))

    private fun maskedPermissions(mask: Mask<LargeTestModel>) =
        ModelPermissions.allowAll<LargeTestModel>().copy(readMask = mask)

    // =========================================================================
    // Fixtures
    // =========================================================================

    /**
     * Four rows spanning both mask entries: one visible to everything, one hidden from `int` sorts,
     * one hidden from `string`/`long` sorts, and one hidden from both.
     */
    private fun rows() = listOf(
        LargeTestModel(byte = 1, int = 1, short = 1, long = 10, string = "a", boolean = true),
        LargeTestModel(byte = 2, int = 2, short = 2, long = 20, string = "b", boolean = false),
        LargeTestModel(byte = 3, int = 3, short = 0, long = 30, string = "c", boolean = true),
        LargeTestModel(byte = 4, int = 4, short = 0, long = 40, string = "d", boolean = false),
    )

    /**
     * Rows are named by [LargeTestModel.byte], which no mask here touches.  Naming them by a masked
     * field would make assertions about *which* rows came back depend on whether their values were
     * hidden, and the two things have to be separable.
     */
    private fun ModelCacheLimitReadable<LargeTestModel>.tags() = state.getOrNull()?.map { it.byte.toInt() }

    private fun CoroutineScope.fixture(
        mask: Mask<LargeTestModel> = twoMasks,
        data: List<LargeTestModel> = rows(),
    ): Triple<QueryRecordingMock, List<LargeTestModel>, ModelCache<LargeTestModel, Uuid>> {
        val mock = QueryRecordingMock(this)
        mock.modelPermissions = maskedPermissions(mask)
        mock.data.putAll(data.associateBy { it._id })
        return Triple(mock, data, ModelCache(mock, LargeTestModel.serializer(), scope = this, log = testLog))
    }

    /** A list that neither expires nor polls, so assertions see only what the test caused. */
    private fun ModelCache<LargeTestModel, Uuid>.quietList(
        condition: Condition<LargeTestModel> = Condition.Always,
        sort: List<SortPart<LargeTestModel>>,
        limit: Int = 100,
    ) = list(Query(condition, sort, limit = limit), maximumAge = 10.minutes, pullFrequency = 10.minutes)

    private fun ModelCache<LargeTestModel, Uuid>.quietItem(id: Uuid) =
        item(id, maximumAge = 10.minutes, pullFrequency = 10.minutes)

    // =========================================================================
    // What the server imposes, and what the client reproduces
    // =========================================================================

    /**
     * One mask entry, two fields: whichever of them a query sorts by, the server imposes the same
     * term.  This is the asymmetry the whole file rests on, asserted directly before anything is
     * built on top of it.
     */
    @Test fun eitherFieldOfATwoFieldMaskEntryNarrowsTheSameWay() = runTest2 {
        val (_, _, cache) = backgroundScope.fixture()
        val byString = cache.quietList(sort = sort { it.string.ascending() })
        val byLong = cache.quietList(sort = sort { it.long.ascending() })
        val releases = listOf(byString.addListener { }, byLong.addListener { })
        delay(1.seconds)

        // Only the two rows with `short gt 0`; the entry hides `string` and `long` from the rest.
        assertEquals(listOf(1, 2), byString.tags())
        assertEquals(listOf(1, 2), byLong.tags())

        releases.forEach { it() }
    }

    /** Sorting across two entries imposes both terms, so the answer is their intersection. */
    @Test fun sortingByFieldsFromTwoMaskEntriesImposesBothTerms() = runTest2 {
        val (_, _, cache) = backgroundScope.fixture()
        val both = cache.quietList(sort = sort { it.int.ascending(); it.long.ascending() })
        val release = both.addListener { }
        delay(1.seconds)

        // `boolean eq true` from the int entry, `short gt 0` from the other; only row 1 passes both.
        assertEquals(listOf(1), both.tags())

        release()
    }

    // =========================================================================
    // Reuse across sorts, which is what knowing the mask buys
    // =========================================================================

    /**
     * A sort picking up fewer mask terms knows every row a sort picking up more could return, so the
     * stricter one costs no request.  This is the whole point of fetching the permissions.
     */
    @Test fun aClaimWithFewerMaskTermsAnswersAQueryWithMore() = runTest2 {
        val (mock, _, cache) = backgroundScope.fixture()
        val fewer = cache.quietList(sort = sort { it.int.ascending() })
        val fewerRelease = fewer.addListener { }
        delay(1.seconds)
        assertEquals(listOf(1, 3), fewer.tags())
        mock.queries.clear()

        val more = cache.quietList(sort = sort { it.int.ascending(); it.long.ascending() })
        val moreRelease = more.addListener { }
        delay(1.seconds)

        assertEquals(listOf(1), more.tags())
        assertEquals(0, mock.queries.size, "every row this admits was already in the looser claim")

        moreRelease(); fewerRelease()
    }

    /** And not the other way: the stricter sort never saw the rows the looser one needs. */
    @Test fun aClaimWithMoreMaskTermsDoesNotAnswerAQueryWithFewer() = runTest2 {
        val (mock, _, cache) = backgroundScope.fixture()
        val moreRelease = cache.quietList(sort = sort { it.int.ascending(); it.long.ascending() }).addListener { }
        delay(1.seconds)
        mock.queries.clear()

        val fewer = cache.quietList(sort = sort { it.int.ascending() })
        val fewerRelease = fewer.addListener { }
        delay(1.seconds)

        assertEquals(1, mock.queries.size, "row 3 was hidden from the stricter sort, so it had to be fetched")
        assertEquals(listOf(1, 3), fewer.tags())

        fewerRelease(); moreRelease()
    }

    /**
     * Two sorts each picking up one term of a different entry cover different rows, so neither can
     * stand in for the other however alike they look.
     */
    @Test fun maskTermsOverDifferentFieldsDoNotCoverEachOther() = runTest2 {
        val (mock, _, cache) = backgroundScope.fixture()
        val byInt = cache.quietList(sort = sort { it.int.ascending() })
        val byIntRelease = byInt.addListener { }
        delay(1.seconds)
        assertEquals(listOf(1, 3), byInt.tags())
        mock.queries.clear()

        val byLong = cache.quietList(sort = sort { it.long.ascending() })
        val byLongRelease = byLong.addListener { }
        delay(1.seconds)

        assertEquals(1, mock.queries.size, "neither sort's rows are a superset of the other's")
        assertEquals(listOf(1, 2), byLong.tags())

        byLongRelease(); byIntRelease()
    }

    /**
     * A masked sort's claim is not useless to other sorts - it is a claim about
     * `condition AND the mask terms`, and a query asking for exactly those rows is answerable from
     * it in any order at all.  The naive rule "a masked list answers nothing else" would miss this;
     * carrying the terms in the condition is what lets it be seen.
     */
    @Test fun aQueryThatAsksForOnlyTheRowsAMaskedSortSawIsAnsweredByIt() = runTest2 {
        val (mock, _, cache) = backgroundScope.fixture(mask = Mask(listOf(hidesInt)))
        val maskedRelease = cache.quietList(sort = sort { it.int.ascending() }).addListener { }
        delay(1.seconds)
        mock.queries.clear()

        // Exactly the mask's own condition, in a different order entirely.
        val sameRows = cache.quietList(
            condition = condition { it.boolean eq true },
            sort = sort { it.short.ascending() },
        )
        val sameRowsRelease = sameRows.addListener { }
        delay(1.seconds)

        assertEquals(listOf(3, 1), sameRows.tags(), "the same rows, re-sorted by the query's own order")
        assertEquals(0, mock.queries.size, "these are the very rows the masked sort was shown")

        sameRowsRelease(); maskedRelease()
    }

    /**
     * The failure that must never happen: a claim earned under a mask being spent on a query the
     * mask did not restrict.  An ID lookup is the sharpest form of it, because an unbacked answer
     * there does not read as a short list - it reads as the row having been deleted.
     */
    @Test fun aMaskedSortNeverReportsARowItCannotSeeAsMissing() = runTest2 {
        val (mock, data, cache) = backgroundScope.fixture(mask = Mask(listOf(hidesInt)))
        val hidden = data.single { it.byte == 2.toByte() }
        val maskedRelease = cache.quietList(sort = sort { it.int.ascending() }).addListener { }
        delay(1.seconds)
        mock.queries.clear()

        val item = cache.quietItem(hidden._id)
        val itemRelease = item.addListener { }
        delay(1.seconds)

        assertEquals(1, mock.queries.size, "the masked list could not have known about this row")
        assertEquals(hidden._id, item.state.getOrNull()?._id, "it exists; it was only hidden from that sort")

        itemRelease(); maskedRelease()
    }

    /**
     * A masked list has to keep working past its first page, and paging is where the mask is easiest
     * to lose: the cursor query is a *different* condition, so if the server's rewrite were not
     * reproduced for it the page would come back from a different set of rows than the head did.
     */
    @Test fun pagingAMaskedSortContinuesFromTheLastRowItWasAllowedToSee() = runTest2 {
        val data = (1..8).map { LargeTestModel(byte = it.toByte(), int = it, short = 1, boolean = it % 2 == 1) }
        val (mock, _, cache) = backgroundScope.fixture(mask = Mask(listOf(hidesInt)), data = data)
        val ref = cache.quietList(sort = sort { it.int.ascending() }, limit = 2)
        val release = ref.addListener { }
        delay(1.seconds)
        assertEquals(listOf(1, 3), ref.tags(), "only the odd rows are visible to an int sort")
        mock.queries.clear()

        ref.limit(4)

        assertEquals(listOf(1, 3, 5, 7), ref.tags())
        assertEquals(1, mock.queries.size, "one page, not a re-read of the whole list")
        assertEquals(2, mock.queries.single().limit, "the page should ask only for the rows it lacks")

        release()
    }

    // =========================================================================
    // Knowledge gathered before the mask was known
    // =========================================================================

    /** Delivers the permissions late, so the cache spends a while not knowing what the server hides. */
    private open class SlowPermissionsMock(scope: CoroutineScope, val after: kotlin.time.Duration) :
        QueryRecordingMock(scope) {
        override suspend fun permissions(): ModelPermissions<LargeTestModel> {
            delay(after)
            return modelPermissions
        }
    }

    /**
     * A mask arriving after a list is already cached invalidates it, because every claim so far was
     * filed under a condition that assumed no mask.  Keeping them would mean answering masked queries
     * from unmasked evidence, which is the one direction that loses rows.
     *
     * Discarding it is not the same as going quiet, though: the reader still has a listener and now
     * has nothing to answer with, so it re-asks as the mask lands rather than waiting out the poll
     * interval it was part-way through.  A reader holding nothing while its own poll insists there
     * is nothing to fetch is the state this must never leave anyone in.
     */
    @Test fun aReadMaskArrivingLateDiscardsWhatWasCachedWithoutIt() = runTest2 {
        val mock = SlowPermissionsMock(this, 3.seconds)
        mock.modelPermissions = maskedPermissions(twoMasks)
        mock.data.putAll(rows().associateBy { it._id })
        val cache = ModelCache(mock, LargeTestModel.serializer(), scope = backgroundScope, log = testLog)

        val ref = cache.list(
            Query(Condition.Always, sort { it.short.ascending() }, limit = 100),
            maximumAge = 10.minutes,
            pullFrequency = 10.seconds,
        )
        val release = ref.addListener { }
        delay(1.seconds)
        assertEquals(4, ref.state.getOrNull()?.size, "sorting by an unmasked field sees everything")
        val queriesBeforeMask = mock.queries.size

        // The mask lands at 3s.  Well inside the 10s poll interval, so anything that happens here is
        // the mask being noticed, not the timer coming round.
        delay(3.seconds)

        assertTrue(
            mock.queries.size > queriesBeforeMask,
            "the mask landed, so what was known without it is no longer trusted - answering again cost a request"
        )
        assertEquals(
            4, ref.state.getOrNull()?.size,
            "and the reader asked as the mask landed rather than sitting empty until its poll"
        )

        release()
    }

    /**
     * But an empty mask changes nothing, so learning about it must not throw away a screenful of
     * data.  A server with no read mask is the common case, and it would be the one paying for this.
     */
    @Test fun anEmptyReadMaskLeavesWhatIsAlreadyCachedAlone() = runTest2 {
        val mock = SlowPermissionsMock(this, 3.seconds)
        mock.data.putAll(rows().associateBy { it._id })
        val cache = ModelCache(mock, LargeTestModel.serializer(), scope = backgroundScope, log = testLog)

        val ref = cache.list(
            Query(Condition.Always, sort { it.short.ascending() }, limit = 100),
            maximumAge = 10.minutes,
            pullFrequency = 10.minutes,
        )
        val release = ref.addListener { }
        delay(1.seconds)
        assertEquals(4, ref.state.getOrNull()?.size)
        val retrievedAt = assertNotNull(ref.lastUpdatedAt.state.getOrNull())
        val queriesSoFar = mock.queries.size

        delay(3.seconds)

        assertEquals(4, ref.state.getOrNull()?.size, "there is no mask, so nothing was assumed wrongly")
        assertEquals(retrievedAt, ref.lastUpdatedAt.state.getOrNull(), "and nothing had to be re-read")
        assertEquals(queriesSoFar, mock.queries.size)

        release()
    }

    /**
     * Permissions that never load leave the cache correct and merely less clever: without knowing the
     * masks it cannot tell which sorts the server narrowed, so it declines to reuse a claim across
     * sorts at all rather than guess.  The answers stay right; only the request count goes up.
     */
    @Test fun permissionsThatFailToLoadCostReuseRatherThanCorrectness() = runTest2 {
        val mock = object : QueryRecordingMock(this) {
            override suspend fun permissions(): ModelPermissions<LargeTestModel> =
                throw IllegalStateException("permissions endpoint is down")
        }
        mock.modelPermissions = maskedPermissions(Mask(listOf(hidesInt)))
        mock.data.putAll(rows().associateBy { it._id })
        val cache = ModelCache(mock, LargeTestModel.serializer(), scope = backgroundScope, log = testLog)

        val unmaskedRelease = cache.quietList(sort = sort { it.short.ascending() }).addListener { }
        delay(1.seconds)
        mock.queries.clear()

        val masked = cache.quietList(sort = sort { it.int.ascending() })
        val maskedRelease = masked.addListener { }
        delay(1.seconds)

        assertEquals(1, mock.queries.size, "with the masks unknown, a different sort is a different question")
        assertEquals(listOf(1, 3), masked.tags(), "and the answer is still exactly what the server returns")

        maskedRelease(); unmaskedRelease()
    }

    // =========================================================================
    // Sockets, which know nothing about anybody's sort
    // =========================================================================

    /**
     * A socket is subscribed to a condition, not to a sort, so it delivers rows a masked list is not
     * allowed to see.  Those rows are perfectly good knowledge - they just do not belong in that list.
     */
    @Test fun aSocketUpdateAboutAnInvisibleRowDoesNotJoinAMaskedList() = runTest2 {
        val mock = SocketMock(this)
        mock.modelPermissions = maskedPermissions(Mask(listOf(hidesInt)))
        val data = rows()
        mock.data.putAll(data.associateBy { it._id })
        val cache = ModelCache(mock, LargeTestModel.serializer(), scope = backgroundScope, log = testLog)

        val masked = cache.quietList(sort = sort { it.int.ascending() })
        // Subscribes, so the socket starts delivering changes to rows the masked list cannot see.
        val subscribed = cache.list(
            Query(condition { it.short gt 0 }, sort { it.short.ascending() }, limit = 100),
            pullFrequency = 10.seconds,
        )
        val releases = listOf(masked.addListener { }, subscribed.addListener { })
        delay(7.seconds)
        assertEquals(listOf(1, 3), masked.tags())

        val invisible = data.single { it.byte == 2.toByte() }
        mock.modify(invisible._id, modification { it.string assign "changed" })
        delay(1.seconds)

        assertTrue(subscribed.state.getOrNull()?.any { it._id == invisible._id } == true, "the socket delivered it")
        assertEquals(listOf(1, 3), masked.tags(), "but an int sort still cannot see that row")

        releases.forEach { it() }
    }

    /**
     * A socket removal means "this row left the condition I subscribed to", which says nothing about
     * a list the row was never in.  Reading it as news about that list would cost it its claim, and
     * a claim lost is a screen blanked.
     */
    @Test fun aSocketRemovalOfAnInvisibleRowDoesNotCostAMaskedListItsClaim() = runTest2 {
        val mock = SocketMock(this)
        mock.modelPermissions = maskedPermissions(Mask(listOf(hidesInt)))
        val data = rows()
        mock.data.putAll(data.associateBy { it._id })
        val cache = ModelCache(mock, LargeTestModel.serializer(), scope = backgroundScope, log = testLog)

        val masked = cache.quietList(sort = sort { it.int.ascending() })
        val subscribed = cache.list(
            Query(condition { it.short gt 0 }, sort { it.short.ascending() }, limit = 100),
            pullFrequency = 10.seconds,
        )
        val releases = listOf(masked.addListener { }, subscribed.addListener { })
        delay(7.seconds)
        assertEquals(listOf(1, 3), masked.tags())
        val retrievedAt = assertNotNull(masked.lastUpdatedAt.state.getOrNull())

        // Moves out of the subscribed condition; the server reports that as a removal.
        val invisible = data.single { it.byte == 2.toByte() }
        mock.modify(invisible._id, modification { it.short assign 0 })
        delay(1.seconds)

        assertEquals(listOf(1, 3), masked.tags(), "the row was never in this list to begin with")
        assertEquals(retrievedAt, masked.lastUpdatedAt.state.getOrNull(), "so nothing here had to be re-read")

        releases.forEach { it() }
    }

    /**
     * The counterpart, which is what stops the test above from passing for the wrong reason.  A
     * removal naming a row the masked list *does* hold says something it cannot check - the row may
     * have been deleted, or merely have left somebody else's filter - so that claim has to go and be
     * re-earned.  Here it turns out the row still exists, and re-reading is how the list finds out.
     */
    @Test fun aSocketRemovalOfAVisibleRowCostsAMaskedListItsClaim() = runTest2 {
        val mock = SocketMock(this)
        mock.modelPermissions = maskedPermissions(Mask(listOf(hidesInt)))
        val data = rows()
        mock.data.putAll(data.associateBy { it._id })
        val cache = ModelCache(mock, LargeTestModel.serializer(), scope = backgroundScope, log = testLog)

        val masked = cache.quietList(sort = sort { it.int.ascending() })
        val subscribed = cache.list(
            Query(condition { it.short gt 0 }, sort { it.short.ascending() }, limit = 100),
            pullFrequency = 10.seconds,
        )
        val releases = listOf(masked.addListener { }, subscribed.addListener { })
        delay(7.seconds)
        assertEquals(listOf(1, 3), masked.tags())
        val retrievedAt = assertNotNull(masked.lastUpdatedAt.state.getOrNull())

        // Row 1 is visible to the int sort and inside the subscription; take it out of the latter.
        val visible = data.single { it.byte == 1.toByte() }
        mock.modify(visible._id, modification { it.short assign 0 })
        delay(1.seconds)

        assertNotEquals(retrievedAt, masked.lastUpdatedAt.state.getOrNull(), "this one had to be re-read")
        assertEquals(listOf(1, 3), masked.tags(), "and the row was still there all along")

        releases.forEach { it() }
    }

    // =========================================================================
    // Values, as opposed to rows
    // =========================================================================

    /**
     * A mask hides values as well as rows, and there is one copy of each row in the store, so what a
     * list settles about a row is what every other reader of it gets.  The row here came back with
     * its `int` blanked; a lookup of it must report that, not the value in the database, and must not
     * go and ask again in the hope of a different answer.
     */
    @Test fun aRowLookedUpAfterAListReportsTheValueTheServerSent() = runTest2 {
        val (mock, data, cache) = backgroundScope.fixture(mask = Mask(listOf(hidesInt)))
        val hidden = data.single { it.byte == 2.toByte() }
        val listRelease = cache.quietList(sort = sort { it.short.ascending() }).addListener { }
        delay(1.seconds)
        mock.queries.clear()

        val item = cache.quietItem(hidden._id)
        val itemRelease = item.addListener { }
        delay(1.seconds)

        assertEquals(0, mock.queries.size, "the list already named this row's value exactly")
        assertEquals(0, item.state.getOrNull()?.int, "and that value is the masked one, not the stored one")

        itemRelease(); listRelease()
    }

    /**
     * The same for a list that *is* narrowed: being shown only some of the rows is no reason to
     * doubt the ones it was shown, so those still settle their own lookups.
     */
    @Test fun aMaskedSortsOwnRowsStillSettleTheirLookups() = runTest2 {
        val (mock, data, cache) = backgroundScope.fixture(mask = Mask(listOf(hidesInt)))
        val visible = data.single { it.byte == 3.toByte() }
        val listRelease = cache.quietList(sort = sort { it.int.ascending() }).addListener { }
        delay(1.seconds)
        mock.queries.clear()

        val item = cache.quietItem(visible._id)
        val itemRelease = item.addListener { }
        delay(1.seconds)

        assertEquals(0, mock.queries.size, "this row was in the masked list, with the value it has")
        assertEquals(visible, item.state.getOrNull())

        itemRelease(); listRelease()
    }

    /**
     * A masked sort's answer omits rows it is not allowed to see, which is not evidence that those
     * rows are gone.  A list that *can* see them must survive it - this is the reconciliation the
     * effective condition exists to keep honest.
     */
    @Test fun aMaskedSortDoesNotStripRowsAnUnmaskedListHolds() = runTest2 {
        val (mock, _, cache) = backgroundScope.fixture(mask = Mask(listOf(hidesInt)))
        // Fills its limit exactly, so its claim is bounded and cannot simply answer the masked list;
        // the masked query has to actually be sent for this to test anything.
        val unmasked = cache.quietList(sort = sort { it.short.ascending() }, limit = 4)
        val unmaskedRelease = unmasked.addListener { }
        delay(1.seconds)
        assertEquals(4, unmasked.state.getOrNull()?.size)
        mock.queries.clear()

        val masked = cache.quietList(sort = sort { it.int.ascending() })
        val maskedRelease = masked.addListener { }
        delay(1.seconds)

        assertTrue(mock.queries.isNotEmpty(), "the masked query has to actually be sent for this to test anything")
        assertEquals(listOf(1, 3), masked.tags())
        assertEquals(4, unmasked.state.getOrNull()?.size, "the hidden rows are hidden, not deleted")

        maskedRelease(); unmaskedRelease()
    }
}
