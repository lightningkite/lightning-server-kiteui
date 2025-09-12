package com.lightningkite.lightningserver.db

import com.lightningkite.services.database.HasId
import com.lightningkite.services.database.Query
import com.lightningkite.reactive.core.Listenable

interface ListReconstructionCalculator<T : HasId<ID>, ID : Comparable<ID>> {
    fun updates(query: Query<T>): Listenable
    fun update(update: CacheUpdate<T, ID>): Unit
    fun cached(query: Query<T>): WithTimestampAndLimit<List<T>>?
    fun recommendQuery(query: Query<T>): Query<T> = query
    fun clear()
}