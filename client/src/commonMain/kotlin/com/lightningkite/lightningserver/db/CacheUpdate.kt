package com.lightningkite.lightningserver.db

import com.lightningkite.services.database.Condition
import com.lightningkite.services.database.HasId
import com.lightningkite.services.database.Query
import kotlin.time.Instant

/**
 * Sealed hierarchy representing different types of cache updates that flow through [ModelCache.newData].
 *
 * All data changes in ModelCache are represented as CacheUpdate events, which are processed by:
 * - [ListReconstructionCalculator] for query result updates
 * - Individual item caches in [ModelCache.lastIndividualValues]
 * - External listeners
 *
 * ## Update Types
 *
 * Each subclass represents a different source or type of data change:
 * - [QueryResult]: Fresh query results from the server
 * - [MultiGetResult]: Bulk item lookup results (with known missing items)
 * - [MutationResult]: Items that were created or modified
 * - [DeletionResult]: Items that were deleted
 * - [SocketChanges]: Real-time updates from WebSocket
 * - [SocketOverload]: Signal to clear all caches (socket can't keep up)
 *
 * ## Usage
 * These are primarily created internally by ModelCache methods and fed into the [ModelCache.newData] signal.
 * Cache implementations (like [ListReconstructionCalculator]) handle each type appropriately to keep
 * cached data in sync without requiring full refetches.
 *
 * @param T The model type
 * @param ID The ID type
 */
public sealed class CacheUpdate<T : HasId<ID>, ID : Comparable<ID>> {
    /**
     * Items affected by this update, if any.
     * Used as a convenience property to extract items regardless of the specific update type.
     * Returns null for [SocketOverload], empty for [DeletionResult], and the relevant items otherwise.
     */
    public abstract val items: Collection<T>?

    /**
     * Socket overload event - clears ALL caches.
     *
     * This is emitted when:
     * - The WebSocket falls too far behind and can't guarantee complete knowledge
     * - A bulk modification is performed (we don't know which items were affected)
     * - The cache is being initialized (default value for [ModelCache.newData])
     *
     * Handlers should:
     * - Clear all cached data
     * - Mark all data as stale/not-ready
     * - Force refetches on next access
     *
     * This is intentionally aggressive to avoid serving stale data when we've lost track of changes.
     */
    public class SocketOverload<T : HasId<ID>, ID : Comparable<ID>>(): CacheUpdate<T, ID>() {
        override val items: Collection<T>? get() = null
        override fun toString(): String = "SocketOverload"
    }

    /**
     * Real-time updates from a WebSocket connection.
     *
     * Contains both changed items and IDs of removed items, along with metadata about
     * which conditions the socket has been monitoring and since when.
     *
     * ## Timestamp Validation
     * The [fromRequirements] set allows handlers to determine if they have "complete knowledge":
     * - If a cached query matches a condition in [fromRequirements]
     * - AND the cache timestamp is AFTER the [ConditionAndTimestamp.activatedAt]
     * - THEN the socket has been watching since before the cache was populated
     * - So we can safely apply both [changed] and [removed]
     *
     * If the above isn't true, only [changed] should be applied (not [removed]) since the
     * socket might have missed deletions that occurred before it connected.
     *
     * @param changed Items that were created or modified
     * @param removed IDs of items that were deleted
     * @param fromCondition The overall condition being monitored (may be broader than individual requirements)
     * @param fromRequirements Specific conditions with activation timestamps for validation
     */
    public class SocketChanges<T : HasId<ID>, ID : Comparable<ID>>(
        public val changed: Set<T>,
        public val removed: Set<ID>,
        public val fromCondition: Condition<T>,
        public val fromRequirements: Set<ConditionAndTimestamp<T>>
    ): CacheUpdate<T, ID>(){
        /**
         * Interface representing a condition that has been actively monitored since a specific time.
         *
         * Used to determine if cached data is "live" (backed by real-time updates).
         * If [activatedAt] is null, the condition has been active since an unknown time
         * (treat conservatively as not having complete knowledge).
         */
        public interface ConditionAndTimestamp<T> {
            /** The condition being monitored */
            public val condition: Condition<T>

            /** When monitoring of this condition started, or null if unknown */
            public val activatedAt: Instant?
        }

        override val items: Collection<T> get() = changed
        override fun toString(): String = "SocketChanges(changed=${changed.size} items, removed=${removed.size} ids, condition=$fromCondition, requirements=${fromRequirements.size})"
    }

    /**
     * Fresh query results from the server.
     *
     * This represents a complete query execution, with all matching items as of the query time.
     * Handlers should:
     * - Store/update the cached result for this exact query
     * - Update timestamp to mark data as fresh
     * - Update individual item caches with items from the result
     *
     * @param query The query that was executed (condition, sort, limit)
     * @param result The list of items that matched the query
     */
    public class QueryResult<T : HasId<ID>, ID : Comparable<ID>>(
        public val query: Query<T>,
        public val result: List<T>
    ): CacheUpdate<T, ID>(){
        override val items: Collection<T> get() = result
        override fun toString(): String = "QueryResult(query=$query, ${result.size} items)"
    }

    /**
     * Results from a bulk item lookup by IDs.
     *
     * Unlike [QueryResult], this explicitly tracks which IDs were missing (confirmed non-existent).
     * Handlers should:
     * - Update/add items from [result]
     * - Mark items in [missing] as confirmed non-existent (e.g., cache as null)
     * - Merge into existing query caches
     *
     * The [missing] set is important for distinguishing "not fetched yet" from "confirmed deleted".
     *
     * @param missing IDs that were requested but don't exist
     * @param result Items that were found
     */
    public class MultiGetResult<T : HasId<ID>, ID : Comparable<ID>>(
        public val missing: Set<ID>,
        public val result: List<T>
    ): CacheUpdate<T, ID>(){
        override val items: Collection<T> get() = result
        override fun toString(): String = "MultiGetResult(missing=${missing.size} ids, found=${result.size} items)"
    }

    /**
     * Items that were mutated (created, modified, or upserted).
     *
     * Represents the result of insert, update, modify, or upsert operations.
     * Handlers should:
     * - Update individual item caches with the new values
     * - Merge into existing query caches (replace old versions, add new items)
     * - Re-evaluate query conditions (mutated items might now match/not match)
     *
     * Note: Timestamp updates are typically NOT applied for mutations in list caches,
     * since this represents partial knowledge (we don't know about other items that might have changed).
     *
     * @param items The items that were mutated (in their post-mutation state)
     */
    public class MutationResult<T : HasId<ID>, ID : Comparable<ID>>(
        override val items: Collection<T>
    ): CacheUpdate<T, ID>() {
        override fun toString(): String = "MutationResult(${items.size} items)"
    }

    /**
     * Items that were deleted.
     *
     * Handlers should:
     * - Mark items in individual caches as deleted (e.g., cache as null)
     * - Remove from all query caches
     * - Update query timestamps conservatively (don't assume complete knowledge)
     *
     * @param deletedIds IDs of items that were deleted
     */
    public class DeletionResult<T : HasId<ID>, ID : Comparable<ID>>(
        public val deletedIds: Set<ID>
    ): CacheUpdate<T, ID>() {
        override val items: Collection<T> = emptyList()
        override fun toString(): String = "DeletionResult(${deletedIds.size} ids)"
    }
}

/*
 * TODO: API Improvement Recommendations
 *
 * 1. Timestamp Information
 *    Current: Only SocketChanges has timestamp information via ConditionAndTimestamp
 *    Problem: Other update types don't indicate when the update occurred
 *    Suggestion: Add optional timestamp field to all update types for better cache age tracking
 *
 * 2. Batching/Combining Updates
 *    Current: Multiple updates require multiple CacheUpdate events
 *    Problem: Inefficient when applying many changes at once
 *    Suggestion: Add CombinedUpdate type that batches multiple updates
 *
 * 3. Partial Update Metadata
 *    Current: No way to indicate if an update is partial vs. complete
 *    Problem: Handlers can't distinguish between "here's everything" and "here's what changed"
 *    Suggestion: Add isComplete/isPartial flag to relevant types
 *
 * 4. Source Tracking
 *    Current: No indication of where the update came from
 *    Problem: Can't distinguish user mutations from socket updates from polling
 *    Suggestion: Add source: UpdateSource enum (API, WebSocket, Local, etc.)
 *
 * 5. Query Result Pagination Info
 *    Current: QueryResult doesn't indicate if more results exist
 *    Problem: Can't tell if limit was hit vs. no more items exist
 *    Suggestion: Add hasMore/totalCount fields to QueryResult
 */