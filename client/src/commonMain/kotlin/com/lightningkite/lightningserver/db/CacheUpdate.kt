package com.lightningkite.lightningserver.db

import com.lightningkite.services.database.Condition
import com.lightningkite.services.database.HasId
import com.lightningkite.services.database.Query
import kotlin.time.Instant


sealed class CacheUpdate<T : HasId<ID>, ID : Comparable<ID>> {
    abstract val items: Collection<T>?
    class SocketOverload<T : HasId<ID>, ID : Comparable<ID>>(): CacheUpdate<T, ID>() {
        override val items: Collection<T>? get() = null
        override fun toString(): String = "SocketOverload($items)"
    }
    class SocketChanges<T : HasId<ID>, ID : Comparable<ID>>(
        val changed: Set<T>,
        val removed: Set<ID>,
        val fromCondition: Condition<T>,
        val fromRequirements: Set<ConditionAndTimestamp<T>>
    ): CacheUpdate<T, ID>(){
        interface ConditionAndTimestamp<T> {
            val condition: Condition<T>
            val activatedAt: Instant?
        }
        override val items: Collection<T> get() = changed
        override fun toString(): String = "SocketChanges($changed, $removed, $fromCondition, $fromRequirements)"
    }
    class QueryResult<T : HasId<ID>, ID : Comparable<ID>>(
        val query: Query<T>,
        val result: List<T>
    ): CacheUpdate<T, ID>(){
        override val items: Collection<T> get() = result
        override fun toString(): String = "QueryResult($query, $result)"
    }
    class MultiGetResult<T : HasId<ID>, ID : Comparable<ID>>(
        val missing: Set<ID>,
        val result: List<T>
    ): CacheUpdate<T, ID>(){
        override val items: Collection<T> get() = result
        override fun toString(): String = "MultiGetResult($missing, $result)"
    }
    class MutationResult<T : HasId<ID>, ID : Comparable<ID>>(
        override val items: Collection<T>
    ): CacheUpdate<T, ID>() {

        override fun toString(): String = "MutationResult($items)"
    }
    class DeletionResult<T : HasId<ID>, ID : Comparable<ID>>(
        val deletedIds: Set<ID>
    ): CacheUpdate<T, ID>() {
        override val items: Collection<T> = listOf()
        override fun toString(): String = "DeletionResult($deletedIds)"
    }
}