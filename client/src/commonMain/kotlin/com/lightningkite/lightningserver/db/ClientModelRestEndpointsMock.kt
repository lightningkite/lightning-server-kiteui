package com.lightningkite.lightningserver.db

import com.lightningkite.kiteui.Console
import com.lightningkite.kiteui.ConsoleRoot
import com.lightningkite.lightningdb.AggregateQuery
import com.lightningkite.lightningdb.CollectionUpdates
import com.lightningkite.lightningdb.Condition
import com.lightningkite.lightningdb.EntryChange
import com.lightningkite.lightningdb.GroupAggregateQuery
import com.lightningkite.lightningdb.GroupCountQuery
import com.lightningkite.lightningdb.HasId
import com.lightningkite.lightningdb.MassModification
import com.lightningkite.lightningdb.ModelPermissions
import com.lightningkite.lightningdb.Modification
import com.lightningkite.lightningdb.Query
import com.lightningkite.lightningdb.QueryPartial
import com.lightningkite.lightningdb.comparator
import com.lightningkite.lightningserver.LSError
import com.lightningkite.lightningserver.LsErrorException
import com.lightningkite.serialization.Partial
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

open class ClientModelRestEndpointsMock<T : HasId<ID>, ID : Comparable<ID>>(
    val scope: CoroutineScope,
    val delayAmount: Duration = 0.1.seconds,
    val log: Console? = null,
) : ClientModelRestEndpoints<T, ID> {
    val data = HashMap<ID, T>()
    open fun change(collectionUpdates: CollectionUpdates<T, ID>) {
        log?.log("changes: $collectionUpdates")
        collectionUpdates.updates.forEach {
            data[it._id] = it
        }
        collectionUpdates.remove.forEach {
            data.remove(it)
        }
    }

    override suspend fun default(): T = throw IllegalArgumentException()
    override suspend fun query(input: Query<T>): List<T> {
        log?.log("query")
        delay(delayAmount)
        return data.values
            .filter { input.condition(it) }
            .let {
                input.orderBy.comparator?.let { c ->
                    it.sortedWith(c)
                } ?: it.sortedBy { it._id }
            }
            .take(input.limit)
    }

    override suspend fun queryPartial(input: QueryPartial<T>): List<Partial<T>> = TODO()
    override suspend fun detail(id: ID): T {
        log?.log("detail")
        delay(delayAmount)
        return data[id] ?: throw LsErrorException(404, LSError(404, "not-found", "", ""))
    }
    override suspend fun insertBulk(input: List<T>): List<T> {
        log?.log("insertBulk")
        delay(delayAmount)
        return input.onEach { change(CollectionUpdates(updates = setOf(it))) }
    }

    override suspend fun insert(input: T): T {
        log?.log("insert")
        delay(delayAmount)
        return input.also { change(CollectionUpdates(updates = setOf(it))) }
    }
    override suspend fun upsert(id: ID, input: T): T {
        log?.log("upsert")
        delay(delayAmount)
        return input.also { change(CollectionUpdates(updates = setOf(it))) }
    }
    override suspend fun bulkReplace(input: List<T>): List<T> {
        log?.log("bulkReplace")
        delay(delayAmount)
        return input.onEach { change(CollectionUpdates(updates = setOf(it))) }
    }

    override suspend fun replace(id: ID, input: T): T {
        log?.log("replace")
        delay(delayAmount)
        return input.also { change(CollectionUpdates(updates = setOf(it))) }
    }
    override suspend fun bulkModify(input: MassModification<T>): Int {
        log?.log("bulkModify")
        delay(delayAmount)
        return data.values.toList().count {
            if (input.condition(it)) {
                input.modification(it).also { change(CollectionUpdates(updates = setOf(it))) }
                true
            } else false
        }
    }

    override suspend fun modifyWithDiff(id: ID, input: Modification<T>): EntryChange<T> {
        log?.log("modifyWithDiff")
        delay(delayAmount)
        return EntryChange(
            data[id],
            data[id]?.let { input(it) }?.also { change(CollectionUpdates(updates = setOf(it))) })
    }

    override suspend fun modify(id: ID, input: Modification<T>): T {
        log?.log("modify")
        delay(delayAmount)
        return data[id]?.let { input(it) }?.also { change(CollectionUpdates(updates = setOf(it))) }
            ?: throw LsErrorException(
                404,
                LSError(404, "not-found", "", "")
            )
    }

    override suspend fun bulkDelete(input: Condition<T>): Int {
        log?.log("bulkDelete")
        delay(delayAmount)
        return data.values.toList().count {
            if (input(it)) {
                change(CollectionUpdates(remove = setOf(it._id)))
                true
            } else false
        }
    }

    override suspend fun delete(id: ID): Unit {
        log?.log("delete")
        delay(delayAmount)
        change(CollectionUpdates(remove = setOf(id)))
    }

    override suspend fun count(input: Condition<T>): Int {
        log?.log("count")
        delay(delayAmount)
        return data.values.asSequence().filter { input(it) }.count { input(it) }
    }

    override suspend fun groupCount(input: GroupCountQuery<T>): Map<String, Int> = TODO()
    override suspend fun groupCount2(input: GroupCountQuery<T>): Map<String, Int> = TODO()
    override suspend fun aggregate(input: AggregateQuery<T>): Double? = TODO()
    override suspend fun groupAggregate(input: GroupAggregateQuery<T>): Map<String, Double?> = TODO()
    override suspend fun groupAggregate2(input: GroupAggregateQuery<T>): Map<String, Double?> = TODO()
    override suspend fun permissions(): ModelPermissions<T> = ModelPermissions.Companion.allowAll()

}