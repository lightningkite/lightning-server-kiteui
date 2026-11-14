package com.lightningkite.lightningserver.db

import com.lightningkite.services.database.optics.*
import com.lightningkite.reactive.core.BasicListenable
import com.lightningkite.services.database.Condition
import com.lightningkite.services.database.optics.AnyOptic
import com.lightningkite.services.database.HasId
import com.lightningkite.services.database.Mask
import com.lightningkite.services.database.Modification
import com.lightningkite.services.database.affects
import com.lightningkite.services.database.readPaths
import com.lightningkite.services.database.Query
import com.lightningkite.services.database.SortPart
import com.lightningkite.services.database._id
import com.lightningkite.services.database.comparator
import kotlinx.serialization.KSerializer
import kotlin.time.Instant

/**
 * Holds the rows a client has seen of one collection, plus timestamped claims about which queries it
 * knows completely.
 *
 * [known] answers a query locally only when a claim backs it. A query that returns fewer rows than its
 * limit claims its whole condition; one that fills its limit claims only through its last row. A claim
 * survives every change reported here, and is dropped only when an answer or a socket message shows a
 * row it relied on was wrong. Deciding when a claim is too old is left to the caller.
 *
 * This class is not thread safe.
 */
internal class CoverageIndex<T : HasId<ID>, ID : Comparable<ID>>(
    serializer: KSerializer<T>,
    /**
     * Returns a row's version, a value that only increases as the row changes, or `null` if unknown.
     * When this is `null` or returns `null`, conflicting accounts of a row are ordered by timestamp.
     */
    private val versionOf: ((T) -> Long?)? = null,
) {
    private val idProp = serializer._id()

    private val items = HashMap<ID, T>()

    // Claude: The order is part of the key because the server ANDs the sort fields' read mask into the
    // condition, so the same condition in another order can match different rows.
    private data class Extent<T>(val condition: Condition<T>, val order: List<SortPart<T>>)

    // Claude: Every match of the extent up to and including [through] is known as of [at]; a null
    // [through] means all of them. [through] is only a position in the order, so it stays valid after
    // that row changes.
    private class Claim<T>(val through: T?, val at: Instant)

    private val claims = HashMap<Extent<T>, Claim<T>>()

    // Claude: [changedAt] is the latest change that could have altered [value]; a change at or before
    // [at] happened before the request went out, so the server already counted it.
    private class Summary<T>(
        val condition: Condition<T>,
        val fields: List<AnyOptic<T>>,
        val value: Any?,
        val at: Instant,
        var changedAt: Instant? = null,
    ) {
        val stale: Boolean get() = changedAt?.let { it > at } == true
    }

    private val summaries = HashMap<Any, Summary<T>>()

    // Claude: A summary's first answer may have been requested before a change it never heard about,
    // since it wasn't stored yet. Any change since its request makes it stale.
    private var lastChangeAt: Instant? = null

    // Claude: When each row's state, present or absent, was last known. Unlike the row's claim, this
    // survives [invalidate] of the row, so a row reappearing still compares against what came before.
    private val seenAt = HashMap<ID, Instant>()

    /** Fires whenever the rows or claims change. */
    public val updates: BasicListenable = BasicListenable()

    private val idOrder: List<SortPart<T>> = listOf<SortPart<T>>().ensureTotal(serializer)

    /** Returns the query that reads the row with [id], which every lookup by ID must use to share claims. */
    public fun idQuery(id: ID): Query<T> = Query(Condition.OnField(idProp, Condition.Equal(id)), idOrder, limit = 1)

    private fun idExtent(id: ID): Extent<T> = Extent(Condition.OnField(idProp, Condition.Equal(id)), idOrder)

    // Claude: A fast path for ID lookups, which are most reads. Missing an unusual spelling of the
    // same condition only costs speed.
    @Suppress("UNCHECKED_CAST")
    private fun singleId(condition: Condition<T>): ID? = (condition as? Condition.OnField<T, *>)
        ?.takeIf { it.key == idProp }
        ?.let { (it.condition as? Condition.Equal<*>)?.value as ID? }

    private fun Query<T>.extent(): Extent<T> = Extent(condition, orderBy)

    // Claude: Copies the server's rule for which mask terms a sort adds. Null until [useMasking] is
    // called, which keeps every comparison on the conservative path.
    private var masking: Mask<T>? = null

    /** Whether the server's read mask is known to hide nothing, so conditions mean the same here as there. */
    public val unmasked: Boolean get() = masking?.pairs?.isEmpty() == true

    private val permitSortCache = HashMap<List<SortPart<T>>, Condition<T>>()

    /**
     * Sets the server's read [mask], so claims made in one sort order can answer queries in another.
     *
     * Claims made before this call are dropped unless [mask] is empty.
     */
    public fun useMasking(mask: Mask<T>) {
        if (masking == mask) return
        masking = mask
        permitSortCache.clear()
        // Claude: Summaries are kept, since the server's answers never depended on us knowing the mask.
        if (mask.pairs.isNotEmpty()) forgetRows() else updates.invokeAll()
    }

    // Claude: Terms the server adds to every query, like permissions, are left out; they cancel in
    // every comparison.
    private fun Extent<T>.effective(): Condition<T> {
        val mask = masking ?: return condition
        val imposed = permitSortCache.getOrPut(order) { mask.permitSort(order) }
        // Claude: `And([c, Always])` would be a different map key than `c`.
        return if (imposed is Condition.Always) condition else Condition.And(listOf(condition, imposed))
    }

    private fun sortFields(order: List<SortPart<T>>): Set<AnyOptic<T>> =
        order.mapTo(HashSet()) { it.field }

    // Claude: A bounded claim's [Claim.through] only means something in its own order. A complete
    // claim can be re-sorted, as long as the other order can't have added mask terms it lacks.
    private fun orderCovers(claimOrder: List<SortPart<T>>, claim: Claim<T>, wantedOrder: List<SortPart<T>>): Boolean =
        claimOrder == wantedOrder || (claim.through == null && (
                masking != null || sortFields(wantedOrder).containsAll(sortFields(claimOrder))
                ))

    // Claude: Any claim whose condition is implied by the query's can answer it. Prefers an exact
    // match, then complete over bounded, then newest.
    private fun backing(query: Query<T>): Claim<T>? {
        val extent = query.extent()
        claims[extent]?.let { return it }
        // Claude: Every held row has a one-row claim; skip them unless the query names an ID.
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

    // Claude: Claims assume "short means the end" and start at the first row, which a limit of zero
    // or a skip would break. [Extent] has no skip, so a skipped query would be filed as unskipped.
    private fun requireCacheable(query: Query<T>) {
        require(query.limit > 0) { "A limit of ${query.limit} asks the server for nothing." }
        require(query.skip == 0) { "A query skipping ${query.skip} rows says nothing about the rows before them." }
    }

    /**
     * The rows [known] found for a query.
     *
     * @property at when the claim backing [items] was made.
     * @property partial `true` if [items] stops where knowledge ends rather than at the query's limit.
     */
    public class Answer<T>(
        public val items: List<T>,
        public val at: Instant,
        public val partial: Boolean,
    )

    /**
     * Returns the rows of [query] that are known, or `null` if no claim covers it.
     *
     * A claim made by a broader query answers this one too.
     *
     * @throws IllegalArgumentException if [query] has a skip or a limit below 1.
     */
    public fun known(query: Query<T>): Answer<T>? {
        requireCacheable(query)
        val extent = query.extent()
        val claim = backing(query) ?: return null
        val effective = extent.effective()
        if (claim.through == null) singleId(query.condition)?.let { id ->
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

    /**
     * A value the server computed from the rows matching a condition, such as a count.
     *
     * @property at when the request for [value] was sent.
     * @property stale `true` if a change since [at] may have altered [value].
     */
    public class Stored(public val value: Any?, public val at: Instant, public val stale: Boolean)

    /** Returns the value last recorded under [key] by [summarized], or `null` if there is none. */
    public fun summary(key: Any): Stored? = summaries[key]?.let { Stored(it.value, it.at, it.stale) }

    /**
     * Records [value] as the server's answer to the summary [key], requested at [at] and received at
     * [receivedAt].
     *
     * The value goes stale when a row matching [condition] is added, removed, or changes one of [fields].
     */
    public fun summarized(
        key: Any,
        condition: Condition<T>,
        fields: List<AnyOptic<T>>,
        value: Any?,
        at: Instant,
        receivedAt: Instant,
    ) {
        // Claude: A change "after" receipt means the clock went backwards; capping it keeps the summary
        // from staying stale until the clock catches up.
        val changedAt = (summaries[key]?.changedAt ?: lastChangeAt)?.coerceAtMost(receivedAt)
        summaries[key] = Summary(condition, fields, value, at, changedAt)
        updates.invokeAll()
    }

    /** Returns the last row known for [query], or `null` if every row is known or no claim covers it. */
    public fun boundary(query: Query<T>): T? = backing(query)?.through

    /** Returns a copy of every row held. */
    public fun cachedItems(): List<T> = items.values.toList()

    /**
     * Records [result] as the server's answer to [query], requested at [at].
     *
     * The new claim replaces the old one for the same query, even if the old one reached further.
     *
     * @param at the time the request was sent, not received, so a fetch that raced a change never
     *   outranks it.
     * @param reachedEnd whether the server said it returned every row; `null` infers it from [result]
     *   being shorter than the limit.
     */
    public fun queried(query: Query<T>, result: List<T>, at: Instant, reachedEnd: Boolean? = null) {
        requireCacheable(query)
        val extent = query.extent()
        val through = if (reachedEndOf(result, query.limit, reachedEnd)) null else result.lastOrNull()
        reconcile(extent.effective(), query.orderBy, after = null, through = through, returned = result, at = at)
        result.forEach { record(it, at, change = false) }
        claims[extent] = Claim(through, at)
        updates.invokeAll()
    }

    private fun reachedEndOf(result: List<T>, limit: Int, declared: Boolean?): Boolean =
        declared ?: (result.size < limit)

    /**
     * Records [result] as the page of [query] after the row [after], extending its claim.
     *
     * The extended claim keeps the older timestamp of the two, since the earlier rows were not re-read.
     *
     * @param pageLimit the limit the page was requested with.
     * @param reachedEnd as in [queried].
     * @throws IllegalArgumentException if no claim covers the rows before [after].
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
        val previous = requireNotNull(backing(query)) { "Nothing covers the rows before this page." }
        val extent = query.extent()
        val through = if (reachedEndOf(result, pageLimit, reachedEnd)) null else result.lastOrNull()
        reconcile(extent.effective(), query.orderBy, after = after, through = through, returned = result, at = at)
        result.forEach { record(it, at, change = false) }
        claims[extent] = Claim(through, minOf(previous.at, at))
        updates.invokeAll()
    }

    /** Records a lookup by ID that found the rows [found] and confirmed the IDs [missing] don't exist. */
    public fun identified(found: List<T>, missing: Set<ID>, at: Instant) {
        found.forEach { record(it, at, change = false) }
        missing.forEach { gone(it, at, change = false) }
        updates.invokeAll()
    }

    /** Records rows this client changed itself. Every claim stays valid. */
    public fun mutated(changed: Collection<T>, at: Instant) {
        changed.forEach { record(it, at, change = true) }
        updates.invokeAll()
    }

    /**
     * Records rows this client deleted itself. Every claim stays valid.
     *
     * A list that was full comes up one row short until it fetches again.
     */
    public fun deleted(ids: Set<ID>, at: Instant) {
        ids.forEach { gone(it, at, change = true) }
        updates.invokeAll()
    }

    /**
     * Records that the server deleted every row matching [condition], including rows not held.
     *
     * Every summary goes stale, since any of them could have counted a row this index never saw.
     */
    public fun bulkDeleted(condition: Condition<T>, at: Instant) {
        items.values.filter { condition(it) }.forEach { gone(it._id, at, change = true) }
        noteChange(at)
        summaries.values.forEach { it.changed(at) }
        updates.invokeAll()
    }

    /**
     * Records a socket message received at [at].
     *
     * A row in [removed] stopped matching the subscription; it was not necessarily deleted. Claims
     * the socket isn't keeping live and that included the row are dropped.
     *
     * @param live returns whether the socket has delivered every change to a condition since a time.
     */
    public fun socketDelta(
        changed: Set<T>,
        removed: Set<ID>,
        at: Instant,
        live: (condition: Condition<T>, since: Instant) -> Boolean,
    ) {
        changed.forEach { record(it, at, change = true) }
        removed.forEach { id ->
            val old = items.remove(id)
            seenAt.remove(id)
            noteChange(at)
            // Claude: Where the row went is unknown, so it may have left or entered any summary's condition.
            summaries.values.forEach { it.changed(at) }
            if (old == null) return@forEach
            // Claude: Otherwise the row would read as deleted rather than unknown.
            claims.remove(idExtent(id))
            claims.entries.removeAll { (extent, claim) ->
                extent.effective()(old) && !live(extent.condition, claim.at)
            }
        }
        updates.invokeAll()
    }

    /**
     * Forgets every row, claim, and summary.
     *
     * A summary requested before [at] and recorded afterwards is stale, since changes may have been missed.
     */
    public fun invalidate(at: Instant) {
        // Claude: Changes may have been missed, so an answer requested before now can't be trusted.
        noteChange(at)
        forgetEverything()
    }

    private fun forgetEverything() {
        summaries.clear()
        forgetRows()
    }

    private fun forgetRows() {
        items.clear()
        claims.clear()
        seenAt.clear()
        updates.invokeAll()
    }

    /**
     * Drops the claims a bulk [modification] could have broken: those whose condition or sort reads a
     * field it writes.
     *
     * Rows already held that the modification changed are not updated; the caller has to re-read them.
     */
    public fun bulkModified(modification: Modification<T>, at: Instant) {
        claims.keys.removeAll { extent ->
            extent.effective().readPaths().any { modification.affects(it) } ||
                extent.order.any { modification.affects(it.field) }
        }
        rowsModified(modification, at)
    }

    /**
     * Records that the server applied [modification] to some rows, possibly ones not held, which the
     * caller re-reads.
     *
     * Claims are kept, but summaries reading a field [modification] writes go stale.
     */
    public fun rowsModified(modification: Modification<T>, at: Instant) {
        noteChange(at)
        summaries.values
            .filter { s -> (s.condition.readPaths() + s.fields).any { modification.affects(it) } }
            .forEach { it.changed(at) }
        updates.invokeAll()
    }

    /**
     * Forgets the claim on the row with [id], so the next lookup of it fetches.
     *
     * Claims on lists that include the row are kept.
     */
    public fun invalidate(id: ID) {
        claims.remove(idExtent(id))
        updates.invokeAll()
    }

    // Claude: [change] means the row changed on the server, not that we only read it. A read of a row
    // we knew nothing about tells us nothing changed; a change to one could have altered any summary.
    private fun record(item: T, at: Instant, change: Boolean) {
        if (alreadyKnowBetter(item._id, at, item)) return
        summariesSee(item._id, item, at, change)
        items[item._id] = item
        claims[idExtent(item._id)] = Claim(through = null, at = at)
    }

    private fun gone(id: ID, at: Instant, change: Boolean) {
        if (alreadyKnowBetter(id, at, incoming = null)) return
        summariesSee(id, null, at, change)
        items.remove(id)
        claims[idExtent(id)] = Claim(through = null, at = at)
    }

    // Claude: A read only reveals a change by comparing with a row we knew; reads of rows never seen
    // are ignored, and polling covers them.
    private fun summariesSee(id: ID, new: T?, at: Instant, change: Boolean) {
        val seen = seenAt[id]
        val old = items[id]
        seenAt[id] = at
        if (if (seen != null) old != new else change) noteChange(at)
        summaries.values.forEach { s ->
            if (if (seen != null) s.alters(old, new) else change) s.changed(at)
        }
    }

    private fun Summary<T>.alters(old: T?, new: T?): Boolean {
        val wasIn = old != null && condition(old)
        val isIn = new != null && condition(new)
        return wasIn != isIn || (wasIn && fields.any { it.get(old!!) != it.get(new!!) })
    }

    private fun Summary<T>.changed(at: Instant) {
        if (changedAt.let { it == null || it < at }) changedAt = at
    }

    private fun noteChange(at: Instant) {
        if (lastChangeAt.let { it == null || it < at }) lastChangeAt = at
    }

    // Claude: Decides which of two accounts of a row is newer, by version when both rows have one, else
    // by the row's claim timestamp. Deletions and absences have no row, so they always use timestamps.
    private fun alreadyKnowBetter(id: ID, at: Instant, incoming: T?): Boolean {
        val versionOf = versionOf
        if (versionOf != null && incoming != null) {
            val held = items[id]?.let(versionOf)
            val offered = versionOf(incoming)
            if (held != null && offered != null) return held > offered
        }
        return claims[idExtent(id)]?.at?.let { it > at } == true
    }

    private fun implies(narrow: Condition<T>, broad: Condition<T>): Boolean =
        impliesCondition(narrow, broad)

    // Claude: A held row that belongs in the range the server just listed but wasn't returned is stale.
    // It is dropped, along with every claim that included it, unless we heard about it after [at].
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
            noteChange(at)
            // Claude: Where the row went is unknown, so it may have left or entered any summary's condition.
            summaries.values.forEach { it.changed(at) }
            items.remove(stale._id)
            seenAt.remove(stale._id)
            claims.keys.removeAll { it.effective()(stale) }
        }
    }
}
