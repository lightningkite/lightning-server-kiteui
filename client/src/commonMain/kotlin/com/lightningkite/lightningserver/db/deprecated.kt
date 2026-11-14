package com.lightningkite.lightningserver.db

@Deprecated("Use ModelReference instead", ReplaceWith("ModelReference<T, *>"))
public typealias WritableModel<T> = ModelReference<T, *>

@Deprecated("Use ModelReference instead", ReplaceWith("ModelReference<T, *>"))
public typealias ModelCacheItemReadable<T> = ModelReference<T, *>

@Deprecated("Use LimitReactiveList instead", ReplaceWith("LimitReactiveList")) public typealias LimitReadable<T> = LimitReactiveList<T>

@Deprecated("Use CachedRestEndpoints instead", ReplaceWith("CachedRestEndpoints<T, ID>"))
public typealias ModelCacheLike<T, ID> = CachedRestEndpoints<T, ID>

@Deprecated("Use CachedRestEndpoints instead", ReplaceWith("CachedRestEndpoints<T, ID>"))
public typealias CachingModelRestEndpoints<T, ID> = CachedRestEndpoints<T, ID>

@Deprecated("Use LimitReactiveList instead", ReplaceWith("LimitReactiveList<T>"))
public typealias ModelCacheLimitReadable<T> = LimitReactiveList<T>
