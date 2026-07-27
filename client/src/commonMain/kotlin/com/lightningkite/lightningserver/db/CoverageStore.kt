package com.lightningkite.lightningserver.db

import com.lightningkite.reactive.core.BasicListenable
import com.lightningkite.services.database.Condition
import com.lightningkite.services.database.HasId
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
public class CoverageStore<T : HasId<ID>, ID : Comparable<ID>>(serializer: KSerializer<T>) {
    private val idProp = serializer._id()

    /** Every item we have been told about, by ID.  Shared by every query - there is only one copy. */
    private val items = HashMap<ID, T>()

    /**
     * What a claim is about.
     *
     * Order is part of the identity, not decoration.  The server adds the sort's read mask to the
     * effective condition, so sorting by a masked field silently excludes rows; a list retrieved in
     * one order is therefore not evidence about the same condition in another, and re-sorting it
     * locally would invent one.
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

    /**
     * Every claim here is "shorter than the limit means the end", so a limit of zero or less - which
     * asks a `Database` for nothing at all rather than for everything - would make that read either
     * meaningless or, worse, a claim of complete knowledge on the strength of an empty answer.
     */
    private fun requirePositiveLimit(query: Query<T>) {
        require(query.limit > 0) { "A limit of ${query.limit} asks the server for nothing." }
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
     * Only exact extent matches count.  Answering a narrow query from a broad claim is possible in
     * principle but undecidable in general, and an implementation that guesses too permissively
     * serves incomplete lists silently.
     */
    public fun known(query: Query<T>): Answer<T>? {
        requirePositiveLimit(query)
        val claim = claims[query.extent()] ?: return null
        if (claim.through == null) singleId(query.condition)?.let { id ->
            // Complete knowledge of an extent holding at most one row: nothing to sort or cut short.
            return Answer(items = listOfNotNull(items[id]), at = claim.at, partial = false)
        }
        val comparator = query.orderBy.comparator!!
        val matching = items.values.filter { query.condition(it) }.sortedWith(comparator)
        val reached = claim.through
            ?.let { through -> matching.takeWhile { comparator.compare(it, through) <= 0 } }
            ?: matching
        return Answer(
            items = reached.take(query.limit),
            at = claim.at,
            partial = claim.through != null && reached.size < query.limit,
        )
    }

    /** How far knowledge of [query]'s extent reaches, or null if it reaches the end. */
    public fun boundary(query: Query<T>): T? = claims[query.extent()]?.through

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
     */
    public fun queried(query: Query<T>, result: List<T>, at: Instant) {
        requirePositiveLimit(query)
        val through = if (result.size < query.limit) null else result.lastOrNull()
        reconcile(query.condition, query.orderBy, after = null, through = through, returned = result, at = at)
        result.forEach { record(it, at) }
        claims[query.extent()] = Claim(through, at)
        updates.invokeAll()
    }

    /**
     * Records a page fetched with a cursor - `condition AND orderBy.after(after)` - extending the
     * claim for [query]'s extent rather than replacing it.
     *
     * The head of the list was not re-read, so the extended claim is only as fresh as its oldest
     * part.  Timestamping it now instead would let a reader page forever while the rows at the top
     * of the list quietly went stale.
     *
     * @param pageLimit how many rows the page asked for, which is what decides whether it ran out
     */
    public fun paged(query: Query<T>, after: T, result: List<T>, pageLimit: Int, at: Instant) {
        require(pageLimit > 0) { "A page of $pageLimit rows asks the server for nothing." }
        val previous = claims[query.extent()]
        val through = if (result.size < pageLimit) null else result.lastOrNull()
        reconcile(query.condition, query.orderBy, after = after, through = through, returned = result, at = at)
        result.forEach { record(it, at) }
        claims[query.extent()] = Claim(through, minOf(previous?.at ?: at, at))
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
                extent.condition(old) && !live(extent.condition, claim.at)
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
     */
    private fun record(item: T, at: Instant) {
        items[item._id] = item
        claims[idExtent(item._id)] = Claim(through = null, at = at)
    }

    /** [id] no longer exists as of [at], unless something already knew better. */
    private fun gone(id: ID, at: Instant) {
        if (outdatedBy(id, at)) return
        items.remove(id)
        claims[idExtent(id)] = Claim(through = null, at = at)
    }

    /**
     * Whether we learned this row's value after [at], which makes anything dated [at] older news
     * about it.
     *
     * Every [record] leaves the moment it happened on the row's one-item claim, so this is a read of
     * something already kept rather than a second set of timestamps.  It bounds the one race left
     * here - a fetch answered from a snapshot taken before a change that landed while it was in
     * flight - to rows we have *not* heard about since, which is where it has to stay: fixing it
     * properly would mean timestamping every value, and that is the bookkeeping this design exists
     * to remove.
     */
    private fun outdatedBy(id: ID, at: Instant): Boolean = claims[idExtent(id)]?.at?.let { it > at } == true

    /**
     * Drops what the server's answer contradicts.
     *
     * The server just listed every row of [condition] in a stretch of [order]; anything we hold that
     * belongs in that stretch but is missing from [returned] is a stale copy.  It goes, and so does
     * every claim it was part of - those claims would otherwise go on serving a list containing a
     * row the server says is not there.
     *
     * Rows we have heard about since the answer was requested are left alone; see [outdatedBy].
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
            it._id !in returnedIds && condition(it) && !outdatedBy(it._id, at) &&
                    (after == null || comparator.compare(it, after) > 0) &&
                    (through == null || comparator.compare(it, through) <= 0)
        }.forEach { stale ->
            items.remove(stale._id)
            claims.keys.removeAll { it.condition(stale) }
        }
    }
}
