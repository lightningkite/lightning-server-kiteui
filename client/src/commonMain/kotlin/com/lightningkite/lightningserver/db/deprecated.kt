package com.lightningkite.lightningserver.db

import com.lightningkite.kiteui.TypedWebSocket
import com.lightningkite.services.database.*
import com.lightningkite.reactive.core.Reactive


interface ClientModelRestEndpointsPlusWs<T : HasId<ID>, ID : Comparable<ID>> {
    fun watch(): TypedWebSocket<Query<T>, ListChange<T>>
}

interface ClientModelRestEndpointsPlusUpdatesWebsocket<T : HasId<ID>, ID : Comparable<ID>> {
    fun updates(): TypedWebSocket<Condition<T>, CollectionUpdates<T, ID>>
}

typealias WritableModel<T> = ModelCacheItemReadable<T>
interface LimitReadable<T>: Reactive<List<T>> {
    var limit: Int
}

typealias ModelCollection<T, ID> = ModelCacheLike<T,ID>

typealias CachingModelRestEndpoints<T, ID> = ModelCacheLike<T,ID>

