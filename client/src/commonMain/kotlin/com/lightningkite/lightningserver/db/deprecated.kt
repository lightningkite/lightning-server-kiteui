package com.lightningkite.lightningserver.db


public typealias WritableModel<T> = ModelCacheItemReadable<T>

@Deprecated("Use LimitReactiveList instead", ReplaceWith("LimitReactiveList")) public typealias LimitReadable<T> = LimitReactiveList<T>

public typealias ModelCollection<T, ID> = ModelCacheLike<T,ID>

public typealias CachingModelRestEndpoints<T, ID> = ModelCacheLike<T,ID>

