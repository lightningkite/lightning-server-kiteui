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
 * - **maximumAge**: Data older than this is considered stale and will trigger a refresh
 * - **pullFrequency**: How often to poll for updates in the background (0 = no polling)
 * - **Duration.INFINITE**: Data never expires and is never refreshed
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
     * - If [maximumAge] is exceeded, the item will be refetched
     * - If [pullFrequency] > 0, the item will be polled at that interval
     * - WebSocket updates (if available) will update the item in real-time
     *
     * @param id The ID of the item to retrieve
     * @param maximumAge How old cached data can be before triggering a refresh (default: never expires)
     * @param pullFrequency How often to poll for updates (default: no polling)
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
     * - If [maximumAge] is exceeded, the query will be re-executed
     * - If [pullFrequency] > 0, the query will be polled at that interval
     * - WebSocket updates (if available) will update matching items in real-time
     *
     * **IMPORTANT**: results are shared. Items retrieved by any query - or by an [item] lookup - are
     * held once, so a query can be answered from what other reads already established, and a change
     * seen anywhere is seen everywhere.
     *
     * @param query The query to execute. [Query.skip] must be zero and [Query.limit] positive:
     *   a skipped query says nothing about the rows before it, which is the only thing the cache
     *   knows how to record, and a limit of zero asks the server for nothing. Page with
     *   [LimitReactiveList.limit] instead of skipping.
     * @param maximumAge How old cached data can be before triggering a refresh (default: never expires)
     * @param pullFrequency How often to poll for updates (default: no polling)
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

