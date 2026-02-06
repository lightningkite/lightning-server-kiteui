package com.lightningkite.lightningserver.db

import com.lightningkite.kiteui.TypedWebSocket
import com.lightningkite.services.database.*
import com.lightningkite.reactive.core.Reactive


typealias WritableModel<T> = ModelCacheItemReadable<T>

@Deprecated("Use LimitReactiveList instead", ReplaceWith("LimitReactiveList")) typealias LimitReadable<T> = LimitReactiveList<T>
interface LimitReactiveList<T>: Reactive<List<T>> {
    var limit: Int
}

typealias ModelCollection<T, ID> = ModelCacheLike<T,ID>

typealias CachingModelRestEndpoints<T, ID> = ModelCacheLike<T,ID>

