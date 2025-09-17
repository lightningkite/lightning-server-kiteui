package com.lightningkite.lightningserver.db

import com.lightningkite.kiteui.Console
import com.lightningkite.services.database.HasId
import com.lightningkite.services.database.Query
import com.lightningkite.services.database.comparator

import com.lightningkite.reactive.core.BasicListenable
import com.lightningkite.reactive.core.Listenable
import kotlinx.serialization.KSerializer
import kotlin.time.Clock

/**
 * A simple tool that can reconstruct lists from partial updates.
 *
 * This could be a lot more smart about when it needs to pull - theoretically, it could merge needs between queries in smarter ways.
 */
class NaiveListReconstructionCalculator<T : HasId<ID>, ID : Comparable<ID>>(
    val serializer: KSerializer<T>,
    val log: Console? = null,
    val clock: Clock = Clock.System
) : ListReconstructionCalculator<T, ID> {
    val byQuery = HashMap<Query<T>, WithTimestampAndLimit<List<T>>>()
    val all = BasicListenable()
    override fun updates(query: Query<T>): Listenable = all
    override fun update(update: CacheUpdate<T, ID>): Unit {
        log?.log("Handling update $update")
        when (update) {
            is CacheUpdate.DeletionResult -> {
                val iter = byQuery.iterator()
                while (iter.hasNext()) {
                    val entry = iter.next()
                    val old = entry.value
                    // If we can't guarantee this was a full knowledge update, we can't update the timestamp with confidence.
                    val new = old.item.update(entry.key, emptyList(), update.deletedIds)
                    entry.setValue(old.copy(item = new))
                }
            }

            is CacheUpdate.MultiGetResult -> {
                val iter = byQuery.iterator()
                while (iter.hasNext()) {
                    val entry = iter.next()
                    val old = entry.value
                    // If we can't guarantee this was a full knowledge update, we can't update the timestamp with confidence.
                    val new = old.item.update(entry.key, update.items, update.missing)
                    entry.setValue(old.copy(item = new))
                }
            }

            is CacheUpdate.MutationResult -> {
                val iter = byQuery.iterator()
                while (iter.hasNext()) {
                    val entry = iter.next()
                    val old = entry.value
                    // If we can't guarantee this was a full knowledge update, we can't update the timestamp with confidence.
                    val new = old.item.update(entry.key, update.items)
                    entry.setValue(old.copy(item = new))
                }
            }

            is CacheUpdate.QueryResult -> byQuery[update.query] = WithTimestampAndLimit(update.result, requestedLimit = update.query.limit, at = clock.now())
            is CacheUpdate.SocketChanges -> {
                val iter = byQuery.iterator()
                while (iter.hasNext()) {
                    val entry = iter.next()
                    val old = entry.value
                    val completeUpdate = update.fromRequirements.any {
                        it.condition == entry.key.condition && it.activatedAt?.let { old.at > it } == true
                    }
                    if (completeUpdate) {
                        val new = old.item.update(entry.key, update.changed, update.removed)
                        entry.setValue(old.copy(item = new, at = clock.now()))
                    } else {
                        // If we can't guarantee this was a full knowledge update, we can't update the timestamp.
                        val new = old.item.update(entry.key, update.changed)
                        entry.setValue(old.copy(item = new))
                    }
                }
            }

            is CacheUpdate.SocketOverload -> byQuery.clear()
        }
        all.invokeAll()
    }

    override fun cached(query: Query<T>): WithTimestampAndLimit<List<T>>? = byQuery[query]
    override fun recommendQuery(query: Query<T>): Query<T> = query
    override fun clear() {
        byQuery.clear()
    }

    fun List<T>.update(query: Query<T>, edits: Collection<T>, removed: Collection<ID> = emptyList()): List<T> {
        // Get the IDs of items to be removed (either explicitly removed or updated)
        val rem = removed + edits.map { it._id }.toSet()

        // Filter out removed items
        val filteredList = this.filter { it._id !in rem }

        // Add edited items
        val combinedList = filteredList + edits

        // Filter by query condition, remove duplicates, and sort according to query
        return combinedList
            .filter { query.condition(it) }
            .distinctBy { it._id }
            .sortedWith(query.orderBy.ensureTotal(serializer).comparator!!)
            .let { 
                // Apply limit if specified in the query
                if (query.limit > 0) it.take(query.limit) else it 
            }
    }
}
