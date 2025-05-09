package com.lightningkite.lightningserver.db

import com.lightningkite.lightningdb.*
import com.lightningkite.serialization.*

interface ClientModelRestEndpoints<T : HasId<ID>, ID : Comparable<ID>> {
    suspend fun default(): T = throw IllegalArgumentException()
    suspend fun query(input: Query<T>): List<T>
    suspend fun queryPartial(input: QueryPartial<T>): List<Partial<T>>
    suspend fun detail(id: ID): T
    suspend fun insertBulk(input: List<T>): List<T>
    suspend fun insert(input: T): T
    suspend fun upsert(id: ID, input: T): T
    suspend fun bulkReplace(input: List<T>): List<T>
    suspend fun replace(id: ID, input: T): T
    suspend fun bulkModify(input: MassModification<T>): Int
    suspend fun modifyWithDiff(id: ID, input: Modification<T>): EntryChange<T>
    suspend fun modify(id: ID, input: Modification<T>): T
    suspend fun bulkDelete(input: Condition<T>): Int
    suspend fun delete(id: ID): Unit
    suspend fun count(input: Condition<T>): Int
    suspend fun groupCount(input: GroupCountQuery<T>): Map<String, Int>
    suspend fun aggregate(input: AggregateQuery<T>): Double?
    suspend fun groupAggregate(input: GroupAggregateQuery<T>): Map<String, Double?>
    suspend fun permissions(): ModelPermissions<T>

}


