package com.lightningkite.lightningserver.db

import com.lightningkite.services.database.HasId
import com.lightningkite.services.database.MassModification
import com.lightningkite.services.database.Query
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

interface ModelCacheLike<T : HasId<ID>, ID : Comparable<ID>> {
    fun item(
        id: ID,
        maximumAge: Duration = Duration.INFINITE,
        pullFrequency: Duration = 0.seconds,
    ): ModelCacheItemReadable<T>
    fun list(
        query: Query<T>,
        maximumAge: Duration = Duration.INFINITE,
        pullFrequency: Duration = 0.seconds,
    ): ModelCacheLimitReadable<T>

    suspend fun add(item: T): T
    suspend fun addAll(items: List<T>): List<T>
    suspend fun upsert(item: T): ModelCacheItemReadable<T>
    suspend fun bulkModify(bulkUpdate: MassModification<T>): Int
    // backwards compat
    operator fun get(id: ID) = this.item(id, pullFrequency = 1.minutes)
    fun query(query: Query<T>) = this.list(query, pullFrequency = 1.minutes)
    fun watch(id: ID) = this.item(id)
    fun watch(query: Query<T>) = this.list(query)
    @Deprecated("Use add instead") suspend fun insert(item: T): ModelCacheItemReadable<T> = add(item).let { this[it._id] }
    @Deprecated("Use addAll instead") suspend fun insert(items: List<T>): List<T> = addAll(items)
}