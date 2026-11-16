package com.lightningkite.lightningserver.db

import com.lightningkite.services.database.Aggregate
import com.lightningkite.services.database.AggregateQuery
import com.lightningkite.services.database.Condition
import com.lightningkite.services.database.HasId
import com.lightningkite.services.database.MassModification
import com.lightningkite.services.database.Modification
import com.lightningkite.services.database.Query
import com.lightningkite.services.database.optics.Optic
import com.lightningkite.lightningdb.modification
import com.lightningkite.reactive.context.awaitOnce
import com.lightningkite.reactive.core.MutableReactive
import com.lightningkite.reactive.core.Reactive
import com.lightningkite.reactive.core.ReactiveState
import kotlinx.serialization.KSerializer
import kotlin.time.Duration
import kotlin.time.Instant
import kotlin.time.Duration.Companion.minutes

/**
 * Holds the rows of one model type on the client and keeps what is shown up to date with the server.
 *
 * Reads return a [Reactive] that fetches according to a [RefreshPolicy] while something listens to it.
 * Writes go to the server and record the result locally, so every open read sees the change.
 */
public interface ModelStore<T : HasId<ID>, ID : Comparable<ID>> {
    /** The serializer of the model type this store holds. */
    public val serializer: KSerializer<T>

    /** The [RefreshPolicy] that reads use when none is passed. */
    public val defaultRefreshPolicy: RefreshPolicy

    /**
     * Returns the row with the given [id], kept up to date according to [refresh].
     *
     * The value is `null` when no such row exists.
     */
    public fun detail(
        id: ID,
        refresh: RefreshPolicy = defaultRefreshPolicy,
    ): Reactive<T?>

    /**
     * Returns the rows matching [query], kept up to date according to [refresh].
     *
     * The query cannot skip rows; to load more, raise the limit of the returned list instead.
     *
     * @throws IllegalArgumentException if `query.skips != 0` or `query.limit < 0`.
     */
    public fun query(
        query: Query<T>,
        refresh: RefreshPolicy = defaultRefreshPolicy,
    ): LimitReactiveList<T>

    /**
     * Returns when the row with the given [id] was last known to match the server.
     *
     * The value is `null` while nothing is held for the row. Reading it never makes a request.
     */
    public fun lastUpdate(id: ID): Reactive<LastUpdate?>

    /**
     * Returns when the rows matching [query] were last known to match the server.
     *
     * The value is `null` while nothing is held for the query. Reading it never makes a request.
     */
    public fun lastUpdate(query: Query<T>): Reactive<LastUpdate?>

    /**
     * Returns the number of rows matching [condition], kept up to date like [query].
     *
     * Rows held locally answer it without a request when a list already covers [condition] completely.
     */
    public fun count(
        condition: Condition<T>,
        refresh: RefreshPolicy = defaultRefreshPolicy,
    ): Reactive<Int>

    /** Returns the number of rows matching [condition] for each value of [groupBy], kept up to date like [query]. */
    public fun <K> groupCount(
        condition: Condition<T>,
        groupBy: Optic<T, K>,
        refresh: RefreshPolicy = defaultRefreshPolicy,
    ): Reactive<Map<K, Int>>

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
     * Returns the [aggregate] of [property] over the rows matching [condition] for each value of [groupBy],
     * kept up to date like [query].
     *
     * A group's value is `null` when none of its rows has a value for [property].
     */
    public fun <K> groupAggregate(
        aggregate: Aggregate,
        condition: Condition<T>,
        groupBy: Optic<T, K>,
        property: Optic<T, out Number?>,
        refresh: RefreshPolicy = defaultRefreshPolicy,
    ): Reactive<Map<K, Double?>>
    

    /** Inserts [item] on the server and returns the row as the server stored it. */
    public suspend fun insert(item: T): T

    /** Inserts [item] as the row [id], or replaces that row if it exists, and returns the row as the server stored it. */
    public suspend fun upsert(id: ID, item: T): T

    /** Replaces the existing row [id] with [item] and returns the row as the server stored it. */
    public suspend fun replace(id: ID, item: T): T

    /** Applies [modification] to the row [id] on the server and returns the modified row. */
    public suspend fun modify(id: ID, modification: Modification<T>): T

    /** Deletes the row [id] on the server. */
    public suspend fun delete(id: ID)

    /** Inserts [items] on the server in one request and returns the rows as the server stored them. */
    public suspend fun bulkInsert(items: List<T>): List<T>

    /** Applies [bulkUpdate] on the server and returns the number of rows it changed. */
    public suspend fun bulkModify(bulkUpdate: MassModification<T>): Int

    /** Applies [modification] to the rows with the given [ids] on the server and returns the number of rows it changed. */
    public suspend fun bulkModify(ids: Set<ID>, modification: Modification<T>): Int

    /** Deletes the rows matching [condition] on the server and returns the number of rows deleted. */
    public suspend fun bulkDelete(condition: Condition<T>): Int


    /** Forgets what is held for the row [id] and fetches it again. */
    public suspend fun invalidate(id: ID)

    /** Forgets everything held, so open reads fetch again. */
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

    /** Returns a [Reference] to the row [id] that reads it according to [refresh]. */
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

    @Deprecated("Use bulkInsert instead", ReplaceWith("bulkInsert(items)"))
    public suspend fun addAll(items: List<T>): List<T> = bulkInsert(items)

    @Deprecated("Use localUpsert instead", ReplaceWith("item.also { localUpsert(it) }"))
    public fun localInsert(item: T): T = item.also { localUpsert(it) }

    @Deprecated("Use upsert(id, item) instead", ReplaceWith("upsert(item._id, item)"))
    public suspend fun upsert(item: T): Reference<T, ID> = ref(upsert(item._id, item)._id)

    /**
     * Says how current the data held for a read is.
     *
     * @property at when the data was last known to match the server.
     * @property live `true` when the update socket has reported every change since [at], so the data is
     *   still current.
     */
    public data class LastUpdate(val at: Instant, val live: Boolean)

    /**
     * Reads and writes the row [id] of [store], with its value kept up to date according to [refreshPolicy].
     *
     * Setting the value to `null` deletes the row. Setting another value inserts it when the row doesn't
     * exist, and otherwise sends only the fields that differ.
     */
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

        /** Upserts [item] as this row. See [ModelStore.upsert]. */
        public suspend fun upsert(item: T): T = store.upsert(id, item)

        /** Replaces this row with [item]. See [ModelStore.replace]. */
        public suspend fun replace(item: T): T = store.replace(id, item)

        /** Applies [modification] to this row. See [ModelStore.modify]. */
        public suspend fun modify(modification: Modification<T>): T = store.modify(id, modification)

        /** Deletes this row. See [ModelStore.delete]. */
        public suspend fun delete(): Unit = store.delete(id)

        /** Forgets what is held for this row and fetches it again. See [ModelStore.invalidate]. */
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
        public val LIVE: RefreshPolicy = RefreshPolicy(Duration.ZERO, Duration.ZERO)

        /**
         * Waits for a fresh fetch each time a reader opens, rather than showing what is held, and never fetches
         * again while it stays open.
         *
         * If that fetch fails, the reader shows the error instead of the held data.
         */
        public val FRESH_ON_OPEN: RefreshPolicy = maxAge(Duration.ZERO)

        /** Uses the held value whenever there is one, however old, and fetches only when nothing is held. */
        public val CACHED_IF_ANY: RefreshPolicy = RefreshPolicy(Duration.INFINITE, Duration.INFINITE)
    }
}
