package com.lightningkite.lightningserver.db

import com.lightningkite.services.database.AggregateQuery
import com.lightningkite.services.database.Condition
import com.lightningkite.services.database.GroupAggregateQuery
import com.lightningkite.services.database.GroupCountQuery
import com.lightningkite.services.database.HasId
import com.lightningkite.services.database.MassModification
import com.lightningkite.services.database.Modification
import com.lightningkite.services.database.Query
import com.lightningkite.lightningdb.modification
import com.lightningkite.reactive.context.awaitOnce
import com.lightningkite.reactive.core.MutableReactive
import com.lightningkite.reactive.core.Reactive
import com.lightningkite.reactive.core.ReactiveState
import kotlinx.serialization.KSerializer
import kotlin.time.Duration
import kotlin.time.Instant
import kotlin.time.Duration.Companion.minutes

public interface ModelStore<T : HasId<ID>, ID : Comparable<ID>> {
    public val serializer: KSerializer<T>
    public val defaultRefreshPolicy: RefreshPolicy

    public fun detail(
        id: ID,
        refresh: RefreshPolicy = defaultRefreshPolicy,
    ): Reactive<T?>

    public fun query(
        query: Query<T>,
        refresh: RefreshPolicy = defaultRefreshPolicy,
    ): LimitReactiveList<T>

    /**
     * Returns the number of rows matching [condition], kept up to date like [query].
     *
     * Rows held locally answer it without a request when a list already covers [condition] completely.
     */
    public fun count(
        condition: Condition<T>,
        refresh: RefreshPolicy = defaultRefreshPolicy,
    ): Reactive<Int>

    /** Returns the number of rows matching the query's condition for each value of its group field, kept up to date like [query]. */
    public fun groupCount(
        query: GroupCountQuery<T>,
        refresh: RefreshPolicy = defaultRefreshPolicy,
    ): Reactive<Map<String, Int>>

    /**
     * Returns the aggregate of a numeric field over the rows matching the query's condition, kept up to
     * date like [query].
     *
     * The value is `null` when no rows match.
     */
    public fun aggregate(
        query: AggregateQuery<T>,
        refresh: RefreshPolicy = defaultRefreshPolicy,
    ): Reactive<Double?>

    /**
     * Returns [aggregate] computed separately for each value of the query's group field, kept up to date
     * like [query].
     */
    public fun groupAggregate(
        query: GroupAggregateQuery<T>,
        refresh: RefreshPolicy = defaultRefreshPolicy,
    ): Reactive<Map<String, Double?>>

    public suspend fun insert(item: T): T

    public suspend fun insertBulk(items: List<T>): List<T>

    public suspend fun upsert(id: ID, item: T): T

    public suspend fun replace(id: ID, item: T): T

    public suspend fun modify(id: ID, modification: Modification<T>): T

    public suspend fun delete(id: ID)

    public suspend fun bulkModify(bulkUpdate: MassModification<T>): Int

    public suspend fun bulkModify(ids: Set<ID>, modification: Modification<T>): Int

    public suspend fun bulkDelete(condition: Condition<T>): Int

    public fun lastUpdate(id: ID): Reactive<LastUpdate?>

    public fun lastUpdate(query: Query<T>): Reactive<LastUpdate?>

    public suspend fun invalidate(id: ID)

    public fun invalidateAll()

    /** Records [item] as the server's current version of its row, without making a request. */
    public fun localUpsert(item: T)

    /** Records that the row [id] was deleted, without making a request. */
    public fun localDelete(id: ID)

    /**
     * Applies [bulkUpdate] to the rows held locally, without making a request.
     *
     * Use this to mirror a change the server made some other way. Lists that could have gained rows
     * from the change fetch again.
     */
    public fun localBulkModify(bulkUpdate: MassModification<T>)

    /** Removes the rows held locally that match [condition], without making a request. */
    public fun localBulkDelete(condition: Condition<T>)

    public fun ref(
        id: ID,
        refresh: RefreshPolicy = defaultRefreshPolicy,
    ): Reference<T, ID> = Reference(this, id, refresh)

    // ========== Backwards Compatibility / Convenience Methods ==========

    @Deprecated("Use invalidateAll instead", ReplaceWith("invalidateAll()"))
    public suspend fun totallyInvalidate(): Unit = invalidateAll()

    @Deprecated("Use ref instead. Share refresh policies across the app.", ReplaceWith("ref(id, RefreshPolicy(maximumAge, pullFrequency))"))
    public fun item(
        id: ID,
        maximumAge: Duration = defaultRefreshPolicy.maximumAge,
        pullFrequency: Duration = defaultRefreshPolicy.pullFrequency
    ): Reference<T, ID> = ref(id, RefreshPolicy(maximumAge, pullFrequency))

    @Deprecated("Use query instead. Share refresh policies across the app.", ReplaceWith("query(query, RefreshPolicy(maximumAge, pullFrequency))"))
    public fun list(
        query: Query<T>,
        maximumAge: Duration = defaultRefreshPolicy.maximumAge,
        pullFrequency: Duration = defaultRefreshPolicy.pullFrequency
    ): LimitReactiveList<T> = query(query, RefreshPolicy(maximumAge, pullFrequency))

    @Deprecated("Use refresh policies instead, and share across the app", ReplaceWith("query(query, RefreshPolicy(maximumAge, pullFrequency))"))
    public fun query(
        query: Query<T>,
        maximumAge: Duration = defaultRefreshPolicy.maximumAge,
        pullFrequency: Duration = defaultRefreshPolicy.pullFrequency
    ): LimitReactiveList<T> = query(query, RefreshPolicy(maximumAge, pullFrequency))

    @Deprecated("Use ref instead.", ReplaceWith("ref(id)"))
    public fun item(
        id: ID,
    ): Reference<T, ID> = ref(id)

    @Deprecated("Use query instead.", ReplaceWith("query(query)"))
    public fun list(
        query: Query<T>,
    ): LimitReactiveList<T> = query(query)

    @Deprecated("Use ref instead", ReplaceWith("ref(id, defaultRefreshPolicy.copy(pullFrequency = 1.minutes))", "kotlin.time.Duration.Companion.minutes"))
    public operator fun get(id: ID): Reference<T, ID> = ref(id, defaultRefreshPolicy.copy(pullFrequency = 1.minutes))

    @Deprecated("Use ref instead", ReplaceWith("ref(id, defaultRefreshPolicy.copy(pullFrequency = Duration.ZERO))", "kotlin.time.Duration"))
    public fun watch(id: ID): Reference<T, ID> = ref(id, defaultRefreshPolicy.copy(pullFrequency = Duration.ZERO))

    @Deprecated("Use query instead", ReplaceWith("query(query, defaultRefreshPolicy.copy(pullFrequency = Duration.ZERO))", "kotlin.time.Duration"))
    public fun watch(query: Query<T>): LimitReactiveList<T> = this.query(query, defaultRefreshPolicy.copy(pullFrequency = Duration.ZERO))

    @Deprecated("Use insert instead", ReplaceWith("insert(item)"))
    public suspend fun add(item: T): T = insert(item)

    @Deprecated("Use insertBulk instead", ReplaceWith("insertBulk(items)"))
    public suspend fun addAll(items: List<T>): List<T> = insertBulk(items)

    @Deprecated("Use localUpsert instead", ReplaceWith("item.also { localUpsert(it) }"))
    public fun localInsert(item: T): T = item.also { localUpsert(it) }

    @Deprecated("Use upsert(id, item) instead", ReplaceWith("upsert(item._id, item)"))
    public suspend fun upsert(item: T): Reference<T, ID> = ref(upsert(item._id, item)._id)

    public data class LastUpdate(val at: Instant, val live: Boolean)

    public data class Reference<T : HasId<ID>, ID : Comparable<ID>>(
        public val store: ModelStore<T, ID>,
        public val id: ID,
        public val refreshPolicy: RefreshPolicy = store.defaultRefreshPolicy,
    ) : MutableReactive<T?> {
        private val detail: Reactive<T?> by lazy { store.detail(id, refreshPolicy) }
        override val state: ReactiveState<T?> get() = detail.state
        override fun addListener(listener: () -> Unit): () -> Unit = detail.addListener(listener)

        override suspend fun set(value: T?) {
            if (value == null) return delete()
            val existing = awaitOnce()
            if (existing == null) upsert(value)
            else modification(store.serializer, existing, value)?.let { modify(it) }
        }

        public suspend fun upsert(item: T): T = store.upsert(id, item)
        public suspend fun replace(item: T): T = store.replace(id, item)
        public suspend fun modify(modification: Modification<T>): T = store.modify(id, modification)
        public suspend fun delete(): Unit = store.delete(id)
        public suspend fun invalidate(): Unit = store.invalidate(id)
    }
}

/**
 * Says when a [ModelStore] read fetches its data again.
 *
 * Define the settings an app uses once and pass them to [ModelStore.query], [ModelStore.ref] and the rest,
 * or set one as a store's [ModelStore.defaultRefreshPolicy].
 *
 * @property maximumAge how old held data may be when a reader opens; older data is fetched again before it
 *   is shown. Data already shown is not withdrawn when it passes this age.
 * @property pullFrequency how often an open reader fetches again. [Duration.ZERO] asks for live data, using
 *   the update socket when there is one, and [Duration.INFINITE] never polls.
 */
public data class RefreshPolicy(
    val maximumAge: Duration,
    val pullFrequency: Duration,
) {
    public fun maxAge(maximumAge: Duration): RefreshPolicy = copy(maximumAge = maximumAge)
    public fun poll(pullFrequency: Duration): RefreshPolicy = copy(pullFrequency = pullFrequency)

    public companion object {
        /**
         * Returns a policy that fetches when a reader opens on data older than [maximumAge], and never polls.
         *
         * Data that is already shown stays until something replaces it.
         */
        public fun maxAge(maximumAge: Duration): RefreshPolicy = RefreshPolicy(maximumAge, pullFrequency = Duration.INFINITE)

        /**
         * Returns a policy that fetches every [pullFrequency] while a reader is open, and opens on whatever data is
         * held, however old.
         */
        public fun poll(pullFrequency: Duration): RefreshPolicy = RefreshPolicy(maximumAge = Duration.INFINITE, pullFrequency)

        /** Keeps the data current, through the update socket when there is one and by polling otherwise. */
        public val LIVE_DATA: RefreshPolicy = RefreshPolicy(Duration.ZERO, Duration.ZERO)

        /** Fetches again each time a cached value is read for the first time, but never while it stays open. */
        public val FETCH_ON_OPEN: RefreshPolicy = maxAge(Duration.ZERO)

        /** Fetches only when nothing is held. Result is cached for the lifetime of the cached entity and never re-fetched. */
        public val FETCH_ONCE: RefreshPolicy = RefreshPolicy(Duration.INFINITE, Duration.INFINITE)
    }
}
