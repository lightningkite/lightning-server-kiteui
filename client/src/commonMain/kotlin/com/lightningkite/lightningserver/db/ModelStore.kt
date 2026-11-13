package com.lightningkite.lightningserver.db

import com.lightningkite.services.database.Condition
import com.lightningkite.services.database.HasId
import com.lightningkite.services.database.MassModification
import com.lightningkite.services.database.Modification
import com.lightningkite.services.database.Query
import com.lightningkite.lightningdb.modification
import com.lightningkite.reactive.context.awaitOnce
import com.lightningkite.reactive.core.Constant
import com.lightningkite.reactive.core.Draft
import com.lightningkite.reactive.core.MutableReactive
import com.lightningkite.reactive.core.Reactive
import com.lightningkite.reactive.core.ReactiveState
import kotlinx.serialization.KSerializer
import kotlin.time.Duration
import kotlin.time.Instant
import kotlin.time.Duration.Companion.minutes

public interface ModelStore<T : HasId<ID>, ID : Comparable<ID>> {
    public val serializer: KSerializer<T>
    public val defaultMaxAge: Duration
    public val defaultPullFrequency: Duration

    public fun detail(
        id: ID,
        maximumAge: Duration = defaultMaxAge,
        pullFrequency: Duration = defaultPullFrequency,
    ): Reactive<T?>

    public fun query(
        query: Query<T>,
        maximumAge: Duration = defaultMaxAge,
        pullFrequency: Duration = defaultPullFrequency,
    ): LimitReactiveList<T>

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
        maximumAge: Duration = defaultMaxAge,
        pullFrequency: Duration = defaultPullFrequency,
    ): ModelReference<T, ID> = ModelReference(this, id, maximumAge, pullFrequency)

    // ========== Backwards Compatibility / Convenience Methods ==========

    @Deprecated("Use invalidateAll instead", ReplaceWith("invalidateAll()"))
    public suspend fun totallyInvalidate(): Unit = invalidateAll()

    @Deprecated("Use ref instead", ReplaceWith("this.ref(id, maximumAge, pullFrequency)"))
    public fun item(
        id: ID,
        maximumAge: Duration = defaultMaxAge,
        pullFrequency: Duration = defaultPullFrequency,
    ): ModelReference<T, ID> = ref(id, maximumAge, pullFrequency)

    @Deprecated("Use query instead", ReplaceWith("this.query(query, maximumAge, pullFrequency)"))
    public fun list(
        query: Query<T>,
        maximumAge: Duration = defaultMaxAge,
        pullFrequency: Duration = defaultPullFrequency,
    ): LimitReactiveList<T> = query(query, maximumAge, pullFrequency)

    @Deprecated("Use ref instead", ReplaceWith("this.ref(id, pullFrequency = 1.minutes)", "kotlin.time.Duration.Companion.minutes"))
    public operator fun get(id: ID): ModelReference<T, ID> = ref(id, pullFrequency = 1.minutes)

    @Deprecated("Use ref instead", ReplaceWith("this.ref(id, pullFrequency = Duration.ZERO)", "kotlin.time.Duration"))
    public fun watch(id: ID): ModelReference<T, ID> = ref(id, pullFrequency = Duration.ZERO)

    @Deprecated("Use query instead", ReplaceWith("this.query(query, pullFrequency = Duration.ZERO)", "kotlin.time.Duration"))
    public fun watch(query: Query<T>): LimitReactiveList<T> = this.query(query, pullFrequency = Duration.ZERO)

    @Deprecated("Use insert instead", ReplaceWith("insert(item)"))
    public suspend fun add(item: T): T = insert(item)

    @Deprecated("Use insertBulk instead", ReplaceWith("insertBulk(items)"))
    public suspend fun addAll(items: List<T>): List<T> = insertBulk(items)

    @Deprecated("Use localUpsert instead", ReplaceWith("item.also { localUpsert(it) }"))
    public fun localInsert(item: T): T = item.also { localUpsert(it) }

    @Deprecated("Use upsert(id, item) instead", ReplaceWith("upsert(item._id, item)"))
    public suspend fun upsert(item: T): ModelReference<T, ID> = ref(upsert(item._id, item)._id)
}

public data class LastUpdate(val at: Instant, val live: Boolean)

public data class ModelReference<T : HasId<ID>, ID : Comparable<ID>>(
    public val store: ModelStore<T, ID>,
    public val id: ID,
    public val maximumAge: Duration = store.defaultMaxAge,
    public val pullFrequency: Duration = store.defaultPullFrequency,
) : MutableReactive<T?> {
    private val detail: Reactive<T?> by lazy { store.detail(id, maximumAge, pullFrequency) }
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
