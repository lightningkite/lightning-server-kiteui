package com.lightningkite.lightningserver.db

import com.lightningkite.lightningdb.Modification
import com.lightningkite.readable.AppScope
import com.lightningkite.readable.Readable
import com.lightningkite.readable.Writable
import com.lightningkite.readable.awaitOnce
import com.lightningkite.readable.invoke
import com.lightningkite.readable.shared
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant

interface ModelCacheReadable<T> : Readable<T> {
//    val disconnectedAt: Readable<Instant?>
    val lastUpdatedAt: Readable<Instant?>
}
interface ModelCacheItemReadable<T> : Writable<T?>, ModelCacheReadable<T?> {
    suspend fun modify(modification: Modification<T>): T?
    suspend fun delete(): Unit
    suspend fun invalidate(): Unit
}

interface ModelCacheLimitReadable<T> : LimitReadable<T>, ModelCacheReadable<List<T>> {
}

fun <T> Readable<ModelCacheItemReadable<T>>.flatten(): ModelCacheItemReadable<T> {
    val root = this
    return object : ModelCacheItemReadable<T>, Readable<T?> by (shared { root()() }) {
        override suspend fun modify(modification: Modification<T>): T? = root.awaitOnce().modify(modification)
        override suspend fun delete() = root.awaitOnce().delete()
        override suspend fun set(value: T?) = root.awaitOnce().set(value)
//        override val disconnectedAt: Readable<Instant?> by lazy { shared { root().disconnectedAt() } }
        override val lastUpdatedAt: Readable<Instant?> by lazy { shared { root().lastUpdatedAt() } }
        override suspend fun invalidate() = root.awaitOnce().invalidate()
    }
}
fun <T> Readable<ModelCacheLimitReadable<T>>.flatten(): ModelCacheLimitReadable<T> {
    val root = this
    return object : ModelCacheLimitReadable<T>, Readable<List<T>> by (shared { root()() }) {
        override var limit: Int
            get() = root.state.onSuccess { it.limit } ?: 0
            set(value) {
                // TODO: grumble grumble, this is bad
                AppScope.launch {
                    root.invoke().limit = value
                }
            }
//        override val disconnectedAt: Readable<Instant?> by lazy { shared { root().disconnectedAt() } }
        override val lastUpdatedAt: Readable<Instant?> by lazy { shared { root().lastUpdatedAt() } }
//        override suspend fun invalidate() = root.awaitOnce().invalidate()
    }
}