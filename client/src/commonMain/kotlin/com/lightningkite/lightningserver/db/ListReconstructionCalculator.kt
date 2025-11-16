package com.lightningkite.lightningserver.db

import com.lightningkite.services.database.HasId
import com.lightningkite.services.database.Query
import com.lightningkite.reactive.core.Listenable

/**
 * Interface for intelligently caching and reconstructing query results from partial updates.
 *
 * Implementations maintain cached query results and incrementally update them based on various
 * types of cache updates (mutations, deletions, socket changes, etc.) without requiring full re-fetches.
 *
 * ## Core Responsibilities
 * 1. **Caching**: Store query results and their metadata (timestamp, limit)
 * 2. **Update Handling**: Process different types of updates ([CacheUpdate]) and merge them into cached results
 * 3. **List Reconstruction**: Rebuild query results by applying filters, sorts, and limits after updates
 * 4. **Change Notification**: Notify subscribers when cached data changes
 *
 * ## Typical Usage
 * ```kotlin
 * val calculator = NaiveListReconstructionCalculator(MyModel.serializer())
 *
 * // Store a query result
 * val query = Query(condition { it.active eq true })
 * calculator.update(CacheUpdate.QueryResult(query, resultList))
 *
 * // Listen for changes
 * calculator.updates(query).addListener { /* refresh UI */ }
 *
 * // Apply an update (e.g., from a mutation)
 * calculator.update(CacheUpdate.MutationResult(listOf(updatedItem)))
 *
 * // Retrieve updated result
 * val cached = calculator.cached(query) // Contains merged results
 * ```
 *
 * ## Implementations
 * - [NaiveListReconstructionCalculator]: Simple per-query caching with isolated lists
 * - [OptimizedListReconstructionCalculator]: Shared global item cache with ID-based tracking
 *
 * @param T The model type, must have an ID
 * @param ID The ID type, must be comparable
 */
interface ListReconstructionCalculator<T : HasId<ID>, ID : Comparable<ID>> {
    /**
     * Returns a [Listenable] that fires when the cache is updated in ways that might affect the given query.
     *
     * Subscribers should refresh their data when notified.
     *
     * Note: Implementations may return a global listenable (all queries) or query-specific listenables.
     *
     * @param query The query to listen for updates to
     * @return A listenable that fires on relevant cache changes
     */
    fun updates(query: Query<T>): Listenable

    /**
     * Applies an update to the cache, merging new data with existing cached queries.
     *
     * Different [CacheUpdate] types are handled differently:
     * - [CacheUpdate.QueryResult]: Stores a fresh query result, replacing any previous cache
     * - [CacheUpdate.MutationResult]: Merges mutated items into existing queries
     * - [CacheUpdate.MultiGetResult]: Merges fetched items and marks missing items
     * - [CacheUpdate.DeletionResult]: Removes deleted items from cached queries
     * - [CacheUpdate.SocketChanges]: Applies real-time changes from WebSocket updates
     * - [CacheUpdate.SocketOverload]: Clears cache when socket can't keep up
     *
     * After processing, implementations should notify subscribers via [updates].
     *
     * @param update The cache update to apply
     */
    fun update(update: CacheUpdate<T, ID>): Unit

    /**
     * Retrieves the cached result for a query, if available.
     *
     * Returns null if the query has never been cached. The returned result includes:
     * - The reconstructed list (filtered, sorted, limited)
     * - The timestamp when the cache was last considered fully up-to-date
     * - The requested limit from the original query
     *
     * Implementations may lazily rebuild the query result on access if it has been marked dirty.
     *
     * @param query The query to look up
     * @return The cached result with metadata, or null if not cached
     */
    fun cached(query: Query<T>): WithTimestampAndLimit<List<T>>?

    /**
     * Recommends an optimized query to fetch for satisfying the given query.
     *
     * Implementations may return:
     * - The same query (no optimization)
     * - A broader query that can satisfy multiple cached queries at once
     * - A query with adjusted limits to account for expected deletions/changes
     *
     * Default implementation returns the query unchanged.
     *
     * @param query The original query
     * @return The recommended query to actually fetch (may be the same)
     */
    fun recommendQuery(query: Query<T>): Query<T> = query

    /**
     * Clears all cached data.
     *
     * After calling this, [cached] will return null for all queries until new results are provided.
     * Implementations should also clear any internal state (dirty flags, item caches, etc.).
     */
    fun clear()
}

/*
 * TODO: API Improvement Recommendations
 *
 * 1. Async Update Support
 *    Current: update() is synchronous
 *    Suggestion: Consider suspend fun update() for implementations that need async processing
 *
 * 2. Bulk Update API
 *    Current: Multiple updates require multiple update() calls
 *    Suggestion: Add updateAll(updates: List<CacheUpdate>) for batch processing
 *
 * 3. Cache Statistics
 *    Suggestion: Add fun stats(): CacheStats to expose hit rates, query counts, etc.
 *
 * 4. Eviction Hooks
 *    Suggestion: Add callbacks for when queries are evicted from cache (for LRU implementations)
 *
 * 5. Completeness API
 *    Current: No way to query if a cached result is complete or partial
 *    Suggestion: Add fun isComplete(query: Query<T>): Boolean or include in WithTimestampAndLimit
 *
 * 6. Query-Specific Clearing
 *    Current: Only clear() for all queries
 *    Suggestion: Add clearQuery(query: Query<T>) to selectively invalidate
 */