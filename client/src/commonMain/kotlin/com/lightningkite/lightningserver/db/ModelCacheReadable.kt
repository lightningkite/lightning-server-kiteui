package com.lightningkite.lightningserver.db

import com.lightningkite.services.database.Modification
import com.lightningkite.reactive.context.awaitOnce
import com.lightningkite.reactive.context.invoke
import com.lightningkite.reactive.core.AppScope
import com.lightningkite.reactive.core.MutableReactive
import com.lightningkite.reactive.core.Reactive
import com.lightningkite.reactive.core.remember
import kotlinx.coroutines.launch
import kotlin.time.Instant

public interface ModelCacheReadable<T> : Reactive<T> {
//    val disconnectedAt: Reactive<Instant?>
public val lastUpdatedAt: Reactive<Instant?>
}
public interface ModelCacheItemReadable<T> : MutableReactive<T?>, ModelCacheReadable<T?> {
    public suspend fun modify(modification: Modification<T>): T?
    public suspend fun delete(): Unit
    public suspend fun invalidate(): Unit
}

public interface ModelCacheLimitReadable<T> : LimitReactiveList<T>, ModelCacheReadable<List<T>> {
}

public fun <T> Reactive<ModelCacheItemReadable<T>>.flatten(): ModelCacheItemReadable<T> {
    val root = this
    return object : ModelCacheItemReadable<T>, Reactive<T?> by (remember { root()() }) {
        override suspend fun modify(modification: Modification<T>): T? = root.awaitOnce().modify(modification)
        override suspend fun delete() = root.awaitOnce().delete()
        override suspend fun set(value: T?) = root.awaitOnce().set(value)
//        override val disconnectedAt: Reactive<Instant?> by lazy { remember { root().disconnectedAt() } }
        override val lastUpdatedAt: Reactive<Instant?> by lazy { remember { root().lastUpdatedAt() } }
        override suspend fun invalidate() = root.awaitOnce().invalidate()
    }
}
public fun <T> Reactive<ModelCacheLimitReadable<T>>.flatten(): ModelCacheLimitReadable<T> {
    val root = this
    return object : ModelCacheLimitReadable<T>, Reactive<List<T>> by (remember { root()() }) {
        override var limit: Int
            get() = root.state.onSuccess { it.limit } ?: 0
            set(value) {
                // TODO: grumble grumble, this is bad
                AppScope.launch {
                    root.invoke().limit = value
                }
            }
//        override val disconnectedAt: Reactive<Instant?> by lazy { remember { root().disconnectedAt() } }
        override val lastUpdatedAt: Reactive<Instant?> by lazy { remember { root().lastUpdatedAt() } }
//        override suspend fun invalidate() = root.awaitOnce().invalidate()
    }
}