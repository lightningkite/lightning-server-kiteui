package com.lightningkite.lightningserver.db

import com.lightningkite.kiteui.Log
import com.lightningkite.services.database.HasId
import com.lightningkite.services.database.Query
import com.lightningkite.services.database.comparator

import com.lightningkite.reactive.core.BasicListenable
import com.lightningkite.reactive.core.Listenable
import kotlinx.serialization.KSerializer
import kotlin.time.Clock

/**
 * A simple implementation of [ListReconstructionCalculator] that maintains an independent cache for each unique query.
 *
 * This calculator stores query results in a hash map keyed by the exact query object. When updates arrive
 * (mutations, deletions, socket changes, etc.), it applies them to all cached queries by filtering, sorting,
 * and re-applying the query conditions.
 *
 * ## Approach
 * - Each query is cached separately with no sharing of data between queries
 * - Updates are applied naively to all cached queries regardless of relevance
 * - No optimization for overlapping queries (e.g., Query(condition=always) and Query(condition=always, limit=10))
 * - No intelligent merging of partial results
 *
 * ## Limitations
 * - **Limited Query Results**: When a query has a limit and items are removed, this calculator will not automatically
 *   know to fetch additional items to fill the gap. The cached list may contain fewer items than the limit.
 * - **No Query Optimization**: `recommendQuery()` returns the query unchanged, missing opportunities to optimize
 *   fetching strategies (e.g., fetching broader queries to satisfy multiple needs).
 * - **Memory Usage**: Each unique query maintains its own copy of matching items, leading to duplication.
 *
 * ## Timestamp Handling
 * The timestamp is only updated when we can guarantee the update represents complete knowledge:
 * - Always updated for [CacheUpdate.QueryResult] (fresh data from server)
 * - Updated for [CacheUpdate.SocketChanges] only when the socket has been active since before the cache entry
 * - Never updated for partial updates like [CacheUpdate.MutationResult] where we merge data without re-validation
 *
 * @param T The model type with an ID
 * @param ID The ID type, must be comparable
 * @param serializer Serializer for the model type, used to extract sort ordering metadata
 * @param log Optional console for debug logging
 * @param clock Clock for timestamp tracking, injectable for testing
 *
 * @see OptimizedListReconstructionCalculator for a more sophisticated implementation
 */
public class NaiveListReconstructionCalculator<T : HasId<ID>, ID : Comparable<ID>>(
    public val serializer: KSerializer<T>,
    public val log: Log? = null,
    public val clock: Clock = Clock.System
) : ListReconstructionCalculator<T, ID> {
    /** Cache of query results, keyed by the exact Query object (uses Query's equals/hashCode) */
    public val byQuery: HashMap<Query<T>, WithTimestampAndLimit<List<T>>> = HashMap<Query<T>, WithTimestampAndLimit<List<T>>>()

    /** Single listenable that fires whenever any cache update occurs (not query-specific) */
    public val all: BasicListenable = BasicListenable()

    /**
     * Returns a listenable that fires whenever the cache is updated.
     * Note: This implementation returns the same listenable for all queries, meaning subscribers
     * will be notified of all cache changes, not just changes relevant to their specific query.
     */
    override fun updates(query: Query<T>): Listenable = all

    /**
     * Applies an update to all cached queries.
     *
     * Depending on the update type, this method either:
     * - Stores a fresh query result ([CacheUpdate.QueryResult])
     * - Merges changes into existing caches ([CacheUpdate.MutationResult], [CacheUpdate.MultiGetResult])
     * - Removes items from existing caches ([CacheUpdate.DeletionResult])
     * - Applies socket-based changes with timestamp validation ([CacheUpdate.SocketChanges])
     * - Clears all caches on socket overload ([CacheUpdate.SocketOverload])
     *
     * After processing, triggers all listeners via [all].
     */
    override fun update(update: CacheUpdate<T, ID>): Unit {
        log?.log("Handling update $update")
        when (update) {
            is CacheUpdate.DeletionResult -> {
                val iter = byQuery.iterator()
                while (iter.hasNext()) {
                    val entry = iter.next()
                    val old = entry.value
                    // If we can't guarantee this was a full knowledge update, we can't update the timestamp with confidence.
                    val new = old.item.update(entry.key, emptyList(), update.deletedIds)
                    entry.setValue(old.copy(item = new))
                }
            }

            is CacheUpdate.MultiGetResult -> {
                val iter = byQuery.iterator()
                while (iter.hasNext()) {
                    val entry = iter.next()
                    val old = entry.value
                    // If we can't guarantee this was a full knowledge update, we can't update the timestamp with confidence.
                    val new = old.item.update(entry.key, update.items, update.missing)
                    entry.setValue(old.copy(item = new))
                }
            }

            is CacheUpdate.MutationResult -> {
                val iter = byQuery.iterator()
                while (iter.hasNext()) {
                    val entry = iter.next()
                    val old = entry.value
                    // If we can't guarantee this was a full knowledge update, we can't update the timestamp with confidence.
                    val new = old.item.update(entry.key, update.items)
                    entry.setValue(old.copy(item = new))
                }
            }

            is CacheUpdate.QueryResult -> byQuery[update.query] = WithTimestampAndLimit(update.result, requestedLimit = update.query.limit, at = update.at ?: clock.now())
            is CacheUpdate.SocketChanges -> {
                val iter = byQuery.iterator()
                while (iter.hasNext()) {
                    val entry = iter.next()
                    val old = entry.value
                    // Check if the socket has complete knowledge for this query's condition.
                    // This is true when:
                    // 1. The socket is listening to the same condition as the query
                    // 2. The socket was activated BEFORE our cached data (old.at > activatedAt means cache is newer)
                    // If both are true, the socket has been watching since before we got the data, so it has
                    // complete knowledge of all changes and we can safely apply removals.
                    val completeUpdate = update.fromRequirements.any {
                        it.condition == entry.key.condition && it.activatedAt?.let { old.at > it } == true
                    }
                    if (completeUpdate) {
                        // Complete knowledge: apply both changes and removals, update timestamp
                        val new = old.item.update(entry.key, update.changed, update.removed)
                        entry.setValue(old.copy(item = new, at = clock.now()))
                    } else {
                        // Partial knowledge: only merge in changed items, don't remove anything or update timestamp.
                        // Removals are skipped because the socket might not have complete visibility of this query.
                        val new = old.item.update(entry.key, update.changed)
                        entry.setValue(old.copy(item = new))
                    }
                }
            }

            is CacheUpdate.SocketOverload -> byQuery.clear()
        }
        all.invokeAll()
    }

    /**
     * Retrieves the cached result for the given query, if it exists.
     * Returns null if the query has never been cached.
     */
    override fun cached(query: Query<T>): WithTimestampAndLimit<List<T>>? = byQuery[query]

    /**
     * Returns a recommended query for fetching data.
     * This naive implementation simply returns the query unchanged.
     * A smarter implementation might return a broader query to satisfy multiple needs.
     */
    override fun recommendQuery(query: Query<T>): Query<T> = query

    /**
     * Clears all cached queries.
     */
    override fun clear() {
        byQuery.clear()
    }

    /**
     * Updates a cached list by merging in edits and removing specified IDs.
     *
     * ## Process
     * 1. Remove items whose IDs are in [removed] or [edits] (edits replace existing items)
     * 2. Add all items from [edits]
     * 3. Filter by the query's condition
     * 4. Remove duplicates by ID (in case of races or multiple updates)
     * 5. Sort according to the query's orderBy (with total ordering via [ensureTotal])
     * 6. Apply the query's limit if present
     *
     * ## Gotchas
     * - Items in [edits] replace items with the same ID (removal happens before addition)
     * - The query condition is re-applied, so edited items that no longer match will be filtered out
     * - [ensureTotal] ensures deterministic ordering even when sort fields have equal values
     * - When a query has a limit and items are removed, this does NOT automatically fetch more items
     *
     * @param query The query whose condition, sorting, and limit should be applied
     * @param edits Items to merge into the list (may be new or updated items)
     * @param removed IDs of items to remove from the list
     * @return The updated, filtered, sorted, and limited list
     */
    public fun List<T>.update(query: Query<T>, edits: Collection<T>, removed: Collection<ID> = emptyList()): List<T> {
        // Get the IDs of items to be removed (either explicitly removed or updated)
        val rem = removed + edits.map { it._id }.toSet()

        // Filter out removed items
        val filteredList = this.filter { it._id !in rem }

        // Add edited items
        val combinedList = filteredList + edits

        // Filter by query condition, remove duplicates, and sort according to query
        return combinedList
            .filter { query.condition(it) }
            .distinctBy { it._id }
            .sortedWith(query.orderBy.ensureTotal(serializer).comparator!!)
            .let {
                // Apply limit if specified in the query
                if (query.limit > 0) it.take(query.limit) else it
            }
    }
}

/*
 * TODO: API Improvement Recommendations
 *
 * 1. Query-Specific Listenables
 *    Current: updates() returns a single global listenable for all queries
 *    Problem: Subscribers are notified of ALL cache changes, even unrelated to their query
 *    Suggestion: Maintain per-query listenables or at least per-condition listenables
 *
 * 2. Query Normalization
 *    Current: Queries are keyed by object identity (equals/hashCode)
 *    Problem: Two semantically identical queries with different object instances won't share cache
 *    Suggestion: Normalize or canonicalize queries, or use structural equality
 *
 * 3. Cache Eviction Policy
 *    Current: Cache grows unbounded until manually cleared
 *    Problem: Memory leaks if queries are generated dynamically
 *    Suggestion: Add LRU eviction, time-based expiration, or manual per-query removal
 *
 * 4. Cache Metrics API
 *    Current: No visibility into cache performance
 *    Suggestion: Expose metrics like hit rate, cached query count, total items cached, update frequency
 *
 * 5. Partial Update Strategy
 *    Current: Partial updates (MutationResult, MultiGetResult) don't update timestamps
 *    Problem: Cache age doesn't reflect that we have some fresh data
 *    Suggestion: Consider a more nuanced staleness model (e.g., per-item timestamps)
 *
 * 6. Limit Handling Transparency
 *    Current: Limited queries can silently return fewer items than the limit after removals
 *    Problem: Consumers don't know if they have incomplete data
 *    Suggestion: Add a flag to WithTimestampAndLimit indicating "may be incomplete due to limit"
 *
 * 7. Testing Visibility
 *    Current: The update() extension function is effectively internal
 *    Suggestion: Consider making it internal or protected for testing, or expose a testing API
 */
