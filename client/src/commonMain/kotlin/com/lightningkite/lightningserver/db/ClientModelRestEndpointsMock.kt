package com.lightningkite.lightningserver.db

import com.lightningkite.kiteui.ConnectionException
import com.lightningkite.kiteui.Log
import com.lightningkite.services.database.*
import com.lightningkite.lightningserver.LSError
import com.lightningkite.lightningserver.LsErrorException
import com.lightningkite.lightningserver.typed.ClientModelRestEndpoints
import com.lightningkite.services.database.Partial
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

public open class ClientModelRestEndpointsMock<T : HasId<ID>, ID : Comparable<ID>>(
    public val scope: CoroutineScope,
    public val delayAmount: Duration = 0.1.seconds,
    public val log: Log? = null,
) : ClientModelRestEndpoints<T, ID> {
    public open var connectivityFailure: Boolean = false

    /**
     * What this mock enforces on reads, exactly as a `ModelPermissionsTable` would.
     *
     * Defaults to allowing everything, which leaves [query] behaving as if permissions did not
     * exist.  Set it to something with a `readMask` to exercise the part clients cannot see: that a
     * sort over a masked field silently narrows the rows a query can return.
     */
    public open var modelPermissions: ModelPermissions<T> = ModelPermissions.allowAll()

    /**
     * The condition the server really applies, which is the caller's plus everything permissions
     * impose.  Mirrors `ModelPermissionsTable.find`; kept in one place so the two cannot drift.
     */
    protected fun effectiveCondition(condition: Condition<T>, orderBy: List<SortPart<T>>): Condition<T> =
        condition and
                modelPermissions.read and
                modelPermissions.readMask.permitSort(orderBy) and
                modelPermissions.readMask(condition)

    public val data: HashMap<ID, T> = HashMap<ID, T>()
    public open fun change(collectionUpdates: CollectionUpdates<T, ID>) {
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
        if (connectivityFailure) throw ConnectionException("Dead")
        log?.log("query")
        delay(delayAmount)
        val effective = effectiveCondition(input.condition, input.orderBy)
        return data.values
            .filter { effective(it) }
            .let {
                input.orderBy.comparator?.let { c ->
                    it.sortedWith(c)
                } ?: it.sortedBy { it._id }
            }
            .take(input.limit)
            .map { modelPermissions.readMask(it) }
    }

    override suspend fun queryPartial(input: QueryPartial<T>): List<Partial<T>> = TODO()
    override suspend fun detail(id: ID): T {
        if (connectivityFailure) throw ConnectionException("Dead")
        log?.log("detail")
        delay(delayAmount)
        val found = data[id]?.takeIf { modelPermissions.read(it) }
            ?: throw LsErrorException(LSError(404, "not-found", "", ""))
        return modelPermissions.readMask(found)
    }

    override suspend fun insertBulk(input: List<T>): List<T> {
        if (connectivityFailure) throw ConnectionException("Dead")
        log?.log("insertBulk")
        delay(delayAmount)
        return input.onEach { change(CollectionUpdates(updates = setOf(it))) }
    }

    override suspend fun insert(input: T): T {
        if (connectivityFailure) throw ConnectionException("Dead")
        log?.log("insert")
        delay(delayAmount)
        return input.also { change(CollectionUpdates(updates = setOf(it))) }
    }

    override suspend fun upsert(id: ID, input: T): T {
        if (connectivityFailure) throw ConnectionException("Dead")
        log?.log("upsert")
        delay(delayAmount)
        return input.also { change(CollectionUpdates(updates = setOf(it))) }
    }

    override suspend fun bulkReplace(input: List<T>): List<T> {
        if (connectivityFailure) throw ConnectionException("Dead")
        log?.log("bulkReplace")
        delay(delayAmount)
        return input.onEach { change(CollectionUpdates(updates = setOf(it))) }
    }

    override suspend fun replace(id: ID, input: T): T {
        if (connectivityFailure) throw ConnectionException("Dead")
        log?.log("replace")
        delay(delayAmount)
        return input.also { change(CollectionUpdates(updates = setOf(it))) }
    }

    override suspend fun bulkModify(input: MassModification<T>): Int {
        if (connectivityFailure) throw ConnectionException("Dead")
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
        if (connectivityFailure) throw ConnectionException("Dead")
        log?.log("modifyWithDiff")
        delay(delayAmount)
        return EntryChange(
            data[id],
            data[id]?.let { input(it) }?.also { change(CollectionUpdates(updates = setOf(it))) })
    }

    override suspend fun modify(id: ID, input: Modification<T>): T {
        if (connectivityFailure) throw ConnectionException("Dead")
        log?.log("modify")
        delay(delayAmount)
        return data[id]?.let { input(it) }?.also { change(CollectionUpdates(updates = setOf(it))) }
            ?: throw LsErrorException(
                LSError(404, "not-found", "", "")
            )
    }

    override suspend fun bulkDelete(input: Condition<T>): Int {
        if (connectivityFailure) throw ConnectionException("Dead")
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
        if (connectivityFailure) throw ConnectionException("Dead")
        log?.log("delete")
        delay(delayAmount)
        change(CollectionUpdates(remove = setOf(id)))
    }

    override suspend fun count(input: Condition<T>): Int {
        if (connectivityFailure) throw ConnectionException("Dead")
        log?.log("count")
        delay(delayAmount)
        return data.values.asSequence().filter { input(it) }.count { input(it) }
    }

    override suspend fun groupCount(input: GroupCountQuery<T>): Map<String, Int> = TODO()
    override suspend fun groupCount2(input: GroupCountQuery<T>): Map<String, Int> = TODO()
    override suspend fun aggregate(input: AggregateQuery<T>): Double? = TODO()
    override suspend fun groupAggregate(input: GroupAggregateQuery<T>): Map<String, Double?> = TODO()
    override suspend fun groupAggregate2(input: GroupAggregateQuery<T>): Map<String, Double?> = TODO()
    override suspend fun permissions(): ModelPermissions<T> = modelPermissions

}