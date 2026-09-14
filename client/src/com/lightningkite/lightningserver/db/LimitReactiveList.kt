package com.lightningkite.lightningserver.db

import com.lightningkite.reactive.core.Reactive

/**
 * A reactive list whose length can be extended on demand, used to implement pagination.
 */
public interface LimitReactiveList<T>: Reactive<List<T>> {
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
