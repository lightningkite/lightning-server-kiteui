package com.lightningkite.lightningserver.db

@Deprecated("Use ModelReference instead", ReplaceWith("ModelReference<T, *>"))
public typealias WritableModel<T> = ModelReference<T, *>

@Deprecated("Use ModelReference instead", ReplaceWith("ModelReference<T, *>"))
public typealias ModelCacheItemReadable<T> = ModelReference<T, *>

@Deprecated("Use LimitReactiveList instead", ReplaceWith("LimitReactiveList")) public typealias LimitReadable<T> = LimitReactiveList<T>

@Deprecated("Use ModelStore instead", ReplaceWith("ModelStore<T, ID>"))
public typealias ModelCacheLike<T, ID> = ModelStore<T, ID>

@Deprecated("Use ModelStore instead", ReplaceWith("ModelStore<T, ID>"))
public typealias CachingModelRestEndpoints<T, ID> = ModelStore<T, ID>

@Deprecated("Use LimitReactiveList instead", ReplaceWith("LimitReactiveList<T>"))
public typealias ModelCacheLimitReadable<T> = LimitReactiveList<T>
