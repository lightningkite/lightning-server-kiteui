package com.lightningkite.lightningserver.db

import com.lightningkite.services.database.HasId
import com.lightningkite.services.database.MassModification
import com.lightningkite.services.database.Query
import kotlinx.serialization.KSerializer
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Common interface for cache implementations that store and retrieve models with IDs.
 *
 * This interface provides a unified API for both single-item and collection caching,
 * with configurable freshness policies and automatic background refresh capabilities.
 *
 * ## Caching Strategy:
 *
 * The two knobs answer two different questions, and confusing them is how a screen ends up either
 * opening on stale data or hammering the server while it sits there:
 *
 * - **maximumAge** is a condition for *arriving*: how old data already in the cache may be for this
 *   to show it straight away rather than waiting for a retrieve first.  It is asked once, when the
 *   reader starts being observed, because that is when someone is watching a screen appear.  It is
 *   not a shelf life: once shown, data keeps being shown until something replaces it.
 * - **pullFrequency** is the whole of how often a reader refreshes itself while it is up.  Zero
 *   means it never does, and a live update socket makes it unnecessary.
 * - **Duration.INFINITE** as a maximumAge accepts whatever is already cached, however old.
 *
 * ## Implementations:
 * - [ModelCache]: Full-featured implementation with WebSocket support
 * - Mock implementations can be created for testing
 *
 * @param T The model type, must implement [HasId]
 * @param ID The ID type, must be [Comparable]
 */
public interface ModelCacheLike<T : HasId<ID>, ID : Comparable<ID>> {
    public val serializer: KSerializer<T>

    /**
     * Gets a reactive reference to a single item by ID.
     *
     * The returned [ModelCacheItemReadable] will automatically fetch and update the item
     * based on the caching policy:
     * - If what is already cached is older than [maximumAge], this retrieves before showing anything
     * - If [pullFrequency] > 0, the item will be polled at that interval while it is observed
     * - WebSocket updates (if available) will update the item in real-time, and replace polling
     *
     * @param id The ID of the item to retrieve
     * @param maximumAge How old already-cached data may be for this to show it on arrival rather than
     *   retrieving first (default: any age is acceptable)
     * @param pullFrequency How often to refresh while observed (default: no polling)
     * @return A reactive reference to the item that updates automatically
     */
    public fun item(
        id: ID,
        maximumAge: Duration = Duration.INFINITE,
        pullFrequency: Duration = 0.seconds,
    ): ModelCacheItemReadable<T>

    /**
     * Gets a reactive reference to a collection of items matching a query.
     *
     * The returned [ModelCacheLimitReadable] will automatically fetch and update the collection
     * based on the caching policy:
     * - If what is already cached is older than [maximumAge], this retrieves before showing anything
     * - If [pullFrequency] > 0, the query will be polled at that interval while it is observed
     * - WebSocket updates (if available) will update matching items in real-time, and replace polling
     *
     * **IMPORTANT**: results are shared. Items retrieved by any query - or by an [item] lookup - are
     * held once, so a query can be answered from what other reads already established, and a change
     * seen anywhere is seen everywhere.
     *
     * @param maximumAge How old already-cached data may be for this to show it on arrival rather than
     *   retrieving first (default: any age is acceptable)
     * @param pullFrequency How often to refresh while observed (default: no polling)
     * @param query The query to execute. [Query.skip] must be zero and [Query.limit] positive:
     *   a skipped query says nothing about the rows before it, which is the only thing the cache
     *   knows how to record, and a limit of zero asks the server for nothing. Page with
     *   [LimitReactiveList.limit] instead of skipping.
     * @return A reactive reference to the query results that updates automatically
     */
    public fun list(
        query: Query<T>,
        maximumAge: Duration = Duration.INFINITE,
        pullFrequency: Duration = 0.seconds,
    ): ModelCacheLimitReadable<T>

    /**
     * Adds a new item to the backend and updates the cache.
     *
     * The item is inserted into the database and the returned item includes any
     * server-generated fields (e.g., ID, timestamps).
     *
     * **Cache Behavior**: The cache is updated immediately with the new item, and all
     * relevant queries are notified of the change.
     *
     * @param item The item to add (typically with an unset or temporary ID)
     * @return The added item with server-generated fields populated
     */
    public suspend fun add(item: T): T

    /**
     * Adds multiple items to the backend in a single batch operation.
     *
     * More efficient than multiple [add] calls when inserting many items.
     *
     * @param items The items to add
     * @return The added items with server-generated fields populated
     */
    public suspend fun addAll(items: List<T>): List<T>

    /**
     * Updates or inserts an item (upsert operation).
     *
     * If an item with the same ID exists, it is updated. Otherwise, a new item is created.
     *
     * @param item The item to upsert
     * @return A reactive reference to the upserted item
     */
    public suspend fun upsert(item: T): ModelCacheItemReadable<T>

    /**
     * Performs a bulk modification operation on items matching a condition.
     *
     * This is more efficient than fetching items individually and updating them one by one.
     *
     * **GOTCHA**: The cache may not immediately reflect all changes from bulk operations.
     * Items that are currently cached will be refreshed, but uncached items won't be
     * fetched until accessed.
     *
     * @param bulkUpdate The mass modification specification (condition and modifications)
     * @return The number of items affected
     */
    public suspend fun bulkModify(bulkUpdate: MassModification<T>): Int

    // ========== Backwards Compatibility / Convenience Methods ==========

    /**
     * Convenience operator for getting an item with default polling (1 minute).
     *
     * Equivalent to `item(id, pullFrequency = 1.minutes)`
     */
    @Deprecated("It's better to use item() and specify your pull frequency.", ReplaceWith("this.item(id, pullFrequency = 1.minutes)", "kotlin.time.Duration.Companion.minutes"))
    public operator fun get(id: ID): ModelCacheItemReadable<T> = this.item(id, pullFrequency = 1.minutes)

    /**
     * Convenience method for querying with default polling (1 minute).
     *
     * Equivalent to `list(query, pullFrequency = 1.minutes)`
     */
    @Deprecated("It's better to use list() and specify your pull frequency.", ReplaceWith("this.list(query, pullFrequency = 1.minutes)", "kotlin.time.Duration.Companion.minutes"))
    public fun query(query: Query<T>): ModelCacheLimitReadable<T> = this.list(query, pullFrequency = 1.minutes)

    /**
     * Convenience method for watching an item with real-time updates (no polling).
     *
     * Relies on WebSocket updates and [maximumAge] only.
     * Equivalent to `item(id)`
     */
    @Deprecated("It's better to use item() and specify your pull frequency.", ReplaceWith("this.item(id)"))
    public fun watch(id: ID): ModelCacheItemReadable<T> = this.item(id)

    /**
     * Convenience method for watching a query with real-time updates (no polling).
     *
     * Relies on WebSocket updates and [maximumAge] only.
     * Equivalent to `list(query)`
     */
    @Deprecated("It's better to use list() and specify your pull frequency.", ReplaceWith("this.list(query)"))
    public fun watch(query: Query<T>): ModelCacheLimitReadable<T> = this.list(query)

    /**
     * @deprecated Use [add] instead. This method will be removed in a future version.
     */
    @Deprecated("Use add instead")
    public suspend fun insert(item: T): ModelCacheItemReadable<T> = add(item).let { this[it._id] }

    /**
     * @deprecated Use [addAll] instead. This method will be removed in a future version.
     */
    @Deprecated("Use addAll instead")
    public suspend fun insert(items: List<T>): List<T> = addAll(items)
}

