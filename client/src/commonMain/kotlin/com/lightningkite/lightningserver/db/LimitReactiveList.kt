package com.lightningkite.lightningserver.db

import com.lightningkite.reactive.context.awaitOnce
import com.lightningkite.reactive.context.invoke
import com.lightningkite.reactive.core.AppScope
import com.lightningkite.reactive.core.Reactive
import com.lightningkite.reactive.core.remember
import kotlinx.coroutines.launch

/**
 * A reactive list whose length can be extended on demand, used to implement pagination.
 */
public interface LimitReactiveList<out T>: Reactive<List<T>> {
    /**
     * The maximum number of items to retrieve.
     *
     * Assigning only *requests* the change; the extra items arrive later, and a failure to
     * retrieve them surfaces in [state] rather than at the assignment.  Prefer [limit].
     */
    public var limit: Int
        @Deprecated(
            "Use limit(n) instead, which reports when the new items have arrived.",
            ReplaceWith("limit(value)")
        )
        set

    /**
     * Sets [limit] and suspends until the newly requested items have been retrieved, so callers
     * such as infinite-scroll views know when their extension is complete.
     *
     * @throws Exception if the retrieval fails.
     */
    public suspend fun limit(count: Int) {
        @Suppress("DEPRECATION")
        limit = count
    }
}

public fun <T> Reactive<LimitReactiveList<T>>.flatten(): LimitReactiveList<T> {
    val root = this
    return object : LimitReactiveList<T>, Reactive<List<T>> by (remember { root()() }) {
        override var limit: Int
            get() = root.state.onSuccess { it.limit } ?: 0
            @Deprecated("Use limit(n) instead, which reports when the new items have arrived.")
            set(value) {
                AppScope.launch { root.awaitOnce().limit(value) }
            }

        override suspend fun limit(count: Int) = root.awaitOnce().limit(count)
    }
}
