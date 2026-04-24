package com.lightningkite.lightningserver.db

import com.lightningkite.kiteui.Log
import com.lightningkite.services.database.HasId
import com.lightningkite.services.database.Query
import com.lightningkite.services.database.comparator
import com.lightningkite.reactive.core.BasicListenable
import com.lightningkite.reactive.core.Listenable
import kotlinx.serialization.KSerializer
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * An optimized implementation of [ListReconstructionCalculator] that maintains a global item cache
 * and efficiently tracks query results using ID sets rather than duplicating full objects.
 *
 * ## Optimization Strategy
 * Unlike [NaiveListReconstructionCalculator] which stores complete lists for each query, this implementation:
 * - Maintains a single global cache of items by ID (reduces memory for overlapping queries)
 * - Tracks query results as sets of IDs rather than full objects
 * - Uses dirty flagging to defer query rebuilding until [cached] is called
 * - Materializes lists lazily only when accessed
 *
 * ## Key Improvements Over Naive
 * - **Memory Efficiency**: Items appearing in multiple queries are stored once
 * - **Lazy Evaluation**: Query results are recomputed only when accessed, not on every update
 * - **Incremental Updates**: Only marks queries as dirty rather than immediately rebuilding them
 *
 * ## Caveats and Gotchas
 * - **Shared Item Cache**: Deleting or marking items as missing affects ALL queries, even unrelated ones.
 *   If one query determines an item is missing, it's removed from the global cache, potentially breaking
 *   other queries that still have that item in their results.
 * - **Full Scan on Rebuild**: [rebuildQuery] scans ALL items in the global cache to find matches, which can be
 *   expensive with many items. This also means new items from mutations are automatically included in query
 *   results even if we never fetched them specifically for that query.
 * - **Incomplete Tracking**: The `isComplete` flag is set but never consumed, so there's no mechanism to
 *   trigger refetches when data is known to be incomplete.
 * - **Memory Growth**: Items that no longer match queries remain in the global cache unless explicitly deleted.
 *
 * @param T The model type with an ID
 * @param ID The ID type, must be comparable
 * @param serializer Serializer for the model type, used to extract sort ordering metadata
 * @param log Optional console for debug logging
 * @param clock Clock for timestamp tracking, injectable for testing
 *
 * @see NaiveListReconstructionCalculator for a simpler implementation with isolated per-query caches
 */
public class OptimizedListReconstructionCalculator<T : HasId<ID>, ID : Comparable<ID>>(
    public val serializer: KSerializer<T>,
    public val log: Log? = null,
    public val clock: Clock = Clock.System
) : ListReconstructionCalculator<T, ID> {

    /**
     * Global item cache: single source of truth for all known items.
     * Shared across all queries to reduce memory duplication.
     */
    private val items = HashMap<ID, T>()

    /**
     * Metadata for a cached query result.
     * Stores IDs rather than full objects to leverage the shared [items] cache.
     */
    private inner class QueryCache(
        val itemIds: Set<ID>,  // IDs that match this query
        val timestamp: Instant, // When this query was last fully updated
        val requestedLimit: Int, // The limit from the original query
        val isComplete: Boolean = true // Whether we have complete knowledge for this query
    ) {
        fun copy(
            itemIds: Set<ID> = this.itemIds,
            timestamp: Instant = this.timestamp,
            requestedLimit: Int = this.requestedLimit,
            isComplete: Boolean = this.isComplete
        ) = QueryCache(itemIds, timestamp, requestedLimit, isComplete)
    }

    /**
     * Cached query results indexed by query.
     * Stores only IDs and metadata, not full item lists.
     */
    private val queryCache = HashMap<Query<T>, QueryCache>()

    /**
     * Set of queries that need re-evaluation due to item changes.
     * Cleared as queries are accessed and rebuilt in [cached].
     */
    private val dirtyQueries = HashSet<Query<T>>()

    /** Single listenable that fires for all cache updates (not query-specific) */
    private val allListenable = BasicListenable()

    /**
     * Returns a listenable that fires when the cache is updated.
     * Note: Same listenable returned for all queries, subscribers receive all cache change notifications.
     */
    override fun updates(query: Query<T>): Listenable = allListenable

    /**
     * Applies an update to the cache.
     *
     * Updates the global item cache and marks affected queries as dirty for lazy rebuilding.
     * The actual query rebuilding is deferred until [cached] is called.
     */
    override fun update(update: CacheUpdate<T, ID>) {
        log?.log("Handling update $update")

        when (update) {
            is CacheUpdate.QueryResult -> {
                // Store items in global cache
                update.result.forEach { item ->
                    items[item._id] = item
                }

                // Update query cache with IDs
                queryCache[update.query] = QueryCache(
                    itemIds = update.result.map { it._id }.toSet(),
                    timestamp = clock.now(),
                    requestedLimit = update.query.limit,
                    isComplete = true
                )
            }

            is CacheUpdate.MutationResult -> {
                // Update items in global cache
                update.items.forEach { item ->
                    items[item._id] = item
                }

                // Mark all queries as dirty since mutations might affect any query
                dirtyQueries.addAll(queryCache.keys)
            }

            is CacheUpdate.MultiGetResult -> {
                // Add/update items in global cache
                update.result.forEach { item ->
                    items[item._id] = item
                }

                // TODO: POTENTIAL BUG - Removing missing items from global cache affects ALL queries
                // If Query A and Query B both reference item X, but Query A's MultiGetResult says X is
                // missing, we remove X from the global cache, breaking Query B's results. Consider
                // maintaining per-query knowledge of missing items instead.
                update.missing.forEach { id ->
                    items.remove(id)
                }

                // Mark all queries as dirty
                dirtyQueries.addAll(queryCache.keys)
            }

            is CacheUpdate.DeletionResult -> {
                // Remove from global cache
                update.deletedIds.forEach { id ->
                    items.remove(id)
                }

                // Remove from query caches and mark as dirty
                queryCache.keys.forEach { query ->
                    dirtyQueries.add(query)
                }
            }

            is CacheUpdate.SocketChanges -> {
                // Update items in global cache
                update.changed.forEach { item ->
                    items[item._id] = item
                }

                // Remove deleted items
                update.removed.forEach { id ->
                    items.remove(id)
                }

                // Update queries based on whether this is a complete update
                queryCache.forEach { (query, cache) ->
                    val completeUpdate = update.fromRequirements.any {
                        it.condition == query.condition &&
                        it.activatedAt?.let { activatedAt -> cache.timestamp > activatedAt } == true
                    }

                    if (completeUpdate) {
                        // This is a complete update for this query - update timestamp
                        queryCache[query] = cache.copy(timestamp = clock.now())
                    }

                    // Mark as dirty for re-evaluation
                    dirtyQueries.add(query)
                }
            }

            is CacheUpdate.SocketOverload -> {
                // Clear everything
                items.clear()
                queryCache.clear()
                dirtyQueries.clear()
            }
        }

        allListenable.invokeAll()
    }

    /**
     * Retrieves the cached result for a query, rebuilding if dirty and materializing from IDs.
     *
     * ## Process
     * 1. Return null if query has never been cached
     * 2. If query is dirty (affected by updates), rebuild its ID set by scanning all items
     * 3. Materialize the list from IDs (lookup, filter, sort, limit)
     * 4. Return the materialized list with timestamp and metadata
     *
     * ## Lazy Evaluation
     * Queries are only rebuilt when accessed, not immediately upon updates. This defers
     * expensive rebuilding until necessary and batches multiple updates between accesses.
     *
     * @return The cached result with materialized list, or null if query has never been cached
     */
    override fun cached(query: Query<T>): WithTimestampAndLimit<List<T>>? {
        val cache = queryCache[query] ?: return null

        // If query is dirty, rebuild it
        if (query in dirtyQueries) {
            rebuildQuery(query, cache)
            dirtyQueries.remove(query)
        }

        // Materialize the list from IDs
        val updatedCache = queryCache[query] ?: return null
        val list = materializeQuery(query, updatedCache.itemIds)

        return WithTimestampAndLimit(
            item = list,
            requestedLimit = updatedCache.requestedLimit,
            at = updatedCache.timestamp
        )
    }

    /**
     * Returns a recommended query for fetching data.
     * This implementation simply returns the query unchanged.
     * A smarter implementation might expand the query to satisfy multiple needs at once.
     */
    override fun recommendQuery(query: Query<T>): Query<T> = query

    /**
     * Clears all caches: global items, query results, and dirty flags.
     */
    override fun clear() {
        items.clear()
        queryCache.clear()
        dirtyQueries.clear()
    }

    /**
     * Rebuilds a query's cached ID set by re-evaluating all items in the global cache.
     *
     * This is called when a query is dirty (has been affected by mutations, deletions, etc.).
     * It performs a full scan of all items to find matches, which ensures that:
     * - Items that no longer match are removed from the query
     * - New items that match are added to the query
     *
     * ## Performance Note
     * This method evaluates EVERY item in the global cache against the query condition.
     * With thousands of items, this can be expensive. Consider optimization strategies like:
     * - Indexing items by common condition fields
     * - Tracking which items are relevant to which queries
     * - Only evaluating changed items
     *
     * ## Correctness Note
     * The combination of `knownIds + allMatchingIds` is redundant since `knownIds` filters
     * `currentCache.itemIds` to only those still in the items cache, and `allMatchingIds`
     * already includes all matching items from the items cache. The union operation doesn't
     * add anything beyond what `allMatchingIds` already provides.
     */
    private fun rebuildQuery(query: Query<T>, currentCache: QueryCache) {
        // Start with items we know are in this query
        val knownIds = currentCache.itemIds.filter { it in items }.toSet()

        // Evaluate all items against the query condition to find new matches
        val allMatchingItems = items.values.filter { query.condition(it) }
        val allMatchingIds = allMatchingItems.map { it._id }.toSet()

        // TODO: This combination is redundant - knownIds is a subset of allMatchingIds
        // (assuming items in the global cache haven't changed to no longer match).
        // Should just use: val finalIds = allMatchingIds
        val finalIds = knownIds + allMatchingIds

        // Update the cache (keep the original timestamp unless we have complete knowledge)
        queryCache[query] = currentCache.copy(
            itemIds = finalIds,
            isComplete = false // We don't have complete knowledge after partial updates
        )
    }

    /**
     * Materializes a concrete list from a set of item IDs by looking them up in the global cache.
     *
     * This performs the final steps to produce a query result:
     * 1. Lookup items by ID in the global cache (may return fewer items if some IDs are no longer cached)
     * 2. Defensively filter by query condition (items may have changed since IDs were cached)
     * 3. Sort according to query's orderBy with total ordering via [ensureTotal]
     * 4. Apply limit if specified
     *
     * ## Gotchas
     * - Items referenced by [itemIds] may have been deleted from the global cache, resulting in fewer items
     * - Items may have changed and no longer match the query condition, causing defensive filtering
     * - The returned list may be shorter than expected due to concurrent modifications
     *
     * @param query The query specifying condition, sort order, and limit
     * @param itemIds Set of IDs that should be in the result (pre-filtered, but may be stale)
     * @return Materialized list, sorted and limited according to the query
     */
    private fun materializeQuery(query: Query<T>, itemIds: Set<ID>): List<T> {
        // Get items from global cache (mapNotNull handles missing items)
        val matchingItems = itemIds.mapNotNull { items[it] }

        // Filter by condition (defensive - items might have changed since being cached)
        val filtered = matchingItems.filter { query.condition(it) }

        // Sort according to query with total ordering for deterministic results
        val sorted = filtered.sortedWith(query.orderBy.ensureTotal(serializer).comparator!!)

        // Apply limit if specified in the query
        return if (query.limit > 0) sorted.take(query.limit) else sorted
    }
}

/*
 * TODO: API Improvement Recommendations
 *
 * 1. Query-Specific Listenables
 *    Current: updates() returns a single global listenable for all queries
 *    Problem: Subscribers are notified of ALL cache changes, even unrelated to their query
 *    Suggestion: Maintain per-query listenables or use a more granular notification system
 *
 * 2. Shared Cache Isolation
 *    Current: Global item cache is shared, deletions/missing items affect all queries
 *    Problem: One query marking an item as missing can break other queries' results
 *    Suggestion: Track item presence per-query or use reference counting before removing from global cache
 *
 * 3. Full-Scan Performance
 *    Current: rebuildQuery() scans ALL items to find matches
 *    Problem: O(n) scan of entire cache on every rebuild, expensive with many items
 *    Suggestion: Add indexing by condition fields, track item-to-query relationships, or use incremental updates
 *
 * 4. isComplete Flag Usage
 *    Current: isComplete is set but never consumed
 *    Problem: No mechanism to trigger refetches when data is known to be incomplete
 *    Suggestion: Expose isComplete in WithTimestampAndLimit or add a separate API to query completeness
 *
 * 5. Memory Leak Prevention
 *    Current: Items remain in global cache indefinitely, itemIds in QueryCache may reference non-existent items
 *    Problem: Memory grows unbounded with no eviction strategy
 *    Suggestion: Implement reference counting, LRU eviction, or periodic cleanup of unreferenced items
 *
 * 6. Redundant Set Operation
 *    Current: rebuildQuery combines knownIds + allMatchingIds
 *    Problem: knownIds is always a subset of allMatchingIds, making the union redundant
 *    Suggestion: Remove knownIds calculation and just use allMatchingIds
 *
 * 7. Cache Metrics and Observability
 *    Current: No visibility into cache performance
 *    Suggestion: Expose metrics like global item count, query count, dirty query count, rebuild frequency
 *
 * 8. Concurrent Modification Safety
 *    Current: No protection against concurrent modifications during iteration
 *    Problem: If updates arrive while materializing a query, behavior is undefined
 *    Suggestion: Consider adding synchronization or using concurrent collections
 */
