package com.lightningkite.lightningserver.db

import com.lightningkite.kiteui.TypedWebSocket
import com.lightningkite.services.database.*
import com.lightningkite.reactive.core.Reactive


public typealias WritableModel<T> = ModelCacheItemReadable<T>

@Deprecated("Use LimitReactiveList instead", ReplaceWith("LimitReactiveList")) public typealias LimitReadable<T> = LimitReactiveList<T>
public interface LimitReactiveList<T>: Reactive<List<T>> {
    public var limit: Int
}

public typealias ModelCollection<T, ID> = ModelCacheLike<T,ID>

public typealias CachingModelRestEndpoints<T, ID> = ModelCacheLike<T,ID>

