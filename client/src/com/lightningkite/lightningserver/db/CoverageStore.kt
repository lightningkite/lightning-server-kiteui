package com.lightningkite.lightningserver.db

import com.lightningkite.reactive.core.BasicListenable
import com.lightningkite.services.database.Condition
import com.lightningkite.services.database.DataClassPathPartial
import com.lightningkite.services.database.HasId
import com.lightningkite.services.database.Mask
import com.lightningkite.services.database.Query
import com.lightningkite.services.database.SortPart
import com.lightningkite.services.database._id
import com.lightningkite.services.database.comparator
import kotlinx.serialization.KSerializer
import kotlin.time.Instant

/**
 * Everything a client knows about one collection: the items it has seen, plus timestamped claims
 * about which stretches of which queries it knows *completely*.
 *
 * ## Why completeness is the thing being tracked
 *
 * Holding items is easy; the hard question a cache has to answer is "is this list all of them?".
 * A store of items alone cannot say, so it either refetches constantly or quietly serves short
 * lists - the worst thing this library can do.  So knowledge here is two parts: [items], the values,
 * and [claims], statements of the form *"for this condition in this order I know every match from
 * the start of the order through here, as of then"*.
 *
 * A read is answered locally only when a claim backs it.  Everything else - polling, staleness,
 * sockets - is the caller's business; see [ModelCache].
 *
 * ## Where claims come from
 *
 * Almost all of them come from one inference: **a query that returns fewer rows than its limit has
 * run off the end of its results**, so it knows the condition completely.  A query that fills its
 * limit only knows as far as its last row.  That single rule covers query results, pagination and
 * ID lookups alike - an ID lookup is just a query whose extent holds at most one row.
 *
 * ## Where claims go
 *
 * A claim survives any change we *watched happen*: our own inserts, modifications and deletions, and
 * socket messages we received.  Those we apply, so the claim stays true.  It is only threatened by
 * changes we might have *missed*, which is a matter of time passing without a live socket, and so is
 * judged by the reader from [Answer.at] rather than tracked here.
 *
 * The exceptions are the two places where something proves a value we hold is wrong without telling
 * us what is right: a query that omits a row we thought belonged in its range, and a socket removal.
 * Both drop the claims that relied on that value, because a claim missing a row is exactly the
 * failure this must never have.  See [reconcile] and [socketDelta].
 *
 * ## Not thread safe
 *
 * Like the rest of the reactive system, this expects to be touched from one thread.
 */
public class CoverageStore<T : HasId<ID>, ID : Comparable<ID>>(
    serializer: KSerializer<T>,
    /**
     * Reads a row's version, for models that carry one.  See [alreadyKnowBetter].
     *
     * A version is any value that only ever increases as a row changes.  Where two accounts of the
     * same row disagree, the higher version is the later one - which a timestamp can only guess at,
     * since two changes inside a clock tick are indistinguishable and the client's clock is its own.
     *
     * Return null for a row whose version is unknown, and leave this null entirely for a model that
     * has none; both fall back to timestamps, which is the behaviour every model had before.
     */
    private val versionOf: ((T) -> Long?)? = null,
) {
    private val idProp = serializer._id()

    /** Every item we have been told about, by ID.  Shared by every query - there is only one copy. */
    private val items = HashMap<ID, T>()

    /**
     * What a claim is about.
     *
     * Order is part of the identity, not decoration.  The server adds the sort's read mask to the
     * effective condition, so sorting by a masked field silently excludes rows; a list retrieved in
     * one order is therefore not generally evidence about the same condition in another.  See
     * [orderCovers] for the one case where it is.
     */
    private data class Extent<T>(val condition: Condition<T>, val order: List<SortPart<T>>)

    /**
     * "I know every match of this extent from the start of the order through [through], as of [at]."
     *
     * A null [through] reaches the end of the extent: complete knowledge of the condition.  [through]
     * is the row as it looked when the claim was made, and is used only as a position in the order,
     * so it stays a valid boundary even if that row is later changed or deleted.
     */
    private class Claim<T>(val through: T?, val at: Instant)

    private val claims = HashMap<Extent<T>, Claim<T>>()

    /** Fires whenever anything here changes. */
    public val updates: BasicListenable = BasicListenable()

    /** The total order every one-item query is filed under; see [idQuery]. */
    private val idOrder: List<SortPart<T>> = listOf<SortPart<T>>().ensureTotal(serializer)

    /**
     * The query that asks for exactly one item.
     *
     * Looking an item up by ID is not a separate kind of read - it is this query, and it earns and
     * spends claims like any other.  Building it in one place keeps every producer and consumer of
     * one-item knowledge filed under the same extent.
     */
    public fun idQuery(id: ID): Query<T> = Query(Condition.OnField(idProp, Condition.Equal(id)), idOrder, limit = 1)

    private fun idExtent(id: ID): Extent<T> = Extent(Condition.OnField(idProp, Condition.Equal(id)), idOrder)

    /**
     * The one ID [condition] can match, when it is an `_id` equality - the shape [idQuery] makes.
     *
     * Item lookups are the most common read there is: a screen showing fifty referenced rows makes
     * fifty of them, and re-answers all fifty every time anything in the collection changes.  Reading
     * them the general way would scan the whole collection to find one row by its key, so recognising
     * the shape is what keeps a screenful of references from costing a screenful of scans.  Failing to
     * recognise it costs only speed, so an unfamiliar spelling of the same condition is safe.
     */
    @Suppress("UNCHECKED_CAST")
    private fun singleId(condition: Condition<T>): ID? = (condition as? Condition.OnField<T, *>)
        ?.takeIf { it.key == idProp }
        ?.let { (it.condition as? Condition.Equal<*>)?.value as ID? }

    private fun Query<T>.extent(): Extent<T> = Extent(condition, orderBy)

    // =========================================================================
    // What the server actually filtered by
    // =========================================================================

    /**
     * The read mask the server applies, once [useMasking] has been told what it is.
     *
     * ## Why this is here at all
     *
     * A query is not run as asked.  The server ANDs its own terms into the condition - notably the
     * mask entries covering any field being *sorted on*, since sorting by a field a caller may not
     * read would leak it by position.  So a list is evidence about `condition AND those terms`, and
     * two lists differing only in sort are evidence about two different conditions.
     *
     * Knowing the mask lets that be computed rather than guessed, which is what makes a complete
     * claim re-sortable: with the terms carried in the condition, [implies] compares the real
     * effective conditions and the order stops being special.
     *
     * ## This is a stand-in
     *
     * The server knows exactly what it ran; deriving it here is a copy of that reasoning, and a copy
     * that will drift the day the server ANDs in a term this does not model.  It is deliberately
     * confined to [effective] so that a server which reports the condition it used can replace it
     * without touching anything else.  Until then, null means "not known", and everything stays on
     * the conservative path that assumes nothing about masks.
     */
    private var masking: Mask<T>? = null

    /** Memoised because [backing] asks per claim, while the orders in play are few. */
    private val permitSortCache = HashMap<List<SortPart<T>>, Condition<T>>()

    /**
     * Supplies the read mask, so effective conditions can be computed exactly.  See [masking].
     *
     * Claims made before this was known were filed under conditions that assumed no mask.  When
     * there is no mask that assumption was right and they all stand; otherwise they are discarded,
     * which is a one-off at startup rather than anything ongoing.
     */
    public fun useMasking(mask: Mask<T>) {
        if (masking == mask) return
        masking = mask
        permitSortCache.clear()
        if (mask.pairs.isNotEmpty()) invalidate() else updates.invokeAll()
    }

    /**
     * The condition the server really applies to this extent.
     *
     * Terms the server adds identically to every query - `permissions.read`, say - are deliberately
     * *not* modelled: they appear on both sides of every comparison [implies] makes, so they cancel,
     * and leaving them out keeps the extents callers see equal to the ones they asked for.
     */
    private fun Extent<T>.effective(): Condition<T> {
        val mask = masking ?: return condition
        val imposed = permitSortCache.getOrPut(order) { mask.permitSort(order) }
        // Never wrap when nothing is imposed: `And([c, Always])` is a different key from `c`, and
        // that would cost every exact-match lookup in the class.
        return if (imposed is Condition.Always) condition else Condition.And(listOf(condition, imposed))
    }

    /** The fields a sort reads, which is all the read mask cares about.  See [orderCovers]. */
    private fun sortFields(order: List<SortPart<T>>): Set<DataClassPathPartial<T>> =
        order.mapTo(HashSet()) { it.field }

    /**
     * Whether a claim in [claimOrder] can answer a query in [wantedOrder].
     *
     * A *bounded* claim needs the very same order: [Claim.through] is a position in it, and names a
     * different set of rows in any other.
     *
     * A complete claim has no boundary to reinterpret - it holds every match, and [known] sorts by
     * the query's own order - so all the sort still decides is which mask terms the server imposed.
     * Once [masking] is known those terms are in the condition and [implies] weighs them, so any
     * order will do.  Until then, the most that can be said without seeing the masks is that a sort
     * reading a subset of another's fields can only have picked up a subset of its mask terms.
     */
    private fun orderCovers(claimOrder: List<SortPart<T>>, claim: Claim<T>, wantedOrder: List<SortPart<T>>): Boolean =
        claimOrder == wantedOrder || (claim.through == null && (
                masking != null || sortFields(wantedOrder).containsAll(sortFields(claimOrder))
                ))

    /**
     * The claim that answers [query], which may have been earned by a broader one.
     *
     * Knowing every match of a condition through a row means knowing every match of anything
     * stricter through that same row, so a claim subsumes any query its condition implies and whose
     * order it covers.  See [implies] and [orderCovers].
     *
     * An exact match always wins, so the common path stays a single lookup.  Failing that, complete
     * knowledge beats bounded, and newer beats older; the alternatives would only have cost a fetch,
     * so this picks the one that most often avoids one.
     */
    private fun backing(query: Query<T>): Claim<T>? {
        val extent = query.extent()
        claims[extent]?.let { return it }
        // Every row we hold carries a one-row claim, so most of [claims] is one-row claims and a
        // search that considered them all would cost a pass over the collection per miss.  Only a
        // query pinning an ID can be answered by one, and as with [singleId], failing to recognise
        // an unfamiliar spelling of that costs a fetch and nothing else.
        val wantsOneRow = singleId(extent.condition) != null
        val wanted = extent.effective()
        return claims.entries
            .filter { (e, claim) ->
                (wantsOneRow || singleId(e.condition) == null) &&
                        orderCovers(e.order, claim, extent.order) &&
                        implies(wanted, e.effective())
            }
            .maxWithOrNull(compareBy({ if (it.value.through == null) 1 else 0 }, { it.value.at }))
            ?.value
    }

    /**
     * Whether [query] is one this store can hold a claim about at all.
     *
     * Every claim here is "shorter than the limit means the end", so a limit of zero or less - which
     * asks a `Database` for nothing at all rather than for everything - would make that read either
     * meaningless or, worse, a claim of complete knowledge on the strength of an empty answer.
     *
     * A skipped query says nothing about the rows before it, so it cannot support the only claim
     * shape here.  [Extent] does not carry skip, so without this a skipped query would quietly be
     * filed under - and answered from - the unskipped one.
     */
    private fun requireCacheable(query: Query<T>) {
        require(query.limit > 0) { "A limit of ${query.limit} asks the server for nothing." }
        require(query.skip == 0) { "A query skipping ${query.skip} rows says nothing about the rows before them." }
    }

    /** What [known] found. */
    public class Answer<T>(
        /** The rows the query returns, as far as knowledge reaches. */
        public val items: List<T>,
        /** When that knowledge was obtained. */
        public val at: Instant,
        /**
         * Whether [items] stops at the edge of what is known rather than at the query's limit, so
         * more has to be retrieved before it is the whole answer.
         */
        public val partial: Boolean,
    )

    /**
     * What is known for [query], or null if no claim backs it and it has to be fetched.
     *
     * A claim earned by a broader condition answers this one too; see [backing].  The filtering and
     * truncation below are done with *[query]'s* own effective condition, so a broad claim's rows
     * are narrowed to this query before they are counted, and [Answer.partial] still measures this
     * query's knowledge rather than the claim's.
     */
    public fun known(query: Query<T>): Answer<T>? {
        requireCacheable(query)
        val extent = query.extent()
        val claim = backing(query) ?: return null
        val effective = extent.effective()
        if (claim.through == null) singleId(query.condition)?.let { id ->
            // Complete knowledge of an extent holding at most one row: nothing to sort or cut short.
            val row = items[id]?.takeIf { effective(it) }
            return Answer(items = listOfNotNull(row), at = claim.at, partial = false)
        }
        val comparator = query.orderBy.comparator!!
        val matching = items.values.filter { effective(it) }.sortedWith(comparator)
        val reached = claim.through
            ?.let { through -> matching.takeWhile { comparator.compare(it, through) <= 0 } }
            ?: matching
        return Answer(
            items = reached.take(query.limit),
            at = claim.at,
            partial = claim.through != null && reached.size < query.limit,
        )
    }

    /** How far knowledge of [query] reaches, or null if it reaches the end.  See [backing]. */
    public fun boundary(query: Query<T>): T? = backing(query)?.through

    /** Everything held, for local optimistic updates.  A copy, so callers may update while iterating. */
    public fun cachedItems(): List<T> = items.values.toList()

    /**
     * Records the server's answer to [query], sent at [at].
     *
     * The timestamp is taken when the request goes out rather than when it comes back, so a fetch
     * that raced a change is never treated as authoritative for longer than it was in flight.
     *
     * This replaces the extent's claim outright rather than merging, so a small-limit answer to an
     * extent a larger-limit reader also watches shortens what that reader sees until it refetches.
     * Merging instead would mean keeping the longer reach with the older timestamp, which would stop
     * the smaller reader's refresh from ever counting as a refresh.  The two readers each fetch once
     * per staleness window either way; only the order is different.
     *
     * @param reachedEnd what the server said about running out of results, when it says so directly;
     *   null to infer it from the answer being shorter than the limit, which is all a plain REST
     *   query lets us do.  See [reachedEndOf].
     */
    public fun queried(query: Query<T>, result: List<T>, at: Instant, reachedEnd: Boolean? = null) {
        requireCacheable(query)
        val extent = query.extent()
        val through = if (reachedEndOf(result, query.limit, reachedEnd)) null else result.lastOrNull()
        reconcile(extent.effective(), query.orderBy, after = null, through = through, returned = result, at = at)
        result.forEach { record(it, at) }
        claims[extent] = Claim(through, at)
        updates.invokeAll()
    }

    /**
     * Whether an answer ran off the end of its results.
     *
     * The inference - *fewer rows than asked for means there were no more* - is the only thing a
     * plain REST query supports, and it is why a query that exactly fills its limit has to settle for
     * a boundary even when the server knew it was done.  [declared] is the way out: a server that
     * reports totality directly is believed, and this stops guessing.
     */
    private fun reachedEndOf(result: List<T>, limit: Int, declared: Boolean?): Boolean =
        declared ?: (result.size < limit)

    /**
     * Records a page fetched with a cursor - `condition AND orderBy.after(after)` - extending the
     * claim for [query]'s extent rather than replacing it.
     *
     * The head of the list was not re-read, so the extended claim is only as fresh as its oldest
     * part.  Timestamping it now instead would let a reader page forever while the rows at the top
     * of the list quietly went stale.
     *
     * @param pageLimit how many rows the page asked for, which is what decides whether it ran out
     * @param reachedEnd as in [queried]
     */
    public fun paged(
        query: Query<T>,
        after: T,
        result: List<T>,
        pageLimit: Int,
        at: Instant,
        reachedEnd: Boolean? = null,
    ) {
        requireCacheable(query)
        require(pageLimit > 0) { "A page of $pageLimit rows asks the server for nothing." }
        // The resulting claim reaches from the start of the order, which is only true if something
        // already covered the head.  Paging into an extent nothing has read would assert knowledge
        // of rows that were never fetched.  A broader claim counts - it covered the head too - and
        // the page's own claim then inherits its age, since that is how old the head is.
        val previous = requireNotNull(backing(query)) { "Nothing covers the rows before this page." }
        val extent = query.extent()
        val through = if (reachedEndOf(result, pageLimit, reachedEnd)) null else result.lastOrNull()
        reconcile(extent.effective(), query.orderBy, after = after, through = through, returned = result, at = at)
        result.forEach { record(it, at) }
        claims[extent] = Claim(through, minOf(previous.at, at))
        updates.invokeAll()
    }

    /**
     * Records an exact lookup by ID: [found] exist with these values, [missing] do not exist.
     *
     * A one-item extent holds at most one row, so either answer is complete knowledge of it - which
     * is why nothing here needs to track "missing" separately from "never asked".
     */
    public fun identified(found: List<T>, missing: Set<ID>, at: Instant) {
        found.forEach { record(it, at) }
        missing.forEach { gone(it, at) }
        updates.invokeAll()
    }

    /** Records items we changed ourselves.  Every claim survives - we watched them change. */
    public fun mutated(changed: Collection<T>, at: Instant) {
        changed.forEach { record(it, at) }
        updates.invokeAll()
    }

    /**
     * Records items we deleted ourselves.  Every claim survives - we watched them go - so a limited
     * list simply comes up a row short, and its reader fetches the row that moved up to fill it.
     */
    public fun deleted(ids: Set<ID>, at: Instant) {
        ids.forEach { gone(it, at) }
        updates.invokeAll()
    }

    /**
     * Applies a socket delta received at [at].
     *
     * [changed] is ordinary observed change and threatens nothing.  [removed] is not a deletion:
     * server-side it means "this item's new value no longer matches what you subscribed to", and the
     * item may well still exist.  For a subscription the socket is actively serving that is the
     * whole truth about the removed row, so those claims survive; for anything else it says nothing
     * about whether the item still exists or still matches, so those claims go.  Reading a removal
     * as a deletion is the easiest way to corrupt data here.
     *
     * @param live whether the socket has been delivering every change to a condition since a moment,
     *   which has to be asked at the time rather than remembered, since sockets close asynchronously
     */
    public fun socketDelta(
        changed: Set<T>,
        removed: Set<ID>,
        at: Instant,
        live: (condition: Condition<T>, since: Instant) -> Boolean,
    ) {
        changed.forEach { record(it, at) }
        removed.forEach { id ->
            val old = items.remove(id) ?: return@forEach
            // Never left behind as "complete, and the item is not in it", which would report the
            // item as deleted on the strength of it having left somebody else's filter.
            claims.remove(idExtent(id))
            claims.entries.removeAll { (extent, claim) ->
                extent.effective()(old) && !live(extent.condition, claim.at)
            }
        }
        updates.invokeAll()
    }

    /**
     * Forgets everything.
     *
     * For when the socket admits it fell behind, and after a bulk modification, where the server does
     * not say which rows it touched.  Intentionally aggressive: nothing here can be trusted, and
     * serving stale data is worse than fetching again.
     */
    public fun invalidate() {
        items.clear()
        claims.clear()
        updates.invokeAll()
    }

    /** Forgets what is known about one ID, so the next read of it fetches. */
    public fun invalidate(id: ID) {
        claims.remove(idExtent(id))
        updates.invokeAll()
    }

    /**
     * Stores [item]'s current value.  Whoever handed it over knew it exactly, so this settles the
     * one-item query for its ID as well.
     *
     * Unless we already know better - an answer assembled before a change we have since been told
     * about is stale about that row, and writing it would undo the change.
     */
    private fun record(item: T, at: Instant) {
        if (alreadyKnowBetter(item._id, at, item)) return
        items[item._id] = item
        claims[idExtent(item._id)] = Claim(through = null, at = at)
    }

    /** [id] no longer exists as of [at], unless something already knew better. */
    private fun gone(id: ID, at: Instant) {
        if (alreadyKnowBetter(id, at, incoming = null)) return
        items.remove(id)
        claims[idExtent(id)] = Claim(through = null, at = at)
    }

    /**
     * Whether what we hold about [id] already accounts for something at least as new as [incoming],
     * dated [at] - which makes that offering old news.
     *
     * ## The one place precedence is decided
     *
     * Every question of the form *"this and that both claim to describe row X - which wins?"* comes
     * through here: [record] before overwriting a value, [gone] before forgetting one, and
     * [reconcile] before treating a row's absence from an answer as proof it is gone.
     *
     * ## By version, when there is one
     *
     * [versionOf] answers the question outright: the higher version is the later account, whatever
     * the clocks said.  It needs the row being offered, which is why [incoming] is a parameter - and
     * why the two callers that have no row to offer cannot use it.
     *
     * A deletion carries nothing to read a version from, and neither does a row's *absence* from a
     * query's results, so both fall back to the timestamp below.  That is a real limit rather than an
     * oversight: a version says which of two accounts is later, and "there is no account" has no
     * version to compare.  A model wanting deletions ordered by version would have to keep tombstones
     * so that a deletion is a row like any other.
     *
     * ## By timestamp otherwise
     *
     * Every [record] leaves the moment it happened on the row's one-item claim, so this reads
     * something already kept rather than a second set of timestamps.  What it bounds is the race a
     * request-time timestamp cannot avoid: an answer assembled from a snapshot taken before a change
     * that landed while it was in flight.  Two changes inside one clock tick are indistinguishable
     * here, which is exactly what a version fixes.
     */
    private fun alreadyKnowBetter(id: ID, at: Instant, incoming: T?): Boolean {
        val versionOf = versionOf
        if (versionOf != null && incoming != null) {
            val held = items[id]?.let(versionOf)
            val offered = versionOf(incoming)
            // Only when both are known; a row that has not got one yet is judged the old way.
            if (held != null && offered != null) return held > offered
        }
        return claims[idExtent(id)]?.at?.let { it > at } == true
    }

    /** Whether every row matching [narrow] also matches [broad].  See the file-level `implies`. */
    private fun implies(narrow: Condition<T>, broad: Condition<T>): Boolean =
        impliesCondition(narrow, broad)

    /**
     * Drops what the server's answer contradicts.
     *
     * The server just listed every row of [condition] in a stretch of [order]; anything we hold that
     * belongs in that stretch but is missing from [returned] is a stale copy.  It goes, and so does
     * every claim it was part of - those claims would otherwise go on serving a list containing a
     * row the server says is not there.
     *
     * Rows we have heard about since the answer was requested are left alone; see [alreadyKnowBetter].
     * Without that, inserting a row while a poll was in flight would make it vanish again.
     */
    private fun reconcile(
        condition: Condition<T>,
        order: List<SortPart<T>>,
        after: T?,
        through: T?,
        returned: List<T>,
        at: Instant,
    ) {
        val comparator = order.comparator!!
        val returnedIds = returned.mapTo(HashSet()) { it._id }
        items.values.filter {
            it._id !in returnedIds && condition(it) && !alreadyKnowBetter(it._id, at, incoming = null) &&
                    (after == null || comparator.compare(it, after) > 0) &&
                    (through == null || comparator.compare(it, through) <= 0)
        }.forEach { stale ->
            items.remove(stale._id)
            claims.keys.removeAll { it.effective()(stale) }
        }
    }
}
