package com.lightningkite.lightningserver.db

@Deprecated("Use ModelStore.Reference instead", ReplaceWith("ModelStore.Reference<T, ID>"))
public typealias ModelReference<T, ID> = ModelStore.Reference<T, ID>

@Deprecated("Use ModelStore.Reference instead", ReplaceWith("ModelStore.Reference<T, *>"))
public typealias WritableModel<T> = ModelStore.Reference<T, *>

@Deprecated("Use ModelStore.Reference instead", ReplaceWith("ModelStore.Reference<T, *>"))
public typealias ModelCacheItemReadable<T> = ModelStore.Reference<T, *>

@Deprecated("Use LimitReactiveList instead", ReplaceWith("LimitReactiveList")) public typealias LimitReadable<T> = LimitReactiveList<T>

@Deprecated("Use ModelStore instead", ReplaceWith("ModelStore<T, ID>"))
public typealias ModelCacheLike<T, ID> = ModelStore<T, ID>

@Deprecated("Use ModelStore instead", ReplaceWith("ModelStore<T, ID>"))
public typealias CachingModelRestEndpoints<T, ID> = ModelStore<T, ID>

@Deprecated("Use LimitReactiveList instead", ReplaceWith("LimitReactiveList<T>"))
public typealias ModelCacheLimitReadable<T> = LimitReactiveList<T>
